package com.group13.auction.item;

import com.group13.auction.TestFixture;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.item.ArtFactory;
import com.group13.auction.model.item.Electronics;
import com.group13.auction.model.item.ElectronicsFactory;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.item.ItemFactory;
import com.group13.auction.model.item.Vehicle;
import com.group13.auction.model.item.VehicleFactory;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link ItemFactory} và các lớp con
 * {@link ArtFactory}, {@link ElectronicsFactory}, {@link VehicleFactory}.
 *
 * <p>Tập trung vào:
 * <ul>
 *   <li>Validation chung ({@code validateCommon}): name, startingPrice, seller, role, rating.</li>
 *   <li>Interaction với {@link IRatingService#canSellerCreateAuction}.</li>
 *   <li>Dispatch đúng loại item qua facade {@code create()}.</li>
 *   <li>Invalid category, null input, boundary input.</li>
 * </ul>
 *
 * <p>Mock duy nhất: {@link IRatingService} — dependency ngoài boundary model.
 * Dùng object thật {@link NormalUser} từ {@link TestFixture}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemFactory")
class ItemFactoryTest {

    @Mock
    private IRatingService ratingService;

    private NormalUser seller;

    @BeforeEach
    void setUp() {
        // Seller hợp lệ: có role SELLER, rating = 3.0 (ACTIVE)
        seller = TestFixture.normalSeller("sellerAA1");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Stub ratingService cho phép seller tạo auction. */
    private void allowSeller() {
        when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
    }

    /** Stub ratingService từ chối seller tạo auction. */
    private void denySeller() {
        when(ratingService.canSellerCreateAuction(seller)).thenReturn(false);
    }

    // =========================================================================
    // ArtFactory — createItem()
    // =========================================================================

    @Nested
    @DisplayName("ArtFactory.createItem()")
    class ArtFactoryTest {

        private ArtFactory factory;

        @BeforeEach
        void setUp() {
            factory = new ArtFactory(ratingService);
        }

        // --- Happy path ---

        @Test
        @DisplayName("input hợp lệ → trả về Art với đúng thuộc tính")
        void validInput_returnsArtWithCorrectAttributes() {
            // Arrange
            allowSeller();

            // Act
            Item item = factory.createItem(
                    "Mona Lisa", "Kiệt tác", 1_000_000L, seller,
                    "Leonardo da Vinci", 1503, "Sơn dầu"
            );

            // Assert
            assertInstanceOf(Art.class, item);
            Art art = (Art) item;
            assertEquals("Mona Lisa", art.getName());
            assertEquals("Kiệt tác", art.getDescription());
            assertEquals(1_000_000L, art.getStartingPrice());
            assertEquals(seller, art.getSeller());
            assertEquals("Leonardo da Vinci", art.getArtist());
            assertEquals(1503, art.getYearCreated());
            assertEquals("Sơn dầu", art.getMedium());
            assertEquals(Item.ItemCategory.ART, art.getCategory());
        }

        @Test
        @DisplayName("item được tạo có ID không null (UUID từ Entity)")
        void createdItem_hasNonNullId() {
            // Arrange
            allowSeller();

            // Act
            Item item = factory.createItem("Tác phẩm", "Mô tả", 500_000L, seller,
                    "Artist", 2000, "Màu nước");

            // Assert
            assertNotNull(item.getId());
            assertFalse(item.getId().isBlank());
        }

        @Test
        @DisplayName("startingPrice đúng biên (= 1) → tạo thành công")
        void startingPriceAtLowerBoundary_succeeds() {
            // Arrange
            allowSeller();

            // Act & Assert — 1 là giá trị nhỏ nhất hợp lệ (> 0)
            assertDoesNotThrow(() ->
                    factory.createItem("Art", "Desc", 1L, seller, "Artist", 2000, "Oil")
            );
        }

        // --- Verify interaction với IRatingService ---

        @Test
        @DisplayName("verify: canSellerCreateAuction được gọi đúng 1 lần với đúng seller")
        void verifyInteraction_canSellerCreateAuctionCalledOnceWithSeller() {
            // Arrange
            allowSeller();

            // Act
            factory.createItem("Art", "Desc", 100_000L, seller, "Artist", 2000, "Oil");

            // Assert
            verify(ratingService, times(1)).canSellerCreateAuction(seller);
        }

        @Test
        @DisplayName("verify: các method IRatingService khác không được gọi khi tạo item")
        void verifyInteraction_noOtherRatingServiceMethodCalled() {
            // Arrange
            allowSeller();

            // Act
            factory.createItem("Art", "Desc", 100_000L, seller, "Artist", 2000, "Oil");

            // Assert — chỉ canSellerCreateAuction được gọi, không gọi gì khác
            verify(ratingService, times(1)).canSellerCreateAuction(seller);
            verifyNoMoreInteractions(ratingService);
        }

        @Test
        @DisplayName("verify: ratingService không được gọi khi validation fail trước đó (name null)")
        void verifyInteraction_ratingServiceNotCalledWhenNameNull() {
            // Arrange — không cần stub vì code sẽ throw trước khi gọi ratingService

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem(null, "Desc", 100_000L, seller, "Artist", 2000, "Oil")
            );

            // Assert — ratingService không được chạm đến
            verify(ratingService, never()).canSellerCreateAuction(any());
        }

        @Test
        @DisplayName("verify: ratingService không được gọi khi seller null")
        void verifyInteraction_ratingServiceNotCalledWhenSellerNull() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Art", "Desc", 100_000L, null, "Artist", 2000, "Oil")
            );

            verify(ratingService, never()).canSellerCreateAuction(any());
        }

        // --- Invalid: name ---

        @Test
        @DisplayName("name null → IllegalArgumentException")
        void nullName_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem(null, "Desc", 100_000L, seller, "Artist", 2000, "Oil")
            );
        }

        @Test
        @DisplayName("name trống → IllegalArgumentException")
        void emptyName_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("", "Desc", 100_000L, seller, "Artist", 2000, "Oil")
            );
        }

        @Test
        @DisplayName("name chỉ khoảng trắng → IllegalArgumentException")
        void blankName_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("   ", "Desc", 100_000L, seller, "Artist", 2000, "Oil")
            );
        }

        // --- Invalid: startingPrice ---

        @Test
        @DisplayName("startingPrice = 0 → IllegalArgumentException")
        void zeroPricePrice_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Art", "Desc", 0L, seller, "Artist", 2000, "Oil")
            );
        }

        @Test
        @DisplayName("startingPrice âm → IllegalArgumentException")
        void negativePrice_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Art", "Desc", -1L, seller, "Artist", 2000, "Oil")
            );
        }

        @Test
        @DisplayName("startingPrice Long.MIN_VALUE → IllegalArgumentException")
        void minLongPrice_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Art", "Desc", Long.MIN_VALUE, seller, "Artist", 2000, "Oil")
            );
        }

        // --- Invalid: seller null ---

        @Test
        @DisplayName("seller null → IllegalArgumentException")
        void nullSeller_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Art", "Desc", 100_000L, null, "Artist", 2000, "Oil")
            );
        }

        // --- Invalid: seller không có role SELLER ---

        @Test
        @DisplayName("seller chỉ có role BIDDER → IllegalArgumentException")
        void sellerWithoutSellerRole_throwsIllegalArgumentException() {
            // Arrange — normalBidder chỉ có role BIDDER
            NormalUser bidderOnly = TestFixture.normalBidder("bidderBB2");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Art", "Desc", 100_000L, bidderOnly, "Artist", 2000, "Oil")
            );
        }

        @Test
        @DisplayName("seller bị BANNED (không có role SELLER trong fixture) → IllegalArgumentException vì role")
        void bannedSellerWithoutSellerRole_throwsIllegalArgumentException() {
            // Arrange — bannedBidder chỉ có role BIDDER
            NormalUser banned = TestFixture.bannedBidder("bidderCC3");

            // Act & Assert — fail vì role check trước rating check
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Art", "Desc", 100_000L, banned, "Artist", 2000, "Oil")
            );
        }

        // --- Invalid: rating thấp (ratingService từ chối) ---

        @Test
        @DisplayName("ratingService từ chối → IllegalStateException")
        void ratingServiceDenies_throwsIllegalStateException() {
            // Arrange
            denySeller();

            // Act & Assert
            assertThrows(IllegalStateException.class, () ->
                    factory.createItem("Art", "Desc", 100_000L, seller, "Artist", 2000, "Oil")
            );
        }

        @Test
        @DisplayName("ratingService từ chối → message exception có nội dung bị khóa/uy tín thấp")
        void ratingServiceDenies_exceptionMessageDescribesReason() {
            // Arrange
            denySeller();

            // Act
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    factory.createItem("Art", "Desc", 100_000L, seller, "Artist", 2000, "Oil")
            );

            // Assert — message phải mô tả nguyên nhân
            assertNotNull(ex.getMessage());
            assertFalse(ex.getMessage().isBlank());
        }

        // --- Boundary: description null (không validate — được phép) ---

        @Test
        @DisplayName("description null → không throw (factory không validate description)")
        void nullDescription_doesNotThrow() {
            // Arrange
            allowSeller();

            // Act & Assert — description không nằm trong validateCommon
            assertDoesNotThrow(() ->
                    factory.createItem("Art", null, 100_000L, seller, "Artist", 2000, "Oil")
            );
        }
    }

    // =========================================================================
    // ElectronicsFactory — createItem()
    // =========================================================================

    @Nested
    @DisplayName("ElectronicsFactory.createItem()")
    class ElectronicsFactoryTest {

        private ElectronicsFactory factory;

        @BeforeEach
        void setUp() {
            factory = new ElectronicsFactory(ratingService);
        }

        // --- Happy path ---

        @Test
        @DisplayName("input hợp lệ → trả về Electronics với đúng thuộc tính")
        void validInput_returnsElectronicsWithCorrectAttributes() {
            // Arrange
            allowSeller();

            // Act
            Item item = factory.createItem(
                    "iPhone 15", "Điện thoại Apple", 20_000_000L, seller,
                    "Apple", 12, "Mới 100%"
            );

            // Assert
            assertInstanceOf(Electronics.class, item);
            Electronics e = (Electronics) item;
            assertEquals("iPhone 15", e.getName());
            assertEquals(20_000_000L, e.getStartingPrice());
            assertEquals("Apple", e.getBrand());
            assertEquals(12, e.getWarrantyMonths());
            assertEquals("Mới 100%", e.getCondition());
            assertEquals(Item.ItemCategory.ELECTRONICS, e.getCategory());
        }

        @Test
        @DisplayName("warrantyMonths = 0 → tạo thành công (Electronics không validate warranty)")
        void zeroWarrantyMonths_succeeds() {
            // Arrange
            allowSeller();

            // Act & Assert
            assertDoesNotThrow(() ->
                    factory.createItem("Phone", "Desc", 1_000_000L, seller, "Samsung", 0, "Used")
            );
        }

        // --- Verify interaction ---

        @Test
        @DisplayName("verify: canSellerCreateAuction được gọi đúng 1 lần")
        void verifyInteraction_calledExactlyOnce() {
            // Arrange
            allowSeller();

            // Act
            factory.createItem("Phone", "Desc", 1_000_000L, seller, "Samsung", 12, "New");

            // Assert
            verify(ratingService, times(1)).canSellerCreateAuction(seller);
            verifyNoMoreInteractions(ratingService);
        }

        // --- Validation chung: giống ArtFactory, chỉ test các trường hợp chưa cover ---

        @Test
        @DisplayName("name null → IllegalArgumentException")
        void nullName_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem(null, "Desc", 1_000_000L, seller, "Brand", 12, "New")
            );
        }

        @Test
        @DisplayName("startingPrice = 0 → IllegalArgumentException")
        void zeroPrice_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Phone", "Desc", 0L, seller, "Brand", 12, "New")
            );
        }

        @Test
        @DisplayName("seller null → IllegalArgumentException")
        void nullSeller_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Phone", "Desc", 1_000_000L, null, "Brand", 12, "New")
            );
        }

        @Test
        @DisplayName("seller không có role SELLER → IllegalArgumentException")
        void sellerWithoutSellerRole_throwsIllegalArgumentException() {
            // Arrange
            NormalUser bidderOnly = TestFixture.normalBidder("bidderDD4");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Phone", "Desc", 1_000_000L, bidderOnly, "Brand", 12, "New")
            );
        }

        @Test
        @DisplayName("ratingService từ chối → IllegalStateException")
        void ratingServiceDenies_throwsIllegalStateException() {
            // Arrange
            denySeller();

            // Act & Assert
            assertThrows(IllegalStateException.class, () ->
                    factory.createItem("Phone", "Desc", 1_000_000L, seller, "Brand", 12, "New")
            );
        }
    }

    // =========================================================================
    // VehicleFactory — createItem()
    // =========================================================================

    @Nested
    @DisplayName("VehicleFactory.createItem()")
    class VehicleFactoryTest {

        private VehicleFactory factory;

        @BeforeEach
        void setUp() {
            factory = new VehicleFactory(ratingService);
        }

        // --- Happy path ---

        @Test
        @DisplayName("input hợp lệ → trả về Vehicle với đúng thuộc tính")
        void validInput_returnsVehicleWithCorrectAttributes() {
            // Arrange
            allowSeller();

            // Act
            Item item = factory.createItem(
                    "Toyota Camry", "Xe sedan", 500_000_000L, seller,
                    "Toyota", 2020, 45000.0
            );

            // Assert
            assertInstanceOf(Vehicle.class, item);
            Vehicle v = (Vehicle) item;
            assertEquals("Toyota Camry", v.getName());
            assertEquals(500_000_000L, v.getStartingPrice());
            assertEquals("Toyota", v.getManufacturer());
            assertEquals(2020, v.getYear());
            assertEquals(45000.0, v.getMileage(), 1e-9);
            assertEquals(Item.ItemCategory.VEHICLE, v.getCategory());
        }

        @Test
        @DisplayName("mileage = 0.0 → tạo thành công (xe mới không có km)")
        void zeroMileage_succeeds() {
            // Arrange
            allowSeller();

            // Act & Assert
            assertDoesNotThrow(() ->
                    factory.createItem("New Car", "Desc", 1_000_000L, seller, "Honda", 2024, 0.0)
            );
        }

        // --- Verify interaction ---

        @Test
        @DisplayName("verify: canSellerCreateAuction được gọi đúng 1 lần")
        void verifyInteraction_calledExactlyOnce() {
            // Arrange
            allowSeller();

            // Act
            factory.createItem("Car", "Desc", 1_000_000L, seller, "Honda", 2020, 10000.0);

            // Assert
            verify(ratingService, times(1)).canSellerCreateAuction(seller);
            verifyNoMoreInteractions(ratingService);
        }

        // --- Validation chung ---

        @Test
        @DisplayName("name blank → IllegalArgumentException")
        void blankName_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("  ", "Desc", 1_000_000L, seller, "Honda", 2020, 0.0)
            );
        }

        @Test
        @DisplayName("startingPrice âm → IllegalArgumentException")
        void negativePrice_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Car", "Desc", -500_000L, seller, "Honda", 2020, 0.0)
            );
        }

        @Test
        @DisplayName("seller không có role SELLER → IllegalArgumentException")
        void sellerWithoutSellerRole_throwsIllegalArgumentException() {
            // Arrange
            NormalUser bidderOnly = TestFixture.normalBidder("bidderEE5");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Car", "Desc", 1_000_000L, bidderOnly, "Honda", 2020, 0.0)
            );
        }

        @Test
        @DisplayName("ratingService từ chối → IllegalStateException")
        void ratingServiceDenies_throwsIllegalStateException() {
            // Arrange
            denySeller();

            // Act & Assert
            assertThrows(IllegalStateException.class, () ->
                    factory.createItem("Car", "Desc", 1_000_000L, seller, "Honda", 2020, 0.0)
            );
        }
    }

    // =========================================================================
    // ItemFactory.create() — facade dispatch theo category
    // =========================================================================

    @Nested
    @DisplayName("ItemFactory.create() — facade dispatch")
    class FacadeCreateTest {

        private ItemFactory factory;

        @BeforeEach
        void setUp() {
            // Dùng ArtFactory làm concrete factory để test facade (logic facade ở ItemFactory)
            factory = new ArtFactory(ratingService);
        }

        // --- Happy path: dispatch đúng loại ---

        @Test
        @DisplayName("category ART → trả về instance Art")
        void categoryArt_returnsArtInstance() {
            // Arrange
            allowSeller();
            Map<String, Object> fields = Map.of("artist", "Picasso", "yearCreated", 1950, "medium", "Oil");

            // Act
            Item item = factory.create("ART", "Guernica", "Mô tả", 1_000_000L, seller, fields);

            // Assert
            assertInstanceOf(Art.class, item);
        }

        @Test
        @DisplayName("category ELECTRONICS → trả về instance Electronics")
        void categoryElectronics_returnsElectronicsInstance() {
            // Arrange
            allowSeller();
            Map<String, Object> fields = Map.of("brand", "Sony", "warrantyMonths", 24, "condition", "New");

            // Act
            Item item = factory.create("ELECTRONICS", "TV 4K", "Mô tả", 5_000_000L, seller, fields);

            // Assert
            assertInstanceOf(Electronics.class, item);
        }

        @Test
        @DisplayName("category VEHICLE → trả về instance Vehicle")
        void categoryVehicle_returnsVehicleInstance() {
            // Arrange
            allowSeller();
            Map<String, Object> fields = Map.of("manufacturer", "BMW", "year", 2022, "mileage", 30000.0);

            // Act
            Item item = factory.create("VEHICLE", "BMW 3 Series", "Mô tả", 800_000_000L, seller, fields);

            // Assert
            assertInstanceOf(Vehicle.class, item);
        }

        // --- Case-insensitive và trim ---

        @Test
        @DisplayName("category 'art' lowercase → dispatch đúng, trả về Art")
        void categoryLowercase_dispatchedCorrectly() {
            // Arrange
            allowSeller();
            Map<String, Object> fields = Map.of("artist", "Artist", "yearCreated", 2000, "medium", "Oil");

            // Act
            Item item = factory.create("art", "Name", "Desc", 100_000L, seller, fields);

            // Assert
            assertInstanceOf(Art.class, item);
        }

        @Test
        @DisplayName("category '  ART  ' với khoảng trắng → trim đúng, dispatch thành công")
        void categoryWithWhitespace_trimmedAndDispatchedCorrectly() {
            // Arrange
            allowSeller();
            Map<String, Object> fields = Map.of("artist", "Artist", "yearCreated", 2000, "medium", "Oil");

            // Act
            Item item = factory.create("  ART  ", "Name", "Desc", 100_000L, seller, fields);

            // Assert
            assertInstanceOf(Art.class, item);
        }

        @Test
        @DisplayName("category 'Electronics' mixed case → dispatch đúng")
        void categoryMixedCase_dispatchedCorrectly() {
            // Arrange
            allowSeller();
            Map<String, Object> fields = Map.of("brand", "LG", "warrantyMonths", 12, "condition", "New");

            // Act
            Item item = factory.create("Electronics", "TV", "Desc", 3_000_000L, seller, fields);

            // Assert
            assertInstanceOf(Electronics.class, item);
        }

        // --- Invalid category ---

        @Test
        @DisplayName("category không hỗ trợ → IllegalArgumentException")
        void unsupportedCategory_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.create("JEWELRY", "Ring", "Desc", 100_000L, seller, Map.of())
            );
        }

        @Test
        @DisplayName("category null → fallback 'OTHER' → IllegalArgumentException")
        void nullCategory_fallsBackToOther_throwsIllegalArgumentException() {
            // Arrange — null → cat = "OTHER" → default case → throw
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.create(null, "Name", "Desc", 100_000L, seller, Map.of())
            );
        }

        @Test
        @DisplayName("category rỗng → trim → 'OTHER' → IllegalArgumentException")
        void emptyCategory_throwsIllegalArgumentException() {
            // Arrange — "" trim upperCase = "" → default case → throw
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.create("", "Name", "Desc", 100_000L, seller, Map.of())
            );
        }

        @Test
        @DisplayName("category 'OTHER' tường minh → IllegalArgumentException")
        void explicitOtherCategory_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.create("OTHER", "Name", "Desc", 100_000L, seller, Map.of())
            );
        }

        @Test
        @DisplayName("category không hỗ trợ → exception message chứa tên category gốc")
        void unsupportedCategory_exceptionMessageContainsCategory() {
            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    factory.create("FURNITURE", "Chair", "Desc", 100_000L, seller, Map.of())
            );

            // Assert — message phải đề cập category không hợp lệ
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("FURNITURE"),
                    "Message phải chứa tên category không hợp lệ: " + ex.getMessage());
        }

        // --- extraFields null ---

        @Test
        @DisplayName("extraFields null → không throw NullPointerException, dùng Map.of() rỗng thay thế")
        void nullExtraFields_usesEmptyMapFallback_noNullPointerException() {
            // Arrange
            allowSeller();
            // Khi extraFields null, factory dùng Map.of() rỗng → các field lấy về null/0

            // Act — ART với args null từ Map.of() rỗng:
            //   artist = null, yearCreated = 0, medium = null
            // Art.create() không validate artist/medium → không throw
            assertDoesNotThrow(() ->
                    factory.create("ART", "Name", "Desc", 100_000L, seller, null)
            );
        }

        // --- Validation chung vẫn áp dụng qua facade ---

        @Test
        @DisplayName("facade: name null → IllegalArgumentException trước khi dispatch")
        void facadeNullName_throwsIllegalArgumentException() {
            // Arrange
            Map<String, Object> fields = Map.of("artist", "Artist", "yearCreated", 2000, "medium", "Oil");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.create("ART", null, "Desc", 100_000L, seller, fields)
            );
        }

        @Test
        @DisplayName("facade: startingPrice = 0 → IllegalArgumentException")
        void facadeZeroPrice_throwsIllegalArgumentException() {
            // Arrange
            Map<String, Object> fields = Map.of("artist", "Artist", "yearCreated", 2000, "medium", "Oil");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.create("ART", "Name", "Desc", 0L, seller, fields)
            );
        }

        @Test
        @DisplayName("facade: seller null → IllegalArgumentException")
        void facadeNullSeller_throwsIllegalArgumentException() {
            // Arrange
            Map<String, Object> fields = Map.of("artist", "Artist", "yearCreated", 2000, "medium", "Oil");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.create("ART", "Name", "Desc", 100_000L, null, fields)
            );
        }

        @Test
        @DisplayName("facade: ratingService từ chối → IllegalStateException")
        void facadeRatingServiceDenies_throwsIllegalStateException() {
            // Arrange
            denySeller();
            Map<String, Object> fields = Map.of("artist", "Artist", "yearCreated", 2000, "medium", "Oil");

            // Act & Assert
            assertThrows(IllegalStateException.class, () ->
                    factory.create("ART", "Name", "Desc", 100_000L, seller, fields)
            );
        }

        // --- canSellerCreateAuction chỉ được gọi đúng lần qua facade ---

        @Test
        @DisplayName("verify: facade gọi canSellerCreateAuction đúng 1 lần dù dispatch qua factory con")
        void facade_verifyCanSellerCreateAuctionCalledExactlyOnce() {
            // Arrange
            allowSeller();
            Map<String, Object> fields = Map.of("artist", "Artist", "yearCreated", 2000, "medium", "Oil");

            // Act
            factory.create("ART", "Name", "Desc", 100_000L, seller, fields);

            // Assert
            verify(ratingService, times(1)).canSellerCreateAuction(seller);
            verifyNoMoreInteractions(ratingService);
        }
    }

    // =========================================================================
    // validateCommon — order của validation (fail-fast thứ tự đúng)
    // =========================================================================

    @Nested
    @DisplayName("validateCommon() — thứ tự fail-fast")
    class ValidationOrderTest {

        private ArtFactory factory;

        @BeforeEach
        void setUp() {
            factory = new ArtFactory(ratingService);
        }

        @Test
        @DisplayName("name null + seller null → fail vì name trước (name check đầu tiên)")
        void bothNameAndSellerNull_failsOnNameFirst() {
            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem(null, "Desc", 100_000L, null, "Artist", 2000, "Oil")
            );

            // Assert — message phải là lỗi tên, không phải lỗi seller
            assertTrue(ex.getMessage().toLowerCase().contains("tên") ||
                            ex.getMessage().toLowerCase().contains("name") ||
                            ex.getMessage().toLowerCase().contains("trống"),
                    "Exception phải là lỗi tên sản phẩm: " + ex.getMessage());

            // ratingService không được gọi
            verify(ratingService, never()).canSellerCreateAuction(any());
        }

        @Test
        @DisplayName("price = 0 + seller null → fail vì price trước (price check thứ hai)")
        void bothZeroPriceAndSellerNull_failsOnPriceFirst() {
            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Art", "Desc", 0L, null, "Artist", 2000, "Oil")
            );

            // Assert — message là lỗi giá, không phải lỗi seller
            assertTrue(ex.getMessage().toLowerCase().contains("giá") ||
                            ex.getMessage().toLowerCase().contains("price") ||
                            ex.getMessage().toLowerCase().contains("lớn hơn"),
                    "Exception phải là lỗi giá: " + ex.getMessage());

            verify(ratingService, never()).canSellerCreateAuction(any());
        }

        @Test
        @DisplayName("seller không có role SELLER → fail vì role trước khi gọi ratingService")
        void sellerWithoutRole_failsOnRoleBeforeRatingService() {
            // Arrange — bidderOnly không có role SELLER
            NormalUser bidderOnly = TestFixture.normalBidder("bidderFF6");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    factory.createItem("Art", "Desc", 100_000L, bidderOnly, "Artist", 2000, "Oil")
            );

            // Assert — ratingService không được chạm đến vì fail trước
            verify(ratingService, never()).canSellerCreateAuction(any());
        }

        @Test
        @DisplayName("ratingService từ chối → throw IllegalStateException (sau tất cả validation khác pass)")
        void ratingDenied_throwsIllegalStateException_afterOtherValidationPassed() {
            // Arrange — mọi validation khác đều pass, chỉ ratingService từ chối
            denySeller();

            // Act & Assert
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    factory.createItem("Art", "Desc", 100_000L, seller, "Artist", 2000, "Oil")
            );

            // Assert — đây là lỗi cuối cùng, sau tất cả validation trước đó pass
            assertNotNull(ex.getMessage());

            // ratingService đã được gọi đúng 1 lần
            verify(ratingService, times(1)).canSellerCreateAuction(seller);
        }
    }
}