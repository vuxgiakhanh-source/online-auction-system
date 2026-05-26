package com.group13.auction.viewmodel.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link NotificationItemViewModel}. */
class NotificationItemViewModelTest {

  @Test
  void hasRelatedAuctionShouldReturnTrueWhenAuctionIdHasText() {
    NotificationItemViewModel viewModel =
        new NotificationItemViewModel(
            "N-1", "BID", "New bid", "Someone placed a bid", "26/05/2026", "A-1", false);

    assertTrue(viewModel.hasRelatedAuction());
  }

  @Test
  void hasRelatedAuctionShouldReturnFalseWhenAuctionIdIsNullOrBlank() {
    NotificationItemViewModel nullAuction =
        new NotificationItemViewModel("N-1", "BID", "Title", "Body", "Now", null, false);
    NotificationItemViewModel blankAuction =
        new NotificationItemViewModel("N-2", "BID", "Title", "Body", "Now", "   ", false);

    assertFalse(nullAuction.hasRelatedAuction());
    assertFalse(blankAuction.hasRelatedAuction());
  }

  @Test
  void readStateTextShouldDescribeReadState() {
    NotificationItemViewModel unread =
        new NotificationItemViewModel("N-1", "BID", "Title", "Body", "Now", "A-1", false);
    NotificationItemViewModel read =
        new NotificationItemViewModel("N-2", "BID", "Title", "Body", "Now", "A-1", true);

    assertEquals("Chưa đọc", unread.readStateText());
    assertEquals("Đã đọc", read.readStateText());
  }

  @Test
  void markReadShouldReturnNewReadViewModelAndKeepOtherFields() {
    NotificationItemViewModel unread =
        new NotificationItemViewModel(
            "N-1", "BID", "New bid", "Someone placed a bid", "26/05/2026", "A-1", false);

    NotificationItemViewModel read = unread.markRead();

    assertNotSame(unread, read);
    assertTrue(read.read());
    assertEquals("N-1", read.id());
    assertEquals("BID", read.type());
    assertEquals("New bid", read.title());
    assertEquals("Someone placed a bid", read.body());
    assertEquals("26/05/2026", read.createdAtText());
    assertEquals("A-1", read.relatedAuctionId());
  }
}