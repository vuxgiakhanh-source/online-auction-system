package com.group13.auction.mapper;

import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.auction.BidHistoryPointViewModel;
import com.group13.auction.viewmodel.auction.LiveBidViewModel;
import java.util.List;

/** Mapper chuyển DTO bid từ {@code auction-common} sang view model của client. */
public final class BidViewModelMapper {

    private BidViewModelMapper() {
        // Utility class.
    }

    /** Chuyển bid update realtime sang view model. */
    public static LiveBidViewModel toLiveBidViewModel(BidDTOs.BidUpdateDTO update) {
        if (update == null) {
            return emptyLiveBidViewModel();
        }

        return new LiveBidViewModel(
                update.getAuctionId(),
                CurrencyUtil.formatVnd(update.getNewCurrentPrice()),
                leaderText(update.getLeaderUsername()),
                reserveStatusText(update.isReserveMet()),
                DateTimeUtil.formatDateTime(update.getTimestamp()),
                DateTimeUtil.formatDateTime(update.getNewEndTime()),
                update.getNewCurrentPrice(),
                update.isReserveMet());
    }

    /** Chuyển kết quả đặt giá thành view model để cập nhật nhanh UI. */
    public static LiveBidViewModel toLiveBidViewModel(BidDTOs.BidResultDTO result) {
        if (result == null) {
            return emptyLiveBidViewModel();
        }

        return new LiveBidViewModel(
                result.getAuctionId(),
                CurrencyUtil.formatVnd(result.getCurrentPrice()),
                "Bạn vừa đặt giá: " + CurrencyUtil.formatVnd(result.getAmount()),
                reserveStatusText(result.isReserveMet()),
                DateTimeUtil.formatDateTime(result.getTimestamp()),
                "--",
                result.getCurrentPrice(),
                result.isReserveMet());
    }

    /** Chuyển response lịch sử bid sang danh sách view model. */
    public static List<BidHistoryPointViewModel> toHistoryPointViewModels(
            BidDTOs.BidHistoryResponseDTO history) {
        if (history == null || history.getPoints() == null) {
            return List.of();
        }

        return history.getPoints().stream()
                .map(BidViewModelMapper::toHistoryPointViewModel)
                .toList();
    }

    /** Chuyển một điểm biểu đồ bid sang view model. */
    public static BidHistoryPointViewModel toHistoryPointViewModel(BidDTOs.BidChartPointDTO point) {
        if (point == null) {
            return new BidHistoryPointViewModel("", 0L, "--", "--", "--", false);
        }

        return new BidHistoryPointViewModel(
                point.getAuctionId(),
                point.getPrice(),
                CurrencyUtil.formatVnd(point.getPrice()),
                isBlank(point.getBidderUsername()) ? "Unknown" : point.getBidderUsername(),
                DateTimeUtil.formatDateTime(point.getTimestamp()),
                point.isAutoBid());
    }

    private static LiveBidViewModel emptyLiveBidViewModel() {
        return new LiveBidViewModel(
            "", "--", "Người dẫn đầu: --", "Trạng thái giá sàn: --", "--", "--", 0L, false);
    }

    private static String reserveStatusText(boolean reserveMet) {
        return reserveMet
            ? "Trạng thái giá sàn: Đã đạt"
            : "Trạng thái giá sàn: Chưa đạt";
    }

    private static String leaderText(String username) {
        return isBlank(username) ? "Người dẫn đầu: chưa có" : "Người dẫn đầu: " + username;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}