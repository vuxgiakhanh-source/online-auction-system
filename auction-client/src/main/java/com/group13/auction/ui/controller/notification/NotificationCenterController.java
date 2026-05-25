package com.group13.auction.ui.controller.notification;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.notification.NotificationService;
import com.group13.auction.service.payment.PaymentService;
import com.group13.auction.service.payment.SecondChanceRealtimeService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.notification.NotificationItemViewModel;
import com.group13.auction.viewmodel.payment.PaymentResultViewModel;
import com.group13.auction.viewmodel.payment.SecondChanceOfferViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

/** Controller cho màn trung tâm thông báo. */
public final class NotificationCenterController {

    private static final String SECOND_CHANCE_NOTIFICATION_TYPE = "SecondChanceOffer";
    private static final String NOTIFICATION_CELL_READ_CLASS = "notification-cell-read";
    private static final String NOTIFICATION_CELL_UNREAD_CLASS = "notification-cell-unread";

    private final NotificationService notificationService = new NotificationService();
    private final PaymentService paymentService = new PaymentService();
    private final SecondChanceRealtimeService secondChanceRealtimeService =
            SecondChanceRealtimeService.getInstance();

    @FXML private ListView<SecondChanceOfferViewModel> secondChanceListView;

    @FXML private ListView<NotificationItemViewModel> secondChanceInboxListView;

    @FXML private VBox secondChanceEmptyBox;

    @FXML private Label offerItemNameLabel;

    @FXML private Label offerAuctionIdLabel;

    @FXML private Label offerPriceLabel;

    @FXML private Label offerDepositLabel;

    @FXML private Label offerDeadlineLabel;

    @FXML private Label offerStatusLabel;

    @FXML private Button acceptSecondChanceButton;

    @FXML private Button declineSecondChanceButton;

    @FXML private ListView<NotificationItemViewModel> notificationListView;

    @FXML private VBox emptyStateBox;

    @FXML private Label titleLabel;

    @FXML private Label bodyLabel;

    @FXML private Label typeLabel;

    @FXML private Label createdAtLabel;

    @FXML private Label readStateLabel;

    @FXML private Label relatedAuctionLabel;

    @FXML private Label statusLabel;

    @FXML private Button markReadButton;

    @FXML private Button openAuctionButton;

    @FXML private Button refreshButton;

    @FXML private ProgressIndicator loadingIndicator;

    /** Khởi tạo notification center và tải danh sách thông báo. */
    @FXML
    public void initialize() {
        secondChanceRealtimeService.start();

        configureSecondChanceListView();
        configureSecondChanceSelectionListener();
        configureSecondChanceInboxListView();

        configureNotificationListView();
        configureNotificationSelectionListener();

        clearSecondChanceDetail();
        clearNotificationDetail();

        loadPendingSecondChanceOffers();
        loadNotifications();
    }

    /** Quay lại dashboard chính. */
    @FXML
    public void handleBackToHome() {
        Navigator.getInstance().goToMainLayout();
    }

    /** Tải lại danh sách thông báo và Second Chance Offer runtime. */
    @FXML
    public void handleRefresh() {
        loadPendingSecondChanceOffers();
        loadNotifications();
    }

    /** Chấp nhận Second Chance Offer đang chọn. */
    @FXML
    public void handleAcceptSecondChance() {
        SecondChanceOfferViewModel selected =
                secondChanceListView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            AlertUtil.showWarning("Vui lòng chọn một Second Chance Offer.");
            return;
        }

        if (selected.expired()) {
            AlertUtil.showWarning("Second Chance Offer này đã hết hạn.");
            secondChanceRealtimeService.removeOfferByAuctionId(selected.auctionId());
            loadPendingSecondChanceOffers();
            return;
        }

        boolean confirmed =
                AlertUtil.confirm(
                        "Chấp nhận Second Chance Offer cho \""
                                + selected.auctionItemName()
                                + "\"?\nGiá đề nghị: "
                                + selected.offerPriceText()
                                + "\nTiền cọc yêu cầu: "
                                + selected.depositRequiredText());
        if (!confirmed) {
            return;
        }

        setLoading(true, "Đang chấp nhận Second Chance Offer...");

        paymentService
                .acceptSecondChance(selected.auctionId())
                .thenAccept(
                        result ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            secondChanceRealtimeService.removeOfferByAuctionId(
                                                    selected.auctionId());
                                            loadPendingSecondChanceOffers();
                                            setLoading(false, "Đã chấp nhận Second Chance Offer.");
                                            AlertUtil.showInfo(buildSecondChanceAcceptedMessage(result));
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không chấp nhận được Second Chance Offer.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    /** Từ chối Second Chance Offer đang chọn. */
    @FXML
    public void handleDeclineSecondChance() {
        SecondChanceOfferViewModel selected =
                secondChanceListView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            AlertUtil.showWarning("Vui lòng chọn một Second Chance Offer.");
            return;
        }

        boolean confirmed =
                AlertUtil.confirm(
                        "Từ chối Second Chance Offer cho \""
                                + selected.auctionItemName()
                                + "\"?");
        if (!confirmed) {
            return;
        }

        setLoading(true, "Đang từ chối Second Chance Offer...");

        paymentService
                .declineSecondChance(selected.auctionId())
                .thenRun(
                        () ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            secondChanceRealtimeService.removeOfferByAuctionId(
                                                    selected.auctionId());
                                            loadPendingSecondChanceOffers();
                                            setLoading(false, "Đã từ chối Second Chance Offer.");
                                            AlertUtil.showInfo(
                                                    "Đã ghi nhận từ chối Second Chance Offer.");
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không từ chối được Second Chance Offer.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    /** Đánh dấu thông báo đang chọn là đã đọc. */
    @FXML
    public void handleMarkRead() {
        NotificationItemViewModel selected = selectedNotification();

        if (selected == null) {
            AlertUtil.showWarning("Vui lòng chọn một thông báo.");
            return;
        }

        if (selected.read()) {
            AlertUtil.showInfo("Thông báo này đã được đánh dấu là đã đọc.");
            return;
        }

        setLoading(true, "Đang đánh dấu thông báo là đã đọc...");

        notificationService
                .markNotificationRead(selected.id())
                .thenRun(
                        () ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            replaceSelectedNotification(selected.markRead());
                                            setLoading(false, "Đã đánh dấu thông báo là đã đọc.");
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không đánh dấu được thông báo.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    /** Mở phiên đấu giá liên quan nếu thông báo có gắn mã phiên. */
    @FXML
    public void handleOpenRelatedAuction() {
        NotificationItemViewModel selected = selectedNotification();

        if (selected == null || !selected.hasRelatedAuction()) {
            AlertUtil.showWarning("Thông báo này không gắn với phiên đấu giá cụ thể.");
            return;
        }

        openAuctionDetail(selected.relatedAuctionId());
    }

    /** Mở phiên đấu giá của Second Chance Offer đang chọn. */
    @FXML
    public void handleOpenSecondChanceAuction() {
        SecondChanceOfferViewModel selected =
                secondChanceListView.getSelectionModel().getSelectedItem();

        if (selected == null || selected.auctionId().isBlank()) {
            AlertUtil.showWarning("Vui lòng chọn một Second Chance Offer.");
            return;
        }

        openAuctionDetail(selected.auctionId());
    }

    private void configureSecondChanceListView() {
        secondChanceListView.setCellFactory(
                ignored ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(
                                    SecondChanceOfferViewModel offer, boolean empty) {
                                super.updateItem(offer, empty);

                                if (empty || offer == null) {
                                    setText(null);
                                    setGraphic(null);
                                    return;
                                }

                                setText(
                                        offer.auctionItemName()
                                                + "\n"
                                                + offer.offerPriceText()
                                                + "  •  Hạn: "
                                                + offer.deadlineText());
                            }
                        });
    }

    private void configureSecondChanceSelectionListener() {
        secondChanceListView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, selected) -> renderSecondChanceDetail(selected));
    }

    private void configureSecondChanceInboxListView() {
        secondChanceInboxListView.setCellFactory(
            ignored ->
                new ListCell<>() {
                    @Override
                    protected void updateItem(
                        NotificationItemViewModel notification, boolean empty) {
                        super.updateItem(notification, empty);
                        renderNotificationCell(this, notification, empty);
                    }
                });

        secondChanceInboxListView
            .getSelectionModel()
            .selectedItemProperty()
            .addListener(
                (observable, oldValue, selected) -> {
                    if (selected != null) {
                        notificationListView.getSelectionModel().clearSelection();
                        renderNotificationDetail(selected);
                    }
                });
    }

    private void configureNotificationListView() {
        notificationListView.setCellFactory(
            ignored ->
                new ListCell<>() {
                    @Override
                    protected void updateItem(
                        NotificationItemViewModel notification, boolean empty) {
                        super.updateItem(notification, empty);
                        renderNotificationCell(this, notification, empty);
                    }
                });
    }

    private void configureNotificationSelectionListener() {
        notificationListView
            .getSelectionModel()
            .selectedItemProperty()
            .addListener(
                (observable, oldValue, selected) -> {
                    if (selected != null) {
                        secondChanceInboxListView.getSelectionModel().clearSelection();
                        renderNotificationDetail(selected);
                    } else if (secondChanceInboxListView
                        .getSelectionModel()
                        .getSelectedItem()
                        == null) {
                        clearNotificationDetail();
                    }
                });
    }

    private void renderNotificationCell(
        ListCell<NotificationItemViewModel> cell,
        NotificationItemViewModel notification,
        boolean empty) {
        cell.getStyleClass().removeAll(NOTIFICATION_CELL_READ_CLASS, NOTIFICATION_CELL_UNREAD_CLASS);

        if (empty || notification == null) {
            cell.setText(null);
            cell.setGraphic(null);
            return;
        }

        cell.getStyleClass()
            .add(notification.read() ? NOTIFICATION_CELL_READ_CLASS : NOTIFICATION_CELL_UNREAD_CLASS);
        cell.setText(buildNotificationCellText(notification));
        cell.setGraphic(null);
    }

    private String buildNotificationCellText(NotificationItemViewModel notification) {
        String unreadDot = notification.read() ? "" : "● ";
        return unreadDot
            + notification.title()
            + "\n"
            + notification.createdAtText()
            + "  •  "
            + notification.readStateText();
    }

    private void loadPendingSecondChanceOffers() {
        List<SecondChanceOfferViewModel> offers = secondChanceRealtimeService.getPendingOffers();
        renderSecondChanceOffers(offers);
    }

    private void loadNotifications() {
        setLoading(true, "Đang tải thông báo...");

        notificationService
                .getNotifications()
                .thenAccept(
                        notifications ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            renderAllNotifications(notifications);
                                            setLoading(false, "Đã tải danh sách thông báo.");
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không tải được danh sách thông báo.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    private void renderSecondChanceOffers(List<SecondChanceOfferViewModel> offers) {
        List<SecondChanceOfferViewModel> safeOffers = offers == null ? List.of() : offers;
        secondChanceListView.setItems(FXCollections.observableArrayList(safeOffers));

        boolean empty = safeOffers.isEmpty();
        secondChanceEmptyBox.setVisible(empty);
        secondChanceEmptyBox.setManaged(empty);
        secondChanceListView.setVisible(!empty);
        secondChanceListView.setManaged(!empty);

        if (empty) {
            clearSecondChanceDetail();
            return;
        }

        secondChanceListView.getSelectionModel().selectFirst();
    }

    private void renderAllNotifications(List<NotificationItemViewModel> notifications) {
        List<NotificationItemViewModel> safeNotifications =
                notifications == null ? List.of() : notifications;

        List<NotificationItemViewModel> general = new java.util.ArrayList<>();
        List<NotificationItemViewModel> secondChanceInbox = new java.util.ArrayList<>();
        for (NotificationItemViewModel item : safeNotifications) {
            if (SECOND_CHANCE_NOTIFICATION_TYPE.equals(item.type())) {
                secondChanceInbox.add(item);
            } else {
                general.add(item);
            }
        }

        renderNotifications(general);
        renderSecondChanceInbox(secondChanceInbox);
    }

    private void renderSecondChanceInbox(List<NotificationItemViewModel> inboxItems) {
        List<NotificationItemViewModel> safeItems = inboxItems == null ? List.of() : inboxItems;
        secondChanceInboxListView.setItems(FXCollections.observableArrayList(safeItems));

        boolean empty = safeItems.isEmpty();
        secondChanceInboxListView.setVisible(!empty);
        secondChanceInboxListView.setManaged(!empty);

        if (!empty) {
            secondChanceInboxListView.getSelectionModel().selectFirst();
        }
    }

    private void renderNotifications(List<NotificationItemViewModel> notifications) {
        List<NotificationItemViewModel> safeNotifications =
                notifications == null ? List.of() : notifications;

        notificationListView.setItems(FXCollections.observableArrayList(safeNotifications));

        boolean empty = safeNotifications.isEmpty();
        emptyStateBox.setVisible(empty);
        emptyStateBox.setManaged(empty);
        notificationListView.setVisible(!empty);
        notificationListView.setManaged(!empty);

        if (empty) {
            clearNotificationDetail();
            statusLabel.setText("Server hiện chưa trả về thông báo nào.");
            return;
        }

        notificationListView.getSelectionModel().selectFirst();
    }

    private void renderSecondChanceDetail(SecondChanceOfferViewModel offer) {
        if (offer == null) {
            clearSecondChanceDetail();
            return;
        }

        offerItemNameLabel.setText(offer.auctionItemName());
        offerAuctionIdLabel.setText(offer.auctionId());
        offerPriceLabel.setText(offer.offerPriceText());
        offerDepositLabel.setText(offer.depositRequiredText());
        offerDeadlineLabel.setText(offer.deadlineText());

        if (offer.expired()) {
            offerStatusLabel.setText("Đã hết hạn");
            acceptSecondChanceButton.setDisable(true);
            declineSecondChanceButton.setDisable(true);
        } else {
            offerStatusLabel.setText("Đang chờ phản hồi");
            acceptSecondChanceButton.setDisable(false);
            declineSecondChanceButton.setDisable(false);
        }
    }

    private void renderNotificationDetail(NotificationItemViewModel notification) {
        if (notification == null) {
            clearNotificationDetail();
            return;
        }

        titleLabel.setText(notification.title());
        bodyLabel.setText(notification.body());
        typeLabel.setText(notification.type());
        createdAtLabel.setText(notification.createdAtText());
        readStateLabel.setText(notification.readStateText());

        if (notification.hasRelatedAuction()) {
            relatedAuctionLabel.setText(notification.relatedAuctionId());
            openAuctionButton.setDisable(false);
        } else {
            relatedAuctionLabel.setText("Không có");
            openAuctionButton.setDisable(true);
        }

        markReadButton.setDisable(notification.read());
    }

    private void clearSecondChanceDetail() {
        offerItemNameLabel.setText("Chưa có Second Chance Offer");
        offerAuctionIdLabel.setText("--");
        offerPriceLabel.setText("--");
        offerDepositLabel.setText("--");
        offerDeadlineLabel.setText("--");
        offerStatusLabel.setText("--");
        acceptSecondChanceButton.setDisable(true);
        declineSecondChanceButton.setDisable(true);
    }

    private void clearNotificationDetail() {
        titleLabel.setText("Chưa chọn thông báo");
        bodyLabel.setText("Chọn một thông báo ở danh sách bên trái để xem chi tiết.");
        typeLabel.setText("--");
        createdAtLabel.setText("--");
        readStateLabel.setText("--");
        relatedAuctionLabel.setText("--");
        markReadButton.setDisable(true);
        openAuctionButton.setDisable(true);
    }

    private NotificationItemViewModel selectedNotification() {
        NotificationItemViewModel fromGeneral =
                notificationListView.getSelectionModel().getSelectedItem();
        if (fromGeneral != null) {
            return fromGeneral;
        }
        return secondChanceInboxListView.getSelectionModel().getSelectedItem();
    }

    private void replaceSelectedNotification(NotificationItemViewModel updatedNotification) {
        int generalIndex = notificationListView.getSelectionModel().getSelectedIndex();
        if (generalIndex >= 0) {
            notificationListView.getItems().set(generalIndex, updatedNotification);
            notificationListView.getSelectionModel().select(generalIndex);
            renderNotificationDetail(updatedNotification);
            return;
        }

        int scoIndex = secondChanceInboxListView.getSelectionModel().getSelectedIndex();
        if (scoIndex >= 0) {
            secondChanceInboxListView.getItems().set(scoIndex, updatedNotification);
            secondChanceInboxListView.getSelectionModel().select(scoIndex);
            renderNotificationDetail(updatedNotification);
        }
    }

    private void openAuctionDetail(String auctionId) {
        AppContext.getInstance()
                .getScreenStateStore()
                .put(ScreenStateKeys.SELECTED_AUCTION_ID, auctionId);
        Navigator.getInstance().goToAuctionDetail();
    }

    private void setLoading(boolean loading, String message) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);

        refreshButton.setDisable(loading);
        notificationListView.setDisable(loading);
        secondChanceListView.setDisable(loading);
        secondChanceInboxListView.setDisable(loading);

        NotificationItemViewModel selectedNotification = selectedNotification();
        markReadButton.setDisable(
                loading || selectedNotification == null || selectedNotification.read());
        openAuctionButton.setDisable(
                loading || selectedNotification == null || !selectedNotification.hasRelatedAuction());

        SecondChanceOfferViewModel selectedOffer =
                secondChanceListView.getSelectionModel().getSelectedItem();
        boolean offerActionDisabled = loading || selectedOffer == null || selectedOffer.expired();
        acceptSecondChanceButton.setDisable(offerActionDisabled);
        declineSecondChanceButton.setDisable(offerActionDisabled);

        statusLabel.setText(message);
    }

    private String buildSecondChanceAcceptedMessage(PaymentResultViewModel result) {
        return "Đã chấp nhận Second Chance Offer.\n"
                + "Giá đề nghị: "
                + result.finalPriceText()
                + "\n"
                + "Tiền cọc đã trừ: "
                + result.depositDeductedText()
                + "\n"
                + "Phần còn cần thanh toán: "
                + result.remainingToPayText()
                + "\n"
                + "Số dư mới: "
                + result.newBalanceText();
    }

    private String extractMessage(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank()
                ? "Có lỗi xảy ra khi xử lý thông báo."
                : message;
    }
}