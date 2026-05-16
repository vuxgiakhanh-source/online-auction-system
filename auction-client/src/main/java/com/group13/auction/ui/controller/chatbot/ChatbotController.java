package com.group13.auction.ui.controller.chatbot;

import com.group13.auction.common.dto.chatbot.ChatbotDTOs;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public final class ChatbotController extends BaseController implements PageLifecycle {

    @FXML private ComboBox<String> categoryBox;
    @FXML private ListView<ChatbotDTOs.FaqSummaryDTO> faqList;
    @FXML private TextField queryField;
    @FXML private TextArea answerArea;

    @FXML
    private void initialize() {
        categoryBox.getItems().setAll("Tất cả", "GENERAL", "BIDDING", "PAYMENT", "RATING", "SELLER");
        services().chatbotService().lastAnswerProperty().addListener((obs, o, r) -> {
            if (r == null) {
                return;
            }
            if (r.isSuccess() && r.getAnswer() != null) {
                answerArea.setText(r.getAnswer());
            } else if (r.getFallbackMessage() != null) {
                answerArea.setText(r.getFallbackMessage());
            } else {
                answerArea.setText("Không tìm thấy câu trả lời phù hợp.");
            }
        });
        services().chatbotService().faqMenu().addListener(
                (javafx.collections.ListChangeListener<ChatbotDTOs.FaqSummaryDTO>) c ->
                        faqList.getItems().setAll(services().chatbotService().faqMenu()));
        faqList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ChatbotDTOs.FaqSummaryDTO item = faqList.getSelectionModel().getSelectedItem();
                if (item != null) {
                    services().chatbotService().askByFaqId(item.getId());
                }
            }
        });
    }

    @Override
    public void onShow() {
        onLoadFaq();
    }

    @FXML
    private void onLoadFaq() {
        String cat = categoryBox.getValue();
        services().chatbotService().loadFaqList("Tất cả".equals(cat) ? null : cat);
    }

    @FXML
    private void onAsk() {
        services().chatbotService().askByQuery(queryField.getText().trim());
    }
}
