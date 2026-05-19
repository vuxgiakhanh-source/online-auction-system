package com.group13.auction.security;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.handler.BidHandler;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.BidService;
import com.group13.auction.service.iservice.IRatingService;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import com.group13.auction.strategy.BidRateLimiter;
import static org.mockito.Mockito.when;

/**
 * Thay thế phần "Spring Security + @PreAuthorize" trong stack hiện tại:
 * kiểm soát truy cập dựa trên {@link ClientSession#isAuthenticated()} và
 * {@link com.group13.auction.network.server.handler.BidHandler} (requireNormalUser cho thao tác bid).
 *
 * <p>Phạm vi:
 * <ul>
 *   <li>Chưa đăng nhập → mọi packet trong BidHandler đều bị từ chối sớm (SYSTEM_ERROR / UNAUTHORIZED).</li>
 *   <li>Payload không parse được → packet *_FAILED tương ứng, không gọi BidService.</li>
 *   <li>Đã login nhưng không phải NormalUser trong hệ thống (role admin / user không tồn tại trong DB map)
 *       → requireNormalUser trả lỗi UNAUTHORIZED.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BidHandler — an toàn WebSocket & phân quyền (unit, đầy đủ)")
class BidHandlerWebSocketSecurityTest {

    private static final Gson GSON = new Gson();

    @Mock(lenient = true)
    WebSocket webSocket;

    @Mock
    BidService bidService;

    @Mock
    IRatingService ratingService;

    SessionManager sessionManager = SessionManager.getInstance();

    @BeforeEach
    void prepareCleanSocketAndMocks() {
        reset(webSocket, bidService, ratingService);
        lenient().when(webSocket.isOpen()).thenReturn(true);
        sessionManager.unregister(webSocket);
        // Reset rate limiter singleton giữa các test để tránh leak state
        BidRateLimiter.getInstance().clearAll();
    }

    @AfterEach
    void detachSocket() {
        sessionManager.unregister(webSocket);
    }

    /** Các packet bị chặn tại cửa handle() khi session chưa authenticate. */
    enum UnauthenticatedBidPacket {
        JOIN_AUCTION(PacketType.JOIN_AUCTION, new JsonPrimitive("auc-any")),
        WATCH_AUCTION(PacketType.WATCH_AUCTION, new JsonPrimitive("auc-any")),
        LEAVE_AUCTION(PacketType.LEAVE_AUCTION, new JsonPrimitive("auc-any")),
        PLACE_BID(PacketType.PLACE_BID, GSON.toJsonTree(new BidDTOs.BidRequestDTO("auc-1", 1_000_000L))),
        REGISTER_AUTO_BID(PacketType.REGISTER_AUTO_BID,
                GSON.toJsonTree(new BidDTOs.AutoBidRequestDTO("auc-1", 9_000_000L))),
        UPDATE_AUTO_BID(PacketType.UPDATE_AUTO_BID,
                GSON.toJsonTree(new BidDTOs.AutoBidRequestDTO("auc-1", 10_000_000L))),
        CANCEL_AUTO_BID(PacketType.CANCEL_AUTO_BID, new JsonPrimitive("auc-1")),
        GET_AUTO_BID_STATUS(PacketType.GET_AUTO_BID_STATUS, new JsonPrimitive("auc-1")),
        GET_BID_HISTORY(PacketType.GET_BID_HISTORY, new JsonPrimitive("auc-1"));

        private final PacketType type;
        private final JsonElement payload;

        UnauthenticatedBidPacket(PacketType type, JsonElement payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    private BidHandler newHandler() {
        return new BidHandler(bidService, ratingService, sessionManager);
    }

    private ClientSession registerOpenSession() {
        when(webSocket.isOpen()).thenReturn(true);
        sessionManager.register(webSocket);
        return sessionManager.getByConnection(webSocket);
    }

    private void assertLastSendContainsSystemUnauthorized() {
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(webSocket).send(sent.capture());
        assertThat(sent.getValue()).contains(PacketType.SYSTEM_ERROR.name());
        assertThat(sent.getValue()).contains(ErrorDTO.UNAUTHORIZED);
        assertThat(sent.getValue()).contains("Chưa đăng nhập");
    }

    @Nested
    @DisplayName("Given session chưa login When gửi packet BidHandler Then UNAUTHORIZED (không gọi BidService)")
    class Unauthorized {

        @ParameterizedTest(name = "{0}")
        @EnumSource(UnauthenticatedBidPacket.class)
        @DisplayName("Parameterized: mọi loại packet bid-flow đều bị chặn")
        void anyBidPacket_rejectedBeforeService(UnauthenticatedBidPacket testCase) {
            ClientSession session = registerOpenSession();

            newHandler().handle(session, testCase.type, testCase.payload, "rid-unauth-" + testCase.name());

            assertLastSendContainsSystemUnauthorized();
            verifyNoInteractions(bidService);
        }
    }

    @Nested
    @DisplayName("Given đã login When payload không parse được Then *_FAILED phù hợp (không gọi BidService)")
    class InvalidPayload {

        @Test
        @DisplayName("PLACE_BID — JSON array không map được BidRequestDTO → PLACE_BID_FAILED")
        void placeBid_invalidJson_returnsPlaceBidFailed() {
            ClientSession session = registerOpenSession();
            session.authenticate("u-1", "alice", "NORMAL_USER");
            JsonElement bad = JsonParser.parseString("[]");

            newHandler().handle(session, PacketType.PLACE_BID, bad, "rid-bad-pb");

            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(webSocket).send(sent.capture());
            assertThat(sent.getValue()).contains(PacketType.PLACE_BID_FAILED.name());
            verifyNoInteractions(bidService);
        }

        @Test
        @DisplayName("REGISTER_AUTO_BID — JSON array không map AutoBidRequestDTO → REGISTER_AUTO_BID_FAILED")
        void registerAutoBid_invalidJson_returnsFailed() {
            ClientSession session = registerOpenSession();
            session.authenticate("u-1", "alice", "NORMAL_USER");
            JsonElement bad = JsonParser.parseString("[]");

            newHandler().handle(session, PacketType.REGISTER_AUTO_BID, bad, "rid-bad-ab");

            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(webSocket).send(sent.capture());
            assertThat(sent.getValue()).contains(PacketType.REGISTER_AUTO_BID_FAILED.name());
            verifyNoInteractions(bidService);
        }
    }

    @Nested
    @DisplayName("Given session đã authenticate nhưng role Admin / không có NormalUser trong manager")
    class NonNormalUserRole {

        @Test
        @DisplayName("ADMIN_STAFF + PLACE_BID → requireNormalUser thất bại → SYSTEM_ERROR UNAUTHORIZED (Chỉ NormalUser…)")
        void adminStaff_cannotPlaceBidViaRequireNormalUser() {
            ClientSession session = registerOpenSession();
            session.authenticate("adm-1", "staffUser", "ADMIN_STAFF");
            JsonElement payload = GSON.toJsonTree(new BidDTOs.BidRequestDTO("auc-x", 1_000_000L));

            newHandler().handle(session, PacketType.PLACE_BID, payload, "rid-admin");

            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(webSocket).send(sent.capture());
            assertThat(sent.getValue()).contains(PacketType.SYSTEM_ERROR.name());
            assertThat(sent.getValue()).contains(ErrorDTO.UNAUTHORIZED);
            assertThat(sent.getValue()).contains("NormalUser");
            verifyNoInteractions(bidService);
        }

        @Test
        @DisplayName("NORMAL_USER flag nhưng username không tồn tại trong DB/manager → vẫn không phải NormalUser instance")
        void authenticatedButUnknownUsername_stillFailsRequireNormalUser() {
            ClientSession session = registerOpenSession();
            session.authenticate("ghost-id", "noSuchUserInDb", "NORMAL_USER");
            JsonElement payload = GSON.toJsonTree(new BidDTOs.BidRequestDTO("auc-x", 1_000_000L));

            newHandler().handle(session, PacketType.PLACE_BID, payload, "rid-ghost");

            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(webSocket).send(sent.capture());
            assertThat(sent.getValue()).contains(PacketType.SYSTEM_ERROR.name());
            assertThat(sent.getValue()).contains(ErrorDTO.UNAUTHORIZED);
            verifyNoInteractions(bidService);
        }
    }

    // ── Rate Limit Tests ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("PLACE_BID — rate limiting (max 5 bid/giây/user)")
    class RateLimiting {

        @Test
        @DisplayName("6 PLACE_BID liên tiếp cùng user trong 1 giây → bid thứ 6 bị RATE_LIMIT_EXCEEDED")
        void placeBid_exceedsRateLimit_returnsRateLimitError() {
            // Arrange — tạo session đã authenticate
            ClientSession session = registerOpenSession();
            session.authenticate("rl-user-id", "rl_test_user", "NORMAL_USER");

            // Payload hợp lệ — bid thứ 6 phải bị chặn bởi rate limiter (theo userId, không username)
            JsonElement payload = GSON.toJsonTree(new BidDTOs.BidRequestDTO("auc-rate", 1_000_000L));

            BidHandler handler = newHandler();
            String rateLimitKey = "rl-user-id";
            BidRateLimiter.getInstance().remove(rateLimitKey);

            // Consume 5 token (đến limit) — BidHandler.tryConsume dùng session.getUserId()
            for (int i = 0; i < 5; i++) {
                BidRateLimiter.getInstance().tryConsume(rateLimitKey);
            }

            // Lần thứ 6 — phải bị chặn bởi rate limiter
            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            handler.handle(session, PacketType.PLACE_BID, payload, "rid-rate-" + 6);

            verify(webSocket, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
            String lastSent = sent.getAllValues().get(sent.getAllValues().size() - 1);
            assertThat(lastSent)
                    .as("Bid thứ 6 phải bị PLACE_BID_FAILED với RATE_LIMIT_EXCEEDED")
                    .contains(PacketType.PLACE_BID_FAILED.name())
                    .contains("RATE_LIMIT_EXCEEDED");
            verifyNoInteractions(bidService);
        }

        @Test
        @DisplayName("5 PLACE_BID liên tiếp cùng user → đều đi qua rate limiter (không bị chặn)")
        void placeBid_withinRateLimit_notBlocked() {
            // Rate limiter cho phép 5/giây — các lần bid trong limit chỉ bị chặn bởi logic khác
            // (requireNormalUser fail), KHÔNG phải rate limiter
            ClientSession session = registerOpenSession();
            session.authenticate("rl-user2-id", "rl_test_user2", "NORMAL_USER");
            JsonElement payload = GSON.toJsonTree(new BidDTOs.BidRequestDTO("auc-rate2", 1_000_000L));

            BidHandler handler = newHandler();
            BidRateLimiter.getInstance().remove("rl-user2-id");

            for (int i = 0; i < 5; i++) {
                handler.handle(session, PacketType.PLACE_BID, payload, "rid-ok-" + i);
            }

            // Mỗi lần đều fail ở requireNormalUser (SYSTEM_ERROR UNAUTHORIZED),
            // KHÔNG phải RATE_LIMIT_EXCEEDED
            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(webSocket, org.mockito.Mockito.times(5)).send(sent.capture());
            for (String msg : sent.getAllValues()) {
                assertThat(msg)
                        .as("Trong giới hạn rate limit, lỗi là UNAUTHORIZED chứ không phải RATE_LIMIT_EXCEEDED")
                        .doesNotContain("RATE_LIMIT_EXCEEDED");
            }
        }
    }
}