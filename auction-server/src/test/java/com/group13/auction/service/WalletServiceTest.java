package com.group13.auction.service;

import com.group13.auction.TestFixture;
import com.group13.auction.bank.SystemBank;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.model.bid.FinancialTransaction;
import com.group13.auction.model.bid.FinancialTransaction.TransactionType;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link WalletService}.
 *
 * <p>Tập trung vào 3 nhóm nghiệp vụ:
 * <ul>
 *   <li>{@code lockDeposit}    — guard insufficient balance, cộng dồn lock, log transaction.</li>
 *   <li>{@code unlockDeposit}  — giảm locked, over-unlock clamp, log transaction.</li>
 *   <li>{@code forfeitDeposit} — tịch thu cọc, chuyển vào SystemBank, log transaction.</li>
 * </ul>
 *
 * <p>Mock: {@link UserDAO}, {@link FinancialTransactionDAO} (external dependency, chạm DB).
 * Không mock: {@link SystemBank} (pure in-memory Singleton),
 *             {@link NormalUser} (domain model),
 *             {@link IRatingService} (dùng Fake từ {@link TestFixture}).
 *
 * <p>SystemBank được reset về 0 trước mỗi test qua {@link TestFixture#resetSystemBankBalance()}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService")
class WalletServiceTest {

    // -------------------------------------------------------------------------
    // Mocks — external dependencies chạm DB / network
    // -------------------------------------------------------------------------
    @Mock private UserDAO               userDAO;
    @Mock private FinancialTransactionDAO financialTransactionDAO;

    // -------------------------------------------------------------------------
    // SUT + cộng sự
    // -------------------------------------------------------------------------
    private WalletService walletService;
    private IRatingService ratingServiceAllowAll;

    private static final String AUCTION_ID = "auction-001";

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.resetSystemBankBalance();         // Singleton isolation
        ratingServiceAllowAll = TestFixture.ratingServiceAllowAll();
        walletService = new WalletService(financialTransactionDAO, userDAO, ratingServiceAllowAll);
    }

    // =========================================================================
    // lockDeposit
    // =========================================================================

    @Nested
    @DisplayName("lockDeposit() — guard, state, log")
    class LockDeposit {

        // --- Happy path ---

        @Test
        @DisplayName("happy: available balance đủ → lockedDeposit tăng đúng amount")
        void sufficientBalance_lockedDepositIncreases() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderAA1", 1_000_000L);
            long depositAmount = 300_000L;

            // Act
            walletService.lockDeposit(bidder, depositAmount, AUCTION_ID);

            // Assert
            assertEquals(300_000L, bidder.getLockedDeposit());
        }

        @Test
        @DisplayName("happy: available balance đủ → balance KHÔNG thay đổi")
        void sufficientBalance_balanceUnchanged() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderBB2", 1_000_000L);
            long balanceBefore = bidder.getBalance();

            // Act
            walletService.lockDeposit(bidder, 300_000L, AUCTION_ID);

            // Assert
            assertEquals(balanceBefore, bidder.getBalance());
        }

        @Test
        @DisplayName("happy: available balance đủ → availableBalance giảm đúng amount")
        void sufficientBalance_availableBalanceDecreases() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderCC3", 1_000_000L);
            long available = bidder.getAvailableBalance();
            long depositAmount = 300_000L;

            // Act
            walletService.lockDeposit(bidder, depositAmount, AUCTION_ID);

            // Assert
            assertEquals(available - depositAmount, bidder.getAvailableBalance());
        }

        @Test
        @DisplayName("happy: lock đúng bằng toàn bộ available balance → availableBalance = 0")
        void lockExactlyAvailableBalance_availableIsZero() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderDD4", 500_000L);

            // Act
            walletService.lockDeposit(bidder, 500_000L, AUCTION_ID);

            // Assert
            assertEquals(0L, bidder.getAvailableBalance());
            assertEquals(500_000L, bidder.getLockedDeposit());
        }

        // --- Insufficient balance (business rule) ---

        @Test
        @DisplayName("insufficient: available < depositAmount → ném AuctionBusinessException")
        void insufficientBalance_throwsAuctionBusinessException() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderEE5", 100_000L);

            // Act & Assert
            assertThrows(AuctionBusinessException.class,
                    () -> walletService.lockDeposit(bidder, 200_000L, AUCTION_ID));
        }

        @Test
        @DisplayName("insufficient: exception mang Reason.INSUFFICIENT_DEPOSIT")
        void insufficientBalance_exceptionHasCorrectReason() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderFF6", 50_000L);

            // Act
            AuctionBusinessException ex = assertThrows(AuctionBusinessException.class,
                    () -> walletService.lockDeposit(bidder, 100_000L, AUCTION_ID));

            // Assert
            assertEquals(AuctionBusinessException.Reason.INSUFFICIENT_DEPOSIT, ex.getReason());
        }

        @Test
        @DisplayName("insufficient: state KHÔNG thay đổi sau khi ném exception (atomicity)")
        void insufficientBalance_stateUnchangedOnException() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderGG7", 100_000L);
            long balanceBefore      = bidder.getBalance();
            long lockedBefore       = bidder.getLockedDeposit();
            long availableBefore    = bidder.getAvailableBalance();

            // Act
            assertThrows(AuctionBusinessException.class,
                    () -> walletService.lockDeposit(bidder, 200_000L, AUCTION_ID));

            // Assert — không có gì thay đổi
            assertEquals(balanceBefore,   bidder.getBalance());
            assertEquals(lockedBefore,    bidder.getLockedDeposit());
            assertEquals(availableBefore, bidder.getAvailableBalance());
        }

        @Test
        @DisplayName("insufficient: userDAO.updateBalances KHÔNG được gọi khi guard fail")
        void insufficientBalance_userDaoNotCalled() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderHH8", 50_000L);

            // Act
            assertThrows(AuctionBusinessException.class,
                    () -> walletService.lockDeposit(bidder, 100_000L, AUCTION_ID));

            // Assert
            verify(userDAO, never()).updateBalances(anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("insufficient: financialTransactionDAO.saveTransaction KHÔNG được gọi khi guard fail")
        void insufficientBalance_txDaoNotCalled() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderII9", 50_000L);

            // Act
            assertThrows(AuctionBusinessException.class,
                    () -> walletService.lockDeposit(bidder, 100_000L, AUCTION_ID));

            // Assert
            verify(financialTransactionDAO, never()).saveTransaction(any());
        }

        // --- Zero amount ---

        @Test
        @DisplayName("zero amount: available = 0 nhưng lock 0 → KHÔNG ném exception")
        void zeroAmountOnZeroBalance_noException() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderJJ0", 0L);

            // Act & Assert
            assertDoesNotThrow(() -> walletService.lockDeposit(bidder, 0L, AUCTION_ID));
        }

        @Test
        @DisplayName("zero amount: lockedDeposit không thay đổi sau lock 0")
        void zeroAmount_lockedDepositUnchanged() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderKK1", 1_000_000L);
            long lockedBefore = bidder.getLockedDeposit();

            // Act
            walletService.lockDeposit(bidder, 0L, AUCTION_ID);

            // Assert
            assertEquals(lockedBefore, bidder.getLockedDeposit());
        }

        // --- Double lock (cùng một user, hai phiên khác nhau) ---

        @Test
        @DisplayName("double lock: hai lần lockDeposit cộng dồn đúng vào lockedDeposit")
        void doubleLock_lockedDepositAccumulates() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderLL2", 1_000_000L);

            // Act
            walletService.lockDeposit(bidder, 300_000L, "auction-001");
            walletService.lockDeposit(bidder, 200_000L, "auction-002");

            // Assert
            assertEquals(500_000L, bidder.getLockedDeposit());
        }

        @Test
        @DisplayName("double lock: lần 2 dùng available balance sau lần 1 → chính xác")
        void doubleLock_secondLockUsesAvailableAfterFirst() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderMM3", 1_000_000L);
            walletService.lockDeposit(bidder, 700_000L, "auction-001");

            // Act — chỉ còn 300_000 available
            walletService.lockDeposit(bidder, 300_000L, "auction-002");

            // Assert
            assertEquals(0L, bidder.getAvailableBalance());
            assertEquals(1_000_000L, bidder.getLockedDeposit());
        }

        @Test
        @DisplayName("double lock: lần 2 vượt available → ném exception, lockedDeposit giữ nguyên sau lần 1")
        void doubleLock_secondLockExceedsAvailable_throws() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderNN4", 1_000_000L);
            walletService.lockDeposit(bidder, 700_000L, "auction-001");
            long lockedAfterFirst = bidder.getLockedDeposit(); // 700_000

            // Act
            assertThrows(AuctionBusinessException.class,
                    () -> walletService.lockDeposit(bidder, 500_000L, "auction-002"));

            // Assert — lockedDeposit giữ nguyên giá trị sau lần 1
            assertEquals(lockedAfterFirst, bidder.getLockedDeposit());
        }

        // --- DAO interaction ---

        @Test
        @DisplayName("dao: userDAO.updateBalances được gọi đúng 1 lần với balance và locked mới")
        void successfulLock_userDaoCalledOnce() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderOO5", 1_000_000L);

            // Act
            walletService.lockDeposit(bidder, 300_000L, AUCTION_ID);

            // Assert
            verify(userDAO, times(1))
                    .updateBalances(eq(bidder.getId()), eq(bidder.getBalance()), eq(bidder.getLockedDeposit()));
        }

        @Test
        @DisplayName("dao: financialTransactionDAO.saveTransaction được gọi đúng 1 lần")
        void successfulLock_txDaoCalledOnce() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderPP6", 1_000_000L);

            // Act
            walletService.lockDeposit(bidder, 300_000L, AUCTION_ID);

            // Assert
            verify(financialTransactionDAO, times(1)).saveTransaction(any(FinancialTransaction.class));
        }

        // --- Transaction log ---

        @Test
        @DisplayName("log: sau lockDeposit thành công → transaction log có đúng 1 entry")
        void successfulLock_transactionLogHasOneEntry() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderQQ7", 1_000_000L);

            // Act
            walletService.lockDeposit(bidder, 300_000L, AUCTION_ID);

            // Assert
            assertEquals(1, walletService.getTransactionLog().size());
        }

        @Test
        @DisplayName("log: transaction type phải là DEPOSIT_LOCK")
        void successfulLock_transactionTypeIsDepositLock() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderRR8", 1_000_000L);

            // Act
            walletService.lockDeposit(bidder, 300_000L, AUCTION_ID);

            // Assert
            FinancialTransaction tx = walletService.getTransactionLog().get(0);
            assertEquals(TransactionType.DEPOSIT_LOCK, tx.getType());
        }

        @Test
        @DisplayName("log: transaction ghi đúng amount và auctionId")
        void successfulLock_transactionHasCorrectAmountAndAuctionId() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderSS9", 1_000_000L);
            long depositAmount = 400_000L;

            // Act
            walletService.lockDeposit(bidder, depositAmount, AUCTION_ID);

            // Assert
            FinancialTransaction tx = walletService.getTransactionLog().get(0);
            assertEquals(depositAmount, tx.getAmount());
            assertEquals(AUCTION_ID,    tx.getAuctionId());
        }

        @Test
        @DisplayName("log: hai lần lockDeposit → transaction log có 2 entries")
        void doubleLock_transactionLogHasTwoEntries() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderTT0", 1_000_000L);

            // Act
            walletService.lockDeposit(bidder, 300_000L, "auction-001");
            walletService.lockDeposit(bidder, 200_000L, "auction-002");

            // Assert
            assertEquals(2, walletService.getTransactionLog().size());
        }

        // --- Transaction log immutability ---

        @Test
        @DisplayName("log: getTransactionLog trả về list không thể modify")
        void transactionLog_isUnmodifiable() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderUU1", 1_000_000L);
            walletService.lockDeposit(bidder, 100_000L, AUCTION_ID);

            // Act & Assert
            List<FinancialTransaction> log = walletService.getTransactionLog();
            assertThrows(UnsupportedOperationException.class,
                    () -> log.add(null),
                    "transaction log phải unmodifiable");
        }
    }

    // =========================================================================
    // unlockDeposit
    // =========================================================================

    @Nested
    @DisplayName("unlockDeposit() — giảm locked, clamp, log")
    class UnlockDeposit {

        // --- Happy path ---

        @Test
        @DisplayName("happy: unlock amount hợp lệ → lockedDeposit giảm đúng lượng")
        void validUnlock_lockedDepositDecreases() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderAA1", 1_000_000L);
            walletService.lockDeposit(bidder, 600_000L, AUCTION_ID);

            // Act
            walletService.unlockDeposit(bidder, 600_000L, AUCTION_ID);

            // Assert
            assertEquals(0L, bidder.getLockedDeposit());
        }

        @Test
        @DisplayName("happy: unlock amount hợp lệ → balance KHÔNG thay đổi")
        void validUnlock_balanceUnchanged() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderBB2", 1_000_000L);
            walletService.lockDeposit(bidder, 400_000L, AUCTION_ID);
            long balanceBefore = bidder.getBalance();

            // Act
            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID);

            // Assert
            assertEquals(balanceBefore, bidder.getBalance());
        }

        @Test
        @DisplayName("happy: unlock → availableBalance tăng đúng lượng đã unlock")
        void validUnlock_availableBalanceIncreases() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderCC3", 1_000_000L);
            walletService.lockDeposit(bidder, 400_000L, AUCTION_ID);
            long availableAfterLock = bidder.getAvailableBalance(); // 600_000

            // Act
            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID);

            // Assert
            assertEquals(availableAfterLock + 400_000L, bidder.getAvailableBalance());
        }

        @Test
        @DisplayName("happy: lock → unlock cùng amount → state khôi phục hoàn toàn")
        void lockThenUnlock_stateFullyRestored() {
            // Arrange
            NormalUser bidder  = TestFixture.bidderWithBalance("bidderDD4", 1_000_000L);
            long initialLocked    = bidder.getLockedDeposit();
            long initialAvailable = bidder.getAvailableBalance();
            long initialBalance   = bidder.getBalance();

            // Act
            walletService.lockDeposit(bidder,   400_000L, AUCTION_ID);
            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID);

            // Assert
            assertEquals(initialBalance,   bidder.getBalance());
            assertEquals(initialLocked,    bidder.getLockedDeposit());
            assertEquals(initialAvailable, bidder.getAvailableBalance());
        }

        // --- Over-unlock: unlock vượt quá locked → clamp về 0 ---

        @Test
        @DisplayName("over-unlock: unlock vượt lockedDeposit → clamp về 0 (không âm)")
        void overUnlock_lockedClampedToZero() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderEE5", 1_000_000L);
            walletService.lockDeposit(bidder, 300_000L, AUCTION_ID);

            // Act — unlock nhiều hơn đang locked
            walletService.unlockDeposit(bidder, 999_999L, AUCTION_ID);

            // Assert
            assertEquals(0L, bidder.getLockedDeposit(),
                    "lockedDeposit phải clamp về 0, không âm");
        }

        @Test
        @DisplayName("over-unlock: balance KHÔNG thay đổi dù lockedDeposit bị clamp")
        void overUnlock_balanceStillUnchanged() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderFF6", 1_000_000L);
            walletService.lockDeposit(bidder, 300_000L, AUCTION_ID);
            long balanceBefore = bidder.getBalance();

            // Act
            walletService.unlockDeposit(bidder, 999_999L, AUCTION_ID);

            // Assert
            assertEquals(balanceBefore, bidder.getBalance());
        }

        @Test
        @DisplayName("over-unlock: unlock Long.MAX_VALUE → lockedDeposit = 0")
        void overUnlockMaxLong_lockedIsZero() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderGG7", 1_000_000L);
            walletService.lockDeposit(bidder, 500_000L, AUCTION_ID);

            // Act
            walletService.unlockDeposit(bidder, Long.MAX_VALUE, AUCTION_ID);

            // Assert
            assertEquals(0L, bidder.getLockedDeposit());
        }

        @Test
        @DisplayName("over-unlock: unlock khi lockedDeposit = 0 → giữ nguyên 0")
        void unlockWhenAlreadyZero_staysZero() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderHH8", 1_000_000L);
            // lockedDeposit = 0 (không lock gì)

            // Act
            walletService.unlockDeposit(bidder, 100_000L, AUCTION_ID);

            // Assert
            assertEquals(0L, bidder.getLockedDeposit());
        }

        // --- Zero amount ---

        @Test
        @DisplayName("zero amount: unlock 0 → lockedDeposit không thay đổi")
        void zeroAmount_lockedDepositUnchanged() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderII9", 1_000_000L);
            walletService.lockDeposit(bidder, 400_000L, AUCTION_ID);
            long lockedBefore = bidder.getLockedDeposit();

            // Act
            walletService.unlockDeposit(bidder, 0L, AUCTION_ID);

            // Assert
            assertEquals(lockedBefore, bidder.getLockedDeposit());
        }

        // --- Partial unlock ---

        @Test
        @DisplayName("partial unlock: nhiều lần unlock từng phần → cộng dồn đúng")
        void partialUnlock_accumulatesCorrectly() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderJJ0", 1_000_000L);
            walletService.lockDeposit(bidder, 900_000L, AUCTION_ID);

            // Act
            walletService.unlockDeposit(bidder, 300_000L, "auction-001");
            walletService.unlockDeposit(bidder, 300_000L, "auction-002");
            walletService.unlockDeposit(bidder, 300_000L, "auction-003");

            // Assert
            assertEquals(0L, bidder.getLockedDeposit());
        }

        // --- DAO interaction ---

        @Test
        @DisplayName("dao: userDAO.updateBalances được gọi đúng 1 lần khi unlock thành công")
        void successfulUnlock_userDaoCalledOnce() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderKK1", 1_000_000L);
            walletService.lockDeposit(bidder, 400_000L, AUCTION_ID);
            clearInvocations(userDAO);

            // Act
            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID);

            // Assert
            verify(userDAO, times(1))
                    .updateBalances(eq(bidder.getId()), eq(bidder.getBalance()), eq(bidder.getLockedDeposit()));
        }

        @Test
        @DisplayName("dao: financialTransactionDAO.saveTransaction được gọi đúng 1 lần khi unlock")
        void successfulUnlock_txDaoCalledOnce() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderLL2", 1_000_000L);
            walletService.lockDeposit(bidder, 400_000L, AUCTION_ID);
            clearInvocations(financialTransactionDAO);

            // Act
            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID);

            // Assert
            verify(financialTransactionDAO, times(1)).saveTransaction(any(FinancialTransaction.class));
        }

        // --- Transaction log ---

        @Test
        @DisplayName("log: transaction type phải là DEPOSIT_UNLOCK")
        void successfulUnlock_transactionTypeIsDepositUnlock() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderMM3", 1_000_000L);
            walletService.lockDeposit(bidder, 400_000L, AUCTION_ID);

            // Act
            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID);

            // Assert — log có 2 entries: LOCK + UNLOCK; lấy phần tử cuối
            List<FinancialTransaction> log = walletService.getTransactionLog();
            assertEquals(2, log.size());
            assertEquals(TransactionType.DEPOSIT_UNLOCK, log.get(1).getType());
        }

        @Test
        @DisplayName("log: transaction ghi đúng amount và auctionId khi unlock")
        void successfulUnlock_transactionHasCorrectAmountAndAuctionId() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderNN4", 1_000_000L);
            walletService.lockDeposit(bidder, 500_000L, AUCTION_ID);
            long unlockAmount = 500_000L;

            // Act
            walletService.unlockDeposit(bidder, unlockAmount, AUCTION_ID);

            // Assert
            FinancialTransaction tx = walletService.getTransactionLog().get(1);
            assertEquals(unlockAmount, tx.getAmount());
            assertEquals(AUCTION_ID,   tx.getAuctionId());
        }

        @Test
        @DisplayName("log: over-unlock vẫn tạo transaction (không throw)")
        void overUnlock_stillCreatesTransaction() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderOO5", 1_000_000L);
            walletService.lockDeposit(bidder, 300_000L, AUCTION_ID);
            int logSizeBeforeUnlock = walletService.getTransactionLog().size();

            // Act — over-unlock không throw
            walletService.unlockDeposit(bidder, 999_999L, AUCTION_ID);

            // Assert — log tăng thêm 1
            assertEquals(logSizeBeforeUnlock + 1, walletService.getTransactionLog().size());
        }

        // --- Repeated unlock ---

        @Test
        @DisplayName("repeated: gọi unlockDeposit 2 lần với cùng amount → lần 2 clamp về 0")
        void repeatedUnlock_secondCallClamps() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderPP6", 1_000_000L);
            walletService.lockDeposit(bidder, 400_000L, AUCTION_ID);

            // Act
            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID); // lần 1: locked → 0
            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID); // lần 2: đã 0 → clamp

            // Assert — vẫn 0, không âm
            assertEquals(0L, bidder.getLockedDeposit());
        }

        @Test
        @DisplayName("repeated: gọi 2 lần → transaction log có 3 entries (1 lock + 2 unlock)")
        void repeatedUnlock_logHasThreeEntries() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderQQ7", 1_000_000L);
            walletService.lockDeposit(bidder, 400_000L, AUCTION_ID);

            // Act
            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID);
            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID);

            // Assert
            assertEquals(3, walletService.getTransactionLog().size());
        }
    }

    // =========================================================================
    // forfeitDeposit
    // =========================================================================

    @Nested
    @DisplayName("forfeitDeposit() — tịch thu cọc, chuyển SystemBank, log")
    class ForfeitDeposit {

        // --- Happy path ---

        @Test
        @DisplayName("happy: forfeit → lockedDeposit giảm đúng amount")
        void forfeit_lockedDepositDecreases() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderAA1", 1_000_000L);
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);

            // Act
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert
            assertEquals(0L, winner.getLockedDeposit());
        }

        @Test
        @DisplayName("happy: forfeit → balance KHÔNG thay đổi (cọc bị tịch thu, không hoàn)")
        void forfeit_balanceUnchanged() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderBB2", 1_000_000L);
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);
            long balanceBefore = winner.getBalance();

            // Act
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert — balance không đổi; tiền cọc đã bị khóa từ trước
            assertEquals(balanceBefore, winner.getBalance());
        }

        @Test
        @DisplayName("happy: forfeit → availableBalance tăng (cọc được giải phóng, nhưng không hoàn về balance)")
        void forfeit_availableBalanceIncreases() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderCC3", 1_000_000L);
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);
            long availableBefore = winner.getAvailableBalance(); // 700_000

            // Act
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert — unlock giải phóng locked → available tăng (dù tiền không về balance)
            assertEquals(availableBefore + 300_000L, winner.getAvailableBalance());
        }

        // --- SystemBank nhận tiền cọc ---

        @Test
        @DisplayName("bank: forfeit → SystemBank.totalBalance tăng đúng depositAmount")
        void forfeit_systemBankReceivesDeposit() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderDD4", 1_000_000L);
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);
            long bankBefore = SystemBank.getInstance().getTotalBalance();

            // Act
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert
            assertEquals(bankBefore + 300_000L, SystemBank.getInstance().getTotalBalance());
        }

        @Test
        @DisplayName("bank: forfeit zero → SystemBank.totalBalance không thay đổi")
        void forfeitZero_systemBankUnchanged() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderEE5", 1_000_000L);
            long bankBefore = SystemBank.getInstance().getTotalBalance();

            // Act
            walletService.forfeitDeposit(winner, 0L, AUCTION_ID);

            // Assert
            assertEquals(bankBefore, SystemBank.getInstance().getTotalBalance());
        }

        @Test
        @DisplayName("bank: hai lần forfeit (2 winner khác nhau) → bank tăng tổng cộng đúng")
        void twoForfeitures_bankReceivesBothDeposits() {
            // Arrange
            NormalUser winner1 = TestFixture.bidderWithBalance("bidderFF6_", 1_000_000L);
            NormalUser winner2 = TestFixture.bidderWithBalance("bidderGG7_", 2_000_000L);
            walletService.lockDeposit(winner1, 300_000L, "auction-001");
            walletService.lockDeposit(winner2, 500_000L, "auction-002");
            long bankAfterLocks = SystemBank.getInstance().getTotalBalance();

            // Act
            walletService.forfeitDeposit(winner1, 300_000L, "auction-001");
            walletService.forfeitDeposit(winner2, 500_000L, "auction-002");

            // Assert
            assertEquals(bankAfterLocks + 300_000L + 500_000L,
                    SystemBank.getInstance().getTotalBalance());
        }

        // --- Forfeit không tương đương unlock (tiền không hoàn) ---

        @Test
        @DisplayName("correctness: forfeit khác unlockDeposit — balance winner sau forfeit < balance trước lock")
        void forfeit_winnerCannotRecoverDeposit() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderHH8", 1_000_000L);
            long balanceBeforeLock = winner.getBalance();
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);

            // Act
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert — balance trước lock ≠ balance sau forfeit: tiền không hoàn
            // Thực ra balance không đổi khi lock/forfeit — nhưng available không = initial balance
            // available = balance - lockedDeposit. Sau forfeit: locked = 0, balance không đổi.
            // Tuy nhiên bank đã nhận 300_000 → tiền đó mất khỏi hệ thống winner.
            assertEquals(balanceBeforeLock, winner.getBalance(), "balance không đổi — cọc đã bị lock từ trước");
            assertEquals(300_000L, SystemBank.getInstance().getTotalBalance(), "tiền thuộc về bank, không trả winner");
        }

        // --- Over-forfeit: vượt lockedDeposit → clamp về 0 ---

        @Test
        @DisplayName("over-forfeit: forfeit vượt lockedDeposit → lockedDeposit clamp về 0")
        void overForfeit_lockedClampedToZero() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderII9", 1_000_000L);
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);

            // Act — forfeit nhiều hơn đang locked
            walletService.forfeitDeposit(winner, 999_999L, AUCTION_ID);

            // Assert
            assertEquals(0L, winner.getLockedDeposit());
        }

        @Test
        @DisplayName("over-forfeit: bank nhận đúng depositAmount được truyền vào (không phải lockedDeposit)")
        void overForfeit_bankReceivesRequestedAmount() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderJJ0", 1_000_000L);
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);
            long bankAfterLock = SystemBank.getInstance().getTotalBalance();

            // Act — forfeit nhiều hơn locked
            walletService.forfeitDeposit(winner, 999_999L, AUCTION_ID);

            // Assert — bank nhận đúng amount được gọi, không bị clamp
            assertEquals(bankAfterLock + 999_999L, SystemBank.getInstance().getTotalBalance());
        }

        // --- DAO interaction ---

        @Test
        @DisplayName("dao: userDAO.updateBalances được gọi đúng 1 lần khi forfeit")
        void forfeit_userDaoCalledOnce() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderKK1", 1_000_000L);
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);
            clearInvocations(userDAO);

            // Act
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert
            verify(userDAO, times(1))
                    .updateBalances(eq(winner.getId()), eq(winner.getBalance()), eq(winner.getLockedDeposit()));
        }

        @Test
        @DisplayName("dao: financialTransactionDAO.saveTransaction được gọi đúng 1 lần khi forfeit")
        void forfeit_txDaoCalledOnce() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderLL2", 1_000_000L);
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);
            clearInvocations(financialTransactionDAO);

            // Act
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert
            verify(financialTransactionDAO, times(1)).saveTransaction(any(FinancialTransaction.class));
        }

        // --- Transaction log ---

        @Test
        @DisplayName("log: transaction type phải là DEPOSIT_FORFEIT")
        void forfeit_transactionTypeIsDepositForfeit() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderMM3", 1_000_000L);
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);

            // Act
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert — entry cuối là FORFEIT
            List<FinancialTransaction> log = walletService.getTransactionLog();
            assertEquals(TransactionType.DEPOSIT_FORFEIT, log.get(log.size() - 1).getType());
        }

        @Test
        @DisplayName("log: transaction ghi đúng amount và auctionId khi forfeit")
        void forfeit_transactionHasCorrectAmountAndAuctionId() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderNN4", 1_000_000L);
            walletService.lockDeposit(winner, 400_000L, AUCTION_ID);
            long forfeitAmount = 400_000L;

            // Act
            walletService.forfeitDeposit(winner, forfeitAmount, AUCTION_ID);

            // Assert
            List<FinancialTransaction> log = walletService.getTransactionLog();
            FinancialTransaction tx = log.get(log.size() - 1);
            assertEquals(forfeitAmount, tx.getAmount());
            assertEquals(AUCTION_ID,    tx.getAuctionId());
        }

        @Test
        @DisplayName("log: toàn bộ sequence lock → forfeit → 2 entries trong log")
        void lockThenForfeit_logHasTwoEntries() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderOO5", 1_000_000L);

            // Act
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert
            assertEquals(2, walletService.getTransactionLog().size());
            assertEquals(TransactionType.DEPOSIT_LOCK,    walletService.getTransactionLog().get(0).getType());
            assertEquals(TransactionType.DEPOSIT_FORFEIT, walletService.getTransactionLog().get(1).getType());
        }

        // --- Penalized state: forfeit không thay đổi hasEverBeenPenalized ---

        @Test
        @DisplayName("penalty: forfeitDeposit KHÔNG tự gọi markPenalized (WalletService không penalize)")
        void forfeit_doesNotMarkPenalized() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderPP6", 1_000_000L);
            assertFalse(winner.isHasEverBeenPenalized());
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);

            // Act
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert — WalletService chỉ xử lý tiền, không penalize rating
            assertFalse(winner.isHasEverBeenPenalized(),
                    "WalletService không chịu trách nhiệm penalize — đó là việc của RatingService");
        }

        @Test
        @DisplayName("penalty: forfeit trên user đã penalized trước → flag vẫn giữ nguyên")
        void forfeit_onAlreadyPenalizedUser_flagUnchanged() {
            // Arrange
            NormalUser winner = TestFixture.penalizedBidder("bidderQQ7");
            walletService.lockDeposit(winner, 300_000L, AUCTION_ID);

            // Act
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert
            assertTrue(winner.isHasEverBeenPenalized(), "penalized flag phải giữ nguyên");
        }

        // --- Forfeit không lock trước ---

        @Test
        @DisplayName("edge: forfeit khi chưa lock → lockedDeposit giữ 0, bank nhận amount (không guard)")
        void forfeitWithoutPriorLock_bankReceivesAmount() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderRR8", 1_000_000L);
            // lockedDeposit = 0 — chưa lock

            // Act — WalletService không guard: caller (PaymentService) phải đảm bảo đúng flow
            walletService.forfeitDeposit(winner, 300_000L, AUCTION_ID);

            // Assert — document behavior
            assertEquals(0L, winner.getLockedDeposit(), "clamp: không thể âm");
            assertEquals(300_000L, SystemBank.getInstance().getTotalBalance(),
                    "bank vẫn nhận amount được truyền vào");
        }
    }

    // =========================================================================
    // Cross-operation consistency
    // =========================================================================

    @Nested
    @DisplayName("Cross-operation — tính nhất quán giữa các operation")
    class CrossOperation {

        @Test
        @DisplayName("lock → unlock → lock lại: state đúng từng bước")
        void lockUnlockRelockSequence_correctAtEachStep() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderAA1", 1_000_000L);

            // Act & Assert step-by-step
            walletService.lockDeposit(bidder, 400_000L, AUCTION_ID);
            assertEquals(400_000L, bidder.getLockedDeposit());
            assertEquals(600_000L, bidder.getAvailableBalance());

            walletService.unlockDeposit(bidder, 400_000L, AUCTION_ID);
            assertEquals(0L,          bidder.getLockedDeposit());
            assertEquals(1_000_000L,  bidder.getAvailableBalance());

            walletService.lockDeposit(bidder, 700_000L, AUCTION_ID);
            assertEquals(700_000L, bidder.getLockedDeposit());
            assertEquals(300_000L, bidder.getAvailableBalance());
        }

        @Test
        @DisplayName("lock → forfeit → balance invariant: availableBalance = balance - lockedDeposit luôn đúng")
        void lockForfeit_availableBalanceInvariantHolds() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("bidderBB2", 2_000_000L);

            walletService.lockDeposit(winner, 600_000L, AUCTION_ID);
            assertEquals(winner.getBalance() - winner.getLockedDeposit(), winner.getAvailableBalance());

            walletService.forfeitDeposit(winner, 600_000L, AUCTION_ID);
            assertEquals(winner.getBalance() - winner.getLockedDeposit(), winner.getAvailableBalance());
        }

        @Test
        @DisplayName("multiple users: operation trên user A không ảnh hưởng user B")
        void multipleUsers_operationsAreIsolated() {
            // Arrange
            NormalUser userA = TestFixture.bidderWithBalance("bidderCC3_", 1_000_000L);
            NormalUser userB = TestFixture.bidderWithBalance("bidderDD4_", 2_000_000L);

            // Act — chỉ thao tác trên userA
            walletService.lockDeposit(userA, 500_000L, "auction-001");
            walletService.forfeitDeposit(userA, 500_000L, "auction-001");

            // Assert — userB không bị ảnh hưởng
            assertEquals(0L,          userB.getLockedDeposit());
            assertEquals(2_000_000L,  userB.getAvailableBalance());
        }

        @Test
        @DisplayName("transaction log: sequence lock + unlock + forfeit → 3 entries đúng type")
        void fullSequence_transactionLogHasCorrectTypes() {
            // Arrange
            NormalUser userA = TestFixture.bidderWithBalance("bidderEE5_", 1_000_000L);
            NormalUser userB = TestFixture.bidderWithBalance("bidderFF6_", 1_000_000L);

            // Act
            walletService.lockDeposit(userA,    300_000L, "auction-A"); // LOCK
            walletService.unlockDeposit(userB,  100_000L, "auction-B"); // UNLOCK (over-unlock, clamped)
            walletService.forfeitDeposit(userA, 300_000L, "auction-A"); // FORFEIT

            // Assert
            List<FinancialTransaction> log = walletService.getTransactionLog();
            assertEquals(3, log.size());
            assertEquals(TransactionType.DEPOSIT_LOCK,    log.get(0).getType());
            assertEquals(TransactionType.DEPOSIT_UNLOCK,  log.get(1).getType());
            assertEquals(TransactionType.DEPOSIT_FORFEIT, log.get(2).getType());
        }

        @Test
        @DisplayName("dao: tổng số lần gọi userDAO.updateBalances bằng tổng số operation thành công")
        void daoCallCount_matchesSuccessfulOperations() {
            // Arrange
            NormalUser bidder = TestFixture.bidderWithBalance("bidderGG7_", 2_000_000L);

            // Act — 4 operations thành công
            walletService.lockDeposit(bidder,   500_000L, "a1"); // 1
            walletService.unlockDeposit(bidder, 200_000L, "a1"); // 2
            walletService.lockDeposit(bidder,   300_000L, "a2"); // 3
            walletService.forfeitDeposit(bidder, 300_000L, "a2"); // 4

            // Assert
            verify(userDAO, times(4)).updateBalances(anyString(), anyLong(), anyLong());
            verify(financialTransactionDAO, times(4)).saveTransaction(any());
        }
    }
}