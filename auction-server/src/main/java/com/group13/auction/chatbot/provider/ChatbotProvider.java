package com.group13.auction.chatbot.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.group13.auction.chatbot.model.ChatbotResponse;
import com.group13.auction.chatbot.model.FAQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Provider trung tâm của Chatbot Module — áp dụng <strong>Singleton Pattern</strong>.
 *
 * <h3>Tại sao dùng Singleton?</h3>
 * <p>File {@code faq_data.json} chỉ cần đọc và parse vào bộ nhớ <em>một lần duy nhất</em>
 * khi server khởi động. Singleton đảm bảo:
 * <ul>
 *   <li>Không tốn I/O đọc file mỗi lần có request chatbot.</li>
 *   <li>Dữ liệu FAQ nhất quán trong toàn bộ vòng đời ứng dụng.</li>
 *   <li>Thread-safe nhờ <em>Initialization-on-demand holder idiom</em>
 *       (lazy loading, không dùng {@code synchronized} toàn cục).</li>
 * </ul>
 *
 * <h3>Chiến lược tìm kiếm:</h3>
 * <ol>
 *   <li><strong>Tra cứu theo ID</strong> — {@code getAnswerByQuestionId()} → O(1) với HashMap.</li>
 *   <li><strong>Tìm kiếm theo từ khóa</strong> — {@code searchByQuery()} → tính matching ratio
 *       dựa trên số từ khớp so với độ dài câu hỏi FAQ, trả về FAQ có ratio cao nhất.</li>
 *   <li><strong>Lọc theo category</strong> — {@code getFaqsByCategory()} → O(n) filter.</li>
 * </ol>
 *
 * <h3>Lưu ý triển khai thực tế:</h3>
 * <p>Để tháo rời hoàn toàn theo kiểu Microservice, {@code ChatbotProvider} chỉ
 * phụ thuộc vào file JSON — không kết nối DB, không phụ thuộc service khác.
 * Khi cần cập nhật FAQ, chỉ cần thay thế file JSON và restart service.
 *
 * @author Group 13 — Chatbot Module
 * @version 1.0
 */
public class ChatbotProvider {

    private static final Logger log = LoggerFactory.getLogger(ChatbotProvider.class);

    /** Đường dẫn file FAQ trong classpath (đặt trong resources của chatbot module). */
    private static final String FAQ_DATA_RESOURCE_PATH = "/chatbot/faq_data.json";

    /**
     * Matching ratio tối thiểu để một FAQ được coi là "phù hợp" với câu hỏi.
     * Điều chỉnh hằng số này để cân bằng giữa precision và recall.
     */
    private static final double MINIMUM_MATCHING_RATIO = 0.4;

    // ── Dữ liệu nội bộ ────────────────────────────────────────────────────────

    /**
     * Index tra cứu nhanh theo faqId → FAQ.
     * Được xây dựng một lần trong constructor, không thay đổi sau đó.
     */
    private final Map<String, FAQ> faqIndexById;

    /**
     * Danh sách đầy đủ toàn bộ FAQ — dùng cho tìm kiếm keyword và lọc category.
     */
    private final List<FAQ> allFaqs;

    // ══════════════════════════════════════════════════════════════════════════
    // Singleton — Initialization-on-demand holder idiom
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Holder class nội bộ — chứa instance duy nhất của {@code ChatbotProvider}.
     *
     * <p>Java đảm bảo class holder chỉ được load (và instance chỉ được tạo) khi
     * {@link #getInstance()} được gọi lần đầu. Cách này vừa lazy vừa thread-safe
     * mà không cần từ khóa {@code synchronized}.
     */
    private static class SingletonHolder {
        private static final ChatbotProvider INSTANCE = new ChatbotProvider();
    }

    /**
     * Truy xuất instance duy nhất của ChatbotProvider.
     *
     * <p>File {@code faq_data.json} sẽ được nạp lần đầu tiên và chỉ một lần duy nhất
     * tại đây, các lần gọi tiếp theo trả về instance đã có trong bộ nhớ.
     *
     * @return instance singleton của ChatbotProvider
     * @throws IllegalStateException nếu không thể đọc file FAQ khi khởi tạo
     */
    public static ChatbotProvider getInstance() {
        return SingletonHolder.INSTANCE;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Constructor nội bộ — chỉ được gọi 1 lần bởi SingletonHolder
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Private constructor — nạp và parse toàn bộ file {@code faq_data.json} vào bộ nhớ.
     *
     * <p>Dùng Gson để deserialize JSON, xây dựng HashMap để tra cứu theo ID trong O(1).
     * Nếu file không tồn tại hoặc parse lỗi → log cảnh báo và khởi động với danh sách rỗng
     * (chatbot vẫn hoạt động, chỉ luôn trả về NOT_FOUND).
     */
    private ChatbotProvider() {
        log.info("[ChatbotProvider] Khởi tạo Singleton — bắt đầu nạp faq_data.json...");

        List<FAQ>         loadedFaqs  = new ArrayList<>();
        Map<String, FAQ>  indexById   = new HashMap<>();

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

        // Bất biến: sau khi constructor xong, các field này không được thay đổi
        this.allFaqs     = Collections.unmodifiableList(loadedFaqs);
        this.faqIndexById = Collections.unmodifiableMap(indexById);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Public API — Phương thức tìm kiếm chính
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Tra cứu câu trả lời theo mã FAQ định danh (ví dụ: "FAQ_001").
     *
     * <p>Đây là phương thức tra cứu nhanh nhất — O(1) — dành cho trường hợp
     * người dùng chọn câu hỏi từ danh sách menu thay vì nhập tự do.
     *
     * @param faqId mã định danh FAQ (không phân biệt hoa/thường)
     * @return {@code ChatbotResponse.ofSuccess(faq)} nếu tìm thấy,
     *         {@code ChatbotResponse.ofNotFound(faqId)} nếu không có mã này
     */
    public ChatbotResponse getAnswerByQuestionId(String faqId) {
        if (faqId == null || faqId.isBlank()) {
            return ChatbotResponse.ofNotFound("[mã câu hỏi trống]");
        }

        // Tra cứu HashMap — O(1)
        FAQ faq = faqIndexById.get(faqId.toUpperCase().trim());

        if (faq != null) {
            log.debug("[ChatbotProvider] Tìm thấy FAQ theo ID: {}", faqId);
            return ChatbotResponse.ofSuccess(faq);
        }

        log.debug("[ChatbotProvider] Không tìm thấy FAQ với ID: {}", faqId);
        return ChatbotResponse.ofNotFound(faqId);
    }

    /**
     * Tìm kiếm câu trả lời theo câu hỏi tự do (free-text query).
     *
     * <h3>Thuật toán matching ratio:</h3>
     * <ol>
     *   <li>Normalize query và FAQ: lowercase, bỏ dấu tiếng Việt, bỏ ký tự đặc biệt.</li>
     *   <li>Tokenize thành {@code Set<String>} để tránh đếm trùng từ.</li>
     *   <li>Tính {@code matchedWordCount / totalFaqQuestionWords} cho từng FAQ.</li>
     *   <li>FAQ có ratio cao nhất và đạt {@code MINIMUM_MATCHING_RATIO} được chọn.</li>
     * </ol>
     *
     * @param query câu hỏi người dùng nhập (free-text, tiếng Việt)
     * @return {@code ChatbotResponse} phù hợp nhất, hoặc NOT_FOUND nếu ratio quá thấp
     */
    public ChatbotResponse searchByQuery(String query) {
        if (query == null || query.isBlank()) {
            return ChatbotResponse.ofNotFound("[câu hỏi trống]");
        }

        Set<String> queryWords = tokenize(query);
        if (queryWords.isEmpty()) {
            return ChatbotResponse.ofNotFound(query);
        }

        Optional<MatchedFaq> bestMatch = allFaqs.stream()
                .map(faq -> new MatchedFaq(faq, calculateMatchingRatio(faq, queryWords)))
                .max((a, b) -> Double.compare(a.matchingRatio, b.matchingRatio));

        if (bestMatch.isPresent()
                && bestMatch.get().matchingRatio >= MINIMUM_MATCHING_RATIO) {
            FAQ found = bestMatch.get().faq;
            log.debug("[ChatbotProvider] Tìm thấy FAQ '{}' với ratio={} cho query='{}'",
                    found.getId(), bestMatch.get().matchingRatio, query);
            return ChatbotResponse.ofSuccess(found);
        }

        double bestRatio = bestMatch.map(matched -> matched.matchingRatio).orElse(0.0);
        log.debug("[ChatbotProvider] Không tìm thấy FAQ phù hợp cho query='{}', bestRatio={}",
                query, bestRatio);
        return ChatbotResponse.ofNotFound(query);
    }

    /**
     * Lấy danh sách tất cả FAQ thuộc một nhóm nghiệp vụ (category).
     *
     * <p>Dùng để hiển thị menu câu hỏi gợi ý theo chủ đề trong giao diện chatbot.
     * Nếu {@code category} là null hoặc rỗng → trả về toàn bộ FAQ.
     *
     * @param category nhóm nghiệp vụ: GENERAL | BIDDING | PAYMENT | RATING | SELLER
     * @return danh sách FAQ thuộc category (có thể rỗng nếu category không hợp lệ)
     */
    public List<FAQ> getFaqsByCategory(String category) {
        if (category == null || category.isBlank()) {
            log.debug("[ChatbotProvider] Trả về toàn bộ {} FAQ.", allFaqs.size());
            return allFaqs; // Đã là unmodifiable
        }

        String upperCategory = category.toUpperCase().trim();
        List<FAQ> result = allFaqs.stream()
                .filter(faq -> upperCategory.equals(faq.getCategory()))
                .collect(Collectors.toList());

        log.debug("[ChatbotProvider] Category '{}' → {} FAQ.", upperCategory, result.size());
        return Collections.unmodifiableList(result);
    }

    /**
     * Trả về toàn bộ danh sách FAQ (tất cả category).
     *
     * @return danh sách không thể thay đổi (unmodifiable)
     */
    public List<FAQ> getAllFaqs() {
        return allFaqs;
    }

    /**
     * Trả về số lượng FAQ đang được nạp trong bộ nhớ.
     * Dùng cho health-check hoặc log thông tin khi debug.
     *
     * @return tổng số FAQ
     */
    public int getTotalFaqCount() {
        return allFaqs.size();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Đọc và parse file {@code faq_data.json} từ classpath bằng Gson.
     *
     * <p>Dùng {@code InputStream} thay vì {@code File} để hoạt động đúng trong
     * fat JAR (Maven Shade Plugin) — resource được đóng gói bên trong JAR.
     *
     * @return danh sách FAQ đã parse
     * @throws IOException nếu không tìm thấy file hoặc JSON không hợp lệ
     */
    private List<FAQ> loadFaqsFromClasspath() throws IOException {
        // Tìm resource trong classpath (hoạt động cả khi chạy từ IDE lẫn fat JAR)
        InputStream inputStream = getClass().getResourceAsStream(FAQ_DATA_RESOURCE_PATH);

        if (inputStream == null) {
            throw new IOException("Không tìm thấy file FAQ tại classpath: " + FAQ_DATA_RESOURCE_PATH);
        }

        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            // Parse JSON root object
            JsonObject root     = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray  faqArray = root.getAsJsonArray("faqs");

            if (faqArray == null) {
                throw new IOException("File JSON không có mảng 'faqs'.");
            }

            Gson     gson   = new Gson();
            List<FAQ> faqs  = new ArrayList<>();

            for (JsonElement element : faqArray) {
                FAQ raw = gson.fromJson(element, FAQ.class);
                if (!isValidFaq(raw)) {
                    log.warn("[ChatbotProvider] Bỏ qua FAQ không hợp lệ: {}", element);
                    continue;
                }
                // Gson gán List mutable — bọc lại qua constructor FAQ để keywords bất biến (an toàn luồng).
                List<String> safeKeywords = raw.getKeywords() == null || raw.getKeywords().isEmpty()
                        ? List.of()
                        : List.copyOf(raw.getKeywords());
                faqs.add(new FAQ(raw.getId(), raw.getCategory(), safeKeywords,
                        raw.getQuestion(), raw.getAnswer()));
            }

            return faqs;
        }
    }

    /**
     * Kiểm tra tính hợp lệ của một FAQ sau khi parse từ JSON.
     * FAQ hợp lệ phải có đầy đủ id, question, và answer.
     *
     * @param faq đối tượng FAQ cần kiểm tra
     * @return true nếu FAQ có đủ thông tin bắt buộc
     */
    private boolean isValidFaq(FAQ faq) {
        return faq != null
                && faq.getId()       != null && !faq.getId().isBlank()
                && faq.getQuestion() != null && !faq.getQuestion().isBlank()
                && faq.getAnswer()   != null && !faq.getAnswer().isBlank();
    }

    private double calculateMatchingRatio(FAQ faq, Set<String> queryWords) {
        Set<String> faqQuestionWords = tokenize(faq.getQuestion());
        if (faqQuestionWords.isEmpty() || queryWords.isEmpty()) {
            return 0.0;
        }

        Set<String> faqMatchingWords = getFaqMatchingWords(faq, faqQuestionWords);
        long matchedWordCount = queryWords.stream()
                .filter(faqMatchingWords::contains)
                .count();

        return (double) matchedWordCount / faqQuestionWords.size();
    }

    private Set<String> getFaqMatchingWords(FAQ faq, Set<String> faqQuestionWords) {
        Set<String> matchingWords = new HashSet<>(faqQuestionWords);
        for (String keyword : faq.getKeywords()) {
            matchingWords.addAll(tokenize(keyword));
        }
        return matchingWords;
    }

    private Set<String> tokenize(String text) {
        String normalizedText = normalize(text);
        if (normalizedText.isBlank()) {
            return Collections.emptySet();
        }

        return List.of(normalizedText.split("\\s+")).stream()
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.toLowerCase().trim()
                .replace('\u0111', 'd')
                .replace('\u0110', 'd');
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        normalized = normalized.replaceAll("[^a-z0-9\\s]", " ");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    // ── Inner class phụ trợ — không expose ra ngoài ───────────────────────────

    private static class MatchedFaq {
        final FAQ faq;
        final double matchingRatio;

        MatchedFaq(FAQ faq, double matchingRatio) {
            this.faq   = faq;
            this.matchingRatio = matchingRatio;
        }
    }
}
