package com.group13.auction.viewmodel.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ChatbotMessageViewModel}. */
class ChatbotMessageViewModelTest {

  @Test
  void constructorShouldTrimInputValues() {
    ChatbotMessageViewModel viewModel =
        new ChatbotMessageViewModel("  You  ", "  Hello OMNI  ", "  20:30  ", true);

    assertEquals("You", viewModel.senderText());
    assertEquals("Hello OMNI", viewModel.content());
    assertEquals("20:30", viewModel.timestampText());
    assertTrue(viewModel.userMessage());
  }

  @Test
  void constructorShouldConvertNullValuesToEmptyStrings() {
    ChatbotMessageViewModel viewModel = new ChatbotMessageViewModel(null, null, null, false);

    assertEquals("", viewModel.senderText());
    assertEquals("", viewModel.content());
    assertEquals("", viewModel.timestampText());
    assertFalse(viewModel.userMessage());
  }

  @Test
  void userFactoryShouldCreateUserMessage() {
    ChatbotMessageViewModel viewModel = ChatbotMessageViewModel.user("How do I bid?");

    assertEquals("You", viewModel.senderText());
    assertEquals("How do I bid?", viewModel.content());
    assertTrue(viewModel.userMessage());
    assertTrue(viewModel.timestampText().matches("\\d{2}:\\d{2}"));
  }

  @Test
  void omniFactoryShouldCreateOmniMessage() {
    ChatbotMessageViewModel viewModel = ChatbotMessageViewModel.omni("You can join an open auction.");

    assertEquals("OMNI", viewModel.senderText());
    assertEquals("You can join an open auction.", viewModel.content());
    assertFalse(viewModel.userMessage());
    assertTrue(viewModel.timestampText().matches("\\d{2}:\\d{2}"));
  }
}