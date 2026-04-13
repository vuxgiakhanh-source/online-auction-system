package com.group13.auction.service;

import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.serviceInterface.IRatingService;

import java.time.LocalDateTime;

/**
 * Quản lý toàn bộ logic rating — đảm bảo chỉ hệ thống mới thay đổi rating.
 * Không phụ thuộc vào service nào khác.
 *
 * <p>Rating KHÔNG bao giờ được thay đổi trực tiếp từ bên ngoài service này.
 * {@link User#adjustRating(double)} chỉ được gọi từ đây.
 * Đã thực hiện TODO: inject UserDAO để persist xuống DB.
 */
public class RatingService implements IRatingService {

  private static final double MIN_RATING_ELIGIBLE = 2.0;
  private static final double MIN_RATING_SELLER = 2.0;
  private static final double REWARD_BIDDER_PAYMENT = 0.2;
  private static final double REWARD_SELLER_SALE = 0.2;
  private static final double PENALTY_LATE_PAYMENT = 1.0;
  private static final double PENALTY_SELLER_QUALITY = 1.0;
  private static final double AUTO_SUSPEND_THRESHOLD = User.RATING_SUSPEND_THRESHOLD;
  private static final long SUSPEND_RESTORE_MONTHS = 6;
  private static final double RESTORE_DELTA = 0.3;

  // Thực hiện TODO: Tiêm UserDAO
  private final UserDAO userDAO;

  public RatingService(UserDAO userDAO) {
    this.userDAO = userDAO;
  }

  // ── Eligibility checks ─────────────────────────────────────────────────────

  @Override
  public boolean isEligible(User user) {
    return user.getAccountStatus() == AccountStatus.ACTIVE
            && user.getRating() >= MIN_RATING_ELIGIBLE;
  }

  @Override
  public boolean canSellerCreateAuction(User seller) {
    return isEligible(seller) && seller.getRating() >= MIN_RATING_SELLER;
  }

  // ── Reward methods ─────────────────────────────────────────────────────────

  @Override
  public void rewardBidder(NormalUser bidder) {
    bidder.adjustRating(REWARD_BIDDER_PAYMENT);
    System.out.printf("[RATING] %s +%.1f → %.1f%n",
            bidder.getUsername(), REWARD_BIDDER_PAYMENT, bidder.getRating());

    // Thực hiện TODO: Cập nhật rating xuống DB
    userDAO.updateRating(bidder.getId(), bidder.getRating());
  }

  @Override
  public void rewardSeller(User seller) {
    seller.adjustRating(REWARD_SELLER_SALE);
    System.out.printf("[RATING] %s +%.1f → %.1f%n",
            seller.getUsername(), REWARD_SELLER_SALE, seller.getRating());

    // Thực hiện TODO: Cập nhật rating xuống DB
    userDAO.updateRating(seller.getId(), seller.getRating());
  }

  // ── Penalty methods ────────────────────────────────────────────────────────

  @Override
  public void penalizeLatePayment(NormalUser bidder) {
    bidder.adjustRating(-PENALTY_LATE_PAYMENT);
    bidder.markPenalized();
    System.out.printf("[RATING] %s -%.1f → %.1f (vi phạm thanh toán)%n",
            bidder.getUsername(), PENALTY_LATE_PAYMENT, bidder.getRating());

    autoSuspendIfNeeded(bidder);

    // Thực hiện TODO: Cập nhật điểm, cờ vi phạm và trạng thái xuống DB
    userDAO.updateRatingAndPenalty(bidder.getId(), bidder.getRating(), bidder.isHasEverBeenPenalized());
    userDAO.updateAccountStatus(bidder.getId(), bidder.getAccountStatus().name());
  }

  @Override
  public void penalizeSeller(User seller) {
    seller.adjustRating(-PENALTY_SELLER_QUALITY);

    boolean isPenalized = false;
    if (seller instanceof NormalUser) {
      ((NormalUser) seller).markPenalized();
      isPenalized = true;
    }

    System.out.printf("[RATING] %s -%.1f → %.1f (vi phạm chất lượng)%n",
            seller.getUsername(), PENALTY_SELLER_QUALITY, seller.getRating());

    autoSuspendIfNeeded(seller);

    // Thực hiện TODO: Cập nhật điểm, cờ vi phạm và trạng thái xuống DB
    userDAO.updateRatingAndPenalty(seller.getId(), seller.getRating(), isPenalized);
    userDAO.updateAccountStatus(seller.getId(), seller.getAccountStatus().name());
  }

  @Override
  public void checkAndRestoreSuspended(User user) {
    if (user.getAccountStatus() != AccountStatus.SUSPENDED) return;
    if (user.getSuspendedAt() == null) return;

    LocalDateTime restoreThreshold = user.getSuspendedAt().plusMonths(SUSPEND_RESTORE_MONTHS);
    if (LocalDateTime.now().isAfter(restoreThreshold)) {
      user.adjustRating(RESTORE_DELTA);

      if (user.getRating() > AUTO_SUSPEND_THRESHOLD) {
        user.setAccountStatus(AccountStatus.ACTIVE);
        System.out.printf("[RATING] %s được khôi phục sau 6 tháng | Rating: %.1f%n",
                user.getUsername(), user.getRating());
      }

      // Thực hiện TODO: Cập nhật điểm và trạng thái mới xuống DB
      userDAO.updateRating(user.getId(), user.getRating());
      userDAO.updateAccountStatus(user.getId(), user.getAccountStatus().name());
    }
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  private void autoSuspendIfNeeded(User user) {
    if (user.getAccountStatus() == AccountStatus.ACTIVE
            && user.getRating() <= AUTO_SUSPEND_THRESHOLD) {
      user.setAccountStatus(AccountStatus.SUSPENDED);
      System.out.printf("[RATING] %s bị SUSPEND — rating %.1f <= %.1f%n",
              user.getUsername(), user.getRating(), AUTO_SUSPEND_THRESHOLD);
    }
  }
}