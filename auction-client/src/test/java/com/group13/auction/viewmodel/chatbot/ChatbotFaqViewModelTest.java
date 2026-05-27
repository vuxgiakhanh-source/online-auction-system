package com.group13.auction.viewmodel.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ChatbotFaqViewModel}. */
class ChatbotFaqViewModelTest {

  @Test
  void constructorShouldTrimInputValues() {
    ChatbotFaqViewModel viewModel =
        new ChatbotFaqViewModel("  FAQ-1  ", "  PAYMENT  ", "  How to pay?  ");

    assertEquals("FAQ-1", viewModel.id());
    assertEquals("PAYMENT", viewModel.category());
    assertEquals("How to pay?", viewModel.question());
  }

  @Test
  void constructorShouldUseSafeDefaultsWhenValuesAreNull() {
    ChatbotFaqViewModel viewModel = new ChatbotFaqViewModel(null, null, null);

    assertEquals("", viewModel.id());
    assertEquals("GENERAL", viewModel.category());
    assertEquals("", viewModel.question());
  }

  @Test
  void hasIdShouldReturnTrueOnlyWhenIdHasText() {
    assertTrue(new ChatbotFaqViewModel("FAQ-1", "GENERAL", "Question").hasId());
    assertFalse(new ChatbotFaqViewModel("   ", "GENERAL", "Question").hasId());
  }

  @Test
  void categoryTextShouldReturnGeneralWhenCategoryIsBlank() {
    ChatbotFaqViewModel viewModel = new ChatbotFaqViewModel("FAQ-1", "   ", "Question");

    assertEquals("GENERAL", viewModel.categoryText());
  }

  @Test
  void displayTextShouldReturnFallbackWhenQuestionIsBlank() {
    ChatbotFaqViewModel viewModel = new ChatbotFaqViewModel("FAQ-1", "GENERAL", "   ");

    assertEquals("Câu hỏi chưa có nội dung", viewModel.displayText());
  }

  @Test
  void toStringShouldReturnDisplayText() {
    ChatbotFaqViewModel viewModel = new ChatbotFaqViewModel("FAQ-1", "GENERAL", "How to bid?");

    assertEquals("How to bid?", viewModel.toString());
  }
}
