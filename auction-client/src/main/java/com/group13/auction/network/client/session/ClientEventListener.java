package com.group13.auction.network.client.session;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.auth.LoginResponseDTO;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.common.dto.user.UserDTO;

import java.util.List;

/**
 * Interface typed event cho phía Client.
 *
 * <p>JavaFX Controller chỉ cần implement các method mà màn hình đó quan tâm.
 * Tất cả method có {@code default} body rỗng để Controller không buộc phải
 * override toàn bộ.
 *
 * <p>Ví dụ — LoginController:
 * <pre>
 *   public class LoginController implements ClientEventListener {
 *
 *       &#64;Override
 *       public void onLoginSuccess(LoginResponseDTO resp) {
 *           Platform.runLater(() -> {
 *               App.setCurrentUser(resp.getUser());
 *               App.switchScene("auction-list.fxml");
 *           });
 *       }
 *
 *       &#64;Override
 *       public void onLoginFailed(ErrorDTO error) {
 *           Platform.runLater(() -> lblError.setText(error.getMessage()));
 *       }
 *   }
 * </pre>
 */
public interface ClientEventListener {

    // ── AUTH ──────────────────────────────────────────────────────────────────

    default void onLoginSuccess(LoginResponseDTO response) {}
    default void onLoginFailed(ErrorDTO error) {}
    default void onRegisterSuccess(LoginResponseDTO response) {}
    default void onRegisterFailed(ErrorDTO error) {}
    default void onLogoutSuccess() {}

    // ── USER / PROFILE ────────────────────────────────────────────────────────

    /** Nhận profile (cả GET_MY_PROFILE_SUCCESS và GET_USER_PROFILE_SUCCESS). */
    default void onUserProfileReceived(UserDTO user) {}
    /** Server từ chối GET_USER_PROFILE (user không tồn tại). */
    default void onUserProfileFailed(ErrorDTO error) {}
    /** Server push khi hệ thống tự động approve role Seller. */
    default void onSellerRoleApproved(UserDTO updatedUser) {}
    /** Server push khi hệ thống từ chối tự động duyệt role Seller. */
    default void onSellerRoleRejected(ErrorDTO error) {}
    /** Server xác nhận đã nhận đơn yêu cầu nâng cấp Seller (REQUEST_SELLER_ROLE_SUCCESS). */
    default void onRequestSellerRoleSuccess() {}
    /** Server từ chối yêu cầu nâng cấp Seller (REQUEST_SELLER_ROLE_FAILED). */
    default void onRequestSellerRoleFailed(ErrorDTO error) {}
    /** Server push thông báo khi tài khoản bị BAN (ACCOUNT_BANNED_NOTIFY). */
    default void onAccountBanned(RatingDTOs.AccountBannedDTO dto) {}
    /** Server push thông báo khi tài khoản bị SUSPEND (ACCOUNT_SUSPENDED_NOTIFY). */
    default void onAccountSuspended(RatingDTOs.AccountSuspendedDTO dto) {}
    /** Server push thông báo khi tài khoản SUSPENDED được khôi phục (ACCOUNT_RESTORED_NOTIFY). */
    default void onAccountRestored(RatingDTOs.AccountRestoredDTO dto) {}

    // ── AUCTION — List / Detail / Create / Update ─────────────────────────────

    default void onAuctionListReceived(AuctionDTOs.AuctionListDTO list) {}
    default void onAuctionDetailReceived(AuctionDTOs.AuctionDTO auction) {}
    /** Server từ chối GET_AUCTION_DETAIL (phiên không tồn tại). */
    default void onAuctionDetailFailed(ErrorDTO error) {}
    default void onAuctionCreated(AuctionDTOs.AuctionDTO auction) {}
    default void onAuctionCreateFailed(ErrorDTO error) {}
    /** Server xác nhận cập nhật phiên thành công (UPDATE_AUCTION_SUCCESS). */
    default void onAuctionUpdated(AuctionDTOs.AuctionDTO auction) {}
    /** Server từ chối cập nhật phiên (UPDATE_AUCTION_FAILED). */
    default void onAuctionUpdateFailed(ErrorDTO error) {}

    // ── AUCTION — Cancel request ──────────────────────────────────────────────

    /** Server xác nhận đã nhận đơn hủy phiên của Seller (CANCEL_AUCTION_REQUEST_SUCCESS). */
    default void onCancelAuctionRequestSuccess(String auctionId) {}
    /** Server từ chối đơn hủy phiên (CANCEL_AUCTION_REQUEST_FAILED). */
    default void onCancelAuctionRequestFailed(ErrorDTO error) {}
    /**
     * Server push cho Staff Admin khi Seller gửi yêu cầu hủy phiên
     * (SELLER_CANCEL_REQUEST_NOTIFY).
     */
    default void onSellerCancelRequestNotify(AuctionDTOs.SellerCancelRequestNotifyDTO dto) {}

    // ── AUCTION — Admin actions ───────────────────────────────────────────────

    /** Server xác nhận Admin đã hủy phiên thành công (ADMIN_CANCEL_AUCTION_SUCCESS). */
    default void onAdminCancelAuctionSuccess(AuctionDTOs.AuctionDTO auction) {}
    /** Server từ chối Admin hủy phiên (ADMIN_CANCEL_AUCTION_FAILED). */
    default void onAdminCancelAuctionFailed(ErrorDTO error) {}
    /** Server trả về danh sách tất cả phiên cho Admin (ADMIN_GET_ALL_AUCTIONS_SUCCESS). */
    default void onAdminAllAuctionsReceived(AuctionDTOs.AuctionListDTO list) {}

    // ── JOIN / WATCH / LEAVE ──────────────────────────────────────────────────

    default void onJoinAuctionSuccess(AuctionDTOs.JoinAuctionResponseDTO response) {}
    default void onJoinAuctionFailed(ErrorDTO error) {}
    default void onWatchAuctionSuccess(AuctionDTOs.AuctionDTO auction) {}
    /** Server từ chối watch (WATCH_AUCTION_FAILED). */
    default void onWatchAuctionFailed(ErrorDTO error) {}
    default void onLeaveAuctionSuccess() {}

    // ── AUCTION LIFECYCLE (realtime broadcast) ────────────────────────────────

    default void onAuctionStarted(AuctionDTOs.AuctionUpdateDTO update) {}
    default void onAuctionEnded(AuctionDTOs.AuctionUpdateDTO update) {}
    default void onAuctionNoWinner(AuctionDTOs.AuctionUpdateDTO update) {}
    default void onAuctionReserveNotMet(AuctionDTOs.AuctionUpdateDTO update) {}
    default void onAuctionCanceled(AuctionDTOs.AuctionUpdateDTO update) {}
    default void onAuctionExtended(AuctionDTOs.AuctionExtendedDTO dto) {}
    default void onAuctionUpcomingEnd(AuctionDTOs.AuctionUpcomingEndDTO dto) {}

    // ── BID ───────────────────────────────────────────────────────────────────

    default void onPlaceBidSuccess(BidDTOs.BidResultDTO result) {}
    default void onPlaceBidFailed(ErrorDTO error) {}
    default void onBidUpdate(BidDTOs.BidUpdateDTO update) {}
    default void onBidReserveNotMet(BidDTOs.BidUpdateDTO update) {}
    default void onBidHistoryReceived(BidDTOs.BidHistoryResponseDTO history) {}
    default void onBidHistoryFailed(ErrorDTO err) {}
    default void onBidChartPointUpdate(BidDTOs.BidChartPointDTO point) {}

    // ── AUTO-BID ──────────────────────────────────────────────────────────────

    /** Nhận sau REGISTER_AUTO_BID_SUCCESS hoặc GET_AUTO_BID_STATUS_SUCCESS. */
    default void onAutoBidRegistered(BidDTOs.AutoBidRegistrationDTO registration) {}
    /** REGISTER_AUTO_BID_FAILED hoặc CANCEL_AUTO_BID_FAILED. */
    default void onAutoBidFailed(ErrorDTO error) {}
    /** Server xác nhận cập nhật maxBid thành công (UPDATE_AUTO_BID_SUCCESS). */
    default void onUpdateAutoBidSuccess(BidDTOs.AutoBidRegistrationDTO registration) {}
    /** Server từ chối cập nhật maxBid (UPDATE_AUTO_BID_FAILED). */
    default void onUpdateAutoBidFailed(ErrorDTO error) {}
    default void onCancelAutoBidSuccess(String auctionId) {}
    default void onAutoBidTriggered(BidDTOs.AutoBidTriggeredDTO notify) {}
    default void onAutoBidExhausted(BidDTOs.AutoBidExhaustedDTO notify) {}

    // ── PAYMENT / WALLET ──────────────────────────────────────────────────────

    default void onDepositSuccess(PaymentDTOs.WalletBalanceResponseDTO balance) {}
    default void onDepositFailed(ErrorDTO error) {}
    default void onWithdrawSuccess(PaymentDTOs.WalletBalanceResponseDTO balance) {}
    default void onWithdrawFailed(ErrorDTO error) {}
    default void onWalletBalanceReceived(PaymentDTOs.WalletBalanceResponseDTO balance) {}
    default void onPaymentSuccess(PaymentDTOs.PaymentResultDTO result) {}
    default void onPaymentFailed(ErrorDTO error) {}
    /** Server push cho Seller khi Winner đã thanh toán (PAYMENT_COMPLETED_NOTIFY). */
    default void onPaymentCompletedNotify(PaymentDTOs.PaymentResultDTO result) {}
    /** Server push cho Winner khi hết hạn thanh toán (PAYMENT_EXPIRED_NOTIFY). */
    default void onPaymentExpiredNotify(PaymentDTOs.PaymentExpiredDTO dto) {}
    /** Server push hoàn cọc cho Bidder thua (DEPOSIT_REFUND_NOTIFY). */
    default void onDepositRefundNotify(PaymentDTOs.DepositRefundDTO dto) {}
    /** Server push tịch thu cọc Winner không trả tiền (DEPOSIT_FORFEITED_NOTIFY). */
    default void onDepositForfeitedNotify(PaymentDTOs.DepositForfeitedDTO dto) {}
    /** Server push đề nghị Second Chance Offer cho runner-up (SECOND_CHANCE_OFFER_NOTIFY). */
    default void onSecondChanceOffer(PaymentDTOs.SecondChanceOfferDTO offer) {}
    /** Server xác nhận runner-up chấp nhận Second Chance (SECOND_CHANCE_ACCEPT_SUCCESS). */
    default void onSecondChanceAcceptSuccess(PaymentDTOs.PaymentResultDTO result) {}
    /** Server từ chối accept Second Chance (SECOND_CHANCE_ACCEPT_FAILED). */
    default void onSecondChanceAcceptFailed(ErrorDTO error) {}
    /** Server xác nhận đã ghi nhận runner-up từ chối Second Chance (SECOND_CHANCE_DECLINE_SUCCESS). */
    default void onSecondChanceDeclineSuccess() {}

    // ── ADMIN — User management ───────────────────────────────────────────────

    /** Server xác nhận Admin ban user thành công (ADMIN_BAN_USER_SUCCESS). */
    default void onAdminBanUserSuccess(UserDTO user) {}
    /** Server từ chối ban user (ADMIN_BAN_USER_FAILED). */
    default void onAdminBanUserFailed(ErrorDTO error) {}
    /** Server xác nhận Admin unban thành công (ADMIN_UNBAN_USER_SUCCESS). */
    default void onAdminUnbanUserSuccess(UserDTO user) {}
    /** Server từ chối unban (ADMIN_UNBAN_USER_FAILED). */
    default void onAdminUnbanUserFailed(ErrorDTO error) {}
    /** Server trả về danh sách toàn bộ user (ADMIN_GET_ALL_USERS_SUCCESS). */
    default void onAdminAllUsersReceived(List<UserDTO> users) {}
    /** Server xác nhận tạo Staff Admin thành công (ADMIN_CREATE_STAFF_SUCCESS). */
    default void onAdminCreateStaffSuccess(UserDTO staff) {}
    /** Server từ chối tạo Staff Admin (ADMIN_CREATE_STAFF_FAILED). */
    default void onAdminCreateStaffFailed(ErrorDTO error) {}
    /** Server trả về danh sách Staff Admin (ADMIN_GET_ALL_STAFF_SUCCESS). */
    default void onAdminAllStaffReceived(List<UserDTO> staff) {}
    /** Server xác nhận Admin approve Seller role (ADMIN_APPROVE_SELLER_ROLE_SUCCESS). */
    default void onAdminApproveSellerRoleSuccess(UserDTO user) {}
    /** Server từ chối Admin approve Seller role (ADMIN_APPROVE_SELLER_ROLE_FAILED). */
    default void onAdminApproveSellerRoleFailed(ErrorDTO error) {}

    // ── RATING ────────────────────────────────────────────────────────────────

    /** Server xác nhận Bidder đánh giá Seller thành công (RATE_SELLER_SUCCESS). */
    default void onRateSellerSuccess() {}
    /** Server từ chối đánh giá Seller (RATE_SELLER_FAILED). */
    default void onRateSellerFailed(ErrorDTO error) {}
    /** Server xác nhận Seller đánh giá Bidder thành công (RATE_BIDDER_SUCCESS). */
    default void onRateBidderSuccess() {}
    /** Server từ chối đánh giá Bidder (RATE_BIDDER_FAILED). */
    default void onRateBidderFailed(ErrorDTO error) {}
    /** Server trả về lịch sử đánh giá (GET_USER_RATINGS_SUCCESS). */
    default void onUserRatingsReceived(RatingDTOs.RatingHistoryDTO history) {}

    // ── QUALITY REPORT ────────────────────────────────────────────────────────

    /** Server xác nhận nhận báo cáo chất lượng (SUBMIT_QUALITY_REPORT_SUCCESS). */
    default void onSubmitQualityReportSuccess(ReportDTOs.QualityReportDTO report) {}
    /** Server từ chối báo cáo chất lượng (SUBMIT_QUALITY_REPORT_FAILED). */
    default void onSubmitQualityReportFailed(ErrorDTO error) {}
    /** Server trả về danh sách báo cáo cho Admin (ADMIN_GET_QUALITY_REPORTS_SUCCESS). */
    default void onAdminQualityReportsReceived(List<ReportDTOs.QualityReportDTO> reports) {}
    /** Server xác nhận Admin approve báo cáo (ADMIN_APPROVE_QUALITY_REPORT_SUCCESS). */
    default void onAdminApproveQualityReportSuccess(ReportDTOs.QualityReportResultDTO result) {}
    /** Server từ chối Admin approve báo cáo (ADMIN_APPROVE_QUALITY_REPORT_FAILED). */
    default void onAdminApproveQualityReportFailed(ErrorDTO error) {}
    /** Server xác nhận Admin reject báo cáo (ADMIN_REJECT_QUALITY_REPORT_SUCCESS). */
    default void onAdminRejectQualityReportSuccess() {}
    /**
     * Server push cho Winner khi báo cáo chất lượng được duyệt và tiền được hoàn
     * (QUALITY_REPORT_APPROVED_NOTIFY).
     */
    default void onQualityReportApprovedNotify(ReportDTOs.QualityReportResultDTO result) {}
    /**
     * Server push cho Seller khi bị báo cáo chất lượng
     * (QUALITY_REPORT_RECEIVED_NOTIFY).
     */
    default void onQualityReportReceivedNotify(ReportDTOs.QualityReportDTO report) {}
    /**
     * Server push cho Seller khi báo cáo chất lượng bị reject
     * (QUALITY_REPORT_REJECTED_NOTIFY).
     */
    default void onQualityReportRejectedNotify(String reportId) {}
    /**
     * Server push khi Seller bị ban vĩnh viễn do không hoàn tiền đúng hạn
     * (SELLER_REFUND_OVERDUE_NOTIFY).
     */
    default void onSellerRefundOverdueNotify(String sellerId) {}

    // ── FRAUD ─────────────────────────────────────────────────────────────────

    /**
     * Server push cho SystemAdmin/Staff khi phát hiện gian lận
     * (FRAUD_DETECTED_NOTIFY).
     */
    default void onFraudDetectedNotify(AdminDTOs.FraudDetectedDTO dto) {}

    // ── NOTIFICATIONS ─────────────────────────────────────────────────────────

    /** Server trả về danh sách thông báo chưa đọc (GET_NOTIFICATIONS_SUCCESS). */
    default void onNotificationsReceived(List<AdminDTOs.NotificationDTO> notifications) {}
    /** Server xác nhận đã đánh dấu thông báo là đã đọc (MARK_NOTIFICATION_READ_SUCCESS). */
    default void onMarkNotificationReadSuccess() {}

    // ── SYSTEM ────────────────────────────────────────────────────────────────

    default void onSystemError(ErrorDTO error) {}
    default void onSystemAnnouncement(AdminDTOs.SystemAnnouncementDTO dto) {}
    default void onServerShutdown(AdminDTOs.ServerShutdownDTO dto) {}
}