package com.group13.auction.mapper;

import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.rating.RatingHistoryViewModel;
import java.util.List;
import java.util.Locale;

/** Mapper chuyển rating DTO từ auction-common sang view model phía client. */
public final class RatingViewModelMapper {

  private RatingViewModelMapper() {
    // Utility class.
  }

  /**
   * Chuyển rating history DTO sang danh sách view model hiển thị trong bảng rating.
   *
   * @param dto rating history DTO server trả về
   * @return danh sách rating view model
   */
  public static List<RatingHistoryViewModel> toHistoryViewModels(RatingDTOs.RatingHistoryDTO dto) {
    if (dto == null || dto.getEntries() == null) {
      return List.of();
    }

    return dto.getEntries().stream()
        .map(entry -> toHistoryViewModel(dto.getUserId(), entry))
        .toList();
  }

  /**
   * Chuyển một rating entry DTO sang view model.
   *
   * @param targetUserId mã người được đánh giá
   * @param dto rating entry DTO
   * @return rating view model
   */
  public static RatingHistoryViewModel toHistoryViewModel(
      String targetUserId, RatingDTOs.RatingEntryDTO dto) {
    if (dto == null) {
      return empty(targetUserId);
    }

    int score = (int) Math.round(dto.getRating());
    String createdAtText = DateTimeUtil.formatDateTime(dto.getCreatedAt());

    return new RatingHistoryViewModel(
        buildRatingId(dto),
        fallback(dto.getFromUserId()),
        fallback(targetUserId),
        "--",
        score,
        String.format(Locale.US, "%.1f / 5.0", dto.getRating()),
        fallback(dto.getComment()),
        createdAtText);
  }

  private static RatingHistoryViewModel empty(String targetUserId) {
    return new RatingHistoryViewModel(
        "--", "--", fallback(targetUserId), "--", 0, "0.0 / 5.0", "--", "--");
  }

  private static String buildRatingId(RatingDTOs.RatingEntryDTO dto) {
    String fromUserId = fallback(dto.getFromUserId());
    String createdAt = dto.getCreatedAt() == null ? "unknown-time" : dto.getCreatedAt().toString();
    return fromUserId + "-" + createdAt;
  }

  private static String fallback(String value) {
    return value == null || value.isBlank() ? "--" : value;
  }
}
