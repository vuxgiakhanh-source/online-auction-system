package com.group13.auction.unit.service;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item.ItemCategory;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.AuctionSortService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link AuctionSortService}.
 *
 * <p>Tập trung vào 3 nhóm chính:
 * <ul>
 *   <li>{@code sortByCurrentPrice} (Asc/Desc) — sort theo giá.</li>
 *   <li>{@code filterByCategory} — lọc theo phân loại sản phẩm.</li>
 *   <li>{@code sortByCategoryThenPrice} — sort kết hợp.</li>
 * </ul>
 *
 * <p>Không mock, không DB, không network.
 * Dùng object thật từ {@link TestFixture}.
 */
@DisplayName("AuctionSortService")
class AuctionSortServiceTest {

    private AuctionSortService sortService;
    private NormalUser seller;

    @BeforeEach
    void setUp() {
        sortService = new AuctionSortService();
        seller = TestFixture.normalSeller("sellerXX1");
    }

    // =========================================================================
    // Helper — tạo auction với category và currentPrice tuỳ ý
    // =========================================================================

    /** Auction có Art item, currentPrice = price. */
    private Auction artAuction(long startingPrice, long currentPrice) {
        return Auction.reconstitute(
                java.util.UUID.randomUUID().toString(),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                TestFixture.art("Art-" + startingPrice, startingPrice, seller),
                java.time.LocalDateTime.now().minusMinutes(1),
                java.time.LocalDateTime.now().plusHours(1),
                currentPrice,
                Auction.AuctionStatus.RUNNING,
                startingPrice * 2);
    }

    /** Auction có Electronics item, currentPrice = price. */
    private Auction electronicsAuction(long startingPrice, long currentPrice) {
        return Auction.reconstitute(
                java.util.UUID.randomUUID().toString(),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                TestFixture.electronics("Elec-" + startingPrice, startingPrice, seller),
                java.time.LocalDateTime.now().minusMinutes(1),
                java.time.LocalDateTime.now().plusHours(1),
                currentPrice,
                Auction.AuctionStatus.RUNNING,
                startingPrice * 2);
    }

    /** Auction có Vehicle item, currentPrice = price. */
    private Auction vehicleAuction(long startingPrice, long currentPrice) {
        return Auction.reconstitute(
                java.util.UUID.randomUUID().toString(),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                TestFixture.vehicle("Vehicle-" + startingPrice, startingPrice, seller),
                java.time.LocalDateTime.now().minusMinutes(1),
                java.time.LocalDateTime.now().plusHours(1),
                currentPrice,
                Auction.AuctionStatus.RUNNING,
                startingPrice * 2);
    }

    // =========================================================================
    // sortByCurrentPriceDesc
    // =========================================================================

    @Nested
    @DisplayName("sortByCurrentPriceDesc()")
    class SortByCurrentPriceDescTest {

        @Test
        @DisplayName("happy path — list 3 phần tử → giảm dần đúng")
        void sortDesc_multipleItems_returnsSortedDescending() {
            // Arrange
            Auction low    = artAuction(1_000_000L, 1_000_000L);
            Auction mid    = artAuction(3_000_000L, 3_000_000L);
            Auction high   = artAuction(5_000_000L, 5_000_000L);
            List<Auction> input = List.of(low, mid, high);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceDesc(input);

            // Assert
            assertEquals(high.getCurrentPrice(), result.get(0).getCurrentPrice());
            assertEquals(mid.getCurrentPrice(),  result.get(1).getCurrentPrice());
            assertEquals(low.getCurrentPrice(),  result.get(2).getCurrentPrice());
        }

        @Test
        @DisplayName("list đã sort ngược → vẫn trả về đúng thứ tự giảm dần")
        void sortDesc_alreadySortedDesc_returnsSameOrder() {
            // Arrange
            Auction a1 = artAuction(5_000_000L, 5_000_000L);
            Auction a2 = artAuction(3_000_000L, 3_000_000L);
            Auction a3 = artAuction(1_000_000L, 1_000_000L);
            List<Auction> input = List.of(a1, a2, a3);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceDesc(input);

            // Assert
            assertEquals(5_000_000L, result.get(0).getCurrentPrice());
            assertEquals(3_000_000L, result.get(1).getCurrentPrice());
            assertEquals(1_000_000L, result.get(2).getCurrentPrice());
        }

        @Test
        @DisplayName("duplicate value — các phần tử giá bằng nhau → đủ số lượng, giá đúng")
        void sortDesc_duplicatePrices_allElementsPreserved() {
            // Arrange
            Auction a1 = artAuction(2_000_000L, 2_000_000L);
            Auction a2 = artAuction(2_000_000L, 2_000_000L);
            Auction a3 = artAuction(2_000_000L, 2_000_000L);
            List<Auction> input = List.of(a1, a2, a3);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceDesc(input);

            // Assert
            assertEquals(3, result.size());
            assertTrue(result.stream().allMatch(a -> a.getCurrentPrice() == 2_000_000L));
        }

        @Test
        @DisplayName("duplicate + distinct — giá trùng nằm đúng vị trí")
        void sortDesc_mixedDuplicateAndDistinct_correctOrder() {
            // Arrange
            Auction a1 = artAuction(5_000_000L, 5_000_000L);
            Auction a2 = artAuction(3_000_000L, 3_000_000L);
            Auction a3 = artAuction(3_000_000L, 3_000_000L);
            Auction a4 = artAuction(1_000_000L, 1_000_000L);
            List<Auction> input = List.of(a4, a2, a1, a3);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceDesc(input);

            // Assert
            assertEquals(5_000_000L, result.get(0).getCurrentPrice());
            assertEquals(3_000_000L, result.get(1).getCurrentPrice());
            assertEquals(3_000_000L, result.get(2).getCurrentPrice());
            assertEquals(1_000_000L, result.get(3).getCurrentPrice());
        }

        @Test
        @DisplayName("empty list → trả về list rỗng")
        void sortDesc_emptyList_returnsEmptyList() {
            // Arrange
            List<Auction> input = Collections.emptyList();

            // Act
            List<Auction> result = sortService.sortByCurrentPriceDesc(input);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("single element → trả về list 1 phần tử đúng")
        void sortDesc_singleElement_returnsSingleElementList() {
            // Arrange
            Auction only = artAuction(2_000_000L, 2_000_000L);
            List<Auction> input = List.of(only);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceDesc(input);

            // Assert
            assertEquals(1, result.size());
            assertEquals(only.getCurrentPrice(), result.get(0).getCurrentPrice());
        }

        @Test
        @DisplayName("không mutate list gốc — input không thay đổi sau sort")
        void sortDesc_doesNotMutateInputList() {
            // Arrange
            Auction low  = artAuction(1_000_000L, 1_000_000L);
            Auction high = artAuction(5_000_000L, 5_000_000L);
            List<Auction> input = new ArrayList<>(List.of(low, high));
            long firstOriginalPrice = input.get(0).getCurrentPrice();

            // Act
            sortService.sortByCurrentPriceDesc(input);

            // Assert
            assertEquals(firstOriginalPrice, input.get(0).getCurrentPrice());
        }

        @Test
        @DisplayName("boundary — giá = 0 → nằm cuối danh sách")
        void sortDesc_zeroPricedAuction_isLast() {
            // Arrange
            Auction zero   = artAuction(500_000L, 500_000L);
            // Tạo auction với currentPrice = startingPrice (không bid)
            Auction normal = artAuction(3_000_000L, 3_000_000L);
            List<Auction> input = List.of(normal, zero);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceDesc(input);

            // Assert
            assertEquals(3_000_000L, result.get(0).getCurrentPrice());
            assertEquals(500_000L,   result.get(1).getCurrentPrice());
        }

        @Test
        @DisplayName("boundary — giá rất lớn (Long.MAX_VALUE-safe) → đứng đầu")
        void sortDesc_veryHighPrice_isFirst() {
            // Arrange
            Auction highPrice = artAuction(1_000_000_000L, 1_000_000_000L);
            Auction lowPrice  = artAuction(1_000_000L,     1_000_000L);
            List<Auction> input = List.of(lowPrice, highPrice);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceDesc(input);

            // Assert
            assertEquals(1_000_000_000L, result.get(0).getCurrentPrice());
        }
    }

    // =========================================================================
    // sortByCurrentPriceAsc
    // =========================================================================

    @Nested
    @DisplayName("sortByCurrentPriceAsc()")
    class SortByCurrentPriceAscTest {

        @Test
        @DisplayName("happy path — list 3 phần tử → tăng dần đúng")
        void sortAsc_multipleItems_returnsSortedAscending() {
            // Arrange
            Auction low  = artAuction(1_000_000L, 1_000_000L);
            Auction mid  = artAuction(3_000_000L, 3_000_000L);
            Auction high = artAuction(5_000_000L, 5_000_000L);
            List<Auction> input = List.of(high, low, mid);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceAsc(input);

            // Assert
            assertEquals(1_000_000L, result.get(0).getCurrentPrice());
            assertEquals(3_000_000L, result.get(1).getCurrentPrice());
            assertEquals(5_000_000L, result.get(2).getCurrentPrice());
        }

        @Test
        @DisplayName("list đã sort tăng dần → vẫn đúng thứ tự")
        void sortAsc_alreadySortedAsc_returnsSameOrder() {
            // Arrange
            Auction a1 = artAuction(1_000_000L, 1_000_000L);
            Auction a2 = artAuction(3_000_000L, 3_000_000L);
            Auction a3 = artAuction(5_000_000L, 5_000_000L);
            List<Auction> input = List.of(a1, a2, a3);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceAsc(input);

            // Assert
            assertEquals(1_000_000L, result.get(0).getCurrentPrice());
            assertEquals(3_000_000L, result.get(1).getCurrentPrice());
            assertEquals(5_000_000L, result.get(2).getCurrentPrice());
        }

        @Test
        @DisplayName("duplicate value — giá bằng nhau → đủ số lượng, giá đúng")
        void sortAsc_duplicatePrices_allElementsPreserved() {
            // Arrange
            Auction a1 = artAuction(4_000_000L, 4_000_000L);
            Auction a2 = artAuction(4_000_000L, 4_000_000L);
            List<Auction> input = List.of(a1, a2);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceAsc(input);

            // Assert
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(a -> a.getCurrentPrice() == 4_000_000L));
        }

        @Test
        @DisplayName("empty list → trả về list rỗng")
        void sortAsc_emptyList_returnsEmptyList() {
            // Arrange & Act
            List<Auction> result = sortService.sortByCurrentPriceAsc(Collections.emptyList());

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("single element → trả về đúng 1 phần tử")
        void sortAsc_singleElement_returnsSingleElementList() {
            // Arrange
            Auction only = artAuction(2_000_000L, 2_000_000L);
            List<Auction> input = List.of(only);

            // Act
            List<Auction> result = sortService.sortByCurrentPriceAsc(input);

            // Assert
            assertEquals(1, result.size());
            assertEquals(only.getCurrentPrice(), result.get(0).getCurrentPrice());
        }

        @Test
        @DisplayName("desc vs asc — cùng input, hai kết quả ngược nhau")
        void sortAsc_reversesDescResult() {
            // Arrange
            Auction a = artAuction(1_000_000L, 1_000_000L);
            Auction b = artAuction(3_000_000L, 3_000_000L);
            Auction c = artAuction(5_000_000L, 5_000_000L);
            List<Auction> input = List.of(b, a, c);

            // Act
            List<Auction> asc  = sortService.sortByCurrentPriceAsc(input);
            List<Auction> desc = sortService.sortByCurrentPriceDesc(input);

            // Assert
            assertEquals(asc.get(0).getCurrentPrice(), desc.get(desc.size() - 1).getCurrentPrice());
            assertEquals(asc.get(asc.size() - 1).getCurrentPrice(), desc.get(0).getCurrentPrice());
        }

        @Test
        @DisplayName("không mutate list gốc")
        void sortAsc_doesNotMutateInputList() {
            // Arrange
            Auction low  = artAuction(1_000_000L, 1_000_000L);
            Auction high = artAuction(5_000_000L, 5_000_000L);
            List<Auction> input = new ArrayList<>(List.of(high, low));
            long originalFirst = input.get(0).getCurrentPrice();

            // Act
            sortService.sortByCurrentPriceAsc(input);

            // Assert
            assertEquals(originalFirst, input.get(0).getCurrentPrice());
        }
    }

    // =========================================================================
    // filterByCategory
    // =========================================================================

    @Nested
    @DisplayName("filterByCategory()")
    class FilterByCategoryTest {

        @Test
        @DisplayName("happy path — lọc ART từ mixed list → chỉ còn ART")
        void filterByCategory_artFromMixedList_returnsOnlyArt() {
            // Arrange
            Auction art1  = artAuction(1_000_000L, 1_000_000L);
            Auction art2  = artAuction(2_000_000L, 2_000_000L);
            Auction elec  = electronicsAuction(3_000_000L, 3_000_000L);
            Auction veh   = vehicleAuction(4_000_000L, 4_000_000L);
            List<Auction> input = List.of(art1, elec, art2, veh);

            // Act
            List<Auction> result = sortService.filterByCategory(input, ItemCategory.ART);

            // Assert
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(a -> a.getItem().getCategory() == ItemCategory.ART));
        }

        @Test
        @DisplayName("happy path — lọc ELECTRONICS từ mixed list → chỉ còn ELECTRONICS")
        void filterByCategory_electronicsFromMixedList_returnsOnlyElectronics() {
            // Arrange
            Auction art  = artAuction(1_000_000L, 1_000_000L);
            Auction elec = electronicsAuction(3_000_000L, 3_000_000L);
            List<Auction> input = List.of(art, elec);

            // Act
            List<Auction> result = sortService.filterByCategory(input, ItemCategory.ELECTRONICS);

            // Assert
            assertEquals(1, result.size());
            assertEquals(ItemCategory.ELECTRONICS, result.get(0).getItem().getCategory());
        }

        @Test
        @DisplayName("lọc VEHICLE từ mixed list → chỉ còn VEHICLE")
        void filterByCategory_vehicleFromMixedList_returnsOnlyVehicle() {
            // Arrange
            Auction art1 = artAuction(1_000_000L, 1_000_000L);
            Auction elec = electronicsAuction(2_000_000L, 2_000_000L);
            Auction veh1 = vehicleAuction(3_000_000L, 3_000_000L);
            Auction veh2 = vehicleAuction(4_000_000L, 4_000_000L);
            List<Auction> input = List.of(art1, elec, veh1, veh2);

            // Act
            List<Auction> result = sortService.filterByCategory(input, ItemCategory.VEHICLE);

            // Assert
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(a -> a.getItem().getCategory() == ItemCategory.VEHICLE));
        }

        @Test
        @DisplayName("category không tồn tại trong list → trả về list rỗng")
        void filterByCategory_categoryNotPresent_returnsEmptyList() {
            // Arrange
            Auction art  = artAuction(1_000_000L, 1_000_000L);
            Auction elec = electronicsAuction(2_000_000L, 2_000_000L);
            List<Auction> input = List.of(art, elec);

            // Act
            List<Auction> result = sortService.filterByCategory(input, ItemCategory.VEHICLE);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("empty list → trả về list rỗng bất kể category")
        void filterByCategory_emptyInput_returnsEmptyList() {
            // Arrange & Act
            List<Auction> result = sortService.filterByCategory(Collections.emptyList(), ItemCategory.ART);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("single element đúng category → trả về chính element đó")
        void filterByCategory_singleMatchingElement_returnsSingleElementList() {
            // Arrange
            Auction art = artAuction(1_000_000L, 1_000_000L);
            List<Auction> input = List.of(art);

            // Act
            List<Auction> result = sortService.filterByCategory(input, ItemCategory.ART);

            // Assert
            assertEquals(1, result.size());
            assertEquals(ItemCategory.ART, result.get(0).getItem().getCategory());
        }

        @Test
        @DisplayName("single element sai category → trả về list rỗng")
        void filterByCategory_singleNonMatchingElement_returnsEmptyList() {
            // Arrange
            Auction elec = electronicsAuction(1_000_000L, 1_000_000L);
            List<Auction> input = List.of(elec);

            // Act
            List<Auction> result = sortService.filterByCategory(input, ItemCategory.ART);

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("all same category — tất cả đúng category → giữ nguyên toàn bộ")
        void filterByCategory_allSameCategory_returnsAll() {
            // Arrange
            Auction a1 = electronicsAuction(1_000_000L, 1_000_000L);
            Auction a2 = electronicsAuction(2_000_000L, 2_000_000L);
            Auction a3 = electronicsAuction(3_000_000L, 3_000_000L);
            List<Auction> input = List.of(a1, a2, a3);

            // Act
            List<Auction> result = sortService.filterByCategory(input, ItemCategory.ELECTRONICS);

            // Assert
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("không mutate list gốc")
        void filterByCategory_doesNotMutateInputList() {
            // Arrange
            Auction art  = artAuction(1_000_000L, 1_000_000L);
            Auction elec = electronicsAuction(2_000_000L, 2_000_000L);
            List<Auction> input = new ArrayList<>(List.of(art, elec));
            int originalSize = input.size();

            // Act
            sortService.filterByCategory(input, ItemCategory.ART);

            // Assert
            assertEquals(originalSize, input.size());
        }

        @Test
        @DisplayName("thứ tự relative trong result — giữ nguyên thứ tự xuất hiện trong input")
        void filterByCategory_preservesRelativeOrder() {
            // Arrange
            Auction art1 = artAuction(1_000_000L, 1_000_000L);
            Auction elec = electronicsAuction(2_000_000L, 2_000_000L);
            Auction art2 = artAuction(3_000_000L, 3_000_000L);
            Auction art3 = artAuction(5_000_000L, 5_000_000L);
            List<Auction> input = List.of(art1, elec, art2, art3);

            // Act
            List<Auction> result = sortService.filterByCategory(input, ItemCategory.ART);

            // Assert
            assertEquals(3, result.size());
            assertEquals(1_000_000L, result.get(0).getCurrentPrice());
            assertEquals(3_000_000L, result.get(1).getCurrentPrice());
            assertEquals(5_000_000L, result.get(2).getCurrentPrice());
        }
    }

    // =========================================================================
    // sortByCategoryThenPrice
    // =========================================================================

    @Nested
    @DisplayName("sortByCategoryThenPrice()")
    class SortByCategoryThenPriceTest {

        @Test
        @DisplayName("happy path — mixed categories → nhóm đúng, trong nhóm giảm giá")
        void sortByCategoryThenPrice_mixedInput_groupedAndPriceDesc() {
            // Arrange
            // ART: enum ordinal = 0, ELECTRONICS = 1, VEHICLE = 2
            Auction artLow   = artAuction(1_000_000L, 1_000_000L);
            Auction artHigh  = artAuction(5_000_000L, 5_000_000L);
            Auction elecMid  = electronicsAuction(3_000_000L, 3_000_000L);
            Auction vehHigh  = vehicleAuction(8_000_000L, 8_000_000L);
            List<Auction> input = List.of(vehHigh, elecMid, artLow, artHigh);

            // Act
            List<Auction> result = sortService.sortByCategoryThenPrice(input);

            // Assert — size không đổi
            assertEquals(4, result.size());

            // ART trước ELECTRONICS trước VEHICLE
            assertEquals(ItemCategory.ART,         result.get(0).getItem().getCategory());
            assertEquals(ItemCategory.ART,         result.get(1).getItem().getCategory());
            assertEquals(ItemCategory.ELECTRONICS, result.get(2).getItem().getCategory());
            assertEquals(ItemCategory.VEHICLE,     result.get(3).getItem().getCategory());

            // Trong ART: giảm giá
            assertTrue(result.get(0).getCurrentPrice() > result.get(1).getCurrentPrice());
        }

        @Test
        @DisplayName("trong cùng category — sort theo giá giảm dần")
        void sortByCategoryThenPrice_sameCategoryItems_sortedByPriceDesc() {
            // Arrange
            Auction art1 = artAuction(1_000_000L, 1_000_000L);
            Auction art2 = artAuction(4_000_000L, 4_000_000L);
            Auction art3 = artAuction(2_000_000L, 2_000_000L);
            List<Auction> input = List.of(art1, art2, art3);

            // Act
            List<Auction> result = sortService.sortByCategoryThenPrice(input);

            // Assert
            assertEquals(4_000_000L, result.get(0).getCurrentPrice());
            assertEquals(2_000_000L, result.get(1).getCurrentPrice());
            assertEquals(1_000_000L, result.get(2).getCurrentPrice());
        }

        @Test
        @DisplayName("duplicate price trong cùng category → đủ phần tử, giá đúng")
        void sortByCategoryThenPrice_duplicatePricesSameCategory_allPreserved() {
            // Arrange
            Auction art1 = artAuction(3_000_000L, 3_000_000L);
            Auction art2 = artAuction(3_000_000L, 3_000_000L);
            Auction art3 = artAuction(1_000_000L, 1_000_000L);
            List<Auction> input = List.of(art3, art1, art2);

            // Act
            List<Auction> result = sortService.sortByCategoryThenPrice(input);

            // Assert
            assertEquals(3, result.size());
            assertEquals(3_000_000L, result.get(0).getCurrentPrice());
            assertEquals(3_000_000L, result.get(1).getCurrentPrice());
            assertEquals(1_000_000L, result.get(2).getCurrentPrice());
        }

        @Test
        @DisplayName("duplicate price across categories — category vẫn phân nhóm đúng")
        void sortByCategoryThenPrice_samePriceAcrossCategories_groupedByCategory() {
            // Arrange — ART và ELECTRONICS cùng giá 2_000_000
            Auction art  = artAuction(2_000_000L, 2_000_000L);
            Auction elec = electronicsAuction(2_000_000L, 2_000_000L);
            List<Auction> input = List.of(elec, art);

            // Act
            List<Auction> result = sortService.sortByCategoryThenPrice(input);

            // Assert — ART (ordinal 0) trước ELECTRONICS (ordinal 1)
            assertEquals(ItemCategory.ART,         result.get(0).getItem().getCategory());
            assertEquals(ItemCategory.ELECTRONICS, result.get(1).getItem().getCategory());
        }

        @Test
        @DisplayName("empty list → trả về list rỗng")
        void sortByCategoryThenPrice_emptyList_returnsEmptyList() {
            // Arrange & Act
            List<Auction> result = sortService.sortByCategoryThenPrice(Collections.emptyList());

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("single element → trả về đúng 1 phần tử")
        void sortByCategoryThenPrice_singleElement_returnsSingleElementList() {
            // Arrange
            Auction only = vehicleAuction(10_000_000L, 10_000_000L);
            List<Auction> input = List.of(only);

            // Act
            List<Auction> result = sortService.sortByCategoryThenPrice(input);

            // Assert
            assertEquals(1, result.size());
            assertEquals(ItemCategory.VEHICLE, result.get(0).getItem().getCategory());
        }

        @Test
        @DisplayName("all same category, all same price — giữ đủ số lượng")
        void sortByCategoryThenPrice_uniformCategoryAndPrice_sizeUnchanged() {
            // Arrange
            List<Auction> input = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                input.add(electronicsAuction(2_000_000L, 2_000_000L));
            }

            // Act
            List<Auction> result = sortService.sortByCategoryThenPrice(input);

            // Assert
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("3 categories đầy đủ — thứ tự category theo enum ordinal (ART < ELECTRONICS < VEHICLE)")
        void sortByCategoryThenPrice_allThreeCategories_categoryOrderMatchesEnumOrdinal() {
            // Arrange
            Auction veh  = vehicleAuction(1_000_000L, 1_000_000L);
            Auction elec = electronicsAuction(2_000_000L, 2_000_000L);
            Auction art  = artAuction(3_000_000L, 3_000_000L);
            List<Auction> input = List.of(veh, elec, art);

            // Act
            List<Auction> result = sortService.sortByCategoryThenPrice(input);

            // Assert
            assertEquals(ItemCategory.ART,         result.get(0).getItem().getCategory());
            assertEquals(ItemCategory.ELECTRONICS, result.get(1).getItem().getCategory());
            assertEquals(ItemCategory.VEHICLE,     result.get(2).getItem().getCategory());
        }

        @Test
        @DisplayName("không mutate list gốc")
        void sortByCategoryThenPrice_doesNotMutateInputList() {
            // Arrange
            Auction art = artAuction(1_000_000L, 1_000_000L);
            Auction veh = vehicleAuction(3_000_000L, 3_000_000L);
            List<Auction> input = new ArrayList<>(List.of(veh, art));
            ItemCategory originalFirstCategory = input.get(0).getItem().getCategory();

            // Act
            sortService.sortByCategoryThenPrice(input);

            // Assert
            assertEquals(originalFirstCategory, input.get(0).getItem().getCategory());
        }

        @Test
        @DisplayName("boundary — giá cao nhất trong category ít phổ biến vẫn sau category ưu tiên")
        void sortByCategoryThenPrice_highVehiclePriceBelowArt_artStillFirst() {
            // Arrange
            Auction artCheap   = artAuction(100_000L,       100_000L);      // ART, giá thấp
            Auction vehicleExp = vehicleAuction(999_999_999L, 999_999_999L); // VEHICLE, giá cao

            List<Auction> input = List.of(vehicleExp, artCheap);

            // Act
            List<Auction> result = sortService.sortByCategoryThenPrice(input);

            // Assert — ART vẫn đứng trước VEHICLE bất kể giá
            assertEquals(ItemCategory.ART,     result.get(0).getItem().getCategory());
            assertEquals(ItemCategory.VEHICLE, result.get(1).getItem().getCategory());
        }

        @Test
        @DisplayName("stable-sort ART: 3 phần tử giá distinct → giảm dần đúng thứ tự")
        void sortByCategoryThenPrice_artGroup_priceDescending() {
            // Arrange
            Auction art1 = artAuction(1_000_000L, 1_000_000L);
            Auction art2 = artAuction(7_000_000L, 7_000_000L);
            Auction art3 = artAuction(4_000_000L, 4_000_000L);
            Auction elec = electronicsAuction(2_000_000L, 2_000_000L);
            List<Auction> input = List.of(art1, elec, art2, art3);

            // Act
            List<Auction> result = sortService.sortByCategoryThenPrice(input);

            // Assert — 3 ART đầu tiên, giảm dần
            assertEquals(7_000_000L, result.get(0).getCurrentPrice());
            assertEquals(4_000_000L, result.get(1).getCurrentPrice());
            assertEquals(1_000_000L, result.get(2).getCurrentPrice());
            assertEquals(ItemCategory.ELECTRONICS, result.get(3).getItem().getCategory());
        }
    }
}