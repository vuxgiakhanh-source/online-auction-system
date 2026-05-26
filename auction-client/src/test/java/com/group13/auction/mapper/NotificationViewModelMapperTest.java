package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.viewmodel.notification.NotificationItemViewModel;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link NotificationViewModelMapper}. */
class NotificationViewModelMapperTest {

  @Test
  void toViewModelsShouldReturnEmptyListWhenArrayIsNull() {
    List<NotificationItemViewModel> viewModels = NotificationViewModelMapper.toViewModels(null);

    assertTrue(viewModels.isEmpty());
  }

  @Test
  void toViewModelsShouldReturnEmptyListWhenArrayIsEmpty() {
    List<NotificationItemViewModel> viewModels =
        NotificationViewModelMapper.toViewModels(new AdminDTOs.NotificationDTO[0]);

    assertTrue(viewModels.isEmpty());
  }

  @Test
  void toViewModelsShouldMapNotificationArrayInOrder() {
    AdminDTOs.NotificationDTO first = createNotification("N-1", "BID", "New bid", false);
    AdminDTOs.NotificationDTO second = createNotification("N-2", "PAYMENT", "Payment", true);

    List<NotificationItemViewModel> viewModels =
        NotificationViewModelMapper.toViewModels(new AdminDTOs.NotificationDTO[] {first, second});

    assertEquals(2, viewModels.size());
    assertEquals("N-1", viewModels.get(0).id());
    assertEquals("N-2", viewModels.get(1).id());
  }

  @Test
  void toViewModelShouldReturnEmptyReadNotificationWhenDtoIsNull() {
    NotificationItemViewModel viewModel = NotificationViewModelMapper.toViewModel(null);

    assertEquals("--", viewModel.id());
    assertEquals("--", viewModel.type());
    assertEquals("--", viewModel.title());
    assertEquals("--", viewModel.body());
    assertEquals("--", viewModel.createdAtText());
    assertTrue(viewModel.read());
  }

  @Test
  void toViewModelShouldMapNotificationFields() {
    AdminDTOs.NotificationDTO dto = createNotification("N-1", "BID", "New bid", false);
    dto.setBody("Someone placed a bid.");
    dto.setRelatedAuctionId("A-1");
    dto.setCreatedAt(LocalDateTime.of(2026, 5, 26, 20, 30));

    NotificationItemViewModel viewModel = NotificationViewModelMapper.toViewModel(dto);

    assertEquals("N-1", viewModel.id());
    assertEquals("BID", viewModel.type());
    assertEquals("New bid", viewModel.title());
    assertEquals("Someone placed a bid.", viewModel.body());
    assertEquals("26/05/2026 20:30", viewModel.createdAtText());
    assertEquals("A-1", viewModel.relatedAuctionId());
    assertTrue(viewModel.hasRelatedAuction());
  }

  @Test
  void toViewModelShouldUseFallbackForBlankTextFieldsAndNullDate() {
    AdminDTOs.NotificationDTO dto = new AdminDTOs.NotificationDTO();
    dto.setId("   ");
    dto.setType(null);
    dto.setTitle("");
    dto.setBody("   ");
    dto.setCreatedAt(null);
    dto.setRead(true);

    NotificationItemViewModel viewModel = NotificationViewModelMapper.toViewModel(dto);

    assertEquals("--", viewModel.id());
    assertEquals("--", viewModel.type());
    assertEquals("--", viewModel.title());
    assertEquals("--", viewModel.body());
    assertEquals("--", viewModel.createdAtText());
    assertTrue(viewModel.read());
  }

  private static AdminDTOs.NotificationDTO createNotification(
      String id, String type, String title, boolean read) {
    AdminDTOs.NotificationDTO dto = new AdminDTOs.NotificationDTO();
    dto.setId(id);
    dto.setType(type);
    dto.setTitle(title);
    dto.setBody("Body");
    dto.setCreatedAt(LocalDateTime.of(2026, 5, 26, 20, 30));
    dto.setRelatedAuctionId("A-1");
    dto.setRead(read);
    return dto;
  }
}