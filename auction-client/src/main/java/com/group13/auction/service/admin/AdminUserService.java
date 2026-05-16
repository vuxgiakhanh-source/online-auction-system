package com.group13.auction.service.admin;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.ErrorMessages;
import com.group13.auction.ui.util.FxThreadUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.List;

/**
 * Admin: quản lý user, staff, duyệt Seller.
 */
public final class AdminUserService extends NetworkService implements ClientEventListener {

    private final ObservableList<UserDTO> users = FXCollections.observableArrayList();
    private final ObservableList<UserDTO> staff = FXCollections.observableArrayList();

    public ObservableList<UserDTO> users() { return users; }
    public ObservableList<UserDTO> staff() { return staff; }

    /** User đang chờ duyệt Seller (BIDDER, chưa có role SELLER). */
    public ObservableList<UserDTO> pendingSellerRequests() {
        return FXCollections.observableArrayList(users.filtered(AdminUserService::isPendingSeller));
    }

    public static boolean isPendingSeller(UserDTO user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        boolean hasSeller = user.getRoles().stream()
                .anyMatch(r -> r != null && r.contains("SELLER"));
        boolean isBidder = user.getRoles().stream()
                .anyMatch("BIDDER"::equals);
        return isBidder && !hasSeller && "ACTIVE".equals(user.getAccountStatus());
    }

    public void loadAllUsers() { network().adminGetAllUsers(); }
    public void loadAllStaff() { network().adminGetAllStaff(); }
    public void banUser(AdminDTOs.AdminBanUserDTO request) { network().adminBanUser(request); }
    public void unbanUser(String userId) { network().adminUnbanUser(userId); }
    public void createStaff(AdminDTOs.CreateStaffAdminDTO request) { network().adminCreateStaff(request); }
    public void approveSeller(String userId) { network().adminApproveSellerRole(userId); }

    @Override
    public void onAdminAllUsersReceived(List<UserDTO> list) {
        users.setAll(list != null ? list : List.of());
    }

    @Override
    public void onAdminAllStaffReceived(List<UserDTO> list) {
        staff.setAll(list != null ? list : List.of());
    }

    @Override
    public void onAdminBanUserSuccess(UserDTO user) {
        FxThreadUtil.runOnFxThread(() -> {
            AlertUtil.showInfo("Đã khóa tài khoản: " + (user != null ? user.getUsername() : ""));
            loadAllUsers();
        });
    }

    @Override
    public void onAdminUnbanUserSuccess(UserDTO user) {
        FxThreadUtil.runOnFxThread(() -> {
            AlertUtil.showInfo("Đã mở khóa: " + (user != null ? user.getUsername() : ""));
            loadAllUsers();
        });
    }

    @Override
    public void onAdminCreateStaffSuccess(UserDTO staffUser) {
        FxThreadUtil.runOnFxThread(() ->
                AlertUtil.showInfo("Tạo staff thành công: " + (staffUser != null ? staffUser.getUsername() : "")));
    }

    @Override
    public void onAdminApproveSellerRoleSuccess(UserDTO user) {
        FxThreadUtil.runOnFxThread(() -> {
            AlertUtil.showInfo("Đã duyệt Seller: " + (user != null ? user.getUsername() : ""));
            loadAllUsers();
        });
    }

    @Override
    public void onAdminBanUserFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    @Override
    public void onAdminUnbanUserFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    @Override
    public void onAdminCreateStaffFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    @Override
    public void onAdminApproveSellerRoleFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }
}
