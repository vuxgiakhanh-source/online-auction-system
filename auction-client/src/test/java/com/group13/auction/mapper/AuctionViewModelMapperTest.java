package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.admin.AuctionModerationViewModel;
import com.group13.auction.viewmodel.auction.AuctionCardViewModel;
import com.group13.auction.viewmodel.auction.AuctionDetailViewModel;
import com.group13.auction.viewmodel.auction.AuctionTimerViewModel;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AuctionViewModelMapper}. */
class AuctionViewModelMapperTest {

  @Test
  void toCardViewModelsShouldReturnEmptyListWhenInputIsNull() {
    assertTrue(AuctionViewModelMapper.toCardViewModels(null).isEmpty());
  }

  @Test
  void toCardViewModelsShouldMapAuctionsInOrder() {
    AuctionDTOs.AuctionDTO first = createAuction("A-1", "OPEN", "ELECTRONICS");
    AuctionDTOs.AuctionDTO second = createAuction("A-2", "RUNNING", "ART");

    List<AuctionCardViewModel> viewModels =
        AuctionViewModelMapper.toCardViewModels(List.of(first, second));

    assertEquals(2, viewModels.size());
    assertEquals("A-1", viewModels.get(0).auctionId());
    assertEquals("A-2", viewModels.get(1).auctionId());
  }

  @Test
  void toCardViewModelShouldMapAuctionCardDisplayData() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "RUNNING", "ELECTRONICS");
    auction.setExtendedEndTime(LocalDateTime.now().plusHours(3));

    AuctionCardViewModel viewModel = AuctionViewModelMapper.toCardViewModel(auction);

    assertEquals("A-1", viewModel.auctionId());
    assertEquals("Vintage Camera", viewModel.itemName());
    assertEquals("Điện tử", viewModel.categoryText());
    assertEquals("Đang đấu giá", viewModel.statusText());
    assertCurrencyTextContains(viewModel.currentPriceText(), "2.500.000");
    assertCurrencyTextContains(viewModel.startingPriceText(), "1.000.000");
    assertEquals(
        DateTimeUtil.formatDateTime(auction.getExtendedEndTime()), viewModel.endTimeText());
    assertEquals("Người bán: seller01", viewModel.sellerText());
    assertEquals("12 lượt truy cập", viewModel.viewerCountText());
    assertEquals("items/camera-1.png", viewModel.primaryImageUrl());
    assertTrue(viewModel.hasImage());
    assertTrue(viewModel.joinable());
  }

  @Test
  void toCardViewModelShouldUseFallbacksWhenAuctionOrItemIsNull() {
    AuctionCardViewModel nullAuction = AuctionViewModelMapper.toCardViewModel(null);

    assertEquals("", nullAuction.auctionId());
    assertEquals("Phiên đấu giá chưa có tên", nullAuction.itemName());
    assertEquals("Khác", nullAuction.categoryText());
    assertEquals("Không rõ", nullAuction.statusText());
    assertEquals("Người bán: --", nullAuction.sellerText());
    assertEquals("0 lượt truy cập", nullAuction.viewerCountText());
    assertFalse(nullAuction.hasImage());
    assertFalse(nullAuction.joinable());

    AuctionDTOs.AuctionDTO auction = new AuctionDTOs.AuctionDTO();
    auction.setId("A-1");
    auction.setStatus("FINISHED");

    AuctionCardViewModel missingItem = AuctionViewModelMapper.toCardViewModel(auction);

    assertEquals("A-1", missingItem.auctionId());
    assertEquals("Phiên đấu giá chưa có tên", missingItem.itemName());
    assertEquals("Khác", missingItem.categoryText());
    assertEquals("Đã kết thúc", missingItem.statusText());
    assertFalse(missingItem.joinable());
  }

  @Test
  void toDetailViewModelShouldMapAuctionDetailDisplayData() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "RUNNING", "ELECTRONICS");
    auction.setReserveMet(true);
    auction.setCurrentLeaderId("U-1");
    auction.setCurrentLeaderUsername("bidder01");
    auction.setExtendedEndTime(LocalDateTime.now().plusHours(3));

    AuctionDetailViewModel viewModel = AuctionViewModelMapper.toDetailViewModel(auction);

    assertEquals("A-1", viewModel.auctionId());
    assertEquals("Vintage Camera", viewModel.itemName());
    assertEquals("Camera film cổ.", viewModel.description());
    assertEquals("Điện tử", viewModel.categoryText());
    assertEquals("Người bán: seller01", viewModel.sellerText());
    assertEquals("RUNNING", viewModel.rawStatus());
    assertEquals("Đang đấu giá", viewModel.statusText());
    assertEquals("U-1", viewModel.currentLeaderId());
    assertEquals("bidder01", viewModel.currentLeaderUsername());
    assertCurrencyTextContains(viewModel.currentPriceText(), "2.500.000");
    assertCurrencyTextContains(viewModel.startingPriceText(), "1.000.000");
    assertEquals("Trạng thái giá sàn: Đã đạt", viewModel.reservePriceText());
    assertEquals("Người dẫn đầu: bidder01", viewModel.leaderText());
    assertEquals("12 lượt truy cập", viewModel.viewerCountText());
    assertEquals(auction.getStartTime(), viewModel.rawStartTime());
    assertEquals(auction.getExtendedEndTime(), viewModel.rawEndTime());
    assertEquals(2_500_000D, viewModel.currentPrice());
    assertTrue(viewModel.hasImages());
    assertEquals("items/camera-1.png", viewModel.primaryImageUrl());
    assertTrue(viewModel.hasProductSpecifications());
    assertTrue(viewModel.joinable());
    assertTrue(viewModel.liveBiddingAllowed());
  }

  @Test
  void toDetailViewModelShouldUseFallbacksWhenAuctionIsNull() {
    AuctionDetailViewModel viewModel = AuctionViewModelMapper.toDetailViewModel(null);

    assertEquals("", viewModel.auctionId());
    assertEquals("Phiên đấu giá chưa có tên", viewModel.itemName());
    assertEquals("Chưa có mô tả chi tiết cho sản phẩm này.", viewModel.description());
    assertEquals("Khác", viewModel.categoryText());
    assertEquals("Người bán: --", viewModel.sellerText());
    assertEquals("", viewModel.rawStatus());
    assertEquals("Không rõ", viewModel.statusText());
    assertEquals("", viewModel.currentLeaderId());
    assertEquals("", viewModel.currentLeaderUsername());
    assertEquals("Trạng thái giá sàn: --", viewModel.reservePriceText());
    assertEquals("Người dẫn đầu: chưa có", viewModel.leaderText());
    assertEquals("0 lượt truy cập", viewModel.viewerCountText());
    assertFalse(viewModel.hasImages());
    assertFalse(viewModel.hasProductSpecifications());
    assertFalse(viewModel.joinable());
    assertFalse(viewModel.liveBiddingAllowed());
  }

  @Test
  void toDetailViewModelShouldMapProductSpecificationsByCategory() {
    AuctionDTOs.AuctionDTO electronics = createAuction("A-1", "RUNNING", "ELECTRONICS");
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
  void toDetailViewModelShouldAppendUnmappedExtraFieldsForKnownCategories() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "OPEN", "ELECTRONICS");
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("brand", "Sony");
    fields.put("warrantyMonths", 12);
    fields.put("condition", "Like new");
    fields.put("serialNumber", "SN-001");
    fields.put("originCountry", "Japan");
    fields.put("empty", "   ");
    auction.getItem().setExtraFields(fields);

    AuctionDetailViewModel viewModel = AuctionViewModelMapper.toDetailViewModel(auction);

    assertTrue(
        viewModel.productSpecifications().stream()
            .anyMatch(
                specification ->
                    specification.label().equals("Serial Number")
                        && specification.value().equals("SN-001")));
    assertTrue(
        viewModel.productSpecifications().stream()
            .anyMatch(
                specification ->
                    specification.label().equals("Origin Country")
                        && specification.value().equals("Japan")));
    assertEquals(
        1,
        viewModel.productSpecifications().stream()
            .filter(specification -> specification.label().equals("Thương hiệu"))
            .count());
    assertFalse(
        viewModel.productSpecifications().stream()
            .anyMatch(specification -> specification.label().equals("Empty")));
  }

  @Test
  void toDetailViewModelShouldMapUnknownCategorySpecificationsWithReadableKeys() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "OPEN", "COLLECTIBLE");
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("serialNumber", "SN-001");
    fields.put("made_in", "Japan");
    fields.put("empty", "   ");
    auction.getItem().setExtraFields(fields);

    AuctionDetailViewModel viewModel = AuctionViewModelMapper.toDetailViewModel(auction);

    assertEquals("COLLECTIBLE", viewModel.categoryText());
    assertTrue(
        viewModel.productSpecifications().stream()
            .anyMatch(
                specification ->
                    specification.label().equals("Serial Number")
                        && specification.value().equals("SN-001")));
    assertTrue(
        viewModel.productSpecifications().stream()
            .anyMatch(
                specification ->
                    specification.label().equals("Made in")
                        && specification.value().equals("Japan")));
    assertFalse(
        viewModel.productSpecifications().stream()
            .anyMatch(specification -> specification.label().equals("Empty")));
  }

  @Test
  void toModerationViewModelsShouldReturnEmptyListWhenInputIsNull() {
    assertTrue(AuctionViewModelMapper.toModerationViewModels(null).isEmpty());
  }

  @Test
  void toModerationViewModelShouldMapAdminAuctionRow() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "OPEN", "ELECTRONICS");

    AuctionModerationViewModel viewModel = AuctionViewModelMapper.toModerationViewModel(auction);

    assertEquals("A-1", viewModel.getAuctionId());
    assertEquals("Vintage Camera", viewModel.getTitle());
    assertEquals("seller01", viewModel.getSellerName());
    assertCurrencyTextContains(viewModel.getCurrentPriceText(), "2.500.000");
    assertEquals("Sắp mở", viewModel.getStatus());
    assertEquals(DateTimeUtil.formatDateTime(auction.getStartTime()), viewModel.getStartTimeText());
    assertEquals(DateTimeUtil.formatDateTime(auction.getEndTime()), viewModel.getEndTimeText());
    assertTrue(viewModel.isCancellable());
  }

  @Test
  void toModerationViewModelShouldAllowAdminCancelOnlyForOpenOrRunningAuctions() {
    assertTrue(
        AuctionViewModelMapper.toModerationViewModel(createAuction("A-1", "OPEN", "ART"))
            .isCancellable());
    assertTrue(
        AuctionViewModelMapper.toModerationViewModel(createAuction("A-2", "RUNNING", "ART"))
            .isCancellable());
    assertFalse(
        AuctionViewModelMapper.toModerationViewModel(createAuction("A-3", "FINISHED", "ART"))
            .isCancellable());
    assertFalse(
        AuctionViewModelMapper.toModerationViewModel(createAuction("A-4", "PAID", "ART"))
            .isCancellable());
    assertFalse(
        AuctionViewModelMapper.toModerationViewModel(createAuction("A-5", "CANCELED", "ART"))
            .isCancellable());
  }

  @Test
  void toTimerViewModelShouldUseEffectiveEndTimeAndEndedState() {
    AuctionDTOs.AuctionDTO futureAuction = createAuction("A-1", "RUNNING", "ELECTRONICS");
    futureAuction.setExtendedEndTime(LocalDateTime.now().plusHours(2));

    AuctionTimerViewModel futureTimer = AuctionViewModelMapper.toTimerViewModel(futureAuction);

    assertEquals(
        DateTimeUtil.formatDateTime(futureAuction.getExtendedEndTime()), futureTimer.endTimeText());
    assertFalse(futureTimer.ended());

    AuctionDTOs.AuctionDTO endedAuction = createAuction("A-2", "RUNNING", "ELECTRONICS");
    endedAuction.setEndTime(LocalDateTime.now().minusMinutes(1));

    AuctionTimerViewModel endedTimer = AuctionViewModelMapper.toTimerViewModel(endedAuction);

    assertEquals("Đã kết thúc", endedTimer.remainingTimeText());
    assertTrue(endedTimer.ended());
  }

  @Test
  void canBidLiveShouldReturnTrueOnlyForRunningAuctionWithFutureEndTime() {
    AuctionDTOs.AuctionDTO running = createAuction("A-1", "RUNNING", "ELECTRONICS");
    running.setEndTime(LocalDateTime.now().plusMinutes(10));

    AuctionDTOs.AuctionDTO open = createAuction("A-2", "OPEN", "ELECTRONICS");
    open.setEndTime(LocalDateTime.now().plusMinutes(10));

    AuctionDTOs.AuctionDTO endedRunning = createAuction("A-3", "RUNNING", "ELECTRONICS");
    endedRunning.setEndTime(LocalDateTime.now().minusMinutes(1));

    assertTrue(AuctionViewModelMapper.canBidLive(running));
    assertFalse(AuctionViewModelMapper.canBidLive(open));
    assertFalse(AuctionViewModelMapper.canBidLive(endedRunning));
    assertFalse(AuctionViewModelMapper.canBidLive(null));
  }

  private static void assertSpecification(
      AuctionDTOs.AuctionDTO auction, String expectedLabel, String expectedValue) {
    AuctionDetailViewModel viewModel = AuctionViewModelMapper.toDetailViewModel(auction);

    assertTrue(
        viewModel.productSpecifications().stream()
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
    auction.setCurrentLeaderId("U-1");
    auction.setCurrentLeaderUsername("bidder01");
    auction.setViewerCount(12);
    auction.setReserveMet(false);
    return auction;
  }

  private static void assertCurrencyTextContains(String actual, String expectedAmount) {
    String normalized = actual.replace('\u00A0', ' ').replace('\u202F', ' ');

    assertTrue(normalized.contains(expectedAmount));
    assertTrue(normalized.contains("₫"));
  }
}
