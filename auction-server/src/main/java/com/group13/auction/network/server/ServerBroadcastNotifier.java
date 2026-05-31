package com.group13.auction.network.server;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.dao.NotificationDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.notification.Notification;
import com.group13.auction.model.notification.NotificationMessages;
import com.group13.auction.model.notification.NotificationTypes;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.observer.AuctionEvent;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Gửi WebSocket và lưu thông báo inbox khi có sự kiện phiên đấu giá (singleton). */
public class ServerBroadcastNotifier {

  private static final Logger log = LoggerFactory.getLogger(ServerBroadcastNotifier.class);
  private static final ServerBroadcastNotifier INSTANCE = new ServerBroadcastNotifier();

  private final SessionManager sessionManager = SessionManager.getInstance();

  // Không final để unit test inject mock DAO (xem TestFixture.silenceGlobalSingletons).
  private NotificationDAO notificationDAO = new NotificationDAO();
  private UserDAO userDAO = new UserDAO();

  /** Đã gửi AUTO_BID_EXHAUSTED cho cặp user+phiên — tránh spam khi chain chạy lặp. */
  private final Set<String> autoBidExhaustedNotifiedKeys = ConcurrentHashMap.newKeySet();

  private ServerBroadcastNotifier() {}

  public static ServerBroadcastNotifier getInstance() {
    return INSTANCE;
  }

  private void persistNotification(String userId, String auctionId, String title, String body) {
    persistNotification(userId, auctionId, NotificationTypes.SYSTEM, title, body);
  }

  private void persistNotification(
      String userId, String auctionId, String notificationType, String title, String body) {
    try {
      Notification notification =
          Notification.create(userId, auctionId, notificationType, title, body);
      notificationDAO.save(notification);
    } catch (Exception e) {
      log.warn(
          "Không thể lưu notification: userId={}, type={}, title={}",
          userId,
          notificationType,
          title,
          e);
    }
  }

  /** Ghi thông báo inbox cho mọi user đã JOINED phiên (theo {@code user_auction_activity}). */
  public void notifyJoinedParticipants(String auctionId, String title, String body) {
    notifyJoinedParticipants(auctionId, title, body, null);
  }

  /**
   * @param excludeUserId bỏ qua user (ví dụ winner đã có thông báo riêng từ {@link
   *     #notifyAuctionEnded}).
   */
  public void notifyJoinedParticipants(
      String auctionId, String title, String body, String excludeUserId) {
    notifyJoinedParticipants(auctionId, NotificationTypes.SYSTEM, title, body, excludeUserId);
  }

  public void notifyJoinedParticipants(
      String auctionId, String notificationType, String title, String body, String excludeUserId) {
    if (auctionId == null || title == null || body == null) {
      return;
    }
    var joinedUserIds = userDAO.findJoinedUserIdsByAuctionId(auctionId);
    int sent = 0;
    for (String userId : joinedUserIds) {
      if (excludeUserId != null && excludeUserId.equals(userId)) {
        continue;
      }
      persistNotification(userId, auctionId, notificationType, title, body);
      sent++;
    }
    if (sent > 0) {
      log.info(
          "Inbox notification sent to {} joined user(s): auctionId={}, title={}",
          sent,
          auctionId,
          title);
    }
  }

  /** Map {@link AuctionEvent} → title/body và gửi tới toàn bộ người đã tham gia phiên. */
  public void notifyJoinedParticipantsForEvent(AuctionEvent event) {
    if (event == null || event.getAuction() == null) {
      return;
    }
    AuctionEvent.AuctionEventType type = event.getEventType();
    if (type == AuctionEvent.AuctionEventType.FRAUD_DETECTED
        || type == AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST
        || type == AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST_ACCEPTED
        || type == AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED
        || type == AuctionEvent.AuctionEventType.BID_PLACED
        || type == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET
        || type == AuctionEvent.AuctionEventType.PAYMENT_COMPLETED
        || type == AuctionEvent.AuctionEventType.SECOND_CHANCE_OFFERED
        || type == AuctionEvent.AuctionEventType.AUCTION_ENDED) {
      // AUCTION_ENDED: inbox won/lost được gửi riêng trong notifyAuctionOutcome() — tránh trùng
      // hoặc lệch title/body khi leader đã rời phiên trước khi kết thúc.
      return;
    }
    String auctionId = event.getAuction().getId();
    String title = eventTitle(type);
    String body =
        event.getMessage() != null && !event.getMessage().isBlank()
            ? event.getMessage()
            : eventBody(event);
    notifyJoinedParticipants(auctionId, title, body, null);
  }

  private static String eventTitle(AuctionEvent.AuctionEventType type) {
    return switch (type) {
      case AUCTION_UPCOMING -> "Phiên sắp bắt đầu";
      case AUCTION_STARTED -> "Phiên đã bắt đầu";
      case BID_PLACED -> "Có bid mới";
      case BID_RESERVE_NOT_MET -> "Bid chưa đạt reserve";
      case AUCTION_EXTENDED -> "Phiên được gia hạn";
      case AUCTION_ENDED -> "Phiên đã kết thúc";
      case AUCTION_NO_WINNER -> "Phiên không có người thắng";
      case RESERVE_NOT_MET_CLOSED -> "Reserve chưa đạt";
      case PAYMENT_COMPLETED -> "Thanh toán hoàn tất";
      case AUCTION_CANCELED -> "Phiên đã hủy";
      case SECOND_CHANCE_OFFERED -> "Cơ hội mua thứ cấp";
      default -> "Cập nhật phiên đấu giá";
    };
  }

  private static String eventBody(AuctionEvent event) {
    NormalUser bidder = event.getBidder();
    String bidderName = bidder != null ? bidder.getUsername() : "Không có";
    return switch (event.getEventType()) {
      case AUCTION_UPCOMING -> "Phiên đấu giá sắp bắt đầu. Hãy chuẩn bị sẵn sàng.";
      case AUCTION_STARTED -> "Phiên đấu giá đã chuyển sang RUNNING.";
      case BID_PLACED -> String.format("%s đặt giá %d.", bidderName, event.getBidAmount());
      case BID_RESERVE_NOT_MET ->
          String.format("%s đặt %d — chưa đạt reserve.", bidderName, event.getBidAmount());
      case AUCTION_EXTENDED ->
          event.getMessage() != null ? event.getMessage() : "Phiên được gia hạn (anti-sniping).";
      case AUCTION_ENDED ->
          String.format(
              "Phiên kết thúc. Người dẫn đầu: %s | Giá: %d.", bidderName, event.getBidAmount());
      case AUCTION_NO_WINNER -> "Phiên kết thúc không có ai đặt giá. Cọc sẽ được hoàn trả.";
      case RESERVE_NOT_MET_CLOSED ->
          String.format("Phiên kết thúc — giá cao nhất %d chưa đạt reserve.", event.getBidAmount());
      case PAYMENT_COMPLETED ->
          String.format("Giao dịch hoàn tất với giá %d.", event.getBidAmount());
      case AUCTION_CANCELED -> "Phiên đấu giá đã bị hủy.";
      case SECOND_CHANCE_OFFERED -> "Winner không thanh toán — hệ thống mở cơ hội mua thứ cấp.";
      default -> "Có cập nhật mới cho phiên bạn đang tham gia.";
    };
  }

  /**
   * Inbox cho bidder được thăng lên dẫn đầu khi leader hiện tại rời phiên (không phải thắng/thua).
   */
  public void notifyLeaderPromotedAfterLeave(
      NormalUser newLeader, Auction auction, String previousLeaderUsername) {
    if (newLeader == null || auction == null) {
      return;
    }
    String previousName =
        previousLeaderUsername != null && !previousLeaderUsername.isBlank()
            ? previousLeaderUsername
            : "Người dẫn đầu trước";
    persistNotification(
        newLeader.getId(),
        auction.getId(),
        NotificationTypes.AUCTION,
        NotificationMessages.leaderPromotedTitle(),
        NotificationMessages.leaderPromotedBody(
            auction, previousName, auction.getCurrentPrice()));
  }

  /** Lưu thông báo inbox khi người dùng chủ động rời phiên. */
  public void notifyUserLeftAuction(
      NormalUser user,
      Auction auction,
      boolean depositForfeited,
      long forfeitedAmount,
      boolean ratingPenalized) {
    if (user == null || auction == null) {
      return;
    }
    String body =
        depositForfeited
            ? NotificationMessages.leaveAuctionForfeitBody(
            auction, forfeitedAmount, ratingPenalized)
            : NotificationMessages.leaveAuctionRefundBody(auction);
    persistNotification(
        user.getId(),
        auction.getId(),
        NotificationTypes.AUCTION,
        NotificationMessages.leaveAuctionTitle(),
        body);
  }

  // Bid events

  /** Xóa cờ đã báo exhausted — gọi khi user đăng ký/cập nhật auto-bid mới trong phiên. */
  public void clearAutoBidExhaustedFlag(String userId, String auctionId) {
    if (userId == null || auctionId == null) {
      return;
    }
    autoBidExhaustedNotifiedKeys.remove(exhaustedKey(userId, auctionId));
  }

  /**
   * Chỉ gửi {@link PacketType#OUTBID_NOTIFY} cho bidder bid thủ công vừa bị vượt giá (client hiện
   * popup). Không lưu inbox/DB — không áp dụng khi đang bật auto-bid.
   */
  public void notifyOutbid(
      NormalUser previousLeader,
      Auction auction,
      NormalUser newBidder,
      long newAmount,
      long previousAmount) {
    if (previousLeader == null || auction == null || newBidder == null) {
      return;
    }
    if (!userDAO.isActiveJoinedParticipant(previousLeader.getId(), auction.getId())) {
      log.debug(
          "Skip outbid notify — user no longer active participant: auctionId={}, userId={}",
          auction.getId(),
          previousLeader.getId());
      return;
    }
    if (previousLeader.getId().equals(newBidder.getId())) {
      return;
    }

    var autoBidRegistry = com.group13.auction.strategy.AutoBidRegistry.getInstance();
    var activeAutoBid = autoBidRegistry.get(previousLeader.getId(), auction.getId());

    // Bidder đang bật auto-bid: hệ thống sẽ tự counter qua AutoBidProcessor — không báo outbid.
    // FIX: KHÔNG cancel entry ở đây, dù calculateNextBid < 0.
    // Lý do: AutoBidProcessor.calcSmartBid có fallback cho LATE/VERY_HOT phase (bid bằng maxBid
    // nếu maxBid > currentPrice), nhưng AutoBidEntry.calculateNextBid không có fallback đó.
    // Nếu cancel sớm tại đây → chain không tìm thấy entry → không counter → chuỗi escalation mất.
    // AutoBidProcessor.notifyExhaustedBidders() chịu trách nhiệm duy nhất cho việc cancel + notify.
    if (activeAutoBid != null) {
      log.debug(
          "Skip outbid notify — user has active auto-bid (AutoBidProcessor will handle counter/exhaust): auctionId={}, userId={}",
          auction.getId(),
          previousLeader.getId());
      return;
    }

    BidDTOs.OutbidNotifyDTO dto = new BidDTOs.OutbidNotifyDTO();
    dto.setAuctionId(auction.getId());
    dto.setAuctionItemName(NotificationMessages.itemName(auction));
    dto.setNewCurrentPrice(newAmount);
    dto.setPreviousPrice(previousAmount);
    dto.setNewBidderUsername(NotificationMessages.username(newBidder));
    sessionManager.sendToUser(previousLeader.getId(), Packet.of(PacketType.OUTBID_NOTIFY, dto));

    log.info(
        "Outbid notification: auctionId={}, outbidUser={}, newBidder={}, amount={}",
        auction.getId(),
        previousLeader.getUsername(),
        newBidder.getUsername(),
        newAmount);
  }

  public void notifyBidUpdate(
      Auction auction, long bidAmount, String bidderUsername, boolean isAutoBid) {
    log.info(
        "Broadcast BID_UPDATE: auctionId={}, bidder={}, amount={}, autoBid={}",
        auction.getId(),
        bidderUsername,
        bidAmount,
        isAutoBid);
    BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, bidAmount, bidAmount);
    PacketType type =
        auction.isReserveMet() ? PacketType.BID_UPDATE : PacketType.BID_RESERVE_NOT_MET_UPDATE;

    sessionManager.broadcastToAuction(auction.getId(), Packet.of(type, update));

    BidDTOs.BidChartPointDTO chartPoint =
        DTOMapper.toBidChartPoint(auction.getId(), bidAmount, bidderUsername, isAutoBid);
    sessionManager.broadcastToAuction(
        auction.getId(), Packet.of(PacketType.BID_CHART_POINT_UPDATE, chartPoint));
  }

  public void notifyAutoBidTriggered(
      String userId,
      String auctionId,
      long bidAmount,
      long newPrice,
      long maxBid,
      boolean isLeading) {
    if (!userDAO.isActiveJoinedParticipant(userId, auctionId)) {
      return;
    }
    BidDTOs.AutoBidTriggeredDTO dto = new BidDTOs.AutoBidTriggeredDTO();
    dto.setAuctionId(auctionId);
    dto.setBidAmount(bidAmount);
    dto.setNewCurrentPrice(newPrice);
    dto.setRemainingMaxBid(maxBid - bidAmount);
    dto.setNowLeading(isLeading);
    dto.setTimestamp(LocalDateTime.now());
    sessionManager.sendToUser(userId, Packet.of(PacketType.AUTO_BID_TRIGGERED_NOTIFY, dto));
  }

  /**
   * Báo auto-bid không còn đủ max để counter — popup + inbox (tối đa một lần cho đến khi đăng ký
   * lại).
   */
  public void notifyAutoBidExhausted(
      String userId, Auction auction, long maxBid, long currentPrice, String leadingUsername) {
    if (userId == null || auction == null) {
      return;
    }
    String auctionId = auction.getId();
    if (!userDAO.isActiveJoinedParticipant(userId, auctionId)) {
      return;
    }
    if (!autoBidExhaustedNotifiedKeys.add(exhaustedKey(userId, auctionId))) {
      log.debug(
          "Skip duplicate auto-bid exhausted notify: auctionId={}, userId={}", auctionId, userId);
      return;
    }

    String leaderName =
        leadingUsername != null && !leadingUsername.isBlank() ? leadingUsername : "Chưa có";

    persistNotification(
        userId,
        auctionId,
        NotificationTypes.AUCTION,
        NotificationMessages.autoBidExhaustedTitle(),
        NotificationMessages.autoBidExhaustedBody(auction, maxBid, currentPrice, leaderName));

    BidDTOs.AutoBidExhaustedDTO dto = new BidDTOs.AutoBidExhaustedDTO();
    dto.setAuctionId(auctionId);
    dto.setMaxBid(maxBid);
    dto.setCurrentPrice(currentPrice);
    dto.setLeadingBidderUsername(leaderName);
    sessionManager.sendToUser(userId, Packet.of(PacketType.AUTO_BID_EXHAUSTED_NOTIFY, dto));

    log.info(
        "Auto-bid exhausted notification: auctionId={}, userId={}, maxBid={}, currentPrice={}",
        auctionId,
        userId,
        maxBid,
        currentPrice);
  }

  private static String exhaustedKey(String userId, String auctionId) {
    return userId + ":" + auctionId;
  }

  // Auction lifecycle

  public void notifyAuctionStarted(Auction auction) {
    AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, null);
    sessionManager.broadcastToAuction(
        auction.getId(), Packet.of(PacketType.AUCTION_STARTED_UPDATE, update));
  }

  public void notifyAuctionEnded(Auction auction) {
    log.info("Deliver AUCTION_ENDED_UPDATE: auctionId={}", auction.getId());
    AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, null);
    Packet<AuctionDTOs.AuctionUpdateDTO> packet =
        Packet.of(PacketType.AUCTION_ENDED_UPDATE, update);
    deliverAuctionLifecycle(auction, packet);
    notifyAuctionOutcome(auction);
  }

  /**
   * JOINED + seller nhận qua user-channel; watcher không JOINED nhận qua room; dedupe trên session.
   */
  private void deliverAuctionLifecycle(Auction auction, Packet<?> packet) {
    if (auction == null || packet == null) {
      return;
    }
    // include users who already LEFT before auction ended
    Set<String> targets =
        new HashSet<>(userDAO.findEverJoinedUserIdsByAuctionId(auction.getId()));
    if (auction.getItem() != null && auction.getItem().getSeller() != null) {
      targets.add(auction.getItem().getSeller().getId());
    }
    sessionManager.deliverAuctionLifecyclePacket(auction.getId(), packet, targets);
  }

  /** Thông báo won/lost cho người tham gia và seller sau khi phiên có winner hợp lệ. */
  public void notifyAuctionOutcome(Auction auction) {
    NormalUser winner = resolveWinningBidder(auction);
    if (auction == null || winner == null || winner.getId() == null) {
      return;
    }
    long finalPrice = auction.getCurrentPrice();
    long depositPaid =
        auction.getWinner() != null
            ? auction.getWinner().getDepositPaid()
            : auction.getItem().getStartingPrice() * 3 / 10;
    String winnerId = winner.getId();
    String winnerName = NotificationMessages.username(winner);
    String sellerId =
        auction.getItem() != null && auction.getItem().getSeller() != null
            ? auction.getItem().getSeller().getId()
            : null;

    if (sellerId != null) {
      persistNotification(
          sellerId,
          auction.getId(),
          NotificationTypes.AUCTION,
          NotificationMessages.auctionEndedSellerTitle(),
          NotificationMessages.auctionEndedSellerBody(auction, winnerName, finalPrice));
    }

    // Một vòng lặp — mỗi user nhận đúng cặp title/body (tránh winner nhận nhầm "thua").
    var participantIds = userDAO.findEverJoinedUserIdsByAuctionId(auction.getId());
    for (String userId : participantIds) {
      if (userId == null || userId.isBlank() || userId.equals(sellerId)) {
        continue;
      }
      if (winnerId.equals(userId)) {
        persistNotification(
            userId,
            auction.getId(),
            NotificationTypes.AUCTION,
            NotificationMessages.auctionWonTitle(),
            NotificationMessages.auctionWonBody(auction, finalPrice, depositPaid));
      } else {
        persistNotification(
            userId,
            auction.getId(),
            NotificationTypes.AUCTION,
            NotificationMessages.auctionLostTitle(),
            NotificationMessages.auctionLostBody(auction, winnerName, finalPrice));
      }
    }
  }

  /** Ưu tiên winner đã persist; fallback currentLeader (RAM) khi phiên vừa đóng. */
  private static NormalUser resolveWinningBidder(Auction auction) {
    if (auction == null) {
      return null;
    }
    if (auction.getWinner() != null && auction.getWinner().getWinner() != null) {
      return auction.getWinner().getWinner();
    }
    return auction.getCurrentLeader();
  }

  public void notifyAuctionNoWinner(Auction auction) {
    AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, "NO_WINNER");
    Packet<AuctionDTOs.AuctionUpdateDTO> packet =
        Packet.of(PacketType.AUCTION_NO_WINNER_UPDATE, update);
    deliverAuctionLifecycle(auction, packet);
    if (auction.getItem() != null && auction.getItem().getSeller() != null) {
      persistNotification(
          auction.getItem().getSeller().getId(),
          auction.getId(),
          "Phiên đấu giá đã kết thúc",
          "Phiên đấu giá không có người thắng.");
    }
  }

  public void notifyAuctionReserveNotMet(Auction auction) {
    AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, "RESERVE_NOT_MET");
    Packet<AuctionDTOs.AuctionUpdateDTO> packet =
        Packet.of(PacketType.AUCTION_RESERVE_NOT_MET_UPDATE, update);
    deliverAuctionLifecycle(auction, packet);
    if (auction.getItem() != null && auction.getItem().getSeller() != null) {
      persistNotification(
          auction.getItem().getSeller().getId(),
          auction.getId(),
          "Phiên đấu giá đã kết thúc",
          "Giá chốt chưa đạt reserve.");
    }
  }

  public void notifyAuctionCanceled(Auction auction, String reason) {
    AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, reason);
    deliverAuctionLifecycle(auction, Packet.of(PacketType.AUCTION_CANCELED_UPDATE, update));
  }

  public void notifyAuctionExtended(
      Auction auction, LocalDateTime newEndTime, int extendedBySeconds) {
    log.info(
        "Broadcast AUCTION_EXTENDED: auctionId={}, newEndTime={}, extendedBy={}s",
        auction.getId(),
        newEndTime,
        extendedBySeconds);
    AuctionDTOs.AuctionExtendedDTO dto = new AuctionDTOs.AuctionExtendedDTO();
    dto.setAuctionId(auction.getId());
    dto.setNewEndTime(newEndTime);
    dto.setExtendedBySeconds(extendedBySeconds);
    sessionManager.broadcastToAuction(
        auction.getId(), Packet.of(PacketType.AUCTION_EXTENDED_NOTIFY, dto));
  }

  public void notifyAuctionUpcomingEnd(Auction auction, int minutesLeft) {
    if (auction == null) {
      return;
    }
    long remainingSeconds = minutesLeft * 60L;
    AuctionDTOs.AuctionUpcomingEndDTO dto = new AuctionDTOs.AuctionUpcomingEndDTO();
    dto.setAuctionId(auction.getId());
    dto.setRemainingSeconds(remainingSeconds);
    sessionManager.broadcastToAuction(
        auction.getId(), Packet.of(PacketType.AUCTION_UPCOMING_END_NOTIFY, dto));

    notifyJoinedParticipants(
        auction.getId(),
        NotificationTypes.AUCTION,
        NotificationMessages.auctionEndingSoonTitle(minutesLeft),
        NotificationMessages.auctionEndingSoonBody(auction, minutesLeft),
        null);
  }

  // Payment

  public void notifyDepositRefund(
      String userId, String auctionId, long refundAmount, long newBalance) {
    PaymentDTOs.DepositRefundDTO dto = new PaymentDTOs.DepositRefundDTO();
    dto.setAuctionId(auctionId);
    dto.setRefundAmount(refundAmount);
    dto.setNewBalance(newBalance);
    sessionManager.sendToUser(userId, Packet.of(PacketType.DEPOSIT_REFUND_NOTIFY, dto));
  }

  public void notifyDepositForfeited(
      String userId, String auctionId, long forfeitedAmount, long newBalance) {
    PaymentDTOs.DepositForfeitedDTO dto = new PaymentDTOs.DepositForfeitedDTO();
    dto.setAuctionId(auctionId);
    dto.setForfeitedAmount(forfeitedAmount);
    dto.setNewBalance(newBalance);
    sessionManager.sendToUser(userId, Packet.of(PacketType.DEPOSIT_FORFEITED_NOTIFY, dto));
  }

  public void notifySecondChanceOffer(
      String runnerUpUserId, PaymentDTOs.SecondChanceOfferDTO offer) {
    sessionManager.sendToUser(
        runnerUpUserId, Packet.of(PacketType.SECOND_CHANCE_OFFER_NOTIFY, offer));
  }

  /**
   * Second Chance chỉ gửi inbox + realtime cho seller và runner-up (không broadcast cho mọi người
   * JOINED).
   */
  public void notifySecondChanceOffered(
      Auction auction, NormalUser runnerUp, SecondChanceOffer offer) {
    if (auction == null || runnerUp == null || offer == null) {
      return;
    }

    String auctionId = auction.getId();
    long offerPrice = offer.getOfferPrice();

    long depositRequired = offer.getDepositPaid();
    persistNotification(
        runnerUp.getId(),
        auctionId,
        NotificationTypes.SECOND_CHANCE_OFFER,
        NotificationMessages.scoReceivedTitle(),
        NotificationMessages.scoReceivedBody(
            auction, offerPrice, depositRequired, offer.getDeadline()));

    if (auction.getItem() != null && auction.getItem().getSeller() != null) {
      NormalUser seller = auction.getItem().getSeller();
      persistNotification(
          seller.getId(),
          auctionId,
          NotificationTypes.SECOND_CHANCE_OFFER,
          NotificationMessages.scoSentToSellerTitle(),
          NotificationMessages.scoSentToSellerBody(
              auction, NotificationMessages.username(runnerUp), offerPrice, offer.getDeadline()));
    }

    PaymentDTOs.SecondChanceOfferDTO dto = DTOMapper.toSecondChanceOfferDTO(auction, offer);
    notifySecondChanceOffer(runnerUp.getId(), dto);

    log.info(
        "Second Chance Offer notified: auctionId={}, runnerUp={}, sellerOnly+runnerUp inbox",
        auctionId,
        runnerUp.getUsername());
  }

  public void notifyPaymentCompleted(String sellerId, PaymentDTOs.PaymentResultDTO result) {
    sessionManager.sendToUser(sellerId, Packet.of(PacketType.PAYMENT_COMPLETED_NOTIFY, result));
  }

  public void notifyPaymentExpired(String winnerId, PaymentDTOs.PaymentExpiredDTO expired) {
    sessionManager.sendToUser(winnerId, Packet.of(PacketType.PAYMENT_EXPIRED_NOTIFY, expired));
  }

  public void notifySecondChanceExpired(Auction auction, SecondChanceOffer offer) {
    if (auction == null || offer == null || offer.getRunnerUp() == null) {
      return;
    }
    NormalUser runnerUp = offer.getRunnerUp();
    sessionManager.sendToUser(
        runnerUp.getId(), Packet.of(PacketType.SECOND_CHANCE_EXPIRED_NOTIFY, auction.getId()));

    persistNotification(
        runnerUp.getId(),
        auction.getId(),
        NotificationTypes.SECOND_CHANCE_OFFER,
        NotificationMessages.scoExpiredTitle(),
        NotificationMessages.scoExpiredRunnerUpBody(auction, offer.getOfferPrice()));

    if (auction.getItem() != null && auction.getItem().getSeller() != null) {
      persistNotification(
          auction.getItem().getSeller().getId(),
          auction.getId(),
          NotificationTypes.SECOND_CHANCE_OFFER,
          NotificationMessages.scoExpiredTitle(),
          NotificationMessages.scoExpiredSellerBody(
              auction, NotificationMessages.username(runnerUp)));
    }
  }

  /**
   * Broadcast tới tất cả watcher khi runner-up chấp nhận Second Chance Offer. Phiên có winner mới —
   * client cần cập nhật UI (hiển thị winner, enable nút thanh toán).
   *
   * @param auction phiên đấu giá (đã có winner mới được set)
   */
  public void notifySecondChanceAccepted(Auction auction) {
    log.info(
        "Broadcast SECOND_CHANCE_ACCEPTED_UPDATE: auctionId={}, newWinner={}",
        auction.getId(),
        auction.getWinner() != null ? auction.getWinner().getWinner().getUsername() : "null");
    AuctionDTOs.AuctionUpdateDTO update =
        DTOMapper.toAuctionUpdateDTO(auction, "SECOND_CHANCE_ACCEPTED");
    sessionManager.broadcastToAuction(
        auction.getId(), Packet.of(PacketType.SECOND_CHANCE_ACCEPTED_UPDATE, update));

    long offerPrice =
        auction.getWinner() != null
            ? auction.getWinner().getFinalPrice()
            : auction.getCurrentPrice();
    if (auction.getItem() != null
        && auction.getItem().getSeller() != null
        && auction.getWinner() != null
        && auction.getWinner().getWinner() != null) {
      NormalUser runnerUp = auction.getWinner().getWinner();
      persistNotification(
          auction.getItem().getSeller().getId(),
          auction.getId(),
          NotificationTypes.SECOND_CHANCE_OFFER,
          NotificationMessages.scoAcceptedSellerTitle(),
          NotificationMessages.scoAcceptedSellerBody(
              auction, NotificationMessages.username(runnerUp), offerPrice));
      persistNotification(
          runnerUp.getId(),
          auction.getId(),
          NotificationTypes.SECOND_CHANCE_OFFER,
          NotificationMessages.scoAcceptedRunnerUpTitle(),
          NotificationMessages.scoAcceptedRunnerUpBody(auction, offerPrice));
    }
  }

  public void notifySecondChanceDeclined(Auction auction, SecondChanceOffer offer) {
    if (auction == null || offer == null || offer.getRunnerUp() == null) {
      return;
    }
    NormalUser runnerUp = offer.getRunnerUp();
    persistNotification(
        runnerUp.getId(),
        auction.getId(),
        NotificationTypes.SECOND_CHANCE_OFFER,
        NotificationMessages.scoDeclinedRunnerUpTitle(),
        NotificationMessages.scoDeclinedRunnerUpBody(auction));

    if (auction.getItem() != null && auction.getItem().getSeller() != null) {
      persistNotification(
          auction.getItem().getSeller().getId(),
          auction.getId(),
          NotificationTypes.SECOND_CHANCE_OFFER,
          NotificationMessages.scoDeclinedSellerTitle(),
          NotificationMessages.scoDeclinedSellerBody(
              auction, NotificationMessages.username(runnerUp)));
    }
  }

  public void notifyPaymentSuccess(Auction auction, PaymentDTOs.PaymentResultDTO result) {
    if (auction == null || auction.getWinner() == null) {
      return;
    }
    AuctionWinner aw = auction.getWinner();
    NormalUser winner = aw.getWinner();
    if (winner == null) {
      return;
    }
    long finalPrice =
        result != null && result.getFinalPrice() > 0 ? result.getFinalPrice() : aw.getFinalPrice();

    persistNotification(
        winner.getId(),
        auction.getId(),
        NotificationTypes.PAYMENT,
        NotificationMessages.paymentSuccessWinnerTitle(),
        NotificationMessages.paymentSuccessWinnerBody(auction, finalPrice, aw.getDepositPaid()));

    if (auction.getItem() != null && auction.getItem().getSeller() != null) {
      String sellerId = auction.getItem().getSeller().getId();
      persistNotification(
          sellerId,
          auction.getId(),
          NotificationTypes.PAYMENT,
          NotificationMessages.paymentSuccessSellerTitle(),
          NotificationMessages.paymentSuccessSellerBody(
              auction, NotificationMessages.username(winner), finalPrice));
      if (result != null) {
        notifyPaymentCompleted(sellerId, result);
      }
    }
  }

  public void notifyPaymentFailed(Auction auction) {
    if (auction == null || auction.getWinner() == null) {
      return;
    }
    AuctionWinner aw = auction.getWinner();
    NormalUser winner = aw.getWinner();
    if (winner == null) {
      return;
    }
    persistNotification(
        winner.getId(),
        auction.getId(),
        NotificationTypes.PAYMENT,
        NotificationMessages.paymentFailedTitle(),
        NotificationMessages.paymentFailedWinnerBody(auction, aw.getDepositPaid()));

    PaymentDTOs.PaymentExpiredDTO expired = new PaymentDTOs.PaymentExpiredDTO();
    expired.setAuctionId(auction.getId());
    expired.setDepositForfeited(aw.getDepositPaid());
    notifyPaymentExpired(winner.getId(), expired);
  }

  public void notifyItemReceived(Auction auction) {
    if (auction == null || auction.getWinner() == null) {
      return;
    }
    NormalUser winner = auction.getWinner().getWinner();
    if (winner == null) {
      return;
    }
    persistNotification(
        winner.getId(),
        auction.getId(),
        NotificationTypes.ORDER,
        NotificationMessages.itemReceivedWinnerTitle(),
        NotificationMessages.itemReceivedWinnerBody(auction));

    if (auction.getItem() != null && auction.getItem().getSeller() != null) {
      persistNotification(
          auction.getItem().getSeller().getId(),
          auction.getId(),
          NotificationTypes.ORDER,
          NotificationMessages.itemReceivedSellerTitle(),
          NotificationMessages.itemReceivedSellerBody(
              auction, NotificationMessages.username(winner)));
    }
  }

  /** Tin nhắn từ seller tới buyer (gọi khi có tính năng nhắn tin). */
  public void notifyNewMessageFromSeller(
      Auction auction, NormalUser buyer, NormalUser seller, String messageText) {
    if (auction == null || buyer == null || seller == null) {
      return;
    }
    persistNotification(
        buyer.getId(),
        auction.getId(),
        NotificationTypes.MESSAGE,
        NotificationMessages.sellerMessageTitle(NotificationMessages.username(seller)),
        NotificationMessages.sellerMessageBody(
            auction, NotificationMessages.username(seller), messageText));
  }

  // Account

  public void notifyAccountSuspended(
      String userId, double currentRating, double threshold, String reason) {
    RatingDTOs.AccountSuspendedDTO dto = new RatingDTOs.AccountSuspendedDTO();
    dto.setCurrentRating(currentRating);
    dto.setThreshold(threshold);
    dto.setReason(reason);
    sessionManager.sendToUser(userId, Packet.of(PacketType.ACCOUNT_SUSPENDED_NOTIFY, dto));
  }

  public void notifyAccountRestored(String userId, double newRating, String newStatus) {
    RatingDTOs.AccountRestoredDTO dto = new RatingDTOs.AccountRestoredDTO();
    dto.setNewRating(newRating);
    dto.setNewStatus(newStatus);
    sessionManager.sendToUser(userId, Packet.of(PacketType.ACCOUNT_RESTORED_NOTIFY, dto));
  }

  public void notifyQualityReportApproved(
      String winnerId, ReportDTOs.QualityReportResultDTO result) {
    sessionManager.sendToUser(
        winnerId, Packet.of(PacketType.QUALITY_REPORT_APPROVED_NOTIFY, result));
  }

  public void notifyQualityReportReceived(String sellerId, ReportDTOs.QualityReportDTO report) {
    sessionManager.sendToUser(
        sellerId, Packet.of(PacketType.QUALITY_REPORT_RECEIVED_NOTIFY, report));
  }

  public void notifyQualityReportRejected(String sellerId, String reportId) {
    sessionManager.sendToUser(
        sellerId, Packet.of(PacketType.QUALITY_REPORT_REJECTED_NOTIFY, reportId));
  }

  public void notifySellerRefundOverdue(String sellerId) {
    sessionManager.sendToUser(
        sellerId, Packet.of(PacketType.SELLER_REFUND_OVERDUE_NOTIFY, sellerId));
  }

  public void notifyFraudDetected(AdminDTOs.FraudDetectedDTO fraud) {
    sessionManager.broadcastToAdmins(Packet.of(PacketType.FRAUD_DETECTED_NOTIFY, fraud));
  }
}