package com.group13.auction.ui.controller.seller;

import com.group13.auction.core.state.ScreenKeys;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;

public final class SellerAuctionDetailController extends BaseController implements PageLifecycle {

    @Override
    public void onShow() {
        screenState().get(ScreenKeys.SELECTED_AUCTION_ID, String.class).ifPresent(id ->
                services().auctionCommandService().loadAuctionDetail(id));
        navigator().goToAuctionDetail();
    }
}
