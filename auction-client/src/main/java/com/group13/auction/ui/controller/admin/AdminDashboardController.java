package com.group13.auction.ui.controller.admin;

import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import javafx.fxml.FXML;
// FXML handlers below

public final class AdminDashboardController extends BaseController implements PageLifecycle {

    @Override
    public void onShow() {}

    @FXML private void onUsers() { navigator().goToAdminUsers(); }
    @FXML private void onAuctions() { navigator().goToAdminAuctions(); }
    @FXML private void onSellers() { navigator().goToAdminSellerApprovals(); }
    @FXML private void onReports() { navigator().goToAdminQualityReports(); }
}
