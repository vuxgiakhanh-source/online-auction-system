package com.group13.auction.mapper;

import com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotFaqListResponseDTO;
import com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotResponseDTO;
import com.group13.auction.common.dto.chatbot.ChatbotDTOs.FaqSummaryDTO;
import com.group13.auction.viewmodel.chatbot.ChatbotFaqViewModel;
import com.group13.auction.viewmodel.chatbot.ChatbotMessageViewModel;
import java.util.List;
import java.util.Objects;

/** Chuyển DTO chatbot từ {@code auction-common} sang view model phía client. */
public final class ChatbotViewModelMapper {

  private static final String DEFAULT_NOT_FOUND_MESSAGE =
      "OMNI chưa tìm thấy câu trả lời phù hợp. Bạn có thể hỏi ngắn gọn hơn "
          + "hoặc chọn một câu hỏi gợi ý ở bên trái.";

  private ChatbotViewModelMapper() {
    // Utility class.
  }

  /**
   * Chuyển response chatbot thành tin nhắn của OMNI.
   *
   * @param response response từ server
   * @return view model tin nhắn chatbot
   */
  public static ChatbotMessageViewModel toBotMessage(ChatbotResponseDTO response) {
    if (response == null) {
      return ChatbotMessageViewModel.omni(DEFAULT_NOT_FOUND_MESSAGE);
    }

    String answer = clean(response.getAnswer());
    if (!answer.isBlank()) {
      return ChatbotMessageViewModel.omni(answer);
    }

    String fallback = clean(response.getFallbackMessage());
    if (!fallback.isBlank()) {
      return ChatbotMessageViewModel.omni(fallback);
    }

    return ChatbotMessageViewModel.omni(DEFAULT_NOT_FOUND_MESSAGE);
  }

  /**
   * Chuyển payload danh sách FAQ thành danh sách view model.
   *
   * @param response response danh sách FAQ
   * @return danh sách FAQ hiển thị trên UI
   */
  public static List<ChatbotFaqViewModel> toFaqViewModels(ChatbotFaqListResponseDTO response) {
    if (response == null || response.getFaqs() == null) {
      return List.of();
    }

    return response.getFaqs().stream()
        .filter(Objects::nonNull)
        .map(ChatbotViewModelMapper::toFaqViewModel)
        .toList();
  }

  private static ChatbotFaqViewModel toFaqViewModel(FaqSummaryDTO faq) {
    return new ChatbotFaqViewModel(faq.getId(), faq.getCategory(), faq.getQuestion());
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }
}