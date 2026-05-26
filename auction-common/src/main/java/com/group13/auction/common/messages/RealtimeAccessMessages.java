package com.group13.auction.common.messages;

/**
 * Thông điệp realtime / bảo mật phiên dùng chung client–server (tiếng Việt).
 */
public final class RealtimeAccessMessages {

    private RealtimeAccessMessages() {}

    /**
     * Tài khoản BANNED/SUSPENDED đã đăng nhập ở chế độ chỉ ví — packet nghiệp vụ bị chặn.
     */
    public static String restrictedAccountDenial() {
        return "Tài khoản của bạn đang bị hạn chế. Chỉ có thể sử dụng các dịch vụ ví.";
    }

    /**
     * Alias cho lỗi realtime khi user bị cấm thao tác phiên đấu giá.
     */
    public static String bannedFromAuctionFeature() {
        return "Bạn đã bị cấm truy cập tính năng này.";
    }
}
