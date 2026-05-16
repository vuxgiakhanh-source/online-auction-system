package com.group13.auction.ui.controller.rating;

import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.core.state.ScreenKeys;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public final class RateBidderController extends BaseController implements PageLifecycle {

    @FXML private TextField bidderIdField;
    @FXML private TextField ratingField;
    @FXML private TextArea commentArea;

    @FXML
    private void onSubmit() {
        RatingDTOs.RateBidderRequestDTO dto = new RatingDTOs.RateBidderRequestDTO();
        dto.setBidderId(bidderIdField.getText().trim());
        dto.setRating(Double.parseDouble(ratingField.getText().trim()));
        dto.setComment(commentArea.getText());
        screenState().get(ScreenKeys.SELECTED_AUCTION_ID, String.class).ifPresent(dto::setAuctionId);
        try {
            services().ratingService().rateBidder(dto);
        } catch (NumberFormatException e) {
            AlertUtil.showWarning("Điểm rating không hợp lệ.");
        }
    }

    @Override
    public void onShow() {}
}
