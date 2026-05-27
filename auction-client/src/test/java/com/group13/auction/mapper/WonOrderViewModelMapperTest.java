package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.viewmodel.order.WonOrderViewModel;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link WonOrderViewModelMapper}. */
class WonOrderViewModelMapperTest {

  @Test
  void toViewModelShouldReturnSafeEmptyOrderWhenAuctionIsNull() {
    WonOrderViewModel viewModel = WonOrderViewModelMapper.toViewModel(null);

    assertEquals("", viewModel.auctionId());
    assertEquals("Sản phẩm đấu giá", viewModel.itemName());
    assertEquals("--", viewModel.sellerUsername());
    assertEquals("Người bán: --", viewModel.sellerText());
    assertEquals("--", viewModel.winningPriceText());
    assertEquals("", viewModel.auctionStatus());
    assertEquals("", viewModel.paymentStatus());
    assertEquals("Không rõ", viewModel.statusText());
    assertEquals("", viewModel.actionHintText());
    assertEquals("", viewModel.primaryImageUrl());
    assertEquals("--", viewModel.confirmReceiptDeadlineText());
    assertEquals("--", viewModel.reportDeadlineText());
    assertFalse(viewModel.canPay());
    assertFalse(viewModel.canConfirmReceipt());
    assertFalse(viewModel.canSubmitReport());
    assertFalse(viewModel.completed());
    assertFalse(viewModel.finished());
  }

  @Test
  void toViewModelShouldMapFinishedOrderAsPayable() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "FINISHED", null);

    WonOrderViewModel viewModel = WonOrderViewModelMapper.toViewModel(auction);

    assertEquals("A-1", viewModel.auctionId());
    assertEquals("Vintage Camera", viewModel.itemName());
    assertEquals("seller01", viewModel.sellerUsername());
    assertEquals("Người bán: seller01", viewModel.sellerText());
    assertCurrencyTextContains(viewModel.winningPriceText(), "2.500.000");
    assertEquals("FINISHED", viewModel.auctionStatus());
    assertEquals("", viewModel.paymentStatus());
    assertEquals("Chờ thanh toán", viewModel.statusText());
    assertEquals("Hoàn tất thanh toán để tiếp tục xử lý đơn hàng.", viewModel.actionHintText());
    assertEquals("items/camera.png", viewModel.primaryImageUrl());
    assertTrue(viewModel.canPay());
    assertFalse(viewModel.canConfirmReceipt());
    assertFalse(viewModel.canSubmitReport());
    assertFalse(viewModel.completed());
    assertTrue(viewModel.finished());
  }

  @Test
  void toViewModelShouldMapFundsHeldAsConfirmReceiptState() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "PAID", "FUNDS_HELD");

    WonOrderViewModel viewModel = WonOrderViewModelMapper.toViewModel(auction);

    assertEquals("Chờ xác nhận nhận hàng", viewModel.statusText());
    assertEquals("Xác nhận khi bạn đã nhận được sản phẩm.", viewModel.actionHintText());
    assertFalse(viewModel.canPay());
    assertTrue(viewModel.canConfirmReceipt());
    assertFalse(viewModel.canSubmitReport());
    assertFalse(viewModel.completed());
    assertTrue(viewModel.paid());
  }

  @Test
  void toViewModelShouldMapItemReceivedAsReportableState() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "PAID", "ITEM_RECEIVED");

    WonOrderViewModel viewModel = WonOrderViewModelMapper.toViewModel(auction);

    assertEquals("Đã nhận hàng", viewModel.statusText());
    assertEquals("Bạn có thể gửi báo cáo nếu sản phẩm có vấn đề.", viewModel.actionHintText());
    assertFalse(viewModel.canPay());
    assertFalse(viewModel.canConfirmReceipt());
    assertTrue(viewModel.canSubmitReport());
    assertTrue(viewModel.reportable());
    assertFalse(viewModel.completed());
  }

  @Test
  void toViewModelShouldMapCompletedOrder() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "PAID", "COMPLETED");

    WonOrderViewModel viewModel = WonOrderViewModelMapper.toViewModel(auction);

    assertEquals("Đã hoàn tất", viewModel.statusText());
    assertEquals("Đơn hàng đã hoàn tất.", viewModel.actionHintText());
    assertFalse(viewModel.canPay());
    assertFalse(viewModel.canConfirmReceipt());
    assertFalse(viewModel.canSubmitReport());
    assertTrue(viewModel.completed());
  }

  @Test
  void toViewModelShouldMapExpiredOrder() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "FINISHED", "EXPIRED");

    WonOrderViewModel viewModel = WonOrderViewModelMapper.toViewModel(auction);

    assertEquals("Đã hết hạn", viewModel.statusText());
    assertEquals("Đơn hàng đã hết hạn xử lý.", viewModel.actionHintText());
    assertFalse(viewModel.canPay());
  }

  @Test
  void toViewModelShouldUseFallbacksWhenItemIsMissingOrBlank() {
    AuctionDTOs.AuctionDTO auction = createAuction("A-1", "PAID", "COMPLETED");
    AuctionDTOs.ItemDTO item = new AuctionDTOs.ItemDTO();
    item.setName("   ");
    item.setSellerUsername(null);
    item.setImageUrls(List.of("   ", "items/valid.png"));
    auction.setItem(item);

    WonOrderViewModel viewModel = WonOrderViewModelMapper.toViewModel(auction);

    assertEquals("Sản phẩm đấu giá", viewModel.itemName());
    assertEquals("--", viewModel.sellerUsername());
    assertEquals("Người bán: --", viewModel.sellerText());
    assertEquals("items/valid.png", viewModel.primaryImageUrl());
  }

  @Test
  void toViewModelsShouldReturnEmptyListWhenAuctionsAreNull() {
    assertTrue(WonOrderViewModelMapper.toViewModels(null).isEmpty());
  }

  @Test
  void toViewModelsShouldMapAuctionsInOrder() {
    AuctionDTOs.AuctionDTO first = createAuction("A-1", "FINISHED", null);
    AuctionDTOs.AuctionDTO second = createAuction("A-2", "PAID", "COMPLETED");

    List<WonOrderViewModel> viewModels =
        WonOrderViewModelMapper.toViewModels(List.of(first, second));

    assertEquals(2, viewModels.size());
    assertEquals("A-1", viewModels.get(0).auctionId());
    assertEquals("A-2", viewModels.get(1).auctionId());
    assertEquals("Đã hoàn tất", viewModels.get(1).statusText());
  }

  private static AuctionDTOs.AuctionDTO createAuction(
      String auctionId, String auctionStatus, String paymentStatus) {
    AuctionDTOs.ItemDTO item = new AuctionDTOs.ItemDTO();
    item.setName("Vintage Camera");
    item.setSellerUsername("seller01");
    item.setImageUrls(List.of("items/camera.png"));

    AuctionDTOs.AuctionDTO auction = new AuctionDTOs.AuctionDTO();
    auction.setId(auctionId);
    auction.setItem(item);
    auction.setCurrentPrice(2_500_000D);
    auction.setStatus(auctionStatus);
    auction.setPaymentStatus(paymentStatus);
    auction.setConfirmReceiptDeadline(LocalDateTime.of(2026, 5, 27, 20, 30));
    auction.setReportDeadline(LocalDateTime.of(2026, 5, 30, 20, 30));
    return auction;
  }

  private static void assertCurrencyTextContains(String actual, String expectedAmount) {
    String normalized = actual.replace('\u00A0', ' ').replace('\u202F', ' ');

    assertTrue(normalized.contains(expectedAmount));
    assertTrue(normalized.contains("₫"));
  }
}
