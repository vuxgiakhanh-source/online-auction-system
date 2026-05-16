package com.group13.auction.ui.controller.profile;

import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import javafx.fxml.FXML;

public final class UpgradeSellerController extends BaseController implements PageLifecycle {

    @Override
    public void onShow() {}

    @FXML
    private void onRequest() {
        services().profileService().requestSellerRole();
    }
}
