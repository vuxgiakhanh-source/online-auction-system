package com.group13.auction.service;

import com.group13.auction.dao.NotificationDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.notification.Notification;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.iservice.IRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Quản lý toàn bộ logic rating — đảm bảo chỉ hệ thống mới thay đổi rating.
 * Không phụ thuộc vào service nào khác.
 *
 * <p>Rating KHÔNG bao giờ được thay đổi trực tiếp từ bên ngoài service này.
 * {@link User#adjustRating(double)} chỉ được gọi từ đây.
 *
 * <p>Cơ chế phục hồi SUSPENDED:
 * Sau 3 tháng được 1 lần, 1 tài khoản được 1 lần.
 */
public class RatingService implements IRatingService {

  private static final Logger log = LoggerFactory.getLogger(RatingService.class);

  private static final double MIN_RATING_ELIGIBLE    = 2.0;
  private static final double MIN_RATING_SELLER      = 2.0;
  private static final double REWARD_BIDDER_PAYMENT  = 0.2;
  private static final double REWARD_SELLER_SALE     = 0.2;
  private static final double PENALTY_LATE_PAYMENT   = 1.0;
  private static final double PENALTY_SELLER_QUALITY = 1.0;
  private static final double PENALTY_EARLY_LEAVE    = 1.0;
  private static final double AUTO_SUSPEND_THRESHOLD = User.RATING_SUSPEND_THRESHOLD;
  private static final long   SUSPEND_RESTORE_MONTHS = 3;
  private static final double RESTORE_DELTA          = 0.6;

  private final UserDAO         userDAO;
  private final NotificationDAO notificationDAO;

  public RatingService(UserDAO userDAO) {
    this(userDAO, new NotificationDAO());
  }

  public RatingService(UserDAO userDAO, NotificationDAO notificationDAO) {
    this.userDAO         = userDAO;
    this.notificationDAO = notificationDAO;
  }

  // ── Eligibility ────────────────────────────────────────────────────────

  @Override
  public boolean isEligible(User user) {
    return user.getAccountStatus() == AccountStatus.ACTIVE
        && user.getRating() >= MIN_RATING_ELIGIBLE;
  }

  @Override
  public boolean canSellerCreateAuction(User seller) {
    return seller.hasRole(User.UserRole.SELLER)
        && isEligible(seller)
        && seller.getRating() >= MIN_RATING_SELLER;
  }

  // ── Rewards ────────────────────────────────────────────────────────────

  @Override
  public void rewardBidder(NormalUser bidder) {
    bidder.adjustRating(REWARD_BIDDER_PAYMENT);
    log.info("Rating reward bidder: username={}, delta=+{}, newRating={}",
        bidder.getUsername(), REWARD_BIDDER_PAYMENT, bidder.getRating());

    userDAO.updateRating(bidder.getId(), bidder.getRating());

    saveNotification(bidder.getId(), null,
        "Điểm uy tín tăng",
        String.format("Bạn đã thanh toán đúng hạn. Điểm uy tín tăng +%.1f → %.2f.",
            REWARD_BIDDER_PAYMENT, bidder.getRating()));
  }

  @Override
  public void rewardSeller(User seller) {
    seller.adjustRating(REWARD_SELLER_SALE);
    log.info("Rating reward seller: username={}, delta=+{}, newRating={}",
        seller.getUsername(), REWARD_SELLER_SALE, seller.getRating());

    userDAO.updateRating(seller.getId(), seller.getRating());

    saveNotification(seller.getId(), null,
        "Điểm uy tín tăng",
        String.format("Phiên đấu giá của bạn hoàn tất thành công. Điểm uy tín tăng +%.1f → %.2f.",
            REWARD_SELLER_SALE, seller.getRating()));
  }

  // ── Penalties ──────────────────────────────────────────────────────────

  @Override
  public void penalizeLatePayment(NormalUser bidder) {
    bidder.adjustRating(-PENALTY_LATE_PAYMENT);
    bidder.markPenalized();
    log.info("Rating penalty late-payment: username={}, delta=-{}, newRating={}",
        bidder.getUsername(), PENALTY_LATE_PAYMENT, bidder.getRating());

    autoSuspendIfNeeded(bidder, null);

    userDAO.updateRatingAndPenalty(bidder.getId(), bidder.getRating(),
        bidder.isHasEverBeenPenalized());
    userDAO.updateAccountStatus(bidder.getId(), bidder.getAccountStatus().name());

    saveNotification(bidder.getId(), null,
        "Cảnh báo: điểm uy tín bị trừ",
        String.format("Bạn không thanh toán đúng hạn. Điểm uy tín trừ -%.1f → %.2f.%s",
            PENALTY_LATE_PAYMENT, bidder.getRating(),
            bidder.getAccountStatus() == AccountStatus.SUSPENDED
                ? " Tài khoản bị tạm khóa." : ""));
  }

  @Override
  public void penalizeSeller(User seller) {
    seller.adjustRating(-PENALTY_SELLER_QUALITY);
    boolean isPenalized = false;
    if (seller instanceof NormalUser) {
      ((NormalUser) seller).markPenalized();
      isPenalized = true;
    }
    log.info("Rating penalty seller-quality: username={}, delta=-{}, newRating={}",
        seller.getUsername(), PENALTY_SELLER_QUALITY, seller.getRating());

    autoSuspendIfNeeded(seller, null);

    userDAO.updateRatingAndPenalty(seller.getId(), seller.getRating(), isPenalized);
    userDAO.updateAccountStatus(seller.getId(), seller.getAccountStatus().name());

    saveNotification(seller.getId(), null,
        "Cảnh báo: điểm uy tín bị trừ",
        String.format("Báo cáo chất lượng sản phẩm được duyệt. Điểm uy tín trừ -%.1f → %.2f.%s",
            PENALTY_SELLER_QUALITY, seller.getRating(),
            seller.getAccountStatus() == AccountStatus.SUSPENDED
                ? " Tài khoản bị tạm khóa." : ""));
  }

  @Override
  public void penalizeEarlyLeave(NormalUser bidder) {
    bidder.adjustRating(-PENALTY_EARLY_LEAVE);
    bidder.markPenalized();
    log.warn("Rating penalty early-leave: username={}, delta=-{}, newRating={}",
        bidder.getUsername(), PENALTY_EARLY_LEAVE, bidder.getRating());

    autoSuspendIfNeeded(bidder, null);

    userDAO.updateRatingAndPenalty(bidder.getId(), bidder.getRating(),
        bidder.isHasEverBeenPenalized());
    userDAO.updateAccountStatus(bidder.getId(), bidder.getAccountStatus().name());

    saveNotification(bidder.getId(), null,
        "Cảnh báo: điểm uy tín bị trừ",
        String.format("Bạn đã rời phiên đấu giá khi đang dẫn đầu. Điểm uy tín trừ -%.1f → %.2f.%s",
            PENALTY_EARLY_LEAVE, bidder.getRating(),
            bidder.getAccountStatus() == AccountStatus.SUSPENDED
                ? " Tài khoản bị tạm khóa." : ""));
  }

  // ── Suspend restore ────────────────────────────────────────────────────

  @Override
  public void checkAndRestoreSuspended(User user) {
    checkAndRestoreSuspended(user, LocalDateTime.now());
  }

  public void checkAndRestoreSuspended(User user, LocalDateTime currentTime) {
    if (user.getAccountStatus() != AccountStatus.SUSPENDED) return;
    if (user.getSuspendedAt() == null) return;
    if (!(user instanceof NormalUser)) return;

    NormalUser normalUser = (NormalUser) user;
    if (normalUser.isHasEverBeenRestored()) {
      log.info("Rating restore skipped — already restored once: username={}", user.getUsername());
      return;
    }

    LocalDateTime threshold = user.getSuspendedAt().plusMonths(SUSPEND_RESTORE_MONTHS);
    if (!currentTime.isAfter(threshold)) return;

    user.adjustRating(RESTORE_DELTA);
    if (user.getRating() > AUTO_SUSPEND_THRESHOLD) {
      user.setAccountStatus(AccountStatus.ACTIVE);
      log.info("Rating auto-restored to ACTIVE: username={}, newRating={}",
          user.getUsername(), user.getRating());
    } else {
      log.info("Rating restored but still suspended: username={}, newRating={}",
          user.getUsername(), user.getRating());
    }

    normalUser.markRestored();

    userDAO.updateRating(user.getId(), user.getRating());
    userDAO.updateAccountStatus(user.getId(), user.getAccountStatus().name());
    userDAO.incrementTimesRestored(user.getId());

    saveNotification(user.getId(), null,
        "Tài khoản được khôi phục",
        String.format("Tài khoản của bạn đã được xem xét lại. Điểm uy tín hiện tại: %.2f. Trạng thái: %s.",
            user.getRating(), user.getAccountStatus().name()));
  }

  // ── User-submitted rating (RATE_SELLER / RATE_BIDDER) ─────────────────

  /**
   * Áp dụng đánh giá từ user (thang 1–5) lên điểm uy tín của target.
   *
   * <p>Công thức: delta = (score - 3.0) × 0.1 → range [-0.2, +0.2].
   * Score 1–2: trừ nhẹ; Score 4–5: cộng nhẹ; Score 3: không thay đổi.
   *
   * @param target    user được đánh giá
   * @param score     điểm 1.0–5.0
   * @param auctionId phiên liên quan (để attach vào notification)
   * @param raterName username của người đánh giá (để ghi vào thông báo)
   */
  public void applyUserRating(User target, double score, String auctionId, String raterName) {
    if (score < 1.0 || score > 5.0) {
      throw new IllegalArgumentException("Rating score phải trong khoảng 1.0 – 5.0, nhận được: " + score);
    }

    double delta = (score - 3.0) * 0.1;
    if (Math.abs(delta) < 0.001) {
      log.info("applyUserRating: score=3.0 → no delta for target={}", target.getUsername());
      return;
    }

    target.adjustRating(delta);
    log.info("applyUserRating: target={}, rater={}, score={}, delta={}, newRating={}",
        target.getUsername(), raterName, score, delta, target.getRating());

    userDAO.updateRating(target.getId(), target.getRating());

    String sign = delta > 0 ? "+" : "";
    saveNotification(target.getId(), auctionId,
        "Bạn nhận được đánh giá mới",
        String.format("%s đánh giá bạn %.1f/5. Điểm uy tín %s%.2f → %.2f.",
            raterName, score, sign, delta, target.getRating()));
  }

  // ── Private helpers ────────────────────────────────────────────────────

  /**
   * Kiểm tra và tự động suspend nếu rating xuống dưới ngưỡng.
   *
   * @param auctionId nullable — đính kèm vào notification nếu có
   */
  private void autoSuspendIfNeeded(User user, String auctionId) {
    if (user.getAccountStatus() == AccountStatus.ACTIVE
        && user.getRating() <= AUTO_SUSPEND_THRESHOLD) {
      user.setAccountStatus(AccountStatus.SUSPENDED);
      log.info("Account auto-suspended: username={}, rating={}, threshold={}",
          user.getUsername(), user.getRating(), AUTO_SUSPEND_THRESHOLD);

      saveNotification(user.getId(), auctionId,
          "Tài khoản bị tạm khóa",
          String.format("Điểm uy tín của bạn (%.2f) xuống dưới ngưỡng cho phép. "
              + "Tài khoản bị tạm khóa và sẽ được xem xét sau 3 tháng.", user.getRating()));
    }
  }

  /** Lưu notification vào DB, fail-safe (không throw). */
  private void saveNotification(String userId, String auctionId,
                                String title, String body) {
    try {
      Notification n = Notification.create(userId, auctionId, title, body);
      notificationDAO.save(n);
    } catch (Exception e) {
      log.warn("Không thể lưu notification: userId={}, title={}", userId, title, e);
    }
  }
}