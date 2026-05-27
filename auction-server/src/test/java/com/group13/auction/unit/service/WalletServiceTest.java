package com.group13.auction.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.WalletService;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests cho {@link WalletService} — lock/unlock/forfeit cọc. */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService")
class WalletServiceTest {

  private static final String AUCTION_ID = "auction-001";

  @Mock private UserDAO userDAO;
  @Mock private FinancialTransactionDAO financialTransactionDAO;

  private WalletService walletService;

  @BeforeEach
  void setUp() throws Exception {
    TestFixture.resetSystemBankBalance();
    walletService =
        new WalletService(financialTransactionDAO, userDAO, TestFixture.ratingServiceAllowAll());
    lenient().when(userDAO.updateBalances(anyString(), anyLong(), anyLong())).thenReturn(true);
    lenient().when(financialTransactionDAO.saveTransaction(any())).thenReturn(true);
  }

  @Nested
  @DisplayName("lockDeposit")
  class LockDeposit {

    @Test
    void sufficientBalance_locksDeposit() {
      NormalUser bidder = TestFixture.bidderWithBalance("wallet01", 1_000_000L);
      walletService.lockDeposit(bidder, 300_000L, AUCTION_ID);
      assertEquals(300_000L, bidder.getLockedDeposit());
      assertEquals(700_000L, bidder.getAvailableBalance());
      verify(financialTransactionDAO).saveTransaction(any());
    }

    @Test
    void insufficientBalance_throws() {
      NormalUser bidder = TestFixture.bidderWithBalance("wallet02", 100_000L);
      assertThrows(
          AuctionBusinessException.class,
          () -> walletService.lockDeposit(bidder, 200_000L, AUCTION_ID));
    }
  }

  @Nested
  @DisplayName("unlockDeposit")
  class UnlockDeposit {

    @Test
    void unlock_reducesLockedDeposit() {
      NormalUser bidder = TestFixture.bidderWithBalance("wallet03", 1_000_000L);
      walletService.lockDeposit(bidder, 400_000L, AUCTION_ID);
      walletService.unlockDeposit(bidder, 150_000L, AUCTION_ID);
      assertEquals(250_000L, bidder.getLockedDeposit());
    }

    @Test
    void unlockMoreThanLocked_clampsToZero() {
      NormalUser bidder = TestFixture.bidderWithBalance("wallet04", 1_000_000L);
      walletService.lockDeposit(bidder, 100_000L, AUCTION_ID);
      walletService.unlockDeposit(bidder, 500_000L, AUCTION_ID);
      assertEquals(0L, bidder.getLockedDeposit());
    }
  }

  @Nested
  @DisplayName("forfeitDeposit")
  class ForfeitDeposit {

    @Test
    void forfeit_movesToSystemBank() throws Exception {
      NormalUser bidder = TestFixture.bidderWithBalance("wallet05", 1_000_000L);
      walletService.lockDeposit(bidder, 200_000L, AUCTION_ID);
      walletService.forfeitDeposit(bidder, 200_000L, AUCTION_ID);
      assertEquals(0L, bidder.getLockedDeposit());
      assertEquals(200_000L, SystemBank.getInstance().getTotalBalance());
    }
  }
}
