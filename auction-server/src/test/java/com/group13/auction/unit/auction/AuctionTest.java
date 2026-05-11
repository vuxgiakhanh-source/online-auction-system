package com.group13.auction.unit.auction;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.user.NormalUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link Auction}.
 *
 * <p>Tập trung vào 4 nhóm business rule chính:
 * <ul>
 *   <li>{@code updateBid} — cập nhật giá và leader.</li>
 *   <li>{@code extendEndTime} — anti-sniping gia hạn thời gian.</li>
 *   <li>{@code isReserveMet} — kiểm tra giá sàn bí mật.</li>
 *   <li>{@code incrementViewerCount} — tăng viewer counter.</li>
 * </ul>
 *
 * <p>Không trigger service, không DB, không network.
 * Dùng object thật từ {@link TestFixture}.
 */
@DisplayName("Auction")
class AuctionTest {

    private NormalUser seller;
    private NormalUser bidder;
    private NormalUser anotherBidder;

    @BeforeEach
    void setUp() {
        seller       = TestFixture.normalSeller("sellerAA1");
        bidder       = TestFixture.bidderWithBalance("bidderBB1", 10_000_000L);
        anotherBidder = TestFixture.bidderWithBalance("bidderCC2", 10_000_000L);
    }

    // =========================================================================
    // create() — trạng thái khởi tạo
    // =========================================================================

    @Nested
    @DisplayName("create() — trạng thái ban đầu")
    class CreateTest {

        @Test
        @DisplayName("create() → status = OPEN")
        void create_initialStatus_isOpen() {
            // Arrange & Act
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);

            // Assert
            assertEquals(Auction.AuctionStatus.OPEN, auction.getStatus());
        }

        @Test
        @DisplayName("create() → currentPrice = item.startingPrice")
        void create_initialCurrentPrice_equalsItemStartingPrice() {
            // Arrange
            long startingPrice = 1_000_000L;
            Art item = TestFixture.art("Tranh Test", startingPrice, seller);

            // Act
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    startingPrice * 2);

            // Assert
            assertEquals(startingPrice, auction.getCurrentPrice());
        }

        @Test
        @DisplayName("create() → currentLeader = null")
        void create_initialCurrentLeader_isNull() {
            // Arrange & Act
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);

            // Assert
            assertNull(auction.getCurrentLeader());
        }

        @Test
        @DisplayName("create() → winner = null")
        void create_initialWinner_isNull() {
            // Arrange & Act
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);

            // Assert
            assertNull(auction.getWinner());
        }

        @Test
        @DisplayName("create() → viewerCount = 0")
        void create_initialViewerCount_isZero() {
            // Arrange & Act
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);

            // Assert
            assertEquals(0, auction.getViewerCount());
        }

        @Test
        @DisplayName("create() → isAcceptingBids = false (chưa RUNNING)")
        void create_initialIsAcceptingBids_isFalse() {
            // Arrange & Act
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);

            // Assert
            assertFalse(auction.isAcceptingBids());
        }

        @Test
        @DisplayName("create() → endTime = originalEndTime ban đầu")
        void create_endTime_equalsOriginalEndTime() {
            // Arrange & Act
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);

            // Assert
            assertEquals(auction.getOriginalEndTime(), auction.getEndTime());
        }

        @Test
        @DisplayName("create() → bidTransactionIds rỗng")
        void create_initialBidTransactionIds_isEmpty() {
            // Arrange & Act
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);

            // Assert
            assertTrue(auction.getBidTransactionIds().isEmpty());
        }
    }

    // =========================================================================
    // updateBid
    // =========================================================================

    @Nested
    @DisplayName("updateBid — cập nhật giá và leader")
    class UpdateBidTest {

        // -- Happy path -------------------------------------------------------

        @Test
        @DisplayName("updateBid() cập nhật currentPrice đúng giá mới")
        void updateBid_setsNewCurrentPrice() {
            // Arrange
            Auction auction  = TestFixture.runningAuction(seller, 1_000_000L);
            long    newPrice = 1_500_000L;

            // Act
            auction.updateBid(newPrice, bidder);

            // Assert
            assertEquals(newPrice, auction.getCurrentPrice());
        }

        @Test
        @DisplayName("updateBid() cập nhật currentLeader đúng bidder mới")
        void updateBid_setsNewCurrentLeader() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act
            auction.updateBid(1_500_000L, bidder);

            // Assert
            assertSame(bidder, auction.getCurrentLeader());
        }

        @Test
        @DisplayName("updateBid() cập nhật cả price và leader trong một lần gọi (atomic)")
        void updateBid_updatesPriceAndLeaderAtomically() {
            // Arrange
            Auction auction  = TestFixture.runningAuction(seller, 1_000_000L);
            long    newPrice = 2_000_000L;

            // Act
            auction.updateBid(newPrice, bidder);

            // Assert — cả hai field phải khớp cùng một lần gọi
            assertEquals(newPrice, auction.getCurrentPrice());
            assertSame(bidder, auction.getCurrentLeader());
        }

        @Test
        @DisplayName("updateBid() lần hai thay thế cả price và leader cũ")
        void updateBid_calledTwice_replacesOldLeaderAndPrice() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            auction.updateBid(1_500_000L, bidder);

            // Act — anotherBidder vượt giá
            auction.updateBid(2_000_000L, anotherBidder);

            // Assert
            assertEquals(2_000_000L, auction.getCurrentPrice());
            assertSame(anotherBidder, auction.getCurrentLeader());
        }

        @Test
        @DisplayName("updateBid() không thay đổi status, item, reservePrice, viewerCount")
        void updateBid_doesNotAlterOtherFields() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            Auction.AuctionStatus statusBefore = auction.getStatus();
            long reserveBefore = auction.getReservePrice();
            int  viewersBefore = auction.getViewerCount();

            // Act
            auction.updateBid(1_500_000L, bidder);

            // Assert
            assertEquals(statusBefore,  auction.getStatus());
            assertEquals(reserveBefore, auction.getReservePrice());
            assertEquals(viewersBefore, auction.getViewerCount());
            assertSame(seller, auction.getItem().getSeller());
        }

        // -- Bid bằng giá hiện tại ------------------------------------------

        @Test
        @DisplayName("updateBid() với giá bằng currentPrice vẫn cập nhật (model không validate)")
        void updateBid_withSamePrice_updatesLeader() {
            // Arrange — model không có guard; validation nằm ở BidService/Strategy
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            auction.updateBid(1_000_000L, bidder);

            // Act — anotherBidder đặt cùng giá
            auction.updateBid(1_000_000L, anotherBidder);

            // Assert — model chấp nhận, leader thay đổi
            assertEquals(1_000_000L, auction.getCurrentPrice());
            assertSame(anotherBidder, auction.getCurrentLeader());
        }

        // -- Bid với giá thấp hơn -------------------------------------------

        @Test
        @DisplayName("updateBid() với giá thấp hơn currentPrice vẫn set (model không validate)")
        void updateBid_withLowerPrice_stillSets() {
            // Arrange — guard là BidService, model là setter thuần
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            auction.updateBid(2_000_000L, bidder);

            // Act
            auction.updateBid(500_000L, anotherBidder);

            // Assert — model không chặn, setter thuần
            assertEquals(500_000L, auction.getCurrentPrice());
            assertSame(anotherBidder, auction.getCurrentLeader());
        }

        // -- Edge cases về giá trị ------------------------------------------

        @Test
        @DisplayName("updateBid() với giá = 0 vẫn set (model không validate giá trị)")
        void updateBid_withZeroPrice_stillSets() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act
            auction.updateBid(0L, bidder);

            // Assert
            assertEquals(0L, auction.getCurrentPrice());
        }

        @Test
        @DisplayName("updateBid() với giá âm vẫn set (model không validate giá trị)")
        void updateBid_withNegativePrice_stillSets() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act
            auction.updateBid(-1L, bidder);

            // Assert — guard là Strategy, không phải model
            assertEquals(-1L, auction.getCurrentPrice());
        }

        @Test
        @DisplayName("updateBid() với giá rất lớn (Long.MAX_VALUE) vẫn set")
        void updateBid_withMaxLongPrice_stillSets() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act
            auction.updateBid(Long.MAX_VALUE, bidder);

            // Assert
            assertEquals(Long.MAX_VALUE, auction.getCurrentPrice());
        }

        // -- Không ảnh hưởng đến bidTransactionIds --------------------------

        @Test
        @DisplayName("updateBid() không tự động thêm vào bidTransactionIds")
        void updateBid_doesNotAddToBidTransactionIds() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            int sizeBefore = auction.getBidTransactionIds().size();

            // Act
            auction.updateBid(1_500_000L, bidder);

            // Assert — addBidTransactionId() là method riêng
            assertEquals(sizeBefore, auction.getBidTransactionIds().size());
        }

        // -- isReserveMet sau updateBid -------------------------------------

        @Test
        @DisplayName("updateBid() đưa giá lên đúng reservePrice → isReserveMet = true")
        void updateBid_toExactReservePrice_reserveMetBecomesTrue() {
            // Arrange — reservePrice = 2_000_000, startingPrice = 1_000_000
            long startingPrice = 1_000_000L;
            long reservePrice  = 2_000_000L;
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);
            auction.transitionToRunning();
            assertFalse(auction.isReserveMet()); // precondition

            // Act
            auction.updateBid(reservePrice, bidder);

            // Assert
            assertTrue(auction.isReserveMet());
        }

        @Test
        @DisplayName("updateBid() vượt reservePrice → isReserveMet = true")
        void updateBid_aboveReservePrice_reserveMetIsTrue() {
            // Arrange
            long startingPrice = 1_000_000L;
            long reservePrice  = 2_000_000L;
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);
            auction.transitionToRunning();

            // Act
            auction.updateBid(2_500_000L, bidder);

            // Assert
            assertTrue(auction.isReserveMet());
        }

        @Test
        @DisplayName("updateBid() dưới reservePrice → isReserveMet = false")
        void updateBid_belowReservePrice_reserveMetIsFalse() {
            // Arrange
            long startingPrice = 1_000_000L;
            long reservePrice  = 5_000_000L;
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);
            auction.transitionToRunning();

            // Act
            auction.updateBid(3_000_000L, bidder);

            // Assert
            assertFalse(auction.isReserveMet());
        }
    }

    // =========================================================================
    // isReserveMet
    // =========================================================================

    @Nested
    @DisplayName("isReserveMet — kiểm tra giá sàn bí mật")
    class IsReserveMetTest {

        @Test
        @DisplayName("currentPrice = reservePrice → isReserveMet = true (đúng ranh giới)")
        void isReserveMet_whenCurrentPriceEqualsReserve_returnsTrue() {
            // Arrange — startingPrice = reservePrice để currentPrice = reservePrice ngay từ đầu
            long price = 1_000_000L;
            Art item = TestFixture.art("Tranh", price, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    price); // reservePrice = startingPrice

            // Assert — currentPrice = startingPrice = reservePrice
            assertTrue(auction.isReserveMet());
        }

        @Test
        @DisplayName("currentPrice > reservePrice → isReserveMet = true")
        void isReserveMet_whenCurrentPriceAboveReserve_returnsTrue() {
            // Arrange
            long startingPrice = 1_000_000L;
            long reservePrice  = 500_000L; // reserve thấp hơn starting
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);

            // Assert — currentPrice = 1_000_000 > reserve = 500_000
            assertTrue(auction.isReserveMet());
        }

        @Test
        @DisplayName("currentPrice < reservePrice → isReserveMet = false")
        void isReserveMet_whenCurrentPriceBelowReserve_returnsFalse() {
            // Arrange
            long startingPrice = 1_000_000L;
            long reservePrice  = 5_000_000L; // reserve cao hơn starting
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);

            // Assert — currentPrice = 1_000_000 < reserve = 5_000_000
            assertFalse(auction.isReserveMet());
        }

        @Test
        @DisplayName("currentPrice = reservePrice - 1 → isReserveMet = false (off-by-one)")
        void isReserveMet_whenCurrentPriceOneLessThanReserve_returnsFalse() {
            // Arrange
            long reservePrice  = 2_000_000L;
            long startingPrice = reservePrice - 1; // currentPrice = reserve - 1
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);

            // Assert
            assertFalse(auction.isReserveMet());
        }

        @Test
        @DisplayName("currentPrice = reservePrice + 1 → isReserveMet = true (off-by-one)")
        void isReserveMet_whenCurrentPriceOneAboveReserve_returnsTrue() {
            // Arrange
            long reservePrice  = 2_000_000L;
            long startingPrice = reservePrice + 1; // currentPrice = reserve + 1
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);

            // Assert
            assertTrue(auction.isReserveMet());
        }

        @Test
        @DisplayName("isReserveMet là pure function — nhiều lần gọi cho cùng kết quả")
        void isReserveMet_calledMultipleTimes_returnsSameValue() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act
            boolean first  = auction.isReserveMet();
            boolean second = auction.isReserveMet();
            boolean third  = auction.isReserveMet();

            // Assert
            assertEquals(first, second);
            assertEquals(second, third);
        }

        @Test
        @DisplayName("isReserveMet thay đổi đúng sau updateBid vượt ngưỡng")
        void isReserveMet_changesCorrectly_afterUpdateBidCrossesThreshold() {
            // Arrange
            long startingPrice = 1_000_000L;
            long reservePrice  = 3_000_000L;
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);
            auction.transitionToRunning();
            assertFalse(auction.isReserveMet()); // precondition: 1M < 3M

            // Act 1: bid dưới reserve
            auction.updateBid(2_000_000L, bidder);
            assertFalse(auction.isReserveMet()); // vẫn chưa đạt

            // Act 2: bid đúng reserve
            auction.updateBid(3_000_000L, bidder);
            assertTrue(auction.isReserveMet()); // đúng ngưỡng

            // Act 3: bid vượt reserve
            auction.updateBid(4_000_000L, anotherBidder);
            assertTrue(auction.isReserveMet()); // vẫn đúng
        }

        @Test
        @DisplayName("isReserveMet không bị ảnh hưởng bởi viewerCount hay endTime")
        void isReserveMet_independentOfViewerCountAndEndTime() {
            // Arrange
            long startingPrice = 1_000_000L;
            long reservePrice  = 500_000L; // met ngay từ đầu
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);

            // Act — thêm viewer và extend time
            auction.incrementViewerCount();
            auction.incrementViewerCount();
            auction.transitionToRunning();
            auction.extendEndTime(Duration.ofMinutes(5));

            // Assert — isReserveMet không thay đổi
            assertTrue(auction.isReserveMet());
        }

        @Test
        @DisplayName("reconstitute với currentPrice < reservePrice → isReserveMet = false")
        void isReserveMet_afterReconstitute_withPriceBelowReserve_returnsFalse() {
            // Arrange
            Art item = TestFixture.art("Tranh", 1_000_000L, seller);
            Auction auction = Auction.reconstitute(
                    "auction-id-001",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    item,
                    LocalDateTime.now().minusHours(1),
                    LocalDateTime.now().plusHours(1),
                    1_500_000L,              // currentPrice
                    Auction.AuctionStatus.RUNNING,
                    5_000_000L);             // reservePrice

            // Assert
            assertFalse(auction.isReserveMet());
        }

        @Test
        @DisplayName("reconstitute với currentPrice >= reservePrice → isReserveMet = true")
        void isReserveMet_afterReconstitute_withPriceAtReserve_returnsTrue() {
            // Arrange
            Art item = TestFixture.art("Tranh", 1_000_000L, seller);
            Auction auction = Auction.reconstitute(
                    "auction-id-002",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    item,
                    LocalDateTime.now().minusHours(1),
                    LocalDateTime.now().plusHours(1),
                    5_000_000L,              // currentPrice = reservePrice
                    Auction.AuctionStatus.RUNNING,
                    5_000_000L);             // reservePrice

            // Assert
            assertTrue(auction.isReserveMet());
        }
    }

    // =========================================================================
    // extendEndTime
    // =========================================================================

    @Nested
    @DisplayName("extendEndTime — anti-sniping gia hạn thời gian")
    class ExtendEndTimeTest {

        // -- Happy path -------------------------------------------------------

        @Test
        @DisplayName("extendEndTime(5 phút) → endTime tăng thêm đúng 5 phút")
        void extendEndTime_fiveMinutes_addsExactDuration() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            LocalDateTime endTimeBefore = auction.getEndTime();
            Duration extension = Duration.ofMinutes(5);

            // Act
            auction.extendEndTime(extension);

            // Assert
            assertEquals(endTimeBefore.plusMinutes(5), auction.getEndTime());
        }

        @Test
        @DisplayName("extendEndTime(1 giây) → endTime tăng đúng 1 giây (min valid)")
        void extendEndTime_oneSecond_addsOneSecond() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            LocalDateTime endTimeBefore = auction.getEndTime();

            // Act
            auction.extendEndTime(Duration.ofSeconds(1));

            // Assert
            assertEquals(endTimeBefore.plusSeconds(1), auction.getEndTime());
        }

        @Test
        @DisplayName("extendEndTime(1 nano) → endTime tăng đúng 1 nano (absolute minimum)")
        void extendEndTime_oneNanosecond_addsOneNano() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            LocalDateTime endTimeBefore = auction.getEndTime();

            // Act
            auction.extendEndTime(Duration.ofNanos(1));

            // Assert
            assertEquals(endTimeBefore.plusNanos(1), auction.getEndTime());
        }

        @Test
        @DisplayName("extendEndTime() gọi nhiều lần → endTime tích lũy đúng")
        void extendEndTime_calledMultipleTimes_accumulatesCorrectly() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            LocalDateTime originalEndTime = auction.getEndTime();

            // Act — tổng 3 lần = 15 phút
            auction.extendEndTime(Duration.ofMinutes(5));
            auction.extendEndTime(Duration.ofMinutes(5));
            auction.extendEndTime(Duration.ofMinutes(5));

            // Assert
            assertEquals(originalEndTime.plusMinutes(15), auction.getEndTime());
        }

        @Test
        @DisplayName("extendEndTime() không thay đổi originalEndTime")
        void extendEndTime_doesNotChangeOriginalEndTime() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            LocalDateTime originalEndTime = auction.getOriginalEndTime();

            // Act
            auction.extendEndTime(Duration.ofMinutes(10));

            // Assert — originalEndTime là final field
            assertEquals(originalEndTime, auction.getOriginalEndTime());
        }

        @Test
        @DisplayName("extendEndTime() không thay đổi status, currentPrice, currentLeader")
        void extendEndTime_doesNotAlterOtherFields() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            auction.updateBid(1_500_000L, bidder);
            Auction.AuctionStatus statusBefore = auction.getStatus();
            long priceBefore = auction.getCurrentPrice();

            // Act
            auction.extendEndTime(Duration.ofMinutes(5));

            // Assert
            assertEquals(statusBefore,  auction.getStatus());
            assertEquals(priceBefore,   auction.getCurrentPrice());
            assertSame(bidder, auction.getCurrentLeader());
        }

        @Test
        @DisplayName("extendEndTime(24h) → endTime tăng đúng 24 giờ (large extension)")
        void extendEndTime_twentyFourHours_addsCorrectly() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            LocalDateTime endTimeBefore = auction.getEndTime();

            // Act
            auction.extendEndTime(Duration.ofHours(24));

            // Assert
            assertEquals(endTimeBefore.plusHours(24), auction.getEndTime());
        }

        // -- Invalid: null extension -----------------------------------------

        @Test
        @DisplayName("extendEndTime(null) → ném IllegalArgumentException")
        void extendEndTime_nullExtension_throwsIllegalArgument() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> auction.extendEndTime(null));
        }

        @Test
        @DisplayName("extendEndTime(null) → endTime không thay đổi sau exception")
        void extendEndTime_nullExtension_endTimeUnchanged() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            LocalDateTime endTimeBefore = auction.getEndTime();

            // Act
            try { auction.extendEndTime(null); } catch (IllegalArgumentException ignored) {}

            // Assert
            assertEquals(endTimeBefore, auction.getEndTime());
        }

        // -- Invalid: zero extension -----------------------------------------

        @Test
        @DisplayName("extendEndTime(Duration.ZERO) → ném IllegalArgumentException")
        void extendEndTime_zeroDuration_throwsIllegalArgument() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> auction.extendEndTime(Duration.ZERO));
        }

        @Test
        @DisplayName("extendEndTime(Duration.ZERO) → exception message mô tả constraint")
        void extendEndTime_zeroDuration_exceptionMessageDescribesConstraint() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> auction.extendEndTime(Duration.ZERO));

            // Assert
            assertNotNull(ex.getMessage());
            assertFalse(ex.getMessage().isBlank());
        }

        // -- Invalid: negative extension -------------------------------------

        @Test
        @DisplayName("extendEndTime(−1 phút) → ném IllegalArgumentException")
        void extendEndTime_negativeOneMinute_throwsIllegalArgument() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> auction.extendEndTime(Duration.ofMinutes(-1)));
        }

        @Test
        @DisplayName("extendEndTime(−1 nano) → ném IllegalArgumentException (minimum negative)")
        void extendEndTime_negativeOneNano_throwsIllegalArgument() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> auction.extendEndTime(Duration.ofNanos(-1)));
        }

        @Test
        @DisplayName("extendEndTime(negative) → endTime không thay đổi sau exception")
        void extendEndTime_negativeExtension_endTimeUnchanged() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            LocalDateTime endTimeBefore = auction.getEndTime();

            // Act
            try { auction.extendEndTime(Duration.ofMinutes(-5)); } catch (IllegalArgumentException ignored) {}

            // Assert
            assertEquals(endTimeBefore, auction.getEndTime());
        }

        // -- Boundary: endTime vs originalEndTime sau extend ----------------

        @Test
        @DisplayName("sau extendEndTime() → endTime > originalEndTime")
        void extendEndTime_validExtension_endTimeAfterOriginal() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            LocalDateTime originalEndTime = auction.getOriginalEndTime();

            // Act
            auction.extendEndTime(Duration.ofMinutes(5));

            // Assert
            assertTrue(auction.getEndTime().isAfter(originalEndTime));
        }

        @Test
        @DisplayName("extendEndTime() exception message chứa thông tin '>0'")
        void extendEndTime_invalidExtension_exceptionMessageContainsConstraint() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> auction.extendEndTime(Duration.ZERO));

            // Assert
            assertTrue(ex.getMessage().contains("> 0") || ex.getMessage().contains(">0"),
                    "Exception message phải đề cập đến constraint > 0");
        }
    }

    // =========================================================================
    // incrementViewerCount
    // =========================================================================

    @Nested
    @DisplayName("incrementViewerCount — tăng viewer counter")
    class IncrementViewerCountTest {

        @Test
        @DisplayName("incrementViewerCount() lần đầu → viewerCount = 1")
        void incrementViewerCount_once_returnsOne() {
            // Arrange
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);
            assertEquals(0, auction.getViewerCount()); // precondition

            // Act
            auction.incrementViewerCount();

            // Assert
            assertEquals(1, auction.getViewerCount());
        }

        @Test
        @DisplayName("incrementViewerCount() nhiều lần → tích lũy đúng")
        void incrementViewerCount_multipleTimes_accumulatesCorrectly() {
            // Arrange
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);

            // Act
            auction.incrementViewerCount();
            auction.incrementViewerCount();
            auction.incrementViewerCount();

            // Assert
            assertEquals(3, auction.getViewerCount());
        }

        @Test
        @DisplayName("incrementViewerCount() luôn tăng đúng 1 mỗi lần gọi")
        void incrementViewerCount_eachCall_incrementsByExactlyOne() {
            // Arrange
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);

            // Act & Assert — kiểm tra từng bước
            auction.incrementViewerCount();
            assertEquals(1, auction.getViewerCount());

            auction.incrementViewerCount();
            assertEquals(2, auction.getViewerCount());

            auction.incrementViewerCount();
            assertEquals(3, auction.getViewerCount());
        }

        @Test
        @DisplayName("incrementViewerCount() không thay đổi status, price, leader")
        void incrementViewerCount_doesNotAlterOtherFields() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            auction.updateBid(1_500_000L, bidder);
            Auction.AuctionStatus statusBefore = auction.getStatus();
            long priceBefore = auction.getCurrentPrice();

            // Act
            auction.incrementViewerCount();

            // Assert
            assertEquals(statusBefore, auction.getStatus());
            assertEquals(priceBefore,  auction.getCurrentPrice());
            assertSame(bidder, auction.getCurrentLeader());
        }

        @Test
        @DisplayName("incrementViewerCount() hoạt động ở mọi trạng thái auction")
        void incrementViewerCount_worksInAllStatuses() {
            // Arrange
            Auction openAuction   = TestFixture.openAuction(seller, 1_000_000L);
            Auction runningAuction = TestFixture.runningAuction(seller, 1_000_000L);
            Auction canceledAuction = TestFixture.canceledFromOpenAuction(seller, 1_000_000L);

            // Act & Assert — không ném exception ở bất kỳ trạng thái nào
            assertDoesNotThrow(openAuction::incrementViewerCount);
            assertDoesNotThrow(runningAuction::incrementViewerCount);
            assertDoesNotThrow(canceledAuction::incrementViewerCount);
        }

        @Test
        @DisplayName("incrementViewerCount() 100 lần → viewerCount = 100")
        void incrementViewerCount_hundredTimes_returnsHundred() {
            // Arrange
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);

            // Act
            for (int i = 0; i < 100; i++) {
                auction.incrementViewerCount();
            }

            // Assert
            assertEquals(100, auction.getViewerCount());
        }

        @Test
        @DisplayName("hai Auction riêng biệt có viewerCount độc lập")
        void incrementViewerCount_twoAuctions_haveIndependentCounters() {
            // Arrange
            Auction auction1 = TestFixture.openAuction(seller, 1_000_000L);
            Auction auction2 = TestFixture.openAuction(seller, 1_000_000L);

            // Act
            auction1.incrementViewerCount();
            auction1.incrementViewerCount();
            auction1.incrementViewerCount();
            auction2.incrementViewerCount();

            // Assert
            assertEquals(3, auction1.getViewerCount());
            assertEquals(1, auction2.getViewerCount());
        }
    }

    // =========================================================================
    // State transitions — lifecycle integrity
    // =========================================================================

    @Nested
    @DisplayName("State transitions — lifecycle integrity")
    class StateTransitionTest {

        @Test
        @DisplayName("OPEN → RUNNING: isAcceptingBids = true")
        void transitionToRunning_isAcceptingBidsBecomesTrue() {
            // Arrange
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);
            assertFalse(auction.isAcceptingBids()); // precondition

            // Act
            auction.transitionToRunning();

            // Assert
            assertTrue(auction.isAcceptingBids());
        }

        @Test
        @DisplayName("RUNNING → FINISHED (hasWinner=true): status = FINISHED")
        void transitionToClose_withWinner_statusIsFinished() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            auction.updateBid(2_000_000L, bidder);

            // Act
            auction.transitionToClose(true);

            // Assert
            assertEquals(Auction.AuctionStatus.FINISHED, auction.getStatus());
        }

        @Test
        @DisplayName("RUNNING → CANCELED (hasWinner=false): status = CANCELED")
        void transitionToClose_withoutWinner_statusIsCanceled() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act
            auction.transitionToClose(false);

            // Assert
            assertEquals(Auction.AuctionStatus.CANCELED, auction.getStatus());
        }

        @Test
        @DisplayName("FINISHED → PAID: status = PAID")
        void transitionToPaid_fromFinished_statusIsPaid() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            auction.updateBid(2_000_000L, bidder);
            auction.transitionToClose(true);

            // Act
            auction.transitionToPaid();

            // Assert
            assertEquals(Auction.AuctionStatus.PAID, auction.getStatus());
        }

        @Test
        @DisplayName("OPEN → RUNNING: currentPrice và currentLeader không đổi")
        void transitionToRunning_doesNotAffectPriceOrLeader() {
            // Arrange
            Auction auction = TestFixture.openAuction(seller, 1_000_000L);
            long priceBefore = auction.getCurrentPrice();

            // Act
            auction.transitionToRunning();

            // Assert
            assertEquals(priceBefore, auction.getCurrentPrice());
            assertNull(auction.getCurrentLeader());
        }

        @Test
        @DisplayName("addBidTransactionId() thêm đúng id vào danh sách")
        void addBidTransactionId_addsIdToList() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            String bidId = "bid-tx-001";

            // Act
            auction.addBidTransactionId(bidId);

            // Assert
            assertEquals(1, auction.getBidTransactionIds().size());
            assertTrue(auction.getBidTransactionIds().contains(bidId));
        }

        @Test
        @DisplayName("getBidTransactionIds() trả về unmodifiable list")
        void getBidTransactionIds_returnsUnmodifiableList() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            auction.addBidTransactionId("bid-tx-001");

            // Act & Assert
            assertThrows(UnsupportedOperationException.class,
                    () -> auction.getBidTransactionIds().add("hacked-id"));
        }
    }

    // =========================================================================
    // Cross-concern: updateBid + isReserveMet + extendEndTime + viewerCount
    // =========================================================================

    @Nested
    @DisplayName("Cross-concern — tương tác giữa các operations")
    class CrossConcernTest {

        @Test
        @DisplayName("full bid flow: updateBid → isReserveMet → extendEndTime độc lập nhau")
        void fullBidFlow_allOperationsIndependent() {
            // Arrange
            long startingPrice = 1_000_000L;
            long reservePrice  = 2_000_000L;
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);
            auction.transitionToRunning();

            LocalDateTime endTimeBefore = auction.getEndTime();

            // Act — mô phỏng anti-sniping: bid vượt reserve + extend
            auction.updateBid(2_500_000L, bidder);
            auction.extendEndTime(Duration.ofMinutes(5));
            auction.incrementViewerCount();

            // Assert — mỗi operation ảnh hưởng đúng field của nó
            assertEquals(2_500_000L,         auction.getCurrentPrice());
            assertSame(bidder,               auction.getCurrentLeader());
            assertTrue(auction.isReserveMet());
            assertEquals(endTimeBefore.plusMinutes(5), auction.getEndTime());
            assertEquals(1,                  auction.getViewerCount());
            assertEquals(Auction.AuctionStatus.RUNNING, auction.getStatus());
        }

        @Test
        @DisplayName("viewerCount tăng không ảnh hưởng đến isReserveMet")
        void incrementViewerCount_doesNotAffectIsReserveMet() {
            // Arrange
            long startingPrice = 1_000_000L;
            long reservePrice  = 5_000_000L;
            Art item = TestFixture.art("Tranh", startingPrice, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    reservePrice);

            boolean reserveMetBefore = auction.isReserveMet(); // false

            // Act
            for (int i = 0; i < 50; i++) {
                auction.incrementViewerCount();
            }

            // Assert
            assertEquals(reserveMetBefore, auction.isReserveMet());
            assertFalse(auction.isReserveMet());
        }

        @Test
        @DisplayName("extendEndTime không thay đổi isReserveMet")
        void extendEndTime_doesNotAffectIsReserveMet() {
            // Arrange — reserve đã met ngay từ đầu
            long price = 1_000_000L;
            Art item = TestFixture.art("Tranh", price, seller);
            Auction auction = Auction.create(
                    item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    price); // reservePrice = startingPrice = currentPrice
            auction.transitionToRunning();
            assertTrue(auction.isReserveMet()); // precondition

            // Act
            auction.extendEndTime(Duration.ofMinutes(10));

            // Assert
            assertTrue(auction.isReserveMet());
        }

        @Test
        @DisplayName("hai auction riêng biệt không chia sẻ state")
        void twoAuctions_haveCompletelyIndependentState() {
            // Arrange
            Auction auction1 = TestFixture.runningAuction(seller, 1_000_000L);
            Auction auction2 = TestFixture.runningAuction(seller, 1_000_000L);

            // Act — thay đổi auction1
            auction1.updateBid(2_000_000L, bidder);
            auction1.extendEndTime(Duration.ofMinutes(5));
            auction1.incrementViewerCount();

            // Assert — auction2 không bị ảnh hưởng
            assertEquals(1_000_000L,              auction2.getCurrentPrice());
            assertNull(auction2.getCurrentLeader());
            assertEquals(auction2.getOriginalEndTime(), auction2.getEndTime());
            assertEquals(0,                        auction2.getViewerCount());
        }
    }
}