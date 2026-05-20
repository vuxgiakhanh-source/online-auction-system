package com.group13.auction.ui.controller.chatbot;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.chatbot.ChatbotService;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.chatbot.ChatbotFaqViewModel;
import com.group13.auction.viewmodel.chatbot.ChatbotMessageViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Controller cho màn chatbot OMNI.
 *
 * <p>Controller chỉ nhận input từ UI, gọi {@link ChatbotService}, rồi cập nhật danh sách tin nhắn.
 * Logic tìm câu trả lời và dữ liệu FAQ vẫn nằm ở server.
 */
public final class ChatbotController {

  private static final String ALL_CATEGORIES_LABEL = "ALL";
  private static final String GENERAL_CATEGORY = "GENERAL";
  private static final String BIDDING_CATEGORY = "BIDDING";
  private static final String PAYMENT_CATEGORY = "PAYMENT";
  private static final String RATING_CATEGORY = "RATING";
  private static final String SELLER_CATEGORY = "SELLER";

  private final ChatbotService chatbotService = new ChatbotService();

  @FXML
  private ComboBox<String> categoryComboBox;

  @FXML
  private ListView<ChatbotFaqViewModel> faqListView;

  @FXML
  private ScrollPane messageScrollPane;

  @FXML
  private VBox messageContainer;

  @FXML
  private TextField messageField;

  @FXML
  private Button sendButton;

  @FXML
  private Label statusLabel;

  /** Khởi tạo màn chatbot và tải danh sách FAQ gợi ý. */
  @FXML
  public void initialize() {
    setupCategoryFilter();
    setupFaqListView();
    addBotMessage(
        ChatbotMessageViewModel.omni(
            "Xin chào, mình là OMNI. Bạn có thể hỏi về đấu giá, thanh toán, "
                + "rating hoặc kênh Seller."));
    loadFaqList(null);
  }

  /** Quay lại màn chính. */
  @FXML
  public void handleBack() {
    Navigator.getInstance().goToMainLayout();
  }

  /** Gửi câu hỏi người dùng nhập tới chatbot server. */
  @FXML
  public void handleSendMessage() {
    String query = messageField.getText() == null ? "" : messageField.getText().trim();
    if (query.isBlank()) {
      setStatus("Nhập câu hỏi trước khi gửi nhé.");
      messageField.requestFocus();
      return;
    }

    addUserMessage(ChatbotMessageViewModel.user(query));
    messageField.clear();
    setChatInputDisabled(true);
    setStatus("OMNI đang xử lý câu hỏi...");

    chatbotService
        .askByQuery(query)
        .thenAccept(
            response ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      addBotMessage(response);
                      setStatus("Sẵn sàng hỗ trợ.");
                      setChatInputDisabled(false);
                      messageField.requestFocus();
                    }))
        .exceptionally(
            throwable -> {
              handleFailure("Không thể gửi câu hỏi tới OMNI.", throwable);
              return null;
            });
  }

  /** Tải lại FAQ theo category đang chọn. */
  @FXML
  public void handleCategoryChanged() {
    String selectedCategory = categoryComboBox.getValue();
    loadFaqList(toRequestCategory(selectedCategory));
  }

  /** Gửi câu hỏi theo FAQ đang được chọn. */
  @FXML
  public void handleFaqClicked() {
    ChatbotFaqViewModel selectedFaq = faqListView.getSelectionModel().getSelectedItem();
    if (selectedFaq == null || !selectedFaq.hasId()) {
      return;
    }

    addUserMessage(ChatbotMessageViewModel.user(selectedFaq.displayText()));
    setChatInputDisabled(true);
    setStatus("OMNI đang tải câu trả lời FAQ...");

    chatbotService
        .askByFaqId(selectedFaq.id())
        .thenAccept(
            response ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      addBotMessage(response);
                      setStatus("Sẵn sàng hỗ trợ.");
                      setChatInputDisabled(false);
                      messageField.requestFocus();
                    }))
        .exceptionally(
            throwable -> {
              handleFailure("Không tải được câu trả lời FAQ.", throwable);
              return null;
            });
  }

  private void setupCategoryFilter() {
    categoryComboBox.setItems(
        FXCollections.observableArrayList(
            ALL_CATEGORIES_LABEL,
            GENERAL_CATEGORY,
            BIDDING_CATEGORY,
            PAYMENT_CATEGORY,
            RATING_CATEGORY,
            SELLER_CATEGORY));
    categoryComboBox.setValue(ALL_CATEGORIES_LABEL);
  }

  private void setupFaqListView() {
    faqListView.setCellFactory(
        listView ->
            new ListCell<>() {
              @Override
              protected void updateItem(ChatbotFaqViewModel item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                  setText(null);
                  setGraphic(null);
                  return;
                }

                Label categoryLabel = new Label(item.categoryText());
                categoryLabel.getStyleClass().add("chatbot-faq-category");

                Label questionLabel = new Label(item.displayText());
                questionLabel.setWrapText(true);
                questionLabel.getStyleClass().add("chatbot-faq-question");

                VBox content = new VBox(4.0, categoryLabel, questionLabel);
                content.getStyleClass().add("chatbot-faq-cell-content");

                setText(null);
                setGraphic(content);
              }
            });
  }

  private void loadFaqList(String category) {
    setStatus("Đang tải câu hỏi gợi ý...");

    chatbotService
        .getFaqList(category)
        .thenAccept(
            faqs ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      updateFaqList(faqs);
                      setStatus("Sẵn sàng hỗ trợ.");
                    }))
        .exceptionally(
            throwable -> {
              handleFailure("Không tải được danh sách FAQ.", throwable);
              return null;
            });
  }

  private void updateFaqList(List<ChatbotFaqViewModel> faqs) {
    faqListView.setItems(FXCollections.observableArrayList(faqs == null ? List.of() : faqs));
  }

  private void addUserMessage(ChatbotMessageViewModel message) {
    addMessageBubble(message);
  }

  private void addBotMessage(ChatbotMessageViewModel message) {
    addMessageBubble(message);
  }

  private void addMessageBubble(ChatbotMessageViewModel message) {
    Label senderLabel = new Label(message.senderText() + " • " + message.timestampText());
    senderLabel.getStyleClass().add("chatbot-message-meta");

    Label contentLabel = new Label(message.content());
    contentLabel.setWrapText(true);
    contentLabel.setMaxWidth(560.0);
    contentLabel.getStyleClass().add("chatbot-message-text");

    VBox bubble = new VBox(5.0, senderLabel, contentLabel);
    bubble.getStyleClass().add(
        message.userMessage() ? "chatbot-message-user" : "chatbot-message-bot");

    HBox row = new HBox(bubble);
    row.setAlignment(message.userMessage() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    row.getStyleClass().add("chatbot-message-row");

    messageContainer.getChildren().add(row);
    scrollToBottom();
  }

  private void scrollToBottom() {
    messageContainer.layout();
    messageScrollPane.setVvalue(1.0);
  }

  private void setChatInputDisabled(boolean disabled) {
    messageField.setDisable(disabled);
    sendButton.setDisable(disabled);
    sendButton.setText(disabled ? "Đang gửi..." : "Gửi");
  }

  private void setStatus(String message) {
    statusLabel.setText(message == null || message.isBlank() ? "Sẵn sàng hỗ trợ." : message);
  }

  private String toRequestCategory(String selectedCategory) {
    if (selectedCategory == null
        || selectedCategory.isBlank()
        || ALL_CATEGORIES_LABEL.equals(selectedCategory)) {
      return null;
    }
    return selectedCategory;
  }

  private void handleFailure(String fallbackMessage, Throwable throwable) {
    FxThreadUtil.runOnFxThread(
        () -> {
          addBotMessage(ChatbotMessageViewModel.omni(extractMessage(fallbackMessage, throwable)));
          setStatus("Có lỗi xảy ra.");
          setChatInputDisabled(false);
          messageField.requestFocus();
        });
  }

  private String extractMessage(String fallbackMessage, Throwable throwable) {
    Throwable current = throwable;
    if (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }

    String message = current == null ? null : current.getMessage();
    if (message == null || message.isBlank()) {
      return fallbackMessage;
    }

    return message;
  }
}