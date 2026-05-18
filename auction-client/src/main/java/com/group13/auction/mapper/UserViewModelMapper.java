package com.group13.auction.mapper;

import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.profile.UserProfileViewModel;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Mapper chuyển user DTO từ {@code auction-common} sang view model phía client. */
public final class UserViewModelMapper {

    private UserViewModelMapper() {
        // Utility class.
    }

    /**
     * Chuyển user DTO sang profile view model.
     *
     * @param dto dữ liệu user server trả về
     * @return view model hồ sơ người dùng
     */
    public static UserProfileViewModel toProfileViewModel(UserDTO dto) {
        if (dto == null) {
            return emptyProfile();
        }

        List<String> roles = safeRoles(dto.getRoles());
        boolean admin = hasRole(roles, "ADMIN") || dto.getAdminType() != null;
        boolean seller = hasRole(roles, "SELLER") || hasRole(roles, "BIDDER_SELLER");
        boolean bidder = hasRole(roles, "BIDDER") || hasRole(roles, "BIDDER_SELLER");

        return new UserProfileViewModel(
                fallback(dto.getId()),
                fallback(dto.getUsername()),
                fallback(dto.getEmail()),
                rolesText(roles),
                primaryRoleText(roles, dto.getAdminType()),
                accountStatusText(dto.getAccountStatus()),
                String.format(Locale.US, "%.1f / 5.0", dto.getRating()),
                formatMoney(dto.getBalance()),
                formatMoney(dto.getLockedDeposit()),
                formatMoney(dto.getAvailableBalance()),
                DateTimeUtil.formatDateTime(dto.getCreatedAt()),
                DateTimeUtil.formatDateTime(dto.getUpdatedAt()),
                bidder,
                seller,
                admin,
                canRequestSellerRole(dto, roles, admin, seller),
                dto.isHasEverBeenPenalized());
    }

    private static UserProfileViewModel emptyProfile() {
        return new UserProfileViewModel(
                "--",
                "--",
                "--",
                "--",
                "--",
                "--",
                "--",
                "--",
                "--",
                "--",
                "--",
                "--",
                false,
                false,
                false,
                false,
                false);
    }

    private static List<String> safeRoles(List<String> roles) {
        return roles == null ? Collections.emptyList() : roles;
    }

    private static boolean hasRole(List<String> roles, String expectedRole) {
        return roles.stream().anyMatch(role -> expectedRole.equalsIgnoreCase(role));
    }

    private static String rolesText(List<String> roles) {
        if (roles.isEmpty()) {
            return "Chưa có vai trò";
        }
        return String.join(", ", roles);
    }

    private static String primaryRoleText(List<String> roles, String adminType) {
        if (adminType != null && !adminType.isBlank()) {
            return "Admin " + adminType;
        }
        if (hasRole(roles, "BIDDER_SELLER")) {
            return "Bidder / Seller";
        }
        if (hasRole(roles, "SELLER")) {
            return "Seller";
        }
        if (hasRole(roles, "BIDDER")) {
            return "Bidder";
        }
        return "User";
    }

    private static String accountStatusText(String status) {
        if (status == null || status.isBlank()) {
            return "--";
        }

        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> "Đang hoạt động";
            case "SUSPENDED" -> "Tạm khóa";
            case "BANNED" -> "Bị cấm";
            case "DELETED" -> "Đã xóa";
            default -> status;
        };
    }

    private static boolean canRequestSellerRole(
            UserDTO dto, List<String> roles, boolean admin, boolean seller) {
        if (dto == null || admin || seller || dto.isHasEverBeenPenalized()) {
            return false;
        }

        String status = dto.getAccountStatus();
        return status != null && status.equalsIgnoreCase("ACTIVE");
    }

    private static String formatMoney(long amount) {
        return CurrencyUtil.formatVnd(BigDecimal.valueOf(amount));
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "--" : value;
    }
}