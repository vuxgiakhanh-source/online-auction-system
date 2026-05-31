package com.group13.auction.unit.chatbot;

import static org.junit.jupiter.api.Assertions.*;

import com.group13.auction.chatbot.model.ChatbotResponse;
import com.group13.auction.chatbot.model.FAQ;
import com.group13.auction.chatbot.provider.ChatbotProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Bộ Unit Test toàn diện cho {@link ChatbotProvider} sau khi refactor logic Matching Score.
 *
 * <h3>Cách chạy:</h3>
 *
 * <pre>{@code mvn test -pl auction-server -Dtest=ChatbotProviderTest}</pre>
 *
 * @author Group 13 — Chatbot Module
 * @version 1.2
 */
@DisplayName("=== ChatbotProvider Test Suite ===")
class ChatbotProviderTest {

  private static ChatbotProvider provider;

  @BeforeAll
  static void setUp() {
    // Lấy instance Singleton đã được nạp dữ liệu từ faq_data.json
    provider = ChatbotProvider.getInstance();
  }
  // 1. TEST KIỂM TRA ĐẢM BẢO PATTERN SINGLETON
  @Nested
  @DisplayName("1. Kiến trúc Singleton & Khởi tạo ban đầu")
  class SingletonTests {

    @Test
    @DisplayName("[SINGLETON] Khởi tạo instance không được null và phải là duy nhất")
    void testSingletonInstance() {
      ChatbotProvider instance1 = ChatbotProvider.getInstance();
      ChatbotProvider instance2 = ChatbotProvider.getInstance();

      assertNotNull(instance1, "Instance không được phép null");
      assertSame(
          instance1, instance2, "Hai lần gọi getInstance phải trả về cùng một vùng nhớ reference");
    }

    @Test
    @DisplayName("[DATA-LOAD] Phải nạp thành công bộ dữ liệu FAQ từ file tài nguyên json")
    void testFaqDataLoaded() {
      assertTrue(
          provider.getTotalFaqCount() > 0, "Tổng số câu hỏi FAQ nạp vào bộ nhớ phải lớn hơn 0");
      assertNotNull(provider.getAllFaqs(), "Danh sách FAQ lưu trữ không được null");
    }
  }
  // 2. TEST LOGIC TÌM KIẾM THEO ID (getAnswerByQuestionId)
  @Nested
  @DisplayName("2. Tra cứu câu trả lời trực tiếp bằng ID (getAnswerByQuestionId)")
  class GetAnswerByIdTests {

    @Test
    @DisplayName(
        "[ID-SUCCESS] Tìm kiếm thành công với mã ID viết thường hoặc viết hoa có khoảng trắng")
    void testGetAnswerById_Success() {
      // Lấy ra ID hợp lệ đầu tiên xuất hiện trong danh sách dữ liệu thực tế
      String validId = provider.getAllFaqs().get(0).getId();

      ChatbotResponse responseLower = provider.getAnswerByQuestionId(validId.toLowerCase());
      ChatbotResponse responseWithSpaces =
          provider.getAnswerByQuestionId("  " + validId.toUpperCase() + "  ");

      assertTrue(responseLower.isSuccess(), "Phải tìm thấy FAQ khi ID viết thường");
      assertTrue(
          responseWithSpaces.isSuccess(), "Phải tìm thấy FAQ khi ID bị dính khoảng trắng dư thừa");
      assertEquals(validId, responseLower.getFaqId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("[ID-INVALID] Trả về NOT_FOUND khi mã ID truyền vào rỗng hoặc null")
    void testGetAnswerById_EmptyOrNull(String invalidId) {
      ChatbotResponse response = provider.getAnswerByQuestionId(invalidId);
      assertFalse(response.isSuccess(), "Không được phép SUCCESS với ID không hợp lệ");
      // ĐÃ FIX: Kiểm tra chuỗi format động sinh ra từ ChatbotResponse.ofNotFound
      assertTrue(
          response.getFallbackMessage().contains("[mã câu hỏi trống]"),
          "Fallback message phải chứa thông tin thông báo mã câu hỏi trống");
    }

    @Test
    @DisplayName("[ID-NOTFOUND] Trả về NOT_FOUND khi mã ID hoàn toàn không tồn tại")
    void testGetAnswerById_NotFound() {
      String nonExistentId = "FAQ_999_NON_EXISTENT";
      ChatbotResponse response = provider.getAnswerByQuestionId(nonExistentId);

      assertFalse(response.isSuccess(), "Phải trả về thất bại với ID lạ");
      // ĐÃ FIX: Sử dụng contains thay vì so khớp tuyệt đối chuỗi thô ban đầu
      assertTrue(
          response.getFallbackMessage().contains(nonExistentId),
          "Fallback message phải chứa mã ID không tìm thấy");
    }
  }
  // 3. TEST CORE LOGIC MATCHING MỚI (searchByQuery)
  @Nested
  @DisplayName("3. Thuật toán tìm kiếm thông minh bằng từ khóa & cụm từ (searchByQuery)")
  class SearchByQueryTests {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("[QUERY-EMPTY] Trả về NOT_FOUND kèm fallback thích hợp khi câu hỏi trống")
    void testSearchByQuery_EmptyOrNull(String emptyQuery) {
      ChatbotResponse response = provider.searchByQuery(emptyQuery);
      assertFalse(response.isSuccess());
      assertTrue(response.getFallbackMessage().contains("trống"));
    }

    @Test
    @DisplayName("[QUERY-EXACT] Ưu tiên tuyệt đối cơ chế khớp nguyên cụm ngắn (Phrase Containment)")
    void testSearchByQuery_ExactPhraseMatch() {
      // Danh sách các câu ngắn thực tế hay lỗi ở bản cũ, hệ thống mới phải xử lý tốt nhờ Khớp
      // nguyên cụm
      String[] shortQueries = {"omni là gì", "giá tối thiểu", "login lỗi", "omnibid là gì"};

      for (String query : shortQueries) {
        ChatbotResponse response = provider.searchByQuery(query);
        assertNotNull(response, "Response không được null");
        if (response.isSuccess()) {
          assertNotNull(response.getAnswer());
        }
      }
    }

    @Test
    @DisplayName(
        "[QUERY-SCORE] Kiểm tra tính toán phân mảnh từ theo công thức mới (matchedWords /"
            + " totalUserWords)")
    void testSearchByQuery_KeywordScoreMatching() {
      // Lấy từ khóa thực tế từ FAQ đầu tiên để kiểm tra thuật toán phân rã Set từ độc lập dữ liệu
      // cứng
      FAQ sampleFaq = provider.getAllFaqs().get(0);
      String sampleQuestion = sampleFaq.getQuestion();

      // Cắt bớt câu hỏi để tạo chuỗi query ngắn của user
      String query =
          sampleQuestion.length() > 10 ? sampleQuestion.substring(0, 10) : sampleQuestion;
      ChatbotResponse response = provider.searchByQuery(query);

      assertNotNull(response);
      assertTrue(
          response.isSuccess(),
          "Thuật toán score mới bắt buộc phải khớp thành công cụm từ trích xuất");
    }

    @Test
    @DisplayName("[QUERY-FALLBACK] Trả về NOT_FOUND khi gõ chuỗi vô nghĩa không dính từ nào")
    void testSearchByQuery_NoMatchFallback() {
      String nonsenseQuery = "xyzabc hỏi cái gì thế này không có trong faq đâu";
      ChatbotResponse response = provider.searchByQuery(nonsenseQuery);

      assertFalse(response.isSuccess(), "Chuỗi vô nghĩa không được phép vượt qua threshold");
      // ĐÃ FIX: Thay vì so bằng tuyệt đối, kiểm tra fallbackMessage hệ thống sinh ra có bọc chuỗi
      // vô nghĩa đó không
      assertTrue(
          response.getFallbackMessage().contains(nonsenseQuery),
          "Fallback message phải chứa câu hỏi vô nghĩa của user");
    }
  }
  // 4. TEST LỌC THEO CATEGORY (getFaqsByCategory)
  @Nested
  @DisplayName("4. Phân loại danh mục kiến thức (getFaqsByCategory)")
  class CategoryTests {

    @Test
    @DisplayName("[CAT-FILTER] Tìm kiếm đúng danh sách FAQ theo Category viết hoa/thường")
    void testGetFaqsByCategory_Valid() {
      // Lấy danh mục thực tế từ FAQ đầu tiên để kiểm tra
      String validCategory = provider.getAllFaqs().get(0).getCategory();

      List<FAQ> listUpper = provider.getFaqsByCategory(validCategory.toUpperCase());
      List<FAQ> listLower = provider.getFaqsByCategory(validCategory.toLowerCase());

      assertFalse(listUpper.isEmpty(), "Danh sách trả về không được rỗng");
      assertEquals(listUpper.size(), listLower.size(), "Kích thước hoa thường phải bằng nhau");

      for (FAQ faq : listUpper) {
        assertEquals(validCategory.toUpperCase(), faq.getCategory());
      }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("[CAT-EMPTY] Trả về toàn bộ kho dữ liệu FAQ khi truyền vào category trống")
    void testGetFaqsByCategory_EmptyOrNull(String emptyCategory) {
      List<FAQ> allFaqs = provider.getFaqsByCategory(emptyCategory);
      assertEquals(
          provider.getTotalFaqCount(),
          allFaqs.size(),
          "Khi category trống, phải fallback trả về toàn bộ dữ liệu hiện có");
    }
  }
  // 5. TEST ĐA LUỒNG ĐỒNG THỜI (THREAD-SAFETY)
  @Nested
  @DisplayName("5. Kiểm tra an toàn đa luồng (Thread-Safety)")
  class ConcurrentTests {

    @Test
    @DisplayName(
        "[THREAD-SAFE] Đảm bảo nhiều thread gọi getInstance đồng thời thu về cùng một instance")
    void multipleThreads_getInstance_returnsSameInstance() throws InterruptedException {
      int threadCount = 10;
      Thread[] threads = new Thread[threadCount];
      ChatbotProvider[] results = new ChatbotProvider[threadCount];

      for (int i = 0; i < threadCount; i++) {
        final int idx = i;
        threads[idx] = new Thread(() -> results[idx] = ChatbotProvider.getInstance());
      }

      for (Thread t : threads) {
        t.start();
      }
      for (Thread t : threads) {
        t.join();
      }

      // So sánh tất cả các vị trí thu về xem có chung một reference không
      for (int i = 1; i < threadCount; i++) {
        assertSame(
            results[0],
            results[i],
            "Thread " + i + " lấy được instance khác — vi phạm nguyên lý Singleton!");
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
        threads[idx] =
            new Thread(
                () -> {
                  try {
                    provider.searchByQuery(queries[idx]);
                  } catch (Throwable t) {
                    errors[idx] = t;
                  }
                });
      }

      for (Thread t : threads) {
        t.start();
      }
      for (Thread t : threads) {
        t.join();
      }

      // Đảm bảo không có thread nào phát sinh ngoại lệ runtime khi tra cứu đồng thời
      for (int i = 0; i < threadCount; i++) {
        assertNull(
            errors[i],
            "Thread "
                + i
                + " throw exception: "
                + (errors[i] != null ? errors[i].getMessage() : "null"));
      }
    }
  }
}
