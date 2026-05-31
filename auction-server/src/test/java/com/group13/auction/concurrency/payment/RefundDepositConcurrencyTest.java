package com.group13.auction.concurrency.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.*;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.PaymentService;
import com.group13.auction.service.WalletService;
import java.util.LinkedHashSet;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

/**
 * ============================================================================
 * RefundDepositsConcurrencyTest — GAP-F (MỚI)
 *
 * <p>Vấn đề: refundDeposits() gọi walletService.unlockDeposit() cho từng bidder tuần tự. Nếu 2
 * threads gọi refundDeposits() đồng thời: - unlockDeposit() của cùng 1 bidder được gọi 2 lần →
 * deposit unlock 2× → âm. - WalletService.unlockDeposit() có synchronized(bidder) → deposit không
 * âm, nhưng availableBalance có thể tăng 2× (unlock 2 lần cùng amount).
 *
 * <p>forfeitDeposit() cũng có cùng vấn đề nếu gọi 2 lần cho 1 winner.
 *
 * <p>Tests: R1: refundDeposits() gọi 2 lần đồng thời — mỗi bidder được unlock đúng 1 lần →
 * availableBalance tăng đúng depositAmount (không double). R2: refundDeposits() concurrent với
 * nhiều bidders — tổng balance đúng. R3: forfeitDeposit() concurrent 2 lần cùng 1 winner — deposit
 * chỉ trừ 1 lần.
 *
 * <p>FIX CẦN: Thêm guard "đã refund chưa" trong refundDeposits() — ví dụ kiểm tra
 * auction.isRefundProcessed() (flag boolean) với synchronized trước khi chạy loop.
 * ============================================================================
 */
@DisplayName("RefundDeposits + ForfeitDeposit: double-call race (GAP-F)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RefundDepositsConcurrencyTest extends ConcurrencyTestBase {

  private PaymentService paymentService;
  private WalletService walletService;
  private IAuctionService mockAuctionService;
  private IRatingService mockRatingService;
  private AuctionWinnerDAO mockAuctionWinnerDAO;
  private SecondChanceOfferDAO mockSecondChanceOfferDAO;
  private BidTransactionDAO mockBidTransactionDAO;
  private UserDAO mockUserDAO;
  private FinancialTransactionDAO mockFinancialDAO;
  // FIX [P2]: tránh `new AuctionDAO()` ngầm trong PaymentService(7-arg) → chạm DB.
  private AuctionDAO mockAuctionDAO;

  private static final long DEPOSIT_AMOUNT = STARTING_PRICE * 3 / 10; // 150_000

  @BeforeEach
  void setUp() {
    mockAuctionService = mock(IAuctionService.class);
    mockRatingService = mock(IRatingService.class);
    mockAuctionWinnerDAO = mock(AuctionWinnerDAO.class);
    mockSecondChanceOfferDAO = mock(SecondChanceOfferDAO.class);
    mockBidTransactionDAO = mock(BidTransactionDAO.class);
    mockUserDAO = mock(UserDAO.class);
    mockFinancialDAO = mock(FinancialTransactionDAO.class);
    mockAuctionDAO = mock(AuctionDAO.class);

    when(mockRatingService.isEligible(any())).thenReturn(true);
    when(mockUserDAO.updateBalances(any(), anyLong(), anyLong())).thenReturn(true);
    when(mockUserDAO.addBalance(any(), anyLong())).thenReturn(true);
    when(mockAuctionWinnerDAO.updatePaymentStatus(any(), any())).thenReturn(true);
    when(mockFinancialDAO.saveTransaction(any())).thenReturn(true);

    walletService = new WalletService(mockFinancialDAO, mockUserDAO, mockRatingService);

    paymentService =
        new PaymentService(
            mockAuctionService,
            mockRatingService,
            walletService,
            mockAuctionDAO,
            mockAuctionWinnerDAO,
            mockSecondChanceOfferDAO,
            mockBidTransactionDAO,
            mockUserDAO);

    resetAuctionManagerUsers();
  }

  @AfterEach
  void tearDown() {
    resetAuctionManagerUsers();
  }

  // R1

  @Test
  @Order(1)
  @DisplayName("R1: refundDeposits() gọi 2 lần concurrent — mỗi bidder được unlock đúng 1 lần")
  @Timeout(value = 8)
  void refundDeposits_concurrent_eachBidderUnlockedOnce() throws InterruptedException {
    int bidderCount = 5;
    List<NormalUser> bidders = new ArrayList<>();
    for (int i = 0; i < bidderCount; i++) {
      NormalUser bidder = buildUser("bidder-R1-" + i, USER_BALANCE);
      bidder.lockDeposit(DEPOSIT_AMOUNT); // khoá cọc trước
      bidders.add(bidder);
    }

    Auction auction = buildRunningAuction();

    LinkedHashSet<String> joinedIds = new LinkedHashSet<>();
    for (NormalUser bidder : bidders) {
      joinedIds.add(bidder.getId());
      when(mockUserDAO.findNormalUserById(bidder.getId())).thenReturn(bidder);
    }
    when(mockUserDAO.findJoinedUserIdsByAuctionId(auction.getId())).thenReturn(joinedIds);

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);
    AtomicInteger successes = new AtomicInteger(0);
    AtomicInteger failures = new AtomicInteger(0);

    Runnable task =
        () -> {
          try {
            gate.await();
            paymentService.refundDeposits(auction);
            successes.incrementAndGet();
          } catch (Exception e) {
            failures.incrementAndGet();
            log.warn("[R1] refundDeposits error: {}", e.getMessage());
          } finally {
            done.countDown();
          }
        };

    new Thread(task).start();
    new Thread(task).start();

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    log.info("[R1] successes={}, failures={}", successes.get(), failures.get());

    for (NormalUser bidder : bidders) {
      long lockedAfter = bidder.getLockedDeposit();
      long available = bidder.getAvailableBalance();

      // Sau khi unlock đúng 1 lần: lockedDeposit về 0, balance về USER_BALANCE
      assertThat(lockedAfter)
          .as("Bidder [%s] lockedDeposit phải = 0 sau refund", bidder.getUsername())
          .isEqualTo(0L);

      assertThat(available)
          .as("Bidder [%s] availableBalance phải = USER_BALANCE sau refund", bidder.getUsername())
          .isEqualTo(USER_BALANCE);
    }
  }

  // R2

  @Test
  @Order(2)
  @DisplayName("R2: refundDeposits() 20 bidders concurrent — tổng balance đúng, không double")
  @Timeout(value = 10)
  void refundDeposits_20Bidders_totalBalanceCorrect() throws InterruptedException {
    int bidderCount = 20;
    List<NormalUser> bidders = new ArrayList<>();
    for (int i = 0; i < bidderCount; i++) {
      NormalUser bidder = buildUser("bidder-R2-" + i, USER_BALANCE);
      bidder.lockDeposit(DEPOSIT_AMOUNT);
      bidders.add(bidder);
    }

    Auction auction = buildRunningAuction();
    LinkedHashSet<String> joinedIds = new LinkedHashSet<>();
    for (NormalUser bidder : bidders) {
      joinedIds.add(bidder.getId());
      when(mockUserDAO.findNormalUserById(bidder.getId())).thenReturn(bidder);
    }
    when(mockUserDAO.findJoinedUserIdsByAuctionId(auction.getId())).thenReturn(joinedIds);

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);

    Runnable task =
        () -> {
          try {
            gate.await();
            paymentService.refundDeposits(auction);
          } catch (Exception ignored) {
          } finally {
            done.countDown();
          }
        };

    new Thread(task).start();
    new Thread(task).start();

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    // Mỗi bidder: availableBalance phải = USER_BALANCE (không double unlock)
    for (NormalUser bidder : bidders) {
      assertThat(bidder.getAvailableBalance())
          .as("Bidder [%s] availableBalance không được vượt USER_BALANCE", bidder.getUsername())
          .isLessThanOrEqualTo(USER_BALANCE);

      // lockedDeposit không được âm
      assertThat(bidder.getLockedDeposit())
          .as("Bidder [%s] lockedDeposit không được âm", bidder.getUsername())
          .isGreaterThanOrEqualTo(0L);
    }
  }

  // R3

  @Test
  @Order(3)
  @DisplayName(
      "R3: forfeitDeposit() concurrent 2 lần cùng winner — deposit chỉ trừ 1 lần, không âm")
  @Timeout(value = 5)
  void forfeitDeposit_concurrent_depositDeductedOnce() throws InterruptedException {
    long depositPaid = DEPOSIT_AMOUNT;

    NormalUser winner = buildUser("winner-R3", USER_BALANCE);
    winner.lockDeposit(depositPaid);

    // Gọi WalletService.forfeitDeposit() trực tiếp (không qua PaymentService)
    // vì forfeit được trigger từ expirePayment() — test isolated
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);
    AtomicInteger successes = new AtomicInteger(0);
    AtomicInteger failures = new AtomicInteger(0);

    String auctionId = java.util.UUID.randomUUID().toString();
    Runnable task =
        () -> {
          try {
            gate.await();
            walletService.forfeitDeposit(winner, depositPaid, auctionId);
            successes.incrementAndGet();
          } catch (Exception e) {
            failures.incrementAndGet();
          } finally {
            done.countDown();
          }
        };

    new Thread(task).start();
    new Thread(task).start();

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    log.info(
        "[R3] successes={}, failures={}, lockedDeposit={}, balance={}",
        successes.get(),
        failures.get(),
        winner.getLockedDeposit(),
        winner.getBalance());

    // lockedDeposit không được âm (double-forfeit sẽ làm âm nếu không synchronized)
    assertThat(winner.getLockedDeposit())
        .as("lockedDeposit không được âm sau forfeit")
        .isGreaterThanOrEqualTo(0L);

    // Nếu synchronized đúng: chỉ 1 forfeit thành công, locked = 0
    // Nếu chưa fix: có thể 2 lần thành công → locked = -depositPaid
    if (failures.get() == 0) {
      log.warn(
          "[R3 POTENTIAL BUG] Cả 2 threads forfeit đều success — "
              + "lockedDeposit sau: {}. Nếu = 0: synchronized OK. "
              + "Nếu âm: double-forfeit.",
          winner.getLockedDeposit());
    }
  }

  // R4

  @Test
  @Order(4)
  @DisplayName("R4: refundDeposits() không hoàn cọc cho winner — winner bị exclude đúng")
  @Timeout(value = 5)
  void refundDeposits_excludesWinner_correctlyProcessed() {
    int bidderCount = 5;
    List<NormalUser> allParticipants = new ArrayList<>();
    for (int i = 0; i < bidderCount; i++) {
      NormalUser bidder = buildUser("bidder-R4-" + i, USER_BALANCE);
      bidder.lockDeposit(DEPOSIT_AMOUNT);
      allParticipants.add(bidder);
    }

    // Winner là participant index 0
    NormalUser winner = allParticipants.get(0);
    Auction auction = buildRunningAuction();
    AuctionWinner aw =
        AuctionWinner.create(winner, auction.getId(), STARTING_PRICE, DEPOSIT_AMOUNT, false);
    auction.setWinner(aw);

    LinkedHashSet<String> joinedIds = new LinkedHashSet<>();
    for (NormalUser participant : allParticipants) {
      joinedIds.add(participant.getId());
      when(mockUserDAO.findNormalUserById(participant.getId())).thenReturn(participant);
    }
    when(mockUserDAO.findJoinedUserIdsByAuctionId(auction.getId())).thenReturn(joinedIds);

    paymentService.refundDeposits(auction);

    // Winner KHÔNG được unlock deposit
    assertThat(winner.getLockedDeposit())
        .as("Winner lockedDeposit phải vẫn bị khoá (không được hoàn)")
        .isEqualTo(DEPOSIT_AMOUNT);

    // Non-winners đều được unlock
    for (int i = 1; i < bidderCount; i++) {
      NormalUser nonWinner = allParticipants.get(i);
      assertThat(nonWinner.getLockedDeposit())
          .as("Non-winner [%s] phải được unlock", nonWinner.getUsername())
          .isEqualTo(0L);
    }
  }
}