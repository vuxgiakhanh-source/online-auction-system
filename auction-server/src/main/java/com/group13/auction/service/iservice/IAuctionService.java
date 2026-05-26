package com.group13.auction.service.iservice;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import java.time.LocalDateTime;

/**
 * Hợp đồng quản lý vòng đời phiên đấu giá.
 */
public interface IAuctionService {

  /**
   * Tạo phiên đấu giá mới ở trạng thái OPEN.
   * Seller cung cấp reservePrice (giá sàn bí mật) ngay khi tạo.
   *
   * @param seller       seller tạo phiên
   * @param item         sản phẩm đưa ra đấu giá
   * @param startTime    thời điểm bắt đầu
   * @param endTime      thời điểm kết thúc
   * @param reservePrice giá sàn bí mật (> 0)
   * @return Auction mới
   */
  Auction createAuction(
          NormalUser seller,
          Item item,
          LocalDateTime startTime,
          LocalDateTime endTime,
          long reservePrice);

  /**
   * Bắt đầu phiên: OPEN -> RUNNING.
   *
   * @param auction phiên cần bắt đầu
   */
  void startAuction(Auction auction);

  /**
   * Đóng phiên khi hết giờ: RUNNING -> FINISHED / CANCELED.
   *
   * @param auction phiên cần đóng
   */
  void closeAuction(Auction auction);

  /**
   * Đánh dấu thanh toán thành công: FINISHED -> PAID.
   *
   * @param auction phiên cần đánh dấu
   */
  void markAsPaid(Auction auction);

  /**
   * SYSTEM tự động huỷ phiên đấu giá (no-winner / reserve-not-met / system-error).
   * Log được ghi vào SystemAdmin.
   *
   * @param auction phiên cần huỷ
   * @param reason  lý do huỷ
   */
  void cancelAuction(Auction auction, Admin.CancelReason reason);

  /**
   * Admin STAFF huỷ phiên đấu giá.
   * Log được ghi vào cả SystemAdmin (audit trail) lẫn staff cụ thể.
   *
   * @param staff   admin STAFF đang xử lý
   * @param auction phiên cần huỷ
   * @param reason  lý do huỷ
   * @throws IllegalArgumentException nếu {@code staff} là SystemAdmin
   */
  void cancelAuction(Admin staff, Auction auction, Admin.CancelReason reason);

  /**
   * SystemAdmin auto duyệt yêu cầu hủy phiên của Seller
   * (phiên chỉ được phép ở trạng thái OPEN).
   *
   * @param auction phiên auction cần hủy
   */
  void autoHandleCancelRequest(Auction auction);

  /**
   * Thông báo trước khi phiên bắt đầu (5–10 phút). Gọi từ scheduler.
   *
   * @param auction phiên sắp bắt đầu
   */
  void notifyUpcoming(Auction auction);

  /**
   * Đăng ký observer theo dõi phiên.
   *
   * @param auctionId id phiên muốn theo dõi
   * @param observer  observer cần thêm
   */
  void addObserver(String auctionId, AuctionObserver observer);

  /**
   * Gỡ observer in-memory của user khỏi phiên (sau leave/cancel participation).
   */
  void removeObserversForUser(String auctionId, String userId);

  void notify(Auction auction, AuctionEvent.AuctionEventType type,
              NormalUser bidder, long amount);

  void notify(Auction auction, AuctionEvent.AuctionEventType type,
              NormalUser bidder, long amount, String message);
}