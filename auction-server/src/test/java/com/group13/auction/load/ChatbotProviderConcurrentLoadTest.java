package com.group13.auction.load;

import com.group13.auction.chatbot.model.ChatbotResponse;
import com.group13.auction.chatbot.provider.ChatbotProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Đồng thời nhiều luồng gọi {@link ChatbotProvider} — không cần Docker.
 * Bảo vệ hồi quy sau khi FAQ được nạp bất biến từ JSON (Gson).
 */
@DisplayName("ChatbotProvider — concurrency / load (unit)")
class ChatbotProviderConcurrentLoadTest {

    private static ChatbotProvider provider;

    @BeforeAll
    static void init() {
        provider = ChatbotProvider.getInstance();
    }

    @Test
    @DisplayName("Nhiều luồng song song: getAnswerByQuestionId + searchByQuery — không lỗi, kết quả hợp lệ")
    void parallelQueries_noFailures() throws Exception {
        assertTrue(provider.getTotalFaqCount() > 0, "Cần faq_data.json trên classpath");

        int threads = 32;
        int opsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger failures = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        String knownId = provider.getAllFaqs().get(0).getId();

        for (int t = 0; t < threads; t++) {
            final int seed = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    try {
                        ChatbotResponse byId = provider.getAnswerByQuestionId(knownId);
                        assertEquals(ChatbotResponse.ResponseStatus.SUCCESS, byId.getStatus());
                        ChatbotResponse q = provider.searchByQuery("đấu giá " + seed + " " + i);
                        assertTrue(q.getStatus() != null);
                        provider.getFaqsByCategory("GENERAL");
                        provider.getAllFaqs();
                    } catch (Throwable e) {
                        failures.incrementAndGet();
                    }
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(0, failures.get(), "Không được có exception trong worker");
    }
}
