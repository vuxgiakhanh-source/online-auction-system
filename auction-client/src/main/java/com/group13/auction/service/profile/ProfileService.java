package com.group13.auction.service.profile;

import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.ErrorMessages;
import com.group13.auction.ui.util.FxThreadUtil;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Luồng USER: profile & nâng cấp Seller.
 */
public final class ProfileService extends NetworkService implements ClientEventListener {

    private final ObjectProperty<UserDTO> currentProfile = new SimpleObjectProperty<>();

    public ObjectProperty<UserDTO> currentProfileProperty() {
        return currentProfile;
    }

    public void loadMyProfile() {
        network().getMyProfile();
    }

    public void loadUserProfile(String userId) {
        network().getUserProfile(userId);
    }

    public void requestSellerRole() {
        network().requestSellerRole();
    }

    @Override
    public void onUserProfileReceived(UserDTO user) {
        currentProfile.set(user);
        refreshSessionIfSelf(user);
    }

    @Override
    public void onSellerRoleApproved(UserDTO updatedUser) {
        currentProfile.set(updatedUser);
        refreshSessionIfSelf(updatedUser);
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Tài khoản đã được duyệt Seller."));
    }

    @Override
    public void onRequestSellerRoleSuccess() {
        FxThreadUtil.runOnFxThread(() ->
                AlertUtil.showInfo("Đã gửi yêu cầu nâng cấp Seller. Chờ admin duyệt hoặc hệ thống tự duyệt."));
    }

    @Override
    public void onRequestSellerRoleFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    @Override
    public void onSellerRoleRejected(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showWarning(
                "Yêu cầu Seller bị từ chối: " + ErrorMessages.from(error)));
    }

    @Override
    public void onUserProfileFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    private void refreshSessionIfSelf(UserDTO user) {
        AppContext.getInstance().getSessionManager().getCurrentSession().ifPresent(session -> {
            if (user.getId() != null && user.getId().equals(session.getUserId())) {
                AppContext.getInstance().getSessionManager().startSession(
                        UserSession.from(session.getToken(), user));
            }
        });
    }
}
