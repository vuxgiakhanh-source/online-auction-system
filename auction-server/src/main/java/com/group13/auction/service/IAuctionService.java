// ════════════════════════════════════════════════════════════════════════════
// FILE: com/group13/auction/service/IAuctionService.java
// ════════════════════════════════════════════════════════════════════════════

package com.group13.auction.service;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.strategy.ReservePriceStrategy;
import java.time.LocalDateTime;

/**
 * Hợp đồng quản lý vòng đời phiên đấu giá.
 */
public interface IAuctionService {

  /**
   * Tạo phiên đấu giá mới ở trạng thái OPEN.
   * Seller BẮT BUỘC cung cấp reserveStrategy ngay khi tạo.
   *
   * @param seller          seller tạo phiên
   * @param item            sản phẩm đưa ra đấu giá
   * @param startTime       thời điểm bắt đầu
   * @param endTime         thời điểm kết thúc
   * @param reserveStrategy reserve price strategy (BẮT BUỘC)
   * @return Auction mới
   */
  Auction createAuction(NormalUser seller, Item item,
                        LocalDateTime startTime, LocalDateTime endTime,
                        ReservePriceStrategy reserveStrategy);

  /**
   * Bắt đầu phiên: OPEN → RUNNING.
   *
   * @param auction phiên cần bắt đầu
   */
  void startAuction(Auction auction);

  /**
   * Đóng phiên khi hết giờ: RUNNING → FINISHED / CANCELED / RESERVE_NOT_MET.
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
   * SYSTEM tự động huỷ phiên đấu giá (no-winner / reserve-not-met / system-error).
   * Log được ghi vào SystemAdmin.
   * Dùng khi không có staff cụ thể nào can thiệp.
   *
   * @param auction phiên cần huỷ
   * @param reason  lý do huỷ
   */
  void cancelAuction(Auction auction, Admin.CancelReason reason);

  /**
   * Admin STAFF huỷ phiên đấu giá sau khi trực tiếp điều tra.
   * Log được ghi vào cả SystemAdmin (audit trail) lẫn staff cụ thể.
   * Dùng khi phiên bị cancel liên tục và cần staff đi kiểm tra.
   *
   * @param staff   admin STAFF đang xử lý
   * @param auction phiên cần huỷ
   * @param reason  lý do huỷ
   * @throws IllegalArgumentException nếu {@code staff} là SystemAdmin
   */
  void cancelAuction(Admin staff, Auction auction, Admin.CancelReason reason);

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
              NormalUser bidder, double amount);

  void notify(Auction auction, AuctionEvent.AuctionEventType type,
              NormalUser bidder, double amount, String message);
}