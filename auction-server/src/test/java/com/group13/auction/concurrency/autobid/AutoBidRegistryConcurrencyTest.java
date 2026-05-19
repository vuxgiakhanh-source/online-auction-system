package com.group13.auction.concurrency.autobid;

import com.group13.auction.strategy.AutoBidRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AutoBidRegistry concurrency")
class AutoBidRegistryConcurrencyTest {

    private final AutoBidRegistry registry = AutoBidRegistry.getInstance();

    @AfterEach
    void tearDown() {
        registry.clearAuction("auc-reg-test");
    }

    @Test
    @DisplayName("32 thread register cùng key — 1 entry, registeredAt ổn định")
    @Timeout(10)
    void concurrentRegister_sameKey_singleEntry() throws Exception {
        String auctionId = "auc-reg-test";
        String userId = "user-reg";
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final long max = 1_000_000L + i;
            pool.submit(() -> {
                try {
                    start.await();
                    registry.register(userId, auctionId, max);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(8, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(errors.get()).isZero();
        assertThat(registry.hasActiveBid(userId, auctionId)).isTrue();
        assertThat(registry.getEntriesForAuction(auctionId)).hasSize(1);
    }
}
