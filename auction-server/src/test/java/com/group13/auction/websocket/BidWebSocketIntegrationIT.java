package com.group13.auction.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.ItemDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.unit.TestFixture;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.handler.BidHandler;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.BidRateLimiter;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * BidWebSocketIntegrationIT — BidHandler × MySQL (Testcontainers) × SessionManager
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * <p>Mô phỏng pipeline thật của server: {@link ClientSession} + mock {@link WebSocket},
 * cùng các DAO/Service production. Khác với {@code BidServiceIntegrationIT} (gọi service trực tiếp),
 * lớp này đi qua {@link BidHandler} — nơi gọi {@link AuctionManager#findAuctionById(String)}:
 * vì vậy mỗi phiên test phải {@link AuctionManager#registerAuction(Auction)} sau khi persist DB.
 *
 * <p>Singleton {@link SessionManager}, {@link AuctionManager}, {@link AutoBidRegistry} được
 * xả state có kiểm soát giữa các test để tránh rò rỉ auction/user/auto-bid giữa các case.
 *
 * <p>{@link com.group13.auction.service.AuctionService} đọc {@code SystemAdmin.getInstance()} ngay khi khởi tạo —
 * phải gọi {@link TestFixture#bootstrapSystemAdmin()} trước khi tạo service (giống {@code PaymentStateTransitionIT}).
 */
@RequiresDocker
@Testcontainers
@ExtendWith(MockitoExtension.class)
@DisplayName("BidWebSocketIntegrationIT — BidHandler × DB × WebSocket (Integration, đầy đủ)")
class BidWebSocketIntegrationIT extends IntegrationTestBase {

    private static final Gson GSON = new Gson();

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0")
            .withDatabaseName("omnibid_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("database/schema.sql");

    @Mock(lenient = true)
    WebSocket webSocket;

    /** Client thứ hai — dùng cho kịch bản multi-connection / broadcast. */
    @Mock(lenient = true)
    WebSocket webSocketPeer;

    SessionManager sessionManager = SessionManager.getInstance();

    private UserDAO userDAO;
    private ItemDAO itemDAO;
    private AuctionDAO auctionDAO;
    private BidTransactionDAO bidTransactionDAO;
    private FinancialTransactionDAO financialTransactionDAO;
    private RatingService ratingService;
    private WalletService walletService;
    private AuctionService auctionService;
    private BidService bidService;
    private BidHandler bidHandler;

    @BeforeAll
    static void configureDataSource() throws Exception {
        configureTestcontainer(mysql);
    }

    @BeforeEach
    void setUp() throws Exception {
        reset(webSocket, webSocketPeer);
        when(webSocket.isOpen()).thenReturn(true);
        when(webSocketPeer.isOpen()).thenReturn(true);

        sessionManager.unregister(webSocket);
        sessionManager.unregister(webSocketPeer);

        resetInMemorySingletons();

        // AuctionService() gọi SystemAdmin.getInstance() ở field initializer — bắt buộc bootstrap.
        TestFixture.bootstrapSystemAdmin();

        userDAO = new UserDAO();
        itemDAO = new ItemDAO();
        auctionDAO = new AuctionDAO();
        bidTransactionDAO = new BidTransactionDAO();
        financialTransactionDAO = new FinancialTransactionDAO();
        ratingService = new RatingService(userDAO);
        walletService = new WalletService(financialTransactionDAO, userDAO, ratingService);
        auctionService = new AuctionService(ratingService, auctionDAO);
        bidService = new BidService(auctionService, ratingService, walletService,
                bidTransactionDAO, auctionDAO, userDAO);
        bidHandler = new BidHandler(bidService, ratingService, sessionManager);

        resetTracking();
    }

    @AfterEach
    void tearDown() throws Exception {
        BidRateLimiter.getInstance().clearAll();
        sessionManager.unregister(webSocket);
        sessionManager.unregister(webSocketPeer);
        resetInMemorySingletons();
        cleanupDB();
        TestFixture.resetSystemAdmin();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-WS-01 — Happy path: JOIN → PLACE_BID
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TC-WS-01 [CRITICAL] Happy path — JOIN_AUCTION → PLACE_BID_SUCCESS")
    class HappyPathJoinAndBid {

        @Test
        @DisplayName("TC-WS-01a: JOIN thành công (JOIN_AUCTION_SUCCESS) rồi đặt giá thành công")
        void joinThenPlaceBid_emitsJoinSuccessAndBidSuccess() {
            Auction auction = givenRunningAuctionInManager("ws_hp_s", 2_000_000L, 5_000_000L);
            NormalUser bidder = buildUserWithBalance("ws_hp_b", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(bidder.getId(), bidder.getUsername(), "NORMAL_USER");

            bidHandler.handle(session, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "rid-join");

            ArgumentCaptor<String> afterJoin = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeastOnce()).send(afterJoin.capture());
            assertThat(afterJoin.getAllValues().stream().anyMatch(s -> s.contains("JOIN_AUCTION_SUCCESS")))
                    .as("Sau JOIN phải có packet JOIN_AUCTION_SUCCESS")
                    .isTrue();

            JsonElement bidPayload = GSON.toJsonTree(new BidDTOs.BidRequestDTO(auction.getId(), 3_000_000L));
            bidHandler.handle(session, PacketType.PLACE_BID, bidPayload, "rid-bid");

            ArgumentCaptor<String> afterBid = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeastOnce()).send(afterBid.capture());
            assertThat(afterBid.getAllValues().stream().anyMatch(s -> s.contains("PLACE_BID_SUCCESS")))
                    .as("Sau PLACE_BID phải có PLACE_BID_SUCCESS")
                    .isTrue();
        }

        @Test
        @DisplayName("TC-WS-01b: Hai lần PLACE_BID hợp lệ liên tiếp — giá tăng, không lỗi")
        void twoSequentialBids_bothSucceed() {
            Auction auction = givenRunningAuctionInManager("ws_seq_s", 1_000_000L, 3_000_000L);
            NormalUser bidder = buildUserWithBalance("ws_seq_b", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(bidder.getId(), bidder.getUsername(), "NORMAL_USER");

            bidHandler.handle(session, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "j1");
            bidHandler.handle(session, PacketType.PLACE_BID,
                    GSON.toJsonTree(new BidDTOs.BidRequestDTO(auction.getId(), 1_500_000L)), "b1");
            bidHandler.handle(session, PacketType.PLACE_BID,
                    GSON.toJsonTree(new BidDTOs.BidRequestDTO(auction.getId(), 2_000_000L)), "b2");

            assertThat(auction.getCurrentPrice()).isEqualTo(2_000_000L);

            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeastOnce()).send(cap.capture());
            long successPackets = cap.getAllValues().stream().filter(s -> s.contains("PLACE_BID_SUCCESS")).count();
            assertThat(successPackets).isGreaterThanOrEqualTo(2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-WS-02 — WATCH / LEAVE / GET_BID_HISTORY
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TC-WS-02 WATCH / LEAVE / GET_BID_HISTORY")
    class WatchLeaveHistory {

        @Test
        @DisplayName("TC-WS-02a: WATCH_AUCTION → WATCH_AUCTION_SUCCESS (không lock deposit)")
        void watchAuction_successPacket() {
            Auction auction = givenRunningAuctionInManager("ws_w_s", 2_000_000L, 5_000_000L);
            NormalUser watcher = buildUserWithBalance("ws_w_b", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(watcher.getId(), watcher.getUsername(), "NORMAL_USER");

            bidHandler.handle(session, PacketType.WATCH_AUCTION, new JsonPrimitive(auction.getId()), "rid-watch");

            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeastOnce()).send(cap.capture());
            assertThat(cap.getAllValues().stream().anyMatch(s -> s.contains("WATCH_AUCTION_SUCCESS")))
                    .isTrue();
        }

        @Test
        @DisplayName("TC-WS-02b: JOIN → LEAVE_AUCTION → LEAVE_AUCTION_SUCCESS")
        void leaveAfterJoin_success() {
            Auction auction = givenRunningAuctionInManager("ws_lv_s", 2_000_000L, 5_000_000L);
            NormalUser bidder = buildUserWithBalance("ws_lv_b", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(bidder.getId(), bidder.getUsername(), "NORMAL_USER");

            bidHandler.handle(session, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "j");
            bidHandler.handle(session, PacketType.LEAVE_AUCTION, new JsonPrimitive(auction.getId()), "l");

            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeastOnce()).send(cap.capture());
            assertThat(cap.getAllValues().stream().anyMatch(s -> s.contains("LEAVE_AUCTION_SUCCESS")))
                    .isTrue();
        }

        @Test
        @DisplayName("TC-WS-02c: Sau khi đặt giá — GET_BID_HISTORY_SUCCESS chứa điểm biểu đồ")
        void getBidHistory_afterBid_returnsSuccessWithPoints() {
            Auction auction = givenRunningAuctionInManager("ws_hist_s", 2_000_000L, 5_000_000L);
            NormalUser bidder = buildUserWithBalance("ws_hist_b", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(bidder.getId(), bidder.getUsername(), "NORMAL_USER");

            bidHandler.handle(session, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "j");
            bidHandler.handle(session, PacketType.PLACE_BID,
                    GSON.toJsonTree(new BidDTOs.BidRequestDTO(auction.getId(), 3_000_000L)), "b");

            bidHandler.handle(session, PacketType.GET_BID_HISTORY, new JsonPrimitive(auction.getId()), "h");

            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeastOnce()).send(cap.capture());
            assertThat(cap.getAllValues().stream().anyMatch(s -> s.contains("GET_BID_HISTORY_SUCCESS")))
                    .as("Phải có GET_BID_HISTORY_SUCCESS sau khi đã có giao dịch")
                    .isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-WS-03 — Multi client & broadcast (WebSocket.send — cùng đường ClientSession.sendRaw)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TC-WS-03 Multi-client — broadcast BID_* tới peer đang watch")
    class MultiClientBroadcast {

        @Test
        @DisplayName("TC-WS-03a: Bidder đặt giá — peer đang join cùng phiên nhận WebSocket.send(JSON) chứa BID_RESERVE_NOT_MET_UPDATE")
        void peerReceivesBroadcastOnBidBelowReserve() throws Exception {
            Auction auction = givenRunningAuctionInManager("ws_bc_s", 2_000_000L, 8_000_000L);
            NormalUser bidderA = buildUserWithBalance("ws_bc_a", 50_000_000L, userDAO);
            NormalUser bidderB = buildUserWithBalance("ws_bc_b", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            sessionManager.register(webSocketPeer);
            ClientSession sessA = sessionManager.getByConnection(webSocket);
            ClientSession sessB = sessionManager.getByConnection(webSocketPeer);
            sessA.authenticate(bidderA.getId(), bidderA.getUsername(), "NORMAL_USER");
            sessB.authenticate(bidderB.getId(), bidderB.getUsername(), "NORMAL_USER");

            bidHandler.handle(sessA, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "ja");
            bidHandler.handle(sessB, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "jb");

            bidHandler.handle(sessA, PacketType.PLACE_BID,
                    GSON.toJsonTree(new BidDTOs.BidRequestDTO(auction.getId(), 3_000_000L)), "bid-a");

            // FIX ASYNC BROADCAST: broadcastToAuctionAsync() gửi qua thread pool riêng.
            // Phải dùng timeout() để Mockito chờ đến khi async send hoàn thành.
            // 2000ms là đủ rộng cho thread pool khởi động và gửi xong.
            ArgumentCaptor<String> broadcastJson = ArgumentCaptor.forClass(String.class);
            verify(webSocketPeer, timeout(2000).atLeastOnce()).send(broadcastJson.capture());
            assertThat(broadcastJson.getAllValues().stream().anyMatch(s -> s.contains("BID_RESERVE_NOT_MET_UPDATE")))
                    .as("Peer đang watch phải nhận broadcast reserve-not-met khi bid < reserve")
                    .isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-WS-04 — Error & edge
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TC-WS-04 Error paths — không join, giá sai, phiên không tồn tại")
    class ErrorPaths {

        @Test
        @DisplayName("TC-WS-04a: Chưa JOIN khi PLACE_BID → không PLACE_BID_SUCCESS (NOT_JOINED / PLACE_BID_FAILED)")
        void placeBidWithoutJoin_noSuccess() {
            Auction auction = givenRunningAuctionInManager("ws_e1_s", 2_000_000L, 5_000_000L);
            NormalUser bidder = buildUserWithBalance("ws_e1_b", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(bidder.getId(), bidder.getUsername(), "NORMAL_USER");

            bidHandler.handle(session, PacketType.PLACE_BID,
                    GSON.toJsonTree(new BidDTOs.BidRequestDTO(auction.getId(), 3_000_000L)), "rid");

            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeastOnce()).send(cap.capture());
            assertThat(cap.getAllValues().stream().noneMatch(s -> s.contains("PLACE_BID_SUCCESS")))
                    .isTrue();
            assertThat(cap.getAllValues().stream().anyMatch(s -> s.contains("PLACE_BID_FAILED")
                    || s.contains(ErrorDTO.NOT_JOINED_AUCTION)))
                    .isTrue();
        }

        @Test
        @DisplayName("TC-WS-04b: Bid quá thấp (InvalidBid) → PLACE_BID_FAILED chứa BID_TOO_LOW")
        void placeBidTooLow_returnsBidTooLow() {
            Auction auction = givenRunningAuctionInManager("ws_e2_s", 2_000_000L, 5_000_000L);
            NormalUser bidder = buildUserWithBalance("ws_e2_b", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(bidder.getId(), bidder.getUsername(), "NORMAL_USER");
            bidHandler.handle(session, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "j");

            bidHandler.handle(session, PacketType.PLACE_BID,
                    GSON.toJsonTree(new BidDTOs.BidRequestDTO(auction.getId(), 1L)), "bad");

            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeastOnce()).send(cap.capture());
            assertThat(cap.getAllValues().stream().anyMatch(s -> s.contains("PLACE_BID_FAILED")
                    && s.contains(ErrorDTO.BID_TOO_LOW))).isTrue();
        }

        @Test
        @DisplayName("TC-WS-04c: JOIN auctionId không có trong AuctionManager → SYSTEM_ERROR AUCTION_NOT_FOUND")
        void joinUnknownAuction_systemError() {
            NormalUser bidder = buildUserWithBalance("ws_e3_b", 50_000_000L, userDAO);
            String ghostId = UUID.randomUUID().toString();

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(bidder.getId(), bidder.getUsername(), "NORMAL_USER");

            bidHandler.handle(session, PacketType.JOIN_AUCTION, new JsonPrimitive(ghostId), "rid");

            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeastOnce()).send(cap.capture());
            assertThat(cap.getAllValues().stream().anyMatch(s -> s.contains("SYSTEM_ERROR")
                    && s.contains(ErrorDTO.AUCTION_NOT_FOUND))).isTrue();
        }

        @Test
        @DisplayName("TC-WS-04d: JOIN thành công rồi LEAVE — PLACE_BID tiếp theo thất bại (đã rời phiên)")
        void leaveThenBid_fails() {
            Auction auction = givenRunningAuctionInManager("ws_e4_s", 2_000_000L, 5_000_000L);
            NormalUser bidder = buildUserWithBalance("ws_e4_b", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(bidder.getId(), bidder.getUsername(), "NORMAL_USER");

            bidHandler.handle(session, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "j");
            bidHandler.handle(session, PacketType.LEAVE_AUCTION, new JsonPrimitive(auction.getId()), "l");
            bidHandler.handle(session, PacketType.PLACE_BID,
                    GSON.toJsonTree(new BidDTOs.BidRequestDTO(auction.getId(), 3_000_000L)), "b");

            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, atLeastOnce()).send(cap.capture());
            assertThat(cap.getAllValues().stream().noneMatch(s -> s.contains("PLACE_BID_SUCCESS")))
                    .isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-WS-05 — VIEWER_COUNT_UPDATE (realtime active viewer count)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TC-WS-05 VIEWER_COUNT_UPDATE — realtime active viewer count")
    class ViewerCountBroadcast {

        @Test
        @DisplayName("TC-WS-05a: JOIN → VIEWER_COUNT_UPDATE với activeViewerCount = 1 được broadcast")
        void join_broadcastsViewerCountUpdate() throws Exception {
            Auction auction = givenRunningAuctionInManager("ws_vc_a", 2_000_000L, 5_000_000L);
            NormalUser bidder = buildUserWithBalance("ws_vc_ab", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(bidder.getId(), bidder.getUsername(), "NORMAL_USER");

            bidHandler.handle(session, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "j-vc");

            // FIX TIMING: VIEWER_COUNT_UPDATE được gửi qua broadcastToAuctionAsync (thread pool riêng).
            // atLeastOnce() return ngay sau JOIN_AUCTION_SUCCESS (send đầu tiên) trước khi
            // VIEWER_COUNT_UPDATE kịp đến → assertion kiểm tra cap.getAllValues() sẽ miss nó.
            // atLeast(2): đợi đến khi CẢ HAI send hoàn thành: [1] JOIN_SUCCESS + [2] VIEWER_COUNT_UPDATE.
            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, timeout(2000).atLeast(2)).send(cap.capture());
            assertThat(cap.getAllValues().stream().anyMatch(s -> s.contains("VIEWER_COUNT_UPDATE")))
                    .as("JOIN phải trigger VIEWER_COUNT_UPDATE broadcast")
                    .isTrue();
            assertThat(cap.getAllValues().stream()
                    .filter(s -> s.contains("VIEWER_COUNT_UPDATE"))
                    .anyMatch(s -> s.contains("\"activeViewerCount\":1")))
                    .as("activeViewerCount phải = 1 sau khi 1 người join")
                    .isTrue();
        }

        @Test
        @DisplayName("TC-WS-05b: WATCH → VIEWER_COUNT_UPDATE với activeViewerCount = 1 được broadcast")
        void watch_broadcastsViewerCountUpdate() throws Exception {
            Auction auction = givenRunningAuctionInManager("ws_vc_w", 2_000_000L, 5_000_000L);
            NormalUser watcher = buildUserWithBalance("ws_vc_wb", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            ClientSession session = sessionManager.getByConnection(webSocket);
            session.authenticate(watcher.getId(), watcher.getUsername(), "NORMAL_USER");

            bidHandler.handle(session, PacketType.WATCH_AUCTION, new JsonPrimitive(auction.getId()), "w-vc");

            // FIX TIMING: atLeast(2) để đợi cả WATCH_AUCTION_SUCCESS + VIEWER_COUNT_UPDATE async.
            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(webSocket, timeout(2000).atLeast(2)).send(cap.capture());
            assertThat(cap.getAllValues().stream().anyMatch(s -> s.contains("VIEWER_COUNT_UPDATE")))
                    .as("WATCH phải trigger VIEWER_COUNT_UPDATE broadcast")
                    .isTrue();
        }

        @Test
        @DisplayName("TC-WS-05c: LEAVE → peer còn lại nhận VIEWER_COUNT_UPDATE với count đã giảm")
        void leave_peerReceivesDecrementedCount() throws Exception {
            Auction auction = givenRunningAuctionInManager("ws_vc_lv", 2_000_000L, 5_000_000L);
            NormalUser bidderA = buildUserWithBalance("ws_vc_la", 50_000_000L, userDAO);
            NormalUser bidderB = buildUserWithBalance("ws_vc_lb", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            sessionManager.register(webSocketPeer);
            ClientSession sessA = sessionManager.getByConnection(webSocket);
            ClientSession sessB = sessionManager.getByConnection(webSocketPeer);
            sessA.authenticate(bidderA.getId(), bidderA.getUsername(), "NORMAL_USER");
            sessB.authenticate(bidderB.getId(), bidderB.getUsername(), "NORMAL_USER");

            bidHandler.handle(sessA, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "ja-lv");
            bidHandler.handle(sessB, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "jb-lv");

            // Xóa tất cả capture trước action cần test để assert rõ ràng hơn
            clearInvocations(webSocket, webSocketPeer);

            // Act — A rời phiên
            bidHandler.handle(sessA, PacketType.LEAVE_AUCTION, new JsonPrimitive(auction.getId()), "lv-vc");

            // B (vẫn đang watch) phải nhận VIEWER_COUNT_UPDATE với count = 1
            ArgumentCaptor<String> peerCap = ArgumentCaptor.forClass(String.class);
            verify(webSocketPeer, timeout(2000).atLeastOnce()).send(peerCap.capture());
            assertThat(peerCap.getAllValues().stream()
                    .filter(s -> s.contains("VIEWER_COUNT_UPDATE"))
                    .anyMatch(s -> s.contains("\"activeViewerCount\":1")))
                    .as("Sau khi A rời, B phải nhận VIEWER_COUNT_UPDATE với activeViewerCount = 1")
                    .isTrue();
        }

        @Test
        @DisplayName("TC-WS-05d: VIEWER_COUNT_UPDATE phản ánh live count, không phải historical viewer_count")
        void viewerCountUpdate_isLiveCount_notHistoricalAccumulated() throws Exception {
            // Historical counter (auction.getViewerCount()) chỉ tăng, không giảm.
            // VIEWER_COUNT_UPDATE phải dùng live connection count — giảm khi user rời.
            Auction auction = givenRunningAuctionInManager("ws_vc_d", 2_000_000L, 5_000_000L);
            NormalUser bidderA = buildUserWithBalance("ws_vc_da", 50_000_000L, userDAO);
            NormalUser bidderB = buildUserWithBalance("ws_vc_db", 50_000_000L, userDAO);

            sessionManager.register(webSocket);
            sessionManager.register(webSocketPeer);
            ClientSession sessA = sessionManager.getByConnection(webSocket);
            ClientSession sessB = sessionManager.getByConnection(webSocketPeer);
            sessA.authenticate(bidderA.getId(), bidderA.getUsername(), "NORMAL_USER");
            sessB.authenticate(bidderB.getId(), bidderB.getUsername(), "NORMAL_USER");

            bidHandler.handle(sessA, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "ja-d");
            bidHandler.handle(sessB, PacketType.JOIN_AUCTION, new JsonPrimitive(auction.getId()), "jb-d");

            // FIX TIMING: clearInvocations() gọi ngay sau join có thể chạy trước khi
            // broadcastToAuctionAsync từ join thứ 2 hoàn thành.
            // Nếu VIEWER_COUNT_UPDATE(count=2) từ join B chạy SAU clearInvocations,
            // nó sẽ bị peerCap capture → assertion noneMatch("activeViewerCount\":2") sẽ fail.
            //
            // Fix: đợi sessB nhận đủ 2 send (JOIN_SUCCESS + VIEWER_COUNT_UPDATE(2)) từ join
            // TRƯỚC KHI clear → đảm bảo mọi async broadcast từ join đã flush xong.
            ArgumentCaptor<String> drainCap = ArgumentCaptor.forClass(String.class);
            verify(webSocketPeer, timeout(2000).atLeast(2)).send(drainCap.capture());

            clearInvocations(webSocket, webSocketPeer);

            // A rời — historical count vẫn = 2, live count phải = 1
            bidHandler.handle(sessA, PacketType.LEAVE_AUCTION, new JsonPrimitive(auction.getId()), "lv-d");

            // Historical counter không giảm (thiết kế đúng)
            assertThat(auction.getViewerCount())
                    .as("Historical viewer_count không giảm — dùng cho analytics, không cho realtime")
                    .isEqualTo(2);

            // Live count trong VIEWER_COUNT_UPDATE phải = 1, không phải 2
            ArgumentCaptor<String> peerCap = ArgumentCaptor.forClass(String.class);
            verify(webSocketPeer, timeout(2000).atLeastOnce()).send(peerCap.capture());
            assertThat(peerCap.getAllValues().stream()
                    .filter(s -> s.contains("VIEWER_COUNT_UPDATE"))
                    .anyMatch(s -> s.contains("\"activeViewerCount\":1")))
                    .as("VIEWER_COUNT_UPDATE phải chứa live count = 1 (không phải historical = 2)")
                    .isTrue();
            assertThat(peerCap.getAllValues().stream()
                    .filter(s -> s.contains("VIEWER_COUNT_UPDATE"))
                    .noneMatch(s -> s.contains("\"activeViewerCount\":2")))
                    .as("VIEWER_COUNT_UPDATE không được chứa stale historical count = 2")
                    .isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo phiên RUNNING trên DB và đăng ký vào {@link AuctionManager} — bắt buộc vì
     * {@link BidHandler} resolve auction qua memory singleton, không đọc lại từ DB trong {@code requireAuction}.
     */
    private Auction givenRunningAuctionInManager(String sellerPrefix, long startingPrice, long reservePrice) {
        NormalUser seller = buildUserWithBalance(sellerPrefix, 80_000_000L, userDAO);
        String itemId = buildItem(seller.getId(), "WS-" + sellerPrefix, startingPrice, itemDAO);
        Item item = itemDAO.findItemById(itemId);
        Auction auction = Auction.create(item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(2),
                reservePrice);
        auctionDAO.createAuction(auction);
        AuctionManager.getInstance().registerAuction(auction);
        auctionService.startAuction(auction);
        trackAuction(auction.getId());
        return auction;
    }

    /**
     * Xả các singleton có state test (auction, user cache, auto-bid registry, lock map) để test độc lập.
     */
    private void resetInMemorySingletons() throws Exception {
        clearMapField(AuctionManager.getInstance(), "allAuctions");
        clearMapField(AuctionManager.getInstance(), "allUsers");

        clearMapField(AutoBidRegistry.getInstance(), "registry");
        clearMapField(AuctionLockRegistry.getInstance(), "locks");
    }

    private static void clearMapField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        Object map = f.get(target);
        if (map instanceof Map<?, ?> m) {
            m.clear();
        }
    }
}