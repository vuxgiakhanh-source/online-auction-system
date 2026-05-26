package com.group13.auction.unit.service;

import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.bid.BidTransaction.BidResult;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionEvent.AuctionEventType;
import com.group13.auction.service.PaymentService;
import com.group13.auction.service.WalletService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock IAuctionService auctionService;
    @Mock IRatingService ratingService;
    @Mock WalletService walletService;
    @Mock AuctionWinnerDAO auctionWinnerDAO;
    @Mock SecondChanceOfferDAO secondChanceOfferDAO;
    @Mock BidTransactionDAO bidTransactionDAO;
    @Mock UserDAO userDAO;

    PaymentService paymentService;
    NormalUser seller;
    NormalUser winner;
    NormalUser runnerUp;

    static final long STARTING_PRICE = 1_000_000L;
    static final long FINAL_PRICE = 3_000_000L;
    static final long DEPOSIT = 300_000L;

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();
        paymentService = new PaymentService(
                auctionService, ratingService, walletService,
                auctionWinnerDAO, secondChanceOfferDAO, bidTransactionDAO, userDAO);
        seller = TestFixture.normalSeller("sellerXX1");
        winner = TestFixture.bidderWithBalance("winnerYY2", 5_000_000L);
        runnerUp = TestFixture.bidderWithBalance("runnerZZ3", 4_000_000L);
        lenient().when(secondChanceOfferDAO.saveOffer(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        TestFixture.resetSystemAdmin();
    }

    @Test
    void completePayment_happyPath() {
        Auction auction = finishedAuctionWithPendingWinner();
        paymentService.completePayment(auction);
        InOrder order = inOrder(walletService, auctionService, ratingService);
        order.verify(walletService).executePaymentToBank(
                winner, FINAL_PRICE, DEPOSIT, auction.getId());
        order.verify(auctionService).markAsPaid(auction);
        order.verify(ratingService).rewardBidder(winner);
    }

    @Test
    void completePayment_expiredWinner_throws() {
        Auction auction = TestFixture.finishedAuction(seller, winner, STARTING_PRICE, FINAL_PRICE);
        auction.setWinner(TestFixture.expiredPendingWinner(
                winner, auction.getId(), FINAL_PRICE, DEPOSIT));
        assertThrows(PaymentException.class, () -> paymentService.completePayment(auction));
        verify(ratingService, never()).rewardBidder(any());
    }

    @Test
    void expirePayment_forfeitsAndOffersSecondChance() {
        Auction auction = finishedAuctionWithPendingWinner();
        auction.setWinner(TestFixture.expiredPendingWinner(
                winner, auction.getId(), FINAL_PRICE, DEPOSIT));
        when(secondChanceOfferDAO.findPendingOfferByAuctionId(auction.getId())).thenReturn(null);
        when(bidTransactionDAO.findHighestValidBidExcept(auction.getId(), winner.getId()))
                .thenReturn(BidTransaction.create(
                        runnerUp, auction.getId(), 2_500_000L, BidResult.ACCEPTED));
        paymentService.expirePayment(auction);
        verify(walletService).forfeitDeposit(winner, DEPOSIT, auction.getId());
        verify(ratingService).penalizeLatePayment(winner);
        verify(auctionService).notify(eq(auction), eq(AuctionEventType.SECOND_CHANCE_OFFERED),
                eq(runnerUp), eq(2_500_000L), anyString());
    }

    @Test
    void acceptSecondChanceOffer_marksAccepted() {
        Auction auction = finishedAuctionWithPendingWinner();
        SecondChanceOffer offer = SecondChanceOffer.create(
                runnerUp, auction.getId(), 2_500_000L, DEPOSIT);
        when(auctionWinnerDAO.saveWinner(any())).thenReturn(true);
        paymentService.acceptSecondChanceOffer(offer, auction);
        assertEquals(SecondChanceOffer.OfferStatus.ACCEPTED, offer.getStatus());
        verify(secondChanceOfferDAO).updateOfferStatus(offer.getId(), "ACCEPTED");
    }

    @Test
    void declineSecondChanceOffer_marksDeclined() {
        Auction auction = finishedAuctionWithPendingWinner();
        SecondChanceOffer offer = SecondChanceOffer.create(
                runnerUp, auction.getId(), 2_500_000L, DEPOSIT);
        paymentService.declineSecondChanceOffer(offer, auction);
        assertEquals(SecondChanceOffer.OfferStatus.DECLINED, offer.getStatus());
        verify(secondChanceOfferDAO).updateOfferStatus(offer.getId(), "DECLINED");
        verify(auctionService).cancelAuction(eq(auction), any());
    }

    private Auction finishedAuctionWithPendingWinner() {
        Auction auction = TestFixture.finishedAuction(
                seller, winner, STARTING_PRICE, FINAL_PRICE);
        AuctionWinner aw = TestFixture.pendingWinner(
                winner, auction.getId(), FINAL_PRICE, DEPOSIT);
        auction.setWinner(aw);
        return auction;
    }
}
