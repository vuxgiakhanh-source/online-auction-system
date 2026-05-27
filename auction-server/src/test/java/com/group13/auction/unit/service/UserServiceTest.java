package com.group13.auction.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group13.auction.dao.AdminDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.exception.AuthenticationException.Reason;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.UserService;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UserService}.
 *
 * <p>Scope: authentication/login flow only.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - Authentication")
class UserServiceTest {

  @Mock private UserDAO userDAO;

  @Mock private AdminDAO adminDAO;

  private UserService sut;

  @BeforeEach
  void setUp() {
    sut = new UserService(userDAO, adminDAO);
    // Đảm bảo AuctionManager không chứa user nào trước mỗi test
    // để UserService.login() không tìm thấy trong memory và luôn đi qua findUserCoreByUsername()
    clearAuctionManagerUsers();
  }

  @AfterEach
  void tearDown() {
    // Dọn user được addToUserList() bởi login() thành công
    clearAuctionManagerUsers();
  }

  private void clearAuctionManagerUsers() {
    try {
      java.lang.reflect.Field f = AuctionManager.class.getDeclaredField("allUsers");
      f.setAccessible(true);
      ((java.util.Map<?, ?>) f.get(AuctionManager.getInstance())).clear();
    } catch (Exception ignored) {
    }
  }

  @Test
  @DisplayName("login - valid credentials and ACTIVE account returns User")
  void login_validCredentials_activeAccount_returnsUser() {
    String rawPassword = "correctPass1";
    NormalUser user = normalBidder("bidder01", rawPassword, AccountStatus.ACTIVE, 3.0);
    when(userDAO.findUserCoreByUsername("bidder01")).thenReturn(user);

    User result = sut.login("bidder01", rawPassword);

    assertThat(result).isSameAs(user);
  }

  @Test
  @DisplayName("login - queries user DAO exactly once")
  void login_callsUserDAOExactlyOnce() {
    String rawPassword = "correctPass1";
    NormalUser user = normalBidder("bidder01", rawPassword, AccountStatus.ACTIVE, 3.0);
    when(userDAO.findUserCoreByUsername("bidder01")).thenReturn(user);

    sut.login("bidder01", rawPassword);

    // FIX: login() dùng findUserCoreByUsername() — lightweight 1-query auth
    verify(userDAO, times(1)).findUserCoreByUsername("bidder01");
    verify(userDAO, never()).findUserByUsername(any());
  }

  @Test
  @DisplayName("login - missing username throws USER_NOT_FOUND")
  void login_usernameNotFound_throwsUserNotFound() {
    when(userDAO.findUserCoreByUsername("unknown")).thenReturn(null);
    when(adminDAO.findByUsername("unknown")).thenReturn(Optional.empty());

    AuthenticationException ex =
        catchThrowableOfType(
            () -> sut.login("unknown", "anyPassword"), AuthenticationException.class);

    assertThat(ex.getReason()).isEqualTo(Reason.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("login - BANNED account with wrong password throws WRONG_PASSWORD")
  void login_bannedAccount_wrongPassword_throwsWrongPassword() {
    NormalUser bannedUser = normalBidder("banned01", "actualPass", AccountStatus.BANNED, 0.0);
    when(userDAO.findUserCoreByUsername("banned01")).thenReturn(bannedUser);

    AuthenticationException ex =
        catchThrowableOfType(
            () -> sut.login("banned01", "wrongPass"), AuthenticationException.class);

    assertThat(ex.getReason()).isEqualTo(Reason.WRONG_PASSWORD);
  }

  @Test
  @DisplayName("login - BANNED account with correct password succeeds (restricted login)")
  void login_bannedAccount_correctPassword_succeeds() {
    NormalUser bannedUser = normalBidder("banned01", "actualPass", AccountStatus.BANNED, 0.0);
    when(userDAO.findUserCoreByUsername("banned01")).thenReturn(bannedUser);

    User result = sut.login("banned01", "actualPass");

    assertThat(result).isSameAs(bannedUser);
    assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.BANNED);
  }

  @Test
  @DisplayName("login - SUSPENDED account with correct password succeeds")
  void login_suspendedAccount_correctPassword_succeeds() {
    NormalUser suspendedUser = normalBidder("susp01", "pass123", AccountStatus.SUSPENDED, 1.0);
    when(userDAO.findUserCoreByUsername("susp01")).thenReturn(suspendedUser);

    User result = sut.login("susp01", "pass123");

    assertThat(result).isSameAs(suspendedUser);
    assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
  }

  @Test
  @DisplayName("login - SUSPENDED account with wrong password throws WRONG_PASSWORD")
  void login_suspended_wrongPassword_throwsWrongPassword() {
    NormalUser suspendedUser = normalBidder("susp02", "correctPass", AccountStatus.SUSPENDED, 1.0);
    when(userDAO.findUserCoreByUsername("susp02")).thenReturn(suspendedUser);

    AuthenticationException ex =
        catchThrowableOfType(() -> sut.login("susp02", "wrongPass"), AuthenticationException.class);

    assertThat(ex.getReason()).isEqualTo(Reason.WRONG_PASSWORD);
  }

  @Test
  @DisplayName("login - ACTIVE account with wrong password throws WRONG_PASSWORD")
  void login_activeAccount_wrongPassword_throwsWrongPassword() {
    NormalUser user = normalBidder("active01", "correctPass", AccountStatus.ACTIVE, 3.0);
    when(userDAO.findUserCoreByUsername("active01")).thenReturn(user);

    AuthenticationException ex =
        catchThrowableOfType(
            () -> sut.login("active01", "wrongPass"), AuthenticationException.class);

    assertThat(ex.getReason()).isEqualTo(Reason.WRONG_PASSWORD);
  }

  @Test
  @DisplayName("login - empty password throws WRONG_PASSWORD")
  void login_emptyPassword_throwsWrongPassword() {
    NormalUser user = normalBidder("active02", "correctPass", AccountStatus.ACTIVE, 3.0);
    when(userDAO.findUserCoreByUsername("active02")).thenReturn(user);

    AuthenticationException ex =
        catchThrowableOfType(() -> sut.login("active02", ""), AuthenticationException.class);

    assertThat(ex.getReason()).isEqualTo(Reason.WRONG_PASSWORD);
  }

  @Test
  @DisplayName("login - password comparison is case-sensitive")
  void login_passwordCaseSensitive_throwsWrongPassword() {
    NormalUser user = normalBidder("active03", "Password1", AccountStatus.ACTIVE, 3.0);
    when(userDAO.findUserCoreByUsername("active03")).thenReturn(user);

    AuthenticationException ex =
        catchThrowableOfType(
            () -> sut.login("active03", "password1"), AuthenticationException.class);

    assertThat(ex.getReason()).isEqualTo(Reason.WRONG_PASSWORD);
  }

  @Test
  @DisplayName("login - USER_NOT_FOUND is checked before password")
  void login_checkOrder_notFoundBeforePassword() {
    when(userDAO.findUserCoreByUsername("ghost")).thenReturn(null);
    when(adminDAO.findByUsername("ghost")).thenReturn(Optional.empty());

    AuthenticationException ex =
        catchThrowableOfType(() -> sut.login("ghost", "anyPass"), AuthenticationException.class);

    assertThat(ex.getReason()).isEqualTo(Reason.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("login - BANNED with wrong password yields WRONG_PASSWORD not ACCOUNT_BANNED")
  void login_checkOrder_bannedStillChecksPassword() {
    NormalUser bannedUser = normalBidder("banned02", "realPass", AccountStatus.BANNED, 0.0);
    when(userDAO.findUserCoreByUsername("banned02")).thenReturn(bannedUser);

    AuthenticationException ex =
        catchThrowableOfType(
            () -> sut.login("banned02", "wrongPass"), AuthenticationException.class);

    assertThat(ex.getReason()).isEqualTo(Reason.WRONG_PASSWORD);
  }

  @Test
  @DisplayName("login - AuthenticationException has descriptive message")
  void login_exceptionContainsDescriptiveMessage() {
    when(userDAO.findUserCoreByUsername("nobody")).thenReturn(null);
    when(adminDAO.findByUsername("nobody")).thenReturn(Optional.empty());

    AuthenticationException ex =
        catchThrowableOfType(() -> sut.login("nobody", "pass"), AuthenticationException.class);

    assertThat(ex.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("login - returns exact object from DAO")
  void login_returnsExactObjectFromDAO() {
    String rawPassword = "correctPass1";
    NormalUser expected = normalBidder("bidder99", rawPassword, AccountStatus.ACTIVE, 4.5);
    when(userDAO.findUserCoreByUsername("bidder99")).thenReturn(expected);

    User result = sut.login("bidder99", rawPassword);

    assertThat(result).isSameAs(expected);
  }

  private static NormalUser normalBidder(
      String username, String rawPassword, AccountStatus status, double rating) {
    return NormalUser.reconstitute(
        UUID.randomUUID().toString(),
        LocalDateTime.now(),
        LocalDateTime.now(),
        username,
        User.hashPassword(rawPassword),
        username + "@test.com",
        status,
        rating,
        0L,
        0L,
        EnumSet.of(User.UserRole.BIDDER),
        false,
        0,
        null);
  }
}
