package com.group13.auction.security;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.model.item.ItemFactory;
import com.group13.auction.network.server.handler.AuctionHandler;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.AuctionService;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Kiểm tra guard xác thực / phân quyền trong {@link AuctionHandler}.
 * Không dùng @Mock nào ngoài 4 dependency của constructor.
 * Tất cả PacketType lấy từ enum thực tế.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionHandler — bảo mật & phân quyền (unit)")
class AuctionHandlerSecurityTest {

    @Mock(lenient = true) WebSocket webSocket;
    @Mock AuctionService  auctionService;
    @Mock AccountService  accountService;
    @Mock SessionManager  sessionManager;
    @Mock ItemFactory     itemFactory;

    private AuctionHandler handler;
    private ClientSession  unauthSession;
    private ClientSession  normalUserSession;
    private ClientSession  adminSession;

    @BeforeEach
    void setUp() {
        when(webSocket.isOpen()).thenReturn(true);
        handler = new AuctionHandler(auctionService, accountService, sessionManager, itemFactory);
        unauthSession     = new ClientSession(webSocket);
        normalUserSession = new ClientSession(webSocket);
        normalUserSession.authenticate("user-1", "alice", "NORMAL_USER");
        adminSession = new ClientSession(webSocket);
        adminSession.authenticate("admin-1", "staff01", "ADMIN_STAFF");
    }

    @AfterEach
    void tearDown() { reset(auctionService, accountService); }

    // ── Unauthenticated ──────────────────────────────────────────────────────
    @Nested
    @DisplayName("Unauthenticated — packet cần auth không gọi service")
    class UnauthenticatedTest {

        @Test @DisplayName("CREATE_AUCTION — auctionService không được gọi")
        void createAuction_unauthenticated() {
            handler.handle(unauthSession, PacketType.CREATE_AUCTION, new JsonPrimitive("{}"), "r1");
            verifyNoInteractions(auctionService);
        }

        @Test @DisplayName("CANCEL_AUCTION_REQUEST — auctionService không được gọi")
        void cancelRequest_unauthenticated() {
            handler.handle(unauthSession, PacketType.CANCEL_AUCTION_REQUEST, new JsonPrimitive("{}"), "r2");
            verifyNoInteractions(auctionService);
        }

        @Test @DisplayName("UPDATE_AUCTION — auctionService không được gọi")
        void updateAuction_unauthenticated() {
            handler.handle(unauthSession, PacketType.UPDATE_AUCTION, new JsonPrimitive("{}"), "r3");
            verifyNoInteractions(auctionService);
        }

        @Test @DisplayName("GET_AUCTION_LIST — không ném exception dù chưa login")
        void getAuctionList_unauthenticated_noException() {
            assertThatCode(() ->
                    handler.handle(unauthSession, PacketType.GET_AUCTION_LIST, JsonNull.INSTANCE, "r4"))
                    .doesNotThrowAnyException();
        }
    }

    // ── Admin endpoints ──────────────────────────────────────────────────────
    @Nested
    @DisplayName("Admin endpoints — NORMAL_USER bị từ chối")
    class AdminEndpointTest {

        @Test @DisplayName("ADMIN_CANCEL_AUCTION với NORMAL_USER — auctionService không được gọi")
        void adminCancel_normalUser() {
            handler.handle(normalUserSession, PacketType.ADMIN_CANCEL_AUCTION, new JsonPrimitive("{}"), "r5");
            verifyNoInteractions(auctionService);
        }

        @Test @DisplayName("ADMIN_GET_ALL_AUCTIONS với NORMAL_USER — auctionService không được gọi")
        void adminGetAll_normalUser() {
            handler.handle(normalUserSession, PacketType.ADMIN_GET_ALL_AUCTIONS, new JsonPrimitive("{}"), "r6");
            verifyNoInteractions(auctionService);
        }

        @Test @DisplayName("ADMIN_CANCEL_AUCTION chưa đăng nhập — không được gọi")
        void adminCancel_unauthenticated() {
            handler.handle(unauthSession, PacketType.ADMIN_CANCEL_AUCTION, new JsonPrimitive("{}"), "r7");
            verifyNoInteractions(auctionService);
        }
    }

    // ── supports() ───────────────────────────────────────────────────────────
    @Nested
    @DisplayName("supports() — đúng tập PacketType")
    class SupportsTest {

        @Test @DisplayName("Hỗ trợ đúng 7 PacketType của AuctionHandler")
        void supports_correctTypes() {
            assertThat(handler.supports(PacketType.CREATE_AUCTION)).isTrue();
            assertThat(handler.supports(PacketType.GET_AUCTION_LIST)).isTrue();
            assertThat(handler.supports(PacketType.GET_AUCTION_DETAIL)).isTrue();
            assertThat(handler.supports(PacketType.UPDATE_AUCTION)).isTrue();
            assertThat(handler.supports(PacketType.CANCEL_AUCTION_REQUEST)).isTrue();
            assertThat(handler.supports(PacketType.ADMIN_CANCEL_AUCTION)).isTrue();
            assertThat(handler.supports(PacketType.ADMIN_GET_ALL_AUCTIONS)).isTrue();
        }

        @Test @DisplayName("Không hỗ trợ PLACE_BID, JOIN_AUCTION, LOGIN, DEPOSIT")
        void supports_rejectsOtherTypes() {
            assertThat(handler.supports(PacketType.PLACE_BID)).isFalse();
            assertThat(handler.supports(PacketType.JOIN_AUCTION)).isFalse();
            assertThat(handler.supports(PacketType.LOGIN)).isFalse();
            assertThat(handler.supports(PacketType.DEPOSIT)).isFalse();
        }
    }

    // ── ClientSession auth state ─────────────────────────────────────────────
    @Nested
    @DisplayName("ClientSession — trạng thái xác thực")
    class SessionAuthStateTest {

        @Test @DisplayName("unauthSession — isAuthenticated = false, isAdmin = false")
        void unauthSession_state() {
            assertThat(unauthSession.isAuthenticated()).isFalse();
            assertThat(unauthSession.isAdmin()).isFalse();
        }

        @Test @DisplayName("normalUserSession — authenticated, không phải admin")
        void normalUserSession_state() {
            assertThat(normalUserSession.isAuthenticated()).isTrue();
            assertThat(normalUserSession.isAdmin()).isFalse();
        }

        @Test @DisplayName("adminSession — authenticated và isAdmin = true")
        void adminSession_state() {
            assertThat(adminSession.isAuthenticated()).isTrue();
            assertThat(adminSession.isAdmin()).isTrue();
        }
    }
}