package com.group13.auction.unit.item;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.group13.auction.model.item.*;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests cho {@link ItemFactory} và các lớp con.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Validation chung (tất cả test cũ giữ nguyên — backward-compatible)
 *   <li>imageUrls truyền qua factory và model
 *   <li>Immutability, null-safety của imageUrls
 *   <li>Constants MAX_IMAGES, MAX_IMAGE_BYTES
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemFactory")
class ItemFactoryTest {

  @Mock IRatingService ratingService;
  private NormalUser seller;

  @BeforeEach
  void setUp() {
    seller = TestFixture.normalSeller("sellerAA1");
  }

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

    @BeforeEach
    void setUp() {
      factory = new ArtFactory(ratingService);
    }

    @Test
    @DisplayName("input hợp lệ → trả về Art với đúng thuộc tính")
    void validInput_returnsArtWithCorrectAttributes() {
      allowSeller();
      Item item =
          factory.createItem(
              "Mona Lisa", "Kiệt tác", 1_000_000L, seller, "Leonardo da Vinci", 1503, "Sơn dầu");
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
      Item item = factory.createItem("Test Art", "desc", 1_000_000L, seller, "Artist", 2000, "Oil");
      assertNotNull(item.getId());
    }

    @Test
    @DisplayName("tên trống → IllegalArgumentException")
    void blankName_throwsException() {
      assertThatThrownBy(
              () -> factory.createItem("  ", "desc", 1_000_000L, seller, "Artist", 2000, "Oil"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("giá = 0 → IllegalArgumentException")
    void zeroPriceThrowsException() {
      assertThatThrownBy(() -> factory.createItem("Art", "desc", 0L, seller, "Artist", 2000, "Oil"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("seller bị từ chối → IllegalStateException")
    void deniedSeller_throwsIllegalState() {
      denySeller();
      assertThatThrownBy(
              () -> factory.createItem("Art", "desc", 1_000_000L, seller, "Artist", 2000, "Oil"))
          .isInstanceOf(IllegalStateException.class);
    }

    // ── imageUrls mới ──────────────────────────────────────────────────────

    @Test
    @DisplayName("createItem không có imageUrls → imageUrls rỗng, không null")
    void createItem_noImages_emptyList() {
      allowSeller();
      Item item =
          factory.createItem("Bức tranh", "desc", 500_000L, seller, "Nghệ sĩ", 2000, "Màu nước");
      assertThat(item.getImageUrls()).isNotNull().isEmpty();
      assertThat(item.hasImages()).isFalse();
    }

    @Test
    @DisplayName("createItem với imageUrls → imageUrls được lưu đúng")
    void createItem_withImages_stored() {
      allowSeller();
      List<String> imgs = List.of("/uploads/items/a.jpg", "/uploads/items/b.jpg");
      Item item =
          factory.createItem(
              "Van Gogh", "desc", 2_000_000L, seller, "Van Gogh", 1889, "Sơn dầu", imgs);
      assertThat(item.getImageUrls()).containsExactlyElementsOf(imgs);
      assertThat(item.hasImages()).isTrue();
    }
  }

  // =========================================================================
  // ElectronicsFactory — createItem()
  // =========================================================================

  @Nested
  @DisplayName("ElectronicsFactory.createItem()")
  class ElectronicsFactoryTests {

    private ElectronicsFactory factory;

    @BeforeEach
    void setUp() {
      factory = new ElectronicsFactory(ratingService);
    }

    @Test
    @DisplayName("input hợp lệ → Electronics đúng thuộc tính")
    void validInput_returnsElectronics() {
      allowSeller();
      Item item =
          factory.createItem("iPhone 15", "Mới 100%", 20_000_000L, seller, "Apple", 12, "Mới");
      assertInstanceOf(Electronics.class, item);
      Electronics e = (Electronics) item;
      assertEquals("Apple", e.getBrand());
      assertEquals(12, e.getWarrantyMonths());
      assertEquals("Mới", e.getCondition());
    }

    @Test
    @DisplayName("createItem không có imageUrls → imageUrls rỗng")
    void noImages_emptyList() {
      allowSeller();
      Item item = factory.createItem("Laptop", "desc", 15_000_000L, seller, "Dell", 24, "Mới");
      assertThat(item.getImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("createItem với imageUrls → lưu đúng")
    void withImages_stored() {
      allowSeller();
      List<String> imgs = List.of("/uploads/items/img1.jpg");
      Item item = factory.createItem("Monitor", "desc", 5_000_000L, seller, "LG", 12, "Mới", imgs);
      assertThat(item.getImageUrls()).hasSize(1).containsExactly("/uploads/items/img1.jpg");
    }
  }

  // =========================================================================
  // VehicleFactory — createItem()
  // =========================================================================

  @Nested
  @DisplayName("VehicleFactory.createItem()")
  class VehicleFactoryTests {

    private VehicleFactory factory;

    @BeforeEach
    void setUp() {
      factory = new VehicleFactory(ratingService);
    }

    @Test
    @DisplayName("input hợp lệ → Vehicle đúng thuộc tính")
    void validInput_returnsVehicle() {
      allowSeller();
      Item item =
          factory.createItem("Toyota Camry", "Đẹp", 500_000_000L, seller, "Toyota", 2020, 50000.0);
      assertInstanceOf(Vehicle.class, item);
      Vehicle v = (Vehicle) item;
      assertEquals("Toyota", v.getManufacturer());
      assertEquals(2020, v.getYear());
      assertEquals(50000.0, v.getMileage());
    }

    @Test
    @DisplayName("createItem với imageUrls → lưu đúng")
    void withImages_stored() {
      allowSeller();
      List<String> imgs = List.of("/uploads/items/car1.jpg", "/uploads/items/car2.jpg");
      Item item =
          factory.createItem(
              "Honda CR-V", "desc", 600_000_000L, seller, "Honda", 2022, 10000.0, imgs);
      assertThat(item.getImageUrls()).hasSize(2);
    }
  }

  // =========================================================================
  // ItemFactory.create() — facade dispatch
  // =========================================================================

  @Nested
  @DisplayName("ItemFactory.create() — facade")
  class FacadeTests {

    private ItemFactory factory;

    @BeforeEach
    void setUp() {
      factory = new ElectronicsFactory(ratingService);
    }

    @Test
    @DisplayName("ELECTRONICS dispatch → Electronics")
    void electronics_dispatch() {
      allowSeller();
      Item item =
          factory.create(
              "ELECTRONICS",
              "PC",
              "desc",
              10_000_000L,
              seller,
              Map.of("brand", "MSI", "warrantyMonths", 12, "condition", "Mới"));
      assertInstanceOf(Electronics.class, item);
    }

    @Test
    @DisplayName("ART dispatch → Art")
    void art_dispatch() {
      allowSeller();
      Item item =
          factory.create(
              "ART",
              "Tranh",
              "desc",
              1_000_000L,
              seller,
              Map.of("artist", "A", "yearCreated", 2000, "medium", "Oil"));
      assertInstanceOf(Art.class, item);
    }

    @Test
    @DisplayName("VEHICLE dispatch → Vehicle")
    void vehicle_dispatch() {
      allowSeller();
      Item item =
          factory.create(
              "VEHICLE",
              "Xe",
              "desc",
              100_000_000L,
              seller,
              Map.of("manufacturer", "Toyota", "year", 2020, "mileage", 0.0));
      assertInstanceOf(Vehicle.class, item);
    }

    @Test
    @DisplayName("facade không có imageUrls → imageUrls rỗng")
    void facade_noImages_emptyList() {
      allowSeller();
      Item item =
          factory.create(
              "ART",
              "Tranh",
              "desc",
              1_000_000L,
              seller,
              Map.of("artist", "A", "yearCreated", 2000, "medium", "Oil"));
      assertThat(item.getImageUrls()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("facade với imageUrls → imageUrls được truyền vào item")
    void facade_withImages_passedThrough() {
      allowSeller();
      List<String> imgs = List.of("/uploads/items/img_a.jpg", "/uploads/items/img_b.png");
      Item item =
          factory.create(
              "ELECTRONICS",
              "Monitor",
              "desc",
              5_000_000L,
              seller,
              Map.of("brand", "LG", "warrantyMonths", 12, "condition", "Mới"),
              imgs);
      assertThat(item.getImageUrls()).containsExactlyInAnyOrderElementsOf(imgs);
    }

    @Test
    @DisplayName("facade với null imageUrls → imageUrls rỗng (không NPE)")
    void facade_nullImages_emptyList() {
      allowSeller();
      Item item =
          factory.create(
              "ART",
              "Tranh",
              "desc",
              1_000_000L,
              seller,
              Map.of("artist", "A", "yearCreated", 2000, "medium", "Oil"),
              null);
      assertThat(item.getImageUrls()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("category không hợp lệ → IllegalArgumentException")
    void unknownCategory_throwsException() {
      assertThatThrownBy(() -> factory.create("UNKNOWN", "x", "y", 1_000_000L, seller, Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("lowercase category → được chuẩn hoá thành uppercase")
    void lowercaseCategory_normalized() {
      allowSeller();
      Item item =
          factory.create(
              "art",
              "Tranh",
              "desc",
              500_000L,
              seller,
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
    @DisplayName("Art.reconstitute không ảnh → imageUrls rỗng, không null")
    void art_reconstitute_noImages_emptyList() {
      NormalUser s = TestFixture.normalSeller("imgS1");
      Art art =
          Art.reconstitute(
              "id",
              LocalDateTime.now(),
              LocalDateTime.now(),
              "Test",
              "desc",
              1_000_000L,
              s,
              "Artist",
              2000,
              "Oil");
      assertThat(art.getImageUrls()).isNotNull().isEmpty();
      assertThat(art.hasImages()).isFalse();
    }

    @Test
    @DisplayName("Art.reconstitute có ảnh → imageUrls đúng")
    void art_reconstitute_withImages() {
      NormalUser s = TestFixture.normalSeller("imgS2");
      List<String> imgs = List.of("/uploads/items/a.jpg", "/uploads/items/b.png");
      Art art =
          Art.reconstitute(
              "id",
              LocalDateTime.now(),
              LocalDateTime.now(),
              "Test",
              "desc",
              1_000_000L,
              s,
              "Artist",
              2000,
              "Oil",
              imgs);
      assertThat(art.getImageUrls()).hasSize(2);
      assertThat(art.hasImages()).isTrue();
    }

    @Test
    @DisplayName("Electronics.reconstitute có ảnh → imageUrls đúng")
    void electronics_reconstitute_withImages() {
      NormalUser s = TestFixture.normalSeller("imgS3");
      List<String> imgs = List.of("/uploads/items/e1.jpg");
      Electronics e =
          Electronics.reconstitute(
              "id",
              LocalDateTime.now(),
              LocalDateTime.now(),
              "Test",
              "desc",
              5_000_000L,
              s,
              "Samsung",
              12,
              "Mới",
              imgs);
      assertThat(e.getImageUrls()).containsExactly("/uploads/items/e1.jpg");
    }

    @Test
    @DisplayName("Vehicle.reconstitute có ảnh → imageUrls đúng")
    void vehicle_reconstitute_withImages() {
      NormalUser s = TestFixture.normalSeller("imgS4");
      List<String> imgs =
          List.of("/uploads/items/v1.jpg", "/uploads/items/v2.jpg", "/uploads/items/v3.jpg");
      Vehicle v =
          Vehicle.reconstitute(
              "id",
              LocalDateTime.now(),
              LocalDateTime.now(),
              "Test",
              "desc",
              100_000_000L,
              s,
              "Toyota",
              2020,
              50000.0,
              imgs);
      assertThat(v.getImageUrls()).hasSize(3);
    }

    @Test
    @DisplayName("imageUrls list là immutable — không thể add/remove")
    void imageUrls_isImmutable() {
      NormalUser s = TestFixture.normalSeller("imgS5");
      List<String> mutable = new ArrayList<>(List.of("/uploads/items/x.jpg"));
      Art art =
          Art.reconstitute(
              "id",
              LocalDateTime.now(),
              LocalDateTime.now(),
              "Test",
              "desc",
              1_000_000L,
              s,
              "Artist",
              2000,
              "Oil",
              mutable);
      assertThatThrownBy(() -> art.getImageUrls().add("/uploads/items/y.jpg"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("null imageUrls → trả về list rỗng, không NPE")
    void nullImageUrls_returnsEmpty() {
      NormalUser s = TestFixture.normalSeller("imgS6");
      Electronics e =
          Electronics.reconstitute(
              "id",
              LocalDateTime.now(),
              LocalDateTime.now(),
              "Test",
              "desc",
              1_000_000L,
              s,
              "Brand",
              12,
              "Mới",
              null);
      assertThat(e.getImageUrls()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("MAX_IMAGES = 3")
    void maxImagesConstant() {
      assertThat(Item.MAX_IMAGES).isEqualTo(3);
    }

    @Test
    @DisplayName("MAX_IMAGE_BYTES = 2_000_000")
    void maxImageBytesConstant() {
      assertThat(Item.MAX_IMAGE_BYTES).isEqualTo(2_000_000L);
    }
  }
}
