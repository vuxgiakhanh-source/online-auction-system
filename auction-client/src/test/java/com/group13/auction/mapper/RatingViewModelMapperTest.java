package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.viewmodel.rating.RatingHistoryViewModel;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RatingViewModelMapper}. */
class RatingViewModelMapperTest {

  @Test
  void toHistoryViewModelsShouldReturnEmptyListWhenDtoIsNull() {
    assertTrue(RatingViewModelMapper.toHistoryViewModels(null).isEmpty());
  }

  @Test
  void toHistoryViewModelsShouldReturnEmptyListWhenEntriesAreNull() {
    RatingDTOs.RatingHistoryDTO dto = new RatingDTOs.RatingHistoryDTO();
    dto.setUserId("U-2");
    dto.setEntries(null);

    assertTrue(RatingViewModelMapper.toHistoryViewModels(dto).isEmpty());
  }

  @Test
  void toHistoryViewModelsShouldMapEntriesInOrder() {
    RatingDTOs.RatingHistoryDTO dto = new RatingDTOs.RatingHistoryDTO();
    dto.setUserId("U-SELLER");

    RatingDTOs.RatingEntryDTO first =
        createEntry("U-1", 4.5, "Giao hàng đúng mô tả.", LocalDateTime.of(2026, 5, 26, 20, 30));
    RatingDTOs.RatingEntryDTO second =
        createEntry("U-2", 5.0, "Trải nghiệm tốt.", LocalDateTime.of(2026, 5, 26, 21, 0));

    dto.setEntries(List.of(first, second));

    List<RatingHistoryViewModel> viewModels = RatingViewModelMapper.toHistoryViewModels(dto);

    assertEquals(2, viewModels.size());

    assertEquals("U-1", viewModels.get(0).getReviewerId());
    assertEquals("U-SELLER", viewModels.get(0).getTargetUserId());
    assertEquals(5, viewModels.get(0).getScore());
    assertEquals("4.5 / 5.0", viewModels.get(0).getScoreText());
    assertEquals("Giao hàng đúng mô tả.", viewModels.get(0).getComment());
    assertEquals("26/05/2026 20:30", viewModels.get(0).getCreatedAtText());

    assertEquals("U-2", viewModels.get(1).getReviewerId());
    assertEquals("5.0 / 5.0", viewModels.get(1).getScoreText());
  }

  @Test
  void toHistoryViewModelShouldReturnEmptyRatingWhenEntryIsNull() {
    RatingHistoryViewModel viewModel = RatingViewModelMapper.toHistoryViewModel("U-SELLER", null);

    assertEquals("--", viewModel.getRatingId());
    assertEquals("--", viewModel.getReviewerId());
    assertEquals("U-SELLER", viewModel.getTargetUserId());
    assertEquals("--", viewModel.getAuctionId());
    assertEquals(0, viewModel.getScore());
    assertEquals("0.0 / 5.0", viewModel.getScoreText());
    assertEquals("--", viewModel.getComment());
    assertEquals("--", viewModel.getCreatedAtText());
  }

  @Test
  void toHistoryViewModelShouldUseFallbacksForBlankFieldsAndNullDate() {
    RatingDTOs.RatingEntryDTO entry = createEntry("   ", 3.2, "   ", null);

    RatingHistoryViewModel viewModel = RatingViewModelMapper.toHistoryViewModel("   ", entry);

    assertEquals("--", viewModel.getReviewerId());
    assertEquals("--", viewModel.getTargetUserId());
    assertEquals("--", viewModel.getAuctionId());
    assertEquals(3, viewModel.getScore());
    assertEquals("3.2 / 5.0", viewModel.getScoreText());
    assertEquals("--", viewModel.getComment());
    assertEquals("--", viewModel.getCreatedAtText());
  }

  @Test
  void toHistoryViewModelShouldBuildRatingIdFromReviewerAndCreatedAt() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 5, 26, 20, 30);
    RatingDTOs.RatingEntryDTO entry = createEntry("U-1", 4.5, "Good", createdAt);

    RatingHistoryViewModel viewModel = RatingViewModelMapper.toHistoryViewModel("U-SELLER", entry);

    assertEquals("U-1-2026-05-26T20:30", viewModel.getRatingId());
  }

  private static RatingDTOs.RatingEntryDTO createEntry(
      String fromUserId, double rating, String comment, LocalDateTime createdAt) {
    RatingDTOs.RatingEntryDTO entry = new RatingDTOs.RatingEntryDTO();
    entry.setFromUserId(fromUserId);
    entry.setRating(rating);
    entry.setComment(comment);
    entry.setCreatedAt(createdAt);
    return entry;
  }
}