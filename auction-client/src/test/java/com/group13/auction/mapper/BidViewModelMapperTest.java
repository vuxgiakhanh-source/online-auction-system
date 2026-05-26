package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.viewmodel.auction.BidHistoryPointViewModel;
import com.group13.auction.viewmodel.auction.LiveBidViewModel;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BidViewModelMapper}. */
class BidViewModelMapperTest {

  @Test
  void toLiveBidViewModelShouldReturnEmptyStateWhenBidUpdateIsNull() {
    LiveBidViewModel viewModel = BidViewModelMapper.toLiveBidViewModel((BidDTOs.BidUpdateDTO) null);

    assertEquals("", viewModel.auctionId());
    assertEquals("--", viewModel.currentPriceText());
    assertEquals("Người dẫn đầu: --", viewModel.leaderText());
    assertEquals("Trạng thái giá sàn: --", viewModel.reserveText());
    assertEquals("--", viewModel.timestampText());
    assertEquals("--", viewModel.endTimeText());
    assertEquals(0L, viewModel.currentPrice());
    assertFalse(viewModel.reserveMet());
  }

  @Test
  void toLiveBidViewModelShouldMapBidUpdateWithLeaderAndReserveMet() {
    BidDTOs.BidUpdateDTO update = new BidDTOs.BidUpdateDTO();
    update.setAuctionId("A-1");
    update.setNewCurrentPrice(2_500_000L);
    update.setLeaderUsername("bidder01");
    update.setReserveMet(true);
    update.setTimestamp(LocalDateTime.of(2026, 5, 26, 20, 30));
    update.setNewEndTime(LocalDateTime.of(2026, 5, 26, 21, 0));

    LiveBidViewModel viewModel = BidViewModelMapper.toLiveBidViewModel(update);

    assertEquals("A-1", viewModel.auctionId());
    assertCurrencyTextContains(viewModel.currentPriceText(), "2.500.000");
    assertEquals("Người dẫn đầu: bidder01", viewModel.leaderText());
    assertEquals("Trạng thái giá sàn: Đã đạt", viewModel.reserveText());
    assertEquals("26/05/2026 20:30", viewModel.timestampText());
    assertEquals("26/05/2026 21:00", viewModel.endTimeText());
    assertEquals(2_500_000L, viewModel.currentPrice());
    assertTrue(viewModel.reserveMet());
  }

  @Test
  void toLiveBidViewModelShouldShowNoLeaderWhenLeaderUsernameIsBlank() {
    BidDTOs.BidUpdateDTO update = new BidDTOs.BidUpdateDTO();
    update.setLeaderUsername("   ");
    update.setReserveMet(false);

    LiveBidViewModel viewModel = BidViewModelMapper.toLiveBidViewModel(update);

    assertEquals("Người dẫn đầu: chưa có", viewModel.leaderText());
    assertEquals("Trạng thái giá sàn: Chưa đạt", viewModel.reserveText());
    assertFalse(viewModel.reserveMet());
  }

  @Test
  void toLiveBidViewModelShouldReturnEmptyStateWhenBidResultIsNull() {
    LiveBidViewModel viewModel = BidViewModelMapper.toLiveBidViewModel((BidDTOs.BidResultDTO) null);

    assertEquals("", viewModel.auctionId());
    assertEquals("--", viewModel.currentPriceText());
    assertEquals("Người dẫn đầu: --", viewModel.leaderText());
    assertEquals("Trạng thái giá sàn: --", viewModel.reserveText());
    assertEquals("--", viewModel.timestampText());
    assertEquals("--", viewModel.endTimeText());
    assertEquals(0L, viewModel.currentPrice());
    assertFalse(viewModel.reserveMet());
  }

  @Test
  void toLiveBidViewModelShouldMapBidResult() {
    BidDTOs.BidResultDTO result = new BidDTOs.BidResultDTO();
    result.setAuctionId("A-1");
    result.setAmount(2_500_000L);
    result.setCurrentPrice(2_500_000L);
    result.setReserveMet(true);
    result.setTimestamp(LocalDateTime.of(2026, 5, 26, 20, 30));

    LiveBidViewModel viewModel = BidViewModelMapper.toLiveBidViewModel(result);

    assertEquals("A-1", viewModel.auctionId());
    assertCurrencyTextContains(viewModel.currentPriceText(), "2.500.000");
    assertTrue(viewModel.leaderText().startsWith("Bạn vừa đặt giá: "));
    assertCurrencyTextContains(viewModel.leaderText(), "2.500.000");
    assertEquals("Trạng thái giá sàn: Đã đạt", viewModel.reserveText());
    assertEquals("26/05/2026 20:30", viewModel.timestampText());
    assertEquals("--", viewModel.endTimeText());
    assertEquals(2_500_000L, viewModel.currentPrice());
    assertTrue(viewModel.reserveMet());
  }

  @Test
  void toHistoryPointViewModelsShouldReturnEmptyListWhenHistoryIsNullOrPointsAreNull() {
    assertTrue(BidViewModelMapper.toHistoryPointViewModels(null).isEmpty());

    BidDTOs.BidHistoryResponseDTO history = new BidDTOs.BidHistoryResponseDTO();
    history.setPoints(null);

    assertTrue(BidViewModelMapper.toHistoryPointViewModels(history).isEmpty());
  }

  @Test
  void toHistoryPointViewModelsShouldMapPointsInOrder() {
    BidDTOs.BidChartPointDTO first = createPoint("A-1", 2_000_000L, "bidder01", false);
    BidDTOs.BidChartPointDTO second = createPoint("A-1", 2_500_000L, "bidder02", true);

    BidDTOs.BidHistoryResponseDTO history = new BidDTOs.BidHistoryResponseDTO();
    history.setPoints(List.of(first, second));

    List<BidHistoryPointViewModel> viewModels =
        BidViewModelMapper.toHistoryPointViewModels(history);

    assertEquals(2, viewModels.size());
    assertEquals("bidder01", viewModels.get(0).bidderUsername());
    assertFalse(viewModels.get(0).autoBid());
    assertEquals("bidder02", viewModels.get(1).bidderUsername());
    assertTrue(viewModels.get(1).autoBid());
  }

  @Test
  void toHistoryPointViewModelShouldReturnEmptyPointWhenPointIsNull() {
    BidHistoryPointViewModel viewModel = BidViewModelMapper.toHistoryPointViewModel(null);

    assertEquals("", viewModel.auctionId());
    assertEquals(0L, viewModel.price());
    assertEquals("--", viewModel.priceText());
    assertEquals("--", viewModel.bidderUsername());
    assertEquals("--", viewModel.timestampText());
    assertFalse(viewModel.autoBid());
  }

  @Test
  void toHistoryPointViewModelShouldUseUnknownWhenBidderUsernameIsBlank() {
    BidDTOs.BidChartPointDTO point = createPoint("A-1", 2_000_000L, "   ", false);

    BidHistoryPointViewModel viewModel = BidViewModelMapper.toHistoryPointViewModel(point);

    assertEquals("Unknown", viewModel.bidderUsername());
  }

  private static BidDTOs.BidChartPointDTO createPoint(
      String auctionId, long price, String bidderUsername, boolean autoBid) {
    BidDTOs.BidChartPointDTO point = new BidDTOs.BidChartPointDTO();
    point.setAuctionId(auctionId);
    point.setPrice(price);
    point.setBidderUsername(bidderUsername);
    point.setTimestamp(LocalDateTime.of(2026, 5, 26, 20, 30));
    point.setAutoBid(autoBid);
    return point;
  }

  private static void assertCurrencyTextContains(String actual, String expectedAmount) {
    String normalized = actual.replace('\u00A0', ' ').replace('\u202F', ' ');

    assertTrue(normalized.contains(expectedAmount));
    assertTrue(normalized.contains("₫"));
  }
}