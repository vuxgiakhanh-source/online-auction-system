package com.group13.auction.unit.item;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.item.*;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link ItemFactory} và các lớp con.
 *
 * <p>Covers:
 * <ul>
 *   <li>Validation chung (tất cả test cũ giữ nguyên — backward-compatible)</li>
 *   <li>imageUrls truyền qua factory và model</li>
 *   <li>Immutability, null-safety của imageUrls</li>
 *   <li>Constants MAX_IMAGES, MAX_IMAGE_BYTES</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemFactory")
class ItemFactoryTest {

    @Mock IRatingService ratingService;
    private NormalUser seller;

    @BeforeEach
    void setUp() { seller = TestFixture.normalSeller("sellerAA1"); }

    private void allowSeller() {
        when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
    }
    private void denySeller() {
        when(ratingService.canSellerCreateAuction(seller)).thenReturn(false);
    }

    // =========================================================================
    // ArtFactory — createItem() — các test cũ không thay đổi
    // =========================================================================

    @Nested
    @DisplayName("ArtFactory.createItem()")
    class ArtFactoryTests {

        private ArtFactory factory;
        @BeforeEach void setUp() { factory = new ArtFactory(ratingService); }

        @Test
        @DisplayName("input hợp lệ → trả về Art với đúng thuộc tính")
        void validInput_returnsArtWithCorrectAttributes() {
            allowSeller();
            Item item = factory.createItem(
                    "Mona Lisa", "Kiệt tác", 1_000_000L, seller,
                    "Leonardo da Vinci", 1503, "Sơn dầu");
            assertInstanceOf(Art.class, item);
            Art art = (Art) item;
            assertEquals("Mona Lisa", art.getName());
            assertEquals(1_000_000L, art.getStartingPrice());
            assertEquals("Leonardo da Vinci", art.getArtist());
            assertEquals(1503, art.getYearCreated());
            assertEquals("Sơn dầu", art.getMedium());
            assertEquals(Item.ItemCategory.ART, art.getCategory());
        }

        @Test
        @DisplayName("item được tạo có ID không null (UUID từ Entity)")
        void createdItem_hasNonNullId() {
            allowSeller();
            Item item = factory.createItem("Test Art", "desc", 1_000_000L, seller,
                    "Artist", 2000, "Oil");
            assertNotNull(item.getId());
        }

        @Test
        @DisplayName("tên trống → IllegalArgumentException")
        void blankName_throwsException() {
            assertThatThrownBy(() -> factory.createItem(
                    "  ", "desc", 1_000_000L, seller, "Artist", 2000, "Oil"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("giá = 0 → IllegalArgumentException")
        void zeroPriceThrowsException() {
            assertThatThrownBy(() -> factory.createItem(
                    "Art", "desc", 0L, seller, "Artist", 2000, "Oil"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("seller bị từ chối → IllegalStateException")
        void deniedSeller_throwsIllegalState() {
            denySeller();
            assertThatThrownBy(() -> factory.createItem(
                    "Art", "desc", 1_000_000L, seller, "Artist", 2000, "Oil"))
                    .isInstanceOf(IllegalStateException.class);
        }

    }

    // =========================================================================
    // ElectronicsFactory — createItem()
    // =========================================================================

    @Nested
    @DisplayName("ElectronicsFactory.createItem()")
    class ElectronicsFactoryTests {

        private ElectronicsFactory factory;
        @BeforeEach void setUp() { factory = new ElectronicsFactory(ratingService); }

        @Test
        @DisplayName("input hợp lệ → Electronics đúng thuộc tính")
        void validInput_returnsElectronics() {
            allowSeller();
            Item item = factory.createItem("iPhone 15", "Mới 100%", 20_000_000L, seller,
                    "Apple", 12, "Mới");
            assertInstanceOf(Electronics.class, item);
            Electronics e = (Electronics) item;
            assertEquals("Apple", e.getBrand());
            assertEquals(12, e.getWarrantyMonths());
            assertEquals("Mới", e.getCondition());
        }

    }

    // =========================================================================
    // VehicleFactory — createItem()
    // =========================================================================

    @Nested
    @DisplayName("VehicleFactory.createItem()")
    class VehicleFactoryTests {

        private VehicleFactory factory;
        @BeforeEach void setUp() { factory = new VehicleFactory(ratingService); }

        @Test
        @DisplayName("input hợp lệ → Vehicle đúng thuộc tính")
        void validInput_returnsVehicle() {
            allowSeller();
            Item item = factory.createItem("Toyota Camry", "Đẹp", 500_000_000L, seller,
                    "Toyota", 2020, 50000.0);
            assertInstanceOf(Vehicle.class, item);
            Vehicle v = (Vehicle) item;
            assertEquals("Toyota", v.getManufacturer());
            assertEquals(2020, v.getYear());
            assertEquals(50000.0, v.getMileage());
        }

    }

    @Nested
    @DisplayName("Factory createItem — imageUrls contract")
    class FactoryImageUrlsContract {

        static Stream<Arguments> factoriesWithAndWithoutImages() {
            IRatingService rating = org.mockito.Mockito.mock(IRatingService.class);
            when(rating.canSellerCreateAuction(any())).thenReturn(true);
            List<String> imgs = List.of("/uploads/items/a.jpg", "/uploads/items/b.jpg");

            BiFunction<List<String>, NormalUser, Item> artFactory = (images, seller) -> {
                ArtFactory f = new ArtFactory(rating);
                return images == null
                        ? f.createItem("Art", "d", 500_000L, seller, "Artist", 2000, "Oil")
                        : f.createItem("Art", "d", 500_000L, seller, "Artist", 2000, "Oil", images);
            };
            BiFunction<List<String>, NormalUser, Item> electronicsFactory = (images, seller) -> {
                ElectronicsFactory f = new ElectronicsFactory(rating);
                return images == null
                        ? f.createItem("PC", "d", 5_000_000L, seller, "LG", 12, "Mới")
                        : f.createItem("PC", "d", 5_000_000L, seller, "LG", 12, "Mới", images);
            };
            BiFunction<List<String>, NormalUser, Item> vehicleFactory = (images, seller) -> {
                VehicleFactory f = new VehicleFactory(rating);
                return images == null
                        ? f.createItem("Car", "d", 100_000_000L, seller, "Honda", 2022, 10000.0)
                        : f.createItem("Car", "d", 100_000_000L, seller, "Honda", 2022, 10000.0, images);
            };

            return Stream.of(
                    Arguments.of("Art", artFactory, null, 0),
                    Arguments.of("Art", artFactory, imgs, 2),
                    Arguments.of("Electronics", electronicsFactory, null, 0),
                    Arguments.of("Electronics", electronicsFactory, List.of("/uploads/items/e.jpg"), 1),
                    Arguments.of("Vehicle", vehicleFactory, null, 0),
                    Arguments.of("Vehicle", vehicleFactory, imgs, 2));
        }

        @ParameterizedTest(name = "{0} images={2}")
        @MethodSource("factoriesWithAndWithoutImages")
        void createItem_imageUrlsStored(String label,
                BiFunction<List<String>, NormalUser, Item> factory,
                List<String> images,
                int expectedCount) {
            Item item = factory.apply(images, TestFixture.normalSeller("imgSeller" + label));
            assertThat(item.getImageUrls()).isNotNull().hasSize(expectedCount);
            assertThat(item.hasImages()).isEqualTo(expectedCount > 0);
            if (expectedCount > 0) {
                assertThat(item.getImageUrls()).containsExactlyElementsOf(images);
            }
        }
    }

    // =========================================================================
    // ItemFactory.create() — facade dispatch
    // =========================================================================

    @Nested
    @DisplayName("ItemFactory.create() — facade")
    class FacadeTests {

        private ItemFactory factory;
        @BeforeEach void setUp() { factory = new ElectronicsFactory(ratingService); }

        @Test
        @DisplayName("ELECTRONICS dispatch → Electronics")
        void electronics_dispatch() {
            allowSeller();
            Item item = factory.create("ELECTRONICS", "PC", "desc", 10_000_000L, seller,
                    Map.of("brand", "MSI", "warrantyMonths", 12, "condition", "Mới"));
            assertInstanceOf(Electronics.class, item);
        }

        @Test
        @DisplayName("ART dispatch → Art")
        void art_dispatch() {
            allowSeller();
            Item item = factory.create("ART", "Tranh", "desc", 1_000_000L, seller,
                    Map.of("artist", "A", "yearCreated", 2000, "medium", "Oil"));
            assertInstanceOf(Art.class, item);
        }

        @Test
        @DisplayName("VEHICLE dispatch → Vehicle")
        void vehicle_dispatch() {
            allowSeller();
            Item item = factory.create("VEHICLE", "Xe", "desc", 100_000_000L, seller,
                    Map.of("manufacturer", "Toyota", "year", 2020, "mileage", 0.0));
            assertInstanceOf(Vehicle.class, item);
        }

        @Test
        @DisplayName("facade không có imageUrls → imageUrls rỗng")
        void facade_noImages_emptyList() {
            allowSeller();
            Item item = factory.create("ART", "Tranh", "desc", 1_000_000L, seller,
                    Map.of("artist", "A", "yearCreated", 2000, "medium", "Oil"));
            assertThat(item.getImageUrls()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("facade với imageUrls → imageUrls được truyền vào item")
        void facade_withImages_passedThrough() {
            allowSeller();
            List<String> imgs = List.of("/uploads/items/img_a.jpg", "/uploads/items/img_b.png");
            Item item = factory.create("ELECTRONICS", "Monitor", "desc", 5_000_000L, seller,
                    Map.of("brand", "LG", "warrantyMonths", 12, "condition", "Mới"),
                    imgs);
            assertThat(item.getImageUrls()).containsExactlyInAnyOrderElementsOf(imgs);
        }

        @Test
        @DisplayName("facade với null imageUrls → imageUrls rỗng (không NPE)")
        void facade_nullImages_emptyList() {
            allowSeller();
            Item item = factory.create("ART", "Tranh", "desc", 1_000_000L, seller,
                    Map.of("artist", "A", "yearCreated", 2000, "medium", "Oil"),
                    null);
            assertThat(item.getImageUrls()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("category không hợp lệ → IllegalArgumentException")
        void unknownCategory_throwsException() {
            assertThatThrownBy(() ->
                    factory.create("UNKNOWN", "x", "y", 1_000_000L, seller, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("lowercase category → được chuẩn hoá thành uppercase")
        void lowercaseCategory_normalized() {
            allowSeller();
            Item item = factory.create("art", "Tranh", "desc", 500_000L, seller,
                    Map.of("artist", "B", "yearCreated", 1999, "medium", "Màu"));
            assertInstanceOf(Art.class, item);
        }
    }

    // =========================================================================
    // Item model — imageUrls contract
    // =========================================================================

    @Nested
    @DisplayName("Item — imageUrls model contract")
    class ItemImageContractTests {

        @Test
        @DisplayName("imageUrls list là immutable — không thể add/remove")
        void imageUrls_isImmutable() {
            NormalUser s = TestFixture.normalSeller("imgS5");
            List<String> mutable = new ArrayList<>(List.of("/uploads/items/x.jpg"));
            Art art = Art.reconstitute("id", LocalDateTime.now(), LocalDateTime.now(),
                    "Test", "desc", 1_000_000L, s, "Artist", 2000, "Oil", mutable);
            assertThatThrownBy(() -> art.getImageUrls().add("/uploads/items/y.jpg"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("null imageUrls → trả về list rỗng, không NPE")
        void nullImageUrls_returnsEmpty() {
            NormalUser s = TestFixture.normalSeller("imgS6");
            Electronics e = Electronics.reconstitute("id", LocalDateTime.now(),
                    LocalDateTime.now(), "Test", "desc", 1_000_000L, s,
                    "Brand", 12, "Mới", null);
            assertThat(e.getImageUrls()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("MAX_IMAGES và MAX_IMAGE_BYTES")
        void imageLimitsConstants() {
            assertThat(Item.MAX_IMAGES).isEqualTo(3);
            assertThat(Item.MAX_IMAGE_BYTES).isEqualTo(2_000_000L);
        }
    }
}