package com.group13.auction.unit.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group13.auction.dao.NotificationDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.notification.Notification;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.ServerBroadcastNotifier;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.BidService;
import com.group13.auction.strategy.AutoBidProcessor;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.unit.TestFixture;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests cho {@link AutoBidProcessor}.
 *
 * <h3>Chiến lược test:</h3>
 *
 * <p>{@code AutoBidProcessor} phụ thuộc vào 3 external dependency cần mock:
 *
 * <ul>
 *   <li>{@link BidService} — đặt giá, có side effect lên Auction domain object
 *   <li>{@link SessionManager} — broadcast/notify qua network
 *   <li>{@link UserDAO} — fallback DB lookup
 * </ul>
 *
 * <p>{@link AutoBidRegistry} là Singleton có state thật → dùng instance thật, reset sạch trong
 * {@code @BeforeEach} / {@code @AfterEach} để đảm bảo isolation.
 *
 * <p>{@link AuctionManager} là Singleton có state thật → inject user vào in-memory để {@code
 * findNormalUserById} hoạt động đúng.
 *
 * <p>Domain model ({@link Auction}, {@link NormalUser}) dùng object thật.
 *
 * <h3>Side effect của bidService.placeBid():</h3>
 *
 * <p>Method này trong production cập nhật {@code auction.updateBid(amount, leader)}. Khi mock, phải
 * dùng {@code doAnswer} để simulate side effect đó, đảm bảo {@code auction.getCurrentLeader()} và
 * {@code auction.getCurrentPrice()} nhất quán.
 *
 * <p>Không DB, không network, không filesystem, không Thread.sleep().
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutoBidProcessor")
class AutoBidProcessorTest {
  // Constants — tier thấp: increment = 50_000
  private static final long STARTING_PRICE = 500_000L;
  private static final long INCREMENT_LOW = 50_000L;
  // Mocks — chỉ external dependency
  @Mock private BidService bidService;
  @Mock private SessionManager sessionManager;
  @Mock private UserDAO userDAO;
  @Mock private NotificationDAO notificationDAO;
  // SUT
  private AutoBidProcessor sut;
  // Fixtures thật
  /** Singleton thật, reset trước/sau mỗi test */
  private AutoBidRegistry registry;

  private NormalUser seller;
  private NormalUser bidderA;
  private NormalUser bidderB;
  private Auction runningAuction;
  // Setup / Teardown
  @BeforeEach
  void setUp() throws Exception {
    sut = new AutoBidProcessor(bidService, sessionManager);
    injectUserDAOMock(sut, userDAO);

    // Registry Singleton thật — phải reset để test isolation
    registry = AutoBidRegistry.getInstance();
    clearInternalRegistry();

    // Domain fixtures thật
    seller = TestFixture.normalSeller("sellerUser01");
    bidderA = TestFixture.bidderWithBalance("bidderAUser", 10_000_000L);
    bidderB = TestFixture.bidderWithBalance("bidderBUser", 10_000_000L);

    // Đưa user vào AuctionManager in-memory (refreshUser để không giữ bản cũ từ test trước)
    AuctionManager.getInstance().refreshUser(bidderA);
    AuctionManager.getInstance().refreshUser(bidderB);

    // Auction đang chạy với giá khởi điểm tier thấp
    runningAuction = TestFixture.runningAuction(seller, STARTING_PRICE);

    // Đảm bảo bidder đã join phiên
    bidderA.addJoinedAuction(runningAuction.getId());
    bidderB.addJoinedAuction(runningAuction.getId());

    // Mặc định: sessionManager không làm gì
    lenient().doNothing().when(sessionManager).sendToUser(anyString(), any());
    lenient().doNothing().when(sessionManager).broadcastToAuctionAsync(anyString(), any());

    wireServerBroadcastNotifier();
  }

  @AfterEach
  void tearDown() throws Exception {
    clearInternalRegistry();
    clearAutoBidExhaustedKeys(ServerBroadcastNotifier.getInstance());
    // FIX: recentBidTimes đã được đổi sang bidActivityRings + các map mới.
    // Xóa tất cả static ConcurrentHashMap để tránh state rò rỉ giữa test.
    for (String fieldName :
        new String[] {
          "bidActivityRings",
          "chainRunning",
          "chainNeedsRecheck",
          "lastAutoBidMs",
          "auctionExecutors"
        }) {
      Field f = AutoBidProcessor.class.getDeclaredField(fieldName);
      f.setAccessible(true);
      ((java.util.concurrent.ConcurrentHashMap<?, ?>) f.get(null)).clear();
    }
  }
  // Reflection helpers
  /** Inject UserDAO mock vào field private trong AutoBidProcessor */
  private static void injectUserDAOMock(AutoBidProcessor processor, UserDAO mockDao)
      throws Exception {
    Field field = AutoBidProcessor.class.getDeclaredField("userDAO");
    field.setAccessible(true);
    field.set(processor, mockDao);
  }

  /** Gắn mock DAO/SessionManager vào ServerBroadcastNotifier cho test exhausted notify. */
  private void wireServerBroadcastNotifier() throws Exception {
    ServerBroadcastNotifier notifier = ServerBroadcastNotifier.getInstance();
    lenient().when(userDAO.isActiveJoinedParticipant(anyString(), anyString())).thenReturn(true);
    lenient().when(notificationDAO.save(any(Notification.class))).thenReturn(true);

    Field userDaoField = ServerBroadcastNotifier.class.getDeclaredField("userDAO");
    userDaoField.setAccessible(true);
    userDaoField.set(notifier, userDAO);

    Field notifDaoField = ServerBroadcastNotifier.class.getDeclaredField("notificationDAO");
    notifDaoField.setAccessible(true);
    notifDaoField.set(notifier, notificationDAO);

    Field smField = ServerBroadcastNotifier.class.getDeclaredField("sessionManager");
    smField.setAccessible(true);
    smField.set(notifier, sessionManager);

    clearAutoBidExhaustedKeys(notifier);
  }

  private static void clearAutoBidExhaustedKeys(ServerBroadcastNotifier notifier)
      throws Exception {
    Field keysField =
        ServerBroadcastNotifier.class.getDeclaredField("autoBidExhaustedNotifiedKeys");
    keysField.setAccessible(true);
    ((java.util.Set<?>) keysField.get(notifier)).clear();
  }

  /** Reset ConcurrentHashMap nội bộ của Singleton AutoBidRegistry */
  @SuppressWarnings("unchecked")
  private void clearInternalRegistry() throws Exception {
    Field mapField = AutoBidRegistry.class.getDeclaredField("registry");
    mapField.setAccessible(true);
    ConcurrentHashMap<String, AutoBidRegistry.AutoBidEntry> map =
        (ConcurrentHashMap<String, AutoBidRegistry.AutoBidEntry>) mapField.get(registry);
    map.clear();
    // Null-out autoBidDAO để tránh kết nối DB trong unit test
    Field daoField = AutoBidRegistry.class.getDeclaredField("autoBidDAO");
    daoField.setAccessible(true);
    daoField.set(registry, null);
  }
  // Stub helpers
  /**
   * Khi bidService.placeBid() được gọi, simulate side effect thật: auction.updateBid(amount,
   * bidder) — đúng như production. Không simulate thì auction.getCurrentLeader() vẫn null → loop
   * sai.
   */
  private void stubPlaceBidWithSideEffect(Auction auction) {
    doAnswer(
            invocation -> {
              NormalUser bidder = invocation.getArgument(0);
              Auction auc = invocation.getArgument(1);
              long amount = invocation.getArgument(2);
              auc.updateBid(amount, bidder);
              return null;
            })
        .when(bidService)
        .placeBid(any(NormalUser.class), eq(auction), anyLong(), any());
  }

  /** Simulate placeBid ném exception — dùng cho test exhausted/cancel path */
  private void stubPlaceBidThrows(Auction auction, RuntimeException ex) {
    doThrow(ex).when(bidService).placeBid(any(), eq(auction), anyLong(), any());
  }

  /** AutoBidProcessor chạy async — chờ chain idle trước khi assert. */
  private void submitAndAwait(Auction auction, String triggeredByUserId) {
    sut.submit(auction, triggeredByUserId);
    try {
      awaitAutoBidChain(auction.getId());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void awaitAutoBidChain(String auctionId) throws Exception {
    Field f = AutoBidProcessor.class.getDeclaredField("chainRunning");
    f.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean>
        runningMap =
            (java.util.concurrent.ConcurrentHashMap<
                    String, java.util.concurrent.atomic.AtomicBoolean>)
                f.get(null);
    long deadline = System.currentTimeMillis() + 5_000L;
    Thread.sleep(20);
    while (System.currentTimeMillis() < deadline) {
      java.util.concurrent.atomic.AtomicBoolean flag = runningMap.get(auctionId);
      if (flag == null || !flag.get()) {
        return;
      }
      Thread.sleep(30);
    }
  }
  // 1. No auto-bidder registered
  @Nested
  @DisplayName("Không có auto-bid nào được đăng ký")
  class NoAutoBidder {

    @Test
    @DisplayName("process() không gọi bidService khi registry trống")
    void process_emptyRegistry_noBidServiceCall() {
      // Arrange: không register auto-bid nào

      // Act
      submitAndAwait(runningAuction, bidderA.getId());

      // Assert
      verify(bidService, never()).placeBid(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("process() không gửi notification khi registry trống")
    void process_emptyRegistry_noNotificationSent() {
      // Arrange: registry trống

      // Act
      submitAndAwait(runningAuction, bidderA.getId());

      // Assert
      verify(sessionManager, never()).sendToUser(anyString(), any());
      verify(sessionManager, never()).broadcastToAuction(anyString(), any());
    }

    @Test
    @DisplayName("process() không ném exception khi registry trống")
    void process_emptyRegistry_doesNotThrow() {
      // Act & Assert
      assertDoesNotThrow(() -> submitAndAwait(runningAuction, bidderA.getId()));
    }
  }
  // 2. Single auto-bidder — không phải current leader
  @Nested
  @DisplayName("Một auto-bidder bị vượt — có thể counter")
  class SingleAutoBidder {

    @Test
    @DisplayName("bidderA bị vượt, còn budget → tự counter ít nhất 1 lần")
    void process_singleBidderExceeded_counterBidOnce() {
      // Arrange
      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 3;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      verify(bidService, atLeastOnce()).placeBid(eq(bidderA), eq(runningAuction), anyLong(), any());
    }

    @Test
    @DisplayName("counter bid amount = currentPrice + increment")
    void process_singleBidder_counterBidAmountIsCurrentPlusIncrement() {
      // Arrange
      long expectedNextBid = STARTING_PRICE + INCREMENT_LOW;
      registry.register(bidderA.getId(), runningAuction.getId(), expectedNextBid);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      verify(bidService).placeBid(eq(bidderA), eq(runningAuction), eq(expectedNextBid), any());
    }

    @Test
    @DisplayName("sau khi counter thành công, bidderA trở thành leader")
    void process_singleBidder_counterBid_bidderBecomesLeader() {
      // Arrange
      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 5;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      assertEquals(bidderA, runningAuction.getCurrentLeader());
    }

    @Test
    @DisplayName("bidderA là current leader → không tự counter")
    void process_singleBidder_alreadyLeading_noCounter() {
      // Arrange: đặt bidderA làm leader thủ công
      runningAuction.updateBid(STARTING_PRICE + INCREMENT_LOW, bidderA);
      registry.register(bidderA.getId(), runningAuction.getId(), 5_000_000L);

      // Act
      submitAndAwait(runningAuction, bidderA.getId());

      // Assert: không gọi bidService vì bidderA đã dẫn đầu
      verify(bidService, never()).placeBid(any(), any(), anyLong(), any());
    }
  }
  // 3. maxBid exhausted — không đủ counter
  @Nested
  @DisplayName("Auto-bidder hết budget (maxBid cạn)")
  class MaxBidExhausted {

    @Test
    @DisplayName("maxBid không đủ counter → bidService không được gọi")
    void process_maxBidTooLow_noBidServiceCall() {
      // Arrange: maxBidA = currentPrice → calculateNextBid = -1
      long insufficientMax = STARTING_PRICE;
      registry.register(bidderA.getId(), runningAuction.getId(), insufficientMax);

      // Act
      submitAndAwait(runningAuction, bidderB.getId());

      // Assert
      verify(bidService, never()).placeBid(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("placeBid ném exception → entry bị xóa khỏi registry")
    void process_placeBidThrows_entryRemovedFromRegistry() {
      // Arrange
      long maxBidA = STARTING_PRICE + INCREMENT_LOW;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      stubPlaceBidThrows(runningAuction, new RuntimeException("maxBid exceeded"));

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      assertFalse(registry.hasActiveBid(bidderA.getId(), runningAuction.getId()));
    }

    @Test
    @DisplayName("maxBid cạn sau khi bidderB vượt → AUTO_BID_EXHAUSTED_NOTIFY gửi cho bidderA")
    void process_exhaustedBidder_exhaustedNotifySent() {
      // Arrange: bidderB bid vượt maxBidA → bidderA hết budget
      long maxBidA = STARTING_PRICE + INCREMENT_LOW;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      long priceAboveMaxA = maxBidA + INCREMENT_LOW;
      runningAuction.updateBid(priceAboveMaxA, bidderB);

      // Act
      submitAndAwait(runningAuction, bidderB.getId());

      // Assert — push realtime + lưu inbox
      verify(sessionManager).sendToUser(eq(bidderA.getId()), any());
      verify(notificationDAO).save(any(Notification.class));
    }

    @Test
    @DisplayName("maxBid cạn → entry bị xóa khỏi registry")
    void process_exhaustedBidder_entryRemovedFromRegistry() {
      // Arrange
      long maxBidA = STARTING_PRICE + INCREMENT_LOW;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      long priceAboveMaxA = maxBidA + INCREMENT_LOW;
      runningAuction.updateBid(priceAboveMaxA, bidderB);

      // Act
      submitAndAwait(runningAuction, bidderB.getId());

      // Assert
      assertFalse(registry.hasActiveBid(bidderA.getId(), runningAuction.getId()));
    }
  }
  // 4. Two competing auto-bidders — different maxBid
  @Nested
  @DisplayName("Hai auto-bidder cạnh tranh — maxBid khác nhau")
  class TwoCompetingBiddersDifferentMaxBid {

    @Test
    @DisplayName("bidderA (maxBid cao hơn) thắng sau chuỗi escalation")
    void process_twoCompeting_higherMaxBidWins() {
      // Arrange
      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 5; // 750_000
      long maxBidB = STARTING_PRICE + INCREMENT_LOW * 2; // 600_000
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      registry.register(bidderB.getId(), runningAuction.getId(), maxBidB);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      assertEquals(
          bidderA, runningAuction.getCurrentLeader(), "bidderA có maxBid cao hơn phải thắng");
    }

    @Test
    @DisplayName("bidderB (maxBid thấp hơn) bị loại → entry bị xóa")
    void process_twoCompeting_lowerMaxBidderEntryRemoved() {
      // Arrange
      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 5;
      long maxBidB = STARTING_PRICE + INCREMENT_LOW * 2;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      registry.register(bidderB.getId(), runningAuction.getId(), maxBidB);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      assertFalse(registry.hasActiveBid(bidderB.getId(), runningAuction.getId()));
    }

    @Test
    @DisplayName("bidderA (maxBid cao hơn) entry vẫn tồn tại trong registry")
    void process_twoCompeting_winnerEntryStillInRegistry() {
      // Arrange
      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 5;
      long maxBidB = STARTING_PRICE + INCREMENT_LOW * 2;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      registry.register(bidderB.getId(), runningAuction.getId(), maxBidB);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      assertTrue(registry.hasActiveBid(bidderA.getId(), runningAuction.getId()));
    }

    @Test
    @DisplayName("giá cuối phải vượt maxBidB (bidderA vượt được bidderB)")
    void process_twoCompeting_finalPriceExceedsLowerMaxBid() {
      // Arrange
      long maxBidB = STARTING_PRICE + INCREMENT_LOW * 2; // 600_000
      long maxBidA = maxBidB + INCREMENT_LOW; // 650_000
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      registry.register(bidderB.getId(), runningAuction.getId(), maxBidB);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      assertTrue(runningAuction.getCurrentPrice() >= maxBidB, "Giá cuối phải vượt maxBidB");
    }
  }
  // 5. Tie: same maxBid — tie-breaking theo registeredAt
  //    (dùng AutoBidEntry constructor trực tiếp để kiểm soát registeredAt
  //     mà không cần Thread.sleep)
  @Nested
  @DisplayName("Tie: hai auto-bidder cùng maxBid — tie-breaking theo registeredAt")
  class SameMaxBidTieBreaking {

    @Test
    @DisplayName("bidderA đăng ký trước (registeredAt nhỏ hơn) cùng maxBid → bidderA thắng tie")
    void process_sameMaxBid_earlierRegistrationWins() throws Exception {
      // Arrange: inject entry trực tiếp với registeredAt kiểm soát được
      long sameMaxBid = STARTING_PRICE + INCREMENT_LOW * 3;
      LocalDateTime earlier = LocalDateTime.now().minusSeconds(10);
      LocalDateTime later = LocalDateTime.now();

      injectEntry(bidderA.getId(), runningAuction.getId(), sameMaxBid, earlier);
      injectEntry(bidderB.getId(), runningAuction.getId(), sameMaxBid, later);

      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert: bidderA (đăng ký sớm hơn) dẫn đầu
      assertEquals(
          bidderA,
          runningAuction.getCurrentLeader(),
          "Với cùng maxBid, người đăng ký trước phải thắng tie");
    }

    @Test
    @DisplayName("bidderB đăng ký trước (registeredAt nhỏ hơn) cùng maxBid → bidderB thắng tie")
    void process_sameMaxBid_laterRegistrationLoses() throws Exception {
      // Arrange
      long sameMaxBid = STARTING_PRICE + INCREMENT_LOW * 3;
      LocalDateTime earlier = LocalDateTime.now().minusSeconds(10);
      LocalDateTime later = LocalDateTime.now();

      injectEntry(bidderB.getId(), runningAuction.getId(), sameMaxBid, earlier);
      injectEntry(bidderA.getId(), runningAuction.getId(), sameMaxBid, later);

      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      assertEquals(
          bidderB, runningAuction.getCurrentLeader(), "bidderB đăng ký trước phải thắng tie");
    }

    @Test
    @DisplayName("tie: cùng maxBid 1 increment → escalation terminate, không infinite loop")
    void process_sameMaxBid_escalationTerminates() throws Exception {
      // Arrange: chỉ 1 người bid được (ai đăng ký trước thắng)
      long sameMaxBid = STARTING_PRICE + INCREMENT_LOW;
      injectEntry(
          bidderA.getId(), runningAuction.getId(), sameMaxBid, LocalDateTime.now().minusSeconds(5));
      injectEntry(bidderB.getId(), runningAuction.getId(), sameMaxBid, LocalDateTime.now());
      stubPlaceBidWithSideEffect(runningAuction);

      // Act & Assert: không infinite loop
      assertDoesNotThrow(() -> submitAndAwait(runningAuction, seller.getId()));
    }

    /**
     * Inject AutoBidEntry trực tiếp vào registry map để kiểm soát registeredAt mà không cần
     * Thread.sleep() (tuân thủ nguyên tắc FIRST).
     */
    @SuppressWarnings("unchecked")
    private void injectEntry(
        String userId, String auctionId, long maxBid, LocalDateTime registeredAt) throws Exception {
      AutoBidRegistry.AutoBidEntry entry =
          new AutoBidRegistry.AutoBidEntry(userId, auctionId, maxBid, registeredAt);
      Field mapField = AutoBidRegistry.class.getDeclaredField("registry");
      mapField.setAccessible(true);
      ConcurrentHashMap<String, AutoBidRegistry.AutoBidEntry> map =
          (ConcurrentHashMap<String, AutoBidRegistry.AutoBidEntry>) mapField.get(registry);
      map.put(userId + ":" + auctionId, entry);
    }
  }
  // 6. Escalation chain termination — không infinite loop
  @Nested
  @DisplayName("Chuỗi escalation phải terminate đúng")
  class EscalationTermination {

    @Test
    @DisplayName("3 auto-bidder khác maxBid → escalation terminate, bidderA (cao nhất) thắng")
    void process_threeCompeting_escalationTerminates() {
      // Arrange
      NormalUser bidderC = TestFixture.bidderWithBalance("bidderCUser", 10_000_000L);
      bidderC.addJoinedAuction(runningAuction.getId());
      AuctionManager.getInstance().addToUserList(bidderC);

      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 6; // 800_000 — cao nhất
      long maxBidB = STARTING_PRICE + INCREMENT_LOW * 3; // 650_000
      long maxBidC = STARTING_PRICE + INCREMENT_LOW * 1; // 550_000 — thấp nhất

      registry.register(bidderC.getId(), runningAuction.getId(), maxBidC);
      registry.register(bidderB.getId(), runningAuction.getId(), maxBidB);
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);

      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      assertDoesNotThrow(() -> submitAndAwait(runningAuction, seller.getId()));

      // Assert
      assertEquals(bidderA, runningAuction.getCurrentLeader());
    }

    @Test
    @DisplayName("bidService được gọi số lần hữu hạn ≤ giới hạn an toàn")
    void process_escalation_bidServiceCallsAreBounded() {
      // Arrange
      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 5;
      long maxBidB = STARTING_PRICE + INCREMENT_LOW * 3;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      registry.register(bidderB.getId(), runningAuction.getId(), maxBidB);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert: 2 auto-bidder → maxIterations = 2*2+2 = 6, số counter thực tế << 6
      verify(bidService, atMost(20)).placeBid(any(), eq(runningAuction), anyLong(), any());
    }

    @Test
    @DisplayName("sau escalation, bidderB bị loại → chỉ bidderA còn trong registry")
    void process_oneExhaustedOneRemaining_loopEnds() {
      // Arrange
      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 4;
      long maxBidB = STARTING_PRICE + INCREMENT_LOW;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      registry.register(bidderB.getId(), runningAuction.getId(), maxBidB);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      assertTrue(registry.hasActiveBid(bidderA.getId(), runningAuction.getId()));
      assertFalse(registry.hasActiveBid(bidderB.getId(), runningAuction.getId()));
    }
  }
  // 7. User không tìm thấy — fallback DB trả về null
  @Nested
  @DisplayName("User không tìm thấy — fallback DB trả về null")
  class UserNotFound {

    @Test
    @DisplayName("user không tồn tại → entry giữ lại (chain skip, không cancel vĩnh viễn)")
    void process_userNotFound_entryRetained() {
      // Arrange
      String ghostUserId = "ghost-user-id-9999";
      registry.register(ghostUserId, runningAuction.getId(), 2_000_000L);
      when(userDAO.findNormalUserById(ghostUserId)).thenReturn(null);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert: resolveUser thất bại chỉ skip iteration, không cancel registry
      assertTrue(registry.hasActiveBid(ghostUserId, runningAuction.getId()));
    }

    @Test
    @DisplayName("user không tìm thấy → bidService không được gọi")
    void process_userNotFound_noBidServiceCall() {
      // Arrange
      String ghostUserId = "ghost-user-id-8888";
      registry.register(ghostUserId, runningAuction.getId(), 2_000_000L);
      when(userDAO.findNormalUserById(ghostUserId)).thenReturn(null);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      verify(bidService, never()).placeBid(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("ghost user bị skip, valid user vẫn counter bình thường")
    void process_ghostUserSkipped_validUserStillCounters() {
      // Arrange: bidderA maxBid cao hơn ghost → được chọn trước khi chain dừng
      String ghostId = "ghost-user-id-7777";
      registry.register(ghostId, runningAuction.getId(), STARTING_PRICE + INCREMENT_LOW);
      registry.register(
          bidderA.getId(), runningAuction.getId(), STARTING_PRICE + INCREMENT_LOW * 4);

      when(userDAO.findNormalUserById(ghostId)).thenReturn(null);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert: bidderA vẫn counter dù ghost user bị skip
      verify(bidService, atLeastOnce()).placeBid(eq(bidderA), eq(runningAuction), anyLong(), any());
    }
  }
  // 8. Race condition — InvalidBidException không được cancel auto-bid entry
  @Nested
  @DisplayName("Race condition: InvalidBidException → entry KHÔNG bị cancel")
  class RaceConditionHandling {

    @Test
    @DisplayName("placeBid ném InvalidBidException (stale price) → entry vẫn còn trong registry")
    void process_invalidBidException_entryNotCancelled() {
      // Arrange: giả lập giá vừa bị đẩy lên (stale price race)
      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 3;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      doThrow(new InvalidBidException("Giá đặt thấp hơn mức tối thiểu", 0L, 0L))
          .when(bidService)
          .placeBid(any(), eq(runningAuction), anyLong(), any());

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert: entry KHÔNG bị xóa — đây là lỗi tạm thời do race, không phải lỗi nghiêm trọng
      assertTrue(
          registry.hasActiveBid(bidderA.getId(), runningAuction.getId()),
          "InvalidBidException là race condition tạm thời — không được cancel entry");
    }

    @Test
    @DisplayName("AuctionClosedException → entry bị cancel và loop dừng")
    void process_auctionClosedException_loopBreaks() {
      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 3;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      doThrow(
              new com.group13.auction.exception.AuctionClosedException(
                  Auction.AuctionStatus.FINISHED))
          .when(bidService)
          .placeBid(any(), eq(runningAuction), anyLong(), any());

      // Act — không ném exception
      assertDoesNotThrow(() -> submitAndAwait(runningAuction, seller.getId()));

      // Assert: bidService chỉ được gọi đúng 1 lần (loop break ngay sau AuctionClosed)
      verify(bidService, times(1)).placeBid(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("RuntimeException khác → entry bị cancel (lỗi nghiêm trọng)")
    void process_unexpectedException_entryCancelled() {
      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 3;
      registry.register(bidderA.getId(), runningAuction.getId(), maxBidA);
      doThrow(new RuntimeException("Lỗi không mong muốn"))
          .when(bidService)
          .placeBid(any(), eq(runningAuction), anyLong(), any());

      submitAndAwait(runningAuction, seller.getId());

      assertFalse(registry.hasActiveBid(bidderA.getId(), runningAuction.getId()));
    }
  }
  // 9. Auction ở trạng thái không hợp lệ
  @Nested
  @DisplayName("Auction ở trạng thái không hợp lệ")
  class InvalidAuctionState {

    @Test
    @DisplayName("auction FINISHED → placeBid ném exception → entry bị cancel")
    void process_finishedAuction_entryRemovedOnException() {
      // Arrange
      NormalUser winner = TestFixture.bidderWithBalance("winnerUser1", 5_000_000L);
      winner.addJoinedAuction(runningAuction.getId());
      Auction finishedAuction =
          TestFixture.finishedAuction(
              seller, winner, STARTING_PRICE, STARTING_PRICE + INCREMENT_LOW);

      long maxBidA = STARTING_PRICE + INCREMENT_LOW * 3;
      registry.register(bidderA.getId(), finishedAuction.getId(), maxBidA);

      doThrow(
              new com.group13.auction.exception.AuctionClosedException(
                  Auction.AuctionStatus.FINISHED))
          .when(bidService)
          .placeBid(any(), eq(finishedAuction), anyLong(), any());

      AuctionManager.getInstance().addToUserList(bidderA);
      bidderA.addJoinedAuction(finishedAuction.getId());

      // Act
      submitAndAwait(finishedAuction, seller.getId());

      // Assert
      assertFalse(registry.hasActiveBid(bidderA.getId(), finishedAuction.getId()));
    }

    @Test
    @DisplayName("auction CANCELED → placeBid ném exception → process không crash")
    void process_canceledAuction_processDoesNotThrow() {
      // Arrange
      Auction canceledAuction = TestFixture.canceledFromRunningAuction(seller, STARTING_PRICE);
      registry.register(bidderA.getId(), canceledAuction.getId(), STARTING_PRICE + INCREMENT_LOW);

      AuctionManager.getInstance().addToUserList(bidderA);
      bidderA.addJoinedAuction(canceledAuction.getId());

      doThrow(
              new com.group13.auction.exception.AuctionClosedException(
                  Auction.AuctionStatus.CANCELED))
          .when(bidService)
          .placeBid(any(), eq(canceledAuction), anyLong(), any());

      // Act & Assert
      assertDoesNotThrow(() -> submitAndAwait(canceledAuction, seller.getId()));
    }
  }
  // 10. triggeredByUserId edge case
  @Nested
  @DisplayName("triggeredByUserId edge case")
  class TriggeredByEdgeCase {

    @Test
    @DisplayName("triggeredBy = auto-bidder đang dẫn đầu → không self-counter")
    void process_triggeredByIsCurrentLeader_noSelfCounter() {
      // Arrange: bidderA đang dẫn đầu và có auto-bid
      runningAuction.updateBid(STARTING_PRICE + INCREMENT_LOW, bidderA);
      registry.register(bidderA.getId(), runningAuction.getId(), 5_000_000L);

      // Act
      submitAndAwait(runningAuction, bidderA.getId());

      // Assert: bidderA là leader → bị skip → không gọi bidService
      verify(bidService, never()).placeBid(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName(
        "manager user thiếu JOINED trong RAM — hydrate từ DB rồi counter (lỗi production)")
    void process_managerUserMissingJoinedInRam_hydratesAndCounters() throws Exception {
      String auctionId = runningAuction.getId();
      long maxBid = STARTING_PRICE + INCREMENT_LOW * 10;

      NormalUser staleInManager =
          NormalUser.reconstitute(
              bidderA.getId(),
              bidderA.getCreatedAt(),
              bidderA.getUpdatedAt(),
              bidderA.getUsername(),
              "hash",
              bidderA.getEmail(),
              com.group13.auction.model.user.User.AccountStatus.ACTIVE,
              3.0,
              10_000_000L,
              0L,
              java.util.EnumSet.of(com.group13.auction.model.user.User.UserRole.BIDDER),
              false,
              0,
              null);
      AuctionManager.getInstance().refreshUser(staleInManager);

      when(userDAO.findJoinedAuctionIdsByUserId(bidderA.getId()))
          .thenReturn(java.util.Set.of(auctionId));

      runningAuction.updateBid(STARTING_PRICE + INCREMENT_LOW, bidderB);
      registry.register(bidderA.getId(), auctionId, maxBid);
      stubPlaceBidWithSideEffect(runningAuction);

      submitAndAwait(runningAuction, bidderB.getId());

      verify(bidService, atLeastOnce()).placeBid(eq(bidderA), eq(runningAuction), anyLong(), any());
      assertTrue(staleInManager.hasJoined(auctionId));
    }

    @Test
    @DisplayName("triggeredBy = bidderB, bidderA có auto-bid và không phải leader → counter")
    void process_triggeredByDifferentUser_autoBidderCounters() {
      // Arrange: bidderB vừa bid, bidderA có auto-bid, bidderA không phải leader
      runningAuction.updateBid(STARTING_PRICE + INCREMENT_LOW, bidderB);
      registry.register(
          bidderA.getId(), runningAuction.getId(), STARTING_PRICE + INCREMENT_LOW * 3);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, bidderB.getId());

      // Assert: bidderA counter
      verify(bidService, atLeastOnce()).placeBid(eq(bidderA), eq(runningAuction), anyLong(), any());
    }
  }
  // 11. Notification correctness
  @Nested
  @DisplayName("Notification: autobid counter broadcast, không gửi TRIGGERED notify")
  class NotificationCorrectness {

    @Test
    @DisplayName("sau khi counter thành công, không gửi AUTO_BID_TRIGGERED_NOTIFY riêng cho user")
    void process_successfulCounter_noTriggeredNotifySent() {
      // Arrange
      registry.register(
          bidderA.getId(), runningAuction.getId(), STARTING_PRICE + INCREMENT_LOW * 3);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert — chỉ broadcast BID_UPDATE, không push TRIGGERED tới user
      verify(sessionManager, never())
          .sendToUser(
              eq(bidderA.getId()),
              argThat(
                  packet ->
                      packet != null
                          && packet.getType()
                              == com.group13.auction.common.protocol.PacketType
                                  .AUTO_BID_TRIGGERED_NOTIFY));
    }

    @Test
    @DisplayName("sau khi counter thành công, broadcastToAuction được gọi đúng auctionId")
    void process_successfulCounter_broadcastSentToCorrectAuction() {
      // Arrange
      String auctionId = runningAuction.getId();
      registry.register(bidderA.getId(), auctionId, STARTING_PRICE + INCREMENT_LOW * 3);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      verify(sessionManager, atLeastOnce()).broadcastToAuctionAsync(eq(auctionId), any());
    }

    @Test
    @DisplayName("không có counter nào xảy ra → không gửi BID_UPDATE broadcast")
    void process_noCounter_noBidUpdateBroadcast() {
      // Arrange: không ai đăng ký auto-bid

      // Act
      submitAndAwait(runningAuction, seller.getId());

      // Assert
      verify(sessionManager, never()).broadcastToAuction(anyString(), any());
    }
  }
  // 12. Repeated processing consistency
  @Nested
  @DisplayName("Repeated processing: gọi process() nhiều lần")
  class RepeatedProcessing {

    @Test
    @DisplayName("sau khi bidderA đã là leader, gọi process() lần 2 không thay đổi leader")
    void process_calledTwice_leaderRemainsAfterFirstProcess() {
      // Arrange
      registry.register(
          bidderA.getId(), runningAuction.getId(), STARTING_PRICE + INCREMENT_LOW * 5);
      registry.register(
          bidderB.getId(), runningAuction.getId(), STARTING_PRICE + INCREMENT_LOW * 2);
      stubPlaceBidWithSideEffect(runningAuction);

      // Act: lần 1
      submitAndAwait(runningAuction, seller.getId());
      NormalUser leaderAfterFirst = runningAuction.getCurrentLeader();

      // Act: lần 2 (bidderA đang dẫn đầu, bidderB đã bị loại)
      submitAndAwait(runningAuction, seller.getId());
      NormalUser leaderAfterSecond = runningAuction.getCurrentLeader();

      // Assert: leader không thay đổi
      assertEquals(leaderAfterFirst, leaderAfterSecond);
    }
  }
  // 13. AutoBidEntry.calculateNextBid — unit test pure logic
  @Nested
  @DisplayName("AutoBidEntry.calculateNextBid — pure logic (không qua process())")
  class AutoBidEntryCalculateNextBid {

    @Test
    @DisplayName("tier thấp (currentPrice=500_000) → nextBid = 550_000")
    void calculateNextBid_lowTier_returnsCurrentPlusLowIncrement() {
      // Arrange
      AutoBidRegistry.AutoBidEntry entry =
          new AutoBidRegistry.AutoBidEntry("u1", "a1", 1_000_000L, LocalDateTime.now());

      // Act & Assert
      assertEquals(550_000L, entry.calculateNextBid(500_000L));
    }

    @Test
    @DisplayName("tier trung (currentPrice=1_000_000) → nextBid = 1_200_000")
    void calculateNextBid_midTier_returnsCurrentPlusMidIncrement() {
      // Arrange
      AutoBidRegistry.AutoBidEntry entry =
          new AutoBidRegistry.AutoBidEntry("u1", "a1", 3_000_000L, LocalDateTime.now());

      // Act & Assert
      assertEquals(1_200_000L, entry.calculateNextBid(1_000_000L));
    }

    @Test
    @DisplayName("nextBid > maxBid → trả về -1 (exhausted)")
    void calculateNextBid_exceedsMaxBid_returnsMinusOne() {
      // Arrange: maxBid=549_999 < nextBid=550_000
      AutoBidRegistry.AutoBidEntry entry =
          new AutoBidRegistry.AutoBidEntry("u1", "a1", 549_999L, LocalDateTime.now());

      // Act & Assert
      assertEquals(-1L, entry.calculateNextBid(500_000L));
    }

    @Test
    @DisplayName("nextBid == maxBid → trả về nextBid (biên trên inclusive)")
    void calculateNextBid_nextBidExactlyEqualsMaxBid_returnsNextBid() {
      // Arrange: maxBid=550_000 == nextBid=550_000
      AutoBidRegistry.AutoBidEntry entry =
          new AutoBidRegistry.AutoBidEntry("u1", "a1", 550_000L, LocalDateTime.now());

      // Act & Assert
      assertEquals(550_000L, entry.calculateNextBid(500_000L));
    }

    @Test
    @DisplayName("currentPrice=0 → nextBid = increment thấp nhất (50_000)")
    void calculateNextBid_zeroPriceCurrentPrice_returnsLowIncrement() {
      // Arrange
      AutoBidRegistry.AutoBidEntry entry =
          new AutoBidRegistry.AutoBidEntry("u1", "a1", 100_000L, LocalDateTime.now());

      // Act & Assert
      assertEquals(50_000L, entry.calculateNextBid(0L));
    }

    @Test
    @DisplayName("tier cao (currentPrice=11_000_000) → nextBid = 11_500_000")
    void calculateNextBid_highTier_returnsCurrentPlusHighIncrement() {
      // Arrange
      AutoBidRegistry.AutoBidEntry entry =
          new AutoBidRegistry.AutoBidEntry("u1", "a1", 20_000_000L, LocalDateTime.now());

      // Act & Assert
      assertEquals(11_500_000L, entry.calculateNextBid(11_000_000L));
    }
  }
}
