package com.group13.auction.user;

import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Admin")
class AdminTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 5, 10, 10, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 5, 10, 10, 5);

    @Nested
    @DisplayName("khởi tạo và role consistency")
    class InitializationAndRoleConsistency {

        @Test
        @DisplayName("reconstitute() với LEVEL_STAFF thì primaryRole là ADMIN")
        void reconstitute_staffAdmin_primaryRoleIsAdmin() {
            // Arrange & Act
            Admin admin = staffAdmin("staff01");

            // Assert
            assertThat(admin.getPrimaryRole()).isEqualTo(User.UserRole.ADMIN);
            assertThat(admin.hasRole(User.UserRole.ADMIN)).isTrue();
        }

        @Test
        @DisplayName("reconstitute() giữ đúng adminLevel")
        void reconstitute_keepsAdminLevel() {
            // Arrange & Act
            Admin staff = staffAdmin("staff02");
            Admin master = masterAdmin("master01");

            // Assert
            assertThat(staff.getAdminLevel()).isEqualTo(Admin.LEVEL_STAFF);
            assertThat(master.getAdminLevel()).isEqualTo(Admin.LEVEL_MASTER);
        }

        @Test
        @DisplayName("Admin STAFF thì isStaff() true và isMaster() false")
        void staffAdmin_permissionFlags_areCorrect() {
            // Arrange
            Admin admin = staffAdmin("staff03");

            // Act & Assert
            assertThat(admin.isStaff()).isTrue();
            assertThat(admin.isMaster()).isFalse();
            assertThat(admin.isSystem()).isFalse();
        }

        @Test
        @DisplayName("Admin MASTER thì isMaster() true và isStaff() false")
        void masterAdmin_permissionFlags_areCorrect() {
            // Arrange
            Admin admin = masterAdmin("master02");

            // Act & Assert
            assertThat(admin.isMaster()).isTrue();
            assertThat(admin.isStaff()).isFalse();
            assertThat(admin.isSystem()).isFalse();
        }

        @Test
        @DisplayName("Admin không có role BIDDER hoặc SELLER")
        void admin_hasOnlyAdminRole() {
            // Arrange
            Admin admin = staffAdmin("staff04");

            // Act & Assert
            assertThat(admin.hasRole(User.UserRole.ADMIN)).isTrue();
            assertThat(admin.hasRole(User.UserRole.BIDDER)).isFalse();
            assertThat(admin.hasRole(User.UserRole.SELLER)).isFalse();
        }

        @Test
        @DisplayName("Admin giữ đúng username, email và accountStatus sau reconstitute()")
        void reconstitute_keepsIdentityAndStatus() {
            // Arrange
            String username = "staff05";
            String email = "staff05@test.com";

            // Act
            Admin admin = Admin.reconstitute(
                    UUID.randomUUID().toString(),
                    CREATED_AT,
                    UPDATED_AT,
                    username,
                    User.hashPassword("adminPass1"),
                    email,
                    User.AccountStatus.SUSPENDED,
                    1.0,
                    Admin.LEVEL_STAFF,
                    CREATED_AT.plusDays(1));

            // Assert
            assertThat(admin.getUsername()).isEqualTo(username);
            assertThat(admin.getEmail()).isEqualTo(email);
            assertThat(admin.getAccountStatus()).isEqualTo(User.AccountStatus.SUSPENDED);
            assertThat(admin.getSuspendedAt()).isEqualTo(CREATED_AT.plusDays(1));
        }
    }

    @Nested
    @DisplayName("permission và invalid action")
    class PermissionAndInvalidAction {

        @Test
        @DisplayName("addRole(BIDDER) trên Admin thì ném UnsupportedOperationException")
        void addRole_bidder_throwsUnsupportedOperationException() {
            // Arrange
            Admin admin = staffAdmin("staff10");

            // Act & Assert
            assertThatThrownBy(() -> admin.addRole(User.UserRole.BIDDER))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Admin");
        }

        @Test
        @DisplayName("addRole(SELLER) trên Admin thì ném UnsupportedOperationException")
        void addRole_seller_throwsUnsupportedOperationException() {
            // Arrange
            Admin admin = staffAdmin("staff11");

            // Act & Assert
            assertThatThrownBy(() -> admin.addRole(User.UserRole.SELLER))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("ADMIN");
        }

        @Test
        @DisplayName("addRole(ADMIN) trên Admin vẫn bị chặn để giữ role integrity")
        void addRole_admin_throwsUnsupportedOperationException() {
            // Arrange
            Admin admin = staffAdmin("staff12");

            // Act & Assert
            assertThatThrownBy(() -> admin.addRole(User.UserRole.ADMIN))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("addRole() thất bại không làm đổi primaryRole")
        void addRole_failureDoesNotChangePrimaryRole() {
            // Arrange
            Admin admin = staffAdmin("staff13");
            User.UserRole roleBefore = admin.getPrimaryRole();

            // Act
            assertThatThrownBy(() -> admin.addRole(User.UserRole.SELLER))
                    .isInstanceOf(UnsupportedOperationException.class);

            // Assert
            assertThat(admin.getPrimaryRole()).isEqualTo(roleBefore);
            assertThat(admin.hasRole(User.UserRole.ADMIN)).isTrue();
            assertThat(admin.hasRole(User.UserRole.SELLER)).isFalse();
        }
    }

    @Nested
    @DisplayName("override behavior")
    class OverrideBehavior {

        @Test
        @DisplayName("getRating() luôn trả về 5.0 dù rating reconstitute thấp hơn")
        void getRating_alwaysReturnsFixedAdminRating() {
            // Arrange
            Admin admin = Admin.reconstitute(
                    UUID.randomUUID().toString(),
                    CREATED_AT,
                    UPDATED_AT,
                    "staff20",
                    User.hashPassword("adminPass1"),
                    "staff20@test.com",
                    User.AccountStatus.ACTIVE,
                    0.5,
                    Admin.LEVEL_STAFF,
                    null);

            // Act & Assert
            assertThat(admin.getRating()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("adjustRating() không làm thay đổi rating cố định của Admin")
        void adjustRating_doesNotChangeAdminRating() {
            // Arrange
            Admin admin = staffAdmin("staff21");

            // Act
            admin.adjustRating(-5.0);
            admin.adjustRating(10.0);

            // Assert
            assertThat(admin.getRating()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("adjustRating() không thay đổi accountStatus")
        void adjustRating_doesNotChangeAccountStatus() {
            // Arrange
            Admin admin = staffAdmin("staff22");

            // Act
            admin.adjustRating(-5.0);

            // Assert
            assertThat(admin.getAccountStatus()).isEqualTo(User.AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("printInfo() không ném exception")
        void printInfo_doesNotThrow() {
            // Arrange
            Admin admin = staffAdmin("staff23");

            // Act & Assert
            assertThatCode(admin::printInfo).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("inherited User behavior")
    class InheritedUserBehavior {

        @Test
        @DisplayName("setAccountStatus(SUSPENDED) cập nhật status và suspendedAt")
        void setAccountStatus_suspended_setsSuspendedAt() {
            // Arrange
            Admin admin = staffAdmin("staff30");

            // Act
            admin.setAccountStatus(User.AccountStatus.SUSPENDED);

            // Assert
            assertThat(admin.getAccountStatus()).isEqualTo(User.AccountStatus.SUSPENDED);
            assertThat(admin.getSuspendedAt()).isNotNull();
        }

        @Test
        @DisplayName("setAccountStatus(null) ném NullPointerException")
        void setAccountStatus_null_throwsNullPointerException() {
            // Arrange
            Admin admin = staffAdmin("staff31");

            // Act & Assert
            assertThatThrownBy(() -> admin.setAccountStatus(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Status");
        }

        @Test
        @DisplayName("addJoinedAuction() kế thừa từ User hoạt động và idempotent theo Set")
        void addJoinedAuction_inheritedBehavior_isIdempotent() {
            // Arrange
            Admin admin = staffAdmin("staff32");
            String auctionId = "auction-1";

            // Act
            admin.addJoinedAuction(auctionId);
            admin.addJoinedAuction(auctionId);

            // Assert
            assertThat(admin.hasJoined(auctionId)).isTrue();
            assertThat(admin.getJoinedAuctionIds()).containsExactly(auctionId);
        }

        @Test
        @DisplayName("addToWatchList() kế thừa từ User không thêm trùng auctionId")
        void addToWatchList_inheritedBehavior_doesNotDuplicate() {
            // Arrange
            Admin admin = staffAdmin("staff33");
            String auctionId = "auction-watch-1";

            // Act
            admin.addToWatchList(auctionId);
            admin.addToWatchList(auctionId);

            // Assert
            assertThat(admin.getWatchListAuctionIds()).containsExactly(auctionId);
        }
    }

    @Nested
    @DisplayName("encapsulation")
    class Encapsulation {

        @Test
        @DisplayName("getActionLog() trả về unmodifiable list")
        void getActionLog_returnsUnmodifiableList() {
            // Arrange
            Admin admin = staffAdmin("staff40");
            admin.addActionLog("log-1");

            // Act
            List<String> logs = admin.getActionLog();

            // Assert
            assertThatThrownBy(() -> logs.add("log-2"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(admin.getActionLog()).containsExactly("log-1");
        }

        @Test
        @DisplayName("getJoinedAuctionIds() trả về unmodifiable set")
        void getJoinedAuctionIds_returnsUnmodifiableSet() {
            // Arrange
            Admin admin = staffAdmin("staff41");
            admin.addJoinedAuction("auction-1");

            // Act
            Set<String> joinedAuctionIds = admin.getJoinedAuctionIds();

            // Assert
            assertThatThrownBy(() -> joinedAuctionIds.add("auction-2"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(admin.getJoinedAuctionIds()).containsExactly("auction-1");
        }

        @Test
        @DisplayName("getWatchListAuctionIds() trả về unmodifiable list")
        void getWatchListAuctionIds_returnsUnmodifiableList() {
            // Arrange
            Admin admin = staffAdmin("staff42");
            admin.addToWatchList("auction-1");

            // Act
            List<String> watchList = admin.getWatchListAuctionIds();

            // Assert
            assertThatThrownBy(() -> watchList.add("auction-2"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(admin.getWatchListAuctionIds()).containsExactly("auction-1");
        }
    }

    @Nested
    @DisplayName("polymorphism behavior")
    class PolymorphismBehavior {

        @Test
        @DisplayName("Admin dùng qua kiểu User vẫn giữ role ADMIN")
        void adminAsUser_keepsAdminRole() {
            // Arrange
            User user = staffAdmin("staff50");

            // Act & Assert
            assertThat(user).isInstanceOf(Admin.class);
            assertThat(user.getPrimaryRole()).isEqualTo(User.UserRole.ADMIN);
            assertThat(user.hasRole(User.UserRole.ADMIN)).isTrue();
        }

        @Test
        @DisplayName("Admin dùng qua kiểu User vẫn chặn addRole()")
        void adminAsUser_addRoleStillThrows() {
            // Arrange
            User user = staffAdmin("staff51");

            // Act & Assert
            assertThatThrownBy(() -> user.addRole(User.UserRole.SELLER))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Admin dùng qua kiểu User vẫn có rating cố định 5.0")
        void adminAsUser_ratingStillFixed() {
            // Arrange
            User user = Admin.reconstitute(
                    UUID.randomUUID().toString(),
                    CREATED_AT,
                    UPDATED_AT,
                    "staff52",
                    User.hashPassword("adminPass1"),
                    "staff52@test.com",
                    User.AccountStatus.ACTIVE,
                    1.0,
                    Admin.LEVEL_STAFF,
                    null);

            // Act
            user.adjustRating(-3.0);

            // Assert
            assertThat(user.getRating()).isEqualTo(5.0);
        }
    }

    @Nested
    @DisplayName("moderation capability enums")
    class ModerationCapabilityEnums {

        @Test
        @DisplayName("BanReason có LOW_RATING và SELLER_REFUND_DEFAULT")
        void banReason_containsSupportedModerationReasons() {
            // Act & Assert
            assertThat(Admin.BanReason.values())
                    .contains(Admin.BanReason.LOW_RATING, Admin.BanReason.SELLER_REFUND_DEFAULT);
        }

        @Test
        @DisplayName("CancelReason có các lý do moderation cần thiết")
        void cancelReason_containsSupportedModerationReasons() {
            // Act & Assert
            assertThat(Admin.CancelReason.values())
                    .contains(
                            Admin.CancelReason.NO_WINNER,
                            Admin.CancelReason.RESERVE_NOT_MET,
                            Admin.CancelReason.SELLER_REQUEST,
                            Admin.CancelReason.SYSTEM_ERROR,
                            Admin.CancelReason.FRAUDULENT_ITEM);
        }
    }

    private static Admin staffAdmin(String username) {
        return Admin.reconstitute(
                UUID.randomUUID().toString(),
                CREATED_AT,
                UPDATED_AT,
                username,
                User.hashPassword("adminPass1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                2.0,
                Admin.LEVEL_STAFF,
                null);
    }

    private static Admin masterAdmin(String username) {
        return Admin.reconstitute(
                UUID.randomUUID().toString(),
                CREATED_AT,
                UPDATED_AT,
                username,
                User.hashPassword("adminPass1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                2.0,
                Admin.LEVEL_MASTER,
                null);
    }
}
