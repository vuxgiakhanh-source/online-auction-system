package com.group13.auction.service;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.model.bid.FinancialTransaction;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.group13.auction.model.user.Admin.CancelReason.SELLER_REQUEST;

/**
 * Quản lý vòng đời phiên đấu giá.
 * Xử lý nghiệp vụ liên quan đến Auction.
 * Seller là chủ thể quyết định tạo phiên; sau khi tạo, phiên được đăng ký vào
 * {@link com.group13.auction.manager.AuctionManager} để tra cứu (in-memory).
 * Nhận {@link IRatingService} qua constructor — không new cứng (DIP).
 * Đã thực hiện TODO: inject AuctionDAO để persist xuống DB.
 */
public class AuctionService implements IAuctionService {

  private static final Logger log = LoggerFactory.getLogger(AuctionService.class);

  private final IRatingService ratingService;
  private final SystemAdmin system = SystemAdmin.getInstance();
  private final AuctionDAO auctionDAO;
  private final FinancialTransactionDAO financialTransactionDAO;
  private final SystemBank systemBank = SystemBank.getInstance();

  /**
   * Map<auctionId, observers> - tập trung quản lý observer.
   */
  private final Map<String, List<AuctionObserver>> observersMap = new ConcurrentHashMap<>();

  public AuctionService(IRatingService ratingService, AuctionDAO auctionDAO) {
    this(ratingService, auctionDAO, new FinancialTransactionDAO());
  }

  public AuctionService(IRatingService ratingService, AuctionDAO auctionDAO,
                        FinancialTransactionDAO financialTransactionDAO) {
    this.ratingService = ratingService;
    this.auctionDAO = auctionDAO;
    this.financialTransactionDAO = financialTransactionDAO;
  }

  /**
   * Tạo phiên đấu giá mới ở trạng thái OPEN.
   * Seller cần rating >= 2.0, ACTIVE và có role SELLER.
   * Reserve price strategy (tức giá Seller muốn) phải được cung cấp.
   * Đã thực hiện TODO: auctionDAO.save(auction).
   *
   * @param seller seller tạo phiên
   * @param item sản phẩm đưa ra đấu giá
   * @param startTime thời điểm bắt đầu
   * @param endTime thời điểm kết thúc
   * @param reservePrice giá sàn bí mật của Seller (>0)
   * @return Auction mới ở trạng thái OPEN
   * @throws IllegalStateException nếu seller không đủ điều kiện
   * @throws IllegalArgumentException nếu endTime trước startTime hoặc thiếu role SELLER
   */
  @Override
  public Auction createAuction(
          NormalUser seller,
          Item item,
          LocalDateTime startTime,
          LocalDateTime endTime,
          long reservePrice) {

    if (!seller.hasRole(User.UserRole.SELLER)) {
      throw new IllegalArgumentException(
              "User chưa có role Seller. Cần được hệ thống phê duyệt trước.");
    }
    if (!ratingService.canSellerCreateAuction(seller)) {
      throw new IllegalStateException(
              "Seller không đủ điều kiện tạo auction (rating < 2.0 hoặc bị ban).");
    }
    if (!endTime.isAfter(startTime)) {
      throw new IllegalArgumentException("endTime phải sau startTime.");
    }
    if (reservePrice <= 0) {
      throw new IllegalArgumentException("reservePrice phải lớn hơn 0.");
    }

    Auction auction = Auction.create(item, startTime, endTime, reservePrice);

    if (!auctionDAO.createAuction(auction)) {
      throw new IllegalStateException("Không thể lưu phiên đấu giá xuống DB.");
    }

    seller.addAuctionId(auction.getId());
    AuctionManager.getInstance().registerAuction(auction);
    log.info("Tạo auction: auctionId={} reserve={}", auction.getId(), reservePrice);

    return auction;
  }

  /**
   * Bắt đầu phiên: OPEN -> RUNNING.
   * Thông báo tất cả observer.
   * Đã thực hiện TODO: auctionDAO.update(auction).
   * Scheduler tự động gọi hàm này: dùng ScheduledExecutorService ở tầng infrastructure
   * (ngoài phạm vi Service) quét định kỳ các Auction có status=OPEN và startTime <= now().
   *
   * @param auction phiên cần bắt đầu
   * @throws IllegalStateException nếu phiên không ở OPEN
   */
  @Override
  public void startAuction(Auction auction) {
    auction.transitionToRunning();
    notify(auction, AuctionEvent.AuctionEventType.AUCTION_STARTED, null, 0L);
    log.info("Phiên bắt đầu: auctionId={}", auction.getId());

    // Thực hiện TODO: auctionDAO.update(auction)
    auctionDAO.updateAuctionStatus(auction.getId(), auction.getStatus().name());
  }


  /**
   * Đóng phiên khi hết giờ.
   *
   * <p>1 trong 2 trường hợp :
   * <ol>
   * <li>Không có currentLeader hoặc chưa đạt reserve -> SYSTEM auto-cancel.</li>
   * <li>Có leader và đạt reserve -> FINISHED (tạo AuctionWinner).</li>
   * </ol>
   *
   * Cả hai trường hợp auto-cancel bằng SystemAdmin.
   * Đã thực hiện TODO: auctionDAO.update(auction).
   * Scheduler tự động gọi hàm này: dùng ScheduledExecutorService ở tầng infrastructure
   * quét định kỳ các Auction có status=RUNNING và endTime <= now().
   *
   * @param auction phiên cần đóng
   * @throws IllegalStateException nếu phiên không ở RUNNING
   */
  @Override
  public void closeAuction(Auction auction) {
    if (auction.getStatus() != Auction.AuctionStatus.RUNNING) {
      throw new IllegalStateException("Phiên đang không ở trạng thái RUNNING - không thể đóng.");
    }
    if (auction.getCurrentLeader() == null) {
      // TH1.1: không có ai đặt giá -> SYSTEM auto-cancel
      notify(auction, AuctionEvent.AuctionEventType.AUCTION_NO_WINNER, null, 0L);
      cancelAuction(auction, Admin.CancelReason.NO_WINNER);
      log.info("Phiên đóng - không có ai đặt giá: auctionId={}", auction.getId());

    } else if (!auction.isReserveMet()) {
      // TH1.2: có leader nhưng chưa đạt reserve -> SYSTEM auto-cancel
      NormalUser leader = auction.getCurrentLeader();
      notify(auction, AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
              leader, auction.getCurrentPrice());
      cancelAuction(auction, Admin.CancelReason.RESERVE_NOT_MET);
      log.info("Phiên đóng - reserve chưa đạt: auctionId={} highestPrice={} reserve={}",
              auction.getId(), auction.getCurrentPrice(), auction.getReservePrice());

    } else {
      // TH2: reserve met, có winner
      NormalUser winner = auction.getCurrentLeader();

      long depositPaid = financialTransactionDAO.findLockedDepositAmount(
              winner.getId(), auction.getId());
      if (depositPaid <= 0) {
        depositPaid = auction.getItem().getStartingPrice() * 3 / 10;
      }

      AuctionWinner auctionWinner = AuctionWinner.create(
              winner, auction.getId(), auction.getCurrentPrice(), depositPaid, false);
      auction.setWinner(auctionWinner);
      auction.transitionToClose(true);

      recordWinnerDepositHeldInBank(winner, depositPaid, auction.getId());

      notify(auction, AuctionEvent.AuctionEventType.AUCTION_ENDED,
              winner, auction.getCurrentPrice());
      log.info("Winner: auctionId={} winner={} price={}",
              auction.getId(), winner.getUsername(), auction.getCurrentPrice());
    }

    // Đã thực hiện TODO: auctionDAO.update(auction)
    auctionDAO.updateAuctionResult(auction);
  }

  /**
   * Đánh dấu thanh toán thành công: FINISHED -> PAID.
   * Tiền đã vào SystemBank (FUNDS_HELD); kích hoạt đếm 7 ngày "nhận hàng".
   * Đã thực hiện TODO: auctionDAO.update(auction).
   *
   * @param auction phiên cần đánh dấu
   * @throws IllegalStateException nếu phiên không ở FINISHED
   */
  @Override
  public void markAsPaid(Auction auction) {
    auction.transitionToPaid();

    // Kích hoạt deadline nhận hàng 7 ngày
    AuctionWinner auctionWinner = auction.getWinner();
    if (auctionWinner != null) {
      auctionWinner.markFundsHeld();
      // TODO: [DB] auctionWinnerDAO.updateFundsHeld(auctionWinner.getId(), ...)
    }

    notify(auction, AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
            auction.getCurrentLeader(), auction.getCurrentPrice());
    log.info("Giao dịch hoàn tất - PAID: auctionId={}", auction.getId());

    // Đã thực hiện TODO: auctionDAO.update(auction)
    auctionDAO.updateAuctionStatus(auction.getId(), auction.getStatus().name());
    cleanupObservers(auction.getId());
  }

  /**
   * SYSTEM tự động huỷ phiên - không cần staff cụ thể.
   * Log được ghi vào {@link SystemAdmin}.
   * Khi no-winner, reserve-not-met, system-error.
   * Đã thực hiện TODO: auctionDAO.update(auction).
   *
   * @param auction phiên cần huỷ
   * @param reason lý do huỷ
   */
  @Override
  public void cancelAuction(Auction auction, Admin.CancelReason reason) {
    auction.transitionToCancel();
    String log = String.format("[SYSTEM AUTO-CANCEL] Phiên %s bị hủy | Lý do: %s",
            auction.getId(), reason);
    system.addActionLog(log);
    AuctionService.log.info("SYSTEM AUTO-CANCEL: auctionId={} reason={}", auction.getId(), reason);

    // Persist DB trước khi notify để đảm bảo nhất quán
    auctionDAO.updateAuctionStatus(auction.getId(), auction.getStatus().name());

    notify(auction, AuctionEvent.AuctionEventType.AUCTION_CANCELED, null, 0L);

    // Notify staff về việc hủy
    AuctionManager.getInstance().notifyStaffObservers(
            new AuctionEvent(AuctionEvent.AuctionEventType.AUCTION_CANCELED, auction, null, 0L));

    // Đã thực hiện TODO: auctionDAO.update(auction)
    auctionDAO.updateAuctionStatus(auction.getId(), auction.getStatus().name());
    cleanupObservers(auction.getId());
  }

  /**
   * Admin STAFF huỷ phiên sau khi trực tiếp điều tra.
   * Log được ghi vào cả {@link SystemAdmin} (audit trail) lẫn {@code staff}.
   * Dùng khi có SELLER_REQUEST hoặc cần người cụ thể chịu trách nhiệm.
   * Đã thực hiện TODO: auctionDAO.update(auction).
   *
   * @param staff admin STAFF đang xử lý (không được là SystemAdmin)
   * @param auction phiên cần huỷ
   * @param reason lý do huỷ
   * @throws IllegalArgumentException nếu {@code staff} là SystemAdmin
   * (SystemAdmin dùng overload không tham số staff)
   */
  @Override
  public void cancelAuction(Admin staff, Auction auction, Admin.CancelReason reason) {
    if (staff.isSystem()) {
      throw new IllegalArgumentException(
              "SystemAdmin không dùng overload này - gọi autoCancelAuction(auction, reason).");
    }
    auction.transitionToCancel();
    String staffLog = String.format("[STAFF CANCEL] %s hủy phiên %s | Lý do: %s",
            staff.getUsername(), auction.getId(), reason);
    staff.addActionLog(staffLog);
    log.info("STAFF CANCEL: staff={} auctionId={} reason={}",
            staff.getUsername(), auction.getId(), reason);

    String auditLog = String.format("[AUDIT] Staff %s hủy phiên %s | Lý do: %s",
            staff.getUsername(), auction.getId(), reason);
    system.addActionLog(auditLog);
    log.info("AUDIT: staff={} auctionId={} reason={}",
            staff.getUsername(), auction.getId(), reason);

    // Đã thực hiện TODO: auctionDAO.update(auction)
    auctionDAO.updateAuctionStatus(auction.getId(), auction.getStatus().name());
    cleanupObservers(auction.getId());
  }

  /**
   * SystemAdmin auto duyệt yêu cầu hủy phiên của Seller,
   * với điều kiện phiên chỉ đang ở trạng thái OPEN
   * Chỉ gọi khi Seller request hủy
   * approve -> hủy
   * reject -> phiên tiếp tục RUNNING
   *
   * @param auction phiên auction cần hủy
   * <p>Lý do hủy (ở đây là Seller Request)
   */
  public void autoHandleCancelRequest(Auction auction) {
    cancelAuction(auction, SELLER_REQUEST);
  }

  /**
   * Thông báo trước khi phiên bắt đầu (5-10 phút).
   * Gọi từ scheduler.
   * Chưa hoàn thiện (có thể bỏ nếu không đủ time)
   *
   * @param auction phiên sắp bắt đầu
   */
  @Override
  public void notifyUpcoming(Auction auction) {
    notify(auction, AuctionEvent.AuctionEventType.AUCTION_UPCOMING, null, 0L);
  }

  /**
   * Đăng ký observer theo dõi phiên.
   *
   * @param auctionId phiên muốn theo dõi
   * @param observer observer cần thêm
   */
  @Override
  public void addObserver(String auctionId, AuctionObserver observer) {
    if (auctionId == null || observer == null) return;
    // computeIfAbsent trả về list hiện có hoặc list mới — atomic, tránh TOCTOU giữa
    // computeIfAbsent và get() tách biệt.
    List<AuctionObserver> observers =
            observersMap.computeIfAbsent(auctionId, k -> new CopyOnWriteArrayList<>());
    synchronized (observers) {
      if (!observers.contains(observer)) {
        observers.add(observer);
      }
    }
  }

  /** Lấy danh sách observer của một phiên (chỉ đọc). */
  public List<AuctionObserver> getObservers(String auctionId) {
    List<AuctionObserver> list = observersMap.get(auctionId);
    if (list == null) return Collections.emptyList();
    return Collections.unmodifiableList(list);
  }


  @Override
  public void notify(Auction auction, AuctionEvent.AuctionEventType type,
                     NormalUser bidder, long amount) {
    notify(auction, type, bidder, amount, null);
  }

  @Override
  public void notify(Auction auction, AuctionEvent.AuctionEventType type,
                     NormalUser bidder, long amount, String message) {
    AuctionEvent event = new AuctionEvent(type, auction, bidder, amount, message);

    // Notify observers cụ thể của phiên
    List<AuctionObserver> observers = observersMap.getOrDefault(
            auction.getId(), Collections.emptyList());
    for (AuctionObserver observer : observers) {
      if (type == AuctionEvent.AuctionEventType.BID_PLACED
              || type == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
        observer.onBidPlaced(event);
      } else {
        observer.onAuctionEnded(event);
      }
    }
    // Global (SystemAdmin) và Staff observers
    AuctionManager.getInstance().notifyGlobalObservers(event);
    AuctionManager.getInstance().notifyStaffObservers(event);
  }

  /**
   * Ghi audit trail cọc winner vào DB trước khi cộng vào SystemBank (escrow FUNDS_HELD).
   * Nếu persist thất bại → không gọi {@link SystemBank#receive(long)}.
   */
  private void recordWinnerDepositHeldInBank(NormalUser winner, long depositPaid, String auctionId) {
    FinancialTransaction tx = FinancialTransaction.create(
            winner.getId(), "SYSTEM_BANK", depositPaid,
            FinancialTransaction.TransactionType.PAYMENT_FROM_WINNER, auctionId);
    if (!financialTransactionDAO.saveTransaction(tx)) {
      throw new IllegalStateException(
              "Không thể ghi nhận cọc winner vào audit trail (auctionId=" + auctionId + ").");
    }
    systemBank.receive(depositPaid);
    log.info("Cọc của winner giữ tại SystemBank (FUNDS_HELD): auctionId={} winner={} deposit={}",
            auctionId, winner.getUsername(), depositPaid);
  }

  // Sau khi notify ended/canceled thành công
  private void cleanupObservers(String auctionId) {
    List<AuctionObserver> observers = observersMap.remove(auctionId);
    if (observers != null) {
      int count = observers.size(); // capture before clear()
      observers.clear();
      log.info("Cleaned up {} observers for auction {}", count, auctionId);
    }
  }
}