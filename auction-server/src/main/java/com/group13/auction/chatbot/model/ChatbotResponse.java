package com.group13.auction.chatbot.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DTO (Data Transfer Object) đại diện cho phản hồi của chatbot gửi về client.
 *
 * <p>Lớp này được serialize thành JSON qua Gson và gửi qua WebSocket trong packet {@code
 * CHATBOT_ANSWER} hoặc {@code CHATBOT_NOT_FOUND}.
 *
 * <p>Sử dụng <strong>Static Factory Methods</strong> thay vì constructor trực tiếp để tạo ra các
 * loại phản hồi khác nhau một cách rõ ràng và dễ đọc:
 *
 * <ul>
 *   <li>{@link #ofSuccess(FAQ)} — tìm thấy câu trả lời
 *   <li>{@link #ofNotFound(String)} — không tìm thấy câu trả lời phù hợp
 *   <li>{@link #ofFaqList(String)} — tiêu đề khi trả danh sách FAQ
 * </ul>
 *
 * <p>Trường {@code timestamp} giúp client hiển thị thời gian phản hồi.
 *
 * @author Group 13 — Chatbot Module
 * @version 1.0
 */
public class ChatbotResponse {

  // ── Enum trạng thái phản hồi ──────────────────────────────────────────────

  /**
   * Trạng thái phản hồi của chatbot.
   *
   * <ul>
   *   <li>{@code SUCCESS} — tìm thấy FAQ phù hợp và trả về câu trả lời.
   *   <li>{@code NOT_FOUND} — không tìm thấy FAQ phù hợp với câu hỏi.
   *   <li>{@code FAQ_LIST} — trả về danh sách câu hỏi theo category (không kèm answer).
   * </ul>
   */
  public enum ResponseStatus {
    SUCCESS,
    NOT_FOUND,
    FAQ_LIST
  }

  // ── Fields ────────────────────────────────────────────────────────────────

  /** Mã FAQ tương ứng (null nếu NOT_FOUND hoặc FAQ_LIST). */
  private final String faqId;

  /** Nội dung câu hỏi. */
  private final String question;

  /** Nội dung câu trả lời chi tiết (null nếu NOT_FOUND). */
  private final String answer;

  /** Nhóm nghiệp vụ: GENERAL | BIDDING | PAYMENT | RATING | SELLER. */
  private final String category;

  /** Trạng thái phản hồi. */
  private final ResponseStatus status;

  /**
   * Thông điệp phụ dành cho trường hợp NOT_FOUND — gợi ý người dùng liên hệ Admin hoặc thử từ khóa
   * khác.
   */
  private final String fallbackMessage;

  /** Thời gian tạo phản hồi — giúp client hiển thị timestamp trong chat UI. */
  private final String timestamp;

  // ── Private Constructor ───────────────────────────────────────────────────

  /** Constructor nội bộ — chỉ được gọi qua Static Factory Methods bên dưới. */
  private ChatbotResponse(
      String faqId,
      String question,
      String answer,
      String category,
      ResponseStatus status,
      String fallbackMessage) {
    this.faqId = faqId;
    this.question = question;
    this.answer = answer;
    this.category = category;
    this.status = status;
    this.fallbackMessage = fallbackMessage;
    this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
  }

  // ── Static Factory Methods ────────────────────────────────────────────────

  /**
   * Tạo phản hồi thành công khi tìm thấy câu trả lời phù hợp.
   *
   * @param faq đối tượng FAQ tìm được
   * @return ChatbotResponse với status = SUCCESS
   */
  public static ChatbotResponse ofSuccess(FAQ faq) {
    return new ChatbotResponse(
        faq.getId(),
        faq.getQuestion(),
        faq.getAnswer(),
        faq.getCategory(),
        ResponseStatus.SUCCESS,
        null);
  }

  /**
   * Tạo phản hồi khi không tìm thấy câu trả lời phù hợp.
   *
   * <p>Kèm thông điệp gợi ý để người dùng không bị bỏ lại một mình — hướng họ liên hệ Admin hoặc
   * thử từ khóa khác.
   *
   * @param originalQuery câu hỏi gốc mà người dùng đã nhập
   * @return ChatbotResponse với status = NOT_FOUND
   */
  public static ChatbotResponse ofNotFound(String originalQuery) {
    String fallback =
        "Xin lỗi, tôi chưa hiểu câu hỏi của bạn. Câu hỏi: \""
            + originalQuery
            + "\". "
            + "Bạn có thể thử lại với từ khóa khác, hoặc liên hệ "
            + "Admin OmniBid để được hỗ trợ trực tiếp.";
    return new ChatbotResponse(null, originalQuery, null, null, ResponseStatus.NOT_FOUND, fallback);
  }

  /**
   * Tạo phản hồi tiêu đề khi trả về danh sách FAQ theo category. Thường đi kèm với payload list FAQ
   * riêng trong packet {@code CHATBOT_FAQ_LIST_SUCCESS}.
   *
   * @param category nhóm nghiệp vụ cần lấy danh sách (null = tất cả)
   * @return ChatbotResponse với status = FAQ_LIST
   */
  public static ChatbotResponse ofFaqList(String category) {
    String cat = (category == null || category.isBlank()) ? "TẤT CẢ" : category;
    String question = "Danh sách câu hỏi thường gặp — Nhóm: " + cat;
    String answer =
        "Dưới đây là các câu hỏi phổ biến trong nhóm "
            + cat
            + ". Chọn mã FAQ (VD: FAQ_001) để xem câu trả lời chi tiết.";
    return new ChatbotResponse(null, question, answer, category, ResponseStatus.FAQ_LIST, null);
  }

  // ── Getters ───────────────────────────────────────────────────────────────

  public String getFaqId() {
    return faqId;
  }

  public String getQuestion() {
    return question;
  }

  public String getAnswer() {
    return answer;
  }

  public String getCategory() {
    return category;
  }

  public ResponseStatus getStatus() {
    return status;
  }

  public String getFallbackMessage() {
    return fallbackMessage;
  }

  public String getTimestamp() {
    return timestamp;
  }

  /**
   * @return true nếu chatbot tìm được câu trả lời phù hợp
   */
  public boolean isSuccess() {
    return status == ResponseStatus.SUCCESS;
  }

  @Override
  public String toString() {
    return "ChatbotResponse{status="
        + status
        + ", faqId='"
        + faqId
        + "'"
        + ", category='"
        + category
        + "'}";
  }
}
