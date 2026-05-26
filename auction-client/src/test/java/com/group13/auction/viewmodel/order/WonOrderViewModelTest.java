package com.group13.auction.viewmodel.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link WonOrderViewModel}. */
class WonOrderViewModelTest {

  @Test
  void constructorShouldUseFallbacksForBlankDisplayFields() {
    WonOrderViewModel viewModel =
        new WonOrderViewModel(
            null,
            "   ",
            "   ",
            "   ",
            "   ",
            null,
            null,
            "   ",
            null,
            null,
            "   ",
            "   ",
            false,
            false,
            false,
            false,
            false);

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
  }

  @Test
  void constructorShouldTrimDisplayFields() {
    WonOrderViewModel viewModel =
        createViewModel(
            "  A-1  ",
            "  Camera  ",
            "  seller01  ",
            "  Người bán: seller01  ",
            "  1.000.000 ₫  ",
            "  FINISHED  ",
            "  UNPAID  ",
            "  Chờ thanh toán  ",
            "  Hãy thanh toán đơn hàng  ",
            "  image.png  ",
            "  27/05/2026  ",
            "  30/05/2026  ",
            true,
            false,
            false,
            false,
            true);

    assertEquals("A-1", viewModel.auctionId());
    assertEquals("Camera", viewModel.itemName());
    assertEquals("seller01", viewModel.sellerUsername());
    assertEquals("Người bán: seller01", viewModel.sellerText());
    assertEquals("1.000.000 ₫", viewModel.winningPriceText());
    assertEquals("FINISHED", viewModel.auctionStatus());
    assertEquals("UNPAID", viewModel.paymentStatus());
    assertEquals("Chờ thanh toán", viewModel.statusText());
    assertEquals("Hãy thanh toán đơn hàng", viewModel.actionHintText());
    assertEquals("image.png", viewModel.primaryImageUrl());
    assertEquals("27/05/2026", viewModel.confirmReceiptDeadlineText());
    assertEquals("30/05/2026", viewModel.reportDeadlineText());
  }

  @Test
  void hasImageShouldReturnTrueOnlyWhenPrimaryImageUrlHasText() {
    assertTrue(createViewModelWithImage("image.png").hasImage());
    assertFalse(createViewModelWithImage("   ").hasImage());
    assertFalse(createViewModelWithImage(null).hasImage());
  }

  @Test
  void paidShouldReturnTrueWhenAuctionStatusIsPaidIgnoringCase() {
    assertTrue(createViewModelWithAuctionStatus("PAID").paid());
    assertTrue(createViewModelWithAuctionStatus("paid").paid());
    assertFalse(createViewModelWithAuctionStatus("FINISHED").paid());
    assertFalse(createViewModelWithAuctionStatus(null).paid());
  }

  @Test
  void statusShouldKeepBackwardCompatibleAuctionStatusValue() {
    WonOrderViewModel viewModel = createViewModelWithAuctionStatus("FINISHED");

    assertEquals("FINISHED", viewModel.status());
  }

  @Test
  void reportableShouldFollowCanSubmitReport() {
    assertTrue(createViewModelWithReportPermission(true).reportable());
    assertFalse(createViewModelWithReportPermission(false).reportable());
  }

  @Test
  void booleanFlagsShouldBeExposedDirectly() {
    WonOrderViewModel viewModel =
        createViewModel(
            "A-1",
            "Camera",
            "seller01",
            "Người bán: seller01",
            "1.000.000 ₫",
            "FINISHED",
            "UNPAID",
            "Chờ thanh toán",
            "Hãy thanh toán đơn hàng",
            "image.png",
            "27/05/2026",
            "30/05/2026",
            true,
            true,
            true,
            true,
            true);

    assertTrue(viewModel.canPay());
    assertTrue(viewModel.canConfirmReceipt());
    assertTrue(viewModel.canSubmitReport());
    assertTrue(viewModel.completed());
    assertTrue(viewModel.finished());
  }

  private static WonOrderViewModel createViewModelWithImage(String imageUrl) {
    return createViewModel(
        "A-1",
        "Camera",
        "seller01",
        "Người bán: seller01",
        "1.000.000 ₫",
        "FINISHED",
        "UNPAID",
        "Chờ thanh toán",
        "",
        imageUrl,
        "--",
        "--",
        false,
        false,
        false,
        false,
        true);
  }

  private static WonOrderViewModel createViewModelWithAuctionStatus(String auctionStatus) {
    return createViewModel(
        "A-1",
        "Camera",
        "seller01",
        "Người bán: seller01",
        "1.000.000 ₫",
        auctionStatus,
        "UNPAID",
        "Chờ thanh toán",
        "",
        "image.png",
        "--",
        "--",
        false,
        false,
        false,
        false,
        true);
  }

  private static WonOrderViewModel createViewModelWithReportPermission(boolean canSubmitReport) {
    return createViewModel(
        "A-1",
        "Camera",
        "seller01",
        "Người bán: seller01",
        "1.000.000 ₫",
        "PAID",
        "PAID",
        "Đã nhận hàng",
        "",
        "image.png",
        "--",
        "--",
        false,
        false,
        canSubmitReport,
        true,
        true);
  }

  private static WonOrderViewModel createViewModel(
      String auctionId,
      String itemName,
      String sellerUsername,
      String sellerText,
      String winningPriceText,
      String auctionStatus,
      String paymentStatus,
      String statusText,
      String actionHintText,
      String primaryImageUrl,
      String confirmReceiptDeadlineText,
      String reportDeadlineText,
      boolean canPay,
      boolean canConfirmReceipt,
      boolean canSubmitReport,
      boolean completed,
      boolean finished) {
    return new WonOrderViewModel(
        auctionId,
        itemName,
        sellerUsername,
        sellerText,
        winningPriceText,
        auctionStatus,
        paymentStatus,
        statusText,
        actionHintText,
        primaryImageUrl,
        confirmReceiptDeadlineText,
        reportDeadlineText,
        canPay,
        canConfirmReceipt,
        canSubmitReport,
        completed,
        finished);
  }
}