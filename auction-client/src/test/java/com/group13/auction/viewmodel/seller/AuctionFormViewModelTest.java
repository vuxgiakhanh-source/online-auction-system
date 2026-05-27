package com.group13.auction.viewmodel.seller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AuctionFormViewModel}. */
class AuctionFormViewModelTest {

  @Test
  void constructorShouldTrimTextValuesAndUppercaseCategory() {
    AuctionFormViewModel form =
        createForm(
            "  Vintage Camera  ",
            "  Camera film cổ  ",
            "  electronics  ",
            1_000_000D,
            1_500_000D,
            validStartTime(),
            validEndTime(),
            Map.of(),
            List.of());

    assertEquals("Vintage Camera", form.itemName());
    assertEquals("Camera film cổ", form.itemDescription());
    assertEquals("ELECTRONICS", form.itemCategory());
  }

  @Test
  void constructorShouldNormalizeExtraFieldsAndImagePaths() {
    Map<String, Object> extraFields = new LinkedHashMap<>();
    extraFields.put(" brand ", "Sony");
    extraFields.put("emptyText", "   ");
    extraFields.put("nullValue", null);

    Path firstImage = Path.of("camera-1.png");
    Path secondImage = Path.of("camera-2.png");

    AuctionFormViewModel form =
        createForm(
            "Camera",
            "Description",
            "ELECTRONICS",
            1_000_000D,
            1_500_000D,
            validStartTime(),
            validEndTime(),
            extraFields,
            Arrays.asList(firstImage, null, firstImage, secondImage));

    assertEquals(Map.of("brand", "Sony"), form.itemExtraFields());
    assertEquals(List.of(firstImage, secondImage), form.imagePaths());
  }

  @Test
  void validateForCreateShouldAcceptValidForm() {
    AuctionFormViewModel form = validForm();

    assertDoesNotThrow(form::validateForCreate);
  }

  @Test
  void validateForCreateShouldRejectBlankItemName() {
    AuctionFormViewModel form =
        createForm(
            "   ",
            "Description",
            "ELECTRONICS",
            1_000_000D,
            1_500_000D,
            validStartTime(),
            validEndTime(),
            Map.of(),
            List.of());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, form::validateForCreate);

    assertEquals("Tên sản phẩm không được để trống.", exception.getMessage());
  }

  @Test
  void validateForCreateShouldRejectBlankDescription() {
    AuctionFormViewModel form =
        createForm(
            "Camera",
            "   ",
            "ELECTRONICS",
            1_000_000D,
            1_500_000D,
            validStartTime(),
            validEndTime(),
            Map.of(),
            List.of());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, form::validateForCreate);

    assertEquals("Mô tả sản phẩm không được để trống.", exception.getMessage());
  }

  @Test
  void validateForCreateShouldRejectUnsupportedCategory() {
    AuctionFormViewModel form =
        createForm(
            "Camera",
            "Description",
            "BOOK",
            1_000_000D,
            1_500_000D,
            validStartTime(),
            validEndTime(),
            Map.of(),
            List.of());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, form::validateForCreate);

    assertEquals("Loại sản phẩm chỉ hỗ trợ ELECTRONICS, ART hoặc VEHICLE.", exception.getMessage());
  }

  @Test
  void validateForCreateShouldRejectNonPositiveStartingPrice() {
    AuctionFormViewModel form =
        createForm(
            "Camera",
            "Description",
            "ELECTRONICS",
            0D,
            1_500_000D,
            validStartTime(),
            validEndTime(),
            Map.of(),
            List.of());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, form::validateForCreate);

    assertEquals("Giá khởi điểm phải lớn hơn 0.", exception.getMessage());
  }

  @Test
  void validateForCreateShouldRejectNonPositiveReservePrice() {
    AuctionFormViewModel form =
        createForm(
            "Camera",
            "Description",
            "ELECTRONICS",
            1_000_000D,
            0D,
            validStartTime(),
            validEndTime(),
            Map.of(),
            List.of());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, form::validateForCreate);

    assertEquals("Giá sàn phải lớn hơn 0.", exception.getMessage());
  }

  @Test
  void validateForCreateShouldRejectMissingStartTime() {
    AuctionFormViewModel form =
        createForm(
            "Camera",
            "Description",
            "ELECTRONICS",
            1_000_000D,
            1_500_000D,
            null,
            validEndTime(),
            Map.of(),
            List.of());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, form::validateForCreate);

    assertEquals("Thời gian bắt đầu không được để trống.", exception.getMessage());
  }

  @Test
  void validateForCreateShouldRejectMissingEndTime() {
    AuctionFormViewModel form =
        createForm(
            "Camera",
            "Description",
            "ELECTRONICS",
            1_000_000D,
            1_500_000D,
            validStartTime(),
            null,
            Map.of(),
            List.of());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, form::validateForCreate);

    assertEquals("Thời gian kết thúc không được để trống.", exception.getMessage());
  }

  @Test
  void validateForCreateShouldRejectEndTimeBeforeStartTime() {
    LocalDateTime startTime = LocalDateTime.of(2026, 5, 26, 21, 0);
    LocalDateTime endTime = LocalDateTime.of(2026, 5, 26, 20, 0);
    AuctionFormViewModel form =
        createForm(
            "Camera",
            "Description",
            "ELECTRONICS",
            1_000_000D,
            1_500_000D,
            startTime,
            endTime,
            Map.of(),
            List.of());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, form::validateForCreate);

    assertEquals("Thời gian kết thúc phải sau thời gian bắt đầu.", exception.getMessage());
  }

  @Test
  void validateForCreateShouldRejectMoreThanMaximumImages() {
    AuctionFormViewModel form =
        createForm(
            "Camera",
            "Description",
            "ELECTRONICS",
            1_000_000D,
            1_500_000D,
            validStartTime(),
            validEndTime(),
            Map.of(),
            List.of(Path.of("1.png"), Path.of("2.png"), Path.of("3.png"), Path.of("4.png")));

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, form::validateForCreate);

    assertEquals("Chỉ được chọn tối đa 3 ảnh.", exception.getMessage());
  }

  @Test
  void toCreateRequestShouldMapFormDataAndNormalizeImageUrls() {
    AuctionFormViewModel form = validForm();

    AuctionDTOs.CreateAuctionRequestDTO request =
        form.toCreateRequest(
            Arrays.asList(" /uploads/items/1.png ", null, "   ", "/uploads/items/2.png"));

    assertEquals("Vintage Camera", request.getItemName());
    assertEquals("Camera film cổ.", request.getItemDescription());
    assertEquals("ELECTRONICS", request.getItemCategory());
    assertEquals(1_000_000D, request.getStartingPrice());
    assertEquals(1_500_000D, request.getReservePrice());
    assertEquals(validStartTime(), request.getStartTime());
    assertEquals(validEndTime(), request.getEndTime());
    assertEquals(Map.of("brand", "Sony"), request.getItemExtraFields());
    assertEquals(List.of("/uploads/items/1.png", "/uploads/items/2.png"), request.getImageUrls());
  }

  private static AuctionFormViewModel validForm() {
    return createForm(
        "Vintage Camera",
        "Camera film cổ.",
        "ELECTRONICS",
        1_000_000D,
        1_500_000D,
        validStartTime(),
        validEndTime(),
        Map.of("brand", "Sony"),
        List.of(Path.of("camera.png")));
  }

  private static AuctionFormViewModel createForm(
      String itemName,
      String itemDescription,
      String itemCategory,
      double startingPrice,
      double reservePrice,
      LocalDateTime startTime,
      LocalDateTime endTime,
      Map<String, Object> itemExtraFields,
      List<Path> imagePaths) {
    return new AuctionFormViewModel(
        itemName,
        itemDescription,
        itemCategory,
        startingPrice,
        reservePrice,
        startTime,
        endTime,
        itemExtraFields,
        imagePaths);
  }

  private static LocalDateTime validStartTime() {
    return LocalDateTime.of(2026, 5, 26, 20, 0);
  }

  private static LocalDateTime validEndTime() {
    return LocalDateTime.of(2026, 5, 26, 21, 0);
  }
}
