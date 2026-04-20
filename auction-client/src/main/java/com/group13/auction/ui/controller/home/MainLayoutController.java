package com.group13.auction.ui.controller.home;

import com.group13.auction.config.ViewPath;
import com.group13.auction.core.navigation.Navigator;
import javafx.fxml.FXML;

/**
 * Controller for the landing page.
 */
public final class MainLayoutController {

    @FXML
    private void handleStart() {
        Navigator.getInstance().goTo(ViewPath.LOGIN_VIEW);
    }

    @FXML
    private void handleGoToLogin() {
        Navigator.getInstance().goTo(ViewPath.LOGIN_VIEW);
    }

    @FXML
    private void handleGoToRegister() {
        Navigator.getInstance().goTo(ViewPath.REGISTER_VIEW);
    }
}
