package com.group13.auction.network.client.support;

import com.group13.auction.common.protocol.PacketType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bảng ánh xạ request packet sang response packet tương ứng.
 *
 * <p>Lớp này giúp toàn bộ client chỉ khai báo cặp success/failure ở một nơi, tránh copy-paste trong
 * service/controller.
 */
public final class PacketExpectations {

  private static final Map<PacketType, PacketExpectation> EXPECTATIONS = buildExpectations();

  private PacketExpectations() {
    // Utility class.
  }

  /**
   * Tìm expectation theo request type.
   *
   * @param requestType loại request client gửi lên server
   * @return expectation nếu request có response cố định
   */
  public static Optional<PacketExpectation> find(PacketType requestType) {
    return Optional.ofNullable(EXPECTATIONS.get(requestType));
  }

  /**
   * Lấy expectation bắt buộc; ném lỗi nếu request chưa được khai báo.
   *
   * @param requestType loại request client gửi lên server
   * @return expectation tương ứng
   */
  public static PacketExpectation require(PacketType requestType) {
    return find(requestType)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Chưa khai báo response expectation cho request: " + requestType));
  }

  private static Map<PacketType, PacketExpectation> buildExpectations() {
    Map<PacketType, PacketExpectation> map = new EnumMap<>(PacketType.class);

    put(map, PacketType.REGISTER, PacketType.REGISTER_SUCCESS, PacketType.REGISTER_FAILED);
    put(map, PacketType.LOGIN, PacketType.LOGIN_SUCCESS, PacketType.LOGIN_FAILED);
    successOnly(map, PacketType.LOGOUT, PacketType.LOGOUT_SUCCESS);

    successOnly(map, PacketType.GET_MY_PROFILE, PacketType.GET_MY_PROFILE_SUCCESS);
    put(
        map,
        PacketType.GET_USER_PROFILE,
        PacketType.GET_USER_PROFILE_SUCCESS,
        PacketType.GET_USER_PROFILE_FAILED);
    put(
        map,
        PacketType.REQUEST_SELLER_ROLE,
        PacketType.REQUEST_SELLER_ROLE_SUCCESS,
        PacketType.REQUEST_SELLER_ROLE_FAILED);

    put(map, PacketType.DEPOSIT, PacketType.DEPOSIT_SUCCESS, PacketType.DEPOSIT_FAILED);
    put(map, PacketType.WITHDRAW, PacketType.WITHDRAW_SUCCESS, PacketType.WITHDRAW_FAILED);
    successOnly(map, PacketType.GET_WALLET_BALANCE, PacketType.GET_WALLET_BALANCE_SUCCESS);
    put(map, PacketType.PAYMENT_REQUEST, PacketType.PAYMENT_SUCCESS, PacketType.PAYMENT_FAILED);
    put(
        map,
        PacketType.CONFIRM_ITEM_RECEIVED,
        PacketType.CONFIRM_ITEM_RECEIVED_SUCCESS,
        PacketType.CONFIRM_ITEM_RECEIVED_FAILED);
    put(
        map,
        PacketType.SECOND_CHANCE_ACCEPT,
        PacketType.SECOND_CHANCE_ACCEPT_SUCCESS,
        PacketType.SECOND_CHANCE_ACCEPT_FAILED);
    successOnly(map, PacketType.SECOND_CHANCE_DECLINE, PacketType.SECOND_CHANCE_DECLINE_SUCCESS);
    put(
        map,
        PacketType.GET_MY_SECOND_CHANCE_OFFERS,
        PacketType.GET_MY_SECOND_CHANCE_OFFERS_SUCCESS,
        PacketType.GET_MY_SECOND_CHANCE_OFFERS_FAILED);

    put(
        map,
        PacketType.CREATE_AUCTION,
        PacketType.CREATE_AUCTION_SUCCESS,
        PacketType.CREATE_AUCTION_FAILED);
    successOnly(map, PacketType.GET_AUCTION_LIST, PacketType.GET_AUCTION_LIST_SUCCESS);
    put(
        map,
        PacketType.SEARCH_ITEMS,
        PacketType.SEARCH_ITEMS_SUCCESS,
        PacketType.SEARCH_ITEMS_FAILED);
    put(
        map,
        PacketType.GET_AUCTION_DETAIL,
        PacketType.GET_AUCTION_DETAIL_SUCCESS,
        PacketType.GET_AUCTION_DETAIL_FAILED);
    put(
        map,
        PacketType.UPDATE_AUCTION,
        PacketType.UPDATE_AUCTION_SUCCESS,
        PacketType.UPDATE_AUCTION_FAILED);
    put(
        map,
        PacketType.CANCEL_AUCTION_REQUEST,
        PacketType.CANCEL_AUCTION_REQUEST_SUCCESS,
        PacketType.CANCEL_AUCTION_REQUEST_FAILED);
    put(
        map,
        PacketType.ADMIN_CANCEL_AUCTION,
        PacketType.ADMIN_CANCEL_AUCTION_SUCCESS,
        PacketType.ADMIN_CANCEL_AUCTION_FAILED);
    successOnly(map, PacketType.ADMIN_GET_ALL_AUCTIONS, PacketType.ADMIN_GET_ALL_AUCTIONS_SUCCESS);
    put(
        map,
        PacketType.ADMIN_GET_SYSTEM_BANK_SUMMARY,
        PacketType.ADMIN_GET_SYSTEM_BANK_SUMMARY_SUCCESS,
        PacketType.ADMIN_GET_SYSTEM_BANK_SUMMARY_FAILED);
    put(
        map,
        PacketType.ADMIN_GET_FINANCIAL_TRANSACTIONS,
        PacketType.ADMIN_GET_FINANCIAL_TRANSACTIONS_SUCCESS,
        PacketType.ADMIN_GET_FINANCIAL_TRANSACTIONS_FAILED);

    put(
        map,
        PacketType.JOIN_AUCTION,
        PacketType.JOIN_AUCTION_SUCCESS,
        PacketType.JOIN_AUCTION_FAILED);
    put(
        map,
        PacketType.WATCH_AUCTION,
        PacketType.WATCH_AUCTION_SUCCESS,
        PacketType.WATCH_AUCTION_FAILED);
    successOnly(map, PacketType.LEAVE_AUCTION, PacketType.LEAVE_AUCTION_SUCCESS);
    put(map, PacketType.PLACE_BID, PacketType.PLACE_BID_SUCCESS, PacketType.PLACE_BID_FAILED);
    put(
        map,
        PacketType.REGISTER_AUTO_BID,
        PacketType.REGISTER_AUTO_BID_SUCCESS,
        PacketType.REGISTER_AUTO_BID_FAILED);
    put(
        map,
        PacketType.UPDATE_AUTO_BID,
        PacketType.UPDATE_AUTO_BID_SUCCESS,
        PacketType.UPDATE_AUTO_BID_FAILED);
    put(
        map,
        PacketType.CANCEL_AUTO_BID,
        PacketType.CANCEL_AUTO_BID_SUCCESS,
        PacketType.CANCEL_AUTO_BID_FAILED);
    successOnly(map, PacketType.GET_AUTO_BID_STATUS, PacketType.GET_AUTO_BID_STATUS_SUCCESS);
    put(
        map,
        PacketType.GET_BID_HISTORY,
        PacketType.GET_BID_HISTORY_SUCCESS,
        PacketType.GET_BID_HISTORY_FAILED);

    put(
        map,
        PacketType.ADMIN_BAN_USER,
        PacketType.ADMIN_BAN_USER_SUCCESS,
        PacketType.ADMIN_BAN_USER_FAILED);
    put(
        map,
        PacketType.ADMIN_UNBAN_USER,
        PacketType.ADMIN_UNBAN_USER_SUCCESS,
        PacketType.ADMIN_UNBAN_USER_FAILED);
    successOnly(map, PacketType.ADMIN_GET_ALL_USERS, PacketType.ADMIN_GET_ALL_USERS_SUCCESS);
    successOnly(map, PacketType.ADMIN_GET_ACCOUNT_BANS, PacketType.ADMIN_GET_ACCOUNT_BANS_SUCCESS);
    put(
        map,
        PacketType.ADMIN_CREATE_STAFF,
        PacketType.ADMIN_CREATE_STAFF_SUCCESS,
        PacketType.ADMIN_CREATE_STAFF_FAILED);
    successOnly(map, PacketType.ADMIN_GET_ALL_STAFF, PacketType.ADMIN_GET_ALL_STAFF_SUCCESS);
    put(
        map,
        PacketType.ADMIN_APPROVE_SELLER_ROLE,
        PacketType.ADMIN_APPROVE_SELLER_ROLE_SUCCESS,
        PacketType.ADMIN_APPROVE_SELLER_ROLE_FAILED);

    put(map, PacketType.RATE_SELLER, PacketType.RATE_SELLER_SUCCESS, PacketType.RATE_SELLER_FAILED);
    put(map, PacketType.RATE_BIDDER, PacketType.RATE_BIDDER_SUCCESS, PacketType.RATE_BIDDER_FAILED);
    successOnly(map, PacketType.GET_USER_RATINGS, PacketType.GET_USER_RATINGS_SUCCESS);

    put(
        map,
        PacketType.SUBMIT_QUALITY_REPORT,
        PacketType.SUBMIT_QUALITY_REPORT_SUCCESS,
        PacketType.SUBMIT_QUALITY_REPORT_FAILED);
    put(
        map,
        PacketType.GET_MY_QUALITY_REPORTS,
        PacketType.GET_MY_QUALITY_REPORTS_SUCCESS,
        PacketType.GET_MY_QUALITY_REPORTS_FAILED);
    put(
        map,
        PacketType.GET_SELLER_QUALITY_REPORTS,
        PacketType.GET_SELLER_QUALITY_REPORTS_SUCCESS,
        PacketType.GET_SELLER_QUALITY_REPORTS_FAILED);
    successOnly(
        map, PacketType.ADMIN_GET_QUALITY_REPORTS, PacketType.ADMIN_GET_QUALITY_REPORTS_SUCCESS);
    put(
        map,
        PacketType.ADMIN_APPROVE_QUALITY_REPORT,
        PacketType.ADMIN_APPROVE_QUALITY_REPORT_SUCCESS,
        PacketType.ADMIN_APPROVE_QUALITY_REPORT_FAILED);
    successOnly(
        map,
        PacketType.ADMIN_REJECT_QUALITY_REPORT,
        PacketType.ADMIN_REJECT_QUALITY_REPORT_SUCCESS);

    successOnly(map, PacketType.GET_NOTIFICATIONS, PacketType.GET_NOTIFICATIONS_SUCCESS);
    successOnly(map, PacketType.MARK_NOTIFICATION_READ, PacketType.MARK_NOTIFICATION_READ_SUCCESS);
    successOnly(map, PacketType.PING, PacketType.PONG);

    put(map, PacketType.CHATBOT_ASK, PacketType.CHATBOT_ANSWER, PacketType.CHATBOT_NOT_FOUND);
    successOnly(map, PacketType.CHATBOT_GET_FAQ_LIST, PacketType.CHATBOT_FAQ_LIST_SUCCESS);

    return Map.copyOf(map);
  }

  private static void put(
      Map<PacketType, PacketExpectation> map,
      PacketType requestType,
      PacketType successType,
      PacketType failureType) {
    map.put(requestType, PacketExpectation.of(requestType, successType, failureType));
  }

  private static void successOnly(
      Map<PacketType, PacketExpectation> map, PacketType requestType, PacketType successType) {
    map.put(requestType, PacketExpectation.successOnly(requestType, successType));
  }
}