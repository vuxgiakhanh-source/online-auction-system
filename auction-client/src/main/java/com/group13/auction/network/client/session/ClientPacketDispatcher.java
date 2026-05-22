package com.group13.auction.network.client.session;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.group13.auction.common.dto.chatbot.ChatbotDTOs;
import com.group13.auction.common.dto.admin.AdminDTOs;
import com.google.gson.JsonObject;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.payment.ConfirmItemReceivedResultDTO;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.client.handler.ServerResponseHandler;

import java.util.List;
import java.util.logging.Logger;

/**
 * Dispatcher phía Client: nhận raw packet từ {@link com.group13.auction.network.client.AuctionWebSocketClient},
 * deserialize payload và gọi đúng method của {@link ClientEventListener}.
 *
 * <p>Tách biệt tầng network (decode) khỏi tầng UI (xử lý event).
 * Controller implement {@link ClientEventListener} và đăng ký vào dispatcher.
 *
 * <p>Cách dùng:
 * <pre>
 *   ClientPacketDispatcher dispatcher = new ClientPacketDispatcher();
 *   dispatcher.addListener(myController);
 *   client.addHandler(dispatcher);   // dispatcher implement ServerResponseHandler
 * </pre>
 */
public class ClientPacketDispatcher implements ServerResponseHandler {

    private static final Logger log = Logger.getLogger(ClientPacketDispatcher.class.getName());

    /** Listener nhận event đã được typed. */
    private final java.util.List<ClientEventListener> listeners =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addListener(ClientEventListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(ClientEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onPacketReceived(PacketType type, JsonElement payload, String requestId) {
        try {
            dispatch(type, payload, requestId);
        } catch (Exception e) {
            log.warning("[DISPATCHER] Error dispatching " + type + ": " + e.getMessage());
        }
    }

    private void dispatch(PacketType type, JsonElement payload, String requestId) {
        switch (type) {
            // ── AUTH ──────────────────────────────────────────────────────────
            case LOGIN_SUCCESS -> {
                var resp = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.auth.LoginResponseDTO.class);
                listeners.forEach(l -> l.onLoginSuccess(resp));
            }
            case LOGIN_FAILED -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onLoginFailed(err));
            }
            case REGISTER_SUCCESS -> {
                var resp = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.auth.LoginResponseDTO.class);
                listeners.forEach(l -> l.onRegisterSuccess(resp));
            }
            case REGISTER_FAILED -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onRegisterFailed(err));
            }
            case LOGOUT_SUCCESS -> listeners.forEach(ClientEventListener::onLogoutSuccess);

            // ── AUCTION LIST / DETAIL ─────────────────────────────────────────
            case GET_AUCTION_LIST_SUCCESS -> {
                var list = PacketCodec.fromElement(payload, AuctionDTOs.AuctionListDTO.class);
                listeners.forEach(l -> l.onAuctionListReceived(list));
            }
            case GET_AUCTION_DETAIL_SUCCESS -> {
                var auction = PacketCodec.fromElement(payload, AuctionDTOs.AuctionDTO.class);
                listeners.forEach(l -> l.onAuctionDetailReceived(auction));
            }
            case CREATE_AUCTION_SUCCESS -> {
                var auction = PacketCodec.fromElement(payload, AuctionDTOs.AuctionDTO.class);
                listeners.forEach(l -> l.onAuctionCreated(auction));
            }
            case CREATE_AUCTION_FAILED -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onAuctionCreateFailed(err));
            }

            // ── JOIN / WATCH ──────────────────────────────────────────────────
            case JOIN_AUCTION_SUCCESS -> {
                var resp = PacketCodec.fromElement(payload, AuctionDTOs.JoinAuctionResponseDTO.class);
                listeners.forEach(l -> l.onJoinAuctionSuccess(resp));
            }
            case JOIN_AUCTION_FAILED -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onJoinAuctionFailed(err));
            }
            case WATCH_AUCTION_SUCCESS -> {
                var auction = PacketCodec.fromElement(payload, AuctionDTOs.AuctionDTO.class);
                listeners.forEach(l -> l.onWatchAuctionSuccess(auction));
            }

            // ── BID REALTIME ──────────────────────────────────────────────────
            case BID_UPDATE -> {
                var update = PacketCodec.fromElement(payload, BidDTOs.BidUpdateDTO.class);
                listeners.forEach(l -> l.onBidUpdate(update));
            }
            case BID_RESERVE_NOT_MET_UPDATE -> {
                var update = PacketCodec.fromElement(payload, BidDTOs.BidUpdateDTO.class);
                listeners.forEach(l -> l.onBidReserveNotMet(update));
            }
            case PLACE_BID_SUCCESS -> {
                var result = PacketCodec.fromElement(payload, BidDTOs.BidResultDTO.class);
                listeners.forEach(l -> l.onPlaceBidSuccess(result));
            }
            case PLACE_BID_FAILED -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onPlaceBidFailed(err));
            }
            case BID_CHART_POINT_UPDATE -> {
                var point = PacketCodec.fromElement(payload, BidDTOs.BidChartPointDTO.class);
                listeners.forEach(l -> l.onBidChartPointUpdate(point));
            }
            case GET_BID_HISTORY_SUCCESS -> {
                var history = PacketCodec.fromElement(payload, BidDTOs.BidHistoryResponseDTO.class);
                listeners.forEach(l -> l.onBidHistoryReceived(history));
            }

            // ── AUTO-BID ──────────────────────────────────────────────────────
            case REGISTER_AUTO_BID_SUCCESS -> {
                var reg = PacketCodec.fromElement(payload, BidDTOs.AutoBidRegistrationDTO.class);
                listeners.forEach(l -> l.onAutoBidRegistered(reg));
            }
            case REGISTER_AUTO_BID_FAILED -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onAutoBidFailed(err));
            }
            case AUTO_BID_TRIGGERED_NOTIFY -> {
                var notify = PacketCodec.fromElement(payload, BidDTOs.AutoBidTriggeredDTO.class);
                listeners.forEach(l -> l.onAutoBidTriggered(notify));
            }
            case AUTO_BID_EXHAUSTED_NOTIFY -> {
                var notify = PacketCodec.fromElement(payload, BidDTOs.AutoBidExhaustedDTO.class);
                listeners.forEach(l -> l.onAutoBidExhausted(notify));
            }

            // ── AUCTION LIFECYCLE ─────────────────────────────────────────────
            case AUCTION_STARTED_UPDATE -> {
                var update = PacketCodec.fromElement(payload, AuctionDTOs.AuctionUpdateDTO.class);
                listeners.forEach(l -> l.onAuctionStarted(update));
            }
            case AUCTION_ENDED_UPDATE -> {
                var update = PacketCodec.fromElement(payload, AuctionDTOs.AuctionUpdateDTO.class);
                listeners.forEach(l -> l.onAuctionEnded(update));
            }
            case AUCTION_NO_WINNER_UPDATE -> {
                var update = PacketCodec.fromElement(payload, AuctionDTOs.AuctionUpdateDTO.class);
                listeners.forEach(l -> l.onAuctionNoWinner(update));
            }
            case AUCTION_RESERVE_NOT_MET_UPDATE -> {
                var update = PacketCodec.fromElement(payload, AuctionDTOs.AuctionUpdateDTO.class);
                listeners.forEach(l -> l.onAuctionReserveNotMet(update));
            }
            case AUCTION_CANCELED_UPDATE -> {
                var update = PacketCodec.fromElement(payload, AuctionDTOs.AuctionUpdateDTO.class);
                listeners.forEach(l -> l.onAuctionCanceled(update));
            }
            case AUCTION_EXTENDED_NOTIFY -> {
                var ext = PacketCodec.fromElement(payload, AuctionDTOs.AuctionExtendedDTO.class);
                listeners.forEach(l -> l.onAuctionExtended(ext));
            }
            case AUCTION_UPCOMING_END_NOTIFY -> {
                var upcoming = PacketCodec.fromElement(payload, AuctionDTOs.AuctionUpcomingEndDTO.class);
                listeners.forEach(l -> l.onAuctionUpcomingEnd(upcoming));
            }

            // ── PAYMENT ───────────────────────────────────────────────────────
            case DEPOSIT_SUCCESS -> {
                var resp = PacketCodec.fromElement(payload, PaymentDTOs.WalletBalanceResponseDTO.class);
                listeners.forEach(l -> l.onDepositSuccess(resp));
            }
            case DEPOSIT_FAILED -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onDepositFailed(err));
            }
            case WITHDRAW_SUCCESS -> {
                var resp = PacketCodec.fromElement(payload, PaymentDTOs.WalletBalanceResponseDTO.class);
                listeners.forEach(l -> l.onWithdrawSuccess(resp));
            }
            case WITHDRAW_FAILED -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onWithdrawFailed(err));
            }
            case GET_WALLET_BALANCE_SUCCESS -> {
                var resp = PacketCodec.fromElement(payload, PaymentDTOs.WalletBalanceResponseDTO.class);
                listeners.forEach(l -> l.onWalletBalanceReceived(resp));
            }
            case PAYMENT_SUCCESS -> {
                var result = PacketCodec.fromElement(payload, PaymentDTOs.PaymentResultDTO.class);
                listeners.forEach(l -> l.onPaymentSuccess(result));
            }
            case PAYMENT_FAILED -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onPaymentFailed(err));
            }
            case PAYMENT_COMPLETED_NOTIFY -> {
                var result = PacketCodec.fromElement(payload, PaymentDTOs.PaymentResultDTO.class);
                listeners.forEach(l -> l.onPaymentCompletedNotify(result));
            }
            case CONFIRM_ITEM_RECEIVED_SUCCESS -> {
                var result = PacketCodec.fromElement(payload, ConfirmItemReceivedResultDTO.class);
                listeners.forEach(l -> l.onItemReceivedConfirmed(result));
            }
            case CONFIRM_ITEM_RECEIVED_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onItemReceivedFailed(err));
            }
            case PAYMENT_EXPIRED_NOTIFY -> {
                var exp = PacketCodec.fromElement(payload, PaymentDTOs.PaymentExpiredDTO.class);
                listeners.forEach(l -> l.onPaymentExpiredNotify(exp));
            }
            case DEPOSIT_REFUND_NOTIFY -> {
                var refund = PacketCodec.fromElement(payload, PaymentDTOs.DepositRefundDTO.class);
                listeners.forEach(l -> l.onDepositRefundNotify(refund));
            }
            case DEPOSIT_FORFEITED_NOTIFY -> {
                var forfeit = PacketCodec.fromElement(payload, PaymentDTOs.DepositForfeitedDTO.class);
                listeners.forEach(l -> l.onDepositForfeitedNotify(forfeit));
            }
            case SECOND_CHANCE_OFFER_NOTIFY -> {
                var offer = PacketCodec.fromElement(payload, PaymentDTOs.SecondChanceOfferDTO.class);
                listeners.forEach(l -> l.onSecondChanceOffer(offer));
            }

            // ── USER / PROFILE ────────────────────────────────────────────────
            case GET_MY_PROFILE_SUCCESS, GET_USER_PROFILE_SUCCESS -> {
                var user = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.user.UserDTO.class);
                listeners.forEach(l -> l.onUserProfileReceived(user));
            }
            case SELLER_ROLE_APPROVED_NOTIFY -> {
                var user = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.user.UserDTO.class);
                listeners.forEach(l -> l.onSellerRoleApproved(user));
            }
            case SELLER_ROLE_REJECTED_NOTIFY -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onSellerRoleRejected(err));
            }
            case ACCOUNT_BANNED_NOTIFY -> {
                var banned = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.rating.RatingDTOs.AccountBannedDTO.class);
                listeners.forEach(l -> l.onAccountBanned(banned));
            }
            case ACCOUNT_SUSPENDED_NOTIFY -> {
                var suspended = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.rating.RatingDTOs.AccountSuspendedDTO.class);
                listeners.forEach(l -> l.onAccountSuspended(suspended));
            }
            case ACCOUNT_RESTORED_NOTIFY -> {
                var restored = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.rating.RatingDTOs.AccountRestoredDTO.class);
                listeners.forEach(l -> l.onAccountRestored(restored));
            }

            // ── SYSTEM ────────────────────────────────────────────────────────
            case SYSTEM_ERROR -> {
                var err = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.core.ErrorDTO.class);
                listeners.forEach(l -> l.onSystemError(err));
            }
            case SYSTEM_ANNOUNCEMENT -> {
                var ann = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.admin.AdminDTOs.SystemAnnouncementDTO.class);
                listeners.forEach(l -> l.onSystemAnnouncement(ann));
            }
            case SERVER_SHUTDOWN_NOTIFY -> {
                var shutdown = PacketCodec.fromElement(payload,
                    com.group13.auction.common.dto.admin.AdminDTOs.ServerShutdownDTO.class);
                listeners.forEach(l -> l.onServerShutdown(shutdown));
            }
            // FIX Bug #8: dispatch PONG → onPong(timestamp) thay vì rơi vào default silent log
            case PONG -> {
                long timestamp = payload != null && !payload.isJsonNull()
                    ? payload.getAsLong() : System.currentTimeMillis();
                listeners.forEach(l -> l.onPong(timestamp));
            }
            case CANCEL_AUTO_BID_SUCCESS -> {
                String auctionId = PacketCodec.fromElement(payload, String.class);
                listeners.forEach(l -> l.onCancelAutoBidSuccess(auctionId));
            }
            case CANCEL_AUTO_BID_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onAutoBidFailed(err)); // tái dùng method đã có
            }
            case GET_AUTO_BID_STATUS_SUCCESS -> {
                var dto = PacketCodec.fromElement(payload, BidDTOs.AutoBidRegistrationDTO.class);
                listeners.forEach(l -> l.onAutoBidRegistered(dto)); // tái dùng
            }
            case LEAVE_AUCTION_SUCCESS -> {
                // FIX: Parse penalty info trực tiếp từ JsonObject — không dùng LeaveAuctionResponseDTO.class
                // để tránh lỗi compile "cannot find symbol" khi auction-client build trước auction-common.
                listeners.forEach(l -> l.onLeaveAuctionSuccess());
                if (payload != null && !payload.isJsonNull() && payload.isJsonObject()) {
                    com.google.gson.JsonObject resp = payload.getAsJsonObject();
                    boolean forfeited = resp.has("depositForfeited")
                        && resp.get("depositForfeited").getAsBoolean();
                    if (forfeited) {
                        long amount    = resp.has("forfeitedAmount")
                            ? resp.get("forfeitedAmount").getAsLong() : 0L;
                        boolean penalized = resp.has("ratingPenalized")
                            && resp.get("ratingPenalized").getAsBoolean();
                        long balance   = resp.has("newAvailableBalance")
                            ? resp.get("newAvailableBalance").getAsLong() : 0L;
                        listeners.forEach(l ->
                            l.onLeaveAuctionPenalty(true, amount, penalized, balance));
                    }
                }
            }
            // VIEWER_COUNT_UPDATE removed — viewCount giờ là tổng lượt truy cập, không còn realtime broadcast
            case GET_BID_HISTORY_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onBidHistoryFailed(err));
            }
            case GET_AUCTION_DETAIL_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onAuctionDetailFailed(err));
            }
            case UPDATE_AUCTION_SUCCESS -> {
                var auction = PacketCodec.fromElement(payload, AuctionDTOs.AuctionDTO.class);
                listeners.forEach(l -> l.onAuctionUpdated(auction));
            }
            case UPDATE_AUCTION_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onAuctionUpdateFailed(err));
            }
            case CANCEL_AUCTION_REQUEST_SUCCESS -> {
                String auctionId = PacketCodec.fromElement(payload, String.class);
                listeners.forEach(l -> l.onCancelAuctionRequestSuccess(auctionId));
            }
            case CANCEL_AUCTION_REQUEST_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onCancelAuctionRequestFailed(err));
            }
            case SELLER_CANCEL_REQUEST_NOTIFY -> {
                var dto = PacketCodec.fromElement(payload, AuctionDTOs.SellerCancelRequestNotifyDTO.class);
                listeners.forEach(l -> l.onSellerCancelRequestNotify(dto));
            }
            case ADMIN_CANCEL_AUCTION_SUCCESS -> {
                var auction = PacketCodec.fromElement(payload, AuctionDTOs.AuctionDTO.class);
                listeners.forEach(l -> l.onAdminCancelAuctionSuccess(auction));
            }
            case ADMIN_CANCEL_AUCTION_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onAdminCancelAuctionFailed(err));
            }
            case ADMIN_GET_ALL_AUCTIONS_SUCCESS -> {
                var list = PacketCodec.fromElement(payload, AuctionDTOs.AuctionListDTO.class);
                listeners.forEach(l -> l.onAdminAllAuctionsReceived(list));
            }
            case WATCH_AUCTION_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onWatchAuctionFailed(err));
            }
            case UPDATE_AUTO_BID_SUCCESS -> {
                var dto = PacketCodec.fromElement(payload, BidDTOs.AutoBidRegistrationDTO.class);
                listeners.forEach(l -> l.onUpdateAutoBidSuccess(dto));
            }
            case UPDATE_AUTO_BID_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onUpdateAutoBidFailed(err));
            }
            case SECOND_CHANCE_ACCEPT_SUCCESS -> {
                var result = PacketCodec.fromElement(payload, PaymentDTOs.PaymentResultDTO.class);
                listeners.forEach(l -> l.onSecondChanceAcceptSuccess(result));
            }
            case SECOND_CHANCE_ACCEPT_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onSecondChanceAcceptFailed(err));
            }
            case SECOND_CHANCE_DECLINE_SUCCESS ->
                listeners.forEach(l -> l.onSecondChanceDeclineSuccess());
            case SECOND_CHANCE_EXPIRED_NOTIFY -> {
                String auctionId = PacketCodec.fromElement(payload, String.class);
                listeners.forEach(l -> l.onSecondChanceExpiredNotify(auctionId));
            }
            case GET_USER_PROFILE_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onUserProfileFailed(err));
            }
            case REQUEST_SELLER_ROLE_SUCCESS ->
                listeners.forEach(l -> l.onRequestSellerRoleSuccess());
            case REQUEST_SELLER_ROLE_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onRequestSellerRoleFailed(err));
            }
            case ADMIN_BAN_USER_SUCCESS -> {
                var user = PacketCodec.fromElement(payload, UserDTO.class);
                listeners.forEach(l -> l.onAdminBanUserSuccess(user));
            }
            case ADMIN_BAN_USER_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onAdminBanUserFailed(err));
            }
            case ADMIN_UNBAN_USER_SUCCESS -> {
                var user = PacketCodec.fromElement(payload, UserDTO.class);
                listeners.forEach(l -> l.onAdminUnbanUserSuccess(user));
            }
            case ADMIN_UNBAN_USER_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onAdminUnbanUserFailed(err));
            }
            case ADMIN_GET_ALL_USERS_SUCCESS -> {
                List<UserDTO> users = PacketCodec.gson().fromJson(payload,
                    new TypeToken<List<UserDTO>>() {}.getType());
                listeners.forEach(l -> l.onAdminAllUsersReceived(users));
            }
            case ADMIN_CREATE_STAFF_SUCCESS -> {
                var user = PacketCodec.fromElement(payload, UserDTO.class);
                listeners.forEach(l -> l.onAdminCreateStaffSuccess(user));
            }
            case ADMIN_CREATE_STAFF_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onAdminCreateStaffFailed(err));
            }
            case ADMIN_GET_ALL_STAFF_SUCCESS -> {
                List<UserDTO> staff = PacketCodec.gson().fromJson(payload,
                    new TypeToken<List<UserDTO>>() {}.getType());
                listeners.forEach(l -> l.onAdminAllStaffReceived(staff));
            }
            case ADMIN_APPROVE_SELLER_ROLE_SUCCESS -> {
                var user = PacketCodec.fromElement(payload, UserDTO.class);
                listeners.forEach(l -> l.onAdminApproveSellerRoleSuccess(user));
            }
            case ADMIN_APPROVE_SELLER_ROLE_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onAdminApproveSellerRoleFailed(err));
            }
            case RATE_SELLER_SUCCESS -> listeners.forEach(l -> l.onRateSellerSuccess());
            case RATE_SELLER_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onRateSellerFailed(err));
            }
            case RATE_BIDDER_SUCCESS -> listeners.forEach(l -> l.onRateBidderSuccess());
            case RATE_BIDDER_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onRateBidderFailed(err));
            }
            case GET_USER_RATINGS_SUCCESS -> {
                var history = PacketCodec.fromElement(payload, RatingDTOs.RatingHistoryDTO.class);
                listeners.forEach(l -> l.onUserRatingsReceived(history));
            }
            case SUBMIT_QUALITY_REPORT_SUCCESS -> {
                var report = PacketCodec.fromElement(payload, ReportDTOs.QualityReportDTO.class);
                listeners.forEach(l -> l.onSubmitQualityReportSuccess(report));
            }
            case SUBMIT_QUALITY_REPORT_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onSubmitQualityReportFailed(err));
            }
            case ADMIN_GET_QUALITY_REPORTS_SUCCESS -> {
                List<ReportDTOs.QualityReportDTO> reports = PacketCodec.gson().fromJson(payload,
                    new TypeToken<List<ReportDTOs.QualityReportDTO>>() {}.getType());
                listeners.forEach(l -> l.onAdminQualityReportsReceived(reports));
            }
            case ADMIN_APPROVE_QUALITY_REPORT_SUCCESS -> {
                var result = PacketCodec.fromElement(payload, ReportDTOs.QualityReportResultDTO.class);
                listeners.forEach(l -> l.onAdminApproveQualityReportSuccess(result));
            }
            case ADMIN_APPROVE_QUALITY_REPORT_FAILED -> {
                var err = PacketCodec.fromElement(payload, ErrorDTO.class);
                listeners.forEach(l -> l.onAdminApproveQualityReportFailed(err));
            }
            case ADMIN_REJECT_QUALITY_REPORT_SUCCESS ->
                listeners.forEach(l -> l.onAdminRejectQualityReportSuccess());
            case QUALITY_REPORT_APPROVED_NOTIFY -> {
                var result = PacketCodec.fromElement(payload, ReportDTOs.QualityReportResultDTO.class);
                listeners.forEach(l -> l.onQualityReportApprovedNotify(result));
            }
            case QUALITY_REPORT_RECEIVED_NOTIFY -> {
                var report = PacketCodec.fromElement(payload, ReportDTOs.QualityReportDTO.class);
                listeners.forEach(l -> l.onQualityReportReceivedNotify(report));
            }
            case QUALITY_REPORT_REJECTED_NOTIFY -> {
                String reportId = PacketCodec.fromElement(payload, String.class);
                listeners.forEach(l -> l.onQualityReportRejectedNotify(reportId));
            }
            case SELLER_REFUND_OVERDUE_NOTIFY -> {
                String sellerId = PacketCodec.fromElement(payload, String.class);
                listeners.forEach(l -> l.onSellerRefundOverdueNotify(sellerId));
            }
            case FRAUD_DETECTED_NOTIFY -> {
                var fraud = PacketCodec.fromElement(payload, AdminDTOs.FraudDetectedDTO.class);
                listeners.forEach(l -> l.onFraudDetectedNotify(fraud));
            }
            case GET_NOTIFICATIONS_SUCCESS -> {
                List<AdminDTOs.NotificationDTO> notifications = PacketCodec.gson().fromJson(payload,
                    new TypeToken<List<AdminDTOs.NotificationDTO>>() {}.getType());
                listeners.forEach(l -> l.onNotificationsReceived(notifications));
            }
            case MARK_NOTIFICATION_READ_SUCCESS ->
                listeners.forEach(l -> l.onMarkNotificationReadSuccess());

            case CHATBOT_ANSWER, CHATBOT_NOT_FOUND -> {
                var response = PacketCodec.fromElement(payload, ChatbotDTOs.ChatbotResponseDTO.class);
                if (type == PacketType.CHATBOT_ANSWER) {
                    listeners.forEach(l -> l.onChatbotAnswer(response));
                } else {
                    listeners.forEach(l -> l.onChatbotNotFound(response));
                }
            }
            case CHATBOT_FAQ_LIST_SUCCESS -> {
                var list = parseChatbotFaqList(payload);
                listeners.forEach(l -> l.onChatbotFaqListReceived(list));
            }

            default -> log.fine("[DISPATCHER] Unhandled packet type: " + type);
        }
    }

    private static ChatbotDTOs.ChatbotFaqListResponseDTO parseChatbotFaqList(JsonElement payload) {
        if (payload == null || !payload.isJsonObject()) {
            return new ChatbotDTOs.ChatbotFaqListResponseDTO();
        }
        JsonObject root = payload.getAsJsonObject();
        ChatbotDTOs.ChatbotFaqListResponseDTO dto = new ChatbotDTOs.ChatbotFaqListResponseDTO();
        if (root.has("header")) {
            dto.setHeader(PacketCodec.fromElement(root.get("header"), ChatbotDTOs.ChatbotResponseDTO.class));
        }
        if (root.has("faqs")) {
            dto.setFaqs(PacketCodec.gson().fromJson(
                root.get("faqs"),
                new TypeToken<java.util.List<ChatbotDTOs.FaqSummaryDTO>>() {}.getType()));
        }
        if (root.has("totalCount")) {
            dto.setTotalCount(root.get("totalCount").getAsInt());
        }
        return dto;
    }
}