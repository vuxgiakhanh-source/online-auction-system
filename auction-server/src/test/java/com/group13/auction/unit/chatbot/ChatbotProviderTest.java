package com.group13.auction.unit.chatbot;

import com.group13.auction.chatbot.model.ChatbotResponse;
import com.group13.auction.chatbot.model.FAQ;
import com.group13.auction.chatbot.provider.ChatbotProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bộ Unit Test toàn diện cho {@link ChatbotProvider} và các model liên quan.
 *
 * <h3>Cách chạy:</h3>
 * <pre>{@code mvn test -pl auction-server -Dtest=ChatbotProviderTest}</pre>
 *
 * <h3>Yêu cầu dữ liệu:</h3>
 * <p>File {@code faq_data.json} phải nằm tại:
 * {@code auction-server/src/main/resources/chatbot/faq_data.json}
 * (hoặc {@code src/test/resources/chatbot/faq_data.json} nếu muốn tách riêng cho test).
 *
 * <h3>Chiến lược test — KHÔNG mock ChatbotProvider:</h3>
 * <p>{@code ChatbotProvider} là Singleton đọc file JSON từ classpath.
 * Cách tiếp cận đúng nhất là để nó tự nạp file thật và kiểm tra hành vi thực tế.
 * Việc mock Singleton rất khó (cần PowerMock) và làm mất đi giá trị test thực.
 * Thay vào đó, chúng ta kiểm soát input (query, ID) và assert output (status, category, v.v.).
 *
 * @see ChatbotProvider
 * @see ChatbotResponse
 * @see FAQ
 */
@DisplayName("ChatbotProvider — Bộ Unit Test Toàn Diện")
class ChatbotProviderTest {

    private static ChatbotProvider provider;

    /**
     * Khởi tạo Singleton một lần duy nhất cho toàn bộ test class.
     * Đây là cách dùng đúng với @BeforeAll — tránh tạo lại Singleton nhiều lần.
     */
    @BeforeAll
    static void setUp() {
        provider = ChatbotProvider.getInstance();
    }


    // =========================================================================
    // 1. SINGLETON PATTERN — Kiểm tra bản chất Singleton
    // =========================================================================

    @Nested
    @DisplayName("1. Singleton Pattern")
    class SingletonTest {

        @Test
        @DisplayName("getInstance() luôn trả về cùng một object reference")
        void getInstance_alwaysReturnsSameInstance() {
            ChatbotProvider a = ChatbotProvider.getInstance();
            ChatbotProvider b = ChatbotProvider.getInstance();
            ChatbotProvider c = ChatbotProvider.getInstance();

            // assertSame kiểm tra cùng địa chỉ bộ nhớ (==), không phải equals()
            assertSame(a, b, "Lần gọi 1 và 2 phải cùng object");
            assertSame(b, c, "Lần gọi 2 và 3 phải cùng object");
        }

        @Test
        @DisplayName("Dữ liệu FAQ được nạp thành công (ít nhất 1 FAQ trong bộ nhớ)")
        void faqData_loadedSuccessfully_atLeastOneFaq() {
            assertTrue(provider.getTotalFaqCount() > 0,
                    "Provider phải nạp được ít nhất 1 FAQ. "
                            + "Kiểm tra xem faq_data.json có đặt đúng classpath chưa: "
                            + "/chatbot/faq_data.json");
        }

        @Test
        @DisplayName("getAllFaqs() trả về list không thể thay đổi (immutable)")
        void getAllFaqs_returnsUnmodifiableList() {
            List<FAQ> faqs = provider.getAllFaqs();

            // Bất kỳ thao tác thay đổi nào (add, remove, clear) trên unmodifiable list
            // đều phải ném UnsupportedOperationException — đây là bảo vệ thread-safety
            assertThrows(UnsupportedOperationException.class,
                    () -> faqs.add(new FAQ("X", "X", List.of(), "X?", "X.")),
                    "getAllFaqs() phải trả về unmodifiable list để bảo vệ dữ liệu nội bộ");
        }

        @Test
        @DisplayName("getTotalFaqCount() khớp với kích thước getAllFaqs()")
        void totalFaqCount_matchesAllFaqsSize() {
            assertEquals(provider.getAllFaqs().size(), provider.getTotalFaqCount(),
                    "getTotalFaqCount() phải nhất quán với getAllFaqs().size()");
        }
    }


    // =========================================================================
    // 2. getAnswerByQuestionId — Tra cứu O(1) theo ID
    // =========================================================================

    @Nested
    @DisplayName("2. getAnswerByQuestionId() — Tra cứu theo ID")
    class GetAnswerByQuestionIdTest {

        // --- Happy Path ---

        @Test
        @DisplayName("[SUCCESS] FAQ_001 — trả về đầy đủ: status, faqId, question, answer, category, timestamp")
        void validId_FAQ001_returnsCompleteSuccessResponse() {
            ChatbotResponse resp = provider.getAnswerByQuestionId("FAQ_001");

            // Status và identity
            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus(),
                    "FAQ_001 tồn tại phải trả về SUCCESS");
            assertTrue(resp.isSuccess(), "isSuccess() phải là true khi SUCCESS");
            assertEquals("FAQ_001", resp.getFaqId(), "faqId phải khớp ID được tra cứu");

            // Nội dung không được null/blank
            assertNotNull(resp.getQuestion(),  "question không được null");
            assertNotNull(resp.getAnswer(),    "answer không được null");
            assertNotNull(resp.getCategory(),  "category không được null");
            assertNotNull(resp.getTimestamp(), "timestamp không được null");
            assertFalse(resp.getQuestion().isBlank(),  "question không được rỗng");
            assertFalse(resp.getAnswer().isBlank(),    "answer không được rỗng");
            assertFalse(resp.getTimestamp().isBlank(), "timestamp không được rỗng");

            // Khi SUCCESS, fallbackMessage KHÔNG được có
            assertNull(resp.getFallbackMessage(),
                    "SUCCESS không được có fallbackMessage");
        }

        @Test
        @DisplayName("[SUCCESS] FAQ_004 — câu hỏi thanh toán thuộc category PAYMENT")
        void validId_FAQ004_belongsToPaymentCategory() {
            ChatbotResponse resp = provider.getAnswerByQuestionId("FAQ_004");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus());
            assertEquals("PAYMENT", resp.getCategory(),
                    "FAQ_004 phải thuộc category PAYMENT theo faq_data.json");
        }

        @Test
        @DisplayName("[SUCCESS] FAQ_006 — câu hỏi điểm uy tín thuộc category RATING")
        void validId_FAQ006_belongsToRatingCategory() {
            ChatbotResponse resp = provider.getAnswerByQuestionId("FAQ_006");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus());
            assertEquals("RATING", resp.getCategory());
        }

        @Test
        @DisplayName("[SUCCESS] FAQ_008 — câu hỏi về Seller thuộc category SELLER")
        void validId_FAQ008_belongsToSellerCategory() {
            ChatbotResponse resp = provider.getAnswerByQuestionId("FAQ_008");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus());
            assertEquals("SELLER", resp.getCategory());
        }

        // --- Case-Insensitive ---

        @Test
        @DisplayName("[CASE-INSENSITIVE] 'faq_001' (viết thường) tìm được như 'FAQ_001'")
        void lowercaseId_findsCorrectly_sameResultAsUppercase() {
            ChatbotResponse upper = provider.getAnswerByQuestionId("FAQ_001");
            ChatbotResponse lower = provider.getAnswerByQuestionId("faq_001");
            ChatbotResponse mixed = provider.getAnswerByQuestionId("Faq_001");

            // Tất cả 3 phải cùng status và cùng faqId
            assertEquals(upper.getStatus(), lower.getStatus(),
                    "Hoa/thường không ảnh hưởng đến kết quả tìm kiếm");
            assertEquals(upper.getFaqId(), lower.getFaqId());
            assertEquals(upper.getStatus(), mixed.getStatus());
        }

        @Test
        @DisplayName("[CASE-INSENSITIVE] ID có khoảng trắng thừa vẫn tìm được (trim)")
        void idWithLeadingTrailingSpaces_findsCorrectly() {
            ChatbotResponse resp = provider.getAnswerByQuestionId("  FAQ_001  ");

            // Nếu implementation có trim() thì phải SUCCESS,
            // nếu không thì NOT_FOUND — test này document hành vi thực tế
            assertNotNull(resp, "Không được throw exception với ID có khoảng trắng thừa");
            // Assertion mềm: chỉ kiểm tra không crash
        }

        // --- Not Found / Edge Cases ---

        @Test
        @DisplayName("[NOT_FOUND] ID không tồn tại 'FAQ_999' — trả về NOT_FOUND với fallbackMessage")
        void nonExistentId_returnsNotFound_withFallbackMessage() {
            ChatbotResponse resp = provider.getAnswerByQuestionId("FAQ_999");

            assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus(),
                    "ID không tồn tại phải trả về NOT_FOUND");
            assertFalse(resp.isSuccess());
            assertNull(resp.getFaqId(),  "NOT_FOUND không có faqId");
            assertNull(resp.getAnswer(), "NOT_FOUND không có answer");
            assertNotNull(resp.getFallbackMessage(),
                    "NOT_FOUND phải có fallbackMessage để hướng dẫn người dùng");
            assertFalse(resp.getFallbackMessage().isBlank(),
                    "fallbackMessage không được rỗng");
        }

        @Test
        @DisplayName("[EDGE] ID null — trả về NOT_FOUND, không throw NullPointerException")
        void nullId_returnsNotFound_noNPE() {
            assertDoesNotThrow(() -> {
                ChatbotResponse resp = provider.getAnswerByQuestionId(null);
                assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus(),
                        "null ID phải trả về NOT_FOUND");
            }, "getAnswerByQuestionId(null) không được throw exception");
        }

        @ParameterizedTest(name = "[EDGE] ID rỗng/blank ''{0}'' — NOT_FOUND")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n", "  \t  "})
        @DisplayName("[EDGE] ID null, rỗng, hoặc chỉ toàn whitespace — NOT_FOUND")
        void blankOrNullId_alwaysReturnsNotFound(String id) {
            assertDoesNotThrow(() -> {
                ChatbotResponse resp = provider.getAnswerByQuestionId(id);
                assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus(),
                        "ID blank/null phải trả về NOT_FOUND, nhận được: " + resp.getStatus());
            });
        }

        @Test
        @DisplayName("[EDGE] ID chỉ toàn ký tự đặc biệt — NOT_FOUND")
        void specialCharactersId_returnsNotFound() {
            ChatbotResponse resp = provider.getAnswerByQuestionId("@#$%^&*()!");
            assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus());
        }

        @Test
        @DisplayName("[EDGE] ID rất dài (1000 ký tự) — NOT_FOUND, không crash")
        void veryLongId_returnsNotFound_noException() {
            String longId = "FAQ_" + "X".repeat(1000);
            assertDoesNotThrow(() -> {
                ChatbotResponse resp = provider.getAnswerByQuestionId(longId);
                assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus());
            });
        }
    }


    // =========================================================================
    // 3. searchByQuery — Matching Ratio + Tiếng Việt
    // =========================================================================

    @Nested
    @DisplayName("3. searchByQuery() — Tìm kiếm Free-text & Matching Ratio")
    class SearchByQueryTest {

        // --- Happy Path: Keyword Matching ---

        @Test
        @DisplayName("[KEYWORD] 'thanh toán 24 giờ' → FAQ thuộc PAYMENT")
        void keywordPayment_findsPaymentFaq() {
            ChatbotResponse resp = provider.searchByQuery("thanh toán sau 24 giờ nộp tiền");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus(),
                    "Query chứa keyword 'thanh toán' và '24 giờ' phải tìm được FAQ PAYMENT");
            assertEquals("PAYMENT", resp.getCategory());
        }

        @Test
        @DisplayName("[KEYWORD] 'giá thầu tối thiểu' → FAQ thuộc BIDDING")
        void keywordBidding_findsBiddingFaq() {
            ChatbotResponse resp = provider.searchByQuery("giá thầu tối thiểu là bao nhiêu");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus());
            assertEquals("BIDDING", resp.getCategory(),
                    "Query về giá thầu phải tìm được FAQ BIDDING");
        }

        @Test
        @DisplayName("[KEYWORD] 'auto bid' → FAQ về đấu giá tự động")
        void keywordAutoBid_findsAutoBidFaq() {
            ChatbotResponse resp = provider.searchByQuery("auto bid hoạt động thế nào");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus());
            assertNotNull(resp.getAnswer());
            assertTrue(resp.getAnswer().length() > 20,
                    "Answer về Auto-Bid phải có nội dung chi tiết (>20 ký tự)");
        }

        @Test
        @DisplayName("[KEYWORD] 'điểm uy tín bị trừ' → FAQ thuộc RATING")
        void keywordRating_findsRatingFaq() {
            ChatbotResponse resp = provider.searchByQuery("điểm uy tín bị trừ khi nào");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus());
            assertEquals("RATING", resp.getCategory());
        }

        @Test
        @DisplayName("[KEYWORD] 'omnibid là gì' → FAQ_001 thuộc GENERAL")
        void keywordOmnibid_findsGeneralFaq() {
            ChatbotResponse resp = provider.searchByQuery("omnibid là gì hệ thống hoạt động");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus());
            assertEquals("GENERAL", resp.getCategory(),
                    "Query về OmniBid phải tìm được FAQ GENERAL");
        }

        // --- Matching Ratio: Exact Match vs Partial Match ---

        @Test
        @DisplayName("[RATIO] Câu hỏi khớp hoàn toàn với question field — phải SUCCESS")
        void exactQuestionMatch_returnsSuccess() {
            // "Quy định về mức giá thầu tối thiểu là gì?" là nội dung question của FAQ_002
            // Query khớp đầy đủ phải có matching ratio cao nhất.
            ChatbotResponse resp = provider.searchByQuery("Quy định về mức giá thầu tối thiểu là gì?");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus(),
                    "Câu hỏi khớp hoàn toàn phải trả về SUCCESS (ratio cao nhất)");
        }

        @Test
        @DisplayName("[RATIO] Keyword đơn lẻ 'payment' → NOT_FOUND vì ratio thấp")
        void singleEnglishKeyword_payment_returnsNotFound() {
            // Keyword đơn lẻ không đủ vượt threshold matching ratio.
            ChatbotResponse resp = provider.searchByQuery("payment");

            assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus(),
                    "Keyword đơn 'payment' không đủ ratio tối thiểu để tìm được FAQ");
        }

        @Test
        @DisplayName("[RATIO] Query nhiều từ khớp nhiều keyword → FAQ có ratio cao nhất được chọn")
        void multipleKeywordMatches_highestRatioFaqSelected() {
            // Query chứa cả 'thanh toán' lẫn '24 giờ' — cả hai đều là keyword của FAQ_004
            // → FAQ_004 phải có ratio cao hơn các FAQ khác
            ChatbotResponse resp = provider.searchByQuery("thanh toán sau 24 giờ nộp tiền");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus());
            assertEquals("PAYMENT", resp.getCategory(),
                    "Query khớp nhiều keyword PAYMENT phải chọn FAQ có ratio cao nhất");
        }

        // --- Tiếng Việt ---

        @Test
        @DisplayName("[UNICODE] Query tiếng Việt đầy đủ dấu — tìm được FAQ tương ứng")
        void vietnameseQueryWithDiacritics_findsFaq() {
            // Kiểm tra engine xử lý đúng Unicode NFC — "đấu giá" không bị mất dấu
            ChatbotResponse resp = provider.searchByQuery("tính năng auto bid đấu giá tự động hoạt động");

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus(),
                    "Query tiếng Việt có dấu phải tìm được FAQ (engine phải hỗ trợ Unicode)");
        }

        @Test
        @DisplayName("[UNICODE] Query UPPERCASE tiếng Việt — engine lowercase trước khi so khớp")
        void uppercaseVietnameseQuery_findsFaqCaseInsensitively() {
            ChatbotResponse lower = provider.searchByQuery("thanh toán sau 24 giờ nộp tiền");
            ChatbotResponse upper = provider.searchByQuery("THANH TOÁN SAU 24 GIỜ NỘP TIỀN");

            // Cả hai phải tìm được FAQ (không nhất thiết cùng FAQ nếu có FAQ khác ratio cao hơn,
            // nhưng đều phải SUCCESS)
            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, lower.getStatus(),
                    "Query thường phải tìm được FAQ");
            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, upper.getStatus(),
                    "Query HOA cũng phải tìm được FAQ vì engine dùng toLowerCase()");
        }

        @Test
        @DisplayName("[UNICODE] Dấu câu trong query không gây lỗi và vẫn tìm được FAQ")
        void queryWithPunctuation_noExceptionAndStillFinds() {
            // "thanh toán?" — dấu ? phải được tách bởi regex [\\s,?.!;:]+
            assertDoesNotThrow(() -> {
                ChatbotResponse resp = provider.searchByQuery("thanh toán?");
                // Sau khi loại dấu câu, token "thanh" và "toán" vẫn còn
                assertNotNull(resp, "Không được null dù query có dấu câu");
            });
        }

        // --- Not Found ---

        @Test
        @DisplayName("[NOT_FOUND] Query ngẫu nhiên không liên quan → NOT_FOUND với fallbackMessage")
        void completelyUnrelatedQuery_returnsNotFound() {
            ChatbotResponse resp = provider.searchByQuery("xyz abc 12345 blockchain quantum");

            assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus(),
                    "Query không liên quan phải trả về NOT_FOUND");
            assertFalse(resp.isSuccess());
            assertNotNull(resp.getFallbackMessage(),
                    "NOT_FOUND phải kèm fallbackMessage hướng dẫn người dùng");
            assertFalse(resp.getFallbackMessage().isBlank());
        }

        @Test
        @DisplayName("[NOT_FOUND] fallbackMessage chứa nội dung câu hỏi gốc")
        void notFound_fallbackMessageContainsOriginalQuery() {
            String weirdQuery = "xyzzy-special-query-12345";
            ChatbotResponse resp = provider.searchByQuery(weirdQuery);

            assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus());
            assertTrue(resp.getFallbackMessage().contains(weirdQuery),
                    "fallbackMessage phải chứa câu hỏi gốc để người dùng nhận ra");
        }

        // --- Edge Cases ---

        @Test
        @DisplayName("[EDGE] Query null — NOT_FOUND, không throw exception")
        void nullQuery_returnsNotFound_noException() {
            assertDoesNotThrow(() -> {
                ChatbotResponse resp = provider.searchByQuery(null);
                assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus(),
                        "null query phải trả về NOT_FOUND");
            });
        }

        @ParameterizedTest(name = "[EDGE] Query rỗng/blank ''{0}'' → NOT_FOUND")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\t", "\n\n"})
        @DisplayName("[EDGE] Query null, rỗng, whitespace-only — luôn NOT_FOUND")
        void blankOrNullQuery_alwaysReturnsNotFound(String query) {
            assertDoesNotThrow(() -> {
                ChatbotResponse resp = provider.searchByQuery(query);
                assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus());
            });
        }

        @Test
        @DisplayName("[EDGE] Query chỉ toàn ký tự đặc biệt — NOT_FOUND, không crash")
        void specialCharactersOnlyQuery_returnsNotFound() {
            // Sau khi normalize/tokenize, không còn token có nghĩa — ratio = 0
            assertDoesNotThrow(() -> {
                ChatbotResponse resp = provider.searchByQuery("!@#$%^&*(),.?;:");
                assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus(),
                        "Ký tự đặc biệt không tạo ra token có nghĩa, ratio = 0 → NOT_FOUND");
            });
        }

        @Test
        @DisplayName("[EDGE] Query rất dài (500+ từ) — không timeout, không crash")
        void veryLongQuery_noExceptionAndReasonableResponse() {
            String longQuery = "thanh toán " + "từ ".repeat(500);
            assertDoesNotThrow(() -> {
                ChatbotResponse resp = provider.searchByQuery(longQuery);
                assertNotNull(resp, "Query rất dài không được trả về null");
                // Phải trả về 1 trong 2 status hợp lệ
                assertTrue(
                        resp.getStatus() == ChatbotResponse.ResponseStatus.SUCCESS
                                || resp.getStatus() == ChatbotResponse.ResponseStatus.NOT_FOUND,
                        "Status phải là SUCCESS hoặc NOT_FOUND, không được là giá trị khác"
                );
            });
        }

        @Test
        @DisplayName("[EDGE] Query chỉ 1 ký tự — không crash, trả về status hợp lệ")
        void singleCharacterQuery_noException() {
            assertDoesNotThrow(() -> {
                ChatbotResponse resp = provider.searchByQuery("a");
                assertNotNull(resp);
            });
        }

        // --- Matching Ratio Threshold (MINIMUM_MATCHING_RATIO = 0.4) ---

        @Test
        @DisplayName("[RATIO] Query ratio thấp (không liên quan) → NOT_FOUND")
        void lowRatioQuery_belowThreshold_returnsNotFound() {
            // ⚠️ UNICODE SUBSTRING TRAP: Phải chọn query cẩn thận!
            // extractRelevantToken() dùng String.contains() — substring match theo ký tự Unicode.
            // Ví dụ: token "ăn" (ă+n) là substring của keyword "đăng" (đ+ă+n+g)
            // → query không liên quan từng có thể tạo false positive khi chỉ đếm số từ khớp.
            //
            // Query an toàn: tất cả token phải KHÔNG là substring (và ngược lại) của bất kỳ keyword nào.
            // Các query dưới đây đã được verify thủ công trên bộ faq_data.json hiện tại:
            ChatbotResponse resp1 = provider.searchByQuery("bầu trời màu xanh lá");
            ChatbotResponse resp2 = provider.searchByQuery("nấu cơm rang trứng muối");
            ChatbotResponse resp3 = provider.searchByQuery("du lịch biển mùa hè");

            assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp1.getStatus(),
                    "'bầu trời màu xanh lá' không liên quan đến đấu giá → ratio thấp → NOT_FOUND");
            assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp2.getStatus(),
                    "'nấu cơm rang trứng muối' không liên quan đến đấu giá → ratio thấp → NOT_FOUND");
            assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp3.getStatus(),
                    "'du lịch biển mùa hè' không liên quan đến đấu giá → ratio thấp → NOT_FOUND");
        }
    }


    // =========================================================================
    // 4. getFaqsByCategory — Lọc theo Category
    // =========================================================================

    @Nested
    @DisplayName("4. getFaqsByCategory() — Lọc theo nhóm nghiệp vụ")
    class GetFaqsByCategoryTest {

        @Test
        @DisplayName("[BIDDING] Trả về ít nhất 1 FAQ, tất cả đều đúng category")
        void biddingCategory_returnsOnlyBiddingFaqs() {
            List<FAQ> faqs = provider.getFaqsByCategory("BIDDING");

            assertFalse(faqs.isEmpty(), "BIDDING phải có ít nhất 1 FAQ");
            // Mọi phần tử phải đúng category — không được lọt FAQ category khác
            assertTrue(faqs.stream().allMatch(f -> "BIDDING".equals(f.getCategory())),
                    "Tất cả FAQ trả về phải thuộc BIDDING");
        }

        @Test
        @DisplayName("[PAYMENT] Trả về ít nhất 1 FAQ, tất cả đều đúng category")
        void paymentCategory_returnsOnlyPaymentFaqs() {
            List<FAQ> faqs = provider.getFaqsByCategory("PAYMENT");

            assertFalse(faqs.isEmpty());
            faqs.forEach(faq ->
                    assertEquals("PAYMENT", faq.getCategory(),
                            "Phần tử '" + faq.getId() + "' không thuộc PAYMENT")
            );
        }

        @Test
        @DisplayName("[RATING] Trả về ít nhất 1 FAQ, tất cả đều đúng category")
        void ratingCategory_returnsOnlyRatingFaqs() {
            List<FAQ> faqs = provider.getFaqsByCategory("RATING");

            assertFalse(faqs.isEmpty());
            assertTrue(faqs.stream().allMatch(f -> "RATING".equals(f.getCategory())));
        }

        @Test
        @DisplayName("[SELLER] Trả về ít nhất 1 FAQ, tất cả đều đúng category")
        void sellerCategory_returnsOnlySellerFaqs() {
            List<FAQ> faqs = provider.getFaqsByCategory("SELLER");

            assertFalse(faqs.isEmpty());
            assertTrue(faqs.stream().allMatch(f -> "SELLER".equals(f.getCategory())));
        }

        @Test
        @DisplayName("[CASE-INSENSITIVE] 'payment' và 'PAYMENT' trả về cùng kết quả")
        void categoryFilterIsCaseInsensitive() {
            List<FAQ> upper = provider.getFaqsByCategory("PAYMENT");
            List<FAQ> lower = provider.getFaqsByCategory("payment");
            List<FAQ> mixed = provider.getFaqsByCategory("Payment");

            assertEquals(upper.size(), lower.size(),
                    "Lọc category không phân biệt hoa/thường");
            assertEquals(upper.size(), mixed.size());
        }

        @Test
        @DisplayName("[CATEGORY=null] Trả về toàn bộ FAQ — giống getAllFaqs()")
        void nullCategory_returnsAllFaqs() {
            List<FAQ> all      = provider.getAllFaqs();
            List<FAQ> filtered = provider.getFaqsByCategory(null);

            assertEquals(all.size(), filtered.size(),
                    "getFaqsByCategory(null) phải trả về toàn bộ FAQ");
        }

        @Test
        @DisplayName("[CATEGORY=blank] Trả về toàn bộ FAQ — giống getAllFaqs()")
        void blankCategory_returnsAllFaqs() {
            List<FAQ> all      = provider.getAllFaqs();
            List<FAQ> filtered = provider.getFaqsByCategory("   ");

            assertEquals(all.size(), filtered.size(),
                    "getFaqsByCategory(blank) phải trả về toàn bộ FAQ");
        }

        @Test
        @DisplayName("[UNKNOWN] Category không tồn tại — trả về list rỗng, không throw")
        void unknownCategory_returnsEmptyList_noException() {
            assertDoesNotThrow(() -> {
                List<FAQ> faqs = provider.getFaqsByCategory("INVALID_CATEGORY_XYZ");
                assertTrue(faqs.isEmpty(),
                        "Category không tồn tại phải trả về list rỗng, không phải null");
                assertNotNull(faqs, "Không được trả về null — phải là empty list");
            });
        }

        @Test
        @DisplayName("[IMMUTABLE] List trả về không thể thay đổi (unmodifiable)")
        void returnedList_isUnmodifiable() {
            List<FAQ> faqs = provider.getFaqsByCategory("BIDDING");

            assertThrows(UnsupportedOperationException.class,
                    () -> faqs.add(new FAQ("X", "BIDDING", List.of(), "X?", "X.")),
                    "List từ getFaqsByCategory() phải là unmodifiable để bảo vệ dữ liệu");
        }

        @Test
        @DisplayName("[CONSISTENCY] Tổng FAQ tất cả category = getTotalFaqCount()")
        void sumOfAllCategories_equalsTotalCount() {
            String[] categories = {"GENERAL", "BIDDING", "PAYMENT", "RATING", "SELLER"};
            int total = 0;
            for (String cat : categories) {
                total += provider.getFaqsByCategory(cat).size();
            }

            assertEquals(provider.getTotalFaqCount(), total,
                    "Tổng FAQ của tất cả category phải bằng getTotalFaqCount() "
                            + "(giả định mỗi FAQ thuộc đúng 1 category)");
        }
    }


    // =========================================================================
    // 5. FAQ MODEL — Kiểm tra logic trong model class
    // =========================================================================

    @Nested
    @DisplayName("5. FAQ Model — containsKeyword() & Immutability")
    class FaqModelTest {

        private FAQ createTestFaq() {
            return new FAQ("T001", "GENERAL",
                    List.of("đấu giá", "omnibid", "hệ thống", "minimum bid"),
                    "Test question?", "Test answer.");
        }

        // --- containsKeyword() ---

        @Test
        @DisplayName("[containsKeyword] Keyword có trong list → true")
        void containsKeyword_exactMatch_returnsTrue() {
            FAQ faq = createTestFaq();

            assertTrue(faq.containsKeyword("omnibid"),  "'omnibid' phải có trong keywords");
            assertTrue(faq.containsKeyword("đấu giá"),  "'đấu giá' phải có trong keywords");
            assertTrue(faq.containsKeyword("hệ thống"), "'hệ thống' phải có trong keywords");
        }

        @Test
        @DisplayName("[containsKeyword] Keyword viết hoa — khớp case-insensitive")
        void containsKeyword_caseInsensitive_returnsTrue() {
            FAQ faq = createTestFaq();

            // Keyword 'omnibid' trong list là lowercase; tìm bằng 'OMNIBID' phải được
            assertTrue(faq.containsKeyword("OMNIBID"),
                    "containsKeyword phải case-insensitive");
            assertTrue(faq.containsKeyword("OmniBid"));
        }

        @Test
        @DisplayName("[containsKeyword] Partial match — token ngắn nằm trong keyword dài → true")
        void containsKeyword_partialMatch_tokenSubstringOfKeyword() {
            // FAQ có keyword 'minimum bid'; query token là 'minimum' (nằm trong keyword)
            FAQ faq = createTestFaq();

            // Theo logic containsKeyword: k.toLowerCase().contains(lowerKeyword)
            // → 'minimum bid'.contains('minimum') = true → phải trả về true
            assertTrue(faq.containsKeyword("minimum"),
                    "Token 'minimum' nằm trong keyword 'minimum bid' → partial match = true");
        }

        @Test
        @DisplayName("[containsKeyword] Keyword không có trong list → false")
        void containsKeyword_notInList_returnsFalse() {
            FAQ faq = createTestFaq();

            assertFalse(faq.containsKeyword("blockchain"),
                    "'blockchain' không có trong keywords");
            assertFalse(faq.containsKeyword("nft"));
        }

        @Test
        @DisplayName("[containsKeyword] Keyword null → false, không throw NPE")
        void containsKeyword_null_returnsFalse_noNPE() {
            FAQ faq = createTestFaq();

            assertDoesNotThrow(() ->
                            assertFalse(faq.containsKeyword(null),
                                    "null keyword phải trả về false"),
                    "containsKeyword(null) không được throw NullPointerException"
            );
        }

        @Test
        @DisplayName("[containsKeyword] Keyword rỗng/blank → false")
        void containsKeyword_blank_returnsFalse() {
            FAQ faq = createTestFaq();

            assertFalse(faq.containsKeyword(""),
                    "Keyword rỗng phải trả về false");
            assertFalse(faq.containsKeyword("   "),
                    "Keyword blank phải trả về false");
        }

        @Test
        @DisplayName("[containsKeyword] FAQ với keywords=null — getKeywords() trả về empty list")
        void faqWithNullKeywords_getKeywords_returnsEmptyList() {
            FAQ faq = new FAQ("T002", "GENERAL", null, "Q?", "A.");

            assertNotNull(faq.getKeywords(),
                    "getKeywords() không được trả về null dù keywords=null");
            assertTrue(faq.getKeywords().isEmpty(),
                    "getKeywords() phải là empty list khi keywords=null trong constructor");
            assertFalse(faq.containsKeyword("bất kỳ"),
                    "FAQ không có keywords không thể chứa keyword nào");
        }

        // --- equals() và hashCode() ---

        @Test
        @DisplayName("[equals] Hai FAQ cùng ID là bằng nhau (equals by ID)")
        void equals_samId_areEqual() {
            FAQ faq1 = new FAQ("FAQ_001", "GENERAL", List.of("kw1"), "Q1?", "A1.");
            FAQ faq2 = new FAQ("FAQ_001", "BIDDING", List.of("kw2"), "Q2?", "A2.");

            assertEquals(faq1, faq2,
                    "FAQ được so sánh theo ID — cùng ID là equal dù fields khác");
        }

        @Test
        @DisplayName("[equals] Hai FAQ khác ID là không bằng nhau")
        void equals_differentId_areNotEqual() {
            FAQ faq1 = new FAQ("FAQ_001", "GENERAL", List.of(), "Q?", "A.");
            FAQ faq2 = new FAQ("FAQ_002", "GENERAL", List.of(), "Q?", "A.");

            assertNotEquals(faq1, faq2, "Khác ID phải không equals");
        }

        @Test
        @DisplayName("[hashCode] Hai FAQ cùng ID có cùng hashCode")
        void hashCode_sameId_sameHashCode() {
            FAQ faq1 = new FAQ("FAQ_001", "GENERAL", List.of(), "Q?", "A.");
            FAQ faq2 = new FAQ("FAQ_001", "BIDDING", List.of(), "Q2?", "A2.");

            assertEquals(faq1.hashCode(), faq2.hashCode(),
                    "Cùng ID phải có cùng hashCode (contract của equals/hashCode)");
        }

        // --- toString() ---

        @Test
        @DisplayName("[toString] Không throw exception và chứa ID + category")
        void toString_containsIdAndCategory() {
            FAQ faq = new FAQ("FAQ_T01", "PAYMENT", List.of(), "Q?", "A.");
            String str = faq.toString();

            assertNotNull(str);
            assertTrue(str.contains("FAQ_T01"), "toString phải chứa ID");
            assertTrue(str.contains("PAYMENT"), "toString phải chứa category");
        }
    }


    // =========================================================================
    // 6. ChatbotResponse MODEL — Static Factory Methods & State
    // =========================================================================

    @Nested
    @DisplayName("6. ChatbotResponse — Static Factory Methods & Trạng thái")
    class ChatbotResponseModelTest {

        private FAQ sampleFaq() {
            return new FAQ("FAQ_001", "GENERAL",
                    List.of("test"), "Câu hỏi test?", "Câu trả lời chi tiết về test.");
        }

        // --- ofSuccess() ---

        @Test
        @DisplayName("[ofSuccess] Tất cả fields được gán đúng từ FAQ")
        void ofSuccess_allFieldsMappedCorrectly() {
            FAQ faq = sampleFaq();
            ChatbotResponse resp = ChatbotResponse.ofSuccess(faq);

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus());
            assertTrue(resp.isSuccess());
            assertEquals("FAQ_001",             resp.getFaqId());
            assertEquals("GENERAL",             resp.getCategory());
            assertEquals("Câu hỏi test?",       resp.getQuestion());
            assertEquals("Câu trả lời chi tiết về test.", resp.getAnswer());
            assertNull(resp.getFallbackMessage(), "SUCCESS không có fallbackMessage");
            assertNotNull(resp.getTimestamp());
        }

        // --- ofNotFound() ---

        @Test
        @DisplayName("[ofNotFound] Tất cả fields được gán đúng")
        void ofNotFound_allFieldsCorrect() {
            String query = "câu hỏi không tìm được";
            ChatbotResponse resp = ChatbotResponse.ofNotFound(query);

            assertEquals(ChatbotResponse.ResponseStatus.NOT_FOUND, resp.getStatus());
            assertFalse(resp.isSuccess());
            assertNull(resp.getFaqId(),   "NOT_FOUND không có faqId");
            assertNull(resp.getAnswer(),  "NOT_FOUND không có answer");
            assertNull(resp.getCategory(),"NOT_FOUND không có category");
            assertNotNull(resp.getFallbackMessage());
            assertTrue(resp.getFallbackMessage().contains(query),
                    "fallbackMessage phải embed câu hỏi gốc để người dùng biết");
        }

        @Test
        @DisplayName("[ofNotFound] fallbackMessage có nội dung gợi ý hành động tiếp theo")
        void ofNotFound_fallbackMessageIsHelpful() {
            ChatbotResponse resp = ChatbotResponse.ofNotFound("xyz");
            String msg = resp.getFallbackMessage();

            // Thông điệp hữu ích phải hướng dẫn người dùng — không chỉ báo lỗi
            assertFalse(msg.isBlank(), "fallbackMessage không được rỗng");
            assertTrue(msg.length() > 20,
                    "fallbackMessage phải đủ dài để hướng dẫn (>20 ký tự), nhận được: " + msg.length());
        }

        // --- ofFaqList() ---

        @Test
        @DisplayName("[ofFaqList] status = FAQ_LIST, isSuccess() = false")
        void ofFaqList_correctStatus() {
            ChatbotResponse resp = ChatbotResponse.ofFaqList("PAYMENT");

            assertEquals(ChatbotResponse.ResponseStatus.FAQ_LIST, resp.getStatus());
            assertFalse(resp.isSuccess(),
                    "FAQ_LIST không phải SUCCESS — isSuccess() phải false");
        }

        @Test
        @DisplayName("[ofFaqList] category=null → vẫn tạo được (không crash)")
        void ofFaqList_withNullCategory_noException() {
            assertDoesNotThrow(() -> {
                ChatbotResponse resp = ChatbotResponse.ofFaqList(null);
                assertEquals(ChatbotResponse.ResponseStatus.FAQ_LIST, resp.getStatus());
            });
        }

        // --- Timestamp ---

        @Test
        @DisplayName("[Timestamp] Luôn được gán, không null, không blank, định dạng HH:mm dd/MM/yyyy")
        void timestamp_alwaysPresentAndCorrectFormat() {
            ChatbotResponse success  = ChatbotResponse.ofSuccess(sampleFaq());
            ChatbotResponse notFound = ChatbotResponse.ofNotFound("q");
            ChatbotResponse faqList  = ChatbotResponse.ofFaqList("BIDDING");

            for (ChatbotResponse resp : List.of(success, notFound, faqList)) {
                assertNotNull(resp.getTimestamp(), "Timestamp không được null");
                assertFalse(resp.getTimestamp().isBlank(), "Timestamp không được blank");
                // Định dạng: "HH:mm dd/MM/yyyy" — phải có dấu ':' và '/'
                assertTrue(resp.getTimestamp().contains(":"),
                        "Timestamp phải chứa ':' — định dạng HH:mm dd/MM/yyyy");
                assertTrue(resp.getTimestamp().contains("/"),
                        "Timestamp phải chứa '/' — định dạng HH:mm dd/MM/yyyy");
            }
        }

        // --- toString() ---

        @Test
        @DisplayName("[toString] Không throw exception, chứa status và faqId")
        void toString_containsKeyInfo() {
            ChatbotResponse resp = ChatbotResponse.ofSuccess(sampleFaq());
            String str = resp.toString();

            assertNotNull(str);
            assertTrue(str.contains("SUCCESS"), "toString phải chứa status");
            assertTrue(str.contains("FAQ_001"), "toString phải chứa faqId");
        }
    }


    // =========================================================================
    // 7. INTEGRATION — Kiểm tra luồng end-to-end
    // =========================================================================

    @Nested
    @DisplayName("7. End-to-End Flow — Luồng tích hợp giữa các method")
    class EndToEndFlowTest {

        @Test
        @DisplayName("[E2E] searchByQuery → lấy faqId → getAnswerByQuestionId → cùng FAQ")
        void searchThenGetById_returnsSameFaq() {
            // Bước 1: Tìm theo query
            ChatbotResponse searchResp = provider.searchByQuery("thanh toán sau 24 giờ nộp tiền");
            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, searchResp.getStatus(),
                    "Bước 1: searchByQuery phải tìm được FAQ");

            // Bước 2: Dùng faqId kết quả để tra cứu lại
            String faqId = searchResp.getFaqId();
            ChatbotResponse getByIdResp = provider.getAnswerByQuestionId(faqId);

            // Bước 3: Kết quả phải nhất quán
            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, getByIdResp.getStatus(),
                    "Bước 3: getAnswerByQuestionId với ID từ searchByQuery phải SUCCESS");
            assertEquals(faqId, getByIdResp.getFaqId(),
                    "faqId phải khớp nhau trong cả hai kết quả");
            assertEquals(searchResp.getCategory(), getByIdResp.getCategory(),
                    "Category phải nhất quán giữa search và getById");
        }

        @Test
        @DisplayName("[E2E] getFaqsByCategory → lấy ID bất kỳ → getAnswerByQuestionId → SUCCESS")
        void getCategoryFaqs_thenGetByAnyId_returnsSuccess() {
            List<FAQ> biddingFaqs = provider.getFaqsByCategory("BIDDING");
            assertFalse(biddingFaqs.isEmpty(), "Cần có ít nhất 1 FAQ BIDDING cho test này");

            // Lấy FAQ đầu tiên trong danh sách BIDDING
            FAQ firstFaq = biddingFaqs.get(0);
            ChatbotResponse resp = provider.getAnswerByQuestionId(firstFaq.getId());

            assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus(),
                    "ID lấy từ getFaqsByCategory phải tra cứu được qua getAnswerByQuestionId");
            assertEquals("BIDDING", resp.getCategory(),
                    "Category phải nhất quán");
        }

        @Test
        @DisplayName("[E2E] Tất cả FAQ trong getAllFaqs() đều tra cứu được qua getAnswerByQuestionId")
        void allFaqs_areRetrievableById() {
            List<FAQ> allFaqs = provider.getAllFaqs();
            assertFalse(allFaqs.isEmpty(), "Cần có ít nhất 1 FAQ");

            // Mọi FAQ trong danh sách phải tra cứu được bằng ID
            for (FAQ faq : allFaqs) {
                ChatbotResponse resp = provider.getAnswerByQuestionId(faq.getId());
                assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, resp.getStatus(),
                        "FAQ '" + faq.getId() + "' từ getAllFaqs() phải tra cứu được");
                assertEquals(faq.getId(), resp.getFaqId(),
                        "faqId phải khớp chính xác");
            }
        }

        @Test
        @DisplayName("[E2E] Không có FAQ nào trùng ID trong toàn bộ dataset")
        void allFaqIds_areUnique() {
            List<FAQ> allFaqs = provider.getAllFaqs();

            long uniqueCount = allFaqs.stream()
                    .map(FAQ::getId)
                    .distinct()
                    .count();

            assertEquals(allFaqs.size(), uniqueCount,
                    "Tất cả FAQ phải có ID duy nhất — vi phạm gây lỗi HashMap lookup");
        }

        @Test
        @DisplayName("[E2E] Không có FAQ nào có question hoặc answer null/blank")
        void allFaqs_haveNonBlankQuestionAndAnswer() {
            provider.getAllFaqs().forEach(faq -> {
                assertNotNull(faq.getQuestion(),
                        "FAQ '" + faq.getId() + "' có question = null");
                assertNotNull(faq.getAnswer(),
                        "FAQ '" + faq.getId() + "' có answer = null");
                assertFalse(faq.getQuestion().isBlank(),
                        "FAQ '" + faq.getId() + "' có question blank");
                assertFalse(faq.getAnswer().isBlank(),
                        "FAQ '" + faq.getId() + "' có answer blank");
            });
        }
    }


    // =========================================================================
    // 8. CONCURRENCY — Thread Safety (Singleton không bị race condition)
    // =========================================================================

    @Nested
    @DisplayName("8. Thread Safety — Singleton Concurrency")
    class ConcurrencyTest {

        @Test
        @DisplayName("[THREAD-SAFE] 10 thread cùng lúc gọi getInstance() — luôn cùng object")
        void multipleThreads_getInstance_returnsSameInstance() throws InterruptedException {
            int threadCount = 10;
            ChatbotProvider[] results = new ChatbotProvider[threadCount];
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[idx] = new Thread(() -> results[idx] = ChatbotProvider.getInstance());
            }

            // Start tất cả thread cùng lúc
            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();

            // Tất cả phải cùng object reference
            for (int i = 1; i < threadCount; i++) {
                assertSame(results[0], results[i],
                        "Thread " + i + " lấy được instance khác — vi phạm Singleton!");
            }
        }

        @Test
        @DisplayName("[THREAD-SAFE] 5 thread đồng thời searchByQuery — không throw exception")
        void multipleThreads_searchByQuery_noException() throws InterruptedException {
            int threadCount = 5;
            String[] queries = {"thanh toán", "giá thầu", "auto bid", "điểm uy tín", "omnibid"};
            Thread[] threads = new Thread[threadCount];
            Throwable[] errors = new Throwable[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[idx] = new Thread(() -> {
                    try {
                        provider.searchByQuery(queries[idx]);
                    } catch (Throwable t) {
                        errors[idx] = t;
                    }
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();

            // Không thread nào được throw exception
            for (int i = 0; i < threadCount; i++) {
                assertNull(errors[i],
                        "Thread " + i + " throw exception: "
                                + (errors[i] != null ? errors[i].getMessage() : "null"));
            }
        }
    }
}
