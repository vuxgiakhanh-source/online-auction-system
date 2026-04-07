package com.group13.auction.service;

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
   * Auto-restore rating sau 6 tháng không vi phạm (cho user SUSPENDED).
   * Cộng thêm 0.3 để user có thể tiếp tục hoạt động nếu rating > 1.5.
   *
   * @param user user cần kiểm tra restore
   */
  void checkAndRestoreSuspended(User user);
}