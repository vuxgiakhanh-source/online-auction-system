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
}
