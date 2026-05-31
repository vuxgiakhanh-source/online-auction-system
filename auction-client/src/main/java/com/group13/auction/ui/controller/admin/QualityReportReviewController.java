package com.group13.auction.ui.controller.admin;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.report.QualityReportService;
import com.group13.auction.ui.util.ImageLoader;
import com.group13.auction.viewmodel.admin.QualityReportReviewViewModel;
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
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;

/**
 * Controller cho màn Admin review báo cáo chất lượng.
 *
 * <p>Controller chỉ hiển thị danh sách report, render chi tiết report được chọn và gọi approve /
 * reject theo API server hiện có.
 */
public final class QualityReportReviewController {

  private final QualityReportService qualityReportService = new QualityReportService();

  private final ToggleGroup statusFilterGroup = new ToggleGroup();
  private String currentStatusFilter = "PENDING";

  @FXML private ToggleButton pendingFilterButton;
  @FXML private ToggleButton approvedFilterButton;
  @FXML private ToggleButton rejectedFilterButton;
  @FXML private ToggleButton allFilterButton;

  @FXML private TableView<QualityReportReviewViewModel> reportTable;
  @FXML private TableColumn<QualityReportReviewViewModel, String> reportIdColumn;
  @FXML private TableColumn<QualityReportReviewViewModel, String> reporterColumn;
  @FXML private TableColumn<QualityReportReviewViewModel, String> auctionColumn;
  @FXML private TableColumn<QualityReportReviewViewModel, String> titleColumn;
  @FXML private TableColumn<QualityReportReviewViewModel, String> reasonColumn;
  @FXML private TableColumn<QualityReportReviewViewModel, String> statusColumn;
  @FXML private TableColumn<QualityReportReviewViewModel, String> createdAtColumn;

  @FXML private TextArea reportDetailArea;
  @FXML private FlowPane evidenceGalleryPane;

  @FXML private Label statusLabel;
  @FXML private Label emptyStateLabel;
  @FXML private ProgressIndicator loadingIndicator;

  @FXML private Button refreshButton;
  @FXML private Button approveButton;
  @FXML private Button rejectButton;
  @FXML private Button backButton;

  /** Khởi tạo bảng review báo cáo chất lượng và tải dữ liệu lần đầu. */
  @FXML
  private void initialize() {
    configureStatusFilters();
    configureTable();
    configureSelectionBinding();
    setBusy(false);
    setReviewButtonsDisabled(true);
    showEmptyState("Chưa có báo cáo chất lượng.");
    loadReports();
  }

  @FXML
  private void handleFilterChange() {
    Toggle selected = statusFilterGroup.getSelectedToggle();
    if (selected == null) {
      return;
    }

    String nextFilter = resolveStatusFilter(selected);
    if (nextFilter.equals(currentStatusFilter)) {
      return;
    }

    currentStatusFilter = nextFilter;
    loadReports();
  }

  @FXML
  private void handleRefresh() {
    loadReports();
  }

  @FXML
  private void handleApproveReport() {
    QualityReportReviewViewModel selectedReport = getSelectedReport();
    if (selectedReport == null) {
      showStatus("Vui lòng chọn report cần duyệt.");
      return;
    }

    if (!selectedReport.isReviewable()) {
      showStatus("Report này không thể xử lý ở trạng thái hiện tại.");
      return;
    }

    setBusy(true);
    showStatus("Đang duyệt report...");

    qualityReportService
        .resolveQualityReport(selectedReport.getReportId())
        .whenComplete((message, throwable) -> handleMutationResult(message, throwable));
  }

  @FXML
  private void handleRejectReport() {
    QualityReportReviewViewModel selectedReport = getSelectedReport();
    if (selectedReport == null) {
      showStatus("Vui lòng chọn report cần từ chối.");
      return;
    }

    if (!selectedReport.isReviewable()) {
      showStatus("Report này không thể xử lý ở trạng thái hiện tại.");
      return;
    }

    setBusy(true);
    showStatus("Đang từ chối report...");

    qualityReportService
        .rejectQualityReport(selectedReport.getReportId())
        .whenComplete(
            (ignored, throwable) -> handleMutationResult("Đã từ chối report.", throwable));
  }

  @FXML
  private void handleBackToDashboard() {
    Navigator.getInstance().goToAdminDashboard();
  }

  private void loadReports() {
    setBusy(true);
    showStatus("Đang tải danh sách báo cáo chất lượng...");
    showEmptyState("");

    qualityReportService
        .getQualityReportsForAdmin(currentStatusFilter)
        .whenComplete((reports, throwable) -> handleReportsResult(reports, throwable));
  }

  private void configureStatusFilters() {
    bindFilterButton(pendingFilterButton, "PENDING");
    bindFilterButton(approvedFilterButton, "APPROVED");
    bindFilterButton(rejectedFilterButton, "REJECTED");
    bindFilterButton(allFilterButton, "ALL");

    if (pendingFilterButton != null) {
      pendingFilterButton.setSelected(true);
    }
  }

  private void bindFilterButton(ToggleButton button, String filterValue) {
    if (button == null) {
      return;
    }

    button.setToggleGroup(statusFilterGroup);
    button.setUserData(filterValue);
  }

  private String resolveStatusFilter(Toggle selectedToggle) {
    Object value = selectedToggle == null ? null : selectedToggle.getUserData();
    if (value instanceof String filter && !filter.isBlank()) {
      return filter;
    }
    return "PENDING";
  }

  private void configureTable() {
    if (reportIdColumn != null) {
      reportIdColumn.setCellValueFactory(new PropertyValueFactory<>("reportId"));
    }
    if (reporterColumn != null) {
      reporterColumn.setCellValueFactory(new PropertyValueFactory<>("reporterId"));
    }
    if (auctionColumn != null) {
      auctionColumn.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
    }
    if (titleColumn != null) {
      titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
    }
    if (reasonColumn != null) {
      reasonColumn.setCellValueFactory(new PropertyValueFactory<>("reason"));
    }
    if (statusColumn != null) {
      statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
    }
    if (createdAtColumn != null) {
      createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAtText"));
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

  private void handleReportsResult(
      List<QualityReportReviewViewModel> reports, Throwable throwable) {
    Platform.runLater(
        () -> {
          setBusy(false);

          if (throwable != null) {
            showStatus(errorMessage(throwable, "Không tải được danh sách báo cáo chất lượng."));
            showEmptyState("Không tải được danh sách báo cáo chất lượng.");
            clearDetail();
            setReviewButtonsDisabled(true);
            return;
          }

          List<QualityReportReviewViewModel> safeReports = reports == null ? List.of() : reports;
          if (reportTable != null) {
            reportTable.setItems(FXCollections.observableArrayList(safeReports));
          }

          if (safeReports.isEmpty()) {
            showStatus("Đã tải danh sách báo cáo chất lượng.");
            showEmptyState(emptyMessageForFilter(currentStatusFilter));
            clearDetail();
          } else {
            showStatus("Tải danh sách báo cáo chất lượng thành công.");
            showEmptyState("");
          }

          renderSelectedReport(getSelectedReport());
        });
  }

  private void handleMutationResult(String successMessage, Throwable throwable) {
    Platform.runLater(
        () -> {
          if (throwable != null) {
            setBusy(false);
            showStatus(errorMessage(throwable, "Không cập nhật được report."));
            renderSelectedReport(getSelectedReport());
            return;
          }

          showStatus(successMessage == null ? "Cập nhật report thành công." : successMessage);
          loadReports();
        });
  }

  private void renderSelectedReport(QualityReportReviewViewModel selectedReport) {
    if (selectedReport == null) {
      clearDetail();
      setReviewButtonsDisabled(true);
      return;
    }

    if (reportDetailArea != null) {
      reportDetailArea.setText(
          "Report ID: "
              + selectedReport.getReportId()
              + "\nNgười gửi: "
              + selectedReport.getReporterId()
              + "\nPhiên đấu giá: "
              + selectedReport.getAuctionId()
              + "\nTiêu đề: "
              + selectedReport.getTitle()
              + "\nLý do: "
              + selectedReport.getReason()
              + "\nTrạng thái: "
              + selectedReport.getStatus()
              + "\nThời gian tạo: "
              + selectedReport.getCreatedAtText()
              + "\n\nMô tả:\n"
              + selectedReport.getDescription());
    }

    if (evidenceGalleryPane != null) {
      ImageLoader.fillPreviewableGallery(evidenceGalleryPane, selectedReport.getEvidenceUrls());
    }

    updateReviewButtons(selectedReport);
  }

  private void updateReviewButtons(QualityReportReviewViewModel selectedReport) {
    boolean disabled = selectedReport == null || !selectedReport.isReviewable();
    if (approveButton != null) {
      approveButton.setDisable(disabled);
    }
    if (rejectButton != null) {
      rejectButton.setDisable(disabled);
    }
  }

  private void setFilterButtonsDisabled(boolean disabled) {
    if (pendingFilterButton != null) {
      pendingFilterButton.setDisable(disabled);
    }
    if (approvedFilterButton != null) {
      approvedFilterButton.setDisable(disabled);
    }
    if (rejectedFilterButton != null) {
      rejectedFilterButton.setDisable(disabled);
    }
    if (allFilterButton != null) {
      allFilterButton.setDisable(disabled);
    }
  }

  private void setReviewButtonsDisabled(boolean disabled) {
    if (approveButton != null) {
      approveButton.setDisable(disabled);
    }
    if (rejectButton != null) {
      rejectButton.setDisable(disabled);
    }
  }

  private void clearDetail() {
    if (reportDetailArea != null) {
      reportDetailArea.clear();
    }
    if (evidenceGalleryPane != null) {
      evidenceGalleryPane.getChildren().clear();
    }
  }

  private QualityReportReviewViewModel getSelectedReport() {
    if (reportTable == null) {
      return null;
    }

    return reportTable.getSelectionModel().getSelectedItem();
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
    setFilterButtonsDisabled(busy);

    if (busy) {
      setReviewButtonsDisabled(true);
    } else {
      updateReviewButtons(getSelectedReport());
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

  private String emptyMessageForFilter(String filter) {
    return switch (filter) {
      case "APPROVED" -> "Không có báo cáo đã duyệt.";
      case "REJECTED" -> "Không có báo cáo đã từ chối.";
      case "ALL" -> "Không có báo cáo chất lượng nào.";
      default -> "Không có báo cáo đang chờ duyệt.";
    };
  }
}
