package com.group13.auction.websocket.handler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auth.LoginRequestDTO;
import com.group13.auction.common.dto.auth.RegisterRequestDTO;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.network.server.handler.AuthHandler;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.UserService;
import com.group13.auction.unit.TestFixture;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link AuthHandler} — REGISTER / LOGIN / LOGOUT.
 *
 * <p>Chiến lược: mock toàn bộ dependency (UserService, AccountService, UserDAO,
 * SessionManager, WebSocket). Verify đúng PacketType response và đúng error code
 * được gửi về qua {@code session.send()}.
 *
 * <p>Không chạm DB, không Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthHandler — REGISTER / LOGIN / LOGOUT dispatch")
class AuthHandlerDispatchTest {

    private static final Gson GSON = PacketCodec.gson();

    @Mock AccountService accountService;
    @Mock UserService     userService;
    @Mock UserDAO         userDAO;
    @Mock WebSocket       webSocket;

    SessionManager sessionManager = SessionManager.getInstance();

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();
        reset(webSocket, userService, accountService, userDAO);
        lenient().when(webSocket.isOpen()).thenReturn(true);
        sessionManager.unregister(webSocket);
        sessionManager.register(webSocket);
    }

    @AfterEach
    void tearDown() throws Exception {
        sessionManager.unregister(webSocket);
        TestFixture.resetSystemAdmin();
        // Clean up AuctionManager in-memory users added during register tests
        try {
            java.lang.reflect.Field f = AuctionManager.class.getDeclaredField("allUsers");
            f.setAccessible(true);
            ((java.util.Map<?, ?>) f.get(AuctionManager.getInstance())).clear();
        } catch (Exception ignored) {}
    }

    private AuthHandler newHandler() {
        // Inject mocked userDAO via reflection to avoid real DB calls
        AuthHandler handler = new AuthHandler(accountService, userService, sessionManager);
        try {
            java.lang.reflect.Field f = AuthHandler.class.getDeclaredField("userDAO");
            f.setAccessible(true);
            f.set(handler, userDAO);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return handler;
    }

    private ClientSession session() {
        return sessionManager.getByConnection(webSocket);
    }

    private String captureLastSent() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(webSocket, atLeastOnce()).send(cap.capture());
        return cap.getValue();
    }

    // =========================================================================
    // REGISTER
    // =========================================================================

    @Nested
    @DisplayName("REGISTER")
    class Register {

        @Test
        @DisplayName("happy path: valid payload → REGISTER_SUCCESS + session authenticated")
        void register_validPayload_success() {
            RegisterRequestDTO dto = new RegisterRequestDTO("newuser1", "pass1234", "new@test.vn");
            JsonElement payload = GSON.toJsonTree(dto);

            when(userDAO.existsByUsername("newuser1")).thenReturn(false);
            when(userDAO.existsByEmail("new@test.vn")).thenReturn(false);
            when(userDAO.save(any(NormalUser.class))).thenReturn(true);

            newHandler().handle(session(), PacketType.REGISTER, payload, "rid-reg-1");

            String sent = captureLastSent();
            assertThat(sent).contains(PacketType.REGISTER_SUCCESS.name());
            assertThat(sent).contains("rid-reg-1");
            assertThat(session().isAuthenticated()).isTrue();
        }

        @Test
        @DisplayName("blank username → REGISTER_FAILED VALIDATION_ERROR")
        void register_blankUsername_validationError() {
            JsonElement payload = GSON.toJsonTree(new RegisterRequestDTO("", "pass1234", "a@b.com"));

            newHandler().handle(session(), PacketType.REGISTER, payload, "rid-reg-2");

            assertThat(captureLastSent())
                    .contains(PacketType.REGISTER_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("blank password → REGISTER_FAILED VALIDATION_ERROR")
        void register_blankPassword_validationError() {
            JsonElement payload = GSON.toJsonTree(new RegisterRequestDTO("validuser", "", "a@b.com"));

            newHandler().handle(session(), PacketType.REGISTER, payload, "rid-reg-3");

            assertThat(captureLastSent())
                    .contains(PacketType.REGISTER_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("blank email → REGISTER_FAILED VALIDATION_ERROR")
        void register_blankEmail_validationError() {
            JsonElement payload = GSON.toJsonTree(new RegisterRequestDTO("validuser", "pass1234", ""));

            newHandler().handle(session(), PacketType.REGISTER, payload, "rid-reg-4");

            assertThat(captureLastSent())
                    .contains(PacketType.REGISTER_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("duplicate username → REGISTER_FAILED DUPLICATE_USERNAME")
        void register_duplicateUsername_duplicateError() {
            when(userDAO.existsByUsername("taken")).thenReturn(true);
            JsonElement payload = GSON.toJsonTree(new RegisterRequestDTO("taken", "pass1234", "x@test.vn"));

            newHandler().handle(session(), PacketType.REGISTER, payload, "rid-reg-5");

            assertThat(captureLastSent())
                    .contains(PacketType.REGISTER_FAILED.name())
                    .contains(ErrorDTO.DUPLICATE_USERNAME);
        }

        @Test
        @DisplayName("duplicate email → REGISTER_FAILED DUPLICATE_EMAIL")
        void register_duplicateEmail_duplicateError() {
            when(userDAO.existsByUsername("freshuser")).thenReturn(false);
            when(userDAO.existsByEmail("taken@test.vn")).thenReturn(true);
            JsonElement payload = GSON.toJsonTree(new RegisterRequestDTO("freshuser", "pass1234", "taken@test.vn"));

            newHandler().handle(session(), PacketType.REGISTER, payload, "rid-reg-6");

            assertThat(captureLastSent())
                    .contains(PacketType.REGISTER_FAILED.name())
                    .contains(ErrorDTO.DUPLICATE_EMAIL);
        }

        @Test
        @DisplayName("DB save returns false → REGISTER_FAILED INTERNAL_ERROR")
        void register_dbSaveFails_internalError() {
            when(userDAO.existsByUsername(anyString())).thenReturn(false);
            when(userDAO.existsByEmail(anyString())).thenReturn(false);
            when(userDAO.save(any())).thenReturn(false);
            JsonElement payload = GSON.toJsonTree(new RegisterRequestDTO("dbfailuser", "pass1234", "db@fail.vn"));

            newHandler().handle(session(), PacketType.REGISTER, payload, "rid-reg-7");

            assertThat(captureLastSent())
                    .contains(PacketType.REGISTER_FAILED.name())
                    .contains(ErrorDTO.INTERNAL_ERROR);
        }

        @Test
        @DisplayName("null payload → REGISTER_FAILED (no NPE)")
        void register_nullPayload_gracefulError() {
            newHandler().handle(session(), PacketType.REGISTER, null, "rid-reg-8");

            assertThat(captureLastSent()).contains(PacketType.REGISTER_FAILED.name());
        }
    }

    // =========================================================================
    // LOGIN
    // =========================================================================

    @Nested
    @DisplayName("LOGIN")
    class Login {

        @Test
        @DisplayName("happy path: valid credentials → LOGIN_SUCCESS + session authenticated")
        void login_validCredentials_success() {
            NormalUser user = TestFixture.bidderWithBalance("loginuser1", 5_000_000L);
            when(userService.login("loginuser1", "pass1234")).thenReturn(user);
            JsonElement payload = GSON.toJsonTree(new LoginRequestDTO("loginuser1", "pass1234"));

            newHandler().handle(session(), PacketType.LOGIN, payload, "rid-login-1");

            String sent = captureLastSent();
            assertThat(sent).contains(PacketType.LOGIN_SUCCESS.name());
            assertThat(session().isAuthenticated()).isTrue();
        }

        @Test
        @DisplayName("wrong credentials → LOGIN_FAILED WRONG_PASSWORD")
        void login_wrongCredentials_wrongPassword() {
            when(userService.login(anyString(), anyString())).thenReturn(null);
            JsonElement payload = GSON.toJsonTree(new LoginRequestDTO("user1", "wrongpass"));

            newHandler().handle(session(), PacketType.LOGIN, payload, "rid-login-2");

            assertThat(captureLastSent())
                    .contains(PacketType.LOGIN_FAILED.name())
                    .contains(ErrorDTO.WRONG_PASSWORD);
        }

        @Test
        @DisplayName("banned account → LOGIN_FAILED ACCOUNT_BANNED")
        void login_bannedAccount_accountBanned() {
            NormalUser banned = TestFixture.bannedBidder("bannedloginusr");
            when(userService.login("bannedloginusr", "pass")).thenReturn(banned);
            JsonElement payload = GSON.toJsonTree(new LoginRequestDTO("bannedloginusr", "pass"));

            newHandler().handle(session(), PacketType.LOGIN, payload, "rid-login-3");

            assertThat(captureLastSent())
                    .contains(PacketType.LOGIN_FAILED.name())
                    .contains(ErrorDTO.ACCOUNT_BANNED);
        }

        @Test
        @DisplayName("blank username → LOGIN_FAILED VALIDATION_ERROR")
        void login_blankUsername_validationError() {
            JsonElement payload = GSON.toJsonTree(new LoginRequestDTO("", "pass1234"));

            newHandler().handle(session(), PacketType.LOGIN, payload, "rid-login-4");

            assertThat(captureLastSent())
                    .contains(PacketType.LOGIN_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
            verify(userService, never()).login(any(), any());
        }

        @Test
        @DisplayName("blank password → LOGIN_FAILED VALIDATION_ERROR — userService not called")
        void login_blankPassword_validationError_noServiceCall() {
            JsonElement payload = GSON.toJsonTree(new LoginRequestDTO("user1", ""));

            newHandler().handle(session(), PacketType.LOGIN, payload, "rid-login-5");

            assertThat(captureLastSent())
                    .contains(PacketType.LOGIN_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
            verify(userService, never()).login(any(), any());
        }

        @Test
        @DisplayName("userService throws AuthenticationException → LOGIN_FAILED với đúng reason")
        void login_authException_propagatesReason() {
            when(userService.login(anyString(), anyString()))
                    .thenThrow(new AuthenticationException(AuthenticationException.Reason.ACCOUNT_SUSPENDED));
            JsonElement payload = GSON.toJsonTree(new LoginRequestDTO("user1", "pass1234"));

            newHandler().handle(session(), PacketType.LOGIN, payload, "rid-login-6");

            assertThat(captureLastSent())
                    .contains(PacketType.LOGIN_FAILED.name())
                    .contains("ACCOUNT_SUSPENDED");
        }

        @Test
        @DisplayName("requestId được pass qua response")
        void login_requestId_includedInResponse() {
            NormalUser user = TestFixture.bidderWithBalance("reqiduser11", 0L);
            when(userService.login(anyString(), anyString())).thenReturn(user);
            JsonElement payload = GSON.toJsonTree(new LoginRequestDTO("reqiduser11", "pass"));

            newHandler().handle(session(), PacketType.LOGIN, payload, "my-custom-rid");

            assertThat(captureLastSent()).contains("my-custom-rid");
        }
    }

    // =========================================================================
    // LOGOUT
    // =========================================================================

    @Nested
    @DisplayName("LOGOUT")
    class Logout {

        @Test
        @DisplayName("logout → LOGOUT_SUCCESS + session deauthenticated")
        void logout_success() {
            // Authenticate first
            sessionManager.authenticate(webSocket, "uid-1", "logoutuser1", "NORMAL_USER");
            assertThat(session().isAuthenticated()).isTrue();

            newHandler().handle(session(), PacketType.LOGOUT, null, "rid-logout-1");

            assertThat(captureLastSent()).contains(PacketType.LOGOUT_SUCCESS.name());
            assertThat(session().isAuthenticated()).isFalse();
        }

        @Test
        @DisplayName("logout without being logged in → still LOGOUT_SUCCESS (idempotent)")
        void logout_whenNotLoggedIn_idempotent() {
            assertThat(session().isAuthenticated()).isFalse();

            newHandler().handle(session(), PacketType.LOGOUT, null, "rid-logout-2");

            assertThat(captureLastSent()).contains(PacketType.LOGOUT_SUCCESS.name());
        }
    }
}
