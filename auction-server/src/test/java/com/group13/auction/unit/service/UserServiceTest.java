package com.group13.auction.unit.service;

import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.exception.AuthenticationException.Reason;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService}.
 *
 * <p>Scope: authentication/login flow only.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - Authentication")
class UserServiceTest {

    @Mock
    private UserDAO userDAO;

    private UserService sut;

    @BeforeEach
    void setUp() {
        sut = new UserService(userDAO);
    }

    @Test
    @DisplayName("login - valid credentials and ACTIVE account returns User")
    void login_validCredentials_activeAccount_returnsUser() {
        String rawPassword = "correctPass1";
        NormalUser user = normalBidder("bidder01", rawPassword, AccountStatus.ACTIVE, 3.0);
        when(userDAO.findUserByUsername("bidder01")).thenReturn(user);

        User result = sut.login("bidder01", rawPassword);

        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("login - queries user DAO exactly once")
    void login_callsUserDAOExactlyOnce() {
        String rawPassword = "correctPass1";
        NormalUser user = normalBidder("bidder01", rawPassword, AccountStatus.ACTIVE, 3.0);
        when(userDAO.findUserByUsername("bidder01")).thenReturn(user);

        sut.login("bidder01", rawPassword);

        verify(userDAO, times(1)).findUserByUsername("bidder01");
    }

    @Test
    @DisplayName("login - missing username throws USER_NOT_FOUND")
    void login_usernameNotFound_throwsUserNotFound() {
        when(userDAO.findUserByUsername("unknown")).thenReturn(null);

        AuthenticationException ex = catchThrowableOfType(
                () -> sut.login("unknown", "anyPassword"),
                AuthenticationException.class
        );

        assertThat(ex.getReason()).isEqualTo(Reason.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("login - BANNED account throws ACCOUNT_BANNED before password check")
    void login_bannedAccount_throwsBanned_beforePasswordCheck() {
        NormalUser bannedUser = normalBidder("banned01", "actualPass", AccountStatus.BANNED, 0.0);
        when(userDAO.findUserByUsername("banned01")).thenReturn(bannedUser);

        AuthenticationException ex = catchThrowableOfType(
                () -> sut.login("banned01", "wrongPass"),
                AuthenticationException.class
        );

        assertThat(ex.getReason()).isEqualTo(Reason.ACCOUNT_BANNED);
    }

    @Test
    @DisplayName("login - SUSPENDED account throws ACCOUNT_SUSPENDED")
    void login_suspendedAccount_throwsSuspended() {
        NormalUser suspendedUser = normalBidder("susp01", "pass123", AccountStatus.SUSPENDED, 1.0);
        when(userDAO.findUserByUsername("susp01")).thenReturn(suspendedUser);

        AuthenticationException ex = catchThrowableOfType(
                () -> sut.login("susp01", "pass123"),
                AuthenticationException.class
        );

        assertThat(ex.getReason()).isEqualTo(Reason.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("login - SUSPENDED account does not reach password failure")
    void login_suspended_doesNotReachPasswordCheck() {
        String rawPassword = "correctPass";
        NormalUser suspendedUser = normalBidder("susp02", rawPassword, AccountStatus.SUSPENDED, 1.0);
        when(userDAO.findUserByUsername("susp02")).thenReturn(suspendedUser);

        AuthenticationException ex = catchThrowableOfType(
                () -> sut.login("susp02", rawPassword),
                AuthenticationException.class
        );

        assertThat(ex.getReason()).isEqualTo(Reason.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("login - ACTIVE account with wrong password throws WRONG_PASSWORD")
    void login_activeAccount_wrongPassword_throwsWrongPassword() {
        NormalUser user = normalBidder("active01", "correctPass", AccountStatus.ACTIVE, 3.0);
        when(userDAO.findUserByUsername("active01")).thenReturn(user);

        AuthenticationException ex = catchThrowableOfType(
                () -> sut.login("active01", "wrongPass"),
                AuthenticationException.class
        );

        assertThat(ex.getReason()).isEqualTo(Reason.WRONG_PASSWORD);
    }

    @Test
    @DisplayName("login - empty password throws WRONG_PASSWORD")
    void login_emptyPassword_throwsWrongPassword() {
        NormalUser user = normalBidder("active02", "correctPass", AccountStatus.ACTIVE, 3.0);
        when(userDAO.findUserByUsername("active02")).thenReturn(user);

        AuthenticationException ex = catchThrowableOfType(
                () -> sut.login("active02", ""),
                AuthenticationException.class
        );

        assertThat(ex.getReason()).isEqualTo(Reason.WRONG_PASSWORD);
    }

    @Test
    @DisplayName("login - password comparison is case-sensitive")
    void login_passwordCaseSensitive_throwsWrongPassword() {
        NormalUser user = normalBidder("active03", "Password1", AccountStatus.ACTIVE, 3.0);
        when(userDAO.findUserByUsername("active03")).thenReturn(user);

        AuthenticationException ex = catchThrowableOfType(
                () -> sut.login("active03", "password1"),
                AuthenticationException.class
        );

        assertThat(ex.getReason()).isEqualTo(Reason.WRONG_PASSWORD);
    }

    @Test
    @DisplayName("login - USER_NOT_FOUND is checked before password")
    void login_checkOrder_notFoundBeforePassword() {
        when(userDAO.findUserByUsername("ghost")).thenReturn(null);

        AuthenticationException ex = catchThrowableOfType(
                () -> sut.login("ghost", "anyPass"),
                AuthenticationException.class
        );

        assertThat(ex.getReason()).isEqualTo(Reason.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("login - BANNED is checked before wrong password")
    void login_checkOrder_bannedBeforePassword() {
        NormalUser bannedUser = normalBidder("banned02", "realPass", AccountStatus.BANNED, 0.0);
        when(userDAO.findUserByUsername("banned02")).thenReturn(bannedUser);

        AuthenticationException ex = catchThrowableOfType(
                () -> sut.login("banned02", "wrongPass"),
                AuthenticationException.class
        );

        assertThat(ex.getReason()).isEqualTo(Reason.ACCOUNT_BANNED);
    }

    @Test
    @DisplayName("login - AuthenticationException has descriptive message")
    void login_exceptionContainsDescriptiveMessage() {
        when(userDAO.findUserByUsername("nobody")).thenReturn(null);

        AuthenticationException ex = catchThrowableOfType(
                () -> sut.login("nobody", "pass"),
                AuthenticationException.class
        );

        assertThat(ex.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("login - returns exact object from DAO")
    void login_returnsExactObjectFromDAO() {
        String rawPassword = "correctPass1";
        NormalUser expected = normalBidder("bidder99", rawPassword, AccountStatus.ACTIVE, 4.5);
        when(userDAO.findUserByUsername("bidder99")).thenReturn(expected);

        User result = sut.login("bidder99", rawPassword);

        assertThat(result).isSameAs(expected);
    }

    private static NormalUser normalBidder(String username, String rawPassword,
                                           AccountStatus status, double rating) {
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
                false,
                null
        );
    }
}
