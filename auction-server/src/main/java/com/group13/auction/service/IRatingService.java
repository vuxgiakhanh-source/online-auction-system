package com.group13.auction.service;

import com.group13.auction.model.user.Bidder;
import com.group13.auction.model.user.User;

/**
 * Hợp đồng quản lý rating — chỉ hệ thống mới được thay đổi rating.
 * Không expose setter rating ra ngoài.
 */
public interface IRatingService {

  /**
   * Kiểm tra user đủ điều kiện hoạt động (ACTIVE + rating >= 1.0).
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
  void rewardBidder(Bidder bidder);

  /**
   * Thưởng rating cho Seller sau khi bán thành công.
   *
   * @param seller seller được thưởng
   */
  void rewardSeller(User seller);

  /**
   * Phạt Bidder khi không thanh toán đúng hạn.
   * Tự động ban nếu rating xuống dưới ngưỡng tối thiểu.
   *
   * @param bidder bidder bị phạt
   */
  void penalizeLatePayment(Bidder bidder);
}