package com.group13.auction.ui.controller.rating;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.rating.RatingService;
import com.group13.auction.viewmodel.rating.RatingHistoryViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller cho màn Rating Center.
 *
 * <p>Controller chỉ đọc input, validate UI tối thiểu thông qua service, gọi server và render kết
 * quả. Các rule nghiệp vụ rating được xử lý ở server.
 */
public final class RatingController {

  private final RatingService ratingService = new RatingService();

  @FXML private ChoiceBox<String> targetTypeChoiceBox;
  @FXML private ChoiceBox<Integer> ratingChoiceBox;

  @FXML private TextField targetUserIdField;
  @FXML private TextField auctionIdField;
  @FXML private TextArea commentArea;

  @FXML private TextField lookupUserIdField;

  @FXML private TableView<RatingHistoryViewModel> ratingTable;
  @FXML private TableColumn<RatingHistoryViewModel, String> reviewerColumn;
  @FXML private TableColumn<RatingHistoryViewModel, String> targetUserColumn;
  @FXML private TableColumn<RatingHistoryViewModel, String> auctionColumn;
  @FXML private TableColumn<RatingHistoryViewModel, String> scoreColumn;
  @FXML private TableColumn<RatingHistoryViewModel, String> commentColumn;
  @FXML private TableColumn<RatingHistoryViewModel, String> createdAtColumn;

  @FXML private Label statusLabel;
  @FXML private Label emptyStateLabel;
  @FXML private ProgressIndicator loadingIndicator;

  @FXML private Button submitButton;
  @FXML private Button refreshButton;
  @FXML private Button backButton;

  /** Khởi tạo form rating và bảng lịch sử rating. */
  @FXML
  private void initialize() {
    configureChoices();
    configureTable();
    setBusy(false);
    showStatus("Nhập thông tin rating hoặc tải lịch sử rating theo mã người dùng.");
    showEmptyState("Chưa tải lịch sử rating.");
  }

  @FXML
  private void handleSubmitRating() {
    String targetType = targetTypeChoiceBox == null ? "Seller" : targetTypeChoiceBox.getValue();
    String targetUserId = textOf(targetUserIdField);
    String auctionId = textOf(auctionIdField);
    Integer score = ratingChoiceBox == null ? null : ratingChoiceBox.getValue();
    String comment = textOf(commentArea);

    if (score == null) {
      showStatus("Vui lòng chọn điểm rating.");
      return;
    }

    setBusy(true);
    showStatus("Đang gửi rating...");

    if ("Seller".equals(targetType)) {
      ratingService
          .rateSeller(targetUserId, auctionId, score.doubleValue(), comment)
          .whenComplete((ignored, throwable) -> handleSubmitResult(throwable));
      return;
    }

    ratingService
        .rateBidder(targetUserId, auctionId, score.doubleValue(), comment)
        .whenComplete((ignored, throwable) -> handleSubmitResult(throwable));
  }

  @FXML
  private void handleLoadRatings() {
    String userId = textOf(lookupUserIdField);
    if (userId.isBlank()) {
      showStatus("Vui lòng nhập mã người dùng cần xem rating.");
      return;
    }

    setBusy(true);
    showStatus("Đang tải lịch sử rating...");

    ratingService
        .getRatings(userId)
        .whenComplete((ratings, throwable) -> handleRatingHistoryResult(ratings, throwable));
  }

  @FXML
  private void handleBackToMain() {
    Navigator.getInstance().goToMainLayout();
  }

  private void configureChoices() {
    if (targetTypeChoiceBox != null) {
      targetTypeChoiceBox.setItems(FXCollections.observableArrayList("Seller", "Bidder"));
      targetTypeChoiceBox.setValue("Seller");
    }

    if (ratingChoiceBox != null) {
      ratingChoiceBox.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5));
      ratingChoiceBox.setValue(5);
    }
  }

  private void configureTable() {
    if (reviewerColumn != null) {
      reviewerColumn.setCellValueFactory(new PropertyValueFactory<>("reviewerId"));
    }
    if (targetUserColumn != null) {
      targetUserColumn.setCellValueFactory(new PropertyValueFactory<>("targetUserId"));
    }
    if (auctionColumn != null) {
      auctionColumn.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
    }
    if (scoreColumn != null) {
      scoreColumn.setCellValueFactory(new PropertyValueFactory<>("scoreText"));
    }
    if (commentColumn != null) {
      commentColumn.setCellValueFactory(new PropertyValueFactory<>("comment"));
    }
    if (createdAtColumn != null) {
      createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAtText"));
    }
  }

  private void handleSubmitResult(Throwable throwable) {
    Platform.runLater(
        () -> {
          setBusy(false);
          if (throwable != null) {
            showStatus(errorMessage(throwable, "Không gửi được rating."));
            return;
          }

          showStatus("Gửi rating thành công.");
          clearSubmitForm();
        });
  }

  private void handleRatingHistoryResult(
      List<RatingHistoryViewModel> ratings, Throwable throwable) {
    Platform.runLater(
        () -> {
          setBusy(false);

          if (throwable != null) {
            showStatus(errorMessage(throwable, "Không tải được lịch sử rating."));
            showEmptyState("Không tải được lịch sử rating.");
            return;
          }

          List<RatingHistoryViewModel> safeRatings = ratings == null ? List.of() : ratings;
          if (ratingTable != null) {
            ratingTable.setItems(FXCollections.observableArrayList(safeRatings));
          }

          if (safeRatings.isEmpty()) {
            showStatus("Đã tải lịch sử rating.");
            showEmptyState("Không có rating nào cho người dùng này.");
          } else {
            showStatus("Tải lịch sử rating thành công.");
            showEmptyState("");
          }
        });
  }

  private void clearSubmitForm() {
    if (targetUserIdField != null) {
      targetUserIdField.clear();
    }
    if (auctionIdField != null) {
      auctionIdField.clear();
    }
    if (commentArea != null) {
      commentArea.clear();
    }
    if (ratingChoiceBox != null) {
      ratingChoiceBox.setValue(5);
    }
  }

  private void setBusy(boolean busy) {
    if (loadingIndicator != null) {
      loadingIndicator.setVisible(busy);
      loadingIndicator.setManaged(busy);
    }
    if (submitButton != null) {
      submitButton.setDisable(busy);
    }
    if (refreshButton != null) {
      refreshButton.setDisable(busy);
    }
    if (backButton != null) {
      backButton.setDisable(busy);
    }
  }

  private void showStatus(String message) {
    if (statusLabel != null) {
      statusLabel.setText(message == null ? "" : message);
    }
  }

  private void showEmptyState(String message) {
    if (emptyStateLabel != null) {
      boolean visible = message != null && !message.isBlank();
      emptyStateLabel.setText(message == null ? "" : message);
      emptyStateLabel.setVisible(visible);
      emptyStateLabel.setManaged(visible);
    }
  }

  private String textOf(TextField field) {
    return field == null || field.getText() == null ? "" : field.getText().trim();
  }

  private String textOf(TextArea area) {
    return area == null || area.getText() == null ? "" : area.getText().trim();
  }

  private String errorMessage(Throwable throwable, String fallbackMessage) {
    Throwable root = unwrap(throwable);
    String message = root.getMessage();
    return message == null || message.isBlank() ? fallbackMessage : message;
  }

  private Throwable unwrap(Throwable throwable) {
    if (throwable instanceof CompletionException && throwable.getCause() != null) {
      return throwable.getCause();
    }
    return throwable;
  }
}
