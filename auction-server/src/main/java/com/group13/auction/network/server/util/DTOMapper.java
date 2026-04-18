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
 *
 * <p>Tập trung mapping tại một nơi, tránh lặp code trong các handler.
 */
public final class DTOMapper {

    private DTOMapper() {}

    // ── User ──────────────────────────────────────────────────────────────────

    /**
     * Chuyển {@link User} thành {@link UserDTO}.
     *
     * @param user        domain user
     * @param showBalance true nếu gửi về chính chủ hoặc Admin (ẩn balance với người khác)
     */
    public static UserDTO toUserDTO(User user, boolean showBalance) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setAccountStatus(user.getAccountStatus().name());
        dto.setRating(user.getRating());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());

        // Roles
        List<String> roles = new ArrayList<>();
        for (User.UserRole role : User.UserRole.values()) {
            if (user.hasRole(role)) roles.add(role.name());
        }
        dto.setRoles(roles);

        // Admin type
        if (user instanceof SystemAdmin) {
            dto.setAdminType("MASTER");
        } else if (user instanceof Admin) {
            dto.setAdminType("STAFF");
        }

        // Balance (chỉ gửi khi được phép)
        if (showBalance && user instanceof NormalUser normalUser) {
            dto.setBalance(normalUser.getBalance());
            dto.setLockedDeposit(normalUser.getLockedDeposit());
            dto.setAvailableBalance(normalUser.getAvailableBalance());
            dto.setHasEverBeenPenalized(normalUser.isHasEverBeenPenalized());
        }

        // Email
        if (user instanceof NormalUser normalUser) {
            dto.setEmail(normalUser.getEmail());
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
        dto.setReservePrice(auction.getReserveStrategy().getReservePrice());
        dto.setStatus(auction.getStatus().name());
        dto.setReserveMet(auction.isReserveMet());
        dto.setViewerCount(auction.getViewerCount());
        dto.setCreatedAt(auction.getCreatedAt());
        dto.setUpdatedAt(auction.getUpdatedAt());

        if (auction.getCurrentLeader() != null) {
            dto.setCurrentLeaderId(auction.getCurrentLeader().getId());
            dto.setCurrentLeaderUsername(auction.getCurrentLeader().getUsername());
        }

        return dto;
    }

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
        return dto;
    }

    public static AuctionDTOs.AuctionUpdateDTO toAuctionUpdateDTO(Auction auction, String cancelReason) {
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

    public static BidDTOs.BidUpdateDTO toBidUpdateDTO(Auction auction, double newPrice) {
        BidDTOs.BidUpdateDTO dto = new BidDTOs.BidUpdateDTO();
        dto.setAuctionId(auction.getId());
        dto.setNewCurrentPrice(newPrice);
        dto.setReserveMet(auction.isReserveMet());
        dto.setTimestamp(LocalDateTime.now());

        if (auction.getCurrentLeader() != null) {
            dto.setLeaderId(auction.getCurrentLeader().getId());
            dto.setLeaderUsername(auction.getCurrentLeader().getUsername());
        }
        return dto;
    }

    public static BidDTOs.BidChartPointDTO toBidChartPoint(String auctionId, double price,
                                                           String bidderUsername, boolean isAutoBid) {
        BidDTOs.BidChartPointDTO dto = new BidDTOs.BidChartPointDTO();
        dto.setAuctionId(auctionId);
        dto.setPrice(price);
        dto.setBidderUsername(bidderUsername);
        dto.setTimestamp(LocalDateTime.now());
        dto.setAutoBid(isAutoBid);
        return dto;
    }
}
