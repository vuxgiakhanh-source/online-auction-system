package com.group13.auction.service;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.Bidder;
import com.group13.auction.model.user.Seller;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import java.time.LocalDateTime;

/**
 * Hợp đồng quản lý vòng đời phiên đấu giá.
 */
public interface IAuctionService {

  /**
   * Tạo phiên đấu giá mới ở trạng thái OPEN.
   *
   * @param seller    seller tạo phiên
   * @param item      sản phẩm đưa ra đấu giá
   * @param startTime thời điểm bắt đầu
   * @param endTime   thời điểm kết thúc
   * @return Auction mới
   */
  Auction createAuction(Seller seller, Item item,
      LocalDateTime startTime, LocalDateTime endTime);

  /**
   * Bắt đầu phiên: OPEN → RUNNING.
   *
   * @param auction phiên cần bắt đầu
   */
  void startAuction(Auction auction);

  /**
   * Đóng phiên khi hết giờ: RUNNING → FINISHED / CANCELED.
   *
   * @param auction phiên cần đóng
   */
  void closeAuction(Auction auction);

  /**
   * Đánh dấu thanh toán thành công: FINISHED → PAID.
   *
   * @param auction phiên cần đánh dấu
   */
  void markAsPaid(Auction auction);

  /**
   * Admin huỷ phiên đấu giá.
   *
   * @param admin   admin thực hiện
   * @param auction phiên cần huỷ
   * @param reason  lý do huỷ
   */
  void cancelAuction(Admin admin, Auction auction, Admin.CancelReason reason);

  /**
   * Thông báo trước khi phiên bắt đầu (5-10 phút).
   * Gọi từ scheduler.
   *
   * @param auction phiên sắp bắt đầu
   */
  void notifyUpcoming(Auction auction);

  /**
   * Đăng ký observer theo dõi phiên.
   *
   * @param auction  phiên muốn theo dõi
   * @param observer observer cần thêm
   */
  void addObserver(Auction auction, AuctionObserver observer);
  void notify(Auction auction, AuctionEvent.AuctionEventType type,
              Bidder bidder, double amount);
}