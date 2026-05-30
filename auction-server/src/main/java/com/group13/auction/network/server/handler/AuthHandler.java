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
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.NormalUserFactory;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.UserService;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý các packet: REGISTER, LOGIN, LOGOUT.
 *
 * <p>Tách biệt hoàn toàn khỏi business logic — chỉ:
 *
 * <ol>
 *   <li>Deserialize payload.
 *   <li>Gọi Service.
 *   <li>Serialize kết quả và gửi về.
 * </ol>
 *
 * <p>Register dùng {@link UserDAO} + {@link NormalUserFactory} trực tiếp vì {@link UserService} chỉ
 * cung cấp {@code login()}. {@link
 * AuctionManager#registerUser(com.group13.auction.model.user.User)} được gọi sau khi lưu DB để đồng
 * bộ in-memory registry.
 */
public class AuthHandler implements PacketHandler {

  private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);

  private static final Set<PacketType> SUPPORTED =
      EnumSet.of(PacketType.REGISTER, PacketType.LOGIN, PacketType.LOGOUT);

  private final AccountService accountService;
  private final UserService userService;
  private final SessionManager sessionManager;

  /** Dùng cho REGISTER: kiểm tra duplicate và lưu user mới vào DB. */
  private final UserDAO userDAO;

  /**
   * Constructor đầy đủ — tự khởi tạo {@link UserDAO} để xử lý đăng ký.
   *
   * @param accountService service tài khoản
   * @param userService service xác thực (login)
   * @param sessionManager quản lý session
   */
  public AuthHandler(
      AccountService accountService, UserService userService, SessionManager sessionManager) {
    this.accountService = accountService;
    this.userService = userService;
    this.sessionManager = sessionManager;
    this.userDAO = new UserDAO();
  }

  @Override
  public boolean supports(PacketType type) {
    return SUPPORTED.contains(type);
  }

  @Override
  public void handle(
      ClientSession session, PacketType type, JsonElement payload, String requestId) {
    switch (type) {
      case REGISTER -> handleRegister(session, payload, requestId);
      case LOGIN -> handleLogin(session, payload, requestId);
      case LOGOUT -> handleLogout(session, requestId);
      default -> {
        /* không xảy ra do supports() đã lọc */
      }
    }
  }

  // ── REGISTER ──────────────────────────────────────────────────────────────

  /**
   * Tạo tài khoản mới:
   *
   * <ol>
   *   <li>Validate input.
   *   <li>Kiểm tra duplicate username / email qua {@link UserDAO}.
   *   <li>Tạo {@link NormalUser} qua {@link NormalUserFactory} (hash password).
   *   <li>Lưu DB + đăng ký {@link AuctionManager} in-memory.
   *   <li>Authenticate session và trả {@code REGISTER_SUCCESS}.
   * </ol>
   */
  private void handleRegister(ClientSession session, JsonElement payload, String requestId) {
    try {
      RegisterRequestDTO req = PacketCodec.fromElement(payload, RegisterRequestDTO.class);

      // Validate input
      if (isBlank(req.getUsername()) || isBlank(req.getPassword()) || isBlank(req.getEmail())) {
        log.warn("Register rejected - blank fields: requestId={}", requestId);
        session.send(
            Packet.of(
                PacketType.REGISTER_FAILED,
                ErrorDTO.of(
                    ErrorDTO.VALIDATION_ERROR,
                    "Username, password và email không được để trống.",
                    requestId),
                requestId));
        return;
      }

      // Kiểm tra trùng username
      if (userDAO.existsByUsername(req.getUsername())) {
        log.warn(
            "Register rejected - duplicate username: username={}, requestId={}",
            req.getUsername(),
            requestId);
        session.send(
            Packet.of(
                PacketType.REGISTER_FAILED,
                ErrorDTO.of(
                    ErrorDTO.DUPLICATE_USERNAME,
                    "Username '" + req.getUsername() + "' đã tồn tại.",
                    requestId),
                requestId));
        return;
      }

      // Kiểm tra trùng email
      if (userDAO.existsByEmail(req.getEmail())) {
        log.warn(
            "Register rejected - duplicate email: email={}, requestId={}",
            req.getEmail(),
            requestId);
        session.send(
            Packet.of(
                PacketType.REGISTER_FAILED,
                ErrorDTO.of(
                    ErrorDTO.DUPLICATE_EMAIL,
                    "Email '" + req.getEmail() + "' đã được sử dụng.",
                    requestId),
                requestId));
        return;
      }

      // Tạo NormalUser qua factory (hash password trong constructor)
      NormalUser newUser =
          new NormalUserFactory().createUser(req.getUsername(), req.getPassword(), req.getEmail());

      // FIX Bug #2: lưu vào DB một lần duy nhất, kiểm tra kết quả.
      // AuctionManager.registerUser() lại gọi userDAO.save() lần nữa → duplicate insert.
      // Dùng addToUserList() thay thế để chỉ thêm vào in-memory.
      boolean saved = userDAO.save(newUser);
      if (!saved) {
        log.error(
            "Register failed - DB save returned false: username={}, requestId={}",
            req.getUsername(),
            requestId);
        session.send(
            Packet.of(
                PacketType.REGISTER_FAILED,
                ErrorDTO.of(
                    ErrorDTO.INTERNAL_ERROR,
                    "Không thể lưu tài khoản vào cơ sở dữ liệu.",
                    requestId),
                requestId));
        return;
      }
      AuctionManager.getInstance().addToUserList(newUser);

      // Tạo token session
      String token = UUID.randomUUID().toString();
      UserDTO userDTO = DTOMapper.toUserDTO(newUser, true);

      // Xác thực session ngay sau register
      sessionManager.authenticate(
          session.getConnection(), newUser.getId(), newUser.getUsername(), resolveRole(newUser));

      log.info(
          "Register success: userId={}, username={}, requestId={}",
          newUser.getId(),
          newUser.getUsername(),
          requestId);
      LoginResponseDTO response = new LoginResponseDTO(token, userDTO);
      session.send(Packet.of(PacketType.REGISTER_SUCCESS, response, requestId));

    } catch (IllegalArgumentException e) {
      log.warn(
          "Register rejected - validation error: requestId={}, reason={}",
          requestId,
          e.getMessage());
      String code =
          e.getMessage().contains("username")
              ? ErrorDTO.DUPLICATE_USERNAME
              : e.getMessage().contains("email")
                  ? ErrorDTO.DUPLICATE_EMAIL
                  : ErrorDTO.VALIDATION_ERROR;
      session.send(
          Packet.of(
              PacketType.REGISTER_FAILED, ErrorDTO.of(code, e.getMessage(), requestId), requestId));
    } catch (Exception e) {
      log.error("Register failed - unexpected error: requestId={}", requestId, e);
      session.send(
          Packet.of(
              PacketType.REGISTER_FAILED,
              ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Lỗi hệ thống khi đăng ký.", requestId),
              requestId));
    }
  }

  // ── LOGIN ─────────────────────────────────────────────────────────────────

  private void handleLogin(ClientSession session, JsonElement payload, String requestId) {
    try {
      LoginRequestDTO req = PacketCodec.fromElement(payload, LoginRequestDTO.class);

      if (isBlank(req.getUsername()) || isBlank(req.getPassword())) {
        log.warn("Login rejected - blank fields: requestId={}", requestId);
        session.send(
            Packet.of(
                PacketType.LOGIN_FAILED,
                ErrorDTO.of(
                    ErrorDTO.VALIDATION_ERROR,
                    "Username và password không được để trống.",
                    requestId),
                requestId));
        return;
      }

      com.group13.auction.model.user.User user =
          userService.login(req.getUsername(), req.getPassword());

      if (user == null) {
        log.warn(
            "Login rejected - wrong credentials: username={}, requestId={}",
            req.getUsername(),
            requestId);
        session.send(
            Packet.of(
                PacketType.LOGIN_FAILED,
                ErrorDTO.of(ErrorDTO.WRONG_PASSWORD, "Sai username hoặc password.", requestId),
                requestId));
        return;
      }

      boolean restricted =
          user.getAccountStatus() == com.group13.auction.model.user.User.AccountStatus.BANNED
              || user.getAccountStatus()
                  == com.group13.auction.model.user.User.AccountStatus.SUSPENDED;

      String token = UUID.randomUUID().toString();
      String role = resolveRole(user);
      UserDTO userDTO = DTOMapper.toUserDTO(user, true);

      sessionManager.authenticate(
          session.getConnection(), user.getId(), user.getUsername(), role, restricted);
      if (user instanceof NormalUser normalUser) {
        session.setCachedUser(normalUser);
      }

      log.info(
          "Login success: userId={}, username={}, role={}, restricted={}, requestId={}",
          user.getId(),
          user.getUsername(),
          role,
          restricted,
          requestId);
      LoginResponseDTO response = new LoginResponseDTO(token, userDTO, restricted);
      session.send(Packet.of(PacketType.LOGIN_SUCCESS, response, requestId));

    } catch (com.group13.auction.exception.AuthenticationException e) {
      log.warn(
          "Login rejected - auth exception: requestId={}, reason={}", requestId, e.getReason());
      session.send(
          Packet.of(
              PacketType.LOGIN_FAILED,
              ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId),
              requestId));
    } catch (Exception e) {
      log.error("Login failed - unexpected error: requestId={}", requestId, e);
      session.send(
          Packet.of(
              PacketType.LOGIN_FAILED,
              ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Lỗi hệ thống khi đăng nhập.", requestId),
              requestId));
    }
  }

  // ── LOGOUT ────────────────────────────────────────────────────────────────

  private void handleLogout(ClientSession session, String requestId) {
    log.info("Logout: username={}, requestId={}", session.getUsername(), requestId);
    sessionManager.deauthenticate(session.getConnection());
    session.send(Packet.of(PacketType.LOGOUT_SUCCESS, null, requestId));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private String resolveRole(com.group13.auction.model.user.User user) {
    if (user instanceof com.group13.auction.model.user.SystemAdmin) {
      return "ADMIN_MASTER";
    }
    if (user instanceof com.group13.auction.model.user.Admin admin) {
      return admin.isMaster() ? "ADMIN_MASTER" : "ADMIN_STAFF";
    }
    return "NORMAL_USER";
  }

  private boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
