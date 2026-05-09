package com.group13.auction.network.server.handler;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.PaymentService;

import java.util.EnumSet;
import java.util.Set;

/**
 * Xử lý các packet liên quan đến thanh toán:
 * DEPOSIT, WITHDRAW, GET_WALLET_BALANCE,
 * PAYMENT_REQUEST, SECOND_CHANCE_ACCEPT, SECOND_CHANCE_DECLINE.
 */
public class PaymentHandler implements PacketHandler {

    private static final Set<PacketType> SUPPORTED = EnumSet.of(
            PacketType.DEPOSIT,
            PacketType.WITHDRAW,
            PacketType.GET_WALLET_BALANCE,
            PacketType.PAYMENT_REQUEST,
            PacketType.SECOND_CHANCE_ACCEPT,
            PacketType.SECOND_CHANCE_DECLINE
    );

    private final PaymentService paymentService;
    private final com.group13.auction.service.AccountService accountService;
    private final SessionManager sessionManager;

    public PaymentHandler(PaymentService paymentService,
                          com.group13.auction.service.AccountService accountService,
                          SessionManager sessionManager) {
        this.paymentService = paymentService;
        this.accountService = accountService;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean supports(PacketType type) { return SUPPORTED.contains(type); }

    @Override
    public void handle(ClientSession session, PacketType type,
                       JsonElement payload, String requestId) {
        if (!session.isAuthenticated()) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chưa đăng nhập.", requestId)));
            return;
        }

        switch (type) {
            case DEPOSIT               -> handleDeposit(session, payload, requestId);
            case WITHDRAW              -> handleWithdraw(session, payload, requestId);
            case GET_WALLET_BALANCE    -> handleGetBalance(session, requestId);
            case PAYMENT_REQUEST       -> handlePayment(session, payload, requestId);
            case SECOND_CHANCE_ACCEPT  -> handleSecondChanceAccept(session, payload, requestId);
            case SECOND_CHANCE_DECLINE -> handleSecondChanceDecline(session, payload, requestId);
            default -> {}
        }
    }

    // ── DEPOSIT ───────────────────────────────────────────────────────────────

    private void handleDeposit(ClientSession session, JsonElement payload, String requestId) {
        try {
            PaymentDTOs.DepositRequestDTO req = PacketCodec.fromElement(
                    payload, PaymentDTOs.DepositRequestDTO.class);

            NormalUser user = requireNormalUser(session, requestId);
            if (user == null) return;

            accountService.deposit(user, req.getAmount());

            PaymentDTOs.WalletBalanceResponseDTO resp = new PaymentDTOs.WalletBalanceResponseDTO(
                    user.getBalance(), user.getLockedDeposit(), user.getAvailableBalance());
            session.send(Packet.of(PacketType.DEPOSIT_SUCCESS, resp, requestId));

        } catch (IllegalArgumentException | IllegalStateException e) {
            session.send(Packet.of(PacketType.DEPOSIT_FAILED,
                    ErrorDTO.of(ErrorDTO.INVALID_AMOUNT, e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.DEPOSIT_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── WITHDRAW ──────────────────────────────────────────────────────────────

    private void handleWithdraw(ClientSession session, JsonElement payload, String requestId) {
        try {
            PaymentDTOs.WithdrawRequestDTO req = PacketCodec.fromElement(
                    payload, PaymentDTOs.WithdrawRequestDTO.class);

            NormalUser user = requireNormalUser(session, requestId);
            if (user == null) return;

            accountService.withdraw(user, req.getAmount());

            PaymentDTOs.WalletBalanceResponseDTO resp = new PaymentDTOs.WalletBalanceResponseDTO(
                    user.getBalance(), user.getLockedDeposit(), user.getAvailableBalance());
            session.send(Packet.of(PacketType.WITHDRAW_SUCCESS, resp, requestId));

        } catch (IllegalArgumentException e) {
            session.send(Packet.of(PacketType.WITHDRAW_FAILED,
                    ErrorDTO.of(ErrorDTO.INSUFFICIENT_BALANCE, e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.WITHDRAW_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── GET WALLET BALANCE ────────────────────────────────────────────────────

    private void handleGetBalance(ClientSession session, String requestId) {
        try {
            NormalUser user = requireNormalUser(session, requestId);
            if (user == null) return;

            PaymentDTOs.WalletBalanceResponseDTO resp = new PaymentDTOs.WalletBalanceResponseDTO(
                    user.getBalance(), user.getLockedDeposit(), user.getAvailableBalance());
            session.send(Packet.of(PacketType.GET_WALLET_BALANCE_SUCCESS, resp, requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── PAYMENT ───────────────────────────────────────────────────────────────

    private void handlePayment(ClientSession session, JsonElement payload, String requestId) {
        try {
            PaymentDTOs.PaymentRequestDTO req = PacketCodec.fromElement(
                    payload, PaymentDTOs.PaymentRequestDTO.class);

            Auction auction = AuctionManager.getInstance().findAuctionById(req.getAuctionId());
            if (auction == null) {
                session.send(Packet.of(PacketType.PAYMENT_FAILED,
                        ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND, "Phiên không tồn tại.", requestId)));
                return;
            }

            paymentService.completePayment(auction);

            PaymentDTOs.PaymentResultDTO result = new PaymentDTOs.PaymentResultDTO();
            result.setAuctionId(req.getAuctionId());
            result.setFinalPrice(auction.getCurrentPrice());
            result.setPaymentStatus("COMPLETED");
            result.setPaidAt(java.time.LocalDateTime.now());

            session.send(Packet.of(PacketType.PAYMENT_SUCCESS, result, requestId));

            // Notify Seller
            if (auction.getItem().getSeller() != null) {
                String sellerId = auction.getItem().getSeller().getId();
                sessionManager.sendToUser(sellerId,
                        Packet.of(PacketType.PAYMENT_COMPLETED_NOTIFY, result));
            }

            // Push AuctionUpdateDTO cho tất cả watcher
            AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, null);
            sessionManager.broadcastToAuction(req.getAuctionId(),
                    Packet.of(PacketType.AUCTION_ENDED_UPDATE, update));

        } catch (PaymentException e) {
            session.send(Packet.of(PacketType.PAYMENT_FAILED,
                    ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.PAYMENT_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── SECOND CHANCE ACCEPT ─────────────────────────────────────────────────

    private void handleSecondChanceAccept(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction = AuctionManager.getInstance().findAuctionById(auctionId);
            if (auction == null) {
                session.send(Packet.of(PacketType.SECOND_CHANCE_ACCEPT_FAILED,
                        ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND, "Phiên không tồn tại.", requestId)));
                return;
            }

            // TODO: lấy offer từ SecondChanceOfferDAO khi có registry
            // Tạm thời placeholder
            PaymentDTOs.PaymentResultDTO result = new PaymentDTOs.PaymentResultDTO();
            result.setAuctionId(auctionId);
            result.setFinalPrice(auction.getCurrentPrice());
            result.setPaymentStatus("PENDING");
            session.send(Packet.of(PacketType.SECOND_CHANCE_ACCEPT_SUCCESS,
                    result, requestId));

        } catch (Exception e) {
            session.send(Packet.of(PacketType.SECOND_CHANCE_ACCEPT_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── SECOND CHANCE DECLINE ─────────────────────────────────────────────────

    private void handleSecondChanceDecline(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction = AuctionManager.getInstance().findAuctionById(auctionId);
            if (auction != null) {
                // TODO: paymentService.declineSecondChanceOffer(offer, auction)
            }
            session.send(Packet.of(PacketType.SECOND_CHANCE_DECLINE_SUCCESS, null, requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NormalUser requireNormalUser(ClientSession session, String requestId) {
        com.group13.auction.model.user.User user =
                AuctionManager.getInstance().findUserByUsername(session.getUsername());
        if (!(user instanceof NormalUser)) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ NormalUser mới được phép.", requestId)));
            return null;
        }
        return (NormalUser) user;
    }
}
