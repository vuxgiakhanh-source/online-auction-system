package com.group13.auction.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.item.ItemFactory;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.network.server.handler.AuctionHandler;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.AuctionService;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link AuctionHandler} — CREATE / GET_LIST / GET_DETAIL /
 * UPDATE / CANCEL_REQUEST / ADMIN_CANCEL / ADMIN_GET_ALL.
 *
 * <p>Chiến lược: mock AuctionService, AccountService, ItemFactory.
 * SessionManager singleton thật — register/unregister mock WebSocket.
 * AuctionManager in-memory thật — clear sau mỗi test để cô lập.
 * Không chạm DB, không Testcontainers.
 *
 * <p>Cùng pattern với {@link PaymentHandlerDispatchTest} và
 * {@link UserAdminHandlerDispatchTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuctionHandler — dispatch & guard")
class AuctionHandlerDispatchTest {

    private static final Gson GSON = PacketCodec.gson();

    @Mock AuctionService  auctionService;
    @Mock AccountService  accountService;
    @Mock ItemFactory     itemFactory;
    @Mock WebSocket       webSocket;

    SessionManager sessionManager = SessionManager.getInstance();

    private NormalUser seller;
    private Admin      staffAdmin;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();
        reset(webSocket, auctionService, accountService, itemFactory);
        lenient().when(webSocket.isOpen()).thenReturn(true);
        lenient().when(webSocket.getRemoteSocketAddress())
                .thenReturn(new java.net.InetSocketAddress("127.0.0.1", 9090));

        sessionManager.unregister(webSocket);
        sessionManager.register(webSocket);

        seller     = TestFixture.normalSeller("auc_seller_01");
        staffAdmin = makeStaffAdmin("auc_staff_01");

        AuctionManager.getInstance().addToUserList(seller);
        AuctionManager.getInstance().addToUserList(staffAdmin);

        sessionManager.authenticate(webSocket, seller.getId(),
                seller.getUsername(), "NORMAL_USER");
    }

    @AfterEach
    void tearDown() throws Exception {
        sessionManager.unregister(webSocket);
        TestFixture.resetSystemAdmin();
        clearAuctionManagerUsers();
        clearAuctionManagerAuctions();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Tạo STAFF Admin không cần DB. */
    private static Admin makeStaffAdmin(String username) {
        return Admin.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("admin_pass"),
                username + "@admin.test",
                User.AccountStatus.ACTIVE,
                5.0,
                Admin.LEVEL_STAFF,
                null
        );
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

    private AuctionHandler newHandler() {
        return new AuctionHandler(auctionService, accountService, sessionManager, itemFactory);
    }

    private ClientSession session() {
        return sessionManager.getByConnection(webSocket);
    }

    private String captureLastSent() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(webSocket, atLeastOnce()).send(cap.capture());
        return cap.getValue();
    }

    /** Đăng nhập lại session dưới role ADMIN. */
    private void reAuthAsAdmin() {
        sessionManager.authenticate(webSocket, staffAdmin.getId(),
                staffAdmin.getUsername(), "ADMIN_STAFF");
    }

    // =========================================================================
    // supports()
    // =========================================================================

    @Nested
    @DisplayName("supports()")
    class SupportsTest {

        @Test
        @DisplayName("Hỗ trợ đúng 7 PacketType của AuctionHandler")
        void supports_all7Types() {
            AuctionHandler h = newHandler();
            assertThat(h.supports(PacketType.CREATE_AUCTION)).isTrue();
            assertThat(h.supports(PacketType.GET_AUCTION_LIST)).isTrue();
            assertThat(h.supports(PacketType.GET_AUCTION_DETAIL)).isTrue();
            assertThat(h.supports(PacketType.UPDATE_AUCTION)).isTrue();
            assertThat(h.supports(PacketType.CANCEL_AUCTION_REQUEST)).isTrue();
            assertThat(h.supports(PacketType.ADMIN_CANCEL_AUCTION)).isTrue();
            assertThat(h.supports(PacketType.ADMIN_GET_ALL_AUCTIONS)).isTrue();
        }

        @Test
        @DisplayName("Không hỗ trợ PLACE_BID, LOGIN, DEPOSIT")
        void supports_rejectsUnrelated() {
            AuctionHandler h = newHandler();
            assertThat(h.supports(PacketType.PLACE_BID)).isFalse();
            assertThat(h.supports(PacketType.LOGIN)).isFalse();
            assertThat(h.supports(PacketType.DEPOSIT)).isFalse();
        }
    }

    // =========================================================================
    // Unauthenticated guard
    // =========================================================================

    @Nested
    @DisplayName("Unauthenticated — mọi packet bị chặn trước khi vào service")
    class UnauthenticatedGuardTest {

        @BeforeEach
        void deauth() {
            sessionManager.unregister(webSocket);
            sessionManager.register(webSocket);
        }

        @Test
        @DisplayName("CREATE_AUCTION chưa đăng nhập → SYSTEM_ERROR UNAUTHORIZED, service không gọi")
        void create_unauthenticated_blocked() {
            newHandler().handle(session(), PacketType.CREATE_AUCTION,
                    new JsonPrimitive("{}"), "r-unauth-1");

            assertThat(captureLastSent())
                    .contains(PacketType.SYSTEM_ERROR.name())
                    .contains(ErrorDTO.UNAUTHORIZED);
            verifyNoInteractions(auctionService);
        }

        @Test
        @DisplayName("UPDATE_AUCTION chưa đăng nhập → SYSTEM_ERROR, service không gọi")
        void update_unauthenticated_blocked() {
            newHandler().handle(session(), PacketType.UPDATE_AUCTION,
                    new JsonPrimitive("{}"), "r-unauth-2");

            assertThat(captureLastSent()).contains(PacketType.SYSTEM_ERROR.name());
            verifyNoInteractions(auctionService);
        }

        @Test
        @DisplayName("CANCEL_AUCTION_REQUEST chưa đăng nhập → bị chặn")
        void cancelRequest_unauthenticated_blocked() {
            newHandler().handle(session(), PacketType.CANCEL_AUCTION_REQUEST,
                    new JsonPrimitive("{}"), "r-unauth-3");

            assertThat(captureLastSent()).contains(PacketType.SYSTEM_ERROR.name());
            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("GET_AUCTION_LIST chưa đăng nhập → bị chặn bởi auth guard")
        void getList_unauthenticated_blocked() {
            newHandler().handle(session(), PacketType.GET_AUCTION_LIST,
                    JsonNull.INSTANCE, "r-unauth-4");

            assertThat(captureLastSent()).contains(PacketType.SYSTEM_ERROR.name());
        }
    }

    // =========================================================================
    // GET_AUCTION_LIST
    // =========================================================================

    @Nested
    @DisplayName("GET_AUCTION_LIST")
    class GetAuctionListTest {

        @Test
        @DisplayName("payload empty → GET_AUCTION_LIST_SUCCESS (default, không filter)")
        void getList_nullPayload_success() {
            AuctionDTOs.AuctionListRequestDTO req = new AuctionDTOs.AuctionListRequestDTO();
            newHandler().handle(session(), PacketType.GET_AUCTION_LIST,
                    GSON.toJsonTree(req), "r-list-1");

            assertThat(captureLastSent()).contains(PacketType.GET_AUCTION_LIST_SUCCESS.name());
        }

        @Test
        @DisplayName("filter status RUNNING → GET_AUCTION_LIST_SUCCESS")
        void getList_withStatusFilter_success() {
            AuctionDTOs.AuctionListRequestDTO req = new AuctionDTOs.AuctionListRequestDTO();
            req.setStatusFilter("RUNNING");

            newHandler().handle(session(), PacketType.GET_AUCTION_LIST,
                    GSON.toJsonTree(req), "r-list-2");

            assertThat(captureLastSent()).contains(PacketType.GET_AUCTION_LIST_SUCCESS.name());
        }

        @Test
        @DisplayName("filter status không hợp lệ → SYSTEM_ERROR")
        void getList_invalidStatusFilter_systemError() {
            AuctionDTOs.AuctionListRequestDTO req = new AuctionDTOs.AuctionListRequestDTO();
            req.setStatusFilter("INVALID_STATUS_XYZ");

            newHandler().handle(session(), PacketType.GET_AUCTION_LIST,
                    GSON.toJsonTree(req), "r-list-3");

            assertThat(captureLastSent()).contains(PacketType.SYSTEM_ERROR.name());
        }

        @Test
        @DisplayName("requestId được echo trong response")
        void getList_requestIdEchoed() {
            AuctionDTOs.AuctionListRequestDTO req = new AuctionDTOs.AuctionListRequestDTO();
            newHandler().handle(session(), PacketType.GET_AUCTION_LIST,
                    GSON.toJsonTree(req), "custom-rid-99");

            assertThat(captureLastSent()).contains("custom-rid-99");
        }

        @Test
        @DisplayName("Có auction OPEN trong hệ thống → list chứa auction đó")
        void getList_withOpenAuction_containsIt() {
            Auction open = TestFixture.openAuction(seller, 500_000L);
            AuctionManager.getInstance().registerAuction(open);

            AuctionDTOs.AuctionListRequestDTO req = new AuctionDTOs.AuctionListRequestDTO();
            newHandler().handle(session(), PacketType.GET_AUCTION_LIST,
                    GSON.toJsonTree(req), "r-list-5");

            assertThat(captureLastSent())
                    .contains(PacketType.GET_AUCTION_LIST_SUCCESS.name())
                    .contains(open.getId());
        }
    }

    // =========================================================================
    // GET_AUCTION_DETAIL
    // =========================================================================

    @Nested
    @DisplayName("GET_AUCTION_DETAIL")
    class GetAuctionDetailTest {

        @Test
        @DisplayName("auctionId tồn tại → GET_AUCTION_DETAIL_SUCCESS chứa auctionId")
        void getDetail_existingAuction_success() {
            Auction auction = TestFixture.openAuction(seller, 500_000L);
            AuctionManager.getInstance().registerAuction(auction);

            newHandler().handle(session(), PacketType.GET_AUCTION_DETAIL,
                    GSON.toJsonTree(auction.getId()), "r-detail-1");

            assertThat(captureLastSent())
                    .contains(PacketType.GET_AUCTION_DETAIL_SUCCESS.name())
                    .contains(auction.getId());
        }

        @Test
        @DisplayName("auctionId không tồn tại → GET_AUCTION_DETAIL_FAILED AUCTION_NOT_FOUND")
        void getDetail_notFound_failed() {
            newHandler().handle(session(), PacketType.GET_AUCTION_DETAIL,
                    GSON.toJsonTree("non-existent-" + UUID.randomUUID()), "r-detail-2");

            assertThat(captureLastSent())
                    .contains(PacketType.GET_AUCTION_DETAIL_FAILED.name())
                    .contains(ErrorDTO.AUCTION_NOT_FOUND);
        }

        @Test
        @DisplayName("requestId được echo trong response")
        void getDetail_requestIdEchoed() {
            Auction auction = TestFixture.openAuction(seller, 500_000L);
            AuctionManager.getInstance().registerAuction(auction);

            newHandler().handle(session(), PacketType.GET_AUCTION_DETAIL,
                    GSON.toJsonTree(auction.getId()), "echo-detail-rid");

            assertThat(captureLastSent()).contains("echo-detail-rid");
        }
    }

    // =========================================================================
    // CREATE_AUCTION
    // =========================================================================

    @Nested
    @DisplayName("CREATE_AUCTION")
    class CreateAuctionTest {

        @Test
        @DisplayName("happy path: factory + service thành công → CREATE_AUCTION_SUCCESS")
        void create_happyPath_success() throws Exception {
            Auction auction = TestFixture.openAuction(seller, 500_000L);
            Item item = auction.getItem();

            when(itemFactory.create(any(), any(), any(), anyLong(), any(), any(), any()))
                    .thenReturn(item);
            when(auctionService.createAuction(any(), any(), any(), any(), anyLong()))
                    .thenReturn(auction);

            AuctionHandler handler = newHandler();
            injectMockItemDAO(handler, true);

            handler.handle(session(), PacketType.CREATE_AUCTION,
                    validCreatePayload(), "r-create-1");

            assertThat(captureLastSent()).contains(PacketType.CREATE_AUCTION_SUCCESS.name());
            verify(auctionService).createAuction(eq(seller), eq(item), any(), any(), anyLong());
        }

        @Test
        @DisplayName("itemDAO.addItem() trả false → CREATE_AUCTION_FAILED INTERNAL_ERROR, service không gọi")
        void create_itemSaveFails_internalError() throws Exception {
            Item item = TestFixture.openAuction(seller, 500_000L).getItem();
            when(itemFactory.create(any(), any(), any(), anyLong(), any(), any(), any()))
                    .thenReturn(item);

            AuctionHandler handler = newHandler();
            injectMockItemDAO(handler, false);

            handler.handle(session(), PacketType.CREATE_AUCTION,
                    validCreatePayload(), "r-create-2");

            assertThat(captureLastSent())
                    .contains(PacketType.CREATE_AUCTION_FAILED.name())
                    .contains(ErrorDTO.INTERNAL_ERROR);
            verifyNoInteractions(auctionService);
        }

        @Test
        @DisplayName("auctionService ném IllegalArgumentException → CREATE_AUCTION_FAILED VALIDATION_ERROR")
        void create_serviceIllegalArgument_validationError() throws Exception {
            Item item = TestFixture.openAuction(seller, 500_000L).getItem();
            when(itemFactory.create(any(), any(), any(), anyLong(), any(), any(), any()))
                    .thenReturn(item);
            when(auctionService.createAuction(any(), any(), any(), any(), anyLong()))
                    .thenThrow(new IllegalArgumentException("endTime phải sau startTime"));

            AuctionHandler handler = newHandler();
            injectMockItemDAO(handler, true);

            handler.handle(session(), PacketType.CREATE_AUCTION,
                    validCreatePayload(), "r-create-3");

            assertThat(captureLastSent())
                    .contains(PacketType.CREATE_AUCTION_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("auctionService ném IllegalStateException (seller bị ban) → CREATE_AUCTION_FAILED VALIDATION_ERROR")
        void create_serviceIllegalState_validationError() throws Exception {
            Item item = TestFixture.openAuction(seller, 500_000L).getItem();
            when(itemFactory.create(any(), any(), any(), anyLong(), any(), any(), any()))
                    .thenReturn(item);
            when(auctionService.createAuction(any(), any(), any(), any(), anyLong()))
                    .thenThrow(new IllegalStateException("Seller không đủ điều kiện"));

            AuctionHandler handler = newHandler();
            injectMockItemDAO(handler, true);

            handler.handle(session(), PacketType.CREATE_AUCTION,
                    validCreatePayload(), "r-create-4");

            assertThat(captureLastSent())
                    .contains(PacketType.CREATE_AUCTION_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("Admin session gọi CREATE_AUCTION → SYSTEM_ERROR UNAUTHORIZED")
        void create_adminSession_unauthorized() {
            reAuthAsAdmin();

            newHandler().handle(session(), PacketType.CREATE_AUCTION,
                    validCreatePayload(), "r-create-5");

            assertThat(captureLastSent())
                    .contains(PacketType.SYSTEM_ERROR.name())
                    .contains(ErrorDTO.UNAUTHORIZED);
            verifyNoInteractions(auctionService);
        }

        @Test
        @DisplayName("null payload → CREATE_AUCTION_FAILED (không NPE)")
        void create_nullPayload_gracefulError() {
            newHandler().handle(session(), PacketType.CREATE_AUCTION, null, "r-create-6");

            assertThat(captureLastSent()).contains(PacketType.CREATE_AUCTION_FAILED.name());
        }

        @Test
        @DisplayName("imageUrls null trong request → vẫn CREATE_AUCTION_SUCCESS (backward-compat)")
        void create_nullImageUrls_backwardCompat() throws Exception {
            Auction auction = TestFixture.openAuction(seller, 500_000L);
            Item item = auction.getItem();
            when(itemFactory.create(any(), any(), any(), anyLong(), any(), any(), any()))
                    .thenReturn(item);
            when(auctionService.createAuction(any(), any(), any(), any(), anyLong()))
                    .thenReturn(auction);

            AuctionHandler handler = newHandler();
            injectMockItemDAO(handler, true);

            // payload không set imageUrls → null
            handler.handle(session(), PacketType.CREATE_AUCTION,
                    createPayloadWithImages(null), "r-create-img-1");

            assertThat(captureLastSent()).contains(PacketType.CREATE_AUCTION_SUCCESS.name());
        }

        @Test
        @DisplayName("imageUrls vượt MAX_IMAGES (4 ảnh) → CREATE_AUCTION_FAILED VALIDATION_ERROR, service không gọi")
        void create_tooManyImages_validationError() throws Exception {
            AuctionHandler handler = newHandler();
            injectMockItemDAO(handler, true);

            handler.handle(session(), PacketType.CREATE_AUCTION,
                    createPayloadWithImages(java.util.List.of(
                            "/uploads/items/a.jpg",
                            "/uploads/items/b.jpg",
                            "/uploads/items/c.jpg",
                            "/uploads/items/d.jpg" // vượt MAX_IMAGES=3
                    )), "r-create-img-2");

            assertThat(captureLastSent())
                    .contains(PacketType.CREATE_AUCTION_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
            verifyNoInteractions(auctionService);
        }

        @Test
        @DisplayName("imageUrls chứa URL path-traversal → CREATE_AUCTION_FAILED VALIDATION_ERROR")
        void create_maliciousImageUrl_validationError() throws Exception {
            AuctionHandler handler = newHandler();
            injectMockItemDAO(handler, true);

            handler.handle(session(), PacketType.CREATE_AUCTION,
                    createPayloadWithImages(java.util.List.of("../../etc/passwd")),
                    "r-create-img-3");

            assertThat(captureLastSent())
                    .contains(PacketType.CREATE_AUCTION_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
            verifyNoInteractions(auctionService);
        }

        @Test
        @DisplayName("imageUrls hợp lệ (2 ảnh) → itemFactory được gọi với imageUrls, CREATE_AUCTION_SUCCESS")
        void create_validImageUrls_passedToFactory() throws Exception {
            Auction auction = TestFixture.openAuction(seller, 500_000L);
            Item item = auction.getItem();
            java.util.List<String> imgs = java.util.List.of(
                    "/uploads/items/x.jpg", "/uploads/items/y.png");

            when(itemFactory.create(any(), any(), any(), anyLong(), any(), any(), any()))
                    .thenReturn(item);
            when(auctionService.createAuction(any(), any(), any(), any(), anyLong()))
                    .thenReturn(auction);

            AuctionHandler handler = newHandler();
            injectMockItemDAO(handler, true);

            handler.handle(session(), PacketType.CREATE_AUCTION,
                    createPayloadWithImages(imgs), "r-create-img-4");

            assertThat(captureLastSent()).contains(PacketType.CREATE_AUCTION_SUCCESS.name());
            // imageUrls phải được truyền vào factory (arg cuối)
            verify(itemFactory).create(any(), any(), any(), anyLong(), any(), any(),
                    argThat(list -> list instanceof java.util.List && ((java.util.List<?>) list).size() == 2));
        }

        // ── Private helpers ──────────────────────────────────────────────────

        private JsonElement validCreatePayload() {
            return createPayloadWithImages(java.util.List.of("/uploads/items/img1.jpg"));
        }

        private JsonElement createPayloadWithImages(java.util.List<String> imageUrls) {
            AuctionDTOs.CreateAuctionRequestDTO req = new AuctionDTOs.CreateAuctionRequestDTO();
            req.setItemName("Tranh sơn dầu cổ điển");
            req.setItemDescription("Mô tả chi tiết sản phẩm");
            req.setItemCategory("ART");
            req.setStartingPrice(500_000);
            req.setReservePrice(800_000);
            req.setStartTime(LocalDateTime.now().plusHours(1));
            req.setEndTime(LocalDateTime.now().plusHours(25));
            req.setImageUrls(imageUrls);
            return GSON.toJsonTree(req);
        }

        private void injectMockItemDAO(AuctionHandler handler, boolean saveSuccess)
                throws Exception {
            com.group13.auction.dao.ItemDAO mockItemDAO =
                    mock(com.group13.auction.dao.ItemDAO.class);
            // addItem 7-arg (có imageUrls) — đây là method handler thực sự gọi
            lenient().when(mockItemDAO.addItem(any(), any(), any(), any(), anyLong(), any(), any()))
                    .thenReturn(saveSuccess);
            // addItem 6-arg backward-compat (không ảnh) — cũng stub phòng khi test khác gọi
            lenient().when(mockItemDAO.addItem(any(), any(), any(), any(), anyLong(), any()))
                    .thenReturn(saveSuccess);
            Field f = AuctionHandler.class.getDeclaredField("itemDAO");
            f.setAccessible(true);
            f.set(handler, mockItemDAO);
        }
    }

    // =========================================================================
    // UPDATE_AUCTION
    // =========================================================================

    @Nested
    @DisplayName("UPDATE_AUCTION")
    class UpdateAuctionTest {

        @Test
        @DisplayName("auctionId không tồn tại → UPDATE_AUCTION_FAILED VALIDATION_ERROR")
        void update_auctionNotFound_validationError() {
            AuctionDTOs.UpdateAuctionDTO req = new AuctionDTOs.UpdateAuctionDTO();
            req.setAuctionId("non-existent-" + UUID.randomUUID());
            req.setNewEndTime(LocalDateTime.now().plusHours(48));

            newHandler().handle(session(), PacketType.UPDATE_AUCTION,
                    GSON.toJsonTree(req), "r-update-1");

            assertThat(captureLastSent())
                    .contains(PacketType.UPDATE_AUCTION_FAILED.name())
                    .contains(ErrorDTO.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("auction đang RUNNING → UPDATE_AUCTION_FAILED (chỉ OPEN mới sửa được)")
        void update_runningAuction_failed() {
            Auction running = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionManager.getInstance().registerAuction(running);

            AuctionDTOs.UpdateAuctionDTO req = new AuctionDTOs.UpdateAuctionDTO();
            req.setAuctionId(running.getId());
            req.setNewEndTime(LocalDateTime.now().plusDays(2));

            newHandler().handle(session(), PacketType.UPDATE_AUCTION,
                    GSON.toJsonTree(req), "r-update-2");

            assertThat(captureLastSent()).contains(PacketType.UPDATE_AUCTION_FAILED.name());
        }

        @Test
        @DisplayName("auction OPEN + newEndTime hợp lệ → UPDATE_AUCTION_SUCCESS")
        void update_openAuction_validEndTime_success() throws Exception {
            Auction open = TestFixture.openAuction(seller, 500_000L);
            AuctionManager.getInstance().registerAuction(open);

            AuctionHandler handler = newHandler();
            injectMockAuctionDAO(handler);

            AuctionDTOs.UpdateAuctionDTO req = new AuctionDTOs.UpdateAuctionDTO();
            req.setAuctionId(open.getId());
            req.setNewEndTime(open.getStartTime().plusHours(30));

            handler.handle(session(), PacketType.UPDATE_AUCTION,
                    GSON.toJsonTree(req), "r-update-3");

            assertThat(captureLastSent()).contains(PacketType.UPDATE_AUCTION_SUCCESS.name());
        }

        @Test
        @DisplayName("Admin session gọi UPDATE → SYSTEM_ERROR UNAUTHORIZED")
        void update_adminSession_unauthorized() {
            reAuthAsAdmin();

            AuctionDTOs.UpdateAuctionDTO req = new AuctionDTOs.UpdateAuctionDTO();
            req.setAuctionId("any-id");

            newHandler().handle(session(), PacketType.UPDATE_AUCTION,
                    GSON.toJsonTree(req), "r-update-4");

            assertThat(captureLastSent()).contains(PacketType.SYSTEM_ERROR.name());
        }

        @Test
        @DisplayName("null payload → UPDATE_AUCTION_FAILED (không NPE)")
        void update_nullPayload_gracefulError() {
            newHandler().handle(session(), PacketType.UPDATE_AUCTION, null, "r-update-5");

            assertThat(captureLastSent()).contains(PacketType.UPDATE_AUCTION_FAILED.name());
        }

        private void injectMockAuctionDAO(AuctionHandler handler) throws Exception {
            com.group13.auction.dao.AuctionDAO mockDAO =
                    mock(com.group13.auction.dao.AuctionDAO.class);
            Field f = AuctionHandler.class.getDeclaredField("auctionDAO");
            f.setAccessible(true);
            f.set(handler, mockDAO);
        }
    }

    // =========================================================================
    // CANCEL_AUCTION_REQUEST
    // =========================================================================

    @Nested
    @DisplayName("CANCEL_AUCTION_REQUEST")
    class CancelAuctionRequestTest {

        @Test
        @DisplayName("happy path: accountService được gọi → CANCEL_AUCTION_REQUEST_SUCCESS")
        void cancelRequest_happyPath_success() {
            Auction open = TestFixture.openAuction(seller, 500_000L);
            AuctionManager.getInstance().registerAuction(open);

            AuctionDTOs.CancelAuctionRequestDTO req = new AuctionDTOs.CancelAuctionRequestDTO();
            req.setAuctionId(open.getId());
            req.setReason("Seller không muốn tiếp tục");

            newHandler().handle(session(), PacketType.CANCEL_AUCTION_REQUEST,
                    GSON.toJsonTree(req), "r-cancel-1");

            assertThat(captureLastSent())
                    .contains(PacketType.CANCEL_AUCTION_REQUEST_SUCCESS.name());
            verify(accountService).requestCancelAuction(eq(seller), eq(open), anyString());
        }

        @Test
        @DisplayName("null payload → CANCEL_AUCTION_REQUEST_FAILED (không NPE)")
        void cancelRequest_serviceThrows_failed() {
            // null payload → PacketCodec.fromElement trả null → NPE khi req.getAuctionId()
            // → handler catch Exception → CANCEL_AUCTION_REQUEST_FAILED
            newHandler().handle(session(), PacketType.CANCEL_AUCTION_REQUEST,
                    null, "r-cancel-2");

            assertThat(captureLastSent())
                    .contains(PacketType.CANCEL_AUCTION_REQUEST_FAILED.name());
        }

        @Test
        @DisplayName("Admin session gọi CANCEL_AUCTION_REQUEST → UNAUTHORIZED (chỉ Seller)")
        void cancelRequest_adminSession_unauthorized() {
            reAuthAsAdmin();

            AuctionDTOs.CancelAuctionRequestDTO req = new AuctionDTOs.CancelAuctionRequestDTO();
            req.setAuctionId("any-id");
            req.setReason("reason");

            newHandler().handle(session(), PacketType.CANCEL_AUCTION_REQUEST,
                    GSON.toJsonTree(req), "r-cancel-3");

            assertThat(captureLastSent()).contains(PacketType.SYSTEM_ERROR.name());
            verifyNoInteractions(accountService);
        }
    }

    // =========================================================================
    // ADMIN_CANCEL_AUCTION
    // =========================================================================

    @Nested
    @DisplayName("ADMIN_CANCEL_AUCTION")
    class AdminCancelAuctionTest {

        @Test
        @DisplayName("NORMAL_USER gọi ADMIN_CANCEL → bị chặn im lặng, auctionService không gọi")
        void adminCancel_normalUser_silentlyBlocked() {
            AuctionDTOs.AdminCancelAuctionDTO req = new AuctionDTOs.AdminCancelAuctionDTO();
            req.setAuctionId("any-id");
            req.setReason("FRAUDULENT_ITEM");

            newHandler().handle(session(), PacketType.ADMIN_CANCEL_AUCTION,
                    GSON.toJsonTree(req), "r-adcancel-1");

            verifyNoInteractions(auctionService);
        }

        @Test
        @DisplayName("Admin gọi ADMIN_CANCEL auction tồn tại → ADMIN_CANCEL_AUCTION_SUCCESS")
        void adminCancel_adminSession_existingAuction_success() {
            reAuthAsAdmin();

            Auction open = TestFixture.openAuction(seller, 500_000L);
            AuctionManager.getInstance().registerAuction(open);

            AuctionDTOs.AdminCancelAuctionDTO req = new AuctionDTOs.AdminCancelAuctionDTO();
            req.setAuctionId(open.getId());
            req.setReason("FRAUDULENT_ITEM");

            newHandler().handle(session(), PacketType.ADMIN_CANCEL_AUCTION,
                    GSON.toJsonTree(req), "r-adcancel-2");

            assertThat(captureLastSent())
                    .contains(PacketType.ADMIN_CANCEL_AUCTION_SUCCESS.name());
            verify(auctionService).cancelAuction(
                    any(Admin.class), eq(open), eq(Admin.CancelReason.FRAUDULENT_ITEM));
        }

        @Test
        @DisplayName("Admin gọi với reason enum không hợp lệ → ADMIN_CANCEL_AUCTION_FAILED")
        void adminCancel_invalidReason_failed() {
            reAuthAsAdmin();

            Auction open = TestFixture.openAuction(seller, 500_000L);
            AuctionManager.getInstance().registerAuction(open);

            AuctionDTOs.AdminCancelAuctionDTO req = new AuctionDTOs.AdminCancelAuctionDTO();
            req.setAuctionId(open.getId());
            req.setReason("INVALID_REASON_XYZ");

            newHandler().handle(session(), PacketType.ADMIN_CANCEL_AUCTION,
                    GSON.toJsonTree(req), "r-adcancel-3");

            assertThat(captureLastSent())
                    .contains(PacketType.ADMIN_CANCEL_AUCTION_FAILED.name());
        }
    }

    // =========================================================================
    // ADMIN_GET_ALL_AUCTIONS
    // =========================================================================

    @Nested
    @DisplayName("ADMIN_GET_ALL_AUCTIONS")
    class AdminGetAllAuctionsTest {

        @Test
        @DisplayName("NORMAL_USER gọi → bị chặn im lặng, không nhận SUCCESS")
        void adminGetAll_normalUser_blocked() {
            newHandler().handle(session(), PacketType.ADMIN_GET_ALL_AUCTIONS,
                    JsonNull.INSTANCE, "r-adgetall-1");

            try {
                String sent = captureLastSent();
                assertThat(sent).doesNotContain(PacketType.ADMIN_GET_ALL_AUCTIONS_SUCCESS.name());
            } catch (org.mockito.exceptions.verification.WantedButNotInvoked ignored) {
                // Không gửi gì cả — hành vi chặn im lặng hoàn toàn hợp lệ
            }
        }

        @Test
        @DisplayName("Admin gọi → ADMIN_GET_ALL_AUCTIONS_SUCCESS")
        void adminGetAll_adminSession_success() {
            reAuthAsAdmin();

            Auction open = TestFixture.openAuction(seller, 500_000L);
            AuctionManager.getInstance().registerAuction(open);

            newHandler().handle(session(), PacketType.ADMIN_GET_ALL_AUCTIONS,
                    JsonNull.INSTANCE, "r-adgetall-2");

            assertThat(captureLastSent())
                    .contains(PacketType.ADMIN_GET_ALL_AUCTIONS_SUCCESS.name());
        }

        @Test
        @DisplayName("Admin gọi — requestId được echo trong response")
        void adminGetAll_requestIdEchoed() {
            reAuthAsAdmin();

            newHandler().handle(session(), PacketType.ADMIN_GET_ALL_AUCTIONS,
                    JsonNull.INSTANCE, "echo-rid-777");

            assertThat(captureLastSent()).contains("echo-rid-777");
        }

        @Test
        @DisplayName("Admin gọi khi không có auction nào → vẫn trả SUCCESS với list rỗng")
        void adminGetAll_emptyList_stillSuccess() {
            reAuthAsAdmin();
            clearAuctionManagerAuctions();

            newHandler().handle(session(), PacketType.ADMIN_GET_ALL_AUCTIONS,
                    JsonNull.INSTANCE, "r-adgetall-4");

            assertThat(captureLastSent())
                    .contains(PacketType.ADMIN_GET_ALL_AUCTIONS_SUCCESS.name());
        }
    }
}