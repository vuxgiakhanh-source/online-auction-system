package com.group13.auction.ui.controller.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.core.state.ScreenKeys;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FormatUtil;
import com.group13.auction.ui.util.ImageLoader;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;

public final class AuctionDetailController extends BaseController implements PageLifecycle {

    @FXML private Label titleLabel;
    @FXML private Label statusLabel;
    @FXML private Label priceLabel;
    @FXML private Label reserveLabel;
    @FXML private Label leaderLabel;
    @FXML private Label timeLabel;
    @FXML private Label descLabel;
    @FXML private ImageView mainImageView;
    @FXML private FlowPane galleryPane;
    @FXML private Button joinButton;
    @FXML private Button payButton;

    private String auctionId;

    @FXML
    private void initialize() {
        services().auctionCommandService().selectedAuctionProperty().addListener((obs, o, a) -> {
            if (a != null) {
                render(a);
            }
        });
    }

    @Override
    public void onShow() {
        auctionId = screenState().get(ScreenKeys.SELECTED_AUCTION_ID, String.class).orElse(null);
        if (auctionId == null) {
            AlertUtil.showWarning("Chưa chọn phiên đấu giá.");
            return;
        }
        services().auctionCommandService().loadAuctionDetail(auctionId);
    }

    @FXML
    private void onWatch() {
        if (auctionId != null) {
            navigator().openLiveBidding(auctionId, ScreenKeys.BIDDING_MODE_WATCH);
        }
    }

    @FXML
    private void onJoin() {
        if (auctionId != null) {
            navigator().openLiveBidding(auctionId, ScreenKeys.BIDDING_MODE_JOIN);
        }
    }

    @FXML
    private void onPay() {
        if (auctionId != null) {
            screenState().put(ScreenKeys.SELECTED_AUCTION_ID, auctionId);
            navigator().goToPayment();
        }
    }

    private void render(AuctionDTOs.AuctionDTO a) {
        var item = a.getItem();
        titleLabel.setText(item != null ? item.getName() : a.getId());
        statusLabel.setText(FormatUtil.auctionStatus(a.getStatus()));
        priceLabel.setText(FormatUtil.currency(a.getCurrentPrice()));
        reserveLabel.setText("Giá dự trữ: " + FormatUtil.currency(a.getReservePrice()));
        leaderLabel.setText(a.getCurrentLeaderUsername() != null ? a.getCurrentLeaderUsername() : "—");
        timeLabel.setText("Kết thúc: " + FormatUtil.dateTime(a.getEndTime()));
        descLabel.setText(item != null ? item.getDescription() : "");

        if (item != null && item.hasImages()) {
            ImageLoader.load(mainImageView, item.getImageUrls().get(0));
            ImageLoader.fillGallery(galleryPane, item.getImageUrls());
        } else {
            mainImageView.setImage(null);
            galleryPane.getChildren().clear();
        }

        String status = a.getStatus() != null ? a.getStatus().toUpperCase() : "";
        boolean finished = status.contains("FINISH") || status.contains("ENDED");
        boolean live = status.contains("LIVE") || status.contains("ACTIVE");
        joinButton.setVisible(live);
        joinButton.setManaged(live);
        payButton.setVisible(finished);
        payButton.setManaged(finished);
    }
}
