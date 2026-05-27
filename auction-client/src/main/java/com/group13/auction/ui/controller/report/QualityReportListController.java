package com.group13.auction.ui.controller.report;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.report.QualityReportService;
import com.group13.auction.viewmodel.report.QualityReportViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;

/** Controller danh sách báo cáo chất lượng cho Bidder hoặc Seller. */
public final class QualityReportListController {

  private final QualityReportService qualityReportService = new QualityReportService();

  private boolean sellerScope;

  @FXML private Label pageTitleLabel;
  @FXML private Label pageSubtitleLabel;
  @FXML private TableView<QualityReportViewModel> reportTable;
  @FXML private TableColumn<QualityReportViewModel, String> itemColumn;
  @FXML private TableColumn<QualityReportViewModel, String> counterpartyColumn;
  @FXML private TableColumn<QualityReportViewModel, String> statusColumn;
  @FXML private TableColumn<QualityReportViewModel, String> createdAtColumn;
  @FXML private TableColumn<QualityReportViewModel, String> auctionIdColumn;

  @FXML private TextArea reportDetailArea;
  @FXML private Label statusLabel;
  @FXML private Label emptyStateLabel;
  @FXML private ProgressIndicator loadingIndicator;

  @FXML private Button refreshButton;
  @FXML private Button backButton;
  @FXML private Button openOrdersButton;

  /** Khởi tạo bảng và tải danh sách báo cáo. */
  @FXML
  private void initialize() {
    sellerScope =
        AppContext.getInstance()
            .getScreenStateStore()
            .get(ScreenStateKeys.QUALITY_REPORT_LIST_SCOPE, String.class)
            .map(scope -> "seller".equalsIgnoreCase(scope))
            .orElse(false);

    configurePageTexts();
    configureTable();
    configureSelectionBinding();
    setBusy(false);
    showEmptyState("Chưa có báo cáo chất lượng.");
    loadReports();
  }

  @FXML
  private void handleRefresh() {
    loadReports();
  }

  @FXML
  private void handleBack() {
    if (sellerScope) {
      Navigator.getInstance().goToSellerDashboard();
    } else {
      Navigator.getInstance().goToMainLayout();
    }
  }

  @FXML
  private void handleOpenOrders() {
    Navigator.getInstance().goToMyOrders();
  }

  private void configurePageTexts() {
    if (sellerScope) {
      if (pageTitleLabel != null) {
        pageTitleLabel.setText("Báo cáo chất lượng (Seller)");
      }
      if (pageSubtitleLabel != null) {
        pageSubtitleLabel.setText(
            "Theo dõi các báo cáo người mua gửi về sản phẩm trong phiên đấu giá của bạn.");
      }
      if (counterpartyColumn != null) {
        counterpartyColumn.setText("Người báo cáo");
      }
      if (openOrdersButton != null) {
        openOrdersButton.setVisible(false);
        openOrdersButton.setManaged(false);
      }
    } else {
      if (pageTitleLabel != null) {
        pageTitleLabel.setText("Báo cáo chất lượng của tôi");
      }
      if (pageSubtitleLabel != null) {
        pageSubtitleLabel.setText("Xem trạng thái các báo cáo bạn đã gửi sau khi nhận hàng.");
      }
      if (counterpartyColumn != null) {
        counterpartyColumn.setText("Người bán");
      }
      if (openOrdersButton != null) {
        openOrdersButton.setVisible(true);
        openOrdersButton.setManaged(true);
      }
    }
  }

  private void loadReports() {
    setBusy(true);
    showStatus("Đang tải danh sách báo cáo...");
    showEmptyState("");

    var future =
        sellerScope
            ? qualityReportService.getSellerQualityReports()
            : qualityReportService.getMyQualityReports();

    future.whenComplete((reports, throwable) -> handleReportsResult(reports, throwable));
  }

  private void configureTable() {
    if (itemColumn != null) {
      itemColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
    }
    if (counterpartyColumn != null) {
      counterpartyColumn.setCellValueFactory(
          cell ->
              new javafx.beans.property.SimpleStringProperty(
                  sellerScope
                      ? cell.getValue().getReporterUsername()
                      : cell.getValue().getSellerUsername()));
    }
    if (statusColumn != null) {
      statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
    }
    if (createdAtColumn != null) {
      createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAtText"));
    }
    if (auctionIdColumn != null) {
      auctionIdColumn.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
    }
  }

  private void configureSelectionBinding() {
    if (reportTable == null) {
      return;
    }

    reportTable
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, oldValue, selectedReport) -> renderSelectedReport(selectedReport));
  }

  private void handleReportsResult(List<QualityReportViewModel> reports, Throwable throwable) {
    Platform.runLater(
        () -> {
          setBusy(false);

          if (throwable != null) {
            showStatus(errorMessage(throwable, "Không tải được danh sách báo cáo."));
            showEmptyState("Không tải được danh sách báo cáo.");
            clearDetail();
            return;
          }

          List<QualityReportViewModel> safeReports = reports == null ? List.of() : reports;
          if (reportTable != null) {
            reportTable.setItems(FXCollections.observableArrayList(safeReports));
          }

          if (safeReports.isEmpty()) {
            showStatus("Đã tải danh sách báo cáo.");
            showEmptyState("Chưa có báo cáo chất lượng nào.");
            clearDetail();
          } else {
            showStatus("Tải danh sách báo cáo thành công.");
            showEmptyState("");
            renderSelectedReport(getSelectedReport());
          }
        });
  }

  private void renderSelectedReport(QualityReportViewModel selectedReport) {
    if (selectedReport == null) {
      clearDetail();
      return;
    }

    if (reportDetailArea != null) {
      reportDetailArea.setText(
          "Mã báo cáo: "
              + selectedReport.getReportId()
              + "\nPhiên đấu giá: "
              + selectedReport.getAuctionId()
              + "\nSản phẩm: "
              + selectedReport.getTitle()
              + "\nTrạng thái: "
              + selectedReport.getStatus()
              + "\nThời gian: "
              + selectedReport.getCreatedAtText()
              + "\nNgười báo cáo: "
              + selectedReport.getReporterUsername()
              + "\nNgười bán: "
              + selectedReport.getSellerUsername()
              + "\n\nMô tả:\n"
              + selectedReport.getDescription());
    }
  }

  private QualityReportViewModel getSelectedReport() {
    if (reportTable == null) {
      return null;
    }
    return reportTable.getSelectionModel().getSelectedItem();
  }

  private void clearDetail() {
    if (reportDetailArea != null) {
      reportDetailArea.clear();
    }
  }

  private void setBusy(boolean busy) {
    if (loadingIndicator != null) {
      loadingIndicator.setVisible(busy);
      loadingIndicator.setManaged(busy);
    }
    if (refreshButton != null) {
      refreshButton.setDisable(busy);
    }
    if (backButton != null) {
      backButton.setDisable(busy);
    }
    if (openOrdersButton != null) {
      openOrdersButton.setDisable(busy);
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
