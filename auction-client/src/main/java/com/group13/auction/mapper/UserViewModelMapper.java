package com.group13.auction.mapper;

import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.profile.UserProfileViewModel;
import com.group13.auction.viewmodel.admin.SellerApprovalViewModel;
import com.group13.auction.viewmodel.admin.UserModerationViewModel;
import com.group13.auction.viewmodel.admin.StaffAdminViewModel;
import java.util.Arrays;
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

    /**
     * Chuyển mảng user DTO sang danh sách view model quản lý người dùng.
     *
     * @param users mảng user DTO server trả về
     * @return danh sách user moderation view model
     */
    public static List<UserModerationViewModel> toModerationViewModels(UserDTO[] users) {
        if (users == null) {
            return List.of();
        }

        return Arrays.stream(users)
                .map(UserViewModelMapper::toModerationViewModel)
                .toList();
    }

    /**
     * Chuyển danh sách user DTO sang danh sách view model quản lý người dùng.
     *
     * @param users danh sách user DTO server trả về
     * @return danh sách user moderation view model
     */
    public static List<UserModerationViewModel> toModerationViewModels(List<UserDTO> users) {
        if (users == null) {
            return List.of();
        }

        return users.stream()
                .map(UserViewModelMapper::toModerationViewModel)
                .toList();
    }

    /**
     * Chuyển một user DTO sang view model quản lý người dùng.
     *
     * @param dto user DTO
     * @return user moderation view model
     */
    public static UserModerationViewModel toModerationViewModel(UserDTO dto) {
        if (dto == null) {
            return new UserModerationViewModel("--", "--", "--", "--", "--", false);
        }

        List<String> roles = safeRoles(dto.getRoles());
        String status = accountStatusText(dto.getAccountStatus());

        return new UserModerationViewModel(
                fallback(dto.getId()),
                fallback(dto.getUsername()),
                fallback(dto.getEmail()),
                rolesText(roles),
                status,
                isBanned(dto));
    }

    /**
     * Chuyển mảng user DTO sang danh sách Staff Admin view model.
     *
     * @param staffAdmins mảng Staff Admin DTO server trả về
     * @return danh sách view model Staff Admin
     */
    public static List<StaffAdminViewModel> toStaffAdminViewModels(UserDTO[] staffAdmins) {
        if (staffAdmins == null) {
            return List.of();
        }

        return Arrays.stream(staffAdmins)
            .map(UserViewModelMapper::toStaffAdminViewModel)
            .toList();
    }

    /**
     * Chuyển một user DTO sang view model Staff Admin.
     *
     * @param dto Staff Admin DTO
     * @return Staff Admin view model
     */
    public static StaffAdminViewModel toStaffAdminViewModel(UserDTO dto) {
        if (dto == null) {
            return new StaffAdminViewModel("--", "--", "--", "--", "--");
        }

        return new StaffAdminViewModel(
            fallback(dto.getId()),
            fallback(dto.getUsername()),
            fallback(dto.getEmail()),
            fallback(dto.getAdminType()),
            accountStatusText(dto.getAccountStatus()));
    }

    /**
     * Chuyển mảng user DTO sang danh sách candidate duyệt Seller.
     *
     * <p>Backend hiện có API approve seller role, nhưng chưa có API riêng để list pending seller
     * request. Vì vậy client lọc từ danh sách user hiện có và chỉ hiển thị candidate phù hợp.
     *
     * @param users mảng user DTO server trả về
     * @return danh sách seller approval view model
     */
    public static List<SellerApprovalViewModel> toSellerApprovalViewModels(UserDTO[] users) {
        if (users == null) {
            return List.of();
        }

        return Arrays.stream(users)
                .map(UserViewModelMapper::toSellerApprovalViewModel)
                .filter(SellerApprovalViewModel::isApprovable)
                .toList();
    }

    /**
     * Chuyển một user DTO sang candidate duyệt Seller.
     *
     * @param dto user DTO
     * @return seller approval view model
     */
    public static SellerApprovalViewModel toSellerApprovalViewModel(UserDTO dto) {
        if (dto == null) {
            return new SellerApprovalViewModel("--", "--", "--", "--", "--", false);
        }

        List<String> roles = safeRoles(dto.getRoles());
        boolean alreadySeller = hasRole(roles, "SELLER") || hasRole(roles, "BIDDER_SELLER");
        boolean admin = hasRole(roles, "ADMIN") || dto.getAdminType() != null;
        boolean active = dto.getAccountStatus() != null && dto.getAccountStatus().equalsIgnoreCase("ACTIVE");
        boolean approvable = !alreadySeller && !admin && active && !dto.isHasEverBeenPenalized();

        String note;
        if (alreadySeller) {
            note = "Người dùng đã có quyền Seller.";
        } else if (admin) {
            note = "Tài khoản Admin không cần duyệt Seller.";
        } else if (!active) {
            note = "Chỉ tài khoản ACTIVE mới có thể duyệt Seller.";
        } else if (dto.isHasEverBeenPenalized()) {
            note = "Tài khoản từng bị phạt, không auto-approve Seller.";
        } else {
            note = "Có thể duyệt quyền Seller bằng API hiện có.";
        }

        return new SellerApprovalViewModel(
                fallback(dto.getId()),
                fallback(dto.getUsername()),
                fallback(dto.getEmail()),
                rolesText(roles),
                note,
                approvable);
    }

    private static boolean isBanned(UserDTO dto) {
        return dto != null
                && dto.getAccountStatus() != null
                && dto.getAccountStatus().equalsIgnoreCase("BANNED");
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