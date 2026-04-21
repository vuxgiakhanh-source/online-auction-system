package com.group13.auction.service;

import com.group13.auction.dao.*;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.AdminFactory;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.model.user.UserFactory;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.StaffObserver;
import com.group13.auction.service.iservice.IAccountService;
import com.group13.auction.service.iservice.IRatingService;

import java.util.List;

/**
 * Quản lý trạng thái tài khoản: ban, deposit, tạo admin STAFF, quản lý role.
 *
 * <p>Chỉ SystemAdmin (MASTER) mới được tạo admin STAFF qua
 * {@link #createStaffAdmin}.
 * MASTER duy nhất là {@link SystemAdmin}, được seed sẵn khi bootstrap.
 *
 * <p>Hệ thống tự động duyệt role Seller nếu user chưa từng bị trừ rating.
 */
public class AccountService implements IAccountService {

  private final IRatingService ratingService;
  private final AdminFactory adminFactory;
  private final WalletService walletService;

  private final UserDAO userDAO;
  private final SellerDAO sellerDAO;
  private final AdminDAO adminDAO;
  private final AuctionDAO auctionDAO;

  /**
   * Đã thực hiện TODO: inject AuctionWinnerDAO để kiểm tra trạng thái thanh toán của winner/runner-up
   * khi xóa tài khoản — dùng auctionWinnerDAO.hasPendingPayment().
   */
  private final AuctionWinnerDAO auctionWinnerDAO;

  public AccountService(
          IRatingService ratingService,
          UserDAO userDAO,
          SellerDAO sellerDAO,
          AdminDAO adminDAO,
          AuctionDAO auctionDAO,
          AuctionWinnerDAO auctionWinnerDAO) {
    this.ratingService = ratingService;
    this.adminFactory = new AdminFactory();
    this.userDAO = userDAO;
    this.sellerDAO = sellerDAO;
    this.adminDAO = adminDAO;
    this.auctionDAO = auctionDAO;
    this.auctionWinnerDAO = auctionWinnerDAO;
    // PaymentHandler cần deposit/withdraw; WalletService chịu trách nhiệm validate và persist số dư.
    this.walletService = new WalletService(new FinancialTransactionDAO(), userDAO, ratingService);
  }

  public void deposit(NormalUser user, long amount) {
    walletService.deposit(user, amount);
  }

  public void withdraw(NormalUser user, long amount) {
    walletService.withdraw(user, amount);
  }

  // Ban

  /**
   * Ban tài khoản với lý do cụ thể — chỉ Admin gọi.
   *
   * @param admin admin thực hiện
   * @param target user bị ban
   * @param reason lý do ban
   */
  @Override
  public void banUser(Admin admin, User target, Admin.BanReason reason) {
    target.setAccountStatus(AccountStatus.BANNED);
    String log = String.format(
            "[ACCOUNT] %s ban %s | Lý do: %s", admin.getUsername(), target.getUsername(), reason);
    admin.addActionLog(log);
    System.out.println(log);

    // Gọi DAO để cập nhật DB
    userDAO.updateAccountStatus(target.getId(), AccountStatus.BANNED.name());
    // TODO: notificationDao.save() - báo user
  }


  // Tạo tài khoản Admin STAFF

  /**
   * Tạo tài khoản Admin STAFF mới — chỉ SystemAdmin gọi method này.
   * AdminFactory tuyệt đối chỉ được cấp bởi System — không được tạo ở ngoài.
   */
  @Override
  public Admin createStaffAdmin(String username, String password, String email) {
    SystemAdmin system = SystemAdmin.getInstance();

    // 1. Khai sinh Object trên RAM trước (Entity sẽ tự động sinh UUID cho biến id final)
    Admin newAdmin = (Admin) adminFactory.createUser(username, password, email);

    // 2. Lấy ID và thông tin vừa tạo lưu xuống Database
    boolean success = adminDAO.createAdmin(
            newAdmin.getId(),
            newAdmin.getUsername(),
            newAdmin.getHashedPassword(),
            newAdmin.getEmail(),
            "STAFF");

    if (!success) {
      throw new RuntimeException("Hệ thống lỗi: Không thể tạo Admin trong cơ sở dữ liệu.");
    }

    // 3. Đăng ký Observer
    AuctionManager.getInstance().addStaffObserver(new StaffObserver(newAdmin));
    AuctionManager.getInstance().registerUser(newAdmin);

    String log = String.format("[SYSTEM] Tạo admin STAFF: %s", username);
    system.addActionLog(log);
    System.out.println(log);

    return newAdmin;
  }

  // Duyệt Seller

  /**
   * Hệ thống tự động duyệt role Seller nếu user chưa từng bị trừ rating.
   */
  @Override
  public void autoApproveSellerRole(NormalUser user) {
    if (!ratingService.isEligible(user)) {
      throw new IllegalStateException(
              "User không đủ điều kiện để thêm role Seller (tài khoản bị khóa hoặc rating thấp).");
    }
    if (user.isHasEverBeenPenalized()) {
      throw new IllegalStateException(
              "User đã từng bị trừ rating — không đủ điều kiện tự động duyệt role Seller.");
    }
    if (user.hasRole(User.UserRole.SELLER)) {
      System.out.printf("[ACCOUNT] %s đã có role Seller.%n", user.getUsername());
      return;
    }

    user.addRole(User.UserRole.SELLER);
    String log = String.format("[SYSTEM AUTO-APPROVE] Duyệt role Seller cho: %s", user.getUsername());
    SystemAdmin.getInstance().addActionLog(log);
    System.out.println(log);

    // Gọi DAO để cập nhật DB
    sellerDAO.approveSellerRole(user.getId());
    // TODO: notificationDao.save() - báo user
  }

  // Seller request hủy phiên

  /**
   * Seller gửi yêu cầu hủy phiên đấu giá lên hệ thống.
   *
   * <p>Phiên sẽ chuyển sang trạng thái {@code CANCEL_REQUESTED} và vẫn
   * tiếp tục nhận bid cho đến khi Staff Admin approve hoặc reject.
   *
   * @param seller seller sở hữu phiên
   * @param auction phiên cần yêu cầu hủy
   * @param reason lý do yêu cầu hủy
   * @throws IllegalArgumentException nếu seller không sở hữu phiên
   * @throws IllegalStateException nếu phiên không ở OPEN hoặc RUNNING
   */
  public void requestCancelAuction(NormalUser seller, Auction auction, String reason) {
    if (!seller.hasRole(User.UserRole.SELLER)) {
      throw new IllegalArgumentException("Chỉ Seller mới có thể yêu cầu hủy phiên.");
    }
    if (!seller.getAllAuctionIds().contains(auction.getId())) {
      throw new IllegalArgumentException("Seller không sở hữu phiên đấu giá này.");
    }
    if (auction.getStatus() != Auction.AuctionStatus.OPEN) {
      throw new IllegalStateException(
              "Phiên đấu giá không thể yêu cầu hủy ở trạng thái: " + auction.getStatus());
    }

    // Notify Staff Admin để xem xét
    AuctionEvent cancelRequestEvent = new AuctionEvent(
            AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST,
            auction, null, 0L,
            String.format("Seller %s yêu cầu hủy: %s", seller.getUsername(), reason));
    AuctionManager.getInstance().notifyStaffObservers(cancelRequestEvent);
    AuctionManager.getInstance().notifyGlobalObservers(cancelRequestEvent);

    System.out.printf(
            "[ACCOUNT] Seller %s gửi yêu cầu hủy phiên %s | Lý do: %s%n",
            seller.getUsername(), auction.getId(), reason);
  }
}