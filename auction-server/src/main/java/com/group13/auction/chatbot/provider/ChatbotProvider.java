package com.group13.auction.chatbot.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.group13.auction.chatbot.model.ChatbotResponse;
import com.group13.auction.chatbot.model.FAQ;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider trung tâm của Chatbot Module — áp dụng <strong>Singleton Pattern</strong>. Đã được
 * refactor thuật toán Matching Ratio tối ưu cho câu hỏi ngắn và hỗ trợ Khớp nguyên cụm.
 *
 * @author Group 13 — Chatbot Module
 * @version 1.1
 */
public class ChatbotProvider {

  private static final Logger log = LoggerFactory.getLogger(ChatbotProvider.class);

  private static final String FAQ_DATA_RESOURCE_PATH = "/chatbot/faq_data.json";

  /**
   * Threshold mềm (0.5). Một câu hỏi đạt >= 50% số từ của user nhập vào sẽ được coi là hợp lệ nếu
   * không có trường hợp khớp nguyên cụm.
   */
  private static final double MINIMUM_MATCHING_SCORE = 0.5;

  private final Map<String, FAQ> faqIndexById;
  private final List<FAQ> allFaqs;

  private static class SingletonHolder {
    private static final ChatbotProvider INSTANCE = new ChatbotProvider();
  }

  /** Trả về singleton instance của chatbot provider. */
  public static ChatbotProvider getInstance() {
    return SingletonHolder.INSTANCE;
  }

  private ChatbotProvider() {
    log.info("[ChatbotProvider] Khởi tạo Singleton — bắt đầu nạp faq_data.json...");

    List<FAQ> loadedFaqs = new ArrayList<>();
    Map<String, FAQ> indexById = new HashMap<>();

    try {
      loadedFaqs = loadFaqsFromClasspath();
      for (FAQ faq : loadedFaqs) {
        indexById.put(faq.getId(), faq);
      }
      log.info("[ChatbotProvider] Nạp thành công {} câu hỏi FAQ.", loadedFaqs.size());
    } catch (Exception e) {
      log.error("[ChatbotProvider] CẢNH BÁO: Không thể nạp faq_data.json — {}", e.getMessage());
      log.warn("[ChatbotProvider] Chatbot sẽ hoạt động nhưng luôn trả về NOT_FOUND.");
    }

    this.allFaqs = Collections.unmodifiableList(loadedFaqs);
    this.faqIndexById = Collections.unmodifiableMap(indexById);
  }
  // Public API — Phương thức tìm kiếm chính
  /** Tìm câu trả lời theo mã FAQ. */
  public ChatbotResponse getAnswerByQuestionId(String faqId) {
    if (faqId == null || faqId.isBlank()) {
      return ChatbotResponse.ofNotFound("[mã câu hỏi trống]");
    }

    FAQ faq = faqIndexById.get(faqId.toUpperCase().trim());

    if (faq != null) {
      log.debug("[ChatbotProvider] Tìm thấy FAQ theo ID: {}", faqId);
      return ChatbotResponse.ofSuccess(faq);
    }

    log.debug("[ChatbotProvider] Không tìm thấy FAQ với ID: {}", faqId);
    return ChatbotResponse.ofNotFound(faqId);
  }

  /**
   * Tìm kiếm câu trả lời theo câu hỏi tự do (free-text query). Chiến lược mới: 1. Ưu tiên tuyệt đối
   * nếu chuỗi câu hỏi user xuất hiện nguyên cụm trong câu hỏi FAQ hoặc Keywords. 2. Nếu không khớp
   * nguyên cụm, tính điểm matchingScore = matchedWords / totalUserWords.
   */
  public ChatbotResponse searchByQuery(String query) {
    if (query == null || query.isBlank()) {
      return ChatbotResponse.ofNotFound("[câu hỏi trống]");
    }

    // Bước 1: Chuẩn hóa câu hỏi của User
    String normalizedQuery = normalize(query);
    if (normalizedQuery.isBlank()) {
      return ChatbotResponse.ofNotFound(query);
    }

    // Tokenize câu hỏi user để tính toán số từ
    Set<String> queryWords = tokenize(normalizedQuery);
    if (queryWords.isEmpty()) {
      return ChatbotResponse.ofNotFound(query);
    }

    // CHIẾN LƯỢC 1: ƯU TIÊN KHỚP NGUYÊN CỤM (Exact Phrase Containment)
    for (FAQ faq : allFaqs) {
      String normalizedFaqQuestion = normalize(faq.getQuestion());

      // Kiểm tra xem query có nằm nguyên vẹn trong câu hỏi FAQ không
      if (normalizedFaqQuestion.contains(normalizedQuery)) {
        log.info(
            "[ChatbotProvider] Khớp nguyên cụm thành công (FAQ Question) cho query='{}' -> FAQ: {}",
            query,
            faq.getId());
        return ChatbotResponse.ofSuccess(faq);
      }

      // Kiểm tra xem query có nằm nguyên vẹn trong Keywords không
      for (String keyword : faq.getKeywords()) {
        if (normalize(keyword).contains(normalizedQuery)) {
          log.info(
              "[ChatbotProvider] Khớp nguyên cụm thành công (Keyword) cho query='{}' -> FAQ: {}",
              query,
              faq.getId());
          return ChatbotResponse.ofSuccess(faq);
        }
      }
    }

    // CHIẾN LƯỢC 2: TÍNH ĐIỂM MATCHING RATIO THEO TỪ KHÓA (Từ câu ngắn của User)
    Optional<MatchedFaq> bestMatch =
        allFaqs.stream()
            .map(faq -> new MatchedFaq(faq, calculateMatchingScore(faq, queryWords)))
            .max((a, b) -> Double.compare(a.matchingScore, b.matchingScore));

    if (bestMatch.isPresent() && bestMatch.get().matchingScore >= MINIMUM_MATCHING_SCORE) {
      FAQ found = bestMatch.get().faq;
      log.debug(
          "[ChatbotProvider] Tìm thấy FAQ '{}' qua điểm số từ khớp với score={} cho query='{}'",
          found.getId(),
          bestMatch.get().matchingScore,
          query);
      return ChatbotResponse.ofSuccess(found);
    }

    double bestScore = bestMatch.map(matched -> matched.matchingScore).orElse(0.0);
    log.debug(
        "[ChatbotProvider] Không tìm thấy FAQ phù hợp cho query='{}', bestScore={}",
        query,
        bestScore);
    return ChatbotResponse.ofNotFound(query);
  }

  /** Lọc danh sách FAQ theo category, null/rỗng thì trả về toàn bộ. */
  public List<FAQ> getFaqsByCategory(String category) {
    if (category == null || category.isBlank()) {
      log.debug("[ChatbotProvider] Trả về toàn bộ {} FAQ.", allFaqs.size());
      return allFaqs;
    }

    String upperCategory = category.toUpperCase().trim();
    List<FAQ> result =
        allFaqs.stream()
            .filter(faq -> upperCategory.equals(faq.getCategory()))
            .collect(Collectors.toList());

    log.debug("[ChatbotProvider] Category '{}' → {} FAQ.", upperCategory, result.size());
    return Collections.unmodifiableList(result);
  }

  /** Trả về toàn bộ FAQ đã nạp từ file dữ liệu. */
  public List<FAQ> getAllFaqs() {
    return allFaqs;
  }

  public int getTotalFaqCount() {
    return allFaqs.size();
  }
  // Private helpers (Đã refactor cô đọng và loại bỏ duplicate logic)
  private List<FAQ> loadFaqsFromClasspath() throws IOException {
    InputStream inputStream = getClass().getResourceAsStream(FAQ_DATA_RESOURCE_PATH);

    if (inputStream == null) {
      throw new IOException("Không tìm thấy file FAQ tại classpath: " + FAQ_DATA_RESOURCE_PATH);
    }

    try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
      JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
      JsonArray faqArray = root.getAsJsonArray("faqs");

      if (faqArray == null) {
        throw new IOException("File JSON không có mảng 'faqs'.");
      }

      Gson gson = new Gson();
      List<FAQ> faqs = new ArrayList<>();

      for (JsonElement element : faqArray) {
        FAQ raw = gson.fromJson(element, FAQ.class);
        if (!isValidFaq(raw)) {
          log.warn("[ChatbotProvider] Bỏ qua FAQ không hợp lệ: {}", element);
          continue;
        }
        List<String> safeKeywords =
            raw.getKeywords() == null || raw.getKeywords().isEmpty()
                ? List.of()
                : List.copyOf(raw.getKeywords());
        faqs.add(
            new FAQ(
                raw.getId(), raw.getCategory(), safeKeywords, raw.getQuestion(), raw.getAnswer()));
      }

      return faqs;
    }
  }

  private boolean isValidFaq(FAQ faq) {
    return faq != null
        && faq.getId() != null
        && !faq.getId().isBlank()
        && faq.getQuestion() != null
        && !faq.getQuestion().isBlank()
        && faq.getAnswer() != null
        && !faq.getAnswer().isBlank();
  }

  /**
   * Thuật toán tính điểm mới: Đổi mẫu số thành tổng số từ của User Query (queryWords.size()) Công
   * thức: score = matchedWords / totalUserWords
   */
  private double calculateMatchingScore(FAQ faq, Set<String> queryWords) {
    if (queryWords.isEmpty()) {
      return 0.0;
    }

    // Gom toàn bộ từ vựng đã được chuẩn hóa của FAQ (gồm cả Question và tất cả Keywords)
    Set<String> faqAllWords = new HashSet<>(tokenize(normalize(faq.getQuestion())));
    for (String keyword : faq.getKeywords()) {
      faqAllWords.addAll(tokenize(normalize(keyword)));
    }

    // Đếm số từ trùng khớp giữa User và FAQ
    long matchedWordCount = queryWords.stream().filter(faqAllWords::contains).count();

    return (double) matchedWordCount / queryWords.size();
  }

  /** Tách chuỗi đã được chuẩn hóa thành một Set các từ riêng biệt. */
  private Set<String> tokenize(String normalizedText) {
    if (normalizedText == null || normalizedText.isBlank()) {
      return Collections.emptySet();
    }

    return List.of(normalizedText.split("\\s+")).stream()
        .filter(token -> !token.isBlank())
        .collect(Collectors.toSet());
  }

  /**
   * Chuẩn hóa văn bản: Chuyển lowercase, khử dấu tiếng Việt, loại bỏ ký tự đặc biệt, trim khoảng
   * trắng dư.
   */
  private String normalize(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    // Chuyển chữ thường và đổi chữ đ/Đ thành d
    String normalized = text.toLowerCase().trim().replace('đ', 'd').replace('Đ', 'd');

    // Khử toàn bộ dấu tiếng Việt bằng Normalizer
    normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");

    // Loại bỏ ký tự đặc biệt, chỉ giữ lại ký tự chữ, số và khoảng trắng
    normalized = normalized.replaceAll("[^a-z0-9\\s]", " ");

    // Gộp nhiều khoảng trắng liền nhau thành 1 khoảng trắng duy nhất
    return normalized.replaceAll("\\s+", " ").trim();
  }

  // Inner class phụ trợ — Cập nhật tên trường để khớp ý nghĩa mới

  private static class MatchedFaq {
    final FAQ faq;
    final double matchingScore;

    MatchedFaq(FAQ faq, double matchingScore) {
      this.faq = faq;
      this.matchingScore = matchingScore;
    }
  }
}
