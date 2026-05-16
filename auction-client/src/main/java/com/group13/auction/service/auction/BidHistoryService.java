package com.group13.auction.service.auction;

import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Lịch sử bid & điểm biểu đồ realtime.
 */
public final class BidHistoryService extends NetworkService implements ClientEventListener {

    private final ObservableList<BidDTOs.BidChartPointDTO> chartPoints =
            FXCollections.observableArrayList();

    public ObservableList<BidDTOs.BidChartPointDTO> chartPoints() {
        return chartPoints;
    }

    public void loadHistory(String auctionId) {
        chartPoints.clear();
        network().getBidHistory(auctionId);
    }

    @Override
    public void onBidHistoryReceived(BidDTOs.BidHistoryResponseDTO history) {
        chartPoints.clear();
        if (history != null && history.getPoints() != null) {
            chartPoints.addAll(history.getPoints());
        }
    }

    @Override
    public void onBidChartPointUpdate(BidDTOs.BidChartPointDTO point) {
        if (point != null) {
            chartPoints.add(point);
        }
    }

    @Override
    public void onBidHistoryFailed(ErrorDTO err) {
        com.group13.auction.ui.util.FxThreadUtil.runOnFxThread(() ->
                com.group13.auction.ui.util.AlertUtil.showError(
                        com.group13.auction.ui.util.ErrorMessages.from(err)));
    }
}
