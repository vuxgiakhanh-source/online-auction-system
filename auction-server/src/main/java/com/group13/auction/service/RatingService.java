package com.group13.auction.service;

import com.group13.auction.dao.UserDAO;
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
 * Đã thực hiện TODO: inject UserDAO để persist xuống DB.
 *
 * <p>Cơ chế phục hồi SUSPENDED:
 * Sau 3 tháng được 1 lần, 1 tài khoản đc 1 lần
 *
 */
public class RatingService implements IRatingService {

  private static final Logger log = LoggerFactory.getLogger(RatingService.class);

  private static final double MIN_RATING_ELIGIBLE = 2.0;
  private static final double MIN_RATING_SELLER = 2.0;
  private static final double REWARD_BIDDER_PAYMENT = 0.2;
  private static final double REWARD_SELLER_SALE = 0.2;
  private static final double PENALTY_LATE_PAYMENT = 1.0;
  private static final double PENALTY_SELLER_QUALITY = 1.0;
  private static final double AUTO_SUSPEND_THRESHOLD = User.RATING_SUSPEND_THRESHOLD;

  /** Thời gian chờ trước khi auto-restore: 3 tháng. */
  private static final long SUSPEND_RESTORE_MONTHS = 3;

  /**
   * Lượng rating cộng thêm khi restore.
   *
   * <p>Cộng đủ để vượt ngưỡng SUSPEND ({@value User#RATING_SUSPEND_THRESHOLD})
   * và đạt tối thiểu {@value #MIN_RATING_ELIGIBLE} để có thể tham gia phiên.
   */
  private static final double RESTORE_DELTA = 0.6;

  private final UserDAO userDAO;

  public RatingService(UserDAO userDAO) {
    this.userDAO = userDAO;
  }

  // Eligibility checks

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

  // Tăng thưởng

  @Override
  public void rewardBidder(NormalUser bidder) {
    bidder.adjustRating(REWARD_BIDDER_PAYMENT);
    log.info("Bidder rating rewarded: userId={}, username={}, delta={}, rating={}",
            bidder.getId(), bidder.getUsername(), REWARD_BIDDER_PAYMENT, bidder.getRating());

    // Thực hiện TODO: Cập nhật rating xuống DB
    userDAO.updateRating(bidder.getId(), bidder.getRating());
    // TODO: notificationDao.save() - báo cho bidder
  }

  @Override
  public void rewardSeller(User seller) {
    seller.adjustRating(REWARD_SELLER_SALE);
    log.info("Seller rating rewarded: userId={}, username={}, delta={}, rating={}",
            seller.getId(), seller.getUsername(), REWARD_SELLER_SALE, seller.getRating());

    // Thực hiện TODO: Cập nhật rating xuống DB
    userDAO.updateRating(seller.getId(), seller.getRating());
    // TODO: notificationDao.save() - báo cho seller
  }

  // Penalty methods

  @Override
  public void penalizeLatePayment(NormalUser bidder) {
    bidder.adjustRating(-PENALTY_LATE_PAYMENT);
    bidder.markPenalized();
    log.warn("Bidder rating penalized for late payment: userId={}, username={}, delta={}, rating={}",
            bidder.getId(), bidder.getUsername(), -PENALTY_LATE_PAYMENT, bidder.getRating());

    autoSuspendIfNeeded(bidder);

    // Thực hiện TODO: Cập nhật điểm, cờ vi phạm và trạng thái xuống DB
    userDAO.updateRatingAndPenalty(bidder.getId(), bidder.getRating(), bidder.isHasEverBeenPenalized());
    userDAO.updateAccountStatus(bidder.getId(), bidder.getAccountStatus().name());
    // TODO: notificationDao.save() - báo cho bidder
  }

  @Override
  public void penalizeSeller(User seller) {
    seller.adjustRating(-PENALTY_SELLER_QUALITY);

    boolean isPenalized = false;
    if (seller instanceof NormalUser) {
      ((NormalUser) seller).markPenalized();
      isPenalized = true;
    }

    log.warn("Seller rating penalized for quality issue: userId={}, username={}, delta={}, rating={}",
            seller.getId(), seller.getUsername(), -PENALTY_SELLER_QUALITY, seller.getRating());

    autoSuspendIfNeeded(seller);

    // Thực hiện TODO: Cập nhật điểm, cờ vi phạm và trạng thái xuống DB
    userDAO.updateRatingAndPenalty(seller.getId(), seller.getRating(), isPenalized);
    userDAO.updateAccountStatus(seller.getId(), seller.getAccountStatus().name());
    // TODO: notificationDao.save() - báo cho seller
  }

  /** method dùng cho hầu như quá trình build
   * @param user user cần kiểm tra restore
   */
  public void checkAndRestoreSuspended(User user) {
    checkAndRestoreSuspended(user, LocalDateTime.now());
  }

  /**
   * (Dành cho Test)
   * Auto-restore rating cho tài khoản SUSPENDED sau 3 tháng.
   *(Miễn chưa từng được restore)
   *
   * <p>Sau khi restore: rating tăng thêm {@value #RESTORE_DELTA},
   * nếu rating > ngưỡng SUSPEND thì chuyển về ACTIVE.
   * Đánh dấu {@code hasEverBeenRestored = true} để chặn restore lần 2.
   *
   * @param user user cần kiểm tra restore
   */
  public void checkAndRestoreSuspended(User user, LocalDateTime currentTime) {
    if (user.getAccountStatus() != AccountStatus.SUSPENDED) {
      return;
    }
    if (user.getSuspendedAt() == null) {
      return;
    }

    // Chỉ NormalUser mới có cơ chế restore 1 lần
    if (!(user instanceof NormalUser)) {
      return;
    }
    NormalUser normalUser = (NormalUser) user;

    // Guard: chỉ restore 1 lần duy nhất
    if (normalUser.isHasEverBeenRestored()) {
      log.info("Suspended user already restored once, skipping restore: userId={}, username={}",
              user.getId(), user.getUsername());
      return;
    }

    LocalDateTime restoreThreshold = user.getSuspendedAt().plusMonths(SUSPEND_RESTORE_MONTHS);
    if (!currentTime.isAfter(restoreThreshold)) {
      return; // Chưa đủ 3 tháng
    }

    // Cộng rating
    user.adjustRating(RESTORE_DELTA);

    if (user.getRating() > AUTO_SUSPEND_THRESHOLD) {
      user.setAccountStatus(AccountStatus.ACTIVE);
      log.info("Suspended user restored: userId={}, username={}, months={}, rating={}, status={}",
              user.getId(), user.getUsername(), SUSPEND_RESTORE_MONTHS, user.getRating(), user.getAccountStatus());
    } else {
      log.info("Suspended user restore delta applied but status remains suspended: userId={}, username={}, delta={}, rating={}, threshold={}",
              user.getId(), user.getUsername(), RESTORE_DELTA, user.getRating(), AUTO_SUSPEND_THRESHOLD);
    }

    // Đánh dấu đã restore 1 lần
    normalUser.markRestored();

    // Đã thực hiện TODO: Cập nhật điểm, cờ restore và trạng thái mới xuống DB
    userDAO.updateRating(user.getId(), user.getRating());
    userDAO.updateAccountStatus(user.getId(), user.getAccountStatus().name());
    // Đã thực hiện TODO: persist cờ hasEverBeenRestored để không mất khi restart
    userDAO.updateHasEverBeenRestored(user.getId(), normalUser.isHasEverBeenRestored());
    // TODO: notificationDao.save() - báo cho user
  }

  // Private helpers

  private void autoSuspendIfNeeded(User user) {
    if (user.getAccountStatus() == AccountStatus.ACTIVE
            && user.getRating() <= AUTO_SUSPEND_THRESHOLD) {
      user.setAccountStatus(AccountStatus.SUSPENDED);
      log.warn("User auto-suspended because rating is below threshold: userId={}, username={}, rating={}, threshold={}",
              user.getId(), user.getUsername(), user.getRating(), AUTO_SUSPEND_THRESHOLD);
    }
    // TODO: notificationDao.save() - báo cho user
  }
}
