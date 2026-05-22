package com.group13.auction.network.server.handler;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.payment.ConfirmItemReceivedResultDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.PaymentService;
import com.group13.auction.strategy.AuctionLockRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Xử lý các packet liên quan đến thanh toán:
 * DEPOSIT, WITHDRAW, GET_WALLET_BALANCE,
 * PAYMENT_REQUEST, SECOND_CHANCE_ACCEPT, SECOND_CHANCE_DECLINE.
 */
public class PaymentHandler implements PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);

    private static final long AUCTION_LOCK_TIMEOUT_SECONDS = 5L;

    private static final Set<PacketType> SUPPORTED = EnumSet.of(
        PacketType.DEPOSIT,
        PacketType.WITHDRAW,
        PacketType.GET_WALLET_BALANCE,
        PacketType.PAYMENT_REQUEST,
        PacketType.CONFIRM_ITEM_RECEIVED,
        PacketType.SECOND_CHANCE_ACCEPT,
        PacketType.SECOND_CHANCE_DECLINE
    );

    private final PaymentService paymentService;
    private final com.group13.auction.service.AccountService accountService;
    private final SessionManager sessionManager;
    private final AuctionLockRegistry lockRegistry = AuctionLockRegistry.getInstance();
    // FIX: dùng field thay vì new SecondChanceOfferDAO() trên mỗi request (tránh tạo object thừa)
    private final SecondChanceOfferDAO secondChanceOfferDAO = new SecondChanceOfferDAO();
    private final AuctionWinnerDAO auctionWinnerDAO = new AuctionWinnerDAO();
    private final UserDAO userDAO = new UserDAO();

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
            log.warn("Reject payment packet from unauthenticated session: type={}, requestId={}", type, requestId);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chưa đăng nhập.", requestId), requestId));
            return;
        }

        switch (type) {
            case DEPOSIT               -> handleDeposit(session, payload, requestId);
            case WITHDRAW              -> handleWithdraw(session, payload, requestId);
            case GET_WALLET_BALANCE    -> handleGetBalance(session, requestId);
            case PAYMENT_REQUEST       -> handlePayment(session, payload, requestId);
            case CONFIRM_ITEM_RECEIVED -> handleConfirmItemReceived(session, payload, requestId);
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
            log.info("Deposit handled: userId={}, username={}, amount={}, requestId={}",
                user.getId(), user.getUsername(), req.getAmount(), requestId);

            // FIX stale cache: xóa cachedUser cũ trong session để BidHandler
            // reload user mới nhất từ DB (có balance đã cập nhật) ở request tiếp theo.
            // Nếu không xóa, BidHandler.requireNormalUser() trả về object cũ có balance=0
            // → lockDeposit() sẽ báo INSUFFICIENT_DEPOSIT dù DB đã cập nhật đúng.
            session.invalidateCachedUser();

            PaymentDTOs.WalletBalanceResponseDTO resp = new PaymentDTOs.WalletBalanceResponseDTO(
                user.getBalance(), user.getLockedDeposit(), user.getAvailableBalance());
            session.send(Packet.of(PacketType.DEPOSIT_SUCCESS, resp, requestId));

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Deposit rejected: username={}, requestId={}, reason={}",
                session.getUsername(), requestId, e.getMessage());
            session.send(Packet.of(PacketType.DEPOSIT_FAILED,
                ErrorDTO.of(ErrorDTO.INVALID_AMOUNT, e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("Deposit failed: username={}, requestId={}",
                session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.DEPOSIT_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
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
            log.info("Withdraw handled: userId={}, username={}, amount={}, requestId={}",
                user.getId(), user.getUsername(), req.getAmount(), requestId);

            // FIX stale cache: tương tự deposit — xóa cache để BidHandler reload balance mới.
            session.invalidateCachedUser();

            PaymentDTOs.WalletBalanceResponseDTO resp = new PaymentDTOs.WalletBalanceResponseDTO(
                user.getBalance(), user.getLockedDeposit(), user.getAvailableBalance());
            session.send(Packet.of(PacketType.WITHDRAW_SUCCESS, resp, requestId));

        } catch (IllegalArgumentException e) {
            log.warn("Withdraw rejected: username={}, requestId={}, reason={}",
                session.getUsername(), requestId, e.getMessage());
            session.send(Packet.of(PacketType.WITHDRAW_FAILED,
                ErrorDTO.of(ErrorDTO.INSUFFICIENT_BALANCE, e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("Withdraw failed: username={}, requestId={}",
                session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.WITHDRAW_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
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
            log.debug("Wallet balance returned: userId={}, username={}, requestId={}",
                user.getId(), user.getUsername(), requestId);
        } catch (Exception e) {
            log.error("Get wallet balance failed: username={}, requestId={}",
                session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── CONFIRM ITEM RECEIVED ─────────────────────────────────────────────────

    private void handleConfirmItemReceived(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);

            com.group13.auction.model.user.NormalUser winner =
                requireNormalUser(session, requestId);
            if (winner == null) return;

            com.group13.auction.model.auction.Auction auction =
                AuctionManager.getInstance().findAuctionById(auctionId);
            if (auction == null) {
                session.send(Packet.of(PacketType.CONFIRM_ITEM_RECEIVED_FAILED,
                    com.group13.auction.common.dto.core.ErrorDTO.of(
                        "AUCTION_NOT_FOUND", "Không tìm thấy phiên đấu giá.", requestId),
                    requestId));
                return;
            }

            // resolveWinner() thử lazy-restore từ DB nếu in-memory null (server restart)
            com.group13.auction.model.auction.AuctionWinner auctionWinner = resolveWinner(auction);
            if (auctionWinner == null) {
                session.send(Packet.of(PacketType.CONFIRM_ITEM_RECEIVED_FAILED,
                    com.group13.auction.common.dto.core.ErrorDTO.of(
                        "AUCTION_NOT_FOUND", "Phiên chưa có kết quả.", requestId),
                    requestId));
                return;
            }

            // Guard: chỉ winner thật mới được xác nhận
            if (!auctionWinner.getWinner().getId().equals(winner.getId())) {
                session.send(Packet.of(PacketType.CONFIRM_ITEM_RECEIVED_FAILED,
                    com.group13.auction.common.dto.core.ErrorDTO.of(
                        "UNAUTHORIZED", "Chỉ winner mới có thể xác nhận nhận hàng.", requestId),
                    requestId));
                return;
            }

            // Guard: phải đã thanh toán (FUNDS_HELD) mới được xác nhận
            if (auctionWinner.getPaymentStatus() != com.group13.auction.model.auction.AuctionWinner.PaymentStatus.FUNDS_HELD) {
                session.send(Packet.of(PacketType.CONFIRM_ITEM_RECEIVED_FAILED,
                    com.group13.auction.common.dto.core.ErrorDTO.of(
                        "INVALID_STATE",
                        "Chỉ được xác nhận nhận hàng sau khi đã thanh toán đầy đủ. Trạng thái hiện tại: "
                            + auctionWinner.getPaymentStatus(),
                        requestId),
                    requestId));
                return;
            }

            // Cập nhật status → ITEM_RECEIVED
            paymentService.confirmItemReceived(auction);

            // Tạo response
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            ConfirmItemReceivedResultDTO result = new ConfirmItemReceivedResultDTO();
            result.setAuctionId(auctionId);
            result.setCanSubmitReport(true);
            result.setConfirmedAt(now);
            result.setReportDeadline(auctionWinner.getReportDeadline());

            session.send(Packet.of(PacketType.CONFIRM_ITEM_RECEIVED_SUCCESS, result, requestId));
            log.info("Item received confirmed: auctionId={}, winnerId={}, requestId={}",
                auctionId, winner.getId(), requestId);

        } catch (Exception e) {
            log.error("Confirm item received failed: username={}, requestId={}",
                session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.CONFIRM_ITEM_RECEIVED_FAILED,
                com.group13.auction.common.dto.core.ErrorDTO.of(
                    "INTERNAL_ERROR", e.getMessage(), requestId),
                requestId));
        }
    }

    // ── PAYMENT ───────────────────────────────────────────────────────────────

    private void handlePayment(ClientSession session, JsonElement payload, String requestId) {
        try {
            PaymentDTOs.PaymentRequestDTO req = PacketCodec.fromElement(
                payload, PaymentDTOs.PaymentRequestDTO.class);

            Auction auction = AuctionManager.getInstance().findAuctionById(req.getAuctionId());
            if (auction == null) {
                log.warn("Payment rejected because auction was not found: auctionId={}, username={}, requestId={}",
                    req.getAuctionId(), session.getUsername(), requestId);
                session.send(Packet.of(PacketType.PAYMENT_FAILED,
                    ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND, "Phiên không tồn tại.", requestId), requestId));
                return;
            }

            // FIX Bug #3: chỉ winner hợp lệ mới được trigger thanh toán.
            // resolveWinner() thử lazy-restore từ DB nếu in-memory null (server restart).
            com.group13.auction.model.auction.AuctionWinner auctionWinner = resolveWinner(auction);
            if (auctionWinner == null) {
                log.warn("Payment rejected because auction has no winner: auctionId={}, username={}, requestId={}",
                    req.getAuctionId(), session.getUsername(), requestId);
                session.send(Packet.of(PacketType.PAYMENT_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR, "Phiên này chưa có winner.", requestId), requestId));
                return;
            }
            NormalUser caller = requireNormalUser(session, requestId);
            if (caller == null) return;
            if (!caller.getId().equals(auctionWinner.getWinner().getId())) {
                log.warn("Payment rejected because caller is not winner: auctionId={}, callerId={}, winnerId={}, requestId={}",
                    req.getAuctionId(), caller.getId(), auctionWinner.getWinner().getId(), requestId);
                session.send(Packet.of(PacketType.PAYMENT_FAILED,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                        "Chỉ winner của phiên mới được thanh toán.", requestId), requestId));
                return;
            }

            boolean locked = lockRegistry.tryLock(auction.getId(), AUCTION_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("Payment lock timeout: auctionId={}, requestId={}", req.getAuctionId(), requestId);
                session.send(Packet.of(PacketType.PAYMENT_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR,
                        "Hệ thống đang xử lý phiên này, vui lòng thử lại.", requestId), requestId));
                return;
            }
            try {
                paymentService.completePayment(auction);
                log.info("Payment request handled: auctionId={}, winnerId={}, username={}, finalPrice={}, requestId={}",
                    req.getAuctionId(), caller.getId(), caller.getUsername(), auction.getCurrentPrice(), requestId);

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
            } finally {
                lockRegistry.unlock(auction.getId());
            }

        } catch (PaymentException e) {
            log.warn("Payment rejected: username={}, requestId={}, reason={}",
                session.getUsername(), requestId, e.getReason());
            session.send(Packet.of(PacketType.PAYMENT_FAILED,
                ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("Payment failed: username={}, requestId={}",
                session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.PAYMENT_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── SECOND CHANCE ACCEPT ─────────────────────────────────────────────────

    private void handleSecondChanceAccept(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction = AuctionManager.getInstance().findAuctionById(auctionId);
            if (auction == null) {
                log.warn("Second chance accept rejected because auction was not found: auctionId={}, username={}, requestId={}",
                    auctionId, session.getUsername(), requestId);
                session.send(Packet.of(PacketType.SECOND_CHANCE_ACCEPT_FAILED,
                    ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND, "Phiên không tồn tại.", requestId), requestId));
                return;
            }

            // FIX Bug #5: lấy offer thật từ SecondChanceOfferDAO field (không tạo new mỗi request).
            com.group13.auction.model.auction.SecondChanceOffer offer =
                secondChanceOfferDAO.findPendingOfferByAuctionId(auctionId);

            if (offer == null) {
                log.warn("Second chance accept rejected because offer was not found: auctionId={}, username={}, requestId={}",
                    auctionId, session.getUsername(), requestId);
                session.send(Packet.of(PacketType.SECOND_CHANCE_ACCEPT_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                        "Không tìm thấy Second Chance Offer PENDING cho phiên này.", requestId), requestId));
                return;
            }

            NormalUser caller = requireNormalUser(session, requestId);
            if (caller == null) return;
            if (!caller.getId().equals(offer.getRunnerUp().getId())) {
                log.warn("Second chance accept rejected because caller is not runner-up: auctionId={}, callerId={}, runnerUpId={}, requestId={}",
                    auctionId, caller.getId(), offer.getRunnerUp().getId(), requestId);
                session.send(Packet.of(PacketType.SECOND_CHANCE_ACCEPT_FAILED,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                        "Bạn không phải runner-up của offer này.", requestId), requestId));
                return;
            }

            boolean locked = lockRegistry.tryLock(auctionId, AUCTION_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                session.send(Packet.of(PacketType.SECOND_CHANCE_ACCEPT_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR,
                        "Hệ thống đang xử lý phiên này, vui lòng thử lại.", requestId), requestId));
                return;
            }
            try {
                paymentService.acceptSecondChanceOffer(offer, auction);
                log.info("Second chance accept handled: auctionId={}, offerId={}, runnerUpId={}, requestId={}",
                    auctionId, offer.getId(), caller.getId(), requestId);

                PaymentDTOs.PaymentResultDTO result = new PaymentDTOs.PaymentResultDTO();
                result.setAuctionId(auctionId);
                result.setFinalPrice(offer.getOfferPrice());
                result.setPaymentStatus("PENDING");
                session.send(Packet.of(PacketType.SECOND_CHANCE_ACCEPT_SUCCESS, result, requestId));
            } finally {
                lockRegistry.unlock(auctionId);
            }

        } catch (Exception e) {
            log.error("Second chance accept failed: username={}, requestId={}",
                session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.SECOND_CHANCE_ACCEPT_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── SECOND CHANCE DECLINE ─────────────────────────────────────────────────

    private void handleSecondChanceDecline(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction = AuctionManager.getInstance().findAuctionById(auctionId);
            if (auction == null) {
                log.warn("Second chance decline rejected because auction was not found: auctionId={}, username={}, requestId={}",
                    auctionId, session.getUsername(), requestId);
                session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND, "Phiên không tồn tại.", requestId), requestId));
                return;
            }

            // FIX Bug #5: lấy offer thật, gọi declineSecondChanceOffer thật.
            com.group13.auction.model.auction.SecondChanceOffer offer =
                secondChanceOfferDAO.findPendingOfferByAuctionId(auctionId);

            if (offer == null) {
                log.warn("Second chance decline rejected because offer was not found: auctionId={}, username={}, requestId={}",
                    auctionId, session.getUsername(), requestId);
                session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                        "Không tìm thấy Second Chance Offer PENDING.", requestId), requestId));
                return;
            }

            NormalUser caller = requireNormalUser(session, requestId);
            if (caller == null) return;
            if (!caller.getId().equals(offer.getRunnerUp().getId())) {
                log.warn("Second chance decline rejected because caller is not runner-up: auctionId={}, callerId={}, runnerUpId={}, requestId={}",
                    auctionId, caller.getId(), offer.getRunnerUp().getId(), requestId);
                session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                        "Bạn không phải runner-up của offer này.", requestId), requestId));
                return;
            }

            boolean locked = lockRegistry.tryLock(auctionId, AUCTION_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR,
                        "Hệ thống đang xử lý phiên này, vui lòng thử lại.", requestId), requestId));
                return;
            }
            try {
                paymentService.declineSecondChanceOffer(offer, auction);
                session.send(Packet.of(PacketType.SECOND_CHANCE_DECLINE_SUCCESS, null, requestId));
                log.info("Second chance decline handled: auctionId={}, offerId={}, runnerUpId={}, requestId={}",
                    auctionId, offer.getId(), caller.getId(), requestId);
            } finally {
                lockRegistry.unlock(auctionId);
            }

        } catch (Exception e) {
            log.error("Second chance decline failed: username={}, requestId={}",
                session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NormalUser requireNormalUser(ClientSession session, String requestId) {
        // FIX Bug 3: Dùng session cache trước — tránh hit DB mỗi payment request và
        // tránh AuctionManager.findUserByUsername() gọi allUsers.put() thay thế in-memory
        // user object, làm mất đồng bộ với object session cache của BidHandler và
        // object Auction.currentLeader đang giữ reference cũ.
        //
        // Tuy nhiên, KHÔNG cache cho payment operations (deposit/withdraw): các thao tác này
        // gọi session.invalidateCachedUser() để force reload balance mới nhất.
        // Nếu cache đã bị invalidate, fallback xuống AuctionManager (in-memory only) → không hit DB lần nữa.
        NormalUser cached = session.getCachedUser();
        if (cached != null) return cached;

        // Fallback: tìm trong in-memory (không query DB để tránh replace allUsers)
        com.group13.auction.model.user.User user =
            AuctionManager.getInstance().findUserByUsernameInMemoryOnly(session.getUsername());
        if (user == null) {
            // Lần đầu tiên (vd: server restart, user chưa có trong memory): load từ DB
            user = AuctionManager.getInstance().findUserByUsername(session.getUsername());
        }
        if (!(user instanceof NormalUser)) {
            log.warn("NormalUser required for payment handler: username={}, requestId={}",
                session.getUsername(), requestId);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ NormalUser mới được phép.", requestId), requestId));
            return null;
        }
        NormalUser normalUser = (NormalUser) user;
        session.setCachedUser(normalUser); // cache để dùng lại, tương nhất quán với BidHandler
        return normalUser;
    }

    /**
     * Lấy AuctionWinner từ in-memory. Nếu null (server vừa restart),
     * thử lazy-restore từ DB rồi gán lại vào auction để các request sau
     * không cần query DB nữa.
     *
     * @return AuctionWinner hoặc null nếu thực sự không có trong DB
     */
    private AuctionWinner resolveWinner(Auction auction) {
        AuctionWinner winner = auction.getWinner();
        if (winner != null) return winner;

        log.warn("Winner null in-memory, attempting lazy restore from DB: auctionId={}, status={}",
            auction.getId(), auction.getStatus());
        try {
            winner = auctionWinnerDAO.findByAuctionId(auction.getId(), userDAO);
            if (winner != null) {
                auction.setWinner(winner);
                log.info("Lazy winner restore success: auctionId={}, winnerId={}",
                    auction.getId(), winner.getWinner().getId());
            } else {
                log.error("No winner found in DB either: auctionId={} — data inconsistency",
                    auction.getId());
            }
        } catch (Exception e) {
            log.error("Lazy winner restore failed: auctionId={}", auction.getId(), e);
        }
        return winner;
    }
}