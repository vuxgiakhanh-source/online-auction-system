package com.group13.auction.core.workflow;

import com.group13.auction.core.feature.ClientFeature;
import com.group13.auction.core.navigation.Route;

/**
 * Mô tả luồng nghiệp vụ end-to-end của hệ thống và route client tương ứng.
 *
 * <pre>
 *  [Khách] → LANDING → LOGIN/REGISTER → MAIN_LAYOUT
 *      → AUCTION_LIST → AUCTION_DETAIL → (JOIN|WATCH) → LIVE_BIDDING
 *      → (thắng) → PAYMENT → RATE / QUALITY_REPORT
 *  [Seller] → SELLER_DASHBOARD → CREATE/EDIT → CANCEL_REQUEST
 *  [Admin]  → ADMIN_* screens
 * </pre>
 */
public final class SystemWorkflow {

    private SystemWorkflow() {}

    /** Luồng đăng nhập / đăng ký. */
    public static final FlowStep AUTH_FLOW = new FlowStep(
            ClientFeature.AUTH,
            Route.LANDING,
            Route.LOGIN,
            Route.REGISTER,
            Route.MAIN_LAYOUT);

    /** Luồng bidder: duyệt → tham gia → đấu giá realtime. */
    public static final FlowStep BIDDER_AUCTION_FLOW = new FlowStep(
            ClientFeature.LIVE_BIDDING,
            Route.AUCTION_LIST,
            Route.AUCTION_DETAIL,
            Route.LIVE_BIDDING);

    /** Luồng thanh toán sau khi thắng phiên. */
    public static final FlowStep WINNER_PAYMENT_FLOW = new FlowStep(
            ClientFeature.PAYMENT,
            Route.PAYMENT,
            Route.RATING_SELLER);

    /** Luồng second chance cho runner-up. */
    public static final FlowStep SECOND_CHANCE_FLOW = new FlowStep(
            ClientFeature.PAYMENT,
            Route.SECOND_CHANCE);

    /** Luồng seller quản lý phiên. */
    public static final FlowStep SELLER_FLOW = new FlowStep(
            ClientFeature.AUCTION_MANAGEMENT,
            Route.SELLER_DASHBOARD,
            Route.SELLER_AUCTION_LIST,
            Route.SELLER_CREATE_AUCTION,
            Route.SELLER_EDIT_AUCTION,
            Route.SELLER_AUCTION_DETAIL);

    /** Luồng admin moderation. */
    public static final FlowStep ADMIN_FLOW = new FlowStep(
            ClientFeature.ADMIN,
            Route.ADMIN_DASHBOARD,
            Route.ADMIN_USERS,
            Route.ADMIN_AUCTIONS,
            Route.ADMIN_SELLER_APPROVALS,
            Route.ADMIN_QUALITY_REPORTS);

    /**
     * Một chuỗi màn hình trong cùng module nghiệp vụ.
     *
     * @param feature module
     * @param routes  thứ tự màn hình gợi ý
     */
    public record FlowStep(ClientFeature feature, Route... routes) {}
}
