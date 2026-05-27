package com.group13.auction.ui.controller.seller;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.service.seller.SellerAuctionService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.seller.SellerAuctionRowViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

/** Controller cho dashboard tổng quan của Seller. */
public final class SellerDashboardController {

  private final SellerAuctionService sellerAuctionService = new SellerAuctionService();

  @FXML private Label sellerNameLabel;

  @FXML private Label sellerStatusLabel;

  @FXML private Label totalAuctionLabel;

  @FXML private Label openAuctionLabel;

  @FXML private Label runningAuctionLabel;

  @FXML private Label finishedAuctionLabel;

  @FXML private Label statusLabel;

  @FXML private ProgressIndicator loadingIndicator;

  /** Khởi tạo màn hình Seller dashboard. */
  @FXML
  public void initialize() {
    renderSellerInfo();
    loadSummary();
  }

  /** Quay lại dashboard chính. */
  @FXML
  public void handleBackToHome() {
    Navigator.getInstance().goToMainLayout();
  }

  /** Tải lại thống kê Seller. */
  @FXML
  public void handleRefresh() {
    loadSummary();
  }

  /** Mở danh sách phiên của Seller. */
  @FXML
  public void handleOpenSellerAuctionList() {
    Navigator.getInstance().goToSellerAuctionList();
  }

  /** Mở form tạo phiên đấu giá. */
  @FXML
  public void handleCreateAuction() {
    Navigator.getInstance().goToCreateAuction();
  }

  /** Mở danh sách báo cáo chất lượng liên quan kênh Seller. */
  @FXML
  public void handleOpenQualityReports() {
    AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .filter(UserSession::isSeller)
        .ifPresentOrElse(
            session -> Navigator.getInstance().goToSellerQualityReports(),
            () -> AlertUtil.showError("Tài khoản hiện tại chưa có quyền Seller."));
  }

  private void renderSellerInfo() {
    UserSession session = AppContext.getInstance().getSessionManager().requireSession();
    sellerNameLabel.setText("Xin chào Seller, " + session.getUsername() + "!");

    String roleText =
        session.getRoles().isEmpty() ? "Chưa có vai trò" : String.join(", ", session.getRoles());
    sellerStatusLabel.setText(
        "Vai trò: " + roleText + "  •  Trạng thái: " + session.getAccountStatus());
  }

  private void loadSummary() {
    setLoading(true, "Đang tải thống kê kênh Seller...");

    sellerAuctionService
        .getMyAuctionRows(null)
        .thenAccept(rows -> FxThreadUtil.runOnFxThread(() -> renderSummary(rows)))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    setLoading(false, "Không tải được dữ liệu Seller.");
                    AlertUtil.showError(extractMessage(throwable));
                  });
              return null;
            });
  }

  private void renderSummary(List<SellerAuctionRowViewModel> rows) {
    int total = rows == null ? 0 : rows.size();
    int open = countByStatus(rows, "Sắp mở");
    int running = countByStatus(rows, "Đang đấu giá");
    int finished = countByStatus(rows, "Đã kết thúc");

    totalAuctionLabel.setText(String.valueOf(total));
    openAuctionLabel.setText(String.valueOf(open));
    runningAuctionLabel.setText(String.valueOf(running));
    finishedAuctionLabel.setText(String.valueOf(finished));

    setLoading(false, "Đã tải " + total + " phiên thuộc tài khoản Seller hiện tại.");
  }

  private int countByStatus(List<SellerAuctionRowViewModel> rows, String statusText) {
    if (rows == null || rows.isEmpty()) {
      return 0;
    }

    return (int) rows.stream().filter(row -> statusText.equals(row.statusText())).count();
  }

  private void setLoading(boolean loading, String message) {
    loadingIndicator.setVisible(loading);
    loadingIndicator.setManaged(loading);
    statusLabel.setText(message);
  }

  private String extractMessage(Throwable throwable) {
    Throwable current = throwable;
    if (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null
        ? "Có lỗi xảy ra khi xử lý Seller dashboard."
        : current.getMessage();
  }
}
