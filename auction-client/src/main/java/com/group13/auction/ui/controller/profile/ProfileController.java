package com.group13.auction.ui.controller.profile;

import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.FormatUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class ProfileController extends BaseController implements PageLifecycle {

    @FXML private Label usernameLabel;
    @FXML private Label emailLabel;
    @FXML private Label rolesLabel;
    @FXML private Label statusLabel;
    @FXML private Label ratingLabel;

    @FXML
    private void initialize() {
        services().profileService().currentProfileProperty().addListener((obs, o, u) -> render(u));
    }

    @Override
    public void onShow() {
        services().profileService().loadMyProfile();
    }

    @FXML
    private void onUpgradeSeller() {
        navigator().goToUpgradeSeller();
    }

    private void render(UserDTO u) {
        if (u == null) {
            return;
        }
        usernameLabel.setText(u.getUsername());
        emailLabel.setText(u.getEmail());
        rolesLabel.setText(u.getRoles() != null ? String.join(", ", u.getRoles()) : "—");
        statusLabel.setText(u.getAccountStatus());
        ratingLabel.setText(String.valueOf(u.getRating()));
    }
}
