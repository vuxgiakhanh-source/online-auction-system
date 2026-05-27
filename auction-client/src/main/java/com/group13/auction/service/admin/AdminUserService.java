package com.group13.auction.service.admin;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.mapper.UserViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.admin.AccountBanViewModel;
import com.group13.auction.viewmodel.admin.SellerApprovalViewModel;
import com.group13.auction.viewmodel.admin.StaffAdminViewModel;
import com.group13.auction.viewmodel.admin.UserModerationViewModel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service xử lý các thao tác quản trị người dùng ở phía client.
 *
 * <p>Lớp này chỉ kiểm tra input cơ bản, gọi API admin thật của server và map DTO sang view model.
 * Việc ban, mở khóa, duyệt Seller và cập nhật dữ liệu là trách nhiệm của server.
 */
public final class AdminUserService {

  private final ClientNetworkFacade networkFacade;

  /** Tạo service dùng network facade mặc định của ứng dụng. */
  public AdminUserService() {
    this(ClientNetworkFacade.getDefault());
  }

  /**
   * Tạo service với dependency truyền vào, hữu ích cho test.
   *
   * @param networkFacade facade tầng network
   */
  public AdminUserService(ClientNetworkFacade networkFacade) {
    this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
  }

  /**
   * Lấy toàn bộ người dùng để hiển thị trong màn Admin User Moderation.
   *
   * @return future chứa danh sách user view model
   */
  public CompletableFuture<List<UserModerationViewModel>> getAllUsers() {
    if (!currentUserIsAdmin()) {
      return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.adminGetAllUsers(),
            PacketType.ADMIN_GET_ALL_USERS_SUCCESS,
            UserDTO[].class,
            "Không tải được danh sách người dùng.")
        .thenApply(UserViewModelMapper::toModerationViewModels);
  }

  /**
   * Lấy danh sách tài khoản đang bị khóa (active) từ bảng {@code account_bans}.
   *
   * @return future chứa danh sách bản ghi khóa
   */
  public CompletableFuture<List<AccountBanViewModel>> getAccountBans() {
    if (!currentUserIsAdmin()) {
      return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.adminGetAccountBans(),
            PacketType.ADMIN_GET_ACCOUNT_BANS_SUCCESS,
            AdminDTOs.AccountBanDTO[].class,
            "Không tải được danh sách tài khoản bị khóa.")
        .thenApply(UserViewModelMapper::toAccountBanViewModels);
  }

  /**
   * Ban một người dùng bằng API admin thật của server.
   *
   * @param userId mã người dùng cần ban
   * @param reason lý do ban, ví dụ {@code FRAUD}, {@code LOW_RATING}, {@code OTHER}
   * @return future chứa user sau khi server cập nhật
   */
  public CompletableFuture<UserModerationViewModel> banUser(String userId, String reason) {
    if (!currentUserIsAdmin()) {
      return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
    }
    if (isBlank(userId)) {
      return AuctionServiceSupport.failedFuture("Thiếu mã người dùng cần ban.");
    }
    if (isBlank(reason)) {
      return AuctionServiceSupport.failedFuture("Vui lòng chọn lý do ban tài khoản.");
    }

    AdminDTOs.AdminBanUserDTO request = new AdminDTOs.AdminBanUserDTO();
    request.setUserId(userId.trim());
    request.setReason(reason.trim());

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.adminBanUser(request),
            PacketType.ADMIN_BAN_USER_SUCCESS,
            UserDTO.class,
            "Không ban được tài khoản.")
        .thenApply(UserViewModelMapper::toModerationViewModel);
  }

  /**
   * Mở khóa một người dùng đã bị ban.
   *
   * @param userId mã người dùng cần mở khóa
   * @return future chứa user sau khi server cập nhật
   */
  public CompletableFuture<UserModerationViewModel> unbanUser(String userId) {
    if (!currentUserIsAdmin()) {
      return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
    }
    if (isBlank(userId)) {
      return AuctionServiceSupport.failedFuture("Thiếu mã người dùng cần mở khóa.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.adminUnbanUser(userId.trim()),
            PacketType.ADMIN_UNBAN_USER_SUCCESS,
            UserDTO.class,
            "Không mở khóa được tài khoản.")
        .thenApply(UserViewModelMapper::toModerationViewModel);
  }

  /**
   * Lấy danh sách Staff Admin. Chỉ MASTER Admin được sử dụng chức năng này.
   *
   * @return future chứa danh sách Staff Admin
   */
  public CompletableFuture<List<StaffAdminViewModel>> getAllStaffAdmins() {
    if (!currentUserIsMasterAdmin()) {
      return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền System Admin.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.adminGetAllStaff(),
            PacketType.ADMIN_GET_ALL_STAFF_SUCCESS,
            UserDTO[].class,
            "Không tải được danh sách Staff Admin.")
        .thenApply(UserViewModelMapper::toStaffAdminViewModels);
  }

  /**
   * Tạo tài khoản Staff Admin mới bằng API System Admin.
   *
   * @param username tên đăng nhập Staff Admin
   * @param password mật khẩu Staff Admin
   * @param email email Staff Admin
   * @return future chứa Staff Admin vừa được tạo
   */
  public CompletableFuture<StaffAdminViewModel> createStaffAdmin(
      String username, String password, String email) {
    if (!currentUserIsMasterAdmin()) {
      return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền System Admin.");
    }
    if (isBlank(username)) {
      return AuctionServiceSupport.failedFuture("Vui lòng nhập tên đăng nhập Staff Admin.");
    }
    if (isBlank(password)) {
      return AuctionServiceSupport.failedFuture("Vui lòng nhập mật khẩu Staff Admin.");
    }
    if (isBlank(email)) {
      return AuctionServiceSupport.failedFuture("Vui lòng nhập email Staff Admin.");
    }

    AdminDTOs.CreateStaffAdminDTO request = new AdminDTOs.CreateStaffAdminDTO();
    request.setUsername(username.trim());
    request.setPassword(password);
    request.setEmail(email.trim());

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.adminCreateStaff(request),
            PacketType.ADMIN_CREATE_STAFF_SUCCESS,
            UserDTO.class,
            "Không tạo được Staff Admin.")
        .thenApply(UserViewModelMapper::toStaffAdminViewModel);
  }

  /**
   * Lấy danh sách candidate có thể duyệt quyền Seller.
   *
   * <p>Server hiện có API {@code ADMIN_APPROVE_SELLER_ROLE}, nhưng chưa có API riêng để list
   * pending seller request. Vì vậy client không fake request; danh sách này được lọc an toàn từ API
   * get all users hiện có.
   *
   * @return future chứa danh sách candidate duyệt Seller
   */
  public CompletableFuture<List<SellerApprovalViewModel>> getSellerApprovalCandidates() {
    if (!currentUserIsAdmin()) {
      return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.adminGetAllUsers(),
            PacketType.ADMIN_GET_ALL_USERS_SUCCESS,
            UserDTO[].class,
            "Không tải được danh sách ứng viên Seller.")
        .thenApply(UserViewModelMapper::toSellerApprovalViewModels);
  }

  private boolean currentUserIsMasterAdmin() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(session -> session.isMasterAdmin())
        .orElse(false);
  }

  /**
   * Duyệt quyền Seller cho một user bằng API thật của server.
   *
   * @param userId mã người dùng cần duyệt Seller
   * @return future chứa user sau khi server cập nhật
   */
  public CompletableFuture<UserModerationViewModel> approveSellerRole(String userId) {
    if (!currentUserIsAdmin()) {
      return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
    }
    if (isBlank(userId)) {
      return AuctionServiceSupport.failedFuture("Thiếu mã người dùng cần duyệt Seller.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.adminApproveSellerRole(userId.trim()),
            PacketType.ADMIN_APPROVE_SELLER_ROLE_SUCCESS,
            UserDTO.class,
            "Không duyệt được quyền Seller.")
        .thenApply(UserViewModelMapper::toModerationViewModel);
  }

  private boolean currentUserIsAdmin() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(session -> session.isAdmin())
        .orElse(false);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
