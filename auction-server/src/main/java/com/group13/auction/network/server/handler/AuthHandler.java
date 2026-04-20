package com.group13.auction.network.server.handler;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auth.LoginRequestDTO;
import com.group13.auction.common.dto.auth.LoginResponseDTO;
import com.group13.auction.common.dto.auth.RegisterRequestDTO;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.UserService;
import com.group13.auction.network.server.util.DTOMapper;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Xử lý các packet: REGISTER, LOGIN, LOGOUT.
 *
 * <p>Tách biệt hoàn toàn khỏi business logic — chỉ:
 * <ol>
 *   <li>Deserialize payload.</li>
 *   <li>Gọi Service.</li>
 *   <li>Serialize kết quả và gửi về.</li>
 * </ol>
 */
public class AuthHandler implements PacketHandler {

    private static final Set<PacketType> SUPPORTED = EnumSet.of(
            PacketType.REGISTER,
            PacketType.LOGIN,
            PacketType.LOGOUT
    );

    private final AccountService accountService;
    private final UserService userService;
    private final SessionManager sessionManager;

    public AuthHandler(AccountService accountService,
                       UserService userService,
                       SessionManager sessionManager) {
        this.accountService = accountService;
        this.userService = userService;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean supports(PacketType type) {
        return SUPPORTED.contains(type);
    }

    @Override
    public void handle(ClientSession session, PacketType type,
                       JsonElement payload, String requestId) {
        switch (type) {
            case REGISTER -> handleRegister(session, payload, requestId);
            case LOGIN    -> handleLogin(session, payload, requestId);
            case LOGOUT   -> handleLogout(session, requestId);
            default       -> { /* không xảy ra do supports() đã lọc */ }
        }
    }

    // ── REGISTER ──────────────────────────────────────────────────────────────

    private void handleRegister(ClientSession session, JsonElement payload, String requestId) {
        try {
            RegisterRequestDTO req = PacketCodec.fromElement(payload, RegisterRequestDTO.class);

            // Validate input
            if (isBlank(req.getUsername()) || isBlank(req.getPassword()) || isBlank(req.getEmail())) {
                session.send(Packet.of(PacketType.REGISTER_FAILED,
                        ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                                "Username, password và email không được để trống.", requestId)));
                return;
            }

            // Gọi service tạo user
            com.group13.auction.model.user.NormalUser newUser =
                    userService.register(req.getUsername(), req.getPassword(), req.getEmail());

            // Tạo token session
            String token = UUID.randomUUID().toString();
            UserDTO userDTO = DTOMapper.toUserDTO(newUser, true);

            // Xác thực session ngay sau register
            sessionManager.authenticate(session.getConnection(),
                    newUser.getId(), newUser.getUsername(), resolveRole(newUser));

            LoginResponseDTO response = new LoginResponseDTO(token, userDTO);
            session.send(Packet.of(PacketType.REGISTER_SUCCESS, response, requestId));

        } catch (IllegalArgumentException e) {
            String code = e.getMessage().contains("username")
                    ? ErrorDTO.DUPLICATE_USERNAME
                    : e.getMessage().contains("email")
                    ? ErrorDTO.DUPLICATE_EMAIL
                    : ErrorDTO.VALIDATION_ERROR;
            session.send(Packet.of(PacketType.REGISTER_FAILED,
                    ErrorDTO.of(code, e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.REGISTER_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Lỗi hệ thống khi đăng ký.", requestId)));
        }
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────────

    private void handleLogin(ClientSession session, JsonElement payload, String requestId) {
        try {
            LoginRequestDTO req = PacketCodec.fromElement(payload, LoginRequestDTO.class);

            if (isBlank(req.getUsername()) || isBlank(req.getPassword())) {
                session.send(Packet.of(PacketType.LOGIN_FAILED,
                        ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                                "Username và password không được để trống.", requestId)));
                return;
            }

            com.group13.auction.model.user.User user =
                    userService.login(req.getUsername(), req.getPassword());

            if (user == null) {
                session.send(Packet.of(PacketType.LOGIN_FAILED,
                        ErrorDTO.of(ErrorDTO.WRONG_PASSWORD,
                                "Sai username hoặc password.", requestId)));
                return;
            }

            // Kiểm tra tài khoản bị ban
            if (user.getAccountStatus() == com.group13.auction.model.user.User.AccountStatus.BANNED) {
                session.send(Packet.of(PacketType.LOGIN_FAILED,
                        ErrorDTO.of(ErrorDTO.ACCOUNT_BANNED,
                                "Tài khoản đã bị khóa vĩnh viễn.", requestId)));
                return;
            }

            String token = UUID.randomUUID().toString();
            String role = resolveRole(user);
            UserDTO userDTO = DTOMapper.toUserDTO(user, true);

            sessionManager.authenticate(session.getConnection(), user.getId(), user.getUsername(), role);

            LoginResponseDTO response = new LoginResponseDTO(token, userDTO);
            session.send(Packet.of(PacketType.LOGIN_SUCCESS, response, requestId));

        } catch (com.group13.auction.exception.AuthenticationException e) {
            session.send(Packet.of(PacketType.LOGIN_FAILED,
                    ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.LOGIN_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Lỗi hệ thống khi đăng nhập.", requestId)));
        }
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────────

    private void handleLogout(ClientSession session, String requestId) {
        sessionManager.deauthenticate(session.getConnection());
        session.send(Packet.of(PacketType.LOGOUT_SUCCESS, null, requestId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveRole(com.group13.auction.model.user.User user) {
        if (user instanceof com.group13.auction.model.user.SystemAdmin) {
            return "ADMIN_MASTER";
        }
        if (user instanceof com.group13.auction.model.user.Admin) {
            return "ADMIN_STAFF";
        }
        return "NORMAL_USER";
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
