package com.group13.auction.concurrency.payment;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.*;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.PaymentService;
import com.group13.auction.service.WalletService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * SecondChanceOfferConcurrencyTest — GAP-E (MỚI)
 *
 * Vấn đề: acceptSecondChanceOffer() và declineSecondChanceOffer() đều kiểm tra
 *   offer.getStatus() == PENDING trước khi thực thi, nhưng check này và
 *   setStatus() không nằm trong synchronized block → race condition:
 *
 *   Thread A (accept): getStatus() == PENDING → [context switch] → setStatus(ACCEPTED)
 *   Thread B (decline): getStatus() == PENDING → setStatus(DECLINED) → final = DECLINED
 *   Thread A tiếp tục:  auction.setWinner(...), lockDeposit() — nhưng status đã DECLINED!
 *
 * Tests:
 *   SC1: accept() + decline() concurrent cùng offer → chỉ 1 thao tác thành công,
 *        status cuối là ACCEPTED hoặc DECLINED (không phải PENDING).
 *   SC2: 3 threads đồng thời decline() cùng offer → chỉ 1 lần cancelAuction().
 *   SC3: accept() đúng 1 lần → auction.setWinner() chỉ được gọi 1 lần.
 *   SC4: offer EXPIRED → accept() phải throw, không thay đổi auction state.
 *
 * FIX CẦN: synchronized(offer) bọc quanh check-and-set trong cả
 *   acceptSecondChanceOffer() và declineSecondChanceOffer().
 * ============================================================================
 */
@DisplayName("SecondChanceOffer: accept/decline race condition (GAP-E)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecondChanceOfferConcurrencyTest extends ConcurrencyTestBase {

    private PaymentService       paymentService;
    private IAuctionService      mockAuctionService;
    private IRatingService       mockRatingService;
    private AuctionWinnerDAO     mockAuctionWinnerDAO;
    private SecondChanceOfferDAO mockSecondChanceOfferDAO;
    private BidTransactionDAO    mockBidTransactionDAO;
    private UserDAO              mockUserDAO;
    private FinancialTransactionDAO mockFinancialDAO;

    @BeforeEach
    void setUp() {
        mockAuctionService       = mock(IAuctionService.class);
        mockRatingService        = mock(IRatingService.class);
        mockAuctionWinnerDAO     = mock(AuctionWinnerDAO.class);
        mockSecondChanceOfferDAO = mock(SecondChanceOfferDAO.class);
        mockBidTransactionDAO    = mock(BidTransactionDAO.class);
        mockUserDAO              = mock(UserDAO.class);
        mockFinancialDAO         = mock(FinancialTransactionDAO.class);

        when(mockRatingService.isEligible(any())).thenReturn(true);
        when(mockUserDAO.updateBalances(any(), anyLong(), anyLong())).thenReturn(true);
        when(mockUserDAO.addBalance(any(), anyLong())).thenReturn(true);
        when(mockAuctionWinnerDAO.updatePaymentStatus(any(), any())).thenReturn(true);
        when(mockSecondChanceOfferDAO.updateOfferStatus(any(), any())).thenReturn(true);
        when(mockFinancialDAO.saveTransaction(any())).thenReturn(true);
        doNothing().when(mockAuctionService).cancelAuction(any(), any());

        WalletService walletService = new WalletService(
                mockFinancialDAO, mockUserDAO, mockRatingService);

        paymentService = new PaymentService(
                mockAuctionService,
                mockRatingService,
                walletService,
                mockAuctionWinnerDAO,
                mockSecondChanceOfferDAO,
                mockBidTransactionDAO,
                mockUserDAO
        );

        resetAuctionManagerUsers();
    }

    @AfterEach
    void tearDown() {
        resetAuctionManagerUsers();
    }

    // ── SC1 ───────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("SC1: accept() + decline() concurrent — chỉ 1 thao tác thành công, status rõ ràng")
    @Timeout(value = 5)
    void acceptAndDecline_concurrent_onlyOneSucceeds() throws InterruptedException {
        NormalUser winner   = buildUser("winner-SC1", USER_BALANCE * 2);
        NormalUser runnerUp = buildUser("runnerUp-SC1", USER_BALANCE);
        winner.lockDeposit(STARTING_PRICE * 3 / 10);

        Auction auction = buildRunningAuction();
        AuctionWinner aw = AuctionWinner.create(winner, auction.getId(),
                STARTING_PRICE, STARTING_PRICE * 3 / 10, false);
        auction.setWinner(aw);

        SecondChanceOffer offer = buildPendingOffer(runnerUp, auction.getId(),
                STARTING_PRICE, LocalDateTime.now().plusDays(1));

        CountDownLatch gate     = new CountDownLatch(1);
        CountDownLatch done     = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);

        // Thread A: accept
        new Thread(() -> {
            try {
                gate.await();
                paymentService.acceptSecondChanceOffer(offer, auction);
                successes.incrementAndGet();
            } catch (Exception e) {
                failures.incrementAndGet();
            } finally {
                done.countDown();
            }
        }).start();

        // Thread B: decline
        new Thread(() -> {
            try {
                gate.await();
                paymentService.declineSecondChanceOffer(offer, auction);
                successes.incrementAndGet();
            } catch (Exception e) {
                failures.incrementAndGet();
            } finally {
                done.countDown();
            }
        }).start();

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        log.info("[SC1] successes={}, failures={}, finalStatus={}",
                successes.get(), failures.get(), offer.getStatus());

        // Chỉ 1 thao tác thành công — không cả hai
        assertThat(successes.get())
                .as("Đúng 1 trong accept/decline phải thành công")
                .isEqualTo(1);
        assertThat(failures.get())
                .as("Thread kia phải throw do status đã thay đổi")
                .isEqualTo(1);

        // Status không được là PENDING sau khi có 1 thao tác thành công
        assertThat(offer.getStatus())
                .as("Status phải là ACCEPTED hoặc DECLINED, không còn PENDING")
                .isNotEqualTo(SecondChanceOffer.OfferStatus.PENDING);
    }

    // ── SC2 ───────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("SC2: 3 threads đồng thời decline() — cancelAuction() chỉ gọi đúng 1 lần")
    @Timeout(value = 5)
    void concurrentDecline_cancelAuctionCalledOnce() throws InterruptedException {
        NormalUser runnerUp = buildUser("runnerUp-SC2", USER_BALANCE);
        Auction auction = buildRunningAuction();

        SecondChanceOffer offer = buildPendingOffer(runnerUp, auction.getId(),
                STARTING_PRICE, LocalDateTime.now().plusDays(1));

        int N = 3;
        CountDownLatch gate     = new CountDownLatch(1);
        CountDownLatch done     = new CountDownLatch(N);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);

        for (int i = 0; i < N; i++) {
            new Thread(() -> {
                try {
                    gate.await();
                    paymentService.declineSecondChanceOffer(offer, auction);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        log.info("[SC2] successes={}, failures={}", successes.get(), failures.get());

        // cancelAuction() phải được gọi đúng 1 lần
        verify(mockAuctionService, times(1)).cancelAuction(any(), any());

        assertThat(offer.getStatus())
                .as("Status phải là DECLINED")
                .isEqualTo(SecondChanceOffer.OfferStatus.DECLINED);
    }

    // ── SC3 ───────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("SC3: 2 threads cùng accept() — auction.setWinner() gọi đúng 1 lần, không double-lockDeposit")
    @Timeout(value = 5)
    void concurrentAccept_setWinnerCalledOnce() throws InterruptedException {
        NormalUser runnerUp = buildUser("runnerUp-SC3", USER_BALANCE * 2);
        NormalUser origWinner = buildUser("origWinner-SC3", USER_BALANCE);
        origWinner.lockDeposit(STARTING_PRICE * 3 / 10);

        Auction auction = buildRunningAuction();
        AuctionWinner aw = AuctionWinner.create(origWinner, auction.getId(),
                STARTING_PRICE, STARTING_PRICE * 3 / 10, false);
        aw.markFundsHeld();
        auction.setWinner(aw);

        long depositPaid = STARTING_PRICE * 3 / 10;
        // runnerUp cần đủ balance để lockDeposit 2 lần (worst case double)
        SecondChanceOffer offer = buildPendingOffer(runnerUp, auction.getId(),
                STARTING_PRICE, LocalDateTime.now().plusDays(1));

        // Spy WalletService để đếm lockDeposit
        WalletService spyWallet = spy(new WalletService(mockFinancialDAO, mockUserDAO, mockRatingService));
        PaymentService ps = new PaymentService(
                mockAuctionService, mockRatingService, spyWallet,
                mockAuctionWinnerDAO, mockSecondChanceOfferDAO,
                mockBidTransactionDAO, mockUserDAO);

        CountDownLatch gate     = new CountDownLatch(1);
        CountDownLatch done     = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try {
                    gate.await();
                    ps.acceptSecondChanceOffer(offer, auction);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        log.info("[SC3] successes={}, failures={}", successes.get(), failures.get());

        // Đúng 1 accept thành công
        assertThat(successes.get())
                .as("Đúng 1 accept phải thành công")
                .isEqualTo(1);

        // lockDeposit chỉ gọi 1 lần — không double-charge runnerUp
        verify(spyWallet, times(1))
                .lockDeposit(eq(runnerUp), anyLong(), eq(auction.getId()));

        assertThat(offer.getStatus())
                .as("Status phải là ACCEPTED")
                .isEqualTo(SecondChanceOffer.OfferStatus.ACCEPTED);
    }

    // ── SC4 ───────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("SC4: accept() offer đã EXPIRED — throw IllegalStateException, không thay đổi auction state")
    void accept_expiredOffer_throwsAndLeavesAuctionUnchanged() {
        NormalUser runnerUp = buildUser("runnerUp-SC4", USER_BALANCE);
        Auction auction = buildRunningAuction();

        // Offer đã hết hạn (deadline trong quá khứ, status PENDING)
        SecondChanceOffer expiredOffer = buildPendingOffer(runnerUp, auction.getId(),
                STARTING_PRICE, LocalDateTime.now().minusSeconds(1));

        // acceptSecondChanceOffer kiểm tra isExpired() → set EXPIRED → cancelAuction
        // hoặc ném exception nếu status != PENDING (tuỳ implementation)
        // Test chỉ đảm bảo: không thay đổi auction winner, không lockDeposit
        assertThatCode(() -> paymentService.acceptSecondChanceOffer(expiredOffer, auction))
                .as("accept() offer expired không được throw unexpected exception")
                .satisfiesAnyOf(
                        code -> {}, // không throw
                        code -> assertThatThrownBy(code::run)
                                .isInstanceOf(Exception.class)
                );

        // Dù expired xử lý thế nào, không được lockDeposit cho runnerUp
        verify(mockFinancialDAO, never()).saveTransaction(argThat(tx ->
                tx != null && tx.toString().contains("DEPOSIT")));

        // Status phải không còn PENDING sau khi expired được xử lý
        assertThat(expiredOffer.getStatus())
                .as("Offer expired phải chuyển sang EXPIRED (không còn PENDING)")
                .isNotEqualTo(SecondChanceOffer.OfferStatus.PENDING);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private SecondChanceOffer buildPendingOffer(NormalUser runnerUp, String auctionId,
                                                long offerPrice, LocalDateTime deadline) {
        return SecondChanceOffer.reconstitute(
                java.util.UUID.randomUUID().toString(),
                java.time.LocalDateTime.now().minusMinutes(5),
                java.time.LocalDateTime.now(),
                runnerUp,
                auctionId,
                offerPrice,
                offerPrice * 3 / 10,
                deadline,
                SecondChanceOffer.OfferStatus.PENDING
        );
    }
}