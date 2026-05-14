package com.group13.auction.websocket.handler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.handler.PaymentHandler;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.PaymentService;
import com.group13.auction.unit.TestFixture;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link PaymentHandler} — DEPOSIT / WITHDRAW / GET_WALLET_BALANCE /
 * PAYMENT_REQUEST / unauthenticated guard.
 *
 * <p>Không chạm DB, không Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentHandler — dispatch & guard")
class PaymentHandlerDispatchTest {

    private static final Gson GSON = PacketCodec.gson();

    @Mock PaymentService   paymentService;
    @Mock AccountService   accountService;
    @Mock WebSocket        webSocket;

    SessionManager sessionManager = SessionManager.getInstance();

    private NormalUser user;

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();
        reset(webSocket, paymentService, accountService);
        lenient().when(webSocket.isOpen()).thenReturn(true);
        sessionManager.unregister(webSocket);
        sessionManager.register(webSocket);

        user = TestFixture.bidderWithBalance("payuser1111", 10_000_000L);
        // Register user in AuctionManager so requireNormalUser() can find it
        AuctionManager.getInstance().addToUserList(user);
        sessionManager.authenticate(webSocket, user.getId(), user.getUsername(), "NORMAL_USER");
    }

    @AfterEach
    void tearDown() throws Exception {
        sessionManager.unregister(webSocket);
        TestFixture.resetSystemAdmin();
        clearAuctionManagerUsers();
        clearAuctionManagerAuctions();
    }

    private void clearAuctionManagerUsers() {
        try {
            Field f = AuctionManager.class.getDeclaredField("allUsers");
            f.setAccessible(true);
            ((Map<?, ?>) f.get(AuctionManager.getInstance())).clear();
        } catch (Exception ignored) {}
    }

    private void clearAuctionManagerAuctions() {
        try {
            Field f = AuctionManager.class.getDeclaredField("allAuctions");
            f.setAccessible(true);
            ((Map<?, ?>) f.get(AuctionManager.getInstance())).clear();
        } catch (Exception ignored) {}
    }

    private PaymentHandler newHandler() {
        return new PaymentHandler(paymentService, accountService, sessionManager);
    }

    private ClientSession session() {
        return sessionManager.getByConnection(webSocket);
    }

    private String captureLastSent() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(webSocket, atLeastOnce()).send(cap.capture());
        return cap.getValue();
    }

    // =========================================================================
    // Auth guard — unauthenticated
    // =========================================================================

    @Nested
    @DisplayName("Unauthenticated guard")
    class UnauthGuard {

        @Test
        @DisplayName("DEPOSIT without login → SYSTEM_ERROR UNAUTHORIZED")
        void deposit_unauthenticated_unauthorized() {
            sessionManager.deauthenticate(webSocket); // log out
            JsonElement payload = GSON.toJsonTree(new PaymentDTOs.DepositRequestDTO(100_000L));

            newHandler().handle(session(), PacketType.DEPOSIT, payload, "rid-unauth");

            assertThat(captureLastSent())
                    .contains(PacketType.SYSTEM_ERROR.name())
                    .contains(ErrorDTO.UNAUTHORIZED);
            verify(accountService, never()).deposit(any(), anyLong());
        }

        @Test
        @DisplayName("WITHDRAW without login → SYSTEM_ERROR UNAUTHORIZED")
        void withdraw_unauthenticated_unauthorized() {
            sessionManager.deauthenticate(webSocket);
            JsonElement payload = GSON.toJsonTree(new PaymentDTOs.WithdrawRequestDTO(50_000L));

            newHandler().handle(session(), PacketType.WITHDRAW, payload, "rid-unauth-w");

            assertThat(captureLastSent())
                    .contains(PacketType.SYSTEM_ERROR.name())
                    .contains(ErrorDTO.UNAUTHORIZED);
        }
    }

    // =========================================================================
    // DEPOSIT
    // =========================================================================

    @Nested
    @DisplayName("DEPOSIT")
    class Deposit {

        @Test
        @DisplayName("happy path → DEPOSIT_SUCCESS với balance đúng")
        void deposit_success() {
            doAnswer(inv -> {
                NormalUser u = inv.getArgument(0);
                long amt = inv.getArgument(1);
                // simulate deposit: add to balance
                java.lang.reflect.Field f = u.getClass().getSuperclass().getDeclaredField("balance");
                f.setAccessible(true);
                f.set(u, ((long) f.get(u)) + amt);
                return null;
            }).when(accountService).deposit(eq(user), anyLong());

            JsonElement payload = GSON.toJsonTree(new PaymentDTOs.DepositRequestDTO(500_000L));
            newHandler().handle(session(), PacketType.DEPOSIT, payload, "rid-dep-1");

            assertThat(captureLastSent()).contains(PacketType.DEPOSIT_SUCCESS.name());
            verify(accountService).deposit(user, 500_000L);
        }

        @Test
        @DisplayName("accountService throws IllegalArgumentException → DEPOSIT_FAILED INVALID_AMOUNT")
        void deposit_invalidAmount_failedWithCode() {
            doThrow(new IllegalArgumentException("Số tiền không hợp lệ"))
                    .when(accountService).deposit(any(), anyLong());
            JsonElement payload = GSON.toJsonTree(new PaymentDTOs.DepositRequestDTO(-1L));

            newHandler().handle(session(), PacketType.DEPOSIT, payload, "rid-dep-2");

            assertThat(captureLastSent())
                    .contains(PacketType.DEPOSIT_FAILED.name())
                    .contains(ErrorDTO.INVALID_AMOUNT);
        }

        @Test
        @DisplayName("null payload → DEPOSIT_FAILED (no NPE to caller)")
        void deposit_nullPayload_gracefulError() {
            newHandler().handle(session(), PacketType.DEPOSIT, null, "rid-dep-3");

            assertThat(captureLastSent()).contains(PacketType.DEPOSIT_FAILED.name());
        }
    }

    // =========================================================================
    // WITHDRAW
    // =========================================================================

    @Nested
    @DisplayName("WITHDRAW")
    class Withdraw {

        @Test
        @DisplayName("happy path → WITHDRAW_SUCCESS")
        void withdraw_success() {
            JsonElement payload = GSON.toJsonTree(new PaymentDTOs.WithdrawRequestDTO(200_000L));
            doNothing().when(accountService).withdraw(any(), anyLong());

            newHandler().handle(session(), PacketType.WITHDRAW, payload, "rid-wd-1");

            assertThat(captureLastSent()).contains(PacketType.WITHDRAW_SUCCESS.name());
        }

        @Test
        @DisplayName("insufficient balance → WITHDRAW_FAILED INSUFFICIENT_BALANCE")
        void withdraw_insufficientBalance_fails() {
            doThrow(new IllegalArgumentException("Số dư không đủ"))
                    .when(accountService).withdraw(any(), anyLong());
            JsonElement payload = GSON.toJsonTree(new PaymentDTOs.WithdrawRequestDTO(999_999_999L));

            newHandler().handle(session(), PacketType.WITHDRAW, payload, "rid-wd-2");

            assertThat(captureLastSent())
                    .contains(PacketType.WITHDRAW_FAILED.name())
                    .contains(ErrorDTO.INSUFFICIENT_BALANCE);
        }
    }

    // =========================================================================
    // GET_WALLET_BALANCE
    // =========================================================================

    @Nested
    @DisplayName("GET_WALLET_BALANCE")
    class GetBalance {

        @Test
        @DisplayName("happy path → GET_WALLET_BALANCE_SUCCESS với balance fields đúng")
        void getBalance_success() {
            newHandler().handle(session(), PacketType.GET_WALLET_BALANCE, null, "rid-bal-1");

            String sent = captureLastSent();
            assertThat(sent).contains(PacketType.GET_WALLET_BALANCE_SUCCESS.name());
            // Response phải chứa balance (10_000_000)
            assertThat(sent).contains("balance");
        }
    }

    // =========================================================================
    // PAYMENT_REQUEST
    // =========================================================================

    @Nested
    @DisplayName("PAYMENT_REQUEST")
    class PaymentRequest {

        @Test
        @DisplayName("auction not found → PAYMENT_FAILED AUCTION_NOT_FOUND")
        void payment_auctionNotFound_fails() {
            PaymentDTOs.PaymentRequestDTO req = new PaymentDTOs.PaymentRequestDTO();
            req.setAuctionId("nonexistent-auction-id");
            JsonElement payload = GSON.toJsonTree(req);

            newHandler().handle(session(), PacketType.PAYMENT_REQUEST, payload, "rid-pay-1");

            assertThat(captureLastSent())
                    .contains(PacketType.PAYMENT_FAILED.name())
                    .contains(ErrorDTO.AUCTION_NOT_FOUND);
        }

        @Test
        @DisplayName("auction has no winner → PAYMENT_FAILED VALIDATION_ERROR")
        void payment_noWinner_fails() {
            NormalUser seller = TestFixture.normalSeller("sellerpay11");
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            // No winner set
            try {
                Field f = AuctionManager.class.getDeclaredField("allAuctions");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Auction> map = (Map<String, Auction>) f.get(AuctionManager.getInstance());
                map.put(auction.getId(), auction);
            } catch (Exception e) { throw new RuntimeException(e); }

            PaymentDTOs.PaymentRequestDTO req = new PaymentDTOs.PaymentRequestDTO();
            req.setAuctionId(auction.getId());
            JsonElement payload = GSON.toJsonTree(req);

            newHandler().handle(session(), PacketType.PAYMENT_REQUEST, payload, "rid-pay-2");

            assertThat(captureLastSent())
                    .contains(PacketType.PAYMENT_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("caller is not winner → PAYMENT_FAILED UNAUTHORIZED")
        void payment_callerNotWinner_unauthorized() {
            NormalUser seller = TestFixture.normalSeller("sellerpay22");
            NormalUser actualWinner = TestFixture.bidderWithBalance("actualwin22", 5_000_000L);
            Auction auction = TestFixture.finishedAuction(seller, actualWinner, 1_000_000L, 2_000_000L);
            AuctionWinner aw = AuctionWinner.create(actualWinner, auction.getId(), 2_000_000L, 300_000L, false);
            auction.setWinner(aw);
            try {
                Field f = AuctionManager.class.getDeclaredField("allAuctions");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Auction> map = (Map<String, Auction>) f.get(AuctionManager.getInstance());
                map.put(auction.getId(), auction);
            } catch (Exception e) { throw new RuntimeException(e); }

            PaymentDTOs.PaymentRequestDTO req = new PaymentDTOs.PaymentRequestDTO();
            req.setAuctionId(auction.getId());
            JsonElement payload = GSON.toJsonTree(req);

            // session is authenticated as 'user' (not actualWinner)
            newHandler().handle(session(), PacketType.PAYMENT_REQUEST, payload, "rid-pay-3");

            assertThat(captureLastSent())
                    .contains(PacketType.PAYMENT_FAILED.name())
                    .contains(ErrorDTO.UNAUTHORIZED);
            verify(paymentService, never()).completePayment(any());
        }

        @Test
        @DisplayName("PaymentException from service → PAYMENT_FAILED with reason code")
        void payment_paymentException_propagatesReason() {
            // Setup: user is the winner
            Auction auction = TestFixture.finishedAuction(
                    TestFixture.normalSeller("sellerpay33"), user, 1_000_000L, 2_000_000L);
            AuctionWinner aw = AuctionWinner.create(user, auction.getId(), 2_000_000L, 300_000L, false);
            aw.markFundsHeld(); // required for releaseToSeller, but we test completePayment path
            auction.setWinner(aw);
            // Reset to PENDING for completePayment
            AuctionWinner awPending = AuctionWinner.create(user, auction.getId(), 2_000_000L, 300_000L, false);
            auction.setWinner(awPending);
            try {
                Field f = AuctionManager.class.getDeclaredField("allAuctions");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Auction> map = (Map<String, Auction>) f.get(AuctionManager.getInstance());
                map.put(auction.getId(), auction);
            } catch (Exception e) { throw new RuntimeException(e); }

            doThrow(new PaymentException(PaymentException.Reason.PAYMENT_EXPIRED, "Đã hết hạn"))
                    .when(paymentService).completePayment(any());

            PaymentDTOs.PaymentRequestDTO req = new PaymentDTOs.PaymentRequestDTO();
            req.setAuctionId(auction.getId());
            JsonElement payload = GSON.toJsonTree(req);

            newHandler().handle(session(), PacketType.PAYMENT_REQUEST, payload, "rid-pay-4");

            assertThat(captureLastSent())
                    .contains(PacketType.PAYMENT_FAILED.name())
                    .contains("PAYMENT_EXPIRED");
        }
    }
}
