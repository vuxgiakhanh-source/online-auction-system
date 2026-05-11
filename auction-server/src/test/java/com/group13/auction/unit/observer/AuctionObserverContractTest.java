package com.group13.auction.unit.observer;

import com.group13.auction.observer.*;
import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent.AuctionEventType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests cho {@link AuctionObserver}.
 *
 * <h2>Chiến lược</h2>
 * <p>File này test <b>contract của interface</b>, không test implementation detail
 * của bất kỳ concrete observer nào. Mỗi test verify một điều khoản trong contract:
 *
 * <ol>
 *   <li><b>API completeness</b> — mọi concrete implementation phải có đủ hai method.</li>
 *   <li><b>Non-throwing contract</b> — không method nào được ném exception với bất kỳ event type.</li>
 *   <li><b>Null-safe contract</b> — observer không crash khi payload nullable = null.</li>
 *   <li><b>Polymorphism / LSP</b> — cùng event type qua tất cả implementation thay thế lẫn nhau
 *       mà không vi phạm contract.</li>
 *   <li><b>Observer isolation</b> — observer A không được ảnh hưởng state observer B.</li>
 *   <li><b>Event routing</b> — {@code onBidPlaced} nhận bid event,
 *       {@code onAuctionEnded} nhận auction lifecycle event; không method nào được
 *       bị gọi nhầm role của method kia.</li>
 *   <li><b>State immutability contract</b> — việc nhận event không được mutate
 *       state của Auction hay NormalUser được truyền vào.</li>
 *   <li><b>Determinism</b> — cùng input trong cùng trạng thái → hành vi nhất quán.</li>
 * </ol>
 *
 * <p>Không DB, không network, không filesystem. Mọi object là object thật.
 * Chỉ mock {@link com.group13.auction.service.iservice.IRatingService} qua
 * {@link TestFixture} vì nó là external dependency.
 */
@DisplayName("AuctionObserver — Interface Contract")
class AuctionObserverContractTest {

    // =========================================================================
    // Test infrastructure
    // =========================================================================

    private final ByteArrayOutputStream outCaptor = new ByteArrayOutputStream();
    private PrintStream originalOut;

    /** Shared fixtures — khởi tạo một lần cho toàn bộ nested class, không share state mutable. */
    private NormalUser seller;
    private NormalUser bidder;
    private Auction runningAuction;

    @BeforeEach
    void setUpStreams() throws Exception {
        originalOut = System.out;
        System.setOut(new PrintStream(outCaptor));

        TestFixture.bootstrapSystemAdmin();

        seller       = TestFixture.normalSeller("sellerAA1");
        bidder       = TestFixture.bidderWithBalance("bidderBB2", 5_000_000L);
        runningAuction = TestFixture.runningAuction(seller, 1_000_000L);
    }

    @AfterEach
    void tearDownStreams() throws Exception {
        System.setOut(originalOut);
        TestFixture.resetSystemAdmin();
    }

    // =========================================================================
    // Factory — tạo mọi concrete implementation từ code thật
    // =========================================================================

    /**
     * Tạo tất cả concrete {@link AuctionObserver} qua object thật.
     * Dùng làm @MethodSource cho parameterized contract tests.
     *
     * <p>Mỗi lần gọi tạo instance mới để test isolation.
     */
    static Stream<AuctionObserver> allConcreteObservers() throws Exception {
        // Bootstrap SystemAdmin Singleton trước khi build observers
        TestFixture.bootstrapSystemAdmin();

        NormalUser seller = TestFixture.normalSeller("sellerZZ1");
        NormalUser bidder = TestFixture.bidderWithBalance("bidderZZ2", 5_000_000L);

        Admin staffAdmin = Admin.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                "staffAdmin1",
                User.hashPassword("adminpass1"),
                "staff@test.com",
                User.AccountStatus.ACTIVE,
                5.0, Admin.LEVEL_STAFF, null);

        Admin masterAdmin = Admin.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                "masterAdm1",
                User.hashPassword("adminpass1"),
                "master@test.com",
                User.AccountStatus.ACTIVE,
                5.0, Admin.LEVEL_MASTER, null);

        SystemAdmin sysAdmin = SystemAdmin.getInstance();

        return Stream.of(
                new BidderObserver(bidder,  TestFixture.ratingServiceAllowAll()),
                new SellerObserver(seller,  TestFixture.ratingServiceAllowAll()),
                new AdminObserver(masterAdmin),
                new StaffObserver(staffAdmin),
                new SystemAdminObserver(sysAdmin)
        );
    }

    /** Helper truy xuất stdout đã capture. */
    private String captured() {
        return outCaptor.toString();
    }

    /** Helper tạo Admin STAFF nhanh. */
    private Admin staffAdmin(String username) {
        return Admin.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                username,
                User.hashPassword("adminpass1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                5.0, Admin.LEVEL_STAFF, null);
    }

    /** Helper tạo Admin MASTER nhanh. */
    private Admin masterAdmin(String username) {
        return Admin.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                username,
                User.hashPassword("adminpass1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                5.0, Admin.LEVEL_MASTER, null);
    }

    // =========================================================================
    // CONTRACT 1 — API completeness
    // Mọi implementation phải có đủ onBidPlaced + onAuctionEnded và compile.
    // =========================================================================

    @Nested
    @DisplayName("Contract 1 — API completeness")
    class ApiCompleteness {

        @Test
        @DisplayName("BidderObserver implements AuctionObserver")
        void bidderObserver_implementsAuctionObserver() {
            NormalUser user = TestFixture.normalBidder("bidderCC3");
            AuctionObserver obs = new BidderObserver(user, TestFixture.ratingServiceAllowAll());
            assertInstanceOf(AuctionObserver.class, obs);
        }

        @Test
        @DisplayName("SellerObserver implements AuctionObserver")
        void sellerObserver_implementsAuctionObserver() {
            NormalUser user = TestFixture.normalSeller("sellerDD4");
            AuctionObserver obs = new SellerObserver(user, TestFixture.ratingServiceAllowAll());
            assertInstanceOf(AuctionObserver.class, obs);
        }

        @Test
        @DisplayName("AdminObserver implements AuctionObserver")
        void adminObserver_implementsAuctionObserver() {
            AuctionObserver obs = new AdminObserver(masterAdmin("masterEE5"));
            assertInstanceOf(AuctionObserver.class, obs);
        }

        @Test
        @DisplayName("StaffObserver implements AuctionObserver")
        void staffObserver_implementsAuctionObserver() {
            AuctionObserver obs = new StaffObserver(staffAdmin("staffAdm6"));
            assertInstanceOf(AuctionObserver.class, obs);
        }

        @Test
        @DisplayName("SystemAdminObserver implements AuctionObserver")
        void systemAdminObserver_implementsAuctionObserver() {
            AuctionObserver obs = new SystemAdminObserver(SystemAdmin.getInstance());
            assertInstanceOf(AuctionObserver.class, obs);
        }

        @Test
        @DisplayName("mỗi implementation có method onBidPlaced callable qua interface reference")
        void allImplementations_onBidPlacedCallableViaInterfaceRef() {
            // Arrange — dùng interface reference để đảm bảo call qua vtable (polymorphism)
            List<AuctionObserver> observers = List.of(
                    new BidderObserver(bidder,  TestFixture.ratingServiceAllowAll()),
                    new SellerObserver(seller,  TestFixture.ratingServiceAllowAll()),
                    new AdminObserver(masterAdmin("masterFF6")),
                    new StaffObserver(staffAdmin("staffAdm7")),
                    new SystemAdminObserver(SystemAdmin.getInstance())
            );
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidder, 1_500_000L);

            // Act & Assert — không ném exception, không fail compile
            for (AuctionObserver obs : observers) {
                assertDoesNotThrow(() -> obs.onBidPlaced(event),
                        obs.getClass().getSimpleName() + " phải có onBidPlaced callable");
            }
        }

        @Test
        @DisplayName("mỗi implementation có method onAuctionEnded callable qua interface reference")
        void allImplementations_onAuctionEndedCallableViaInterfaceRef() {
            List<AuctionObserver> observers = List.of(
                    new BidderObserver(bidder,  TestFixture.ratingServiceAllowAll()),
                    new SellerObserver(seller,  TestFixture.ratingServiceAllowAll()),
                    new AdminObserver(masterAdmin("masterGG7")),
                    new StaffObserver(staffAdmin("staffAdm8")),
                    new SystemAdminObserver(SystemAdmin.getInstance())
            );
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_ENDED, runningAuction, bidder, 3_000_000L);

            for (AuctionObserver obs : observers) {
                assertDoesNotThrow(() -> obs.onAuctionEnded(event),
                        obs.getClass().getSimpleName() + " phải có onAuctionEnded callable");
            }
        }
    }

    // =========================================================================
    // CONTRACT 2 — Non-throwing contract
    // Không method nào được ném exception với bất kỳ event type nào.
    // =========================================================================

    @Nested
    @DisplayName("Contract 2 — Non-throwing: không được ném exception với bất kỳ event type nào")
    class NonThrowingContract {

        @ParameterizedTest(name = "BidderObserver.onBidPlaced({0}) — không ném exception")
        @EnumSource(AuctionEventType.class)
        @DisplayName("BidderObserver.onBidPlaced — toàn bộ event type không ném exception")
        void bidderObserver_onBidPlaced_neverThrows(AuctionEventType type) {
            AuctionObserver obs = new BidderObserver(bidder, TestFixture.ratingServiceAllowAll());
            AuctionEvent event  = new AuctionEvent(type, runningAuction, bidder, 1_000_000L);
            assertDoesNotThrow(() -> obs.onBidPlaced(event));
        }

        @ParameterizedTest(name = "BidderObserver.onAuctionEnded({0}) — không ném exception")
        @EnumSource(AuctionEventType.class)
        @DisplayName("BidderObserver.onAuctionEnded — toàn bộ event type không ném exception")
        void bidderObserver_onAuctionEnded_neverThrows(AuctionEventType type) {
            AuctionObserver obs = new BidderObserver(bidder, TestFixture.ratingServiceAllowAll());
            AuctionEvent event  = new AuctionEvent(type, runningAuction, bidder, 0L);
            assertDoesNotThrow(() -> obs.onAuctionEnded(event));
        }

        @ParameterizedTest(name = "SellerObserver.onBidPlaced({0}) — không ném exception")
        @EnumSource(AuctionEventType.class)
        @DisplayName("SellerObserver.onBidPlaced — toàn bộ event type không ném exception")
        void sellerObserver_onBidPlaced_neverThrows(AuctionEventType type) {
            AuctionObserver obs = new SellerObserver(seller, TestFixture.ratingServiceAllowAll());
            AuctionEvent event  = new AuctionEvent(type, runningAuction, bidder, 1_000_000L);
            assertDoesNotThrow(() -> obs.onBidPlaced(event));
        }

        @ParameterizedTest(name = "SellerObserver.onAuctionEnded({0}) — không ném exception")
        @EnumSource(AuctionEventType.class)
        @DisplayName("SellerObserver.onAuctionEnded — toàn bộ event type không ném exception")
        void sellerObserver_onAuctionEnded_neverThrows(AuctionEventType type) {
            AuctionObserver obs = new SellerObserver(seller, TestFixture.ratingServiceAllowAll());
            // PAYMENT_COMPLETED gọi ratingService.rewardSeller — dùng allowAll để không throw
            AuctionEvent event  = new AuctionEvent(type, runningAuction, bidder, 0L);
            assertDoesNotThrow(() -> obs.onAuctionEnded(event));
        }

        @ParameterizedTest(name = "AdminObserver.onBidPlaced({0}) — không ném exception")
        @EnumSource(AuctionEventType.class)
        @DisplayName("AdminObserver.onBidPlaced — toàn bộ event type không ném exception")
        void adminObserver_onBidPlaced_neverThrows(AuctionEventType type) {
            AuctionObserver obs = new AdminObserver(masterAdmin("masterHH8"));
            AuctionEvent event  = new AuctionEvent(type, runningAuction, bidder, 1_000_000L);
            assertDoesNotThrow(() -> obs.onBidPlaced(event));
        }

        @ParameterizedTest(name = "AdminObserver.onAuctionEnded({0}) — không ném exception")
        @EnumSource(AuctionEventType.class)
        @DisplayName("AdminObserver.onAuctionEnded — toàn bộ event type không ném exception")
        void adminObserver_onAuctionEnded_neverThrows(AuctionEventType type) {
            AuctionObserver obs = new AdminObserver(masterAdmin("masterII9"));
            AuctionEvent event  = new AuctionEvent(type, runningAuction, bidder, 0L, "msg");
            assertDoesNotThrow(() -> obs.onAuctionEnded(event));
        }

        @ParameterizedTest(name = "StaffObserver.onBidPlaced({0}) — không ném exception")
        @EnumSource(AuctionEventType.class)
        @DisplayName("StaffObserver.onBidPlaced — toàn bộ event type không ném exception")
        void staffObserver_onBidPlaced_neverThrows(AuctionEventType type) {
            AuctionObserver obs = new StaffObserver(staffAdmin("staffAdm9"));
            AuctionEvent event  = new AuctionEvent(type, runningAuction, bidder, 1_000_000L);
            assertDoesNotThrow(() -> obs.onBidPlaced(event));
        }

        @ParameterizedTest(name = "StaffObserver.onAuctionEnded({0}) — không ném exception")
        @EnumSource(AuctionEventType.class)
        @DisplayName("StaffObserver.onAuctionEnded — toàn bộ event type không ném exception")
        void staffObserver_onAuctionEnded_neverThrows(AuctionEventType type) {
            AuctionObserver obs = new StaffObserver(staffAdmin("staffAd10"));
            AuctionEvent event  = new AuctionEvent(type, runningAuction, null, 0L, "msg");
            assertDoesNotThrow(() -> obs.onAuctionEnded(event));
        }

        @ParameterizedTest(name = "SystemAdminObserver.onBidPlaced({0}) — không ném exception")
        @EnumSource(AuctionEventType.class)
        @DisplayName("SystemAdminObserver.onBidPlaced — toàn bộ event type không ném exception")
        void systemAdminObserver_onBidPlaced_neverThrows(AuctionEventType type) {
            AuctionObserver obs = new SystemAdminObserver(SystemAdmin.getInstance());
            AuctionEvent event  = new AuctionEvent(type, runningAuction, bidder, 1_000_000L);
            assertDoesNotThrow(() -> obs.onBidPlaced(event));
        }

        @ParameterizedTest(name = "SystemAdminObserver.onAuctionEnded({0}) — không ném exception")
        @EnumSource(AuctionEventType.class)
        @DisplayName("SystemAdminObserver.onAuctionEnded — toàn bộ event type không ném exception")
        void systemAdminObserver_onAuctionEnded_neverThrows(AuctionEventType type) {
            AuctionObserver obs = new SystemAdminObserver(SystemAdmin.getInstance());
            AuctionEvent event  = new AuctionEvent(type, runningAuction, null, 0L, null);
            assertDoesNotThrow(() -> obs.onAuctionEnded(event));
        }
    }

    // =========================================================================
    // CONTRACT 3 — Null-safe payload contract
    // bidder = null, message = null, bidAmount = 0 — không được crash.
    // =========================================================================

    @Nested
    @DisplayName("Contract 3 — Null-safe: nullable payload không crash")
    class NullSafePayloadContract {

        /** Tạo event với bidder = null, message = null, bidAmount = 0 — payload tối thiểu. */
        private AuctionEvent minimalEvent(AuctionEventType type) {
            return new AuctionEvent(type, runningAuction, null, 0L, null);
        }

        @Test
        @DisplayName("BidderObserver: bidder=null trong BID_PLACED → không NPE")
        void bidderObserver_nullBidderInBidPlaced_noNpe() {
            AuctionObserver obs = new BidderObserver(bidder, TestFixture.ratingServiceAllowAll());
            assertDoesNotThrow(() -> obs.onBidPlaced(minimalEvent(AuctionEventType.BID_PLACED)));
        }

        @Test
        @DisplayName("SellerObserver: bidder=null trong AUCTION_ENDED → không NPE")
        void sellerObserver_nullBidderInAuctionEnded_noNpe() {
            AuctionObserver obs = new SellerObserver(seller, TestFixture.ratingServiceAllowAll());
            assertDoesNotThrow(() -> obs.onAuctionEnded(minimalEvent(AuctionEventType.AUCTION_ENDED)));
        }

        @Test
        @DisplayName("AdminObserver: bidder=null trong BID_PLACED → không NPE")
        void adminObserver_nullBidderInBidPlaced_noNpe() {
            AuctionObserver obs = new AdminObserver(masterAdmin("masterJJ0"));
            assertDoesNotThrow(() -> obs.onBidPlaced(minimalEvent(AuctionEventType.BID_PLACED)));
        }

        @Test
        @DisplayName("AdminObserver: message=null trong FRAUD_DETECTED → không NPE")
        void adminObserver_nullMessageInFraudDetected_noNpe() {
            AuctionObserver obs = new AdminObserver(masterAdmin("masterKK1"));
            assertDoesNotThrow(() -> obs.onAuctionEnded(minimalEvent(AuctionEventType.FRAUD_DETECTED)));
        }

        @Test
        @DisplayName("StaffObserver: message=null trong FRAUD_DETECTED → không NPE")
        void staffObserver_nullMessageInFraudDetected_noNpe() {
            AuctionObserver obs = new StaffObserver(staffAdmin("staffAd11"));
            assertDoesNotThrow(() -> obs.onAuctionEnded(minimalEvent(AuctionEventType.FRAUD_DETECTED)));
        }

        @Test
        @DisplayName("StaffObserver: message=null trong SELLER_CANCEL_REQUEST → không NPE")
        void staffObserver_nullMessageInSellerCancelRequest_noNpe() {
            AuctionObserver obs = new StaffObserver(staffAdmin("staffAd12"));
            assertDoesNotThrow(() -> obs.onAuctionEnded(minimalEvent(AuctionEventType.SELLER_CANCEL_REQUEST)));
        }

        @Test
        @DisplayName("SystemAdminObserver: bidder=null trong AUCTION_ENDED → không NPE")
        void systemAdminObserver_nullBidderInAuctionEnded_noNpe() {
            AuctionObserver obs = new SystemAdminObserver(SystemAdmin.getInstance());
            assertDoesNotThrow(() -> obs.onAuctionEnded(minimalEvent(AuctionEventType.AUCTION_ENDED)));
        }

        @Test
        @DisplayName("SystemAdminObserver: bidder=null trong SECOND_CHANCE_OFFERED → không NPE")
        void systemAdminObserver_nullBidderInSecondChance_noNpe() {
            AuctionObserver obs = new SystemAdminObserver(SystemAdmin.getInstance());
            assertDoesNotThrow(() -> obs.onAuctionEnded(minimalEvent(AuctionEventType.SECOND_CHANCE_OFFERED)));
        }

        @ParameterizedTest(name = "payload tối thiểu: bidder=null, message=null, amount=0 — {0}")
        @EnumSource(AuctionEventType.class)
        @DisplayName("mọi implementation: payload tối thiểu trong onBidPlaced không crash")
        void allObservers_minimalPayload_onBidPlaced_noException(AuctionEventType type) {
            // Chỉ test BidderObserver + SellerObserver vì AdminObserver/Staff/System
            // đã được covered riêng ở trên
            List<AuctionObserver> observers = List.of(
                    new BidderObserver(bidder, TestFixture.ratingServiceAllowAll()),
                    new SellerObserver(seller, TestFixture.ratingServiceAllowAll())
            );
            AuctionEvent event = new AuctionEvent(type, runningAuction, null, 0L, null);

            for (AuctionObserver obs : observers) {
                assertDoesNotThrow(() -> obs.onBidPlaced(event),
                        obs.getClass().getSimpleName() + ".onBidPlaced(" + type + ") null-payload");
            }
        }
    }

    // =========================================================================
    // CONTRACT 4 — Polymorphism / LSP
    // Substituting bất kỳ concrete observer nào qua interface reference
    // phải tuân thủ Liskov Substitution Principle.
    // =========================================================================

    @Nested
    @DisplayName("Contract 4 — Polymorphism / LSP")
    class PolymorphismLsp {

        @Test
        @DisplayName("LSP: BID_PLACED qua List<AuctionObserver> — tất cả xử lý, không crash")
        void lsp_bidPlacedViaListOfObservers_allHandledWithoutException() {
            // Arrange
            List<AuctionObserver> observers = List.of(
                    new BidderObserver(bidder,  TestFixture.ratingServiceAllowAll()),
                    new SellerObserver(seller,  TestFixture.ratingServiceAllowAll()),
                    new AdminObserver(masterAdmin("masterLL2")),
                    new StaffObserver(staffAdmin("staffAd13")),
                    new SystemAdminObserver(SystemAdmin.getInstance())
            );
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidder, 2_000_000L);

            // Act & Assert
            for (AuctionObserver obs : observers) {
                assertDoesNotThrow(() -> obs.onBidPlaced(event),
                        "LSP violated: " + obs.getClass().getSimpleName()
                                + " ném exception khi nhận BID_PLACED");
            }
        }

        @Test
        @DisplayName("LSP: AUCTION_ENDED qua List<AuctionObserver> — tất cả xử lý, không crash")
        void lsp_auctionEndedViaListOfObservers_allHandledWithoutException() {
            // Arrange
            List<AuctionObserver> observers = List.of(
                    new BidderObserver(bidder,  TestFixture.ratingServiceAllowAll()),
                    new SellerObserver(seller,  TestFixture.ratingServiceAllowAll()),
                    new AdminObserver(masterAdmin("masterMM3")),
                    new StaffObserver(staffAdmin("staffAd14")),
                    new SystemAdminObserver(SystemAdmin.getInstance())
            );
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_ENDED, runningAuction, bidder, 3_000_000L);

            // Act & Assert
            for (AuctionObserver obs : observers) {
                assertDoesNotThrow(() -> obs.onAuctionEnded(event),
                        "LSP violated: " + obs.getClass().getSimpleName()
                                + " ném exception khi nhận AUCTION_ENDED");
            }
        }

        @Test
        @DisplayName("LSP: FRAUD_DETECTED qua interface — tất cả xử lý, không crash")
        void lsp_fraudDetectedViaInterface_allHandledWithoutException() {
            List<AuctionObserver> observers = List.of(
                    new BidderObserver(bidder,  TestFixture.ratingServiceAllowAll()),
                    new SellerObserver(seller,  TestFixture.ratingServiceAllowAll()),
                    new AdminObserver(masterAdmin("masterNN4")),
                    new StaffObserver(staffAdmin("staffAd15")),
                    new SystemAdminObserver(SystemAdmin.getInstance())
            );
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.FRAUD_DETECTED, runningAuction, null, 0L, "Bid ring detected");

            for (AuctionObserver obs : observers) {
                assertDoesNotThrow(() -> obs.onAuctionEnded(event),
                        "LSP violated: " + obs.getClass().getSimpleName()
                                + " ném exception khi nhận FRAUD_DETECTED");
            }
        }

        @Test
        @DisplayName("LSP: AUCTION_CANCELED qua interface — tất cả xử lý, không crash")
        void lsp_auctionCanceledViaInterface_allHandledWithoutException() {
            List<AuctionObserver> observers = List.of(
                    new BidderObserver(bidder,  TestFixture.ratingServiceAllowAll()),
                    new SellerObserver(seller,  TestFixture.ratingServiceAllowAll()),
                    new AdminObserver(masterAdmin("masterOO5")),
                    new StaffObserver(staffAdmin("staffAd16")),
                    new SystemAdminObserver(SystemAdmin.getInstance())
            );
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_CANCELED, runningAuction, null, 0L);

            for (AuctionObserver obs : observers) {
                assertDoesNotThrow(() -> obs.onAuctionEnded(event),
                        "LSP violated: " + obs.getClass().getSimpleName()
                                + " ném exception khi nhận AUCTION_CANCELED");
            }
        }

        @Test
        @DisplayName("LSP: cùng observer có thể xử lý nhiều event type liên tiếp, không accumulate state lỗi")
        void lsp_sameObserver_multipleEventTypes_noAccumulatedError() {
            // Arrange — kiểm tra observer không "nhớ" state lỗi giữa các call
            AuctionObserver obs = new BidderObserver(bidder, TestFixture.ratingServiceAllowAll());

            // Act — gọi nhiều event type khác nhau liên tiếp qua cùng instance
            assertAll(
                    () -> assertDoesNotThrow(() -> obs.onBidPlaced(
                            new AuctionEvent(AuctionEventType.BID_PLACED, runningAuction, bidder, 1_000_000L))),
                    () -> assertDoesNotThrow(() -> obs.onBidPlaced(
                            new AuctionEvent(AuctionEventType.BID_RESERVE_NOT_MET, runningAuction, bidder, 800_000L))),
                    () -> assertDoesNotThrow(() -> obs.onAuctionEnded(
                            new AuctionEvent(AuctionEventType.AUCTION_EXTENDED, runningAuction, null, 0L))),
                    () -> assertDoesNotThrow(() -> obs.onAuctionEnded(
                            new AuctionEvent(AuctionEventType.AUCTION_ENDED, runningAuction, bidder, 2_000_000L)))
            );
        }

        @Test
        @DisplayName("LSP: thay concrete observer bằng anonymous implementation — contract vẫn giữ")
        void lsp_anonymousImplementation_contractHolds() {
            // Arrange — anonymous observer: tối giản, không throw, không mutate
            AuctionObserver anonymous = new AuctionObserver() {
                @Override public void onBidPlaced(AuctionEvent event)    { /* no-op */ }
                @Override public void onAuctionEnded(AuctionEvent event) { /* no-op */ }
            };

            AuctionEvent bidEvent  = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidder, 1_000_000L);
            AuctionEvent endEvent  = new AuctionEvent(
                    AuctionEventType.AUCTION_ENDED, runningAuction, bidder, 3_000_000L);

            // Act & Assert — tất cả contract clause phải hold
            assertDoesNotThrow(() -> anonymous.onBidPlaced(bidEvent));
            assertDoesNotThrow(() -> anonymous.onAuctionEnded(endEvent));
        }
    }

    // =========================================================================
    // CONTRACT 5 — Observer isolation
    // Observer A không được thay đổi state Observer B.
    // =========================================================================

    @Nested
    @DisplayName("Contract 5 — Observer isolation: A không ảnh hưởng B")
    class ObserverIsolation {

        @Test
        @DisplayName("hai BidderObserver trên hai bidder khác nhau — event cho A không ảnh hưởng B")
        void twoBidderObservers_eventForA_doesNotAffectB() {
            // Arrange
            NormalUser bidderA = TestFixture.bidderWithBalance("bidderAA8", 1_000_000L);
            NormalUser bidderB = TestFixture.bidderWithBalance("bidderBB9", 2_000_000L);
            double ratingA = bidderA.getRating();
            double ratingB = bidderB.getRating();

            AuctionObserver obsA = new BidderObserver(bidderA, TestFixture.ratingServiceAllowAll());
            AuctionObserver obsB = new BidderObserver(bidderB, TestFixture.ratingServiceAllowAll());

            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidderA, 1_500_000L);

            // Act — chỉ gọi obsA
            obsA.onBidPlaced(event);

            // Assert — state của bidderB không bị ảnh hưởng
            assertEquals(ratingA, bidderA.getRating(), 1e-9, "ratingA phải giữ nguyên");
            assertEquals(ratingB, bidderB.getRating(), 1e-9, "ratingB không được thay đổi bởi obsA");
        }

        @Test
        @DisplayName("hai SellerObserver — PAYMENT_COMPLETED cho A không trigger rewardSeller cho B")
        void twoSellerObservers_paymentForA_doesNotTriggerRewardForB() {
            // Arrange
            NormalUser sellerA = TestFixture.normalSeller("sellerAA9");
            NormalUser sellerB = TestFixture.normalSeller("sellerBB0");

            // ratingService.rewardSeller không thay đổi rating (allowAll là no-op)
            // → dùng để verify không có side-effect chéo giữa hai observer
            double ratingB = sellerB.getRating();

            AuctionObserver obsA = new SellerObserver(sellerA, TestFixture.ratingServiceAllowAll());
            // obsB không được gọi
            AuctionObserver obsB = new SellerObserver(sellerB, TestFixture.ratingServiceAllowAll());

            AuctionEvent paymentEvent = new AuctionEvent(
                    AuctionEventType.PAYMENT_COMPLETED, runningAuction, bidder, 3_000_000L);

            // Act — chỉ gọi obsA
            obsA.onAuctionEnded(paymentEvent);

            // Assert — sellerB không thay đổi
            assertEquals(ratingB, sellerB.getRating(), 1e-9,
                    "PAYMENT_COMPLETED của obsA không được trigger reward cho sellerB");
        }

        @Test
        @DisplayName("hai StaffObserver — FRAUD_DETECTED cho staffA chỉ log vào actionLog của staffA")
        void twoStaffObservers_fraudForA_onlyLogsToStaffA() {
            // Arrange
            Admin staffA = staffAdmin("staffAd17");
            Admin staffB = staffAdmin("staffAd18");

            int logSizeBefore_A = staffA.getActionLog().size();
            int logSizeBefore_B = staffB.getActionLog().size();

            AuctionObserver obsA = new StaffObserver(staffA);
            // obsB không được gọi

            AuctionEvent fraudEvent = new AuctionEvent(
                    AuctionEventType.FRAUD_DETECTED, runningAuction, null, 0L, "Shill bidding");

            // Act
            obsA.onAuctionEnded(fraudEvent);

            // Assert — A nhận log, B không nhận
            assertTrue(staffA.getActionLog().size() > logSizeBefore_A,
                    "staffA phải nhận log FRAUD_DETECTED");
            assertEquals(logSizeBefore_B, staffB.getActionLog().size(),
                    "staffB không được nhận log từ obsA");
        }

        @Test
        @DisplayName("hai AdminObserver — event cho adminA không thêm vào actionLog của adminB")
        void twoAdminObservers_eventForA_doesNotAppendToB() {
            // Arrange
            Admin adminA = masterAdmin("masterPP6");
            Admin adminB = masterAdmin("masterQQ7");
            // AdminObserver log ra stdout (không dùng actionLog) nên test isolation qua username
            AuctionObserver obsA = new AdminObserver(adminA);
            AuctionObserver obsB = new AdminObserver(adminB);

            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidder, 1_500_000L);

            // Act — obsA nhận event
            outCaptor.reset();
            obsA.onBidPlaced(event);
            String outputFromA = captured();

            // Assert — output chứa adminA, không chứa adminB
            assertTrue(outputFromA.contains(adminA.getUsername()),
                    "output phải chứa username adminA");
            assertFalse(outputFromA.contains(adminB.getUsername()),
                    "output không được chứa username adminB — observer targeting sai");
        }
    }

    // =========================================================================
    // CONTRACT 6 — Event routing correctness
    // onBidPlaced nhận bid event; onAuctionEnded nhận lifecycle event.
    // =========================================================================

    @Nested
    @DisplayName("Contract 6 — Event routing: method đúng nhận đúng event role")
    class EventRoutingContract {

        @Test
        @DisplayName("BID_PLACED: onBidPlaced là điểm nhận — onAuctionEnded không phải")
        void bidPlacedEventRoutedToOnBidPlaced() {
            // Arrange — dùng SystemAdminObserver vì nó log tất cả event không lọc
            AuctionObserver obs = new SystemAdminObserver(SystemAdmin.getInstance());
            AuctionEvent event  = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidder, 2_000_000L);

            // Act
            outCaptor.reset();
            obs.onBidPlaced(event);
            boolean onBidPlacedProducesOutput = !captured().isBlank();

            outCaptor.reset();
            obs.onAuctionEnded(event);
            boolean onAuctionEndedHandlesBidEvent = !captured().isBlank();

            // Assert — BID_PLACED được xử lý bởi onBidPlaced; onAuctionEnded có thể xử lý hoặc không
            // (contract: gọi sai phương thức không được crash)
            assertTrue(onBidPlacedProducesOutput,
                    "BID_PLACED qua onBidPlaced phải tạo ra output");
            assertDoesNotThrow(() -> obs.onAuctionEnded(event),
                    "gọi onAuctionEnded với BID_PLACED không được crash — chỉ là silent");
        }

        @Test
        @DisplayName("AUCTION_ENDED: onAuctionEnded là điểm nhận đúng — không crash dù call sai method")
        void auctionEndedEventRoutedToOnAuctionEnded() {
            AuctionObserver obs = new SystemAdminObserver(SystemAdmin.getInstance());
            AuctionEvent event  = new AuctionEvent(
                    AuctionEventType.AUCTION_ENDED, runningAuction, bidder, 3_000_000L);

            outCaptor.reset();
            obs.onAuctionEnded(event);
            boolean onAuctionEndedProducesOutput = !captured().isBlank();

            // Gọi nhầm method không được crash
            assertDoesNotThrow(() -> obs.onBidPlaced(event));

            assertTrue(onAuctionEndedProducesOutput,
                    "AUCTION_ENDED qua onAuctionEnded phải tạo ra output");
        }

        @Test
        @DisplayName("StaffObserver.onBidPlaced: contract im lặng với BID_PLACED — không log bid event")
        void staffObserver_onBidPlaced_silenForBidEvents() {
            // Theo doc: Staff không nhận bid event trừ khi join phiên đó.
            // StaffObserver.onBidPlaced là empty method — không emit bất kỳ output nào.
            Admin staff = staffAdmin("staffAd19");
            AuctionObserver obs = new StaffObserver(staff);
            int logBefore = staff.getActionLog().size();

            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidder, 1_500_000L);

            outCaptor.reset();
            obs.onBidPlaced(event);

            // Assert — Staff không log bid event
            assertTrue(captured().isBlank(),
                    "StaffObserver.onBidPlaced phải im lặng với BID_PLACED");
            assertEquals(logBefore, staff.getActionLog().size(),
                    "onBidPlaced không được thêm vào actionLog của staff");
        }

        @ParameterizedTest(name = "StaffObserver.onBidPlaced({0}) → luôn im lặng")
        @EnumSource(AuctionEventType.class)
        @DisplayName("StaffObserver.onBidPlaced — im lặng với mọi event type (contract: không nhận bid)")
        void staffObserver_onBidPlaced_alwaysSilent_allEventTypes(AuctionEventType type) {
            Admin staff = staffAdmin("staffAd20");
            AuctionObserver obs = new StaffObserver(staff);

            outCaptor.reset();
            obs.onBidPlaced(new AuctionEvent(type, runningAuction, bidder, 1_000_000L));

            assertTrue(captured().isBlank(),
                    "StaffObserver.onBidPlaced phải im lặng với " + type);
        }
    }

    // =========================================================================
    // CONTRACT 7 — State immutability contract
    // Auction và NormalUser không được bị mutate bởi observer.
    // =========================================================================

    @Nested
    @DisplayName("Contract 7 — State immutability: observer không mutate Auction/NormalUser")
    class StateImmutabilityContract {

        @Test
        @DisplayName("BidderObserver không mutate balance của bidder sau BID_PLACED")
        void bidderObserver_bidPlaced_doesNotMutateBidderBalance() {
            NormalUser bidderX = TestFixture.bidderWithBalance("bidderCC4", 2_000_000L);
            long balanceBefore = bidderX.getBalance();

            AuctionObserver obs = new BidderObserver(bidderX, TestFixture.ratingServiceAllowAll());
            obs.onBidPlaced(new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidderX, 500_000L));

            assertEquals(balanceBefore, bidderX.getBalance(),
                    "BidderObserver.onBidPlaced không được thay đổi balance của bidder");
        }

        @Test
        @DisplayName("SellerObserver không mutate balance của seller sau AUCTION_ENDED")
        void sellerObserver_auctionEnded_doesNotMutateSellerBalance() {
            NormalUser sellerX = TestFixture.normalSeller("sellerCC5");
            long balanceBefore = sellerX.getBalance();

            AuctionObserver obs = new SellerObserver(sellerX, TestFixture.ratingServiceAllowAll());
            obs.onAuctionEnded(new AuctionEvent(
                    AuctionEventType.AUCTION_ENDED, runningAuction, bidder, 3_000_000L));

            assertEquals(balanceBefore, sellerX.getBalance(),
                    "SellerObserver.onAuctionEnded không được thay đổi balance của seller");
        }

        @Test
        @DisplayName("mọi observer không mutate Auction.status sau khi nhận BID_PLACED")
        void allObservers_bidPlaced_doesNotMutateAuctionStatus() {
            Auction.AuctionStatus statusBefore = runningAuction.getStatus();
            long priceBefore = runningAuction.getCurrentPrice();

            List<AuctionObserver> observers = List.of(
                    new BidderObserver(bidder, TestFixture.ratingServiceAllowAll()),
                    new SellerObserver(seller, TestFixture.ratingServiceAllowAll()),
                    new AdminObserver(masterAdmin("masterRR8")),
                    new StaffObserver(staffAdmin("staffAd21")),
                    new SystemAdminObserver(SystemAdmin.getInstance())
            );
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidder, 2_000_000L);

            for (AuctionObserver obs : observers) {
                obs.onBidPlaced(event);
            }

            assertEquals(statusBefore, runningAuction.getStatus(),
                    "không observer nào được thay đổi status của auction");
            assertEquals(priceBefore, runningAuction.getCurrentPrice(),
                    "không observer nào được thay đổi currentPrice của auction");
        }

        @Test
        @DisplayName("mọi observer không mutate Auction.status sau khi nhận AUCTION_ENDED")
        void allObservers_auctionEnded_doesNotMutateAuctionStatus() {
            Auction.AuctionStatus statusBefore = runningAuction.getStatus();

            List<AuctionObserver> observers = List.of(
                    new BidderObserver(bidder, TestFixture.ratingServiceAllowAll()),
                    new SellerObserver(seller, TestFixture.ratingServiceAllowAll()),
                    new AdminObserver(masterAdmin("masterSS9")),
                    new StaffObserver(staffAdmin("staffAd22")),
                    new SystemAdminObserver(SystemAdmin.getInstance())
            );
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_ENDED, runningAuction, bidder, 3_000_000L);

            for (AuctionObserver obs : observers) {
                obs.onAuctionEnded(event);
            }

            assertEquals(statusBefore, runningAuction.getStatus(),
                    "không observer nào được thay đổi status của auction qua onAuctionEnded");
        }

        @Test
        @DisplayName("StaffObserver không mutate Auction.status sau FRAUD_DETECTED")
        void staffObserver_fraudDetected_doesNotMutateAuction() {
            Auction.AuctionStatus statusBefore = runningAuction.getStatus();
            Admin staff = staffAdmin("staffAd23");
            int logBefore = staff.getActionLog().size();

            AuctionObserver obs = new StaffObserver(staff);
            obs.onAuctionEnded(new AuctionEvent(
                    AuctionEventType.FRAUD_DETECTED, runningAuction, null, 0L, "Ring bidding"));

            assertEquals(statusBefore, runningAuction.getStatus(),
                    "StaffObserver không được mutate Auction.status");
            // staff.actionLog tăng là expected behavior (log action)
            assertTrue(staff.getActionLog().size() > logBefore,
                    "StaffObserver phải log FRAUD_DETECTED vào actionLog");
        }
    }

    // =========================================================================
    // CONTRACT 8 — Determinism
    // Cùng input, cùng state → behavior nhất quán qua N lần gọi.
    // =========================================================================

    @Nested
    @DisplayName("Contract 8 — Determinism: cùng input → behavior nhất quán")
    class DeterminismContract {

        @Test
        @DisplayName("BidderObserver: gọi onBidPlaced hai lần với cùng event → hành vi nhất quán")
        void bidderObserver_onBidPlaced_calledTwice_consistentBehavior() {
            AuctionObserver obs = new BidderObserver(bidder, TestFixture.ratingServiceAllowAll());
            AuctionEvent event  = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidder, 1_500_000L);

            outCaptor.reset();
            obs.onBidPlaced(event);
            String first = captured();

            outCaptor.reset();
            obs.onBidPlaced(event);
            String second = captured();

            // Assert — output phải giống nhau (deterministic)
            assertEquals(first, second,
                    "BidderObserver.onBidPlaced phải deterministic: cùng event → cùng output");
        }

        @Test
        @DisplayName("SystemAdminObserver: gọi onBidPlaced hai lần với cùng event → output giống nhau")
        void systemAdminObserver_onBidPlaced_calledTwice_sameOutput() {
            AuctionObserver obs = new SystemAdminObserver(SystemAdmin.getInstance());
            AuctionEvent event  = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidder, 2_000_000L);

            outCaptor.reset();
            obs.onBidPlaced(event);
            String first = captured();

            outCaptor.reset();
            obs.onBidPlaced(event);
            String second = captured();

            assertEquals(first, second,
                    "SystemAdminObserver.onBidPlaced phải deterministic");
        }

        @Test
        @DisplayName("StaffObserver: FRAUD_DETECTED hai lần → actionLog tăng đúng 2 entry")
        void staffObserver_fraudDetectedTwice_twoLogEntries() {
            Admin staff = staffAdmin("staffAd24");
            AuctionObserver obs = new StaffObserver(staff);
            int logBefore = staff.getActionLog().size();

            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.FRAUD_DETECTED, runningAuction, null, 0L, "Shill bids");

            obs.onAuctionEnded(event);
            obs.onAuctionEnded(event);

            assertEquals(logBefore + 2, staff.getActionLog().size(),
                    "hai lần FRAUD_DETECTED phải tạo đúng 2 log entry (no dedup tại model layer)");
        }

        @Test
        @DisplayName("AdminObserver: BID_PLACED hai lần → output emit hai lần (không tự dedup)")
        void adminObserver_bidPlacedTwice_outputEmittedTwice() {
            AuctionObserver obs = new AdminObserver(masterAdmin("masterTT0"));
            AuctionEvent event  = new AuctionEvent(
                    AuctionEventType.BID_PLACED, runningAuction, bidder, 1_500_000L);

            outCaptor.reset();
            obs.onBidPlaced(event);
            obs.onBidPlaced(event);

            long lineCount = captured().lines()
                    .filter(l -> !l.isBlank())
                    .count();
            assertEquals(2L, lineCount,
                    "AdminObserver phải emit đúng 2 dòng output cho 2 lần BID_PLACED");
        }
    }

    // =========================================================================
    // CONTRACT 9 — Construction contract
    // =========================================================================

    @Nested
    @DisplayName("Contract 9 — Construction contract")
    class ConstructionContract {

        @Test
        @DisplayName("StaffObserver: construct với Admin không phải STAFF → IllegalArgumentException")
        void staffObserver_constructWithNonStaff_throwsIllegalArgument() {
            Admin master = masterAdmin("masterUU1");
            assertThrows(IllegalArgumentException.class,
                    () -> new StaffObserver(master),
                    "StaffObserver chỉ được nhận Admin với adminLevel = STAFF");
        }

        @Test
        @DisplayName("StaffObserver: construct với Admin STAFF → không ném exception")
        void staffObserver_constructWithStaff_noException() {
            Admin staff = staffAdmin("staffAd25");
            assertDoesNotThrow(() -> new StaffObserver(staff),
                    "StaffObserver phải nhận Admin STAFF không ném exception");
        }

        @Test
        @DisplayName("BidderObserver: construct với NormalUser và IRatingService hợp lệ → không ném exception")
        void bidderObserver_constructWithValidArgs_noException() {
            assertDoesNotThrow(
                    () -> new BidderObserver(bidder, TestFixture.ratingServiceAllowAll()));
        }

        @Test
        @DisplayName("SellerObserver: construct với NormalUser và IRatingService hợp lệ → không ném exception")
        void sellerObserver_constructWithValidArgs_noException() {
            assertDoesNotThrow(
                    () -> new SellerObserver(seller, TestFixture.ratingServiceAllowAll()));
        }

        @Test
        @DisplayName("AdminObserver: construct với Admin hợp lệ → không ném exception")
        void adminObserver_constructWithValidAdmin_noException() {
            assertDoesNotThrow(() -> new AdminObserver(masterAdmin("masterVV2")));
        }

        @Test
        @DisplayName("SystemAdminObserver: construct với SystemAdmin hợp lệ → không ném exception")
        void systemAdminObserver_constructWithValidArgs_noException() {
            assertDoesNotThrow(() -> new SystemAdminObserver(SystemAdmin.getInstance()));
        }
    }
}