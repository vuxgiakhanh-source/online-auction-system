package com.group13.auction.stress.session;

import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Stress test SessionManager — Singleton thật, không DB.
 *
 * <p>SessionManager API dùng:
 *   register(WebSocket) → ClientSession
 *   unregister(WebSocket)
 *   authenticate(WebSocket, userId, username, role)
 *   getByConnection(WebSocket) → ClientSession
 *   getConnectedCount()
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionManager Stress — đăng ký đồng thời")
class SessionManagerStressTest {

    private static final Logger log = LoggerFactory.getLogger(SessionManagerStressTest.class);
    private static final int TIMEOUT_SEC = 30;

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() throws Exception {
        sessionManager = SessionManager.getInstance();
        clearSessionManagerMaps();
    }

    /** Reset 2 ConcurrentHashMap trong Singleton để tránh state leak giữa các test. */
    private void clearSessionManagerMaps() {
        for (String field : new String[]{"byConnection", "byUserId"}) {
            try {
                Field f = SessionManager.class.getDeclaredField(field);
                f.setAccessible(true);
                ((ConcurrentHashMap<?, ?>) f.get(sessionManager)).clear();
            } catch (Exception e) {
                log.warn("Cannot clear field {}: {}", field, e.getMessage());
            }
        }
    }

    /** Tạo mock WebSocket với địa chỉ hợp lệ (SessionManager.register gọi getRemoteSocketAddress). */
    private WebSocket mockWs(String id) {
        WebSocket ws = mock(WebSocket.class, withSettings().name("ws-" + id));
        lenient().when(ws.isOpen()).thenReturn(true);
        lenient().when(ws.getRemoteSocketAddress())
                .thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        return ws;
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — 100 thread register đồng thời, không NPE hay lỗi")
    void stress_concurrentRegister_noErrors() throws Exception {
        int COUNT = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            final String id = "reg-" + i;
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                try {
                    sessionManager.register(mockWs(id));
                } catch (Exception e) {
                    errors.incrementAndGet();
                    log.warn("[STRESS SESSION] register error: {}", e.getMessage());
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(errors.get()).as("Không có exception khi register đồng thời").isZero();
        assertThat(sessionManager.getConnectedCount()).isEqualTo(COUNT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — register rồi unregister đồng thời, không state leak")
    void stress_registerUnregister_noStateLeak() throws Exception {
        int COUNT = 100;
        List<WebSocket> wsList = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) wsList.add(mockWs("rr-" + i));

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            final WebSocket ws = wsList.get(i);
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                try {
                    sessionManager.register(ws);
                    sessionManager.unregister(ws);
                } catch (Exception e) {
                    errors.incrementAndGet();
                    log.warn("[STRESS SESSION] register/unregister error: {}", e.getMessage());
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(errors.get()).as("Không có exception khi register/unregister đồng thời").isZero();
        // Sau register+unregister đầy đủ mỗi ws → map phải trống
        assertThat(sessionManager.getConnectedCount())
                .as("byConnection phải trống sau khi tất cả unregister")
                .isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — 50 authenticate đồng thời, getByConnection trả đúng session")
    void stress_concurrentAuthenticate_sessionConsistent() throws Exception {
        int COUNT = 50;
        List<WebSocket> wsList = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            WebSocket ws = mockWs("auth-" + i);
            wsList.add(ws);
            sessionManager.register(ws);
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                try {
                    sessionManager.authenticate(
                            wsList.get(idx), "userId-" + idx, "user-" + idx, "NORMAL_USER");
                } catch (Exception e) {
                    errors.incrementAndGet();
                    log.warn("[STRESS SESSION] authenticate error: {}", e.getMessage());
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(errors.get()).as("Không có exception khi authenticate đồng thời").isZero();

        // Mỗi session phải được đánh dấu authenticated
        for (int i = 0; i < COUNT; i++) {
            ClientSession session = sessionManager.getByConnection(wsList.get(i));
            assertThat(session)
                    .as("Session %d phải tồn tại sau authenticate", i)
                    .isNotNull();
            assertThat(session.isAuthenticated())
                    .as("Session %d phải isAuthenticated=true", i)
                    .isTrue();
        }
    }
}
