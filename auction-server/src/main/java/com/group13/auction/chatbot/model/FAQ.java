package com.group13.auction.chatbot.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Model đại diện cho một mục câu hỏi thường gặp (FAQ) trong hệ thống chatbot.
 *
 * <p>Dữ liệu được đọc từ {@code faq_data.json} thông qua {@link
 * com.group13.auction.chatbot.provider.ChatbotProvider}. Lớp này là immutable sau khi khởi tạo để
 * đảm bảo thread-safety khi nhiều client truy vấn chatbot đồng thời.
 *
 * <p>Mỗi FAQ thuộc một {@code category} (nhóm nghiệp vụ) và có danh sách {@code keywords} giúp
 * thuật toán tìm kiếm so khớp câu hỏi tự do của người dùng.
 *
 * <h3>Cấu trúc JSON tương ứng:</h3>
 *
 * <pre>{@code
 * {
 *   "id": "FAQ_001",
 *   "category": "GENERAL",
 *   "keywords": ["omnibid", "hệ thống"],
 *   "question": "OmniBid là gì?",
 *   "answer": "OmniBid là sàn đấu giá trực tuyến..."
 * }
 * }</pre>
 *
 * @author Group 13 — Chatbot Module
 * @version 1.0
 */
public class FAQ {

  // Fields

  /**
   * Mã định danh duy nhất của câu hỏi (ví dụ: "FAQ_001"). Dùng để tra cứu trực tiếp qua {@code
   * getAnswerByQuestionId()}.
   */
  private String id;

  /**
   * Nhóm nghiệp vụ của câu hỏi. Các giá trị hợp lệ: GENERAL | BIDDING | PAYMENT | RATING | SELLER.
   */
  private String category;

  /**
   * Danh sách từ khóa liên quan, dùng để tìm kiếm câu hỏi phù hợp khi người dùng nhập câu hỏi tự do
   * (free-text query).
   */
  private List<String> keywords;

  /** Nội dung câu hỏi (tiếng Việt, thân thiện với người dùng). */
  private String question;

  /** Nội dung câu trả lời chi tiết. */
  private String answer;

  // Constructor

  /**
   * Constructor mặc định không tham số — bắt buộc để Gson deserialize từ JSON. Không dùng trực tiếp
   * trong code ứng dụng.
   */
  public FAQ() {}

  /**
   * Constructor đầy đủ — dùng cho unit test hoặc khởi tạo thủ công.
   *
   * @param id mã định danh duy nhất
   * @param category nhóm nghiệp vụ
   * @param keywords danh sách từ khóa tìm kiếm
   * @param question nội dung câu hỏi
   * @param answer nội dung câu trả lời
   */
  public FAQ(String id, String category, List<String> keywords, String question, String answer) {
    this.id = id;
    this.category = category;
    this.keywords =
        keywords != null ? Collections.unmodifiableList(keywords) : Collections.emptyList();
    this.question = question;
    this.answer = answer;
  }

  // Getters

  /**
   * @return mã định danh FAQ (ví dụ: "FAQ_001")
   */
  public String getId() {
    return id;
  }

  /**
   * @return category nhóm nghiệp vụ (GENERAL, BIDDING, PAYMENT, RATING, SELLER)
   */
  public String getCategory() {
    return category;
  }

  /**
   * @return danh sách từ khóa tìm kiếm (unmodifiable để tránh bị thay đổi ngoài ý muốn)
   */
  public List<String> getKeywords() {
    return keywords != null ? keywords : Collections.emptyList();
  }

  /**
   * @return nội dung câu hỏi
   */
  public String getQuestion() {
    return question;
  }

  /**
   * @return nội dung câu trả lời
   */
  public String getAnswer() {
    return answer;
  }

  // Utility

  /**
   * Kiểm tra FAQ có chứa từ khóa cho trước không (so khớp không phân biệt hoa/thường).
   *
   * <p>Dùng nội bộ trong {@link com.group13.auction.chatbot.provider.ChatbotProvider} để tính điểm
   * phù hợp (relevance score) khi tìm kiếm free-text.
   *
   * @param keyword từ khóa cần kiểm tra
   * @return true nếu từ khóa xuất hiện trong danh sách
   */
  public boolean containsKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return false;
    }
    String lowerKeyword = keyword.toLowerCase().trim();
    return getKeywords().stream()
        .anyMatch(
            k -> k.toLowerCase().contains(lowerKeyword) || lowerKeyword.contains(k.toLowerCase()));
  }

  @Override
  public String toString() {
    return "FAQ{id='" + id + "', category='" + category + "', question='" + question + "'}";
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FAQ)) {
      return false;
    }
    FAQ other = (FAQ) obj;
    return Objects.equals(id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
