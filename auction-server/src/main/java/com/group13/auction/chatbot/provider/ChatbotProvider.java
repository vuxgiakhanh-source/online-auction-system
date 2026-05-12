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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *   <li><strong>Tìm kiếm theo từ khóa</strong> — {@code searchByQuery()} → tính điểm phù hợp
 *       (relevance score) dựa trên số keyword khớp, trả về FAQ có điểm cao nhất.</li>
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
     * Điểm relevance tối thiểu để một FAQ được coi là "phù hợp" với câu hỏi.
     * Điều chỉnh hằng số này để cân bằng giữa precision và recall.
     */
    private static final int MINIMUM_RELEVANCE_SCORE = 1;

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
     * <h3>Thuật toán tính điểm phù hợp (Relevance Scoring):</h3>
     * <ol>
     *   <li>Tách câu hỏi của người dùng thành các token (phân tách bằng khoảng trắng/dấu câu).</li>
     *   <li>Với mỗi FAQ, đếm số token khớp với keyword trong danh sách {@code keywords}.</li>
     *   <li>Ngoài ra kiểm tra xem câu hỏi có chứa text của {@code question} field không.</li>
     *   <li>FAQ có điểm cao nhất (và ≥ {@code MINIMUM_RELEVANCE_SCORE}) được chọn.</li>
     * </ol>
     *
     * @param query câu hỏi người dùng nhập (free-text, tiếng Việt)
     * @return {@code ChatbotResponse} phù hợp nhất, hoặc NOT_FOUND nếu điểm quá thấp
     */
    public ChatbotResponse searchByQuery(String query) {
        if (query == null || query.isBlank()) {
            return ChatbotResponse.ofNotFound("[câu hỏi trống]");
        }

        String normalizedQuery = query.toLowerCase().trim();

        // Tính điểm relevance cho từng FAQ và chọn cái cao nhất
        Optional<ScoredFaq> bestMatch = allFaqs.stream()
                .map(faq -> new ScoredFaq(faq, calculateRelevanceScore(faq, normalizedQuery)))
                .filter(scored -> scored.score >= MINIMUM_RELEVANCE_SCORE)
                .max((a, b) -> Integer.compare(a.score, b.score));

        if (bestMatch.isPresent()) {
            FAQ found = bestMatch.get().faq;
            log.debug("[ChatbotProvider] Tìm thấy FAQ '{}' với điểm={} cho query='{}'",
                    found.getId(), bestMatch.get().score, query);
            return ChatbotResponse.ofSuccess(found);
        }

        log.debug("[ChatbotProvider] Không tìm thấy FAQ phù hợp cho query='{}'", query);
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
                FAQ faq = gson.fromJson(element, FAQ.class);
                if (isValidFaq(faq)) {
                    faqs.add(faq);
                } else {
                    log.warn("[ChatbotProvider] Bỏ qua FAQ không hợp lệ: {}", element);
                }
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

    /**
     * Tính điểm phù hợp (relevance score) giữa một FAQ và câu hỏi người dùng.
     *
     * <h3>Các tiêu chí tính điểm:</h3>
     * <ul>
     *   <li>+2 điểm: mỗi keyword của FAQ xuất hiện trong query (hoặc ngược lại).</li>
     *   <li>+3 điểm: query chứa chuỗi con của nội dung {@code question} field.</li>
     *   <li>+1 điểm: query khớp với category (BIDDING, PAYMENT, v.v.).</li>
     * </ul>
     *
     * @param faq            FAQ cần tính điểm
     * @param normalizedQuery câu hỏi đã chuẩn hóa (lowercase, trim)
     * @return điểm phù hợp (≥ 0)
     */
    private int calculateRelevanceScore(FAQ faq, String normalizedQuery) {
        int score = 0;

        // Tiêu chí 1: Keyword matching — mỗi keyword khớp +2 điểm
        for (String keyword : faq.getKeywords()) {
            if (faq.containsKeyword(extractRelevantToken(normalizedQuery, keyword))) {
                score += 2;
            }
        }

        // Tiêu chí 2: Câu hỏi FAQ xuất hiện trong query hoặc ngược lại — +3 điểm
        String lowerQuestion = faq.getQuestion().toLowerCase();
        if (normalizedQuery.contains(lowerQuestion)
                || lowerQuestion.contains(normalizedQuery)
                || hasSignificantOverlap(normalizedQuery, lowerQuestion)) {
            score += 3;
        }

        // Tiêu chí 3: Query đề cập đến category — +1 điểm
        if (faq.getCategory() != null
                && normalizedQuery.contains(faq.getCategory().toLowerCase())) {
            score += 1;
        }

        return score;
    }

    /**
     * Tìm token trong query khớp với keyword cho trước.
     * Tách query thành các từ và kiểm tra từng từ với keyword.
     *
     * @param query   câu hỏi đã chuẩn hóa
     * @param keyword từ khóa cần tìm
     * @return token phù hợp (hoặc rỗng nếu không có)
     */
    private String extractRelevantToken(String query, String keyword) {
        // Tách query thành tokens và kiểm tra từng token
        String[] tokens = query.split("[\\s,?.!;:]+");
        for (String token : tokens) {
            if (!token.isBlank() && (token.contains(keyword) || keyword.contains(token))) {
                return token;
            }
        }
        // Kiểm tra cả query nguyên (keyword có thể là cụm từ nhiều chữ)
        return query;
    }

    /**
     * Kiểm tra xem hai chuỗi có chứa đủ số từ chung hay không.
     * Dùng để bắt trường hợp câu hỏi dài có nhiều từ chung với FAQ.
     *
     * @param a chuỗi thứ nhất
     * @param b chuỗi thứ hai
     * @return true nếu ≥ 2 từ chung (mỗi từ ≥ 3 ký tự)
     */
    private boolean hasSignificantOverlap(String a, String b) {
        String[] wordsA = a.split("\\s+");
        int commonWordCount = 0;

        for (String word : wordsA) {
            if (word.length() >= 3 && b.contains(word)) {
                commonWordCount++;
                if (commonWordCount >= 2) return true; // Đủ 2 từ chung — dừng sớm
            }
        }
        return false;
    }

    // ── Inner class phụ trợ — không expose ra ngoài ───────────────────────────

    /**
     * Value object nội bộ ghép FAQ với điểm relevance.
     * Dùng trong stream pipeline của {@link #searchByQuery(String)}.
     */
    private static class ScoredFaq {
        final FAQ faq;
        final int score;

        ScoredFaq(FAQ faq, int score) {
            this.faq   = faq;
            this.score = score;
        }
    }
}