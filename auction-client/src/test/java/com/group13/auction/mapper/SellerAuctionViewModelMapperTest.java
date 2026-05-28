package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.seller.SellerAuctionRowViewModel;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SellerAuctionViewModelMapper}. */
class SellerAuctionViewModelMapperTest {

  @Test
  void toSellerRowsShouldReturnEmptyListWhenAuctionsOrSessionIsNull() {
    UserSession session =
        UserSession.of(
            "token", "SELLER-1", "seller01", "seller01@example.com", List.of("SELLER"), "ACTIVE");

    assertTrue(SellerAuctionViewModelMapper.toSellerRows(null, session).isEmpty());
    assertTrue(
        SellerAuctionViewModelMapper.toSellerRows(
                List.of(createAuction("A-1", "OPEN", "ELECTRONICS")), null)
            .isEmpty());
  }

  @Test
  void toSellerRowsShouldFilterBySellerId() {
    UserSession session =
        UserSession.of(
            "token", "SELLER-1", "otherName", "seller01@example.com", List.of("SELLER"), "ACTIVE");

    AuctionDTOs.AuctionDTO owned = createAuction("A-1", "OPEN", "ELECTRONICS");
    owned.getItem().setSellerId("SELLER-1");
    owned.getItem().setSellerUsername("seller01");

    AuctionDTOs.AuctionDTO other = createAuction("A-2", "OPEN", "ELECTRONICS");
    other.getItem().setSellerId("SELLER-2");
    other.getItem().setSellerUsername("seller02");

    List<SellerAuctionRowViewModel> rows =
        SellerAuctionViewModelMapper.toSellerRows(List.of(owned, other), session);

    assertEquals(1, rows.size());
    assertEquals("A-1", rows.get(0).auctionId());
  }

  @Test
  void toSellerRowsShouldFilterBySellerUsernameIgnoringCaseWhenSellerIdDoesNotMatch() {
    UserSession session =
        UserSession.of(
            "token", "SELLER-X", "Seller01", "seller01@example.com", List.of("SELLER"), "ACTIVE");

    AuctionDTOs.AuctionDTO ownedByUsername = createAuction("A-1", "OPEN", "ELECTRONICS");
    ownedByUsername.getItem().setSellerId(null);
    ownedByUsername.getItem().setSellerUsername("seller01");

    AuctionDTOs.AuctionDTO other = createAuction("A-2", "OPEN", "ELECTRONICS");
    other.getItem().setSellerId(null);
    other.getItem().setSellerUsername("seller02");

    List<SellerAuctionRowViewModel> rows =
        SellerAuctionViewModelMapper.toSellerRows(List.of(ownedByUsername, other), session);

    assertEquals(1, rows.size());
    assertEquals("A-1", rows.get(0).auctionId());
  }

  @Test
  void toRowShouldMapOpenAuctionAsEditableAndCancelable() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "OPEN", "ELECTRONICS");

    SellerAuctionRowViewModel row = SellerAuctionViewModelMapper.toRow(auction);

    assertEquals("A-1", row.auctionId());
    assertEquals("Vintage Camera", row.itemName());
    assertEquals("Điện tử", row.categoryText());
    assertEquals("Sắp mở", row.statusText());
    assertCurrencyTextContains(row.currentPriceText(), "2.500.000");
    assertCurrencyTextContains(row.startingPriceText(), "1.000.000");
    assertCurrencyTextContains(row.reservePriceText(), "3.000.000");
    assertEquals(DateTimeUtil.formatDateTime(auction.getStartTime()), row.startTimeText());
    assertEquals(DateTimeUtil.formatDateTime(auction.getEndTime()), row.endTimeText());
    assertEquals(auction.getStartTime(), row.startTime());
    assertEquals(auction.getEndTime(), row.endTime());
    assertEquals("12 lượt truy cập", row.viewerCountText());
    assertEquals("items/camera-1.png", row.primaryImageUrl());
    assertTrue(row.hasImages());
    assertTrue(row.hasProductSpecifications());
    assertTrue(row.editable());
    assertTrue(row.cancelRequestAllowed());
  }

  @Test
  void toRowShouldUseExtendedEndTimeWhenAvailable() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "OPEN", "ELECTRONICS");
    LocalDateTime extendedEndTime = LocalDateTime.now().plusHours(4);
    auction.setExtendedEndTime(extendedEndTime);

    SellerAuctionRowViewModel row = SellerAuctionViewModelMapper.toRow(auction);

    assertEquals(extendedEndTime, row.endTime());
    assertEquals(DateTimeUtil.formatDateTime(extendedEndTime), row.endTimeText());
  }

  @Test
  void toRowShouldDisableEditAndCancelWhenAuctionIsNotOpen() {
    AuctionDTOs.AuctionDTO running = createAuction("A-1", "RUNNING", "ELECTRONICS");
    AuctionDTOs.AuctionDTO finished = createAuction("A-2", "FINISHED", "ELECTRONICS");

    SellerAuctionRowViewModel runningRow = SellerAuctionViewModelMapper.toRow(running);
    SellerAuctionRowViewModel finishedRow = SellerAuctionViewModelMapper.toRow(finished);

    assertFalse(runningRow.editable());
    assertFalse(runningRow.cancelRequestAllowed());
    assertFalse(finishedRow.editable());
    assertFalse(finishedRow.cancelRequestAllowed());
  }

  @Test
  void toRowShouldUseFallbacksWhenAuctionOrItemIsNull() {
    SellerAuctionRowViewModel nullRow = SellerAuctionViewModelMapper.toRow(null);

    assertEquals("", nullRow.auctionId());
    assertEquals("Phiên đấu giá chưa có tên", nullRow.itemName());
    assertEquals("Khác", nullRow.categoryText());
    assertEquals("Không rõ", nullRow.statusText());
    assertEquals("0 lượt truy cập", nullRow.viewerCountText());
    assertFalse(nullRow.hasImages());
    assertFalse(nullRow.hasProductSpecifications());
    assertFalse(nullRow.editable());
    assertFalse(nullRow.cancelRequestAllowed());

    AuctionDTOs.AuctionDTO auction = new AuctionDTOs.AuctionDTO();
    auction.setId("A-1");
    auction.setStatus("OPEN");
    auction.setViewerCount(-10);

    SellerAuctionRowViewModel missingItem = SellerAuctionViewModelMapper.toRow(auction);

    assertEquals("A-1", missingItem.auctionId());
    assertEquals("Phiên đấu giá chưa có tên", missingItem.itemName());
    assertEquals("Khác", missingItem.categoryText());
    assertEquals("0 lượt truy cập", missingItem.viewerCountText());
    assertTrue(missingItem.editable());
  }

  @Test
  void toRowShouldMapProductSpecificationsByCategory() {
    AuctionDTOs.AuctionDTO electronics = createAuction("A-1", "OPEN", "ELECTRONICS");
    assertSpecification(electronics, "Thương hiệu", "Sony");
    assertSpecification(electronics, "Bảo hành", "12 tháng");
    assertSpecification(electronics, "Tình trạng", "Like new");

    AuctionDTOs.AuctionDTO art = createAuction("A-2", "OPEN", "ART");
    art.getItem()
        .setExtraFields(Map.of("artist", "Van Gogh", "yearCreated", 1889, "medium", "Oil"));
    assertSpecification(art, "Nghệ sĩ", "Van Gogh");
    assertSpecification(art, "Năm sáng tác", "1,889");
    assertSpecification(art, "Chất liệu", "Oil");

    AuctionDTOs.AuctionDTO vehicle = createAuction("A-3", "OPEN", "VEHICLE");
    vehicle
        .getItem()
        .setExtraFields(Map.of("manufacturer", "Honda", "year", 2020, "mileage", 12500));
    assertSpecification(vehicle, "Nhà sản xuất", "Honda");
    assertSpecification(vehicle, "Năm sản xuất", "2,020");
    assertSpecification(vehicle, "Số km đã đi", "12,500 km");
  }

  @Test
  void toRowShouldAppendUnmappedExtraFieldsForKnownCategories() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "OPEN", "ELECTRONICS");
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("brand", "Sony");
    fields.put("warrantyMonths", 12);
    fields.put("condition", "Like new");
    fields.put("serialNumber", "SN-001");
    fields.put("originCountry", "Japan");
    fields.put("empty", "   ");
    auction.getItem().setExtraFields(fields);

    SellerAuctionRowViewModel row = SellerAuctionViewModelMapper.toRow(auction);

    assertTrue(
        row.productSpecifications().stream()
            .anyMatch(
                specification ->
                    specification.label().equals("Serial Number")
                        && specification.value().equals("SN-001")));
    assertTrue(
        row.productSpecifications().stream()
            .anyMatch(
                specification ->
                    specification.label().equals("Origin Country")
                        && specification.value().equals("Japan")));
    assertEquals(
        1,
        row.productSpecifications().stream()
            .filter(specification -> specification.label().equals("Thương hiệu"))
            .count());
    assertFalse(
        row.productSpecifications().stream()
            .anyMatch(specification -> specification.label().equals("Empty")));
  }

  @Test
  void toRowShouldMapUnknownCategorySpecificationsWithReadableKeys() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "OPEN", "COLLECTIBLE");
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("serialNumber", "SN-001");
    fields.put("made_in", "Japan");
    fields.put("empty", "   ");
    auction.getItem().setExtraFields(fields);

    SellerAuctionRowViewModel row = SellerAuctionViewModelMapper.toRow(auction);

    assertEquals("COLLECTIBLE", row.categoryText());
    assertTrue(
        row.productSpecifications().stream()
            .anyMatch(
                specification ->
                    specification.label().equals("Serial Number")
                        && specification.value().equals("SN-001")));
    assertTrue(
        row.productSpecifications().stream()
            .anyMatch(
                specification ->
                    specification.label().equals("Made in")
                        && specification.value().equals("Japan")));
    assertFalse(
        row.productSpecifications().stream()
            .anyMatch(specification -> specification.label().equals("Empty")));
  }

  @Test
  void toRowShouldMapKnownStatusesToDisplayText() {
    assertEquals(
        "Sắp mở",
        SellerAuctionViewModelMapper.toRow(createAuction("A-1", "OPEN", "ART")).statusText());
    assertEquals(
        "Đang đấu giá",
        SellerAuctionViewModelMapper.toRow(createAuction("A-2", "RUNNING", "ART")).statusText());
    assertEquals(
        "Đã kết thúc",
        SellerAuctionViewModelMapper.toRow(createAuction("A-3", "FINISHED", "ART")).statusText());
    assertEquals(
        "Đã thanh toán",
        SellerAuctionViewModelMapper.toRow(createAuction("A-4", "PAID", "ART")).statusText());
    assertEquals(
        "Đã hủy",
        SellerAuctionViewModelMapper.toRow(createAuction("A-5", "CANCELED", "ART")).statusText());
    assertEquals(
        "Chưa đạt giá sàn",
        SellerAuctionViewModelMapper.toRow(createAuction("A-6", "RESERVE_NOT_MET", "ART"))
            .statusText());
    assertEquals(
        "Không rõ",
        SellerAuctionViewModelMapper.toRow(createAuction("A-7", "UNKNOWN", "ART")).statusText());
  }

  private static void assertSpecification(
      AuctionDTOs.AuctionDTO auction, String expectedLabel, String expectedValue) {
    SellerAuctionRowViewModel row = SellerAuctionViewModelMapper.toRow(auction);

    assertTrue(
        row.productSpecifications().stream()
            .anyMatch(
                specification ->
                    specification.label().equals(expectedLabel)
                        && specification.value().equals(expectedValue)));
  }

  private static AuctionDTOs.AuctionDTO createAuction(
      String auctionId, String status, String category) {
    AuctionDTOs.ItemDTO item = new AuctionDTOs.ItemDTO();
    item.setId("I-" + auctionId);
    item.setName("Vintage Camera");
    item.setDescription("Camera film cổ.");
    item.setCategory(category);
    item.setStartingPrice(1_000_000D);
    item.setSellerId("SELLER-1");
    item.setSellerUsername("seller01");
    item.setImageUrls(List.of("items/camera-1.png", "   ", "items/camera-2.png"));
    item.setExtraFields(Map.of("brand", "Sony", "warrantyMonths", 12, "condition", "Like new"));

    AuctionDTOs.AuctionDTO auction = new AuctionDTOs.AuctionDTO();
    auction.setId(auctionId);
    auction.setItem(item);
    auction.setStartTime(LocalDateTime.now().minusMinutes(10));
    auction.setEndTime(LocalDateTime.now().plusHours(2));
    auction.setCurrentPrice(2_500_000D);
    auction.setReservePrice(3_000_000D);
    auction.setStatus(status);
    auction.setViewerCount(12);
    return auction;
  }

  private static void assertCurrencyTextContains(String actual, String expectedAmount) {
    String normalized = actual.replace('\u00A0', ' ').replace('\u202F', ' ');

    assertTrue(normalized.contains(expectedAmount));
    assertTrue(normalized.contains("₫"));
  }
}
