package com.group13.auction.concurrency.lock;

import static org.assertj.core.api.Assertions.assertThat;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.strategy.AuctionLockRegistry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;

/**
 * ============================================================================
 * AuctionLockRegistryConcurrencyTest — Group A Unit contract của lock per-auction (Bottom-up).
 * Setup: chỉ cần AuctionLockRegistry, không cần BidService hay DAO.
 * ============================================================================
 */
@DisplayName("Lock: AuctionLockRegistry — Unit Contract")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuctionLockRegistryConcurrencyTest extends ConcurrencyTestBase {

  private AuctionLockRegistry lockRegistry;
  private Auction auction;

  @BeforeEach
  void setUp() {
    lockRegistry = AuctionLockRegistry.getInstance();
    auction = buildRunningAuction();
  }

  @AfterEach
  void tearDown() {
    // Dùng clearAll() để đảm bảo không còn lock nào rò rỉ sau mỗi test,
    // kể cả UUID-based IDs được tạo trong A2, A5.
    lockRegistry.clearAll();
  }

  // ── A1 ────────────────────────────────────────────────────────────────────

  @Test
  @Order(1)
  @DisplayName("A1: Cùng auctionId phải trả về cùng một ReentrantLock instance (identity)")
  void sameLockInstanceForSameAuctionId() {
    String id = auction.getId();
    ReentrantLock l1 = lockRegistry.getLock(id);
    ReentrantLock l2 = lockRegistry.getLock(id);
    assertThat(l1).isSameAs(l2);
  }

  // ── A2 ────────────────────────────────────────────────────────────────────

  @Test
  @Order(2)
  @DisplayName("A2: Hai auctionId khác nhau phải trả về hai lock khác nhau")
  void differentLockInstancesForDifferentAuctions() {
    String id1 = UUID.randomUUID().toString();
    String id2 = UUID.randomUUID().toString();
    ReentrantLock l1 = lockRegistry.getLock(id1);
    ReentrantLock l2 = lockRegistry.getLock(id2);
    assertThat(l1).isNotSameAs(l2);
    lockRegistry.release(id1);
    lockRegistry.release(id2);
  }

  // ── A3 ────────────────────────────────────────────────────────────────────

  @Test
  @Order(3)
  @DisplayName(
      "A3: Lock phải đảm bảo mutual exclusion — chỉ 1 thread vào critical section tại 1 thời điểm")
  @Timeout(value = 5)
  void lockEnsuresMutualExclusion() throws InterruptedException {
    ReentrantLock lock = lockRegistry.getLock(auction.getId());
    AtomicInteger concurrent = new AtomicInteger(0);
    AtomicInteger maxConcurrent = new AtomicInteger(0);
    int N = 10;
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(N);

    for (int i = 0; i < N; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  lock.lock();
                  try {
                    int cur = concurrent.incrementAndGet();
                    maxConcurrent.accumulateAndGet(cur, Math::max);
                    Thread.sleep(2);
                    concurrent.decrementAndGet();
                  } finally {
                    lock.unlock();
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(maxConcurrent.get())
        .as("Tối đa 1 thread trong critical section tại 1 thời điểm")
        .isEqualTo(1);
  }

  // ── A4 ────────────────────────────────────────────────────────────────────

  @Test
  @Order(4)
  @DisplayName("A4: release() phải xóa lock khỏi registry — tránh memory leak")
  void releaseRemovesLockFromRegistry() {
    String tempId = UUID.randomUUID().toString();
    int before = lockRegistry.size();
    lockRegistry.getLock(tempId);
    assertThat(lockRegistry.size()).isEqualTo(before + 1);
    lockRegistry.release(tempId);
    assertThat(lockRegistry.size()).isEqualTo(before);
  }

  // ── A5 ────────────────────────────────────────────────────────────────────

  @Test
  @Order(5)
  @DisplayName("A5: computeIfAbsent atomic — 50 threads tranh cùng id vẫn chỉ tạo 1 lock")
  @Timeout(value = 5)
  void concurrentGetLockReturnsSameInstance() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Set<ReentrantLock> lockSet = ConcurrentHashMap.newKeySet();
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(50);

    for (int i = 0; i < 50; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  lockSet.add(lockRegistry.getLock(id));
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    lockRegistry.release(id);

    assertThat(lockSet).hasSize(1);
  }
}
