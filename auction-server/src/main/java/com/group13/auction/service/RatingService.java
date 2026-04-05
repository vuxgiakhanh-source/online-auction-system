package com.group13.auction.service;

import com.group13.auction.model.user.Bidder;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;

/**
 * Quản lý toàn bộ logic rating — đảm bảo chỉ hệ thống mới thay đổi rating.
 * Không phụ thuộc vào service nào khác
 * 
 * <p>Rating KHÔNG bao giờ được thay đổi trực tiếp từ bên ngoài service này.
 * {@link User#adjustRating(double)} chỉ được gọi từ đây.
 * TODO: inject UserDAO để persist xuống DB.
 */
public class RatingService implements IRatingService {

  private static final double MIN_RATING_ELIGIBLE    = 1.0;
  private static final double MIN_RATING_SELLER      = 2.0;
  private static final double REWARD_BIDDER_PAYMENT  = 0.2;
  private static final double REWARD_SELLER_SALE     = 0.3;
  private static final double PENALTY_LATE_PAYMENT   = 1.0;

  // ── Eligibility checks ─────────────────────────────────────────────────────

  /**
   * Kiểm tra user đủ điều kiện hoạt động.
   * Điều kiện: ACTIVE và rating >= 1.0.
   *
   * @param user user cần kiểm tra
   * @return true nếu đủ điều kiện
   */
  public boolean isEligible(User user) {
    return user.getAccountStatus() == AccountStatus.ACTIVE
        && user.getRating() >= MIN_RATING_ELIGIBLE;
  }

  /**
   * Kiểm tra Seller đủ điều kiện tạo auction.
   * Điều kiện: isEligible() và rating >= 2.0.
   *
   * @param seller seller cần kiểm tra
   * @return true nếu đủ điều kiện
   */
  public boolean canSellerCreateAuction(User seller) {
    return isEligible(seller) && seller.getRating() >= MIN_RATING_SELLER;
  }

  // ── Reward methods ─────────────────────────────────────────────────────────

  /**
   * Thưởng rating cho Bidder sau khi thanh toán đúng hạn.
   * TODO: userDAO.update(bidder).
   *
   * @param bidder bidder được thưởng
   */
  public void rewardBidder(Bidder bidder) {
    bidder.adjustRating(REWARD_BIDDER_PAYMENT);
    System.out.printf("[RATING] %s +%.1f → %.1f%n",
        bidder.getUsername(), REWARD_BIDDER_PAYMENT, bidder.getRating());
    // TODO: userDAO.update(bidder)
  }

  /**
   * Thưởng rating cho Seller sau khi bán thành công.
   * TODO: userDAO.update(seller).
   *
   * @param seller seller được thưởng
   */
  public void rewardSeller(User seller) {
    seller.adjustRating(REWARD_SELLER_SALE);
    System.out.printf("[RATING] %s +%.1f → %.1f%n",
        seller.getUsername(), REWARD_SELLER_SALE, seller.getRating());
    // TODO: userDAO.update(seller)
  }

  // ── Penalty methods ────────────────────────────────────────────────────────

  /**
   * Phạt Bidder khi không thanh toán đúng hạn.
   * Tự động ban nếu rating xuống dưới ngưỡng tối thiểu.
   * TODO: userDAO.update(bidder).
   *
   * @param bidder bidder bị phạt
   */
  public void penalizeLatePayment(Bidder bidder) {
    bidder.adjustRating(-PENALTY_LATE_PAYMENT);
    System.out.printf("[RATING] %s -%.1f → %.1f (vi phạm thanh toán)%n",
        bidder.getUsername(), PENALTY_LATE_PAYMENT, bidder.getRating());
    autoBanIfNeeded(bidder);
    // TODO: userDAO.update(bidder)
  }

  /**
   * Tự động ban nếu rating xuống dưới ngưỡng tối thiểu.
   *
   * @param user user cần kiểm tra
   */
  private void autoBanIfNeeded(User user) {
    if (user.getRating() < MIN_RATING_ELIGIBLE
        && user.getAccountStatus() == AccountStatus.ACTIVE) {
      user.setAccountStatus(AccountStatus.BANNED);
      System.out.printf("[RATING] %s tự động bị ban (rating %.1f < %.1f).%n",
          user.getUsername(), user.getRating(), MIN_RATING_ELIGIBLE);
    }
  }
}