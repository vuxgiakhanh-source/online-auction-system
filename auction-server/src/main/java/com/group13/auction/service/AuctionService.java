package com.group13.auction.service;

import com.group13.auction.dao.AuctionDAO;
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
import com.group13.auction.service.serviceInterface.IAuctionService;
import com.group13.auction.service.serviceInterface.IRatingService;
import com.group13.auction.strategy.ReservePriceStrategy;
import java.time.LocalDateTime;

/**
 * Quản lý vòng đời phiên đấu giá.
 * Xử lý nghiệp vụ liên quan đến Auction.
 * Seller là chủ thể quyết định tạo phiên; sau khi tạo, phiên được đăng ký vào
 * {@link com.group13.auction.manager.AuctionManager} để tra cứu (in-memory).
 * Nhận {@link IRatingService} qua constructor — không new cứng (DIP).
 * Đã thực hiện TODO: inject AuctionDAO để persist xuống DB.
 */
public class AuctionService implements IAuctionService {

  private final IRatingService ratingService;
  private final SystemAdmin system = SystemAdmin.getInstance();
  private final AuctionDAO auctionDAO; // Thực hiện TODO: inject AuctionDAO

  public AuctionService(IRatingService ratingService, AuctionDAO auctionDAO) {
    this.ratingService = ratingService;
    this.auctionDAO = auctionDAO;
  }

  /**
   * Tạo phiên đấu giá mới ở trạng thái OPEN.
   * Seller cần rating >= 3.0 và có role SELLER.
   * Reserve price strategy (tức giá Seller muốn) phải được cung cấp.
   * Có thể set lịch trước nhiều ngày.
   * Đã thực hiện TODO: auctionDAO.save(auction).
   *
   * @param seller seller tạo phiên
   * @param item sản phẩm đưa ra đấu giá
   * @param startTime thời điểm bắt đầu
   * @param endTime thời điểm kết thúc
   * @param reserveStrategy reserve price strategy
   * @return Auction mới ở trạng thái OPEN
   * @throws IllegalStateException nếu seller không đủ điều kiện
   * @throws IllegalArgumentException nếu endTime trước startTime hoặc thiếu role SELLER
   */
  @Override
  public Auction createAuction(NormalUser seller, Item item,
                               LocalDateTime startTime, LocalDateTime endTime,
                               ReservePriceStrategy reserveStrategy) {
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
    if (reserveStrategy == null) {
      throw new IllegalArgumentException(
              "ReservePriceStrategy không được null — bắt buộc thiết lập khi tạo auction.");
    }

    Auction auction = Auction.create(item, startTime, endTime, reserveStrategy);
    seller.addAuctionId(auction.getId());
    AuctionManager.getInstance().registerAuction(auction);
    System.out.printf("[AUCTION SERVICE] Tạo auction: %s (reserve: %.0f)%n",
            auction.getId(), reserveStrategy.getReservePrice());

    // Đã thực hiện TODO: auctionDAO.save(auction)
    auctionDAO.createAuction(auction);

    return auction;
  }

  /**
   * Bắt đầu phiên: OPEN -> RUNNING.
   * Thông báo tất cả observer.
   * Đã thực hiện TODO: auctionDAO.update(auction).
   *
   * @param auction phiên cần bắt đầu
   * @throws IllegalStateException nếu phiên không ở OPEN
   */
  @Override
  public void startAuction(Auction auction) {
    // State object tự ném IllegalStateException nếu không hợp lệ
    auction.transitionToRunning();
    notify(auction, AuctionEvent.AuctionEventType.AUCTION_STARTED, null, 0);
    System.out.printf("[AUCTION SERVICE] Phiên bắt đầu: %s%n", auction.getId());

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
   * Cả hai trường hợp auto-cancel đều ghi log vào SystemAdmin.
   * Đã thực hiện TODO: auctionDAO.update(auction).
   *
   * @param auction phiên cần đóng
   * @throws IllegalStateException nếu phiên không ở RUNNING
   */
  @Override
  public void closeAuction(Auction auction) {
    if (auction.getCurrentLeader() == null) {
      // TH1.1: không có ai đặt giá -> SYSTEM auto-cancel
      notify(auction, AuctionEvent.AuctionEventType.AUCTION_NO_WINNER, null, 0);
      cancelAuction(auction, Admin.CancelReason.NO_WINNER);
      System.out.println("[AUCTION SERVICE] Phiên đóng - không có ai đặt giá.");

    } else if (!auction.isReserveMet()) {
      // TH1.2: có leader nhưng chưa đạt reserve -> SYSTEM auto-cancel
      NormalUser leader = auction.getCurrentLeader();
      notify(auction, AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
              leader, auction.getCurrentPrice());
      cancelAuction(auction, Admin.CancelReason.RESERVE_NOT_MET);
      System.out.printf(
              "[AUCTION SERVICE] Phiên đóng - giá cao nhất %.0f chưa đạt reserve %.0f.%n",
              auction.getCurrentPrice(),
              auction.getReserveStrategy().getReservePrice());

    } else {
      // TH2: reserve met, có winner
      NormalUser winner = auction.getCurrentLeader();
      double depositPaid = auction.getItem().getStartingPrice() * 0.3;
      auction.setWinner(
              AuctionWinner.create(winner, auction.getId(),
                      auction.getCurrentPrice(), depositPaid));
      auction.transitionToClose(true);
      notify(auction, AuctionEvent.AuctionEventType.AUCTION_ENDED,
              winner, auction.getCurrentPrice());
      System.out.printf("[AUCTION SERVICE] Winner: %s | Giá: %.0f%n",
              winner.getUsername(), auction.getCurrentPrice());
    }

    // Đã thực hiện TODO: auctionDAO.update(auction)
    auctionDAO.updateAuctionResult(auction);
  }

  /**
   * Đánh dấu thanh toán thành công: FINISHED → PAID.
   * Đã thực hiện TODO: auctionDAO.update(auction).
   *
   * @param auction phiên cần đánh dấu
   * @throws IllegalStateException nếu phiên không ở FINISHED
   */
  @Override
  public void markAsPaid(Auction auction) {
    auction.transitionToPaid();
    notify(auction, AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
            auction.getCurrentLeader(), auction.getCurrentPrice());
    System.out.println("[AUCTION SERVICE] Giao dịch hoàn tất — PAID.");

    // Đã thực hiện TODO: auctionDAO.update(auction)
    auctionDAO.updateAuctionStatus(auction.getId(), auction.getStatus().name());
  }

  /**
   * SYSTEM tự động huỷ phiên — không cần staff cụ thể.
   * Log được ghi vào {@link SystemAdmin}.
   * Khi: no-winner, reserve-not-met, system-error.
   * Đã thực hiện TODO: auctionDAO.update(auction).
   *
   * @param auction phiên cần huỷ
   * @param reason lý do huỷ
   */
  @Override
  public void cancelAuction(Auction auction, Admin.CancelReason reason) {
    system.autoCancelAuction(auction, reason);
    notify(auction, AuctionEvent.AuctionEventType.AUCTION_CANCELED, null, 0);

    // Notify staff về việc hủy
    AuctionManager.getInstance().notifyStaffObservers(
            new AuctionEvent(AuctionEvent.AuctionEventType.AUCTION_CANCELED, auction, null, 0));

    // Đã thực hiện TODO: auctionDAO.update(auction)
    auctionDAO.updateAuctionStatus(auction.getId(), auction.getStatus().name());
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
    if (staff.isMaster()) {
      throw new IllegalArgumentException(
              "SystemAdmin không dùng overload này — gọi cancelAuction(auction, reason).");
    }
    system.cancelAuctionByStaff(staff, auction, reason);

    // Đã thực hiện TODO: auctionDAO.update(auction)
    auctionDAO.updateAuctionStatus(auction.getId(), auction.getStatus().name());
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
    notify(auction, AuctionEvent.AuctionEventType.AUCTION_UPCOMING, null, 0);
  }

  /**
   * Đăng ký observer theo dõi phiên.
   *
   * @param auction phiên muốn theo dõi
   * @param observer observer cần thêm
   */
  @Override
  public void addObserver(Auction auction, AuctionObserver observer) {
    auction.addObserver(observer);
  }

  @Override
  public void notify(Auction auction, AuctionEvent.AuctionEventType type,
                     NormalUser bidder, double amount) {
    notify(auction, type, bidder, amount, null);
  }

  @Override
  public void notify(Auction auction, AuctionEvent.AuctionEventType type,
                     NormalUser bidder, double amount, String message) {
    AuctionEvent event = new AuctionEvent(type, auction, bidder, amount, message);
    // Notify observers cho auction cụ thể
    for (AuctionObserver observer : auction.getObservers()) {
      if (type == AuctionEvent.AuctionEventType.BID_PLACED
              || type == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
        observer.onBidPlaced(event);
      } else {
        observer.onAuctionEnded(event);
      }
    }
    // Truyền tin nhắn tới global observers (SystemAdmin)
    AuctionManager.getInstance().notifyGlobalObservers(event);
    // Truyền tin nhắn tới staff observers (chỉ event liên quan)
    AuctionManager.getInstance().notifyStaffObservers(event);
  }
}