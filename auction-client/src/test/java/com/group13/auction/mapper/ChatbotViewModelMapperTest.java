package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.chatbot.ChatbotDTOs;
import com.group13.auction.viewmodel.chatbot.ChatbotFaqViewModel;
import com.group13.auction.viewmodel.chatbot.ChatbotMessageViewModel;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ChatbotViewModelMapper}. */
class ChatbotViewModelMapperTest {

  private static final String DEFAULT_NOT_FOUND_MESSAGE =
      "OMNI chưa tìm thấy câu trả lời phù hợp. Bạn có thể hỏi ngắn gọn hơn "
          + "hoặc chọn một câu hỏi gợi ý ở bên trái.";

  @Test
  void toBotMessageShouldReturnDefaultMessageWhenResponseIsNull() {
    ChatbotMessageViewModel viewModel = ChatbotViewModelMapper.toBotMessage(null);

    assertEquals("OMNI", viewModel.senderText());
    assertEquals(DEFAULT_NOT_FOUND_MESSAGE, viewModel.content());
    assertFalse(viewModel.userMessage());
    assertTrue(viewModel.timestampText().matches("\\d{2}:\\d{2}"));
  }

  @Test
  void toBotMessageShouldPreferAnswerWhenAnswerHasText() {
    ChatbotDTOs.ChatbotResponseDTO response = new ChatbotDTOs.ChatbotResponseDTO();
    response.setAnswer("  Bạn có thể tham gia phiên đang mở.  ");
    response.setFallbackMessage("Fallback message");

    ChatbotMessageViewModel viewModel = ChatbotViewModelMapper.toBotMessage(response);

    assertEquals("Bạn có thể tham gia phiên đang mở.", viewModel.content());
    assertEquals("OMNI", viewModel.senderText());
    assertFalse(viewModel.userMessage());
  }

  @Test
  void toBotMessageShouldUseFallbackWhenAnswerIsBlank() {
    ChatbotDTOs.ChatbotResponseDTO response = new ChatbotDTOs.ChatbotResponseDTO();
    response.setAnswer("   ");
    response.setFallbackMessage("  Hãy thử hỏi ngắn gọn hơn.  ");

    ChatbotMessageViewModel viewModel = ChatbotViewModelMapper.toBotMessage(response);

    assertEquals("Hãy thử hỏi ngắn gọn hơn.", viewModel.content());
  }

  @Test
  void toBotMessageShouldUseDefaultMessageWhenAnswerAndFallbackAreBlank() {
    ChatbotDTOs.ChatbotResponseDTO response = new ChatbotDTOs.ChatbotResponseDTO();
    response.setAnswer("   ");
    response.setFallbackMessage("   ");

    ChatbotMessageViewModel viewModel = ChatbotViewModelMapper.toBotMessage(response);

    assertEquals(DEFAULT_NOT_FOUND_MESSAGE, viewModel.content());
  }

  @Test
  void toFaqViewModelsShouldReturnEmptyListWhenResponseIsNull() {
    assertTrue(ChatbotViewModelMapper.toFaqViewModels(null).isEmpty());
  }

  @Test
  void toFaqViewModelsShouldReturnEmptyListWhenFaqsAreNull() {
    ChatbotDTOs.ChatbotFaqListResponseDTO response = new ChatbotDTOs.ChatbotFaqListResponseDTO();

    assertTrue(ChatbotViewModelMapper.toFaqViewModels(response).isEmpty());
  }

  @Test
  void toFaqViewModelsShouldFilterNullFaqsAndMapFaqData() {
    ChatbotDTOs.FaqSummaryDTO paymentFaq = new ChatbotDTOs.FaqSummaryDTO();
    paymentFaq.setId("FAQ-1");
    paymentFaq.setCategory("PAYMENT");
    paymentFaq.setQuestion("Thanh toán như thế nào?");

    ChatbotDTOs.FaqSummaryDTO biddingFaq = new ChatbotDTOs.FaqSummaryDTO();
    biddingFaq.setId("FAQ-2");
    biddingFaq.setCategory("BIDDING");
    biddingFaq.setQuestion("Làm sao để đặt giá?");

    ChatbotDTOs.ChatbotFaqListResponseDTO response = new ChatbotDTOs.ChatbotFaqListResponseDTO();
    response.setFaqs(List.of(paymentFaq, biddingFaq));

    List<ChatbotFaqViewModel> viewModels = ChatbotViewModelMapper.toFaqViewModels(response);

    assertEquals(2, viewModels.size());

    assertEquals("FAQ-1", viewModels.get(0).id());
    assertEquals("PAYMENT", viewModels.get(0).category());
    assertEquals("Thanh toán như thế nào?", viewModels.get(0).question());

    assertEquals("FAQ-2", viewModels.get(1).id());
    assertEquals("BIDDING", viewModels.get(1).category());
    assertEquals("Làm sao để đặt giá?", viewModels.get(1).question());
  }
}
