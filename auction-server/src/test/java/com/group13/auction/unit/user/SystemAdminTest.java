package com.group13.auction.unit.user;

import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SystemAdmin")
class SystemAdminTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 10, 10, 0);

    @AfterEach
    void tearDown() throws Exception {
        resetSystemAdmin();
    }

    @Nested
    @DisplayName("singleton và elevated permission")
    class SingletonAndElevatedPermission {

        @Test
        @DisplayName("getInstance() khi chưa bootstrap thì ném IllegalStateException")
        void getInstance_withoutBootstrap_throwsIllegalStateException() throws Exception {
            // Arrange
            resetSystemAdmin();

            // Act & Assert
            assertThatThrownBy(SystemAdmin::getInstance)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bootstrap");
        }

        @Test
        @DisplayName("SystemAdmin có isSystem() true")
        void systemAdmin_isSystemTrue() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));

            // Act & Assert
            assertThat(systemAdmin.isSystem()).isTrue();
        }

        @Test
        @DisplayName("SystemAdmin có isMaster() true và isStaff() false")
        void systemAdmin_masterPermissionFlagsAreCorrect() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));

            // Act & Assert
            assertThat(systemAdmin.isMaster()).isTrue();
            assertThat(systemAdmin.isStaff()).isFalse();
            assertThat(systemAdmin.getAdminLevel()).isEqualTo(Admin.LEVEL_MASTER);
        }

        @Test
        @DisplayName("SystemAdmin có primaryRole là ADMIN")
        void systemAdmin_primaryRoleIsAdmin() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));

            // Act & Assert
            assertThat(systemAdmin.getPrimaryRole()).isEqualTo(User.UserRole.ADMIN);
            assertThat(systemAdmin.hasRole(User.UserRole.ADMIN)).isTrue();
        }

        @Test
        @DisplayName("SystemAdmin không có role BIDDER hoặc SELLER")
        void systemAdmin_hasOnlyAdminRole() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));

            // Act & Assert
            assertThat(systemAdmin.hasRole(User.UserRole.ADMIN)).isTrue();
            assertThat(systemAdmin.hasRole(User.UserRole.BIDDER)).isFalse();
            assertThat(systemAdmin.hasRole(User.UserRole.SELLER)).isFalse();
        }
    }

    @Nested
    @DisplayName("override behavior")
    class OverrideBehavior {

        @Test
        @DisplayName("addRole(SELLER) trên SystemAdmin bị chặn như Admin")
        void addRole_seller_throwsUnsupportedOperationException() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));

            // Act & Assert
            assertThatThrownBy(() -> systemAdmin.addRole(User.UserRole.SELLER))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Admin");
        }

        @Test
        @DisplayName("addRole(BIDDER) trên SystemAdmin không làm đổi role")
        void addRole_bidder_doesNotChangeRole() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));

            // Act
            assertThatThrownBy(() -> systemAdmin.addRole(User.UserRole.BIDDER))
                    .isInstanceOf(UnsupportedOperationException.class);

            // Assert
            assertThat(systemAdmin.getPrimaryRole()).isEqualTo(User.UserRole.ADMIN);
            assertThat(systemAdmin.hasRole(User.UserRole.BIDDER)).isFalse();
        }

        @Test
        @DisplayName("getRating() luôn là 5.0")
        void getRating_alwaysReturnsFixedAdminRating() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));

            // Act
            systemAdmin.adjustRating(-5.0);

            // Assert
            assertThat(systemAdmin.getRating()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("printInfo() không ném exception")
        void printInfo_doesNotThrow() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));

            // Act & Assert
            assertThatCode(systemAdmin::printInfo).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("autoBanIfNeeded()")
    class AutoBanIfNeeded {

        @Test
        @DisplayName("autoBanIfNeeded() với user null thì ném NullPointerException")
        void autoBanIfNeeded_nullUser_throwsNullPointerException() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);

            // Act & Assert
            assertThatThrownBy(() -> systemAdmin.autoBanIfNeeded(null))
                    .isInstanceOf(NullPointerException.class);

            verify(userDAO, never()).updateAccountStatus(null, User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }

        @Test
        @DisplayName("autoBanIfNeeded() với user ACTIVE rating dưới ngưỡng thì chuyển BANNED và persist")
        void autoBanIfNeeded_activeLowRatingUser_bansAndPersists() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            NormalUser user = normalBidder("bidder01", User.AccountStatus.ACTIVE, 1.9);
            when(userDAO.updateAccountStatus(user.getId(), User.AccountStatus.BANNED.name()))
                    .thenReturn(true);

            // Act
            systemAdmin.autoBanIfNeeded(user);

            // Assert
            assertThat(user.getAccountStatus()).isEqualTo(User.AccountStatus.BANNED);
            verify(userDAO, times(1)).updateAccountStatus(user.getId(), User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }

        @Test
        @DisplayName("autoBanIfNeeded() với user ACTIVE rating bằng ngưỡng thì không ban")
        void autoBanIfNeeded_ratingAtThreshold_doesNotBan() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            NormalUser user = normalBidder("bidder02", User.AccountStatus.ACTIVE, SystemAdmin.MIN_ELIGIBLE_RATING);

            // Act
            systemAdmin.autoBanIfNeeded(user);

            // Assert
            assertThat(user.getAccountStatus()).isEqualTo(User.AccountStatus.ACTIVE);
            verify(userDAO, never()).updateAccountStatus(user.getId(), User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }

        @Test
        @DisplayName("autoBanIfNeeded() với user đã BANNED thì bỏ qua và không persist thêm")
        void autoBanIfNeeded_alreadyBannedUser_doesNotPersistAgain() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            NormalUser user = normalBidder("bidder-already-banned", User.AccountStatus.BANNED, 0.5);

            // Act
            systemAdmin.autoBanIfNeeded(user);

            // Assert
            assertThat(user.getAccountStatus()).isEqualTo(User.AccountStatus.BANNED);
            verify(userDAO, never()).updateAccountStatus(user.getId(), User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }

        @Test
        @DisplayName("autoBanIfNeeded() với user đã SUSPENDED thì không đổi state")
        void autoBanIfNeeded_suspendedUser_doesNotChangeState() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            NormalUser user = normalBidder("bidder03", User.AccountStatus.SUSPENDED, 1.0);

            // Act
            systemAdmin.autoBanIfNeeded(user);

            // Assert
            assertThat(user.getAccountStatus()).isEqualTo(User.AccountStatus.SUSPENDED);
            verify(userDAO, never()).updateAccountStatus(user.getId(), User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }

        @Test
        @DisplayName("autoBanIfNeeded() với Admin thì bỏ qua")
        void autoBanIfNeeded_adminTarget_isIgnored() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            Admin staff = staffAdmin("staff01", User.AccountStatus.ACTIVE);

            // Act
            systemAdmin.autoBanIfNeeded(staff);

            // Assert
            assertThat(staff.getAccountStatus()).isEqualTo(User.AccountStatus.ACTIVE);
            verify(userDAO, never()).updateAccountStatus(staff.getId(), User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }

        @Test
        @DisplayName("autoBanIfNeeded() vẫn ban in-memory khi UserDAO trả false")
        void autoBanIfNeeded_daoUpdateFails_stillBansInMemory() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            NormalUser user = normalBidder("bidder04", User.AccountStatus.ACTIVE, 0.5);
            when(userDAO.updateAccountStatus(user.getId(), User.AccountStatus.BANNED.name()))
                    .thenReturn(false);

            // Act
            systemAdmin.autoBanIfNeeded(user);

            // Assert
            assertThat(user.getAccountStatus()).isEqualTo(User.AccountStatus.BANNED);
            verify(userDAO, times(1)).updateAccountStatus(user.getId(), User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }
    }

    @Nested
    @DisplayName("banUserByStaff()")
    class BanUserByStaff {

        @Test
        @DisplayName("banUserByStaff() với staff null thì vẫn ban target nhưng ném NullPointerException khi ghi log")
        void banUserByStaff_nullStaff_bansTargetThenThrowsNullPointerException() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            NormalUser target = normalBidder("bidder-null-staff", User.AccountStatus.ACTIVE, 3.0);

            // Act & Assert
            assertThatThrownBy(() -> systemAdmin.banUserByStaff(null, target, Admin.BanReason.LOW_RATING))
                    .isInstanceOf(NullPointerException.class);

            assertThat(target.getAccountStatus()).isEqualTo(User.AccountStatus.BANNED);
            verify(userDAO, never()).updateAccountStatus(target.getId(), User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }

        @Test
        @DisplayName("banUserByStaff() với target null thì ném NullPointerException")
        void banUserByStaff_nullTarget_throwsNullPointerException() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            Admin staff = staffAdmin("staff-null-target", User.AccountStatus.ACTIVE);

            // Act & Assert
            assertThatThrownBy(() -> systemAdmin.banUserByStaff(staff, null, Admin.BanReason.LOW_RATING))
                    .isInstanceOf(NullPointerException.class);

            assertThat(staff.getActionLog()).isEmpty();
            verify(userDAO, never()).updateAccountStatus(null, User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }

        @Test
        @DisplayName("banUserByStaff() với reason null vẫn ban target và ghi log null")
        void banUserByStaff_nullReason_bansTargetAndLogsNullReason() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            Admin staff = staffAdmin("staff-null-reason", User.AccountStatus.ACTIVE);
            NormalUser target = normalBidder("bidder-null-reason", User.AccountStatus.ACTIVE, 3.0);

            // Act
            systemAdmin.banUserByStaff(staff, target, null);

            // Assert
            assertThat(target.getAccountStatus()).isEqualTo(User.AccountStatus.BANNED);
            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(staff.getActionLog().get(0)).contains("null");
            assertThat(systemAdmin.getActionLog()).hasSize(1);
            assertThat(systemAdmin.getActionLog().get(0)).contains("null");
            verify(userDAO, times(1)).updateAccountStatus(target.getId(), User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }

        @Test
        @DisplayName("banUserByStaff() với userDAO null vẫn ban in-memory và ghi log")
        void banUserByStaff_nullUserDao_bansInMemoryAndLogs() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(null);
            Admin staff = staffAdmin("staff-no-dao", User.AccountStatus.ACTIVE);
            NormalUser target = normalBidder("bidder-no-dao", User.AccountStatus.ACTIVE, 3.0);

            // Act
            systemAdmin.banUserByStaff(staff, target, Admin.BanReason.LOW_RATING);

            // Assert
            assertThat(target.getAccountStatus()).isEqualTo(User.AccountStatus.BANNED);
            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(systemAdmin.getActionLog()).hasSize(1);
        }

        @Test
        @DisplayName("banUserByStaff() với NormalUser thì chuyển BANNED và persist")
        void banUserByStaff_normalUser_bansAndPersists() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            Admin staff = staffAdmin("staff10", User.AccountStatus.ACTIVE);
            NormalUser target = normalBidder("bidder10", User.AccountStatus.ACTIVE, 3.0);
            when(userDAO.updateAccountStatus(target.getId(), User.AccountStatus.BANNED.name()))
                    .thenReturn(true);

            // Act
            systemAdmin.banUserByStaff(staff, target, Admin.BanReason.LOW_RATING);

            // Assert
            assertThat(target.getAccountStatus()).isEqualTo(User.AccountStatus.BANNED);
            verify(userDAO, times(1)).updateAccountStatus(target.getId(), User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }

        @Test
        @DisplayName("banUserByStaff() ghi action log cho staff")
        void banUserByStaff_recordsStaffActionLog() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));
            Admin staff = staffAdmin("staff11", User.AccountStatus.ACTIVE);
            NormalUser target = normalBidder("bidder11", User.AccountStatus.ACTIVE, 3.0);

            // Act
            systemAdmin.banUserByStaff(staff, target, Admin.BanReason.LOW_RATING);

            // Assert
            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(staff.getActionLog().get(0))
                    .contains(staff.getUsername())
                    .contains(target.getUsername())
                    .contains(Admin.BanReason.LOW_RATING.name());
        }

        @Test
        @DisplayName("banUserByStaff() ghi audit log cho SystemAdmin")
        void banUserByStaff_recordsSystemAuditLog() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));
            Admin staff = staffAdmin("staff12", User.AccountStatus.ACTIVE);
            NormalUser target = normalBidder("bidder12", User.AccountStatus.ACTIVE, 3.0);

            // Act
            systemAdmin.banUserByStaff(staff, target, Admin.BanReason.SELLER_REFUND_DEFAULT);

            // Assert
            assertThat(systemAdmin.getActionLog()).hasSize(1);
            assertThat(systemAdmin.getActionLog().get(0))
                    .contains(staff.getUsername())
                    .contains(target.getUsername())
                    .contains(Admin.BanReason.SELLER_REFUND_DEFAULT.name());
        }

        @Test
        @DisplayName("banUserByStaff() với target Admin thì bỏ qua")
        void banUserByStaff_adminTarget_isIgnored() throws Exception {
            // Arrange
            UserDAO userDAO = mock(UserDAO.class);
            SystemAdmin systemAdmin = systemAdmin(userDAO);
            Admin staff = staffAdmin("staff13", User.AccountStatus.ACTIVE);
            Admin target = staffAdmin("staffTarget", User.AccountStatus.ACTIVE);

            // Act
            systemAdmin.banUserByStaff(staff, target, Admin.BanReason.LOW_RATING);

            // Assert
            assertThat(target.getAccountStatus()).isEqualTo(User.AccountStatus.ACTIVE);
            assertThat(staff.getActionLog()).isEmpty();
            assertThat(systemAdmin.getActionLog()).isEmpty();
            verify(userDAO, never()).updateAccountStatus(target.getId(), User.AccountStatus.BANNED.name());
            verifyNoMoreInteractions(userDAO);
        }
    }

    @Nested
    @DisplayName("inherited behavior và polymorphism")
    class InheritedBehaviorAndPolymorphism {

        @Test
        @DisplayName("SystemAdmin dùng qua kiểu Admin vẫn có isSystem() true")
        void systemAdminAsAdmin_keepsSystemIdentity() throws Exception {
            // Arrange
            Admin admin = systemAdmin(mock(UserDAO.class));

            // Act & Assert
            assertThat(admin.isSystem()).isTrue();
            assertThat(admin.isMaster()).isTrue();
            assertThat(admin.isStaff()).isFalse();
        }

        @Test
        @DisplayName("SystemAdmin dùng qua kiểu User vẫn giữ primaryRole ADMIN")
        void systemAdminAsUser_keepsAdminRole() throws Exception {
            // Arrange
            User user = systemAdmin(mock(UserDAO.class));

            // Act & Assert
            assertThat(user).isInstanceOf(SystemAdmin.class);
            assertThat(user.getPrimaryRole()).isEqualTo(User.UserRole.ADMIN);
            assertThat(user.hasRole(User.UserRole.ADMIN)).isTrue();
        }

        @Test
        @DisplayName("SystemAdmin dùng qua kiểu User vẫn chặn addRole()")
        void systemAdminAsUser_addRoleStillThrows() throws Exception {
            // Arrange
            User user = systemAdmin(mock(UserDAO.class));

            // Act & Assert
            assertThatThrownBy(() -> user.addRole(User.UserRole.SELLER))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("setAccountStatus(SUSPENDED) kế thừa từ User vẫn cập nhật suspendedAt")
        void setAccountStatus_suspended_setsSuspendedAt() throws Exception {
            // Arrange
            SystemAdmin systemAdmin = systemAdmin(mock(UserDAO.class));

            // Act
            systemAdmin.setAccountStatus(User.AccountStatus.SUSPENDED);

            // Assert
            assertThat(systemAdmin.getAccountStatus()).isEqualTo(User.AccountStatus.SUSPENDED);
            assertThat(systemAdmin.getSuspendedAt()).isNotNull();
        }
    }

    private static SystemAdmin systemAdmin(UserDAO userDAO) throws Exception {
        Constructor<SystemAdmin> constructor = SystemAdmin.class
                .getDeclaredConstructor(String.class, String.class, String.class);
        constructor.setAccessible(true);
        SystemAdmin systemAdmin = constructor.newInstance("SYSTEM", "systemPass1", "system@test.com");

        Field userDaoField = SystemAdmin.class.getDeclaredField("userDAO");
        userDaoField.setAccessible(true);
        userDaoField.set(systemAdmin, userDAO);

        Field instanceField = SystemAdmin.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, systemAdmin);

        return systemAdmin;
    }

    private static void resetSystemAdmin() throws Exception {
        Field instanceField = SystemAdmin.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private static Admin staffAdmin(String username, User.AccountStatus status) {
        return Admin.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(1),
                NOW.minusDays(1),
                username,
                User.hashPassword("adminPass1"),
                username + "@test.com",
                status,
                5.0,
                Admin.LEVEL_STAFF,
                null);
    }

    private static NormalUser normalBidder(String username, User.AccountStatus status, double rating) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(1),
                NOW.minusDays(1),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                status,
                rating,
                0L,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                false,
                0,
                status == User.AccountStatus.SUSPENDED ? NOW.minusDays(1) : null);
    }
}
