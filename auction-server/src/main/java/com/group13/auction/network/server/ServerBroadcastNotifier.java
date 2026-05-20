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
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.notification.Notification;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cầu nối giữa Observer pattern phía server và WebSocket broadcast.
 *
 * <p>Khi {@link com.group13.auction.observer.AuctionObserver} nhận event từ service,
 * nó gọi {@link ServerBroadcastNotifier} để push packet tới đúng client(s).
 *
 * <p>Đây là lớp được inject vào Observer để tránh observer biết về WebSocket trực tiếp.
 *
 * <p>Singleton — toàn server dùng 1 instance.
 */
public class ServerBroadcastNotifier {

    private static final Logger log = LoggerFactory.getLogger(ServerBroadcastNotifier.class);
    private static final ServerBroadcastNotifier INSTANCE = new ServerBroadcastNotifier();

    private final SessionManager sessionManager = SessionManager.getInstance();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    private ServerBroadcastNotifier() {}

    public static ServerBroadcastNotifier getInstance() { return INSTANCE; }

    private void persistNotification(String userId, String auctionId, String title, String body) {
        Notification notification = Notification.create(userId, auctionId, title + "\n" + body);
        notificationDAO.save(notification);
    }

    // ── Bid events ────────────────────────────────────────────────────────────

    public void notifyBidUpdate(Auction auction, long bidAmount,
                                String bidderUsername, boolean isAutoBid) {
        log.info("Broadcast BID_UPDATE: auctionId={}, bidder={}, amount={}, autoBid={}",
                auction.getId(), bidderUsername, bidAmount, isAutoBid);
        BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, bidAmount, bidAmount);
        PacketType type = auction.isReserveMet()
                ? PacketType.BID_UPDATE
                : PacketType.BID_RESERVE_NOT_MET_UPDATE;

        sessionManager.broadcastToAuction(auction.getId(), Packet.of(type, update));

        BidDTOs.BidChartPointDTO chartPoint = DTOMapper.toBidChartPoint(
                auction.getId(), bidAmount, bidderUsername, isAutoBid);
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.BID_CHART_POINT_UPDATE, chartPoint));
    }

    public void notifyAutoBidTriggered(String userId, String auctionId,
                                       long bidAmount, long newPrice,
                                       long maxBid, boolean isLeading) {
        BidDTOs.AutoBidTriggeredDTO dto = new BidDTOs.AutoBidTriggeredDTO();
        dto.setAuctionId(auctionId);
        dto.setBidAmount(bidAmount);
        dto.setNewCurrentPrice(newPrice);
        dto.setRemainingMaxBid(maxBid - bidAmount);
        dto.setNowLeading(isLeading);
        dto.setTimestamp(LocalDateTime.now());
        sessionManager.sendToUser(userId, Packet.of(PacketType.AUTO_BID_TRIGGERED_NOTIFY, dto));
    }

    public void notifyAutoBidExhausted(String userId, String auctionId,
                                       long maxBid, long currentPrice,
                                       String leadingUsername) {
        BidDTOs.AutoBidExhaustedDTO dto = new BidDTOs.AutoBidExhaustedDTO();
        dto.setAuctionId(auctionId);
        dto.setMaxBid(maxBid);
        dto.setCurrentPrice(currentPrice);
        dto.setLeadingBidderUsername(leadingUsername);
        sessionManager.sendToUser(userId, Packet.of(PacketType.AUTO_BID_EXHAUSTED_NOTIFY, dto));
    }

    // ── Auction lifecycle ─────────────────────────────────────────────────────

    public void notifyAuctionStarted(Auction auction) {
        AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, null);
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.AUCTION_STARTED_UPDATE, update));
    }

    public void notifyAuctionEnded(Auction auction) {
        log.info("Broadcast AUCTION_ENDED_UPDATE: auctionId={}", auction.getId());
        AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, null);
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.AUCTION_ENDED_UPDATE, update));
        if (auction.getItem() != null && auction.getItem().getSeller() != null) {
            persistNotification(auction.getItem().getSeller().getId(), auction.getId(),
                    "Phiên đấu giá đã kết thúc", "Phiên đấu giá của bạn đã kết thúc với winner hợp lệ.");
        }
        if (auction.getCurrentLeader() != null) {
            persistNotification(auction.getCurrentLeader().getId(), auction.getId(),
                    "Bạn đã thắng phiên đấu giá", "Chúc mừng bạn đã trở thành người thắng cuộc.");
        }
    }

    public void notifyAuctionNoWinner(Auction auction) {
        AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, "NO_WINNER");
        Packet<AuctionDTOs.AuctionUpdateDTO> packet = Packet.of(PacketType.AUCTION_NO_WINNER_UPDATE, update);
        sessionManager.broadcastToAuction(auction.getId(), packet);
        if (auction.getItem() != null && auction.getItem().getSeller() != null) {
            sessionManager.sendToUser(auction.getItem().getSeller().getId(), packet);
            persistNotification(auction.getItem().getSeller().getId(), auction.getId(),
                    "Phiên đấu giá đã kết thúc", "Phiên đấu giá không có người thắng.");
        }
    }

    public void notifyAuctionReserveNotMet(Auction auction) {
        AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, "RESERVE_NOT_MET");
        Packet<AuctionDTOs.AuctionUpdateDTO> packet = Packet.of(PacketType.AUCTION_RESERVE_NOT_MET_UPDATE, update);
        sessionManager.broadcastToAuction(auction.getId(), packet);
        if (auction.getItem() != null && auction.getItem().getSeller() != null) {
            sessionManager.sendToUser(auction.getItem().getSeller().getId(), packet);
            persistNotification(auction.getItem().getSeller().getId(), auction.getId(),
                    "Phiên đấu giá đã kết thúc", "Giá chốt chưa đạt reserve.");
        }
    }

    public void notifyAuctionCanceled(Auction auction, String reason) {
        AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, reason);
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.AUCTION_CANCELED_UPDATE, update));
    }

    public void notifyAuctionExtended(Auction auction,
                                      LocalDateTime newEndTime, int extendedBySeconds) {
        log.info("Broadcast AUCTION_EXTENDED: auctionId={}, newEndTime={}, extendedBy={}s",
                auction.getId(), newEndTime, extendedBySeconds);
        AuctionDTOs.AuctionExtendedDTO dto = new AuctionDTOs.AuctionExtendedDTO();
        dto.setAuctionId(auction.getId());
        dto.setNewEndTime(newEndTime);
        dto.setExtendedBySeconds(extendedBySeconds);
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.AUCTION_EXTENDED_NOTIFY, dto));
    }

    public void notifyAuctionUpcomingEnd(String auctionId, long remainingSeconds) {
        AuctionDTOs.AuctionUpcomingEndDTO dto = new AuctionDTOs.AuctionUpcomingEndDTO();
        dto.setAuctionId(auctionId);
        dto.setRemainingSeconds(remainingSeconds);
        sessionManager.broadcastToAuction(auctionId,
                Packet.of(PacketType.AUCTION_UPCOMING_END_NOTIFY, dto));
    }

    // ── Payment ───────────────────────────────────────────────────────────────

    public void notifyDepositRefund(String userId, String auctionId,
                                    long refundAmount, long newBalance) {
        PaymentDTOs.DepositRefundDTO dto = new PaymentDTOs.DepositRefundDTO();
        dto.setAuctionId(auctionId);
        dto.setRefundAmount(refundAmount);
        dto.setNewBalance(newBalance);
        sessionManager.sendToUser(userId, Packet.of(PacketType.DEPOSIT_REFUND_NOTIFY, dto));
    }

    public void notifyDepositForfeited(String userId, String auctionId,
                                       long forfeitedAmount, long newBalance) {
        PaymentDTOs.DepositForfeitedDTO dto = new PaymentDTOs.DepositForfeitedDTO();
        dto.setAuctionId(auctionId);
        dto.setForfeitedAmount(forfeitedAmount);
        dto.setNewBalance(newBalance);
        sessionManager.sendToUser(userId, Packet.of(PacketType.DEPOSIT_FORFEITED_NOTIFY, dto));
    }

    public void notifySecondChanceOffer(String runnerUpUserId,
                                        PaymentDTOs.SecondChanceOfferDTO offer) {
        sessionManager.sendToUser(runnerUpUserId,
                Packet.of(PacketType.SECOND_CHANCE_OFFER_NOTIFY, offer));
    }

    public void notifyPaymentCompleted(String sellerId,
                                       PaymentDTOs.PaymentResultDTO result) {
        sessionManager.sendToUser(sellerId,
                Packet.of(PacketType.PAYMENT_COMPLETED_NOTIFY, result));
    }

    public void notifyPaymentExpired(String winnerId,
                                     PaymentDTOs.PaymentExpiredDTO expired) {
        sessionManager.sendToUser(winnerId,
                Packet.of(PacketType.PAYMENT_EXPIRED_NOTIFY, expired));
    }

    public void notifySecondChanceExpired(String runnerUpUserId, String auctionId) {
        sessionManager.sendToUser(runnerUpUserId,
                Packet.of(PacketType.SECOND_CHANCE_EXPIRED_NOTIFY, auctionId));
    }

    // ── Account ───────────────────────────────────────────────────────────────

    public void notifyAccountSuspended(String userId, double currentRating,
                                       double threshold, String reason) {
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

    public void notifyQualityReportApproved(String winnerId,
                                            ReportDTOs.QualityReportResultDTO result) {
        sessionManager.sendToUser(winnerId,
                Packet.of(PacketType.QUALITY_REPORT_APPROVED_NOTIFY, result));
    }

    public void notifyQualityReportReceived(String sellerId,
                                            ReportDTOs.QualityReportDTO report) {
        sessionManager.sendToUser(sellerId,
                Packet.of(PacketType.QUALITY_REPORT_RECEIVED_NOTIFY, report));
    }

    public void notifyQualityReportRejected(String sellerId, String reportId) {
        sessionManager.sendToUser(sellerId,
                Packet.of(PacketType.QUALITY_REPORT_REJECTED_NOTIFY, reportId));
    }

    public void notifySellerRefundOverdue(String sellerId) {
        sessionManager.sendToUser(sellerId,
                Packet.of(PacketType.SELLER_REFUND_OVERDUE_NOTIFY, sellerId));
    }

    public void notifyFraudDetected(AdminDTOs.FraudDetectedDTO fraud) {
        sessionManager.broadcastToAdmins(Packet.of(PacketType.FRAUD_DETECTED_NOTIFY, fraud));
    }
}
