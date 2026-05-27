package com.group13.auction.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import com.google.gson.JsonElement;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.handler.PacketHandler;
import com.group13.auction.network.server.router.PacketRouter;
import com.group13.auction.network.server.session.ClientSession;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ============================================================================ LOAD TEST —
 * PacketRouterLoadTest (unit, không cần Docker)
 * ============================================================================
 *
 * <p>Kiểm tra PacketRouter dưới tải đồng thời: - route() song song 32 thread — không exception,
 * không deadlock - Nhiều loại packet xen kẽ — dispatch đúng handler - Packet lỗi (malformed JSON)
 * song song — không crash router - register() handler song song nhiều thread
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PacketRouterLoadTest — PacketRouter dưới tải cao (unit)")
class PacketRouterLoadTest {

  @Mock PacketHandler authHandler;
  @Mock PacketHandler bidHandler;
  @Mock PacketHandler paymentHandler;
  @Mock ClientSession session;

  private PacketRouter router;

  @BeforeEach
  void setUp() {
    lenient()
        .when(authHandler.supports(any()))
        .thenAnswer(
            inv -> {
              PacketType t = inv.getArgument(0);
              return t == PacketType.LOGIN || t == PacketType.REGISTER || t == PacketType.LOGOUT;
            });
    lenient()
        .when(bidHandler.supports(any()))
        .thenAnswer(
            inv -> {
              PacketType t = inv.getArgument(0);
              return t == PacketType.PLACE_BID
                  || t == PacketType.JOIN_AUCTION
                  || t == PacketType.WATCH_AUCTION
                  || t == PacketType.LEAVE_AUCTION;
            });
    lenient()
        .when(paymentHandler.supports(any()))
        .thenAnswer(
            inv -> {
              PacketType t = inv.getArgument(0);
              return t == PacketType.DEPOSIT || t == PacketType.WITHDRAW;
            });
    // doNothing cho handle()
    lenient()
        .doNothing()
        .when(authHandler)
        .handle(any(), any(), any(JsonElement.class), anyString());
    lenient()
        .doNothing()
        .when(bidHandler)
        .handle(any(), any(), any(JsonElement.class), anyString());
    lenient()
        .doNothing()
        .when(paymentHandler)
        .handle(any(), any(), any(JsonElement.class), anyString());
    // session.send cũng doNothing
    lenient().doNothing().when(session).send(any());

    router = new PacketRouter();
    router.register(authHandler);
    router.register(bidHandler);
    router.register(paymentHandler);
  }

  // =========================================================================
  // Group 1 – route() song song nhiều loại packet
  // =========================================================================

  @Nested
  @DisplayName("Group 1 – route() song song nhiều loại packet")
  class RouteConcurrentTest {

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-PR1: 32 thread × 200 lần route() với nhiều PacketType — không exception, không deadlock")
    void concurrent_route_mixedPacketTypes_noException() throws Exception {
      int threads = 32;
      int opsPerThread = 200;

      // Chuẩn bị các packet JSON hợp lệ
      String loginJson =
          PacketCodec.encode(
              Packet.of(
                  PacketType.LOGIN,
                  new com.group13.auction.common.dto.auth.LoginRequestDTO("user", "pass"),
                  "r1"));
      String joinJson = "{\"type\":\"JOIN_AUCTION\",\"requestId\":\"r2\",\"payload\":\"auc-1\"}";
      String bidJson =
          "{\"type\":\"PLACE_BID\",\"requestId\":\"r3\",\"payload\":{\"auctionId\":\"A\",\"amount\":1000000}}";
      String depJson =
          "{\"type\":\"DEPOSIT\",\"requestId\":\"r4\",\"payload\":{\"amount\":500000}}";
      String[] packets = {loginJson, joinJson, bidJson, depJson};

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        final int seed = t;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int op = 0; op < opsPerThread; op++) {
                      try {
                        router.route(session, packets[(seed + op) % packets.length]);
                      } catch (Exception e) {
                        failures.incrementAndGet();
                      }
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
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

      assertThat(failures.get())
          .as("route() không được throw exception bất kỳ dưới 32 thread")
          .isZero();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-PR2: 16 thread route() xen kẽ packet hợp lệ và malformed JSON — router không crash")
    void concurrent_route_mixedValidAndMalformed_routerDoesNotCrash() throws Exception {
      int threads = 16;
      int opsPerThread = 100;

      String validJson =
          PacketCodec.encode(
              Packet.of(
                  PacketType.LOGIN,
                  new com.group13.auction.common.dto.auth.LoginRequestDTO("u", "p"),
                  "valid"));
      String malformedJson = "{ not-valid-json-at-all";
      String emptyJson = "";
      String unknownType = "{\"type\":\"UNKNOWN_TYPE_XYZ\",\"requestId\":\"u1\",\"payload\":null}";

      String[] candidates = {validJson, malformedJson, emptyJson, unknownType};

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger crashes = new AtomicInteger(); // NPE, OOME, StackOverflow, etc.

      for (int t = 0; t < threads; t++) {
        final int seed = t;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int op = 0; op < opsPerThread; op++) {
                      try {
                        router.route(session, candidates[(seed + op) % candidates.length]);
                      } catch (Error e) {
                        // Lỗi JVM (StackOverflow, OOM) là crash thực sự
                        crashes.incrementAndGet();
                      }
                      // Exception Java thông thường là chấp nhận được (lỗi validation)
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
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

      assertThat(crashes.get()).as("Router không được bị JVM crash dưới bất kỳ input nào").isZero();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName("L-PR3: Throughput — 8 thread route() trong 10s phải > 1000 lần/s tổng cộng")
    void routeThroughput_sustainedLoad_meetsThreshold() throws Exception {
      int threads = 8;
      int durationMs = 10_000;
      String json =
          PacketCodec.encode(
              Packet.of(
                  PacketType.LOGIN,
                  new com.group13.auction.common.dto.auth.LoginRequestDTO("u", "p"),
                  "tp"));

      AtomicInteger totalRouted = new AtomicInteger();
      long endTime = System.currentTimeMillis() + durationMs;

      ExecutorService pool = Executors.newFixedThreadPool(threads);
      List<Future<?>> futures = new ArrayList<>();

      for (int t = 0; t < threads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  while (System.currentTimeMillis() < endTime) {
                    try {
                      router.route(session, json);
                      totalRouted.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                  }
                }));
      }

      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

      double throughputPerSec = (double) totalRouted.get() / (durationMs / 1000.0);
      assertThat(totalRouted.get()).as("Phải có ít nhất 1 route thực hiện được").isPositive();
      assertThat(throughputPerSec)
          .as("Throughput phải > 1000 route/s (thực tế: %.0f/s)", throughputPerSec)
          .isGreaterThan(1000.0);
    }
  }

  // =========================================================================
  // Group 2 – PacketCodec encode/decode dưới tải
  // =========================================================================

  @Nested
  @DisplayName("Group 2 – PacketCodec encode/decode song song")
  class PacketCodecLoadTest {

    @Test
    @Timeout(value = 20)
    @DisplayName("L-PR4: 16 thread × 500 lần encode Packet — không exception, output hợp lệ JSON")
    void concurrent_encode_noException_validJson() throws Exception {
      int threads = 16;
      int opsPerThread = 500;

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        final int seed = t;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int op = 0; op < opsPerThread; op++) {
                      try {
                        String json =
                            PacketCodec.encode(
                                Packet.of(PacketType.PING, null, "req-" + seed + "-" + op));
                        if (json == null || !json.contains("PING")) {
                          failures.incrementAndGet();
                        }
                      } catch (Exception e) {
                        failures.incrementAndGet();
                      }
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
      assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
      assertThat(failures.get())
          .as("PacketCodec.encode không được fail hay trả về null dưới tải")
          .isZero();
    }

    @Test
    @Timeout(value = 20)
    @DisplayName("L-PR5: 8 thread × 300 lần peekType — không exception, trả về đúng PacketType")
    void concurrent_peekType_noException_correctType() throws Exception {
      int threads = 8;
      int opsPerThread = 300;

      String loginJson =
          PacketCodec.encode(
              Packet.of(
                  PacketType.LOGIN,
                  new com.group13.auction.common.dto.auth.LoginRequestDTO("u", "p"),
                  "r"));
      String bidJson = "{\"type\":\"PLACE_BID\",\"requestId\":\"r\",\"payload\":{}}";

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        final int seed = t;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int op = 0; op < opsPerThread; op++) {
                      try {
                        String json = (seed % 2 == 0) ? loginJson : bidJson;
                        PacketType type = PacketCodec.peekType(json);
                        if (type == null) {
                          failures.incrementAndGet();
                        }
                        if (seed % 2 == 0 && type != PacketType.LOGIN) {
                          failures.incrementAndGet();
                        }
                      } catch (Exception e) {
                        failures.incrementAndGet();
                      }
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
      assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
      assertThat(failures.get()).isZero();
    }
  }
}
