package com.group13.auction.strategy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link AutoBidRegistry}.
 *
 * <p><b>Chiến lược isolation:</b>
 * AutoBidRegistry là Singleton eager-init với {@code INSTANCE} final.
 * Không thể thay INSTANCE → reset nội bộ ConcurrentHashMap qua reflection
 * trong {@code @BeforeEach} / {@code @AfterEach} để đảm bảo mỗi test bắt đầu
 * với registry trống, hoàn toàn độc lập với thứ tự chạy.
 *
 * <p>Không mock, không DB, không network, không Thread.sleep().
 */
@DisplayName("AutoBidRegistry")
class AutoBidRegistryTest {

    // ── SUT ───────────────────────────────────────────────────────────────────
    private AutoBidRegistry registry;

    // ── Fixtures ──────────────────────────────────────────────────────────────
    private static final String USER_A    = "user-alpha";
    private static final String USER_B    = "user-bravo";
    private static final String USER_C    = "user-charlie";
    private static final String AUCTION_1 = "auction-001";
    private static final String AUCTION_2 = "auction-002";
    private static final long   MAX_BID_LOW  =   500_000L;
    private static final long   MAX_BID_MID  = 3_000_000L;
    private static final long   MAX_BID_HIGH = 8_000_000L;

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        registry = AutoBidRegistry.getInstance();
        clearInternalRegistry();
    }

    @AfterEach
    void tearDown() throws Exception {
        clearInternalRegistry();
    }

    @SuppressWarnings("unchecked")
    private void clearInternalRegistry() throws Exception {
        Field field = AutoBidRegistry.class.getDeclaredField("registry");
        field.setAccessible(true);
        ConcurrentHashMap<String, AutoBidRegistry.AutoBidEntry> map =
                (ConcurrentHashMap<String, AutoBidRegistry.AutoBidEntry>) field.get(registry);
        map.clear();
    }

    // =========================================================================
    // register()
    // =========================================================================

    @Nested
    @DisplayName("register() — đăng ký auto-bid")
    class RegisterTest {

        @Test
        @DisplayName("register hợp lệ → entry xuất hiện trong registry")
        void register_validInput_entryExists() {
            // Arrange & Act
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Assert
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_1));
        }

        @Test
        @DisplayName("register hợp lệ → get() trả về đúng userId")
        void register_validInput_getReturnsCorrectUserId() {
            // Arrange & Act
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Assert
            AutoBidRegistry.AutoBidEntry entry = registry.get(USER_A, AUCTION_1);
            assertNotNull(entry);
            assertEquals(USER_A, entry.getUserId());
        }

        @Test
        @DisplayName("register hợp lệ → get() trả về đúng auctionId")
        void register_validInput_getReturnsCorrectAuctionId() {
            // Arrange & Act
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Assert
            AutoBidRegistry.AutoBidEntry entry = registry.get(USER_A, AUCTION_1);
            assertNotNull(entry);
            assertEquals(AUCTION_1, entry.getAuctionId());
        }

        @Test
        @DisplayName("register hợp lệ → get() trả về đúng maxBid")
        void register_validInput_getReturnsCorrectMaxBid() {
            // Arrange & Act
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Assert
            AutoBidRegistry.AutoBidEntry entry = registry.get(USER_A, AUCTION_1);
            assertNotNull(entry);
            assertEquals(MAX_BID_MID, entry.getMaxBid());
        }

        @Test
        @DisplayName("register hợp lệ → registeredAt không null")
        void register_validInput_registeredAtIsNotNull() {
            // Arrange & Act
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Assert
            AutoBidRegistry.AutoBidEntry entry = registry.get(USER_A, AUCTION_1);
            assertNotNull(entry);
            assertNotNull(entry.getRegisteredAt());
        }

        @Test
        @DisplayName("register cùng userId+auctionId lần 2 → overwrite maxBid")
        void register_duplicateKey_overwritesMaxBid() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);

            // Act
            registry.register(USER_A, AUCTION_1, MAX_BID_HIGH);

            // Assert
            AutoBidRegistry.AutoBidEntry entry = registry.get(USER_A, AUCTION_1);
            assertNotNull(entry);
            assertEquals(MAX_BID_HIGH, entry.getMaxBid(),
                    "Register lần 2 cùng key phải ghi đè maxBid cũ");
        }

        @Test
        @DisplayName("register overwrite → registry chỉ chứa 1 entry cho cặp userId+auctionId")
        void register_duplicateKey_doesNotCreateExtraEntry() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);

            // Act
            registry.register(USER_A, AUCTION_1, MAX_BID_HIGH);

            // Assert
            Collection<AutoBidRegistry.AutoBidEntry> entries =
                    registry.getEntriesForAuction(AUCTION_1);
            assertEquals(1, entries.size(), "Overwrite không được tạo entry mới thừa");
        }

        @Test
        @DisplayName("register nhiều lần overwrite → maxBid luôn là giá trị cuối cùng")
        void register_multipleOverwrites_retainsLastMaxBid() {
            // Arrange & Act
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);
            registry.register(USER_A, AUCTION_1, MAX_BID_HIGH);
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);

            // Assert
            assertEquals(MAX_BID_LOW, registry.get(USER_A, AUCTION_1).getMaxBid());
        }

        @Test
        @DisplayName("register user khác nhau cùng 1 auction → cả hai đều tồn tại")
        void register_differentUsers_sameAuction_bothExist() {
            // Arrange & Act
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);
            registry.register(USER_B, AUCTION_1, MAX_BID_HIGH);

            // Assert
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_1));
            assertTrue(registry.hasActiveBid(USER_B, AUCTION_1));
        }

        @Test
        @DisplayName("register cùng user khác auction → hai entry độc lập")
        void register_sameUser_differentAuctions_bothExist() {
            // Arrange & Act
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);
            registry.register(USER_A, AUCTION_2, MAX_BID_HIGH);

            // Assert
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_1));
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_2));
        }

        @Test
        @DisplayName("register USER_A không ảnh hưởng USER_B trong cùng auction")
        void register_userA_doesNotAffectUserB() {
            // Arrange
            registry.register(USER_B, AUCTION_1, MAX_BID_MID);

            // Act — overwrite USER_A (không liên quan USER_B)
            registry.register(USER_A, AUCTION_1, MAX_BID_HIGH);

            // Assert
            assertEquals(MAX_BID_HIGH, registry.get(USER_A, AUCTION_1).getMaxBid());
            assertEquals(MAX_BID_MID,  registry.get(USER_B, AUCTION_1).getMaxBid(),
                    "Overwrite USER_A không được thay đổi maxBid của USER_B");
        }
    }

    // =========================================================================
    // cancel()
    // =========================================================================

    @Nested
    @DisplayName("cancel() — hủy auto-bid")
    class CancelTest {

        @Test
        @DisplayName("cancel entry tồn tại → trả về true")
        void cancel_existingEntry_returnsTrue() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Act & Assert
            assertTrue(registry.cancel(USER_A, AUCTION_1));
        }

        @Test
        @DisplayName("cancel entry tồn tại → entry biến mất khỏi registry")
        void cancel_existingEntry_entryNoLongerExists() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Act
            registry.cancel(USER_A, AUCTION_1);

            // Assert
            assertFalse(registry.hasActiveBid(USER_A, AUCTION_1));
        }

        @Test
        @DisplayName("cancel entry không tồn tại → trả về false")
        void cancel_nonExistentEntry_returnsFalse() {
            // Arrange — registry trống

            // Act & Assert
            assertFalse(registry.cancel(USER_A, AUCTION_1));
        }

        @Test
        @DisplayName("cancel 2 lần cùng entry → lần 2 trả về false")
        void cancel_twice_secondCancelReturnsFalse() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);
            registry.cancel(USER_A, AUCTION_1);

            // Act & Assert
            assertFalse(registry.cancel(USER_A, AUCTION_1));
        }

        @Test
        @DisplayName("cancel USER_A không ảnh hưởng USER_B cùng auction")
        void cancel_userA_doesNotAffectUserB() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);
            registry.register(USER_B, AUCTION_1, MAX_BID_HIGH);

            // Act
            registry.cancel(USER_A, AUCTION_1);

            // Assert
            assertFalse(registry.hasActiveBid(USER_A, AUCTION_1));
            assertTrue(registry.hasActiveBid(USER_B, AUCTION_1));
        }

        @Test
        @DisplayName("cancel USER_A auction1 không ảnh hưởng USER_A auction2")
        void cancel_auction1_doesNotAffectSameUserInAuction2() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);
            registry.register(USER_A, AUCTION_2, MAX_BID_HIGH);

            // Act
            registry.cancel(USER_A, AUCTION_1);

            // Assert
            assertFalse(registry.hasActiveBid(USER_A, AUCTION_1));
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_2));
        }

        @Test
        @DisplayName("cancel trên registry trống không ném exception")
        void cancel_emptyRegistry_doesNotThrow() {
            // Act & Assert
            assertDoesNotThrow(() -> registry.cancel(USER_A, AUCTION_1));
        }
    }

    // =========================================================================
    // get() / hasActiveBid()
    // =========================================================================

    @Nested
    @DisplayName("get() / hasActiveBid() — lookup")
    class GetAndHasActiveBidTest {

        @Test
        @DisplayName("get() sau register → trả về entry đúng")
        void get_afterRegister_returnsEntry() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Act
            AutoBidRegistry.AutoBidEntry entry = registry.get(USER_A, AUCTION_1);

            // Assert
            assertNotNull(entry);
            assertEquals(USER_A, entry.getUserId());
            assertEquals(AUCTION_1, entry.getAuctionId());
            assertEquals(MAX_BID_MID, entry.getMaxBid());
        }

        @Test
        @DisplayName("get() khi không có entry → trả về null không ném exception")
        void get_notExist_returnsNullWithoutException() {
            // Arrange — registry trống

            // Act & Assert
            assertDoesNotThrow(() -> {
                AutoBidRegistry.AutoBidEntry entry = registry.get(USER_A, AUCTION_1);
                assertNull(entry);
            });
        }

        @Test
        @DisplayName("get() sau cancel → trả về null")
        void get_afterCancel_returnsNull() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);
            registry.cancel(USER_A, AUCTION_1);

            // Act & Assert
            assertNull(registry.get(USER_A, AUCTION_1));
        }

        @Test
        @DisplayName("hasActiveBid() sau register → trả về true")
        void hasActiveBid_afterRegister_returnsTrue() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Act & Assert
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_1));
        }

        @Test
        @DisplayName("hasActiveBid() khi không có entry → trả về false")
        void hasActiveBid_notExist_returnsFalse() {
            // Assert
            assertFalse(registry.hasActiveBid(USER_A, AUCTION_1));
        }

        @Test
        @DisplayName("hasActiveBid() sau cancel → trả về false")
        void hasActiveBid_afterCancel_returnsFalse() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);
            registry.cancel(USER_A, AUCTION_1);

            // Assert
            assertFalse(registry.hasActiveBid(USER_A, AUCTION_1));
        }

        @Test
        @DisplayName("get() và hasActiveBid() nhất quán với nhau")
        void get_and_hasActiveBid_areConsistent() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Assert: cả hai phải đồng thuận
            boolean hasActive = registry.hasActiveBid(USER_A, AUCTION_1);
            AutoBidRegistry.AutoBidEntry entry = registry.get(USER_A, AUCTION_1);
            assertEquals(hasActive, entry != null);
        }
    }

    // =========================================================================
    // getEntriesForAuction()
    // =========================================================================

    @Nested
    @DisplayName("getEntriesForAuction() — snapshot lấy entries theo auction")
    class GetEntriesForAuctionTest {

        @Test
        @DisplayName("registry trống → trả về collection rỗng")
        void getEntriesForAuction_emptyRegistry_returnsEmpty() {
            // Act
            Collection<AutoBidRegistry.AutoBidEntry> entries =
                    registry.getEntriesForAuction(AUCTION_1);

            // Assert
            assertTrue(entries.isEmpty());
        }

        @Test
        @DisplayName("1 user trong auction → trả về 1 entry")
        void getEntriesForAuction_oneUser_returnsOneEntry() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Act
            Collection<AutoBidRegistry.AutoBidEntry> entries =
                    registry.getEntriesForAuction(AUCTION_1);

            // Assert
            assertEquals(1, entries.size());
        }

        @Test
        @DisplayName("3 user trong cùng auction → trả về 3 entry")
        void getEntriesForAuction_threeUsers_returnsThreeEntries() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);
            registry.register(USER_B, AUCTION_1, MAX_BID_MID);
            registry.register(USER_C, AUCTION_1, MAX_BID_HIGH);

            // Act
            Collection<AutoBidRegistry.AutoBidEntry> entries =
                    registry.getEntriesForAuction(AUCTION_1);

            // Assert
            assertEquals(3, entries.size());
        }

        @Test
        @DisplayName("getEntriesForAuction(auction1) chỉ trả về entry của auction1, không lẫn auction2")
        void getEntriesForAuction_filtersCorrectAuction() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);
            registry.register(USER_B, AUCTION_2, MAX_BID_HIGH); // auction2 — không được include

            // Act
            Collection<AutoBidRegistry.AutoBidEntry> entries =
                    registry.getEntriesForAuction(AUCTION_1);

            // Assert
            assertEquals(1, entries.size());
            entries.forEach(e -> assertEquals(AUCTION_1, e.getAuctionId()));
        }

        @Test
        @DisplayName("trả về snapshot — thay đổi sau khi gọi không ảnh hưởng kết quả")
        void getEntriesForAuction_returnsSnapshot_notLiveView() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);

            // Act: lấy snapshot trước, rồi thêm entry mới
            Collection<AutoBidRegistry.AutoBidEntry> snapshot =
                    registry.getEntriesForAuction(AUCTION_1);
            registry.register(USER_B, AUCTION_1, MAX_BID_HIGH);

            // Assert: snapshot không thay đổi
            assertEquals(1, snapshot.size(),
                    "Snapshot không được bị ảnh hưởng bởi thay đổi sau khi lấy");
        }

        @Test
        @DisplayName("sau cancel → getEntriesForAuction không còn chứa entry đó")
        void getEntriesForAuction_afterCancel_entryRemoved() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);
            registry.register(USER_B, AUCTION_1, MAX_BID_HIGH);
            registry.cancel(USER_A, AUCTION_1);

            // Act
            Collection<AutoBidRegistry.AutoBidEntry> entries =
                    registry.getEntriesForAuction(AUCTION_1);

            // Assert
            assertEquals(1, entries.size());
            entries.forEach(e -> assertEquals(USER_B, e.getUserId()));
        }
    }

    // =========================================================================
    // clearAuction()
    // =========================================================================

    @Nested
    @DisplayName("clearAuction() — xóa toàn bộ entry của một phiên")
    class ClearAuctionTest {

        @Test
        @DisplayName("clearAuction → tất cả entry của phiên đó bị xóa")
        void clearAuction_removesAllEntriesForAuction() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);
            registry.register(USER_B, AUCTION_1, MAX_BID_MID);
            registry.register(USER_C, AUCTION_1, MAX_BID_HIGH);

            // Act
            registry.clearAuction(AUCTION_1);

            // Assert
            assertTrue(registry.getEntriesForAuction(AUCTION_1).isEmpty());
        }

        @Test
        @DisplayName("clearAuction(auction1) không xóa entry của auction2")
        void clearAuction_doesNotAffectOtherAuctions() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);
            registry.register(USER_B, AUCTION_2, MAX_BID_HIGH);

            // Act
            registry.clearAuction(AUCTION_1);

            // Assert: auction2 không bị ảnh hưởng
            assertTrue(registry.hasActiveBid(USER_B, AUCTION_2));
        }

        @Test
        @DisplayName("clearAuction trên registry trống không ném exception")
        void clearAuction_emptyRegistry_doesNotThrow() {
            // Act & Assert
            assertDoesNotThrow(() -> registry.clearAuction(AUCTION_1));
        }

        @Test
        @DisplayName("clearAuction rồi register lại → hoạt động bình thường")
        void clearAuction_thenRegisterAgain_worksNormally() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_MID);
            registry.clearAuction(AUCTION_1);

            // Act
            registry.register(USER_A, AUCTION_1, MAX_BID_HIGH);

            // Assert
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_1));
            assertEquals(MAX_BID_HIGH, registry.get(USER_A, AUCTION_1).getMaxBid());
        }

        @Test
        @DisplayName("clearAuction với auction không tồn tại trong registry → không ném exception")
        void clearAuction_nonExistentAuction_doesNotThrow() {
            // Arrange
            registry.register(USER_A, AUCTION_2, MAX_BID_MID);

            // Act & Assert
            assertDoesNotThrow(() -> registry.clearAuction(AUCTION_1));
        }
    }

    // =========================================================================
    // AutoBidEntry.calculateNextBid()
    // =========================================================================

    @Nested
    @DisplayName("AutoBidEntry.calculateNextBid() — tính giá bid kế tiếp")
    class CalculateNextBidTest {

        @Test
        @DisplayName("currentPrice + increment ≤ maxBid → trả về currentPrice + increment")
        void calculateNextBid_withinBudget_returnsNextPrice() {
            // Arrange — currentPrice=2_000_000, increment=200_000, next=2_200_000 ≤ 5_000_000
            AutoBidRegistry.AutoBidEntry entry = new AutoBidRegistry.AutoBidEntry(
                    USER_A, AUCTION_1, 5_000_000L, LocalDateTime.now());

            // Act
            long next = entry.calculateNextBid(2_000_000L);

            // Assert
            assertEquals(2_200_000L, next);
        }

        @Test
        @DisplayName("currentPrice + increment > maxBid → trả về -1")
        void calculateNextBid_exceedsBudget_returnsNegativeOne() {
            // Arrange — maxBid=2_100_000, currentPrice=2_000_000, next=2_200_000 > maxBid
            AutoBidRegistry.AutoBidEntry entry = new AutoBidRegistry.AutoBidEntry(
                    USER_A, AUCTION_1, 2_100_000L, LocalDateTime.now());

            // Act
            long next = entry.calculateNextBid(2_000_000L);

            // Assert
            assertEquals(-1L, next);
        }

        @Test
        @DisplayName("maxBid == nextBid → trả về nextBid (biên trên inclusive)")
        void calculateNextBid_maxBidEqualsNextBid_returnsNextBid() {
            // Arrange — currentPrice=1_000_000, increment=200_000, next=1_200_000 == maxBid
            AutoBidRegistry.AutoBidEntry entry = new AutoBidRegistry.AutoBidEntry(
                    USER_A, AUCTION_1, 1_200_000L, LocalDateTime.now());

            // Act
            long next = entry.calculateNextBid(1_000_000L);

            // Assert
            assertEquals(1_200_000L, next,
                    "Khi nextBid == maxBid vẫn phải bid được (boundary inclusive)");
        }

        @Test
        @DisplayName("calculateNextBid gọi nhiều lần cùng input → kết quả deterministic")
        void calculateNextBid_repeatedCall_sameResult() {
            // Arrange
            AutoBidRegistry.AutoBidEntry entry = new AutoBidRegistry.AutoBidEntry(
                    USER_A, AUCTION_1, 5_000_000L, LocalDateTime.now());

            // Act
            long first  = entry.calculateNextBid(1_000_000L);
            long second = entry.calculateNextBid(1_000_000L);

            // Assert
            assertEquals(first, second);
        }

        @Test
        @DisplayName("tier thấp (currentPrice=500_000) → increment=50_000")
        void calculateNextBid_lowTier_returnsLowIncrement() {
            // Arrange
            AutoBidRegistry.AutoBidEntry entry = new AutoBidRegistry.AutoBidEntry(
                    USER_A, AUCTION_1, 1_000_000L, LocalDateTime.now());

            // Act
            long next = entry.calculateNextBid(500_000L);

            // Assert
            assertEquals(550_000L, next);
        }

        @Test
        @DisplayName("tier trung (currentPrice=1_000_000) → increment=200_000")
        void calculateNextBid_midTier_returnsMidIncrement() {
            // Arrange
            AutoBidRegistry.AutoBidEntry entry = new AutoBidRegistry.AutoBidEntry(
                    USER_A, AUCTION_1, 3_000_000L, LocalDateTime.now());

            // Act
            long next = entry.calculateNextBid(1_000_000L);

            // Assert
            assertEquals(1_200_000L, next);
        }

        @Test
        @DisplayName("tier cao (currentPrice=11_000_000) → increment=500_000")
        void calculateNextBid_highTier_returnsHighIncrement() {
            // Arrange
            AutoBidRegistry.AutoBidEntry entry = new AutoBidRegistry.AutoBidEntry(
                    USER_A, AUCTION_1, 20_000_000L, LocalDateTime.now());

            // Act
            long next = entry.calculateNextBid(11_000_000L);

            // Assert
            assertEquals(11_500_000L, next);
        }

        @Test
        @DisplayName("currentPrice=0 → nextBid = increment thấp nhất (50_000)")
        void calculateNextBid_zeroPriceCurrentPrice_returnsLowIncrement() {
            // Arrange
            AutoBidRegistry.AutoBidEntry entry = new AutoBidRegistry.AutoBidEntry(
                    USER_A, AUCTION_1, 100_000L, LocalDateTime.now());

            // Act
            long next = entry.calculateNextBid(0L);

            // Assert
            assertEquals(50_000L, next);
        }
    }

    // =========================================================================
    // Edge cases — register/cancel lifecycle
    // =========================================================================

    @Nested
    @DisplayName("Edge cases — register/cancel cycle, isolated state")
    class EdgeCaseTest {

        @Test
        @DisplayName("register → cancel → register lại → entry tồn tại với maxBid mới")
        void registerCancelRegister_cycle_entryExistsWithNewMaxBid() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);
            registry.cancel(USER_A, AUCTION_1);

            // Act
            registry.register(USER_A, AUCTION_1, MAX_BID_HIGH);

            // Assert
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_1));
            assertEquals(MAX_BID_HIGH, registry.get(USER_A, AUCTION_1).getMaxBid());
        }

        @Test
        @DisplayName("register/cancel cycle nhiều lần → trạng thái cuối chính xác")
        void multipleRegisterCancelCycles_finalStateIsCorrect() {
            // Arrange & Act
            for (int i = 0; i < 5; i++) {
                registry.register(USER_A, AUCTION_1, MAX_BID_MID);
                registry.cancel(USER_A, AUCTION_1);
            }
            registry.register(USER_A, AUCTION_1, MAX_BID_HIGH);

            // Assert
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_1));
            assertEquals(MAX_BID_HIGH, registry.get(USER_A, AUCTION_1).getMaxBid());
        }

        @Test
        @DisplayName("nhiều user, nhiều auction — cancel một cặp không ảnh hưởng phần còn lại")
        void multiUserMultiAuction_removeOnePair_restIntact() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);
            registry.register(USER_A, AUCTION_2, MAX_BID_MID);
            registry.register(USER_B, AUCTION_1, MAX_BID_MID);
            registry.register(USER_B, AUCTION_2, MAX_BID_HIGH);
            registry.register(USER_C, AUCTION_1, MAX_BID_HIGH);

            // Act
            registry.cancel(USER_B, AUCTION_1);

            // Assert — 4 cặp còn lại nguyên vẹn
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_1));
            assertTrue(registry.hasActiveBid(USER_A, AUCTION_2));
            assertFalse(registry.hasActiveBid(USER_B, AUCTION_1));
            assertTrue(registry.hasActiveBid(USER_B, AUCTION_2));
            assertTrue(registry.hasActiveBid(USER_C, AUCTION_1));
        }

        @Test
        @DisplayName("overwrite không để lại orphan entry — getEntriesForAuction không trả về entry cũ")
        void overwrite_doesNotLeaveOrphanEntry() {
            // Arrange
            registry.register(USER_A, AUCTION_1, MAX_BID_LOW);

            // Act
            registry.register(USER_A, AUCTION_1, MAX_BID_HIGH);

            // Assert — chỉ 1 entry, không có orphan với maxBid cũ
            Collection<AutoBidRegistry.AutoBidEntry> entries =
                    registry.getEntriesForAuction(AUCTION_1);
            assertEquals(1, entries.size());
            entries.forEach(e -> assertNotEquals(MAX_BID_LOW, e.getMaxBid(),
                    "Không được còn orphan entry với maxBid cũ"));
        }

        @Test
        @DisplayName("get() trên registry trống không ném exception — trả về null")
        void get_emptyRegistry_returnsNullWithoutException() {
            // Act & Assert
            assertDoesNotThrow(() -> {
                AutoBidRegistry.AutoBidEntry entry = registry.get(USER_A, AUCTION_1);
                assertNull(entry);
            });
        }
    }
}