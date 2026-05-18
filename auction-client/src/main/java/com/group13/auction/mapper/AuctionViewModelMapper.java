package com.group13.auction.mapper;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.auction.AuctionCardViewModel;
import com.group13.auction.viewmodel.auction.AuctionDetailViewModel;
import com.group13.auction.viewmodel.auction.AuctionTimerViewModel;
import com.group13.auction.viewmodel.admin.AuctionModerationViewModel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Mapper chuyển DTO auction từ {@code auction-common} sang view model của client. */
public final class AuctionViewModelMapper {

    private AuctionViewModelMapper() {
        // Utility class.
    }

    /** Chuyển danh sách DTO sang danh sách card view model. */
    public static List<AuctionCardViewModel> toCardViewModels(
            List<AuctionDTOs.AuctionDTO> auctions) {
        if (auctions == null) {
            return List.of();
        }

        return auctions.stream()
                .map(AuctionViewModelMapper::toCardViewModel)
                .toList();
    }

    /** Chuyển một DTO auction sang card view model. */
    public static AuctionCardViewModel toCardViewModel(AuctionDTOs.AuctionDTO auction) {
        AuctionDTOs.ItemDTO item = auction == null ? null : auction.getItem();
        LocalDateTime effectiveEndTime = effectiveEndTime(auction);

        return new AuctionCardViewModel(
                safeAuctionId(auction),
                itemName(item),
                categoryText(item),
                statusText(auction == null ? null : auction.getStatus()),
                CurrencyUtil.formatVnd(auction == null ? 0 : auction.getCurrentPrice()),
                CurrencyUtil.formatVnd(item == null ? 0 : item.getStartingPrice()),
                remainingTimeText(effectiveEndTime),
                DateTimeUtil.formatDateTime(effectiveEndTime),
                sellerText(item),
                viewerCountText(auction == null ? 0 : auction.getViewerCount()),
                canJoinOrWatch(auction));
    }

    /** Chuyển một DTO auction sang detail view model. */
    public static AuctionDetailViewModel toDetailViewModel(AuctionDTOs.AuctionDTO auction) {
        AuctionDTOs.ItemDTO item = auction == null ? null : auction.getItem();
        LocalDateTime effectiveEndTime = effectiveEndTime(auction);
        double currentPrice = auction == null ? 0 : auction.getCurrentPrice();
        String rawStatus = normalize(auction == null ? null : auction.getStatus());
        String currentLeaderId =
                auction == null || auction.getCurrentLeaderId() == null
                        ? ""
                        : auction.getCurrentLeaderId();
        String currentLeaderUsername =
                auction == null || auction.getCurrentLeaderUsername() == null
                        ? ""
                        : auction.getCurrentLeaderUsername();

        return new AuctionDetailViewModel(
                safeAuctionId(auction),
                itemName(item),
                descriptionText(item),
                categoryText(item),
                sellerText(item),
                rawStatus,
                statusText(auction == null ? null : auction.getStatus()),
                currentLeaderId,
                currentLeaderUsername,
                CurrencyUtil.formatVnd(currentPrice),
                CurrencyUtil.formatVnd(item == null ? 0 : item.getStartingPrice()),
                CurrencyUtil.formatVnd(auction == null ? 0 : auction.getReservePrice()),
                leaderText(auction),
                viewerCountText(auction == null ? 0 : auction.getViewerCount()),
                DateTimeUtil.formatDateTime(auction == null ? null : auction.getStartTime()),
                DateTimeUtil.formatDateTime(effectiveEndTime),
                remainingTimeText(effectiveEndTime),
                canJoinOrWatch(auction),
                canBidLive(auction),
                currentPrice);
    }

    /**
     * Chuyển danh sách auction DTO sang danh sách view model dành cho Admin moderation.
     *
     * @param auctions danh sách auction DTO server trả về
     * @return danh sách auction moderation view model
     */
    public static List<AuctionModerationViewModel> toModerationViewModels(
            List<AuctionDTOs.AuctionDTO> auctions) {
        if (auctions == null) {
            return List.of();
        }

        return auctions.stream()
                .map(AuctionViewModelMapper::toModerationViewModel)
                .toList();
    }

    /**
     * Chuyển một auction DTO sang view model dành cho Admin moderation.
     *
     * @param auction auction DTO
     * @return auction moderation view model
     */
    public static AuctionModerationViewModel toModerationViewModel(
            AuctionDTOs.AuctionDTO auction) {
        AuctionDTOs.ItemDTO item = auction == null ? null : auction.getItem();
        LocalDateTime effectiveEndTime = effectiveEndTime(auction);
        String status = auction == null ? null : auction.getStatus();

        return new AuctionModerationViewModel(
                safeAuctionId(auction),
                itemName(item),
                sellerName(item),
                CurrencyUtil.formatVnd(auction == null ? 0 : auction.getCurrentPrice()),
                statusText(status),
                DateTimeUtil.formatDateTime(auction == null ? null : auction.getStartTime()),
                DateTimeUtil.formatDateTime(effectiveEndTime),
                canAdminCancel(status));
    }

    private static String sellerName(AuctionDTOs.ItemDTO item) {
        if (item == null || isBlank(item.getSellerUsername())) {
            return "--";
        }

        return item.getSellerUsername();
    }

    private static boolean canAdminCancel(String status) {
        String normalizedStatus = normalize(status);
        return "OPEN".equals(normalizedStatus) || "RUNNING".equals(normalizedStatus);
    }

    /** Tạo view model đếm ngược dựa trên end time của auction. */
    public static AuctionTimerViewModel toTimerViewModel(AuctionDTOs.AuctionDTO auction) {
        LocalDateTime effectiveEndTime = effectiveEndTime(auction);
        boolean ended = effectiveEndTime == null || !effectiveEndTime.isAfter(LocalDateTime.now());

        return new AuctionTimerViewModel(
                remainingTimeText(effectiveEndTime),
                DateTimeUtil.formatDateTime(effectiveEndTime),
                ended);
    }

    /** Kiểm tra phiên có thể mở live bidding từ phía UI hay không. */
    public static boolean canBidLive(AuctionDTOs.AuctionDTO auction) {
        if (auction == null) {
            return false;
        }

        String status = normalize(auction.getStatus());
        return "RUNNING".equals(status) && !toTimerViewModel(auction).ended();
    }

    private static boolean canJoinOrWatch(AuctionDTOs.AuctionDTO auction) {
        if (auction == null) {
            return false;
        }

        String status = normalize(auction.getStatus());
        return "OPEN".equals(status) || "RUNNING".equals(status);
    }

    private static String safeAuctionId(AuctionDTOs.AuctionDTO auction) {
        return auction == null || auction.getId() == null ? "" : auction.getId();
    }

    private static String itemName(AuctionDTOs.ItemDTO item) {
        if (item == null || isBlank(item.getName())) {
            return "Phiên đấu giá chưa có tên";
        }

        return item.getName();
    }

    private static String descriptionText(AuctionDTOs.ItemDTO item) {
        if (item == null || isBlank(item.getDescription())) {
            return "Chưa có mô tả chi tiết cho sản phẩm này.";
        }

        return item.getDescription();
    }

    private static String categoryText(AuctionDTOs.ItemDTO item) {
        if (item == null || isBlank(item.getCategory())) {
            return "Khác";
        }

        return switch (normalize(item.getCategory())) {
            case "ELECTRONICS" -> "Điện tử";
            case "ART" -> "Nghệ thuật";
            case "VEHICLE" -> "Phương tiện";
            default -> item.getCategory();
        };
    }

    private static String sellerText(AuctionDTOs.ItemDTO item) {
        if (item == null || isBlank(item.getSellerUsername())) {
            return "Người bán: --";
        }

        return "Người bán: " + item.getSellerUsername();
    }

    private static String statusText(String status) {
        if (isBlank(status)) {
            return "Không rõ";
        }

        return switch (normalize(status)) {
            case "OPEN" -> "Sắp mở";
            case "RUNNING" -> "Đang đấu giá";
            case "FINISHED" -> "Đã kết thúc";
            case "PAID" -> "Đã thanh toán";
            case "CANCELED" -> "Đã hủy";
            case "RESERVE_NOT_MET" -> "Chưa đạt giá sàn";
            default -> status;
        };
    }

    private static String leaderText(AuctionDTOs.AuctionDTO auction) {
        if (auction == null || isBlank(auction.getCurrentLeaderUsername())) {
            return "Người dẫn đầu: chưa có";
        }

        return "Người dẫn đầu: " + auction.getCurrentLeaderUsername();
    }

    private static String viewerCountText(int viewerCount) {
        return Math.max(0, viewerCount) + " người đang xem";
    }

    private static String remainingTimeText(LocalDateTime endTime) {
        Duration remaining = endTime == null
                ? Duration.ZERO
                : Duration.between(LocalDateTime.now(), endTime);

        return DateTimeUtil.formatRemaining(remaining);
    }

    private static LocalDateTime effectiveEndTime(AuctionDTOs.AuctionDTO auction) {
        if (auction == null) {
            return null;
        }

        return auction.getExtendedEndTime() != null
                ? auction.getExtendedEndTime()
                : auction.getEndTime();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}