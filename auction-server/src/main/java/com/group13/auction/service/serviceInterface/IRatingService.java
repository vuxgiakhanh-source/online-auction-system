package com.group13.auction.service.serviceInterface;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;

/**
 * Hợp đồng quản lý rating — chỉ hệ thống mới được thay đổi rating.
 * Không expose setter rating ra ngoài.
 */
public interface IRatingService {

  /**
   * Kiểm tra user đủ điều kiện hoạt động (ACTIVE + rating >= 2.0).
   *
   * @param user user cần kiểm tra
   * @return true nếu đủ điều kiện
   */
  boolean isEligible(User user);

  /**
   * Kiểm tra Seller đủ điều kiện tạo auction (isEligible + rating >= 2.0).
   *
   * @param seller seller cần kiểm tra
   * @return true nếu đủ điều kiện
   */
  boolean canSellerCreateAuction(User seller);

  /**
   * Thưởng rating cho Bidder sau khi thanh toán đúng hạn.
   *
   * @param bidder bidder được thưởng
   */
  void rewardBidder(NormalUser bidder);

  /**
   * Thưởng rating cho Seller sau khi bán thành công.
   *
   * @param seller seller được thưởng
   */
  void rewardSeller(User seller);

  /**
   * Phạt Bidder khi không thanh toán đúng hạn.
   * Tự động suspend/ban nếu rating xuống dưới ngưỡng.
   *
   * @param bidder bidder bị phạt
   */
  void penalizeLatePayment(NormalUser bidder);

  /**
   * Phạt Seller khi bị báo cáo chất lượng kém.
   *
   * @param seller seller bị phạt
   */
  void penalizeSeller(User seller);

  /**
   * Auto-restore rating sau 3 tháng bị SUSPENDED.
   *
   * <p>Cơ chế chỉ xảy ra <b>1 lần duy nhất</b> trên mỗi NormalUser.
   * Sau khi được restore, tài khoản không được auto-restore thêm lần nữa
   * dù bị SUSPENDED lại sau đó.
   *
   * <p>Gọi bởi scheduler định kỳ (ví dụ: mỗi ngày lúc 0h).
   *
   * @param user user cần kiểm tra restore
   */
  void checkAndRestoreSuspended(User user);
}