package com.group13.auction.websocket;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auth.LoginRequestDTO;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.handler.PacketHandler;
import com.group13.auction.network.server.router.PacketRouter;
import com.group13.auction.network.server.session.ClientSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tương đương tầng "API / Controller" trong kiến trúc WebSocket thuần:
 * {@link PacketRouter} là điểm vào duy nhất sau khi JSON đến từ client.
 *
 * <p><b>Lưu ý dự án:</b> không có Spring MVC / MockMvc — kiểm thử router + handler contract.
 *
 * <p>Phạm vi: định tuyến đúng handler, thứ tự ưu tiên handler đầu tiên {@code supports()==true},
 * lỗi parse JSON, type không có handler, và bubble lỗi runtime từ handler → {@code SYSTEM_ERROR}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PacketRouter — định tuyến packet WebSocket (unit, đầy đủ)")
class PacketRouterDispatchTest {

    @Mock
    PacketHandler authHandler;

    @Mock
    PacketHandler bidHandler;

    @Mock
    PacketHandler paymentHandler;

    @Mock
    ClientSession session;

    PacketRouter router;

    @BeforeEach
    void setUp() {
        // Auth: LOGIN / REGISTER / LOGOUT
        lenient().when(authHandler.supports(any())).thenAnswer(inv -> {
            PacketType t = inv.getArgument(0);
            return t == PacketType.LOGIN || t == PacketType.REGISTER || t == PacketType.LOGOUT;
        });
        // Bid: JOIN / WATCH / LEAVE / PLACE_BID — khớp phạm vi BidHandler thật
        lenient().when(bidHandler.supports(any())).thenAnswer(inv -> {
            PacketType t = inv.getArgument(0);
            return t == PacketType.PLACE_BID
                    || t == PacketType.JOIN_AUCTION
                    || t == PacketType.WATCH_AUCTION
                    || t == PacketType.LEAVE_AUCTION;
        });
        // Payment: ví dụ handler thứ ba — đảm bảo router không gọi nhầm khi type khác
        lenient().when(paymentHandler.supports(any())).thenAnswer(inv -> {
            PacketType t = inv.getArgument(0);
            return t == PacketType.DEPOSIT || t == PacketType.WITHDRAW;
        });

        router = new PacketRouter();
        router.register(authHandler);
        router.register(bidHandler);
        router.register(paymentHandler);
    }

    // ── Happy path: định tuyến đúng handler ───────────────────────────────────

    @Nested
    @DisplayName("Given JSON hợp lệ When route Then đúng handler duy nhất được gọi")
    class HappyPathRouting {

        @Test
        @DisplayName("LOGIN → AuthHandler, không chạm Bid/Payment")
        void login_dispatchesToAuthHandlerOnly() {
            LoginRequestDTO dto = new LoginRequestDTO("alice", "secret");
            String json = PacketCodec.encode(Packet.of(PacketType.LOGIN, dto, "req-login-1"));

            router.route(session, json);

            verify(authHandler, times(1)).handle(
                    eq(session), eq(PacketType.LOGIN), any(JsonElement.class), eq("req-login-1"));
            verifyNoInteractions(bidHandler, paymentHandler);
        }

        @Test
        @DisplayName("JOIN_AUCTION → BidHandler")
        void joinAuction_dispatchesToBidHandler() {
            String json = """
                    {"type":"JOIN_AUCTION","requestId":"rid-join","payload":"auc-1"}
                    """;

            router.route(session, json);

            verify(bidHandler, times(1)).handle(
                    eq(session), eq(PacketType.JOIN_AUCTION), any(JsonElement.class), eq("rid-join"));
            verify(authHandler, never()).handle(any(), eq(PacketType.JOIN_AUCTION), any(), any());
            verify(paymentHandler, never()).handle(any(), eq(PacketType.JOIN_AUCTION), any(), any());
        }

        @Test
        @DisplayName("WATCH_AUCTION → BidHandler")
        void watchAuction_dispatchesToBidHandler() {
            String json = """
                    {"type":"WATCH_AUCTION","requestId":"rid-watch","payload":"auc-2"}
                    """;

            router.route(session, json);

            verify(bidHandler, times(1)).handle(
                    eq(session), eq(PacketType.WATCH_AUCTION), any(JsonElement.class), eq("rid-watch"));
            // supports() bị gọi khi router loop — chỉ verify handle() không được gọi
            verify(authHandler, never()).handle(any(), any(), any(), any());
            verify(paymentHandler, never()).handle(any(), any(), any(), any());
        }

        @Test
        @DisplayName("LEAVE_AUCTION → BidHandler")
        void leaveAuction_dispatchesToBidHandler() {
            String json = """
                    {"type":"LEAVE_AUCTION","requestId":"rid-leave","payload":"auc-3"}
                    """;

            router.route(session, json);

            verify(bidHandler, times(1)).handle(
                    eq(session), eq(PacketType.LEAVE_AUCTION), any(JsonElement.class), eq("rid-leave"));
        }

        @Test
        @DisplayName("PLACE_BID → BidHandler với requestId khớp")
        void placeBid_dispatchesToBidHandler() {
            String json = """
                    {"type":"PLACE_BID","requestId":"rid-bid-1","payload":{"auctionId":"A-1","amount":1500000}}
                    """;

            router.route(session, json);

            verify(bidHandler, times(1)).handle(
                    eq(session), eq(PacketType.PLACE_BID), any(JsonElement.class), eq("rid-bid-1"));
            verify(authHandler, never()).handle(any(), eq(PacketType.PLACE_BID), any(), any());
        }

        @Test
        @DisplayName("DEPOSIT → PaymentHandler (không đi vào BidHandler)")
        void deposit_dispatchesToPaymentHandler() {
            String json = """
                    {"type":"DEPOSIT","requestId":"rid-dep","payload":{"amount":100000}}
                    """;

            router.route(session, json);

            verify(paymentHandler, times(1)).handle(
                    eq(session), eq(PacketType.DEPOSIT), any(JsonElement.class), eq("rid-dep"));
            verify(bidHandler, never()).handle(any(), eq(PacketType.DEPOSIT), any(), any());
        }
    }

    // ── Edge: requestId, thứ tự đăng ký handler ───────────────────────────────

    @Nested
    @DisplayName("Edge cases — header JSON, thứ tự handler")
    class EdgeCases {

        @Test
        @DisplayName("When thiếu requestId Then handler nhận requestId = null")
        void missingRequestId_passesNullToHandler() {
            String json = """
                    {"type":"LOGIN","payload":{"username":"u","password":"p"}}
                    """;

            router.route(session, json);

            verify(authHandler).handle(eq(session), eq(PacketType.LOGIN), any(JsonElement.class), eq(null));
        }

        @Test
        @DisplayName("When đăng ký hai handler cùng supports(type) Then handler đăng ký trước thắng")
        void firstRegisteredHandlerWinsWhenBothSupportSameType() {
            PacketHandler first = org.mockito.Mockito.mock(PacketHandler.class);
            PacketHandler second = org.mockito.Mockito.mock(PacketHandler.class);
            lenient().when(first.supports(any())).thenReturn(true);
            lenient().when(second.supports(any())).thenReturn(true);

            PacketRouter local = new PacketRouter();
            local.register(first);
            local.register(second);
            String json = PacketCodec.encode(Packet.of(PacketType.PING, null, "only-first"));

            local.route(session, json);

            verify(first, times(1)).handle(eq(session), eq(PacketType.PING), any(), eq("only-first"));
            verify(second, never()).handle(any(), any(), any(), any());
        }
    }

    // ── Error: malformed JSON, unsupported type ───────────────────────────────

    @Nested
    @DisplayName("Error paths — parse & validation")
    class MalformedAndUnsupported {

        @Test
        @DisplayName("When JSON sai cú pháp Then SYSTEM_ERROR (VALIDATION) và không gọi handler")
        void malformedJson_noHandlerInvoked() {
            router.route(session, "{ not-json");

            verify(session).send(argThat(p -> p.getType() == PacketType.SYSTEM_ERROR
                    && p.getPayload() instanceof ErrorDTO err
                    && ErrorDTO.VALIDATION_ERROR.equals(err.getCode())));
            verifyNoInteractions(authHandler, bidHandler, paymentHandler);
        }

        @Test
        @DisplayName("When type không ai supports Then SYSTEM_ERROR validation message")
        void unknownType_returnsValidationError() {
            String json = PacketCodec.encode(Packet.of(PacketType.PING, null, "r-ping"));

            router.route(session, json);

            verify(session).send(argThat(p -> p.getType() == PacketType.SYSTEM_ERROR));
            // supports() bị gọi khi router loop qua handlers — chỉ verify handle() không được gọi
            verify(authHandler, never()).handle(any(), any(), any(), any());
            verify(bidHandler, never()).handle(any(), any(), any(), any());
            verify(paymentHandler, never()).handle(any(), any(), any(), any());
        }
    }

    // ── Error: handler ném exception ─────────────────────────────────────────

    @Nested
    @DisplayName("Error paths — handler runtime exception")
    class HandlerThrows {

        @Test
        @DisplayName("When BidHandler.handle ném RuntimeException Then SYSTEM_ERROR INTERNAL và đã vào handler")
        void bidHandlerThrows_becomesSystemError() {
            doThrow(new RuntimeException("simulated-db-down")).when(bidHandler).handle(
                    eq(session), eq(PacketType.PLACE_BID), any(JsonElement.class), any());

            String json = """
                    {"type":"PLACE_BID","requestId":"rid-x","payload":{"auctionId":"A-1","amount":1000}}
                    """;

            router.route(session, json);

            verify(bidHandler, times(1)).handle(
                    eq(session), eq(PacketType.PLACE_BID), any(JsonElement.class), eq("rid-x"));
            verify(session).send(argThat(p -> p.getType() == PacketType.SYSTEM_ERROR
                    && p.getPayload() instanceof ErrorDTO err
                    && ErrorDTO.INTERNAL_ERROR.equals(err.getCode())
                    && err.getMessage() != null
                    && err.getMessage().contains("PLACE_BID")));
        }
    }
}