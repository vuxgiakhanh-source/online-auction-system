package com.group13.auction.service.iservice;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.service.BidService;
import com.group13.auction.strategy.BidStrategy;

/** Hợp đồng xử lý đặt giá: join, watch, placeBid. */
public interface IBidService {

  /**
   * Bidder tham gia phiên đấu giá. Tự động vào watchList và addObserver. Join thành công: khóa cọc
   * (30% giá khởi điểm) khỏi balance. Không cho Seller tự đấu giá món hàng của chính mình.
   *
   * @param bidder bidder muốn tham gia
   * @param auction phiên muốn tham gia
   * @param observer observer của bidder để nhận notify
   */
  void joinAuction(User bidder, Auction auction, AuctionObserver observer);

  /**
   * Theo dõi phiên mà không tham gia đặt bid.
   *
   * @param bidder bidder muốn theo dõi
   * @param auction phiên muốn theo dõi
   * @param observer observer để nhận notify
   */
  void watchAuction(User bidder, Auction auction, AuctionObserver observer);

  /**
   * Đặt giá cho một phiên đấu giá. Luôn check status == ACTIVE và rating >= threshold tại mỗi lần
   * placeBid.
   *
   * @param bidder người đặt giá
   * @param auction phiên đấu giá
   * @param amount số tiền đặt
   * @param strategy strategy kiểm tra tính hợp lệ
   */
  void placeBid(NormalUser bidder, Auction auction, long amount, BidStrategy strategy);

  /**
   * Rời phiên đấu giá: mở khóa cọc, xóa join state khỏi cả in-memory lẫn DB.
   *
   * <p>Phải persist xuống DB vì findUserByUsername() luôn load lại từ DB — nếu chỉ xóa in-memory
   * thì lần gọi tiếp theo vẫn thấy user đang JOINED.
   *
   * <p>Nếu {@code auction} không null và user là NormalUser đang giữ cọc, cọc sẽ được mở khóa (30%
   * giá khởi điểm) để trả lại số dư khả dụng.
   *
   * @param user bidder muốn rời phiên
   * @param auction phiên cần rời (null-safe: nếu null thì bỏ qua bước unlock cọc)
   */
  /**
   * Rời phiên. Trả về {@link com.group13.auction.service.BidService.LeaveResult} chứa đủ thông tin
   * penalty (leaderChanged, depositForfeited, ...) được tính nhất quán bên trong lock auction —
   * BidHandler dùng trực tiếp, không tính lại.
   */
  BidService.LeaveResult leaveAuction(User user, Auction auction);
}
