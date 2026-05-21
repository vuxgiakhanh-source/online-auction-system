package com.group13.auction.network.server.util;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class chuyển đổi domain model → DTO.
 * Tập trung mapping tại một nơi, tránh lặp code trong các handler.
 */
public final class DTOMapper {

    private DTOMapper() {}

    // ── User ──────────────────────────────────────────────────────────────────

    public static UserDTO toUserDTO(User user, boolean showBalance) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setAccountStatus(user.getAccountStatus().name());
        dto.setRating(user.getRating());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());

        List<String> roles = new ArrayList<>();
        for (User.UserRole role : User.UserRole.values()) {
            if (user.hasRole(role)) roles.add(role.name());
        }
        dto.setRoles(roles);

        if (user instanceof SystemAdmin) {
            dto.setAdminType("MASTER");
        } else if (user instanceof Admin) {
            dto.setAdminType("STAFF");
        }

        if (showBalance && user instanceof NormalUser normalUser) {
            dto.setBalance(normalUser.getBalance());
            dto.setLockedDeposit(normalUser.getLockedDeposit());
            dto.setAvailableBalance(normalUser.getAvailableBalance());
            dto.setHasEverBeenPenalized(normalUser.isHasEverBeenPenalized());
        }

        if (user instanceof NormalUser normalUser) {
            dto.setEmail(normalUser.getEmail());
            dto.setTimesRestored(normalUser.getTimesRestored());
        } else if (user instanceof Admin admin) {
            dto.setEmail(admin.getEmail());
        }

        return dto;
    }

    // ── Auction ───────────────────────────────────────────────────────────────

    public static AuctionDTOs.AuctionDTO toAuctionDTO(Auction auction) {
        AuctionDTOs.AuctionDTO dto = new AuctionDTOs.AuctionDTO();
        dto.setId(auction.getId());
        dto.setItem(toItemDTO(auction.getItem()));
        dto.setStartTime(auction.getStartTime());
        dto.setEndTime(auction.getEndTime());
        dto.setCurrentPrice(auction.getCurrentPrice());
        dto.setReservePrice(auction.getReservePrice());
        dto.setStatus(auction.getStatus().name());
        dto.setReserveMet(auction.isReserveMet());
        dto.setViewerCount(auction.getViewerCount());
        dto.setCreatedAt(auction.getCreatedAt());
        dto.setUpdatedAt(auction.getUpdatedAt());

        if (auction.getCurrentLeader() != null) {
            dto.setCurrentLeaderId(auction.getCurrentLeader().getId());
            dto.setCurrentLeaderUsername(auction.getCurrentLeader().getUsername());
        }
        // Populate winner payment info — client cần để hiển thị đúng trạng thái
        // đơn hàng (FUNDS_HELD / ITEM_RECEIVED) sau khi refresh mà không bị stale.
        if (auction.getWinner() != null) {
            dto.setPaymentStatus(auction.getWinner().getPaymentStatus().name());
            dto.setConfirmReceiptDeadline(auction.getWinner().getConfirmReceiptDeadline());
            dto.setReportDeadline(auction.getWinner().getReportDeadline());
        }
        return dto;
    }

    /**
     * Map Item domain → ItemDTO.
     * imageUrls được map luôn — list rỗng nếu item không có ảnh.
     */
    public static AuctionDTOs.ItemDTO toItemDTO(Item item) {
        AuctionDTOs.ItemDTO dto = new AuctionDTOs.ItemDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        dto.setCategory(item.getClass().getSimpleName().toUpperCase());
        dto.setStartingPrice(item.getStartingPrice());
        if (item.getSeller() != null) {
            dto.setSellerId(item.getSeller().getId());
            dto.setSellerUsername(item.getSeller().getUsername());
        }
        // Map imageUrls — trả về list rỗng (không null) khi item không có ảnh
        dto.setImageUrls(item.getImageUrls());
        return dto;
    }

    public static AuctionDTOs.AuctionUpdateDTO toAuctionUpdateDTO(Auction auction,
                                                                  String cancelReason) {
        AuctionDTOs.AuctionUpdateDTO dto = new AuctionDTOs.AuctionUpdateDTO();
        dto.setAuctionId(auction.getId());
        dto.setNewStatus(auction.getStatus().name());
        dto.setCancelReason(cancelReason);

        if (auction.getWinner() != null) {
            dto.setFinalPrice(auction.getWinner().getFinalPrice());
            dto.setWinnerId(auction.getWinner().getWinner().getId());
            dto.setWinnerUsername(auction.getWinner().getWinner().getUsername());
        }
        return dto;
    }

    // ── Bid ───────────────────────────────────────────────────────────────────

    /**
     * Tạo BidUpdateDTO với đầy đủ thông tin bao gồm delta giá.
     *
     * @param auction      phiên đấu giá (đã update currentPrice + leader sau bid)
     * @param newPrice     giá vừa được chấp nhận
     * @param previousPrice giá trước khi bid này xảy ra (capture TRƯỚC khi placeBid)
     */
    public static BidDTOs.BidUpdateDTO toBidUpdateDTO(Auction auction, long newPrice, long previousPrice) {
        BidDTOs.BidUpdateDTO dto = new BidDTOs.BidUpdateDTO();
        dto.setAuctionId(auction.getId());
        dto.setNewCurrentPrice(newPrice);
        dto.setPreviousPrice(previousPrice);
        dto.setPriceChange(newPrice - previousPrice);   // luôn dương trong đấu giá hợp lệ
        dto.setReserveMet(auction.isReserveMet());
        dto.setTimestamp(LocalDateTime.now());

        if (auction.getCurrentLeader() != null) {
            dto.setLeaderId(auction.getCurrentLeader().getId());
            dto.setLeaderUsername(auction.getCurrentLeader().getUsername());
        }
        return dto;
    }

    public static BidDTOs.BidChartPointDTO toBidChartPoint(String auctionId, long price,
                                                           String bidderUsername,
                                                           boolean isAutoBid) {
        BidDTOs.BidChartPointDTO dto = new BidDTOs.BidChartPointDTO();
        dto.setAuctionId(auctionId);
        dto.setPrice(price);
        dto.setBidderUsername(bidderUsername);
        dto.setTimestamp(LocalDateTime.now());
        dto.setAutoBid(isAutoBid);
        return dto;
    }
}