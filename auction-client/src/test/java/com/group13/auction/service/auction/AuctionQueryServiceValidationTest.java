package com.group13.auction.service.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link AuctionQueryService}. */
class AuctionQueryServiceValidationTest {

  @Test
  void getAuctionDetailShouldFailWhenAuctionIdIsNull() {
    AuctionQueryService service = createService();

    assertFutureFailsWithMessage(service.getAuctionDetail(null), "Thiếu mã phiên đấu giá.");
  }

  @Test
  void getAuctionDetailShouldFailWhenAuctionIdIsBlank() {
    AuctionQueryService service = createService();

    assertFutureFailsWithMessage(service.getAuctionDetail("   "), "Thiếu mã phiên đấu giá.");
  }

  @Test
  void sortByRequestedOrderShouldSortViewerCountDescending() {
    AuctionDTOs.AuctionDTO lowViews = auction("low", 5, 100_000, LocalDateTime.now());
    AuctionDTOs.AuctionDTO highViews = auction("high", 25, 80_000, LocalDateTime.now());
    AuctionDTOs.AuctionDTO middleViews = auction("middle", 12, 120_000, LocalDateTime.now());

    List<AuctionDTOs.AuctionDTO> sorted =
        AuctionQueryService.sortByRequestedOrder(
            List.of(lowViews, highViews, middleViews), "VIEWER_COUNT");

    assertEquals(
        List.of("high", "middle", "low"),
        sorted.stream().map(AuctionDTOs.AuctionDTO::getId).toList());
  }

  @Test
  void sortByRequestedOrderShouldBreakViewerCountTiesByCurrentPriceDescending() {
    LocalDateTime startTime = LocalDateTime.now();
    AuctionDTOs.AuctionDTO cheaper = auction("cheaper", 10, 100_000, startTime);
    AuctionDTOs.AuctionDTO expensive = auction("expensive", 10, 300_000, startTime);

    List<AuctionDTOs.AuctionDTO> sorted =
        AuctionQueryService.sortByRequestedOrder(List.of(cheaper, expensive), "VIEWER_COUNT");

    assertEquals(
        List.of("expensive", "cheaper"),
        sorted.stream().map(AuctionDTOs.AuctionDTO::getId).toList());
  }

  private static AuctionQueryService createService() {
    return new AuctionQueryService(ClientNetworkFacade.getDefault());
  }

  private static AuctionDTOs.AuctionDTO auction(
      String id, int viewerCount, double currentPrice, LocalDateTime startTime) {
    AuctionDTOs.AuctionDTO auction = new AuctionDTOs.AuctionDTO();
    auction.setId(id);
    auction.setViewerCount(viewerCount);
    auction.setCurrentPrice(currentPrice);
    auction.setStartTime(startTime);
    return auction;
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}
