package com.group13.auction.service;

import com.group13.auction.TestFixture;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.AuctionWinner.PaymentStatus;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionEvent.AuctionEventType;
import com.group13.auction.service.PaymentService;
import com.group13.auction.service.WalletService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link PaymentService}.
 *
 * <p>Tập trung vào orchestration và business logic của:
 * <ul>
 *   <li>{@code completePayment} — happy path, expired, no-winner.</li>
 *   <li>{@code expirePayment}   — forfeit + penalize + second chance flow.</li>
 *   <li>Second-chance flow      — offer tạo, accept, decline, expired.</li>
 * </ul>
 *
 * <p>Mọi external dependency đều được mock.
 * Không DB, không network, không filesystem.
 * Chỉ verify interaction quan trọng ảnh hưởng business behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────

    @Mock IAuctionService    auctionService;
    @Mock IRatingService     ratingService;
    @Mock WalletService      walletService;
    @Mock AuctionWinnerDAO   auctionWinnerDAO;
    @Mock SecondChanceOfferDAO secondChanceOfferDAO;
    @Mock BidTransactionDAO  bidTransactionDAO;
    @Mock UserDAO            userDAO;

    PaymentService paymentService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    NormalUser seller;
    NormalUser winner;
    NormalUser runnerUp;

    static final long STARTING_PRICE = 1_000_000L;
    static final long FINAL_PRICE    = 3_000_000L;
    static final long DEPOSIT        =   300_000L; // 30% startingPrice

    @BeforeEach
    void setUp() throws Exception {
        // Bootstrap SystemAdmin Singleton không qua DB — bắt buộc vì
        // expirePayment() gọi SystemAdmin.getInstance().autoBanIfNeeded()
        TestFixture.bootstrapSystemAdmin();

        paymentService = new PaymentService(
                auctionService, ratingService, walletService,
                auctionWinnerDAO, secondChanceOfferDAO,
                bidTransactionDAO, userDAO);

        seller   = TestFixture.normalSeller("sellerXX1");
        winner   = TestFixture.bidderWithBalance("winnerYY2", 5_000_000L);
        runnerUp = TestFixture.bidderWithBalance("runnerZZ3", 4_000_000L);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Reset Singleton để tránh state rò rỉ giữa các test
        TestFixture.resetSystemAdmin();
    }

    // =========================================================================
    // completePayment
    // =========================================================================

    @Nested
    @DisplayName("completePayment()")
    class CompletePayment {

        // --- Happy path ---

        @Test
        @DisplayName("happy path: chuyển tiền vào bank, markFundsHeld, markAsPaid, rewardBidder")
        void happyPath_fullOrchestration() {
            // Arrange
            Auction auction = finishedAuctionWithPendingWinner();
            AuctionWinner aw = auction.getWinner();

            // Act
            paymentService.completePayment(auction);

            // Assert — các interaction quan trọng theo thứ tự
            InOrder order = inOrder(walletService, auctionService, ratingService);
            order.verify(walletService).executePaymentToBank(
                    winner, FINAL_PRICE, DEPOSIT, auction.getId());
            order.verify(auctionService).markAsPaid(auction);
            order.verify(ratingService).rewardBidder(winner);

            // State transition
            assertEquals(PaymentStatus.FUNDS_HELD, aw.getPaymentStatus());
            assertNotNull(aw.getConfirmReceiptDeadline(),
                    "confirmReceiptDeadline phải được ghi sau FUNDS_HELD");
        }

        @Test
        @DisplayName("happy path: persist trạng thái FUNDS_HELD xuống DB")
        void happyPath_persistsFundsHeldStatus() {
            // Arrange
            Auction auction = finishedAuctionWithPendingWinner();
            AuctionWinner aw = auction.getWinner();

            // Act
            paymentService.completePayment(auction);

            // Assert — DAO được gọi với status đúng
            verify(auctionWinnerDAO).updatePaymentStatus(
                    aw.getId(), PaymentStatus.FUNDS_HELD.name());
        }

        @Test
        @DisplayName("happy path: notify PAYMENT_COMPLETED với đúng winner và finalPrice")
        void happyPath_notifiesPaymentCompleted() {
            // Arrange
            Auction auction = finishedAuctionWithPendingWinner();

            // Act
            paymentService.completePayment(auction);

            // Assert
            verify(auctionService).notify(
                    auction, AuctionEventType.PAYMENT_COMPLETED,
                    winner, FINAL_PRICE);
        }

        @Test
        @DisplayName("happy path: remainingAmount = finalPrice − depositPaid")
        void happyPath_remainingAmountCorrect() {
            // Arrange
            Auction auction = finishedAuctionWithPendingWinner();
            AuctionWinner aw = auction.getWinner();
            long expectedRemaining = FINAL_PRICE - DEPOSIT;

            // Act & Assert — trước khi thanh toán
            assertEquals(expectedRemaining, aw.getRemainingAmount());

            // Sau khi thanh toán: finalPrice và deposit không thay đổi
            paymentService.completePayment(auction);
            assertEquals(expectedRemaining, aw.getRemainingAmount());
        }

        @Test
        @DisplayName("remainingAmount = 0 khi deposit >= finalPrice (edge: deposit cover hết)")
        void remainingAmount_zeroWhenDepositCoversAll() {
            // Arrange — deposit = finalPrice
            Auction auction = finishedAuction();
            AuctionWinner aw = TestFixture.pendingWinner(
                    winner, auction.getId(), FINAL_PRICE, FINAL_PRICE);
            auction.setWinner(aw);

            // Assert
            assertEquals(0L, aw.getRemainingAmount());
        }

        // --- Payment expired: gọi completePayment khi đã quá hạn ---

        @Test
        @DisplayName("expired winner: ném PaymentException(PAYMENT_EXPIRED)")
        void expiredWinner_throwsPaymentException() {
            // Arrange
            Auction auction = finishedAuction();
            AuctionWinner expiredWinner = TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT);
            auction.setWinner(expiredWinner);

            // Act & Assert
            PaymentException ex = assertThrows(PaymentException.class,
                    () -> paymentService.completePayment(auction));
            assertEquals(PaymentException.Reason.PAYMENT_EXPIRED, ex.getReason());
        }

        @Test
        @DisplayName("expired winner: wallet KHÔNG được gọi")
        void expiredWinner_walletNotCalled() {
            // Arrange
            Auction auction = finishedAuction();
            auction.setWinner(TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT));

            // Act
            assertThrows(PaymentException.class,
                    () -> paymentService.completePayment(auction));

            // Assert — không chuyển tiền
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("expired winner: status KHÔNG đổi (vẫn PENDING)")
        void expiredWinner_statusUnchanged() {
            // Arrange
            Auction auction = finishedAuction();
            AuctionWinner expiredWinner = TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT);
            auction.setWinner(expiredWinner);

            // Act
            assertThrows(PaymentException.class,
                    () -> paymentService.completePayment(auction));

            // Assert
            assertEquals(PaymentStatus.PENDING, expiredWinner.getPaymentStatus());
        }

        // --- No winner ---

        @Test
        @DisplayName("auction không có winner: ném IllegalStateException")
        void noWinner_throwsIllegalStateException() {
            // Arrange — auction chưa set winner
            Auction auction = finishedAuction();

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> paymentService.completePayment(auction));
        }

        @Test
        @DisplayName("auction không có winner: không gọi bất kỳ service nào")
        void noWinner_noServicesCalled() {
            // Arrange
            Auction auction = finishedAuction();

            // Act
            assertThrows(IllegalStateException.class,
                    () -> paymentService.completePayment(auction));

            // Assert
            verifyNoInteractions(walletService, ratingService);
        }
    }

    // =========================================================================
    // expirePayment
    // =========================================================================

    @Nested
    @DisplayName("expirePayment()")
    class ExpirePayment {

        // --- Happy path: winner không thanh toán đúng hạn ---

        @Test
        @DisplayName("expired winner: forfeit deposit, penalize, set EXPIRED, offer second chance")
        void expiredWinner_fullExpireFlow() {
            // Arrange
            Auction auction = finishedAuction();
            AuctionWinner aw = TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT);
            auction.setWinner(aw);
            when(bidTransactionDAO.findHighestValidBidExcept(
                    auction.getId(), winner.getId()))
                    .thenReturn(null); // không có runner-up

            // Act
            paymentService.expirePayment(auction);

            // Assert — thứ tự forfeit → penalize
            InOrder order = inOrder(walletService, ratingService);
            order.verify(walletService).forfeitDeposit(winner, DEPOSIT, auction.getId());
            order.verify(ratingService).penalizeLatePayment(winner);

            // Status transition
            assertEquals(PaymentStatus.EXPIRED, aw.getPaymentStatus());
        }

        @Test
        @DisplayName("expired winner: persist trạng thái EXPIRED xuống DB")
        void expiredWinner_persistsExpiredStatus() {
            // Arrange
            Auction auction = finishedAuction();
            AuctionWinner aw = TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT);
            auction.setWinner(aw);
            when(bidTransactionDAO.findHighestValidBidExcept(any(), any()))
                    .thenReturn(null);

            // Act
            paymentService.expirePayment(auction);

            // Assert
            verify(auctionWinnerDAO).updatePaymentStatus(
                    aw.getId(), PaymentStatus.EXPIRED.name());
        }

        // --- Không expired: gọi expirePayment quá sớm ---

        @Test
        @DisplayName("winner chưa expired: không forfeit, không penalize, status giữ PENDING")
        void notExpiredYet_noActionTaken() {
            // Arrange — winner còn trong hạn 24h
            Auction auction = finishedAuction();
            AuctionWinner aw = TestFixture.pendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT);
            auction.setWinner(aw);

            // Act
            paymentService.expirePayment(auction);

            // Assert — không có side effect
            verifyNoInteractions(walletService, ratingService);
            assertEquals(PaymentStatus.PENDING, aw.getPaymentStatus());
        }

        // --- Second offer winner: cancel ngay ---

        @Test
        @DisplayName("isSecondOffer winner: cancel auction ngay, không forfeit/penalize")
        void secondOfferWinner_cancelAuctionDirectly() {
            // Arrange — winner là second-chance offer (isSecondOffer=true)
            Auction auction = finishedAuction();
            AuctionWinner secondWinner = TestFixture.secondOfferWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT);
            auction.setWinner(secondWinner);

            // Act
            paymentService.expirePayment(auction);

            // Assert — cancel ngay
            verify(auctionService).cancelAuction(
                    auction, Admin.CancelReason.NO_WINNER);
            // Không forfeit, không penalize
            verifyNoInteractions(walletService, ratingService);
        }

        @Test
        @DisplayName("isSecondOffer winner: status KHÔNG thay đổi sang EXPIRED")
        void secondOfferWinner_statusNotChangedToExpired() {
            // Arrange
            Auction auction = finishedAuction();
            AuctionWinner secondWinner = TestFixture.secondOfferWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT);
            auction.setWinner(secondWinner);

            // Act
            paymentService.expirePayment(auction);

            // Assert — vẫn PENDING (cancel flow không đổi status này)
            assertNotEquals(PaymentStatus.EXPIRED, secondWinner.getPaymentStatus());
        }

        // --- No winner ---

        @Test
        @DisplayName("auction không có winner: ném IllegalStateException")
        void noWinner_throwsIllegalStateException() {
            // Arrange
            Auction auction = finishedAuction();

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> paymentService.expirePayment(auction));
        }

        // --- failed payment flow: state integrity ---

        @Test
        @DisplayName("forfeit và penalize đều phải xảy ra, không skip nhau")
        void forfeitAndPenalize_bothExecuted() {
            // Arrange
            Auction auction = finishedAuction();
            auction.setWinner(TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT));
            when(bidTransactionDAO.findHighestValidBidExcept(any(), any()))
                    .thenReturn(null);

            // Act
            paymentService.expirePayment(auction);

            // Assert — cả hai đều được gọi
            verify(walletService, times(1)).forfeitDeposit(winner, DEPOSIT, auction.getId());
            verify(ratingService, times(1)).penalizeLatePayment(winner);
        }
    }

    // =========================================================================
    // Second-chance flow (offerSecondChance / createSecondChanceOffer /
    //                     acceptSecondChanceOffer / declineSecondChanceOffer)
    // =========================================================================

    @Nested
    @DisplayName("Second-chance flow")
    class SecondChanceFlow {

        // --- offerSecondChance: tìm runner-up sau khi winner expire ---

        @Test
        @DisplayName("có runner-up đủ điều kiện: tạo SecondChanceOffer và lưu DB")
        void runnerUpFound_secondChanceOfferCreatedAndSaved() {
            // Arrange
            Auction auction = finishedAuction();
            auction.setWinner(TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT));

            BidTransaction runnerUpBid = BidTransaction.create(
                    runnerUp, auction.getId(),
                    auction.getReservePrice() + 1,  // >= reservePrice
                    BidTransaction.BidResult.ACCEPTED);
            when(bidTransactionDAO.findHighestValidBidExcept(
                    auction.getId(), winner.getId()))
                    .thenReturn(runnerUpBid);

            // Act
            paymentService.expirePayment(auction);

            // Assert — SecondChanceOffer được lưu DB
            verify(secondChanceOfferDAO).saveOffer(argThat(offer ->
                    offer.getRunnerUp().equals(runnerUp)
                            && offer.getOfferPrice() == runnerUpBid.getAmount()
                            && offer.getStatus() == SecondChanceOffer.OfferStatus.PENDING));
        }

        @Test
        @DisplayName("có runner-up đủ điều kiện: notify SECOND_CHANCE_OFFERED")
        void runnerUpFound_notifiesSecondChanceOffered() {
            // Arrange
            Auction auction = finishedAuction();
            auction.setWinner(TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT));

            BidTransaction runnerUpBid = BidTransaction.create(
                    runnerUp, auction.getId(),
                    auction.getReservePrice() + 1,
                    BidTransaction.BidResult.ACCEPTED);
            when(bidTransactionDAO.findHighestValidBidExcept(
                    auction.getId(), winner.getId()))
                    .thenReturn(runnerUpBid);

            // Act
            paymentService.expirePayment(auction);

            // Assert
            verify(auctionService).notify(
                    eq(auction), eq(AuctionEventType.SECOND_CHANCE_OFFERED),
                    isNull(), eq(0L));
        }

        @Test
        @DisplayName("không có runner-up: cancel auction với NO_WINNER")
        void noRunnerUp_cancelAuction() {
            // Arrange
            Auction auction = finishedAuction();
            auction.setWinner(TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT));
            when(bidTransactionDAO.findHighestValidBidExcept(any(), any()))
                    .thenReturn(null);

            // Act
            paymentService.expirePayment(auction);

            // Assert
            verify(auctionService).cancelAuction(auction, Admin.CancelReason.NO_WINNER);
            verify(secondChanceOfferDAO, never()).saveOffer(any());
        }

        @Test
        @DisplayName("runner-up bid dưới reservePrice: cancel auction, không tạo offer")
        void runnerUpBelowReserve_cancelAuction_noOffer() {
            // Arrange
            Auction auction = finishedAuction();
            // reservePrice = startingPrice * 2 = 2_000_000
            long belowReserve = auction.getReservePrice() - 1;
            long deposit      = STARTING_PRICE * 3 / 10;

            auction.setWinner(TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT));

            BidTransaction lowBid = BidTransaction.create(
                    runnerUp, auction.getId(),
                    belowReserve,
                    BidTransaction.BidResult.ACCEPTED);
            when(bidTransactionDAO.findHighestValidBidExcept(
                    auction.getId(), winner.getId()))
                    .thenReturn(lowBid);

            // Act
            paymentService.expirePayment(auction);

            // Assert — bid dưới reserve → cancel, không save offer
            verify(auctionService, atLeastOnce())
                    .cancelAuction(auction, Admin.CancelReason.NO_WINNER);
            verify(secondChanceOfferDAO, never()).saveOffer(any());
        }

        // --- Second-chance eligibility: remainingAmount ---

        @Test
        @DisplayName("second chance offer: remainingAmount = offerPrice − depositPaid")
        void secondChanceOffer_remainingAmountCorrect() {
            // Arrange
            long offerPrice = 2_500_000L;
            long depositPaid = DEPOSIT;
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, "auction-id-001", offerPrice, depositPaid);

            // Act & Assert
            assertEquals(offerPrice - depositPaid, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("second chance offer: remainingAmount = 0 khi deposit >= offerPrice")
        void secondChanceOffer_remainingZeroWhenDepositCoversAll() {
            // Arrange
            long offerPrice  = 200_000L;
            long depositPaid = 300_000L; // deposit > offerPrice
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, "auction-id-002", offerPrice, depositPaid);

            // Act & Assert
            assertEquals(0L, offer.getRemainingAmount());
        }

        // --- acceptSecondChanceOffer ---

        @Test
        @DisplayName("accept offer PENDING: runner-up trở thành winner mới, deposit bị lock")
        void acceptOffer_pending_runnerUpBecomesNewWinner() {
            // Arrange
            Auction auction = finishedAuction();
            long offerPrice  = STARTING_PRICE * 2; // = reservePrice
            long depositPaid = DEPOSIT;
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, auction.getId(), offerPrice, depositPaid);

            // Act
            paymentService.acceptSecondChanceOffer(offer, auction);

            // Assert — runner-up trở thành winner mới trong auction
            assertNotNull(auction.getWinner());
            assertEquals(runnerUp, auction.getWinner().getWinner());
            assertEquals(offerPrice, auction.getWinner().getFinalPrice());
            assertTrue(auction.getWinner().getIsSecondOffer());
        }

        @Test
        @DisplayName("accept offer PENDING: lock deposit của runner-up")
        void acceptOffer_pending_locksRunnerUpDeposit() {
            // Arrange
            Auction auction = finishedAuction();
            long depositPaid = DEPOSIT;
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, auction.getId(), STARTING_PRICE * 2, depositPaid);

            // Act
            paymentService.acceptSecondChanceOffer(offer, auction);

            // Assert
            verify(walletService).lockDeposit(runnerUp, depositPaid, auction.getId());
        }

        @Test
        @DisplayName("accept offer PENDING: status chuyển sang ACCEPTED")
        void acceptOffer_pending_statusBecomesAccepted() {
            // Arrange
            Auction auction = finishedAuction();
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, auction.getId(), STARTING_PRICE * 2, DEPOSIT);

            // Act
            paymentService.acceptSecondChanceOffer(offer, auction);

            // Assert
            assertEquals(SecondChanceOffer.OfferStatus.ACCEPTED, offer.getStatus());
            verify(secondChanceOfferDAO).updateOfferStatus(
                    offer.getId(), SecondChanceOffer.OfferStatus.ACCEPTED.name());
        }

        @Test
        @DisplayName("accept offer ACCEPTED (không còn PENDING): ném IllegalStateException")
        void acceptOffer_alreadyAccepted_throwsIllegalState() {
            // Arrange — offer đã ACCEPTED
            Auction auction = finishedAuction();
            SecondChanceOffer acceptedOffer = TestFixture.acceptedOffer(
                    runnerUp, auction.getId(), STARTING_PRICE * 2, DEPOSIT);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> paymentService.acceptSecondChanceOffer(acceptedOffer, auction));
        }

        @Test
        @DisplayName("accept offer DECLINED: ném IllegalStateException")
        void acceptOffer_alreadyDeclined_throwsIllegalState() {
            // Arrange
            Auction auction = finishedAuction();
            SecondChanceOffer declinedOffer = TestFixture.declinedOffer(
                    runnerUp, auction.getId(), STARTING_PRICE * 2, DEPOSIT);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> paymentService.acceptSecondChanceOffer(declinedOffer, auction));
        }

        @Test
        @DisplayName("accept offer EXPIRED (deadline đã qua): hủy offer, cancel auction")
        void acceptOffer_expired_cancelAuction() {
            // Arrange — deadline đã qua, isExpired() = true
            Auction auction = finishedAuction();
            SecondChanceOffer expiredOffer = TestFixture.expiredPendingOffer(
                    runnerUp, auction.getId(), STARTING_PRICE * 2, DEPOSIT);

            // Act
            paymentService.acceptSecondChanceOffer(expiredOffer, auction);

            // Assert — offer hết hạn → cancel auction
            assertEquals(SecondChanceOffer.OfferStatus.EXPIRED, expiredOffer.getStatus());
            verify(auctionService).cancelAuction(auction, Admin.CancelReason.NO_WINNER);
            verify(walletService, never()).lockDeposit(any(), anyLong(), any());
        }

        @Test
        @DisplayName("accept offer EXPIRED: persist trạng thái EXPIRED xuống DB")
        void acceptOffer_expired_persistsExpiredStatus() {
            // Arrange
            Auction auction = finishedAuction();
            SecondChanceOffer expiredOffer = TestFixture.expiredPendingOffer(
                    runnerUp, auction.getId(), STARTING_PRICE * 2, DEPOSIT);

            // Act
            paymentService.acceptSecondChanceOffer(expiredOffer, auction);

            // Assert
            verify(secondChanceOfferDAO).updateOfferStatus(
                    expiredOffer.getId(), SecondChanceOffer.OfferStatus.EXPIRED.name());
        }

        // --- declineSecondChanceOffer ---

        @Test
        @DisplayName("decline offer PENDING: status chuyển sang DECLINED, cancel auction")
        void declineOffer_pending_statusDeclinedAndAuctionCanceled() {
            // Arrange
            Auction auction = finishedAuction();
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, auction.getId(), STARTING_PRICE * 2, DEPOSIT);

            // Act
            paymentService.declineSecondChanceOffer(offer, auction);

            // Assert
            assertEquals(SecondChanceOffer.OfferStatus.DECLINED, offer.getStatus());
            verify(auctionService).cancelAuction(auction, Admin.CancelReason.NO_WINNER);
            verify(secondChanceOfferDAO).updateOfferStatus(
                    offer.getId(), SecondChanceOffer.OfferStatus.DECLINED.name());
        }

        @Test
        @DisplayName("decline offer PENDING: wallet KHÔNG được gọi")
        void declineOffer_pending_walletNotCalled() {
            // Arrange
            Auction auction = finishedAuction();
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, auction.getId(), STARTING_PRICE * 2, DEPOSIT);

            // Act
            paymentService.declineSecondChanceOffer(offer, auction);

            // Assert
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("decline offer ACCEPTED (không còn PENDING): ném IllegalStateException")
        void declineOffer_alreadyAccepted_throwsIllegalState() {
            // Arrange
            Auction auction = finishedAuction();
            SecondChanceOffer acceptedOffer = TestFixture.acceptedOffer(
                    runnerUp, auction.getId(), STARTING_PRICE * 2, DEPOSIT);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> paymentService.declineSecondChanceOffer(acceptedOffer, auction));
        }

        @Test
        @DisplayName("decline offer DECLINED (duplicate decline): ném IllegalStateException")
        void declineOffer_alreadyDeclined_throwsIllegalState() {
            // Arrange
            Auction auction = finishedAuction();
            SecondChanceOffer declinedOffer = TestFixture.declinedOffer(
                    runnerUp, auction.getId(), STARTING_PRICE * 2, DEPOSIT);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> paymentService.declineSecondChanceOffer(declinedOffer, auction));
        }
    }

    // =========================================================================
    // Payment lifecycle consistency
    // =========================================================================

    @Nested
    @DisplayName("Payment lifecycle consistency")
    class PaymentLifecycle {

        @Test
        @DisplayName("PENDING → FUNDS_HELD: completePayment làm đúng transition")
        void pendingToFundsHeld_validTransition() {
            // Arrange
            Auction auction = finishedAuctionWithPendingWinner();
            AuctionWinner aw = auction.getWinner();
            assertEquals(PaymentStatus.PENDING, aw.getPaymentStatus());

            // Act
            paymentService.completePayment(auction);

            // Assert
            assertEquals(PaymentStatus.FUNDS_HELD, aw.getPaymentStatus());
        }

        @Test
        @DisplayName("PENDING → EXPIRED: expirePayment làm đúng transition")
        void pendingToExpired_validTransition() {
            // Arrange
            Auction auction = finishedAuction();
            AuctionWinner aw = TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT);
            auction.setWinner(aw);
            when(bidTransactionDAO.findHighestValidBidExcept(any(), any()))
                    .thenReturn(null);

            // Act
            paymentService.expirePayment(auction);

            // Assert
            assertEquals(PaymentStatus.EXPIRED, aw.getPaymentStatus());
        }

        @Test
        @DisplayName("completePayment chỉ gọi walletService đúng 1 lần (anti-duplicate)")
        void completePayment_walletCalledExactlyOnce() {
            // Arrange
            Auction auction = finishedAuctionWithPendingWinner();

            // Act
            paymentService.completePayment(auction);

            // Assert — không có double-transfer
            verify(walletService, times(1))
                    .executePaymentToBank(any(), anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("expirePayment chỉ forfeit đúng 1 lần (anti-duplicate)")
        void expirePayment_forfeitCalledExactlyOnce() {
            // Arrange
            Auction auction = finishedAuction();
            auction.setWinner(TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT));
            when(bidTransactionDAO.findHighestValidBidExcept(any(), any()))
                    .thenReturn(null);

            // Act
            paymentService.expirePayment(auction);

            // Assert
            verify(walletService, times(1))
                    .forfeitDeposit(any(), anyLong(), any());
        }

        @Test
        @DisplayName("completePayment không gọi forfeitDeposit")
        void completePayment_neverForfeits() {
            // Arrange
            Auction auction = finishedAuctionWithPendingWinner();

            // Act
            paymentService.completePayment(auction);

            // Assert
            verify(walletService, never()).forfeitDeposit(any(), anyLong(), any());
        }

        @Test
        @DisplayName("expirePayment không gọi executePaymentToBank")
        void expirePayment_neverExecutesPayment() {
            // Arrange
            Auction auction = finishedAuction();
            auction.setWinner(TestFixture.expiredPendingWinner(
                    winner, auction.getId(), FINAL_PRICE, DEPOSIT));
            when(bidTransactionDAO.findHighestValidBidExcept(any(), any()))
                    .thenReturn(null);

            // Act
            paymentService.expirePayment(auction);

            // Assert
            verify(walletService, never())
                    .executePaymentToBank(any(), anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("completePayment không gọi cancelAuction")
        void completePayment_neverCancelsAuction() {
            // Arrange
            Auction auction = finishedAuctionWithPendingWinner();

            // Act
            paymentService.completePayment(auction);

            // Assert
            verify(auctionService, never()).cancelAuction(any(), any(Admin.CancelReason.class));
        }

        @Test
        @DisplayName("ratingService.rewardBidder chỉ được gọi khi thanh toán thành công")
        void rewardBidder_onlyOnSuccess() {
            // Arrange — kiểm tra reward KHÔNG gọi khi expired
            Auction expiredAuction = finishedAuction();
            expiredAuction.setWinner(TestFixture.expiredPendingWinner(
                    winner, expiredAuction.getId(), FINAL_PRICE, DEPOSIT));

            // Act
            assertThrows(PaymentException.class,
                    () -> paymentService.completePayment(expiredAuction));

            // Assert — không reward khi payment thất bại
            verify(ratingService, never()).rewardBidder(any());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Tạo finished auction (không có winner). */
    private Auction finishedAuction() {
        return TestFixture.finishedAuction(seller, winner, STARTING_PRICE, FINAL_PRICE);
    }

    /** Tạo finished auction với pending winner hợp lệ đã được set. */
    private Auction finishedAuctionWithPendingWinner() {
        Auction auction = TestFixture.finishedAuction(
                seller, winner, STARTING_PRICE, FINAL_PRICE);
        AuctionWinner aw = TestFixture.pendingWinner(
                winner, auction.getId(), FINAL_PRICE, DEPOSIT);
        auction.setWinner(aw);
        return auction;
    }
}