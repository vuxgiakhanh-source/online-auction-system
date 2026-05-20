package com.group13.auction.viewmodel.profile;

/** Dữ liệu hồ sơ người dùng đã được format để hiển thị trên giao diện JavaFX. */
public final class UserProfileViewModel {

    private final String userId;
    private final String username;
    private final String email;
    private final String rolesText;
    private final String primaryRoleText;
    private final String accountStatusText;
    private final String ratingText;
    private final String balanceText;
    private final String lockedDepositText;
    private final String availableBalanceText;
    private final String createdAtText;
    private final String updatedAtText;
    private final boolean bidder;
    private final boolean seller;
    private final boolean admin;
    private final boolean canRequestSellerRole;
    private final boolean penalized;

    /**
     * Tạo view model cho hồ sơ người dùng.
     *
     * @param userId id người dùng
     * @param username tên đăng nhập
     * @param email email
     * @param rolesText danh sách role đã format
     * @param primaryRoleText role chính để hiển thị
     * @param accountStatusText trạng thái tài khoản đã format
     * @param ratingText điểm đánh giá đã format
     * @param balanceText số dư đã format
     * @param lockedDepositText đặt cọc đang khóa đã format
     * @param availableBalanceText số dư khả dụng đã format
     * @param createdAtText ngày tạo tài khoản đã format
     * @param updatedAtText thời điểm cập nhật đã format
     * @param bidder có quyền bidder hay không
     * @param seller có quyền seller hay không
     * @param admin có quyền admin hay không
     * @param canRequestSellerRole có thể gửi yêu cầu nâng cấp seller hay không
     * @param penalized đã từng bị phạt hay chưa
     */
    public UserProfileViewModel(
            String userId,
            String username,
            String email,
            String rolesText,
            String primaryRoleText,
            String accountStatusText,
            String ratingText,
            String balanceText,
            String lockedDepositText,
            String availableBalanceText,
            String createdAtText,
            String updatedAtText,
            boolean bidder,
            boolean seller,
            boolean admin,
            boolean canRequestSellerRole,
            boolean penalized) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.rolesText = rolesText;
        this.primaryRoleText = primaryRoleText;
        this.accountStatusText = accountStatusText;
        this.ratingText = ratingText;
        this.balanceText = balanceText;
        this.lockedDepositText = lockedDepositText;
        this.availableBalanceText = availableBalanceText;
        this.createdAtText = createdAtText;
        this.updatedAtText = updatedAtText;
        this.bidder = bidder;
        this.seller = seller;
        this.admin = admin;
        this.canRequestSellerRole = canRequestSellerRole;
        this.penalized = penalized;
    }

    public String userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String rolesText() {
        return rolesText;
    }

    public String primaryRoleText() {
        return primaryRoleText;
    }

    public String accountStatusText() {
        return accountStatusText;
    }

    public String ratingText() {
        return ratingText;
    }

    public String balanceText() {
        return balanceText;
    }

    public String lockedDepositText() {
        return lockedDepositText;
    }

    public String availableBalanceText() {
        return availableBalanceText;
    }

    public String createdAtText() {
        return createdAtText;
    }

    public String updatedAtText() {
        return updatedAtText;
    }

    public boolean bidder() {
        return bidder;
    }

    public boolean seller() {
        return seller;
    }

    public boolean admin() {
        return admin;
    }

    public boolean canRequestSellerRole() {
        return canRequestSellerRole;
    }

    public boolean penalized() {
        return penalized;
    }
}