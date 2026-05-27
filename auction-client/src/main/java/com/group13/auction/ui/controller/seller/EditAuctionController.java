package com.group13.auction.ui.controller.seller;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.seller.SellerAuctionService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.seller.SellerAuctionRowViewModel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;

/** Controller cho màn gia hạn thời gian kết thúc của phiên đấu giá. */
public final class EditAuctionController {

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  private final SellerAuctionService sellerAuctionService = new SellerAuctionService();

  private SellerAuctionRowViewModel selectedAuction;

  @FXML private Label itemNameLabel;

  @FXML private Label auctionIdLabel;

  @FXML private Label statusLabel;

  @FXML private Label currentEndTimeLabel;

  @FXML private DatePicker newEndDatePicker;

  @FXML private TextField newEndTimeField;

  @FXML private Button saveButton;

  @FXML private Button resetButton;

  @FXML private Label messageLabel;

  @FXML private ProgressIndicator loadingIndicator;

  /** Khởi tạo màn gia hạn phiên từ dữ liệu được truyền qua screen state. */
  @FXML
  public void initialize() {
    selectedAuction =
        AppContext.getInstance()
            .getScreenStateStore()
            .get(ScreenStateKeys.SELECTED_SELLER_AUCTION_ROW, SellerAuctionRowViewModel.class)
            .orElse(null);

    if (selectedAuction == null) {
      renderMissingState();
      return;
    }

    renderAuctionInfo();
    resetNewEndTime();
    setLoading(false, "Chỉ có thể gia hạn phiên đang ở trạng thái sắp mở.");
  }

  /** Quay lại danh sách phiên của người bán. */
  @FXML
  public void handleBackToSellerList() {
    Navigator.getInstance().goToSellerAuctionList();
  }

  /** Đặt lại thời gian gia hạn đề xuất. */
  @FXML
  public void handleReset() {
    if (selectedAuction == null) {
      return;
    }

    resetNewEndTime();
    messageLabel.setText("Thời gian gia hạn đã được đặt lại.");
  }

  /** Gửi yêu cầu cập nhật thời gian kết thúc mới tới server. */
  @FXML
  public void handleSave() {
    if (selectedAuction == null) {
      AlertUtil.showWarning("Chưa chọn phiên đấu giá để chỉnh sửa.");
      return;
    }

    LocalDateTime newEndTime;
    try {
      newEndTime = parseNewEndTime();
      validateNewEndTime(newEndTime);
    } catch (IllegalArgumentException exception) {
      AlertUtil.showWarning(exception.getMessage());
      messageLabel.setText(exception.getMessage());
      return;
    }

    boolean confirmed =
        AlertUtil.confirm(
            "Xác nhận gia hạn thời gian kết thúc phiên đến "
                + DateTimeUtil.formatDateTime(newEndTime)
                + "?");

    if (!confirmed) {
      return;
    }

    updateAuctionEndTime(newEndTime);
  }

  private void renderAuctionInfo() {
    itemNameLabel.setText(selectedAuction.itemName());
    auctionIdLabel.setText("Mã phiên: " + selectedAuction.auctionId());
    statusLabel.setText("Trạng thái: " + selectedAuction.statusText());
    currentEndTimeLabel.setText("Thời gian kết thúc hiện tại: " + selectedAuction.endTimeText());

    boolean editable = selectedAuction.editable();
    saveButton.setDisable(!editable);
    resetButton.setDisable(!editable);

    if (!editable) {
      messageLabel.setText("Chỉ phiên ở trạng thái sắp mở mới có thể gia hạn.");
    }
  }

  private void renderMissingState() {
    itemNameLabel.setText("Chưa chọn phiên đấu giá");
    auctionIdLabel.setText("Không có mã phiên.");
    statusLabel.setText("Không có trạng thái.");
    currentEndTimeLabel.setText("Không có thời gian kết thúc.");

    saveButton.setDisable(true);
    resetButton.setDisable(true);
    newEndDatePicker.setDisable(true);
    newEndTimeField.setDisable(true);

    setLoading(false, "Không tìm thấy dữ liệu phiên đấu giá.");
  }

  private void resetNewEndTime() {
    LocalDateTime baseEndTime = selectedAuction.endTime();
    LocalDateTime suggestedEndTime =
        baseEndTime == null
            ? LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)
            : baseEndTime.plusMinutes(15).withSecond(0).withNano(0);

    newEndDatePicker.setValue(suggestedEndTime.toLocalDate());
    newEndTimeField.setText(suggestedEndTime.toLocalTime().format(TIME_FORMATTER));
  }

  private LocalDateTime parseNewEndTime() {
    LocalDate date = newEndDatePicker.getValue();
    if (date == null) {
      throw new IllegalArgumentException("Ngày kết thúc mới không được để trống.");
    }

    String timeText = newEndTimeField.getText();
    if (timeText == null || timeText.isBlank()) {
      throw new IllegalArgumentException("Giờ kết thúc mới không được để trống.");
    }

    try {
      LocalTime time = LocalTime.parse(timeText.trim(), TIME_FORMATTER);
      return LocalDateTime.of(date, time);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Giờ kết thúc mới phải có định dạng HH:mm.");
    }
  }

  private void validateNewEndTime(LocalDateTime newEndTime) {
    if (!"Sắp mở".equals(selectedAuction.statusText())) {
      throw new IllegalArgumentException("Chỉ phiên sắp mở mới có thể gia hạn.");
    }

    if (selectedAuction.startTime() != null && !newEndTime.isAfter(selectedAuction.startTime())) {
      throw new IllegalArgumentException("Thời gian kết thúc mới phải sau thời gian bắt đầu.");
    }

    if (selectedAuction.endTime() != null && !newEndTime.isAfter(selectedAuction.endTime())) {
      throw new IllegalArgumentException(
          "Thời gian kết thúc mới phải sau thời gian kết thúc hiện tại.");
    }
  }

  private void updateAuctionEndTime(LocalDateTime newEndTime) {
    setLoading(true, "Đang cập nhật thời gian kết thúc...");

    sellerAuctionService
        .updateOpenAuctionEndTime(selectedAuction.auctionId(), newEndTime)
        .thenAccept(
            updatedAuction ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      AlertUtil.showInfo("Thời gian kết thúc đã được cập nhật.");
                      Navigator.getInstance().goToSellerAuctionList();
                    }))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    setLoading(false, "Không cập nhật được thời gian kết thúc.");
                    AlertUtil.showError(extractMessage(throwable));
                  });
              return null;
            });
  }

  private void setLoading(boolean loading, String message) {
    loadingIndicator.setVisible(loading);
    loadingIndicator.setManaged(loading);

    saveButton.setDisable(loading || selectedAuction == null || !selectedAuction.editable());
    resetButton.setDisable(loading || selectedAuction == null || !selectedAuction.editable());
    newEndDatePicker.setDisable(loading || selectedAuction == null || !selectedAuction.editable());
    newEndTimeField.setDisable(loading || selectedAuction == null || !selectedAuction.editable());

    messageLabel.setText(message);
  }

  private String extractMessage(Throwable throwable) {
    Throwable current = throwable;
    if (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }

    String message = current.getMessage();
    return message == null || message.isBlank() ? "Không xử lý được yêu cầu." : message;
  }
}
