package com.group13.auction.service.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link ChatbotService}. */
class ChatbotServiceValidationTest {

  @Test
  void askByQueryShouldFailWhenQueryIsNull() {
    ChatbotService service = createService();

    assertFutureFailsWithMessage(service.askByQuery(null), "Vui lòng nhập câu hỏi cho OMNI.");
  }

  @Test
  void askByQueryShouldFailWhenQueryIsBlank() {
    ChatbotService service = createService();

    assertFutureFailsWithMessage(service.askByQuery("   "), "Vui lòng nhập câu hỏi cho OMNI.");
  }

  @Test
  void askByFaqIdShouldFailWhenFaqIdIsNull() {
    ChatbotService service = createService();

    assertFutureFailsWithMessage(service.askByFaqId(null), "Câu hỏi gợi ý không hợp lệ.");
  }

  @Test
  void askByFaqIdShouldFailWhenFaqIdIsBlank() {
    ChatbotService service = createService();

    assertFutureFailsWithMessage(service.askByFaqId("   "), "Câu hỏi gợi ý không hợp lệ.");
  }

  private static ChatbotService createService() {
    return new ChatbotService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}
