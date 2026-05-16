package com.group13.auction.ui.controller.rating;

import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public final class RatingHistoryController extends BaseController implements PageLifecycle {

    @FXML private TextField userIdField;
    @FXML private Label summaryLabel;

    @FXML
    private void initialize() {
        services().ratingService().historyProperty().addListener((obs, o, h) -> {
            if (h != null) {
                summaryLabel.setText("TB: " + h.getAverageRating() + " | Tổng: " + h.getTotalRatings());
            }
        });
    }

    @FXML
    private void onLoad() {
        services().ratingService().loadUserRatings(userIdField.getText().trim());
    }

    @Override
    public void onShow() {}
}
