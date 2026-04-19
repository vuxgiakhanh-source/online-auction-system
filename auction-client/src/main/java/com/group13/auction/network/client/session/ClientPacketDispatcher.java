package com.group13.auction.network.client.session;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.client.handler.ServerResponseHandler;

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

            default -> log.fine("[DISPATCHER] Unhandled packet type: " + type);
        }
    }
}