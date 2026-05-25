package com.group13.auction.unit.service;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.dao.AdminDAO;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.SellerDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link AccountService}.
 *
 * <p>SUT được tạo với các DAO mock và IRatingService mock/fake.
 * Mỗi test chỉ verify đúng business interaction quan trọng,
 * không verify mọi method call không liên quan đến behavior đang test.
 *
 * <p>Không DB, không network, không filesystem.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService")
class AccountServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────

    @Mock IRatingService ratingService;
    @Mock UserDAO        userDAO;
    @Mock SellerDAO      sellerDAO;
    @Mock AdminDAO       adminDAO;
    @Mock AuctionDAO     auctionDAO;
    @Mock AuctionWinnerDAO auctionWinnerDAO;

    // ── SUT ──────────────────────────────────────────────────────────────────

    AccountService sut;

    // ── Shared admin fixture ──────────────────────────────────────────────────

    Admin staffAdmin;

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();

        sut = new AccountService(
                ratingService,
                userDAO,
                sellerDAO,
                adminDAO,
                auctionDAO,
                auctionWinnerDAO);

        staffAdmin = Admin.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                "staffAdmin",
                User.hashPassword("adminPass1"),
                "staff@test.com",
                AccountStatus.ACTIVE,
                5.0,
                Admin.LEVEL_STAFF,
                null);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        TestFixture.resetSystemAdmin();
    }

    // =========================================================================
    // deposit
    // =========================================================================

    @Nested
    @DisplayName("deposit()")
    class Deposit {

        private void allowWallet(NormalUser user) {
            when(ratingService.isWalletOperationAllowed(user)).thenReturn(true);
        }

        private void denyWallet(NormalUser user) {
            when(ratingService.isWalletOperationAllowed(user)).thenReturn(false);
        }

        // --- Happy path ---

        @Test
        @DisplayName("deposit hợp lệ → balance tăng đúng amount")
        void validDeposit_balanceIncreases() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderAA1", 500_000L);
            allowWallet(user);

            // Act
            sut.deposit(user, 300_000L);

            // Assert
            assertEquals(800_000L, user.getBalance());
        }

        @Test
        @DisplayName("deposit hợp lệ → gọi userDAO.addBalance với đúng userId và amount")
        void validDeposit_persistsToDAO() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderBB2", 0L);
            allowWallet(user);

            // Act
            sut.deposit(user, 1_000_000L);

            // Assert — interaction quan trọng: tiền phải được persist
            verify(userDAO).addBalance(eq(user.getId()), eq(1_000_000L));
        }

        @Test
        @DisplayName("deposit nhiều lần → balance cộng dồn đúng")
        void multipleDeposits_balanceAccumulates() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderCC3", 0L);
            allowWallet(user);

            // Act
            sut.deposit(user, 500_000L);
            sut.deposit(user, 300_000L);

            // Assert
            assertEquals(800_000L, user.getBalance());
        }

        // --- Invalid amount ---

        @Test
        @DisplayName("deposit zero → IllegalArgumentException, balance không đổi")
        void zeroAmount_throwsIllegalArgumentException() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderDD4", 500_000L);
            allowWallet(user);
            long balanceBefore = user.getBalance();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> sut.deposit(user, 0L));
            assertEquals(balanceBefore, user.getBalance());
        }

        @Test
        @DisplayName("deposit negative → IllegalArgumentException, balance không đổi")
        void negativeAmount_throwsIllegalArgumentException() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderEE5", 500_000L);
            allowWallet(user);
            long balanceBefore = user.getBalance();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> sut.deposit(user, -100_000L));
            assertEquals(balanceBefore, user.getBalance());
        }

        @Test
        @DisplayName("deposit invalid amount → không gọi userDAO (không persist)")
        void invalidAmount_noDaoPersist() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderFF6", 500_000L);
            allowWallet(user);

            // Act
            assertThrows(IllegalArgumentException.class, () -> sut.deposit(user, 0L));

            // Assert — không được gọi persist khi invalid
            verify(userDAO, never()).addBalance(anyString(), anyLong());
        }

        // --- Ineligible account ---

        @Test
        @DisplayName("user SUSPENDED (restricted) → deposit vẫn được phép")
        void suspendedUser_canDeposit() {
            NormalUser user = TestFixture.suspendedBidder("bidderGG7");
            allowWallet(user);

            sut.deposit(user, 500_000L);

            assertEquals(500_000L, user.getBalance());
            verify(userDAO).addBalance(user.getId(), 500_000L);
        }

        @Test
        @DisplayName("user BANNED (restricted) → deposit vẫn được phép")
        void bannedUser_canDeposit() {
            NormalUser user = TestFixture.bannedBidder("bidderHH8");
            allowWallet(user);

            sut.deposit(user, 500_000L);

            assertEquals(500_000L, user.getBalance());
            verify(userDAO).addBalance(user.getId(), 500_000L);
        }

        @Test
        @DisplayName("wallet denied + zero amount → IllegalStateException trước amount check")
        void walletDeniedCheckedBeforeAmount() {
            NormalUser user = TestFixture.bidderWithBalance("bidderII9", 0L);
            denyWallet(user);

            assertThrows(IllegalStateException.class, () -> sut.deposit(user, 0L));
        }
    }

    // =========================================================================
    // withdraw
    // =========================================================================

    @Nested
    @DisplayName("withdraw()")
    class Withdraw {

        private void allowWallet(NormalUser user) {
            when(ratingService.isWalletOperationAllowed(user)).thenReturn(true);
        }

        private void denyWallet(NormalUser user) {
            when(ratingService.isWalletOperationAllowed(user)).thenReturn(false);
        }

        // --- Happy path ---

        @Test
        @DisplayName("withdraw hợp lệ → balance giảm đúng amount")
        void validWithdraw_balanceDecreases() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderJJ0", 1_000_000L);
            allowWallet(user);

            // Act
            sut.withdraw(user, 400_000L);

            // Assert
            assertEquals(600_000L, user.getBalance());
        }

        @Test
        @DisplayName("withdraw hợp lệ → gọi userDAO.updateBalances với balance và lockedDeposit chính xác")
        void validWithdraw_persistsBalanceAndLockedDeposit() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderKK1", 1_000_000L);
            allowWallet(user);

            // Act
            sut.withdraw(user, 400_000L);

            // Assert — persist balance mới (600_000) và locked (0)
            verify(userDAO).updateBalances(user.getId(), 600_000L, 0L);
        }

        @Test
        @DisplayName("withdraw đúng bằng available balance → balance = 0, thành công")
        void withdrawExactAvailableBalance_succeeds() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderLL2", 500_000L);
            allowWallet(user);

            // Act
            sut.withdraw(user, 500_000L);

            // Assert
            assertEquals(0L, user.getBalance());
        }

        @Test
        @DisplayName("withdraw khi có lockedDeposit → chỉ rút được phần available")
        void withdrawWithLockedDeposit_onlyAvailableWithdrawn() {
            // Arrange — balance = 1_000_000, lock 300_000 → available = 700_000
            NormalUser user = TestFixture.bidderWithBalance("bidderMM3", 1_000_000L);
            user.lockDeposit(300_000L);
            allowWallet(user);

            // Act
            sut.withdraw(user, 700_000L);

            // Assert — balance còn 300_000, locked vẫn 300_000
            assertEquals(300_000L, user.getBalance());
            assertEquals(300_000L, user.getLockedDeposit());
        }

        // --- Insufficient balance ---

        @Test
        @DisplayName("withdraw vượt available balance → IllegalArgumentException")
        void insufficientBalance_throwsIllegalArgumentException() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderNN4", 200_000L);
            allowWallet(user);

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> sut.withdraw(user, 300_000L));
        }

        @Test
        @DisplayName("insufficient balance → balance không thay đổi (rollback hoặc không thực hiện)")
        void insufficientBalance_balanceUnchanged() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderOO5", 200_000L);
            allowWallet(user);

            // Act
            assertThrows(IllegalArgumentException.class, () -> sut.withdraw(user, 300_000L));

            // Assert — balance giữ nguyên
            assertEquals(200_000L, user.getBalance());
        }

        @Test
        @DisplayName("insufficient balance → không persist DAO")
        void insufficientBalance_noPersist() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderPP6", 200_000L);
            allowWallet(user);

            // Act
            assertThrows(IllegalArgumentException.class, () -> sut.withdraw(user, 300_000L));

            // Assert — không gọi updateBalances khi thất bại
            verify(userDAO, never()).updateBalances(anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("withdraw vượt available (có locked) → IllegalArgumentException")
        void withdrawExceedsAvailableDueToLock_throwsException() {
            // Arrange — balance = 1_000_000, lock 800_000 → available = 200_000
            NormalUser user = TestFixture.bidderWithBalance("bidderQQ7", 1_000_000L);
            user.lockDeposit(800_000L);
            allowWallet(user);

            // Act & Assert — cố rút 300_000 > 200_000 available
            assertThrows(IllegalArgumentException.class, () -> sut.withdraw(user, 300_000L));
        }

        // --- Invalid amount ---

        @Test
        @DisplayName("withdraw zero → IllegalArgumentException")
        void zeroAmount_throwsIllegalArgumentException() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderRR8", 500_000L);
            allowWallet(user);

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> sut.withdraw(user, 0L));
        }

        @Test
        @DisplayName("withdraw negative → IllegalArgumentException")
        void negativeAmount_throwsIllegalArgumentException() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderSS9", 500_000L);
            allowWallet(user);

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> sut.withdraw(user, -50_000L));
        }

        // --- Ineligible account ---

        @Test
        @DisplayName("wallet denied → IllegalStateException trước khi kiểm tra amount")
        void walletDenied_throwsIllegalStateException() {
            NormalUser user = TestFixture.bidderWithBalance("bidderTT0", 500_000L);
            denyWallet(user);

            assertThrows(IllegalStateException.class, () -> sut.withdraw(user, 100_000L));
        }

        @Test
        @DisplayName("user BANNED (restricted) → withdraw vẫn được phép")
        void bannedUser_canWithdraw() {
            NormalUser user = TestFixture.bannedBidder("bidderUU1");
            user.setBalance(500_000L);
            allowWallet(user);

            sut.withdraw(user, 100_000L);

            assertEquals(400_000L, user.getBalance());
            verify(userDAO).updateBalances(user.getId(), 400_000L, user.getLockedDeposit());
        }

        // --- persist correctness via ArgumentCaptor ---

        @Test
        @DisplayName("persist: updateBalances nhận đúng userId, balance mới, lockedDeposit hiện tại")
        void persistCallReceivesCorrectArguments() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderVV2", 2_000_000L);
            user.lockDeposit(500_000L); // locked = 500_000, available = 1_500_000
            allowWallet(user);

            ArgumentCaptor<String> idCaptor     = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Long>   balCaptor    = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long>   lockedCaptor = ArgumentCaptor.forClass(Long.class);

            // Act
            sut.withdraw(user, 1_000_000L);

            // Assert — balance = 2_000_000 - 1_000_000 = 1_000_000; locked = 500_000
            verify(userDAO).updateBalances(
                    idCaptor.capture(), balCaptor.capture(), lockedCaptor.capture());
            assertEquals(user.getId(), idCaptor.getValue());
            assertEquals(1_000_000L, balCaptor.getValue());
            assertEquals(500_000L,   lockedCaptor.getValue());
        }
    }

    // =========================================================================
    // autoApproveSellerRole
    // =========================================================================

    @Nested
    @DisplayName("autoApproveSellerRole()")
    class AutoApproveSellerRole {

        // --- Happy path ---

        @Test
        @DisplayName("user đủ điều kiện → được cấp role SELLER")
        void eligibleUser_getsSellerRole() {
            // Arrange — ACTIVE, chưa penalized, chưa có SELLER
            NormalUser user = TestFixture.normalBidder("bidderWW3");
            when(ratingService.isEligible(user)).thenReturn(true);

            // Act
            sut.autoApproveSellerRole(user);

            // Assert
            assertTrue(user.hasRole(User.UserRole.SELLER));
        }

        @Test
        @DisplayName("user đủ điều kiện → gọi sellerDAO.approveSellerRole với đúng userId")
        void eligibleUser_persistsSellerRole() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderXX4");
            when(ratingService.isEligible(user)).thenReturn(true);

            // Act
            sut.autoApproveSellerRole(user);

            // Assert — interaction quan trọng: role phải được persist
            verify(sellerDAO).approveSellerRole(user.getId());
        }

        @Test
        @DisplayName("user đủ điều kiện → giữ nguyên role BIDDER (không mất role cũ)")
        void eligibleUser_retainsBidderRole() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderYY5");
            when(ratingService.isEligible(user)).thenReturn(true);

            // Act
            sut.autoApproveSellerRole(user);

            // Assert
            assertTrue(user.hasRole(User.UserRole.BIDDER));
        }

        // --- Seller eligibility conditions ---

        @Test
        @DisplayName("user ineligible (SUSPENDED/low rating) → IllegalStateException")
        void ineligibleUser_throwsIllegalStateException() {
            // Arrange
            NormalUser user = TestFixture.suspendedBidder("bidderZZ6");
            when(ratingService.isEligible(user)).thenReturn(false);

            // Act & Assert
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> sut.autoApproveSellerRole(user));
            assertNotNull(ex.getMessage());
        }

        @Test
        @DisplayName("user ineligible → SELLER role KHÔNG được thêm")
        void ineligibleUser_sellerRoleNotAdded() {
            // Arrange
            NormalUser user = TestFixture.suspendedBidder("bidderAB7");
            when(ratingService.isEligible(user)).thenReturn(false);

            // Act
            assertThrows(IllegalStateException.class, () -> sut.autoApproveSellerRole(user));

            // Assert
            assertFalse(user.hasRole(User.UserRole.SELLER));
        }

        @Test
        @DisplayName("user ineligible → sellerDAO KHÔNG được gọi")
        void ineligibleUser_sellerDaoNotCalled() {
            // Arrange
            NormalUser user = TestFixture.suspendedBidder("bidderBC8");
            when(ratingService.isEligible(user)).thenReturn(false);

            // Act
            assertThrows(IllegalStateException.class, () -> sut.autoApproveSellerRole(user));

            // Assert — persist không được xảy ra
            verify(sellerDAO, never()).approveSellerRole(anyString());
        }

        @Test
        @DisplayName("user bị penalized (hasEverBeenPenalized=true) → IllegalStateException")
        void penalizedUser_throwsIllegalStateException() {
            // Arrange — eligible nhưng đã từng bị phạt
            NormalUser user = TestFixture.penalizedBidder("bidderBD9");
            when(ratingService.isEligible(user)).thenReturn(true);

            // Act & Assert
            assertThrows(IllegalStateException.class, () -> sut.autoApproveSellerRole(user));
        }

        @Test
        @DisplayName("user penalized → SELLER role KHÔNG được thêm")
        void penalizedUser_sellerRoleNotAdded() {
            // Arrange
            NormalUser user = TestFixture.penalizedBidder("bidderBE0");
            when(ratingService.isEligible(user)).thenReturn(true);

            // Act
            assertThrows(IllegalStateException.class, () -> sut.autoApproveSellerRole(user));

            // Assert
            assertFalse(user.hasRole(User.UserRole.SELLER));
        }

        @Test
        @DisplayName("penalized check sau ineligible check → penalized + ineligible throw IllegalStateException (ineligible)")
        void penalizedAndIneligible_ineligibleCheckFirst() {
            // Arrange — cả hai điều kiện sai: ineligible phải được check trước
            NormalUser user = TestFixture.penalizedBidder("bidderBF1");
            when(ratingService.isEligible(user)).thenReturn(false);

            // Act — phải throw IllegalStateException từ isEligible check
            assertThrows(IllegalStateException.class, () -> sut.autoApproveSellerRole(user));

            // Verify isEligible được gọi
            verify(ratingService).isEligible(user);
        }

        // --- Already has SELLER role (idempotent) ---

        @Test
        @DisplayName("user đã có role SELLER → không thêm lần nữa (idempotent), không gọi sellerDAO")
        void alreadyHasSellerRole_idempotent_noDaoPersist() {
            // Arrange
            NormalUser user = TestFixture.normalSeller("sellerAA1");
            when(ratingService.isEligible(user)).thenReturn(true);

            // Act
            sut.autoApproveSellerRole(user);

            // Assert — đã có role → không persist lại
            verify(sellerDAO, never()).approveSellerRole(anyString());
            assertTrue(user.hasRole(User.UserRole.SELLER)); // vẫn có
        }

        // --- Role transition correctness ---

        @Test
        @DisplayName("approve thành công → isEligible được gọi đúng 1 lần với đúng user")
        void approveSuccess_eligibilityCheckedOnce() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderBG2");
            when(ratingService.isEligible(user)).thenReturn(true);

            // Act
            sut.autoApproveSellerRole(user);

            // Assert
            verify(ratingService, times(1)).isEligible(user);
        }
    }

    // =========================================================================
    // banUser
    // =========================================================================

    @Nested
    @DisplayName("banUser()")
    class BanUser {

        // --- Happy path ---

        @Test
        @DisplayName("ban ACTIVE user → status chuyển thành BANNED")
        void activeUser_statusBecomesBanned() {
            // Arrange
            NormalUser target = TestFixture.normalBidder("bidderBH3");
            assertEquals(AccountStatus.ACTIVE, target.getAccountStatus());

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert
            assertEquals(AccountStatus.BANNED, target.getAccountStatus());
        }

        @Test
        @DisplayName("ban user → gọi userDAO.updateAccountStatus với BANNED")
        void banUser_persistsBannedStatus() {
            // Arrange
            NormalUser target = TestFixture.normalBidder("bidderBI4");

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert — interaction quan trọng: trạng thái ban phải được persist
            verify(userDAO).updateAccountStatus(target.getId(), AccountStatus.BANNED.name());
        }

        @Test
        @DisplayName("ban với LOW_RATING → persist 'BANNED' (không phụ thuộc lý do)")
        void banWithLowRatingReason_persistsBanned() {
            // Arrange
            NormalUser target = TestFixture.normalBidder("bidderBJ5");

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert
            verify(userDAO).updateAccountStatus(eq(target.getId()), eq("BANNED"));
        }

        @Test
        @DisplayName("ban với SELLER_REFUND_DEFAULT → persist 'BANNED'")
        void banWithSellerRefundReason_persistsBanned() {
            // Arrange
            NormalUser target = TestFixture.normalSeller("sellerBK6");

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.SELLER_REFUND_DEFAULT);

            // Assert
            verify(userDAO).updateAccountStatus(eq(target.getId()), eq("BANNED"));
            assertEquals(AccountStatus.BANNED, target.getAccountStatus());
        }

        @Test
        @DisplayName("ban user → action log được thêm vào admin")
        void banUser_adminActionLogRecorded() {
            // Arrange
            NormalUser target = TestFixture.normalBidder("bidderBL7");
            int logSizeBefore = staffAdmin.getActionLog().size();

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert — admin phải ghi nhận hành động
            assertEquals(logSizeBefore + 1, staffAdmin.getActionLog().size());
        }

        @Test
        @DisplayName("ban user → action log chứa username của admin và target")
        void banUser_actionLogContainsAdminAndTargetName() {
            // Arrange
            NormalUser target = TestFixture.normalBidder("bidderBM8");

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert — log phải đủ thông tin trace
            String lastLog = staffAdmin.getActionLog().get(staffAdmin.getActionLog().size() - 1);
            assertTrue(lastLog.contains(staffAdmin.getUsername()),
                    "log phải chứa username của admin");
            assertTrue(lastLog.contains(target.getUsername()),
                    "log phải chứa username của target");
        }

        // --- Repeated ban ---

        @Test
        @DisplayName("ban user đã BANNED → status vẫn BANNED (idempotent)")
        void alreadyBannedUser_remainsBanned() {
            // Arrange
            NormalUser target = TestFixture.bannedBidder("bidderBN9");
            assertEquals(AccountStatus.BANNED, target.getAccountStatus());

            // Act — ban lần 2
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert
            assertEquals(AccountStatus.BANNED, target.getAccountStatus());
        }

        @Test
        @DisplayName("repeated ban → persist vẫn được gọi (không skip khi đã BANNED)")
        void repeatedBan_stillPersists() {
            // Arrange
            NormalUser target = TestFixture.bannedBidder("bidderBO0");

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert — persist phải xảy ra dù đã BANNED (đảm bảo DB sync)
            verify(userDAO).updateAccountStatus(target.getId(), AccountStatus.BANNED.name());
        }

        // --- Invalid account state ---

        @Test
        @DisplayName("ban SUSPENDED user → status chuyển BANNED (ghi đè SUSPENDED)")
        void suspendedUser_statusBecomesBanned() {
            // Arrange
            NormalUser target = TestFixture.suspendedBidder("bidderBP1");
            assertEquals(AccountStatus.SUSPENDED, target.getAccountStatus());

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert
            assertEquals(AccountStatus.BANNED, target.getAccountStatus());
        }

        @Test
        @DisplayName("ban Seller → Seller cũng bị BANNED (không phân biệt role)")
        void banSeller_statusBecomesBanned() {
            // Arrange
            NormalUser seller = TestFixture.normalSeller("sellerBQ2");

            // Act
            sut.banUser(staffAdmin, seller, Admin.BanReason.SELLER_REFUND_DEFAULT);

            // Assert
            assertEquals(AccountStatus.BANNED, seller.getAccountStatus());
        }

        // --- State consistency ---

        @Test
        @DisplayName("ban user → balance KHÔNG thay đổi")
        void banUser_balanceUnchanged() {
            // Arrange
            NormalUser target = TestFixture.bidderWithBalance("bidderBR3", 2_000_000L);
            long balanceBefore = target.getBalance();

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert
            assertEquals(balanceBefore, target.getBalance());
        }

        @Test
        @DisplayName("ban user → rating KHÔNG thay đổi")
        void banUser_ratingUnchanged() {
            // Arrange
            NormalUser target = TestFixture.bidderWithRating("bidderBS4", 2.5);
            double ratingBefore = target.getRating();

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert
            assertEquals(ratingBefore, target.getRating(), 1e-9);
        }

        @Test
        @DisplayName("persist: updateAccountStatus nhận đúng userId và 'BANNED'")
        void persistCallReceivesCorrectArguments() {
            // Arrange
            NormalUser target = TestFixture.normalBidder("bidderBT5");
            ArgumentCaptor<String> idCaptor     = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);

            // Act
            sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);

            // Assert
            verify(userDAO).updateAccountStatus(idCaptor.capture(), statusCaptor.capture());
            assertEquals(target.getId(), idCaptor.getValue());
            assertEquals("BANNED", statusCaptor.getValue());
        }

        // --- null safety ---

        @Test
        @DisplayName("null reason → NullPointerException không bị nuốt")
        void nullReason_exceptionPropagates() {
            // Arrange
            NormalUser target = TestFixture.normalBidder("bidderBU6");

            // Act & Assert — null reason phải lộ ra, không bị nuốt thầm
            assertThrows(Exception.class,
                    () -> sut.banUser(staffAdmin, target, null));
        }
    }
}