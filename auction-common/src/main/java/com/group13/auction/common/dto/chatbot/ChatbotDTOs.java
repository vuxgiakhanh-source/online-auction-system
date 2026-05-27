package com.group13.auction.common.dto.chatbot;

/** DTO dùng chung cho module Chatbot (client ↔ server qua WebSocket). */
public final class ChatbotDTOs {

  private ChatbotDTOs() {}

  /** Yêu cầu hỏi chatbot — chọn một trong {@code faqId} hoặc {@code query}. */
  public static class ChatbotAskRequestDTO {
    private String faqId;
    private String query;

    public ChatbotAskRequestDTO() {}

    public static ChatbotAskRequestDTO byFaqId(String faqId) {
      ChatbotAskRequestDTO dto = new ChatbotAskRequestDTO();
      dto.faqId = faqId;
      return dto;
    }

    public static ChatbotAskRequestDTO byQuery(String query) {
      ChatbotAskRequestDTO dto = new ChatbotAskRequestDTO();
      dto.query = query;
      return dto;
    }

    public String getFaqId() {
      return faqId;
    }

    public void setFaqId(String faqId) {
      this.faqId = faqId;
    }

    public String getQuery() {
      return query;
    }

    public void setQuery(String query) {
      this.query = query;
    }
  }

  /** Yêu cầu danh sách FAQ theo category (null = tất cả). */
  public static class ChatbotFaqListRequestDTO {
    private String category;

    public ChatbotFaqListRequestDTO() {}

    public ChatbotFaqListRequestDTO(String category) {
      this.category = category;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }
  }

  /** Phản hồi chatbot — mirror JSON server gửi về. */
  public static class ChatbotResponseDTO {
    public enum ResponseStatus {
      SUCCESS,
      NOT_FOUND,
      FAQ_LIST
    }

    private String faqId;
    private String question;
    private String answer;
    private String category;
    private ResponseStatus status;
    private String fallbackMessage;
    private String timestamp;

    public String getFaqId() {
      return faqId;
    }

    public void setFaqId(String faqId) {
      this.faqId = faqId;
    }

    public String getQuestion() {
      return question;
    }

    public void setQuestion(String question) {
      this.question = question;
    }

    public String getAnswer() {
      return answer;
    }

    public void setAnswer(String answer) {
      this.answer = answer;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public ResponseStatus getStatus() {
      return status;
    }

    public void setStatus(ResponseStatus status) {
      this.status = status;
    }

    public String getFallbackMessage() {
      return fallbackMessage;
    }

    public void setFallbackMessage(String fallbackMessage) {
      this.fallbackMessage = fallbackMessage;
    }

    public String getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(String timestamp) {
      this.timestamp = timestamp;
    }

    public boolean isSuccess() {
      return status == ResponseStatus.SUCCESS;
    }
  }

  /** Mục FAQ rút gọn trong danh sách gợi ý. */
  public static class FaqSummaryDTO {
    private String id;
    private String category;
    private String question;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public String getQuestion() {
      return question;
    }

    public void setQuestion(String question) {
      this.question = question;
    }
  }

  /** Payload {@code CHATBOT_FAQ_LIST_SUCCESS}. */
  public static class ChatbotFaqListResponseDTO {
    private ChatbotResponseDTO header;
    private java.util.List<FaqSummaryDTO> faqs;
    private int totalCount;

    public ChatbotResponseDTO getHeader() {
      return header;
    }

    public void setHeader(ChatbotResponseDTO header) {
      this.header = header;
    }

    public java.util.List<FaqSummaryDTO> getFaqs() {
      return faqs;
    }

    public void setFaqs(java.util.List<FaqSummaryDTO> faqs) {
      this.faqs = faqs;
    }

    public int getTotalCount() {
      return totalCount;
    }

    public void setTotalCount(int totalCount) {
      this.totalCount = totalCount;
    }
  }
}
