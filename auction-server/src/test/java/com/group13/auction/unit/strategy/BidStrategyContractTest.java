package com.group13.auction.unit.strategy;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.strategy.AutoBidStrategy;
import com.group13.auction.strategy.BidStrategy;
import com.group13.auction.strategy.StandardBidStrategy;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BidStrategy — contract & polymorphism")
class BidStrategyContractTest {

    static Stream<BidStrategy> allImplementations() {
        return Stream.of(new StandardBidStrategy(), new AutoBidStrategy(10_000_000L));
    }

    static Stream<Arguments> validMinimumBids() {
        return Stream.of(
                Arguments.of(500_000L, 550_000L),
                Arguments.of(2_000_000L, 2_200_000L),
                Arguments.of(15_000_000L, 15_500_000L)
        );
    }

    @ParameterizedTest
    @MethodSource("allImplementations")
    void describe_nonBlankAndIdempotent(BidStrategy strategy) {
        String first = strategy.describe();
        assertNotNull(first);
        assertFalse(first.isBlank());
        assertEquals(first, strategy.describe());
    }

    @ParameterizedTest
    @MethodSource("validMinimumBids")
    void standard_validMinimumBid(long currentPrice, long bid) {
        Auction auction = auctionWithPrice(currentPrice);
        assertTrue(new StandardBidStrategy().isValidBid(auction, bid));
    }

    @ParameterizedTest
    @MethodSource("validMinimumBids")
    void auto_validMinimumBid_withinMax(long currentPrice, long bid) {
        Auction auction = auctionWithPrice(currentPrice);
        assertTrue(new AutoBidStrategy(bid + 1).isValidBid(auction, bid));
    }

    @Test
    void standard_rejectsBelowMinimum() {
        Auction auction = auctionWithPrice(500_000L);
        assertFalse(new StandardBidStrategy().isValidBid(auction, 549_999L));
        assertFalse(new StandardBidStrategy().isValidBid(auction, 0L));
    }

    @Test
    void auto_rejectsAboveMaxBid() {
        Auction auction = auctionWithPrice(500_000L);
        assertFalse(new AutoBidStrategy(600_000L).isValidBid(auction, 700_000L));
    }

    @Test
    void polymorphism_sameAuctionDifferentStrategies() {
        Auction auction = auctionWithPrice(500_000L);
        BidStrategy standard = new StandardBidStrategy();
        BidStrategy auto = new AutoBidStrategy(600_000L);
        assertTrue(standard.isValidBid(auction, 550_000L));
        assertFalse(auto.isValidBid(auction, 700_000L));
    }

    private static Auction auctionWithPrice(long currentPrice) {
        NormalUser seller = TestFixture.normalSeller("sellerFixt1");
        long startingPrice = Math.max(1L, currentPrice / 2);
        return TestFixture.auctionWithStatus(
                seller, startingPrice, currentPrice, Auction.AuctionStatus.RUNNING);
    }
}
