package com.group13.auction.network.client.session;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.auth.LoginResponseDTO;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.dto.user.UserDTO;

/**
 * Interface typed event cho phía Client.
 *
 * <p>JavaFX Controller chỉ cần implement các method mà màn hình đó quan tâm.
 * Các method có default body rỗng để Controller không cần override tất cả.
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

    default void onUserProfileReceived(UserDTO user) {}
    default void onSellerRoleApproved(UserDTO updatedUser) {}
    default void onSellerRoleRejected(ErrorDTO error) {}
    default void onAccountBanned(RatingDTOs.AccountBannedDTO dto) {}
    default void onAccountSuspended(RatingDTOs.AccountSuspendedDTO dto) {}
    default void onAccountRestored(RatingDTOs.AccountRestoredDTO dto) {}

    // ── AUCTION ───────────────────────────────────────────────────────────────

    default void onAuctionListReceived(AuctionDTOs.AuctionListDTO list) {}
    default void onAuctionDetailReceived(AuctionDTOs.AuctionDTO auction) {}
    default void onAuctionCreated(AuctionDTOs.AuctionDTO auction) {}
    default void onAuctionCreateFailed(ErrorDTO error) {}

    default void onJoinAuctionSuccess(AuctionDTOs.JoinAuctionResponseDTO response) {}
    default void onJoinAuctionFailed(ErrorDTO error) {}
    default void onWatchAuctionSuccess(AuctionDTOs.AuctionDTO auction) {}

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
    default void onBidChartPointUpdate(BidDTOs.BidChartPointDTO point) {}

    // ── AUTO-BID ──────────────────────────────────────────────────────────────

    default void onAutoBidRegistered(BidDTOs.AutoBidRegistrationDTO registration) {}
    default void onAutoBidFailed(ErrorDTO error) {}
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
    default void onPaymentCompletedNotify(PaymentDTOs.PaymentResultDTO result) {}
    default void onPaymentExpiredNotify(PaymentDTOs.PaymentExpiredDTO dto) {}
    default void onDepositRefundNotify(PaymentDTOs.DepositRefundDTO dto) {}
    default void onDepositForfeitedNotify(PaymentDTOs.DepositForfeitedDTO dto) {}
    default void onSecondChanceOffer(PaymentDTOs.SecondChanceOfferDTO offer) {}

    // ── SYSTEM ────────────────────────────────────────────────────────────────

    default void onSystemError(ErrorDTO error) {}
    default void onSystemAnnouncement(AdminDTOs.SystemAnnouncementDTO dto) {}
    default void onServerShutdown(AdminDTOs.ServerShutdownDTO dto) {}
}
