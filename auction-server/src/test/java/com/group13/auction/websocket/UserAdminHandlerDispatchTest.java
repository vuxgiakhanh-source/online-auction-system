package com.group13.auction.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.handler.UserAdminHandler;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.QualityReportService;
import com.group13.auction.service.RatingService;
import com.group13.auction.unit.TestFixture;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link UserAdminHandler} — profile, seller role, ban/unban, rating, quality report.
 *
 * <p>Chiến lược: mock AccountService, RatingService, QualityReportService.
 * SessionManager singleton thật — register/unregister mock WebSocket.
 * Không chạm DB, không Testcontainers.
 *
 * <p>Cùng pattern với {@link PaymentHandlerDispatchTest}:
 * captureLastSent() lấy JSON packet cuối cùng gửi đi, assert PacketType + field.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserAdminHandler — dispatch & guard")
class UserAdminHandlerDispatchTest {

    private static final Gson GSON = PacketCodec.gson();

    @Mock AccountService       accountService;
    @Mock RatingService        ratingService;
    @Mock QualityReportService qualityReportService;
    @Mock WebSocket            webSocket;

    SessionManager sessionManager = SessionManager.getInstance();

    private NormalUser normalUser;
    private Admin      staffAdmin;

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();
        reset(webSocket, accountService, ratingService, qualityReportService);
        lenient().when(webSocket.isOpen()).thenReturn(true);
        lenient().when(webSocket.getRemoteSocketAddress())
                .thenReturn(new java.net.InetSocketAddress("127.0.0.1", 8080));
        sessionManager.unregister(webSocket);
        sessionManager.register(webSocket);

        normalUser = TestFixture.bidderWithBalance("handler_user01", 5_000_000L);
        AuctionManager.getInstance().addToUserList(normalUser);
        sessionManager.authenticate(webSocket, normalUser.getId(),
                normalUser.getUsername(), "NORMAL_USER");
    }

    @AfterEach
    void tearDown() throws Exception {
        sessionManager.unregister(webSocket);
        TestFixture.resetSystemAdmin();
        clearAuctionManagerUsers();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clearAuctionManagerUsers() {
        try {
            Field f = AuctionManager.class.getDeclaredField("allUsers");
            f.setAccessible(true);
            ((Map<?, ?>) f.get(AuctionManager.getInstance())).clear();
        } catch (Exception ignored) {}
    }

    private UserAdminHandler newHandler() {
        return new UserAdminHandler(accountService, ratingService,
                qualityReportService, sessionManager);
    }

    private ClientSession session() {
        return sessionManager.getByConnection(webSocket);
    }

    private String captureLastSent() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(webSocket, atLeastOnce()).send(cap.capture());
        return cap.getValue();
    }

    // ── supports() ────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("supports() — đúng tập PacketType")
    class SupportsTest {

        @Test
        @DisplayName("Hỗ trợ đúng các PacketType của UserAdminHandler")
        void supports_correctTypes() {
            UserAdminHandler h = newHandler();
            assertThat(h.supports(PacketType.GET_MY_PROFILE)).isTrue();
            assertThat(h.supports(PacketType.GET_USER_PROFILE)).isTrue();
            assertThat(h.supports(PacketType.REQUEST_SELLER_ROLE)).isTrue();
            assertThat(h.supports(PacketType.ADMIN_BAN_USER)).isTrue();
            assertThat(h.supports(PacketType.ADMIN_UNBAN_USER)).isTrue();
            assertThat(h.supports(PacketType.ADMIN_GET_ALL_USERS)).isTrue();
            assertThat(h.supports(PacketType.RATE_SELLER)).isTrue();
            assertThat(h.supports(PacketType.RATE_BIDDER)).isTrue();
            assertThat(h.supports(PacketType.SUBMIT_QUALITY_REPORT)).isTrue();
            assertThat(h.supports(PacketType.PING)).isTrue();
        }

        @Test
        @DisplayName("Không hỗ trợ PLACE_BID, DEPOSIT, LOGIN")
        void supports_rejectsOtherTypes() {
            UserAdminHandler h = newHandler();
            assertThat(h.supports(PacketType.PLACE_BID)).isFalse();
            assertThat(h.supports(PacketType.DEPOSIT)).isFalse();
            assertThat(h.supports(PacketType.LOGIN)).isFalse();
        }
    }

    // ── Unauthenticated guard ─────────────────────────────────────────────────
    @Nested
    @DisplayName("Unauthenticated guard — tất cả packet trả SYSTEM_ERROR UNAUTHORIZED")
    class UnauthGuard {

        @Test
        @DisplayName("GET_MY_PROFILE chưa đăng nhập → SYSTEM_ERROR UNAUTHORIZED")
        void getMyProfile_unauthenticated_unauthorized() {
            sessionManager.deauthenticate(webSocket);
            newHandler().handle(session(), PacketType.GET_MY_PROFILE, null, "r1");
            assertThat(captureLastSent())
                    .contains(PacketType.SYSTEM_ERROR.name())
                    .contains(ErrorDTO.UNAUTHORIZED);
        }

        @Test
        @DisplayName("REQUEST_SELLER_ROLE chưa đăng nhập → SYSTEM_ERROR UNAUTHORIZED")
        void requestSellerRole_unauthenticated_unauthorized() {
            sessionManager.deauthenticate(webSocket);
            newHandler().handle(session(), PacketType.REQUEST_SELLER_ROLE, null, "r2");
            assertThat(captureLastSent())
                    .contains(PacketType.SYSTEM_ERROR.name())
                    .contains(ErrorDTO.UNAUTHORIZED);
        }

        @Test
        @DisplayName("ADMIN_BAN_USER chưa đăng nhập → SYSTEM_ERROR UNAUTHORIZED")
        void adminBanUser_unauthenticated_unauthorized() {
            sessionManager.deauthenticate(webSocket);
            newHandler().handle(session(), PacketType.ADMIN_BAN_USER, null, "r3");
            assertThat(captureLastSent())
                    .contains(PacketType.SYSTEM_ERROR.name())
                    .contains(ErrorDTO.UNAUTHORIZED);
        }
    }

    // ── PING ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("PING")
    class PingTest {

        @Test
        @DisplayName("PING → PONG ngay cả khi chưa login")
        void ping_alwaysReturnsPong() {
            sessionManager.deauthenticate(webSocket);
            newHandler().handle(session(), PacketType.PING, null, "r-ping");
            assertThat(captureLastSent()).contains(PacketType.PONG.name());
        }

        @Test
        @DisplayName("PING đã login → PONG")
        void ping_authenticated_returnsPong() {
            newHandler().handle(session(), PacketType.PING, null, "r-ping-auth");
            assertThat(captureLastSent()).contains(PacketType.PONG.name());
        }
    }

    // ── GET_MY_PROFILE ───────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET_MY_PROFILE")
    class GetMyProfileTest {

        @Test
        @DisplayName("happy path → GET_MY_PROFILE_SUCCESS chứa username")
        void getMyProfile_success() {
            newHandler().handle(session(), PacketType.GET_MY_PROFILE, null, "r-profile-1");
            assertThat(captureLastSent())
                    .contains(PacketType.GET_MY_PROFILE_SUCCESS.name())
                    .contains(normalUser.getUsername());
        }

        @Test
        @DisplayName("user không tồn tại trong AuctionManager → SYSTEM_ERROR USER_NOT_FOUND")
        void getMyProfile_userNotFound() {
            clearAuctionManagerUsers(); // xóa user khỏi in-memory
            newHandler().handle(session(), PacketType.GET_MY_PROFILE, null, "r-profile-2");
            assertThat(captureLastSent())
                    .contains(PacketType.SYSTEM_ERROR.name())
                    .contains(ErrorDTO.USER_NOT_FOUND);
        }
    }

    // ── GET_USER_PROFILE ─────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET_USER_PROFILE")
    class GetUserProfileTest {

        @Test
        @DisplayName("userId tồn tại → GET_USER_PROFILE_SUCCESS")
        void getUserProfile_found() {
            JsonElement payload = GSON.toJsonTree(normalUser.getId());
            newHandler().handle(session(), PacketType.GET_USER_PROFILE, payload, "r-up-1");
            assertThat(captureLastSent())
                    .contains(PacketType.GET_USER_PROFILE_SUCCESS.name());
        }

        @Test
        @DisplayName("userId không tồn tại → GET_USER_PROFILE_FAILED USER_NOT_FOUND")
        void getUserProfile_notFound() {
            JsonElement payload = GSON.toJsonTree("nonexistent-user-id");
            newHandler().handle(session(), PacketType.GET_USER_PROFILE, payload, "r-up-2");
            assertThat(captureLastSent())
                    .contains(PacketType.GET_USER_PROFILE_FAILED.name())
                    .contains(ErrorDTO.USER_NOT_FOUND);
        }
    }

    // ── REQUEST_SELLER_ROLE ──────────────────────────────────────────────────
    @Nested
    @DisplayName("REQUEST_SELLER_ROLE")
    class RequestSellerRoleTest {

        @Test
        @DisplayName("happy path → REQUEST_SELLER_ROLE_SUCCESS + SELLER_ROLE_APPROVED_NOTIFY")
        void requestSellerRole_success() {
            doNothing().when(accountService).autoApproveSellerRole(any());
            newHandler().handle(session(), PacketType.REQUEST_SELLER_ROLE, null, "r-seller-1");
            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeast(2)).send(cap.capture());
            String allSent = String.join("|", cap.getAllValues());
            assertThat(allSent).contains(PacketType.REQUEST_SELLER_ROLE_SUCCESS.name());
            assertThat(allSent).contains(PacketType.SELLER_ROLE_APPROVED_NOTIFY.name());
        }

        @Test
        @DisplayName("accountService throws → REQUEST_SELLER_ROLE_FAILED + SELLER_ROLE_REJECTED_NOTIFY")
        void requestSellerRole_rejected() {
            doThrow(new IllegalStateException("Không đủ điều kiện"))
                    .when(accountService).autoApproveSellerRole(any());
            newHandler().handle(session(), PacketType.REQUEST_SELLER_ROLE, null, "r-seller-2");
            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeast(2)).send(cap.capture());
            String allSent = String.join("|", cap.getAllValues());
            assertThat(allSent).contains(PacketType.REQUEST_SELLER_ROLE_FAILED.name());
            assertThat(allSent).contains(PacketType.SELLER_ROLE_REJECTED_NOTIFY.name());
        }
    }

    // ── ADMIN_BAN_USER — phân quyền ──────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN_BAN_USER — chỉ ADMIN mới được ban")
    class AdminBanUserTest {

        @Test
        @DisplayName("NORMAL_USER cố ban → ADMIN_BAN_USER_FAILED UNAUTHORIZED")
        void banUser_normalUser_unauthorized() {
            AdminDTOs.AdminBanUserDTO req = new AdminDTOs.AdminBanUserDTO();
            req.setUserId(normalUser.getId());
            req.setReason("FRAUD");
            newHandler().handle(session(), PacketType.ADMIN_BAN_USER,
                    GSON.toJsonTree(req), "r-ban-1");
            assertThat(captureLastSent())
                    .contains(PacketType.ADMIN_BAN_USER_FAILED.name())
                    .contains(ErrorDTO.UNAUTHORIZED);
            verify(accountService, never()).banUser(any(), any(), any());
        }
    }

    // ── ADMIN_GET_ALL_USERS — phân quyền ─────────────────────────────────────
    @Nested
    @DisplayName("ADMIN_GET_ALL_USERS — chỉ ADMIN mới được xem")
    class AdminGetAllUsersTest {

        @Test
        @DisplayName("NORMAL_USER cố lấy danh sách → SYSTEM_ERROR UNAUTHORIZED")
        void getAllUsers_normalUser_unauthorized() {
            newHandler().handle(session(), PacketType.ADMIN_GET_ALL_USERS, null, "r-all-1");
            assertThat(captureLastSent())
                    .contains(PacketType.SYSTEM_ERROR.name())
                    .contains(ErrorDTO.UNAUTHORIZED);
        }
    }
}
