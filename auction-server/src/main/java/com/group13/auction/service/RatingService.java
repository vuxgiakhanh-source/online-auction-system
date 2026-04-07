package com.group13.auction.service;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import java.time.LocalDateTime;

/**
 * Quản lý toàn bộ logic rating — đảm bảo chỉ hệ thống mới thay đổi rating.
 * Không phụ thuộc vào service nào khác.
 *
 * <p>Rating KHÔNG bao giờ được thay đổi trực tiếp từ bên ngoài service này.
 * {@link User#adjustRating(double)} chỉ được gọi từ đây.
 * TODO: inject UserDAO để persist xuống DB.
 */
public class RatingService implements IRatingService {

  /** Bidder cần rating >= 2.0 để tham gia phiên. */
  private static final double MIN_RATING_ELIGIBLE    = 2.0;
  private static final double MIN_RATING_SELLER      = 2.0;
  private static final double REWARD_BIDDER_PAYMENT  = 0.2;
  private static final double REWARD_SELLER_SALE     = 0.2;
  private static final double PENALTY_LATE_PAYMENT   = 1.0;
  private static final double PENALTY_SELLER_QUALITY = 1.0;
  /** Ngưỡng tự động đình chỉ tài khoản. */
  private static final double AUTO_SUSPEND_THRESHOLD = User.RATING_SUSPEND_THRESHOLD;
  /** Tháng không vi phạm để auto-restore. */
  private static final long   SUSPEND_RESTORE_MONTHS = 6;
  /** Điểm cộng thêm sau 6 tháng suspend không vi phạm. */
  private static final double RESTORE_DELTA          = 0.3;

  // ── Eligibility checks ─────────────────────────────────────────────────

  /**
   * Kiểm tra user đủ điều kiện hoạt động.
   * Điều kiện: ACTIVE và rating >= 2.0.
   *
   * @param user user cần kiểm tra
   * @return true nếu đủ điều kiện
   */
  @Override
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
  @Override
  public boolean canSellerCreateAuction(User seller) {
    return isEligible(seller) && seller.getRating() >= MIN_RATING_SELLER;
  }

  // ── Reward methods ─────────────────────────────────────────────────────

  /**
   * Thưởng rating cho Bidder sau khi thanh toán đúng hạn.
   * TODO: userDAO.update(bidder).
   *
   * @param bidder bidder được thưởng
   */
  @Override
  public void rewardBidder(NormalUser bidder) {
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
  @Override
  public void rewardSeller(User seller) {
    seller.adjustRating(REWARD_SELLER_SALE);
    System.out.printf("[RATING] %s +%.1f → %.1f%n",
            seller.getUsername(), REWARD_SELLER_SALE, seller.getRating());
    // TODO: userDAO.update(seller)
  }

  // ── Penalty methods ────────────────────────────────────────────────────

  /**
   * Phạt Bidder khi không thanh toán đúng hạn.
   * Tự động suspend nếu rating <= 1.5.
   * TODO: userDAO.update(bidder).
   *
   * @param bidder bidder bị phạt
   */
  @Override
  public void penalizeLatePayment(NormalUser bidder) {
    bidder.adjustRating(-PENALTY_LATE_PAYMENT);
    System.out.printf("[RATING] %s -%.1f → %.1f (vi phạm thanh toán)%n",
            bidder.getUsername(), PENALTY_LATE_PAYMENT, bidder.getRating());
    autoSuspendIfNeeded(bidder);
    // TODO: userDAO.update(bidder)
  }

  /**
   * Phạt Seller khi bị báo cáo chất lượng kém (approved bởi admin).
   * Tự động suspend nếu rating <= 1.5.
   * TODO: userDAO.update(seller).
   *
   * @param seller seller bị phạt
   */
  @Override
  public void penalizeSeller(User seller) {
    seller.adjustRating(-PENALTY_SELLER_QUALITY);
    System.out.printf("[RATING] %s -%.1f → %.1f (vi phạm chất lượng)%n",
            seller.getUsername(), PENALTY_SELLER_QUALITY, seller.getRating());
    autoSuspendIfNeeded(seller);
    // TODO: userDAO.update(seller)
  }

  /**
   * Auto-restore rating sau 6 tháng không vi phạm.
   * Cộng thêm 0.3; nếu rating sau khi cộng > 1.5 thì chuyển về ACTIVE.
   * TODO: userDAO.update(user).
   *
   * @param user user cần kiểm tra restore
   */
  @Override
  public void checkAndRestoreSuspended(User user) {
    if (user.getAccountStatus() != AccountStatus.SUSPENDED) return;
    if (user.getSuspendedAt() == null) return;

    LocalDateTime restoreTime = user.getSuspendedAt().plusMonths(SUSPEND_RESTORE_MONTHS);
    if (LocalDateTime.now().isBefore(restoreTime)) return;

    user.adjustRating(RESTORE_DELTA);
    System.out.printf("[RATING] %s auto-restore +%.1f → %.1f (sau 6 tháng suspend)%n",
            user.getUsername(), RESTORE_DELTA, user.getRating());

    if (user.getRating() > AUTO_SUSPEND_THRESHOLD) {
      user.setAccountStatus(AccountStatus.ACTIVE);
      System.out.printf("[ACCOUNT] %s được khôi phục ACTIVE (rating %.1f > %.1f)%n",
              user.getUsername(), user.getRating(), AUTO_SUSPEND_THRESHOLD);
    }
    // TODO: userDAO.update(user)
  }

  // ── Private helpers ────────────────────────────────────────────────────

  /**
   * Tự động đình chỉ (SUSPENDED) nếu rating <= 1.5.
   * Khác với ban: SUSPENDED có thể phục hồi sau 6 tháng.
   *
   * @param user user cần kiểm tra
   */
  private void autoSuspendIfNeeded(User user) {
    if (user.getRating() <= AUTO_SUSPEND_THRESHOLD
            && user.getAccountStatus() == AccountStatus.ACTIVE) {
      user.setAccountStatus(AccountStatus.SUSPENDED);
      System.out.printf("[ACCOUNT] %s tự động bị SUSPEND (rating %.1f <= %.1f). Sẽ xem xét phục hồi sau 6 tháng.%n",
              user.getUsername(), user.getRating(), AUTO_SUSPEND_THRESHOLD);
    }
  }
}