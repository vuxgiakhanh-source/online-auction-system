package com.group13.auction.ui.controller.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.state.ScreenKeys;
import com.group13.auction.ui.component.BidPriceChart;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FormatUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public final class LiveBiddingController extends BaseController implements PageLifecycle {

    @FXML private Label infoLabel;
    @FXML private Label phaseLabel;
    @FXML private BidPriceChart bidChart;
    @FXML private ListView<String> historyList;
    @FXML private TextField bidAmountField;
    @FXML private TextField maxBidField;

    private String auctionId;

    @FXML
    private void initialize() {
        services().bidHistoryService().chartPoints().addListener(
                (javafx.collections.ListChangeListener<BidDTOs.BidChartPointDTO>) c -> {
                    rebuildList();
                    rebuildChart();
                });
        services().bidService().lastUpdateProperty().addListener((obs, o, u) -> {
            if (u != null) {
                infoLabel.setText("Giá: " + FormatUtil.currency(u.getNewCurrentPrice())
                        + " | Leader: " + (u.getLeaderUsername() != null ? u.getLeaderUsername() : "—")
                        + (u.isReserveMet() ? " | ✓ Reserve" : " | Chưa đạt reserve"));
            }
        });
        services().auctionRealtimeService().phaseProperty().addListener((obs, o, p) ->
                phaseLabel.setText(p != null ? p.name() : ""));

        services().auctionRealtimeService().lastUpdateProperty().addListener((obs, o, update) -> {
            if (update == null || auctionId == null || !auctionId.equals(update.getAuctionId())) {
                return;
            }
            String status = update.getNewStatus() != null ? update.getNewStatus().toUpperCase() : "";
            if (status.contains("FINISH") || status.contains("ENDED")) {
                handleAuctionEnded(update);
            }
        });
    }

    @Override
    public void onShow() {
        auctionId = screenState().get(ScreenKeys.SELECTED_AUCTION_ID, String.class).orElse(null);
        String mode = screenState()
                .get(ScreenKeys.LIVE_SESSION_MODE, String.class)
                .orElse(ScreenKeys.BIDDING_MODE_WATCH);
        if (auctionId == null) {
            AlertUtil.showWarning("Thiếu auctionId.");
            return;
        }
        historyList.getItems().clear();
        bidChart.setPoints(java.util.List.of());
        if (ScreenKeys.BIDDING_MODE_JOIN.equals(mode)) {
            services().watchAuctionService().join(auctionId);
            infoLabel.setText("Đang tham gia (đóng cọc)...");
        } else {
            services().watchAuctionService().watch(auctionId);
            infoLabel.setText("Đang theo dõi...");
        }
        services().bidHistoryService().loadHistory(auctionId);
        services().autoBidService().loadStatus(auctionId);
    }

    @FXML
    private void onPlaceBid() {
        try {
            long amount = Long.parseLong(bidAmountField.getText().trim());
            services().bidService().placeBid(auctionId, amount);
        } catch (NumberFormatException e) {
            AlertUtil.showWarning("Giá bid không hợp lệ.");
        }
    }

    @FXML
    private void onRegisterAutoBid() {
        try {
            long max = Long.parseLong(maxBidField.getText().trim());
            services().autoBidService().register(auctionId, max);
        } catch (NumberFormatException e) {
            AlertUtil.showWarning("Max bid không hợp lệ.");
        }
    }

    @FXML
    private void onLeave() {
        if (auctionId != null) {
            services().watchAuctionService().leave(auctionId);
            navigator().goToAuctionDetail();
        }
    }

    private void handleAuctionEnded(AuctionDTOs.AuctionUpdateDTO update) {
        AppContext.getInstance().getSessionManager().getCurrentSession().ifPresent(session -> {
            if (update.getWinnerId() != null && session.getUserId().equals(update.getWinnerId())) {
                screenState().put(ScreenKeys.SELECTED_AUCTION_ID, update.getAuctionId());
                if (AlertUtil.confirm("Phiên kết thúc — bạn thắng với giá "
                        + FormatUtil.currency(update.getFinalPrice()) + ". Thanh toán ngay?")) {
                    navigator().goToPayment();
                }
            } else {
                AlertUtil.showInfo("Phiên đấu giá đã kết thúc.");
            }
        });
    }

    private void rebuildList() {
        historyList.getItems().clear();
        for (BidDTOs.BidChartPointDTO p : services().bidHistoryService().chartPoints()) {
            historyList.getItems().add(FormatUtil.currency(p.getPrice()) + " — "
                    + (p.getBidderUsername() != null ? p.getBidderUsername() : "?")
                    + (p.isAutoBid() ? " (auto)" : ""));
        }
    }

    private void rebuildChart() {
        bidChart.setPoints(services().bidHistoryService().chartPoints());
    }
}
