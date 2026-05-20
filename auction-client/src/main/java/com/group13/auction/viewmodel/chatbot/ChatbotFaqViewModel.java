package com.group13.auction.viewmodel.chatbot;

import java.util.Objects;

/** Dữ liệu FAQ chatbot rút gọn để hiển thị trong danh sách gợi ý. */
public final class ChatbotFaqViewModel {

  private final String id;
  private final String category;
  private final String question;

  /**
   * Tạo view model cho một câu hỏi FAQ.
   *
   * @param id mã FAQ
   * @param category nhóm FAQ
   * @param question câu hỏi gợi ý
   */
  public ChatbotFaqViewModel(String id, String category, String question) {
    this.id = Objects.requireNonNullElse(id, "").trim();
    this.category = Objects.requireNonNullElse(category, "GENERAL").trim();
    this.question = Objects.requireNonNullElse(question, "").trim();
  }

  public String id() {
    return id;
  }

  public String category() {
    return category;
  }

  public String question() {
    return question;
  }

  public boolean hasId() {
    return !id.isBlank();
  }

  public String categoryText() {
    return category.isBlank() ? "GENERAL" : category;
  }

  public String displayText() {
    return question.isBlank() ? "Câu hỏi chưa có nội dung" : question;
  }

  @Override
  public String toString() {
    return displayText();
  }
}