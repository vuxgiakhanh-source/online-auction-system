package com.group13.auction.mapper;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.notification.NotificationItemViewModel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Mapper chuyển notification DTO từ {@code auction-common} sang view model phía client. */
public final class NotificationViewModelMapper {

    private NotificationViewModelMapper() {
        // Utility class.
    }

    /**
     * Chuyển mảng notification DTO sang danh sách view model.
     *
     * @param notifications mảng DTO server trả về
     * @return danh sách view model thông báo
     */
    public static List<NotificationItemViewModel> toViewModels(
            AdminDTOs.NotificationDTO[] notifications) {
        if (notifications == null || notifications.length == 0) {
            return Collections.emptyList();
        }

        return Arrays.stream(notifications)
                .map(NotificationViewModelMapper::toViewModel)
                .toList();
    }

    /**
     * Chuyển một notification DTO sang view model.
     *
     * @param dto notification DTO
     * @return view model thông báo
     */
    public static NotificationItemViewModel toViewModel(AdminDTOs.NotificationDTO dto) {
        if (dto == null) {
            return empty();
        }

        return new NotificationItemViewModel(
                fallback(dto.getId()),
                fallback(dto.getType()),
                fallback(dto.getTitle()),
                fallback(dto.getBody()),
                DateTimeUtil.formatDateTime(dto.getCreatedAt()),
                dto.getRelatedAuctionId(),
                dto.isRead());
    }

    private static NotificationItemViewModel empty() {
        return new NotificationItemViewModel("--", "--", "--", "--", "--", null, true);
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "--" : value;
    }
}