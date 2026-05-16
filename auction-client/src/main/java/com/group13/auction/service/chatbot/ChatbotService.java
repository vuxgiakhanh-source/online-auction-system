package com.group13.auction.service.chatbot;

import com.group13.auction.common.dto.chatbot.ChatbotDTOs;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Chatbot FAQ — không cần đăng nhập.
 */
public final class ChatbotService extends NetworkService implements ClientEventListener {

    private final ObjectProperty<ChatbotDTOs.ChatbotResponseDTO> lastAnswer =
            new SimpleObjectProperty<>();
    private final ObservableList<ChatbotDTOs.FaqSummaryDTO> faqMenu =
            FXCollections.observableArrayList();

    public ObjectProperty<ChatbotDTOs.ChatbotResponseDTO> lastAnswerProperty() {
        return lastAnswer;
    }

    public ObservableList<ChatbotDTOs.FaqSummaryDTO> faqMenu() {
        return faqMenu;
    }

    public void askByFaqId(String faqId) {
        network().chatbotAsk(ChatbotDTOs.ChatbotAskRequestDTO.byFaqId(faqId));
    }

    public void askByQuery(String query) {
        network().chatbotAsk(ChatbotDTOs.ChatbotAskRequestDTO.byQuery(query));
    }

    public void loadFaqList(String category) {
        network().chatbotGetFaqList(new ChatbotDTOs.ChatbotFaqListRequestDTO(category));
    }

    @Override
    public void onChatbotAnswer(ChatbotDTOs.ChatbotResponseDTO response) {
        lastAnswer.set(response);
    }

    @Override
    public void onChatbotNotFound(ChatbotDTOs.ChatbotResponseDTO response) {
        lastAnswer.set(response);
    }

    @Override
    public void onChatbotFaqListReceived(ChatbotDTOs.ChatbotFaqListResponseDTO list) {
        faqMenu.clear();
        if (list != null && list.getFaqs() != null) {
            faqMenu.addAll(list.getFaqs());
        }
    }
}
