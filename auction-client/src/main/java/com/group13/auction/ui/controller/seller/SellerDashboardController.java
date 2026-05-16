package com.group13.auction.ui.controller.seller;

import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import javafx.fxml.FXML;

public final class SellerDashboardController extends BaseController implements PageLifecycle {

    @Override
    public void onShow() {}

    @FXML private void onMyAuctions() { navigator().goToSellerAuctionList(); }
    @FXML private void onCreate() { navigator().goToSellerCreateAuction(); }
}
