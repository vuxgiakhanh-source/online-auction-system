package com.group13.auction.ui.controller.admin;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.admin.AdminSystemBankService;
import com.group13.auction.viewmodel.admin.FinancialTransactionPageViewModel;
import com.group13.auction.viewmodel.admin.FinancialTransactionViewModel;
import com.group13.auction.viewmodel.admin.SystemBankSummaryViewModel;
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
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/** Controller for the Admin System Bank screen. */
public final class SystemBankController {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 50;

  private final AdminSystemBankService systemBankService = new AdminSystemBankService();
  private int pendingRequests = 0;
  private boolean loadHasError = false;
  private int currentPage = DEFAULT_PAGE;
  private int currentPageSize = DEFAULT_PAGE_SIZE;
  private int totalPages = 1;
  private int totalItems = 0;

  @FXML private Label totalBalanceLabel;
  @FXML private Label totalFundsHeldLabel;
  @FXML private Label totalPaymentReceivedLabel;
  @FXML private Label totalTaxCollectedLabel;
  @FXML private Label totalDepositForfeitedLabel;
  @FXML private Label totalPayoutToSellerLabel;
  @FXML private Label totalRefundedToWinnerLabel;
  @FXML private Label updatedAtLabel;
  @FXML private Label statusLabel;
  @FXML private Label emptyStateLabel;
  @FXML private Label paginationLabel;

  @FXML private ChoiceBox<TransactionFilter> transactionTypeChoiceBox;
  @FXML private TextField auctionIdField;

  @FXML private TableView<FinancialTransactionViewModel> transactionTable;
  @FXML private TableColumn<FinancialTransactionViewModel, String> transactionIdColumn;
  @FXML private TableColumn<FinancialTransactionViewModel, String> typeColumn;
  @FXML private TableColumn<FinancialTransactionViewModel, String> amountColumn;
  @FXML private TableColumn<FinancialTransactionViewModel, String> senderColumn;
  @FXML private TableColumn<FinancialTransactionViewModel, String> receiverColumn;
  @FXML private TableColumn<FinancialTransactionViewModel, String> auctionColumn;
  @FXML private TableColumn<FinancialTransactionViewModel, String> createdAtColumn;

  @FXML private Button refreshButton;
  @FXML private Button clearFilterButton;
  @FXML private Button backButton;
  @FXML private Button previousPageButton;
  @FXML private Button nextPageButton;
  @FXML private ProgressIndicator loadingIndicator;

  @FXML
  private void initialize() {
    configureFilters();
    configureTable();
    setBusy(false);
    loadSystemBank();
  }

  @FXML
  private void handleRefresh() {
    currentPage = DEFAULT_PAGE;
    loadSystemBank();
  }

  @FXML
  private void handleClearFilters() {
    if (transactionTypeChoiceBox != null) {
      transactionTypeChoiceBox.setValue(TransactionFilter.ALL);
    }
    if (auctionIdField != null) {
      auctionIdField.clear();
    }
    currentPage = DEFAULT_PAGE;
    loadTransactions();
  }

  @FXML
  private void handleBackToDashboard() {
    Navigator.getInstance().goToAdminDashboard();
  }

  @FXML
  private void handlePreviousPage() {
    if (currentPage <= 1) {
      return;
    }
    currentPage--;
    loadTransactions();
  }

  @FXML
  private void handleNextPage() {
    if (currentPage >= totalPages) {
      return;
    }
    currentPage++;
    loadTransactions();
  }

  private void loadSystemBank() {
    loadHasError = false;
    showStatus("Dang tai du lieu System Bank...");
    showEmptyState("");

    beginRequest();
    systemBankService.getSummary().whenComplete(this::handleSummaryResult);
    loadTransactions(false);
  }

  private void loadTransactions() {
    loadTransactions(true);
  }

  private void loadTransactions(boolean resetStatus) {
    if (resetStatus) {
      loadHasError = false;
      showStatus("Dang tai lich su giao dich tai chinh...");
      showEmptyState("");
    }
    TransactionFilter filter =
        transactionTypeChoiceBox == null
            ? TransactionFilter.ALL
            : transactionTypeChoiceBox.getValue();
    String auctionId = auctionIdField == null ? null : auctionIdField.getText();
    String transactionType = filter == null ? null : filter.packetValue();

    beginRequest();
    systemBankService
        .getTransactions(transactionType, auctionId, currentPage, currentPageSize)
        .whenComplete(this::handleTransactionsResult);
  }

  private void configureFilters() {
    if (transactionTypeChoiceBox != null) {
      transactionTypeChoiceBox.setItems(
          FXCollections.observableArrayList(TransactionFilter.values()));
      transactionTypeChoiceBox.setValue(TransactionFilter.ALL);
    }
  }

  private void configureTable() {
    if (transactionIdColumn != null) {
      transactionIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
    }
    if (typeColumn != null) {
      typeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionTypeText"));
    }
    if (amountColumn != null) {
      amountColumn.setCellValueFactory(new PropertyValueFactory<>("amountText"));
    }
    if (senderColumn != null) {
      senderColumn.setCellValueFactory(new PropertyValueFactory<>("senderId"));
    }
    if (receiverColumn != null) {
      receiverColumn.setCellValueFactory(new PropertyValueFactory<>("receiverId"));
    }
    if (auctionColumn != null) {
      auctionColumn.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
    }
    if (createdAtColumn != null) {
      createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAtText"));
    }
  }

  private void handleSummaryResult(SystemBankSummaryViewModel summary, Throwable throwable) {
    Platform.runLater(
        () -> {
          if (throwable != null) {
            loadHasError = true;
            showStatus(errorMessage(throwable, "Khong tai duoc tong quan System Bank."));
            finishRequest();
            return;
          }
          applySummary(summary);
          if (finishRequest() && !loadHasError) {
            showStatus("Du lieu System Bank da duoc cap nhat.");
          }
        });
  }

  private void handleTransactionsResult(
      FinancialTransactionPageViewModel transactionPage, Throwable throwable) {
    Platform.runLater(
        () -> {
          if (throwable != null) {
            loadHasError = true;
            showStatus(errorMessage(throwable, "Khong tai duoc lich su giao dich tai chinh."));
            showEmptyState("Khong tai duoc lich su giao dich tai chinh.");
            finishRequest();
            return;
          }

          List<FinancialTransactionViewModel> safeTransactions =
              transactionPage == null ? List.of() : transactionPage.getTransactions();
          applyTransactionPage(transactionPage);
          if (transactionTable != null) {
            transactionTable.setItems(FXCollections.observableArrayList(safeTransactions));
          }
          if (safeTransactions.isEmpty()) {
            showEmptyState("Chua co giao dich tai chinh phu hop.");
          } else {
            showEmptyState("");
          }
          if (finishRequest() && !loadHasError) {
            showStatus("Du lieu System Bank da duoc cap nhat.");
          }
        });
  }

  private void applySummary(SystemBankSummaryViewModel summary) {
    if (summary == null) {
      return;
    }
    setText(totalBalanceLabel, summary.getTotalBalanceText());
    setText(totalFundsHeldLabel, summary.getTotalFundsHeldText());
    setText(totalPaymentReceivedLabel, summary.getTotalPaymentReceivedText());
    setText(totalTaxCollectedLabel, summary.getTotalTaxCollectedText());
    setText(totalDepositForfeitedLabel, summary.getTotalDepositForfeitedText());
    setText(totalPayoutToSellerLabel, summary.getTotalPayoutToSellerText());
    setText(totalRefundedToWinnerLabel, summary.getTotalRefundedToWinnerText());
    setText(updatedAtLabel, "Cap nhat: " + summary.getUpdatedAtText());
  }

  private void applyTransactionPage(FinancialTransactionPageViewModel transactionPage) {
    if (transactionPage == null) {
      currentPage = DEFAULT_PAGE;
      currentPageSize = DEFAULT_PAGE_SIZE;
      totalPages = 1;
      totalItems = 0;
      updatePaginationControls();
      return;
    }
    currentPage = transactionPage.getPage();
    currentPageSize = transactionPage.getPageSize();
    totalPages = transactionPage.getTotalPages();
    totalItems = transactionPage.getTotalItems();
    updatePaginationControls();
  }

  private void beginRequest() {
    pendingRequests++;
    setBusy(true);
  }

  private boolean finishRequest() {
    pendingRequests = Math.max(0, pendingRequests - 1);
    boolean done = pendingRequests == 0;
    if (done) {
      setBusy(false);
    }
    return done;
  }

  private void setBusy(boolean busy) {
    if (loadingIndicator != null) {
      loadingIndicator.setVisible(busy);
      loadingIndicator.setManaged(busy);
    }
    if (refreshButton != null) {
      refreshButton.setDisable(busy);
    }
    if (clearFilterButton != null) {
      clearFilterButton.setDisable(busy);
    }
    if (backButton != null) {
      backButton.setDisable(busy);
    }
    updatePaginationControls();
  }

  private void setText(Label label, String text) {
    if (label != null) {
      label.setText(text == null || text.isBlank() ? "--" : text);
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

  private void updatePaginationControls() {
    int safePage = Math.max(1, currentPage);
    int safeTotalPages = Math.max(1, totalPages);
    if (paginationLabel != null) {
      paginationLabel.setText(
          String.format("Trang %d/%d - %d giao dich", safePage, safeTotalPages, totalItems));
    }

    boolean busy = pendingRequests > 0;
    if (previousPageButton != null) {
      previousPageButton.setDisable(busy || safePage <= 1);
    }
    if (nextPageButton != null) {
      nextPageButton.setDisable(busy || safePage >= safeTotalPages);
    }
  }

  private String errorMessage(Throwable throwable, String fallbackMessage) {
    Throwable root = unwrap(throwable);
    String message = root.getMessage();
    return message == null || message.isBlank() ? fallbackMessage : message;
  }

  private Throwable unwrap(Throwable throwable) {
    Throwable current = throwable;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private enum TransactionFilter {
    ALL("Tat ca", null),
    PAYMENT_FROM_WINNER("Nguoi thang thanh toan", "PAYMENT_FROM_WINNER"),
    TAX_COLLECTED("Thue he thong", "TAX_COLLECTED"),
    PAYOUT_TO_SELLER("Giai ngan seller", "PAYOUT_TO_SELLER"),
    REFUND_TO_WINNER("Hoan tien nguoi mua", "REFUND_TO_WINNER"),
    DEPOSIT_FORFEIT("Tich thu coc", "DEPOSIT_FORFEIT"),
    DEPOSIT_LOCK("Khoa tien coc", "DEPOSIT_LOCK"),
    DEPOSIT_UNLOCK("Hoan tien coc", "DEPOSIT_UNLOCK"),
    SECOND_CHANCE_PAYMENT("Second Chance", "SECOND_CHANCE_PAYMENT");

    private final String label;
    private final String packetValue;

    TransactionFilter(String label, String packetValue) {
      this.label = label;
      this.packetValue = packetValue;
    }

    String packetValue() {
      return packetValue;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
