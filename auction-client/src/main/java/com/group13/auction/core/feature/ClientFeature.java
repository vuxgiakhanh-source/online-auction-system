package com.group13.auction.core.feature;

import com.group13.auction.common.protocol.PacketType;
import java.util.EnumSet;
import java.util.Set;

/**
 * Các module nghiệp vụ phía client — mỗi module ánh xạ 1 nhóm {@link PacketType} trên server.
 */
public enum ClientFeature {

    AUTH(
            "Xác thực",
            PacketType.REGISTER, PacketType.LOGIN, PacketType.LOGOUT),

    PROFILE(
            "Hồ sơ & nâng cấp Seller",
            PacketType.GET_MY_PROFILE,
            PacketType.GET_USER_PROFILE,
            PacketType.REQUEST_SELLER_ROLE),

    WALLET(
            "Ví & giao dịch",
            PacketType.DEPOSIT,
            PacketType.WITHDRAW,
            PacketType.GET_WALLET_BALANCE),

    AUCTION_MANAGEMENT(
            "Quản lý phiên đấu giá",
            PacketType.CREATE_AUCTION,
            PacketType.GET_AUCTION_LIST,
            PacketType.GET_AUCTION_DETAIL,
            PacketType.UPDATE_AUCTION,
            PacketType.CANCEL_AUCTION_REQUEST),

    LIVE_BIDDING(
            "Đấu giá trực tiếp",
            PacketType.JOIN_AUCTION,
            PacketType.WATCH_AUCTION,
            PacketType.LEAVE_AUCTION,
            PacketType.PLACE_BID,
            PacketType.GET_BID_HISTORY),

    AUTO_BID(
            "Đấu giá tự động",
            PacketType.REGISTER_AUTO_BID,
            PacketType.UPDATE_AUTO_BID,
            PacketType.CANCEL_AUTO_BID,
            PacketType.GET_AUTO_BID_STATUS),

    PAYMENT(
            "Thanh toán sau đấu giá",
            PacketType.PAYMENT_REQUEST,
            PacketType.SECOND_CHANCE_ACCEPT,
            PacketType.SECOND_CHANCE_DECLINE),

    RATING(
            "Đánh giá",
            PacketType.RATE_SELLER,
            PacketType.RATE_BIDDER,
            PacketType.GET_USER_RATINGS),

    QUALITY_REPORT(
            "Báo cáo chất lượng",
            PacketType.SUBMIT_QUALITY_REPORT),

    ADMIN(
            "Quản trị",
            PacketType.ADMIN_BAN_USER,
            PacketType.ADMIN_UNBAN_USER,
            PacketType.ADMIN_GET_ALL_USERS,
            PacketType.ADMIN_CREATE_STAFF,
            PacketType.ADMIN_GET_ALL_STAFF,
            PacketType.ADMIN_APPROVE_SELLER_ROLE,
            PacketType.ADMIN_CANCEL_AUCTION,
            PacketType.ADMIN_GET_ALL_AUCTIONS,
            PacketType.ADMIN_GET_QUALITY_REPORTS,
            PacketType.ADMIN_APPROVE_QUALITY_REPORT,
            PacketType.ADMIN_REJECT_QUALITY_REPORT),

    NOTIFICATION(
            "Thông báo",
            PacketType.GET_NOTIFICATIONS,
            PacketType.MARK_NOTIFICATION_READ),

    CHATBOT(
            "Hỗ trợ FAQ",
            PacketType.CHATBOT_ASK,
            PacketType.CHATBOT_GET_FAQ_LIST),

    SYSTEM(
            "Hệ thống",
            PacketType.PING,
            PacketType.PONG);

    private final String displayName;
    private final Set<PacketType> requestPackets;

    ClientFeature(String displayName, PacketType... requests) {
        this.displayName = displayName;
        this.requestPackets = EnumSet.copyOf(Set.of(requests));
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<PacketType> getRequestPackets() {
        return requestPackets;
    }
}
