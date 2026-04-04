package com.group13.auction.service;

import com.group13.auction.exception.AuctionClosedException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.Auction.AuctionStatus;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.Bidder;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.user.Seller;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import java.time.LocalDateTime;

/**
 * Xử lý nghiệp vụ liên quan đến Auction.
 * Seller là chủ thể quyết định tạo phiên; sau khi tạo, phiên được đăng ký vào
 * {@link com.group13.auction.manager.AuctionManager} để tra cứu (in-memory).
 * TODO: inject AuctionDAO để persist xuống DB.
 */
public class AuctionService {

  private final UserService userService;

  public AuctionService(UserService userService) {
    this.userService = userService;
  }

  /**
   * Tạo phiên đấu giá mới ở trạng thái OPEN.
   * Seller cần rating >= 2.0.
   * Có thể set lịch trước nhiều ngày.
   * TODO: auctionDAO.save(auction).
   *
   * @param seller    seller tạo phiên
   * @param item      sản phẩm đưa ra đấu giá
   * @param startTime thời điểm bắt đầu
   * @param endTime   thời điểm kết thúc
   * @return Auction mới ở trạng thái OPEN
   * @throws IllegalStateException    nếu seller không đủ điều kiện
   * @throws IllegalArgumentException nếu endTime trước startTime
   */
  public Auction createAuction(Seller seller, Item item,
      LocalDateTime startTime, LocalDateTime endTime) {
    if (!userService.canSellerCreateAuction(seller)) {
      throw new IllegalStateException(
          "Seller không đủ điều kiện tạo auction (rating < 2.0 hoặc bị ban).");
    }
    if (!endTime.isAfter(startTime)) {
      throw new IllegalArgumentException("endTime phải sau startTime.");
    }
    Auction auction = Auction.create(item, startTime, endTime);
    seller.addAuctionId(auction.getId());
    AuctionManager.getInstance().registerAuction(auction);
    System.out.printf("[AUCTION SERVICE] Tạo auction: %s%n", auction.getId());
    // TODO: auctionDAO.save(auction)
    return auction;
  }

  /**
   * Bắt đầu phiên: OPEN → RUNNING.
   * Thông báo tất cả observer.
   * TODO: auctionDAO.update(auction).
   *
   * @param auction phiên cần bắt đầu
   * @throws IllegalStateException nếu phiên không ở OPEN
   */
  public void startAuction(Auction auction) {
    if (auction.getStatus() != AuctionStatus.OPEN) {
      throw new IllegalStateException(
          "Phiên không ở trạng thái OPEN: " + auction.getStatus());
    }
    auction.setStatus(AuctionStatus.RUNNING);
    notify(auction, AuctionEvent.AuctionEventType.AUCTION_STARTED, null, 0);
    System.out.printf("[AUCTION SERVICE] Phiên bắt đầu: %s%n", auction.getId());
    // TODO: auctionDAO.update(auction)
  }

  /**
   * Đóng phiên khi hết giờ: RUNNING → FINISHED / CANCELED.
   * Tạo AuctionWinner nếu có người thắng.
   * TODO: auctionDAO.update(auction).
   *
   * @param auction phiên cần đóng
   * @throws IllegalStateException nếu phiên không ở RUNNING
   */
  public void closeAuction(Auction auction) {
    if (auction.getStatus() != AuctionStatus.RUNNING) {
      throw new IllegalStateException(
          "Phiên không ở trạng thái RUNNING: " + auction.getStatus());
    }
    if (auction.getCurrentLeader() == null) {
      auction.setStatus(AuctionStatus.CANCELED);
      notify(auction, AuctionEvent.AuctionEventType.AUCTION_ENDED, null, 0);
      System.out.println("[AUCTION SERVICE] Phiên đóng — không có winner.");
    } else {
      Bidder winner = (Bidder) auction.getCurrentLeader();
      auction.setStatus(AuctionStatus.FINISHED);
      auction.setWinner(
          AuctionWinner.create(winner, auction.getId(), auction.getCurrentPrice()));
      notify(auction, AuctionEvent.AuctionEventType.AUCTION_ENDED,
          winner, auction.getCurrentPrice());
      System.out.printf("[AUCTION SERVICE] Winner: %s | Giá: %.0f%n",
          winner.getUsername(), auction.getCurrentPrice());
    }
    // TODO: auctionDAO.update(auction)
  }

  /**
   * Đánh dấu thanh toán thành công: FINISHED → PAID.
   * TODO: auctionDAO.update(auction).
   *
   * @param auction phiên cần đánh dấu
   * @throws IllegalStateException nếu phiên không ở FINISHED
   */
  public void markAsPaid(Auction auction) {
    if (auction.getStatus() != AuctionStatus.FINISHED) {
      throw new IllegalStateException(
          "Phiên không ở trạng thái FINISHED: " + auction.getStatus());
    }
    auction.setStatus(AuctionStatus.PAID);
    notify(auction, AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
        (Bidder) auction.getCurrentLeader(), auction.getCurrentPrice());
    System.out.println("[AUCTION SERVICE] Giao dịch hoàn tất — PAID.");
    // TODO: auctionDAO.update(auction)
  }

  /**
   * Admin huỷ phiên đấu giá.
   * TODO: auctionDAO.update(auction).
   *
   * @param admin   admin thực hiện
   * @param auction phiên cần huỷ
   * @param reason  lý do huỷ
   */
  public void cancelAuction(Admin admin, Auction auction,
      Admin.CancelReason reason) {
    auction.setStatus(AuctionStatus.CANCELED);
    notify(auction, AuctionEvent.AuctionEventType.AUCTION_CANCELED, null, 0);
    String log = String.format("[AUCTION SERVICE] %s huỷ phiên %s | Lý do: %s",
        admin.getUsername(), auction.getId(), reason);
    admin.addActionLog(log);
    System.out.println(log);
    // TODO: auctionDAO.update(auction)
  }

  /**
   * Thông báo trước khi phiên bắt đầu (5-10 phút).
   * Gọi từ scheduler.
   *
   * @param auction phiên sắp bắt đầu
   */
  public void notifyUpcoming(Auction auction) {
    notify(auction, AuctionEvent.AuctionEventType.AUCTION_UPCOMING, null, 0);
    System.out.printf("[AUCTION SERVICE] Phiên sắp bắt đầu: %s%n",
        auction.getId());
  }

  /**
   * Đăng ký observer theo dõi phiên.
   *
   * @param auction  phiên muốn theo dõi
   * @param observer observer cần thêm
   */
  public void addObserver(Auction auction, AuctionObserver observer) {
    auction.addObserver(observer);
  }

  /**
   * Thông báo tất cả observer — gọi đúng method theo loại event.
   * Giảm lặp code (DRY) bằng cách tập trung notify tại đây.
   */
  public void notify(Auction auction, AuctionEvent.AuctionEventType type,
      Bidder bidder, double amount) {
    AuctionEvent event = new AuctionEvent(type, auction, bidder, amount);
    for (AuctionObserver observer : auction.getObservers()) {
      if (type == AuctionEvent.AuctionEventType.BID_PLACED) {
        observer.onBidPlaced(event);
      } else {
        observer.onAuctionEnded(event);
      }
    }
    AuctionManager.getInstance().notifyGlobalObservers(event);
  }
}