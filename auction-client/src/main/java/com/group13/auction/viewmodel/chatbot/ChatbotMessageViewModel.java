package com.group13.auction.viewmodel.chatbot;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Dữ liệu một tin nhắn chatbot đã được chuẩn bị để hiển thị trên JavaFX UI. */
public final class ChatbotMessageViewModel {

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  private final String senderText;
  private final String content;
  private final String timestampText;
  private final boolean userMessage;

  /**
   * Tạo view model cho một tin nhắn chatbot.
   *
   * @param senderText tên người gửi hiển thị trên UI
   * @param content nội dung tin nhắn
   * @param timestampText thời điểm hiển thị ngắn gọn
   * @param userMessage {@code true} nếu là tin nhắn của người dùng
   */
  public ChatbotMessageViewModel(
      String senderText, String content, String timestampText, boolean userMessage) {
    this.senderText = Objects.requireNonNullElse(senderText, "").trim();
    this.content = Objects.requireNonNullElse(content, "").trim();
    this.timestampText = Objects.requireNonNullElse(timestampText, "").trim();
    this.userMessage = userMessage;
  }

  /**
   * Tạo tin nhắn người dùng từ nội dung nhập trên UI.
   *
   * @param content nội dung câu hỏi
   * @return view model tin nhắn người dùng
   */
  public static ChatbotMessageViewModel user(String content) {
    return new ChatbotMessageViewModel("You", content, nowText(), true);
  }

  /**
   * Tạo tin nhắn phản hồi từ OMNI.
   *
   * @param content nội dung phản hồi
   * @return view model tin nhắn chatbot
   */
  public static ChatbotMessageViewModel omni(String content) {
    return new ChatbotMessageViewModel("OMNI", content, nowText(), false);
  }

  public String senderText() {
    return senderText;
  }

  public String content() {
    return content;
  }

  public String timestampText() {
    return timestampText;
  }

  public boolean userMessage() {
    return userMessage;
  }

  private static String nowText() {
    return LocalTime.now().format(TIME_FORMATTER);
  }
}
