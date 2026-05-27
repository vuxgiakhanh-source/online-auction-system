package com.group13.auction.unit.auction;

import static org.junit.jupiter.api.Assertions.*;

import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests cho {@link AuctionWinner} — thanh toán và deadline. */
@DisplayName("AuctionWinner")
class AuctionWinnerTest {

  private NormalUser winner;
  private String auctionId;

  @BeforeEach
  void setUp() {
    winner = TestFixture.normalBidder("winBidder1");
    auctionId = UUID.randomUUID().toString();
  }

  @Test
  void create_pendingPaymentWithFutureDeadline() {
    AuctionWinner w = AuctionWinner.create(winner, auctionId, 2_000_000L, 300_000L, false);
    assertEquals(AuctionWinner.PaymentStatus.PENDING, w.getPaymentStatus());
    assertTrue(w.getPaymentDeadline().isAfter(LocalDateTime.now()));
    assertEquals(1_700_000L, w.getRemainingAmount());
  }

  @Test
  void isExpired_pastDeadline() {
    AuctionWinner w = TestFixture.expiredPendingWinner(winner, auctionId, 1_000_000L, 200_000L);
    assertTrue(w.isExpired());
  }

  @Test
  void markFundsHeld_setsDeadline() {
    AuctionWinner w = TestFixture.pendingWinner(winner, auctionId, 1_000_000L, 200_000L);
    w.markFundsHeld();
    assertEquals(AuctionWinner.PaymentStatus.FUNDS_HELD, w.getPaymentStatus());
    assertNotNull(w.getConfirmReceiptDeadline());
  }

  @Test
  void confirmReceipt_setsReportDeadline() {
    AuctionWinner w = TestFixture.fundsHeldWinner(winner, auctionId, 1_000_000L, 200_000L);
    w.confirmReceipt();
    assertEquals(AuctionWinner.PaymentStatus.ITEM_RECEIVED, w.getPaymentStatus());
    assertNotNull(w.getReportDeadline());
  }
}
