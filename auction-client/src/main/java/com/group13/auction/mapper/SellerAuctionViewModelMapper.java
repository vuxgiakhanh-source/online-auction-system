package com.group13.auction.mapper;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.seller.SellerAuctionRowViewModel;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper chuyển auction DTO sang view model phục vụ màn quản lý phiên của người bán.
 */
public final class SellerAuctionViewModelMapper {

    private SellerAuctionViewModelMapper() {
        // Utility class.
    }

    /**
     * Lọc và map danh sách auction chung thành danh sách phiên thuộc người bán hiện tại.
     *
     * <p>Hiện backend chưa có API riêng cho danh sách phiên của từng người bán, nên client lọc theo
     * {@code sellerId} hoặc {@code sellerUsername} từ DTO trả về. Đây chỉ là bước lọc hiển thị; kiểm
     * tra quyền cuối cùng vẫn do server xử lý.
     *
     * @param auctions danh sách auction từ server
     * @param session session hiện tại
     * @return danh sách row view model của người bán
     */
    public static List<SellerAuctionRowViewModel> toSellerRows(
            List<AuctionDTOs.AuctionDTO> auctions, UserSession session) {
        if (auctions == null || session == null) {
            return List.of();
        }

        return auctions.stream()
                .filter(auction -> belongsToSeller(auction, session))
                .map(SellerAuctionViewModelMapper::toRow)
                .toList();
    }

    /**
     * Chuyển một auction DTO sang row view model.
     *
     * @param auction auction DTO từ server
     * @return row view model
     */
    public static SellerAuctionRowViewModel toRow(AuctionDTOs.AuctionDTO auction) {
        AuctionDTOs.ItemDTO item = auction == null ? null : auction.getItem();
        String status = normalize(auction == null ? null : auction.getStatus());
        boolean isOpen = "OPEN".equals(status);

        LocalDateTime startTime = auction == null ? null : auction.getStartTime();
        LocalDateTime endTime = effectiveEndTime(auction);

        return new SellerAuctionRowViewModel(
                auction == null || auction.getId() == null ? "" : auction.getId(),
                itemName(item),
                categoryText(item),
                statusText(status),
                CurrencyUtil.formatVnd(auction == null ? 0 : auction.getCurrentPrice()),
                CurrencyUtil.formatVnd(item == null ? 0 : item.getStartingPrice()),
                CurrencyUtil.formatVnd(auction == null ? 0 : auction.getReservePrice()),
                DateTimeUtil.formatDateTime(startTime),
                DateTimeUtil.formatDateTime(endTime),
                startTime,
                endTime,
                Math.max(0, auction == null ? 0 : auction.getViewerCount()) + " người xem",
                isOpen,
                isOpen);
    }

    private static boolean belongsToSeller(AuctionDTOs.AuctionDTO auction, UserSession session) {
        if (auction == null || auction.getItem() == null || session == null) {
            return false;
        }

        AuctionDTOs.ItemDTO item = auction.getItem();
        if (hasText(item.getSellerId()) && item.getSellerId().equals(session.getUserId())) {
            return true;
        }

        return hasText(item.getSellerUsername())
                && item.getSellerUsername().equalsIgnoreCase(session.getUsername());
    }

    private static LocalDateTime effectiveEndTime(AuctionDTOs.AuctionDTO auction) {
        if (auction == null) {
            return null;
        }
        return auction.getExtendedEndTime() != null ? auction.getExtendedEndTime() : auction.getEndTime();
    }

    private static String itemName(AuctionDTOs.ItemDTO item) {
        if (item == null || !hasText(item.getName())) {
            return "Phiên đấu giá chưa có tên";
        }
        return item.getName();
    }

    private static String categoryText(AuctionDTOs.ItemDTO item) {
        if (item == null || !hasText(item.getCategory())) {
            return "Khác";
        }

        return switch (normalize(item.getCategory())) {
            case "ELECTRONICS" -> "Điện tử";
            case "ART" -> "Nghệ thuật";
            case "VEHICLE" -> "Phương tiện";
            default -> item.getCategory();
        };
    }

    private static String statusText(String status) {
        return switch (normalize(status)) {
            case "OPEN" -> "Sắp mở";
            case "RUNNING" -> "Đang đấu giá";
            case "FINISHED" -> "Đã kết thúc";
            case "PAID" -> "Đã thanh toán";
            case "CANCELED" -> "Đã hủy";
            case "RESERVE_NOT_MET" -> "Chưa đạt giá sàn";
            default -> "Không rõ";
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}