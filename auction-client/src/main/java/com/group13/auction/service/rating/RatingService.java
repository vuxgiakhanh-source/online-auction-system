package com.group13.auction.service.rating;

import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.ErrorMessages;
import com.group13.auction.ui.util.FxThreadUtil;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Đánh giá Seller/Bidder & xem lịch sử rating.
 */
public final class RatingService extends NetworkService implements ClientEventListener {

    private final ObjectProperty<RatingDTOs.RatingHistoryDTO> history =
            new SimpleObjectProperty<>();

    public ObjectProperty<RatingDTOs.RatingHistoryDTO> historyProperty() {
        return history;
    }

    public void rateSeller(RatingDTOs.RateSellerRequestDTO request) {
        network().rateSeller(request);
    }

    public void rateBidder(RatingDTOs.RateBidderRequestDTO request) {
        network().rateBidder(request);
    }

    public void loadUserRatings(String userId) {
        network().getUserRatings(userId);
    }

    @Override
    public void onUserRatingsReceived(RatingDTOs.RatingHistoryDTO dto) {
        history.set(dto);
    }

    @Override
    public void onRateSellerSuccess() {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Đánh giá Seller thành công."));
    }

    @Override
    public void onRateSellerFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    @Override
    public void onRateBidderSuccess() {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Đánh giá Bidder thành công."));
    }

    @Override
    public void onRateBidderFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }
}
