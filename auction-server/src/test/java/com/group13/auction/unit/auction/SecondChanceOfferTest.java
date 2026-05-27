package com.group13.auction.unit.auction;

import static org.junit.jupiter.api.Assertions.*;

import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("SecondChanceOffer")
class SecondChanceOfferTest {

  private NormalUser runnerUp;
  private String auctionId;

  @BeforeEach
  void setUp() {
    runnerUp = TestFixture.normalBidder("runnerUp1");
    auctionId = UUID.randomUUID().toString();
  }

  @Test
  void create_startsPendingWithFutureDeadline() {
    SecondChanceOffer offer = SecondChanceOffer.create(runnerUp, auctionId, 2_000_000L, 300_000L);
    assertEquals(SecondChanceOffer.OfferStatus.PENDING, offer.getStatus());
    assertTrue(offer.getDeadline().isAfter(LocalDateTime.now()));
  }

  @ParameterizedTest
  @CsvSource({"2000000, 300000, 1700000", "500000, 500000, 0", "100000, 200000, 0"})
  void getRemainingAmount(long offerPrice, long depositPaid, long expected) {
    SecondChanceOffer offer =
        SecondChanceOffer.reconstitute(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            runnerUp,
            auctionId,
            offerPrice,
            depositPaid,
            LocalDateTime.now().plusHours(1),
            SecondChanceOffer.OfferStatus.PENDING);
    assertEquals(expected, offer.getRemainingAmount());
  }

  @Test
  void isExpired_whenPastDeadlineAndPending() {
    SecondChanceOffer offer =
        SecondChanceOffer.reconstitute(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            runnerUp,
            auctionId,
            2_000_000L,
            300_000L,
            LocalDateTime.now().minusHours(1),
            SecondChanceOffer.OfferStatus.PENDING);
    assertTrue(offer.isExpired());
  }

  @Test
  void isExpired_falseWhenDeadlineInFuture() {
    SecondChanceOffer offer =
        SecondChanceOffer.reconstitute(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            runnerUp,
            auctionId,
            2_000_000L,
            300_000L,
            LocalDateTime.now().plusHours(1),
            SecondChanceOffer.OfferStatus.PENDING);
    assertFalse(offer.isExpired());
  }

  @Test
  void setStatus_updatesStatus() {
    SecondChanceOffer offer = SecondChanceOffer.create(runnerUp, auctionId, 2_000_000L, 300_000L);
    offer.setStatus(SecondChanceOffer.OfferStatus.ACCEPTED);
    assertEquals(SecondChanceOffer.OfferStatus.ACCEPTED, offer.getStatus());
  }
}
