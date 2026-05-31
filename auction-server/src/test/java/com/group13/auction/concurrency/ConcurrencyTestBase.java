package com.group13.auction.concurrency;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Electronics;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.unit.TestFixture;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class chứa helper dùng chung cho tất cả concurrency test. Tránh duplicate code giữa 7 file
 * test.
 */
public abstract class ConcurrencyTestBase {

  protected static final Logger log = LoggerFactory.getLogger(ConcurrencyTestBase.class);

  protected static final long STARTING_PRICE = 500_000L;
  protected static final long RESERVE_PRICE = 800_000L;
  protected static final long USER_BALANCE = 50_000_000L;
  protected static final long TIMEOUT_MS = 10_000L;

  /**
   * Concurrency tests gọi {@link BidService#placeBid}, {@link AutoBidRegistry#register}, … — các
   * Singleton này mặc định chạm DB (NotificationDAO, AutoBidDAO). Không mock → HikariCP block tới
   * 6–30s/call, làm CI timeout và flaky giữa các test.
   */
  @BeforeEach
  void prepareConcurrencyTestEnvironment() throws Exception {
    TestFixture.silenceGlobalSingletons();
    TestFixture.resetSystemBankBalance();
    clearAutoBidRegistry();
  }

  // Fixture builders

  protected NormalUser buildUser(String username, long balance) {
    return NormalUser.reconstitute(
        UUID.randomUUID().toString(),
        LocalDateTime.now(),
        LocalDateTime.now(),
        username,
        "hashed",
        username + "@test.com",
        User.AccountStatus.ACTIVE,
        3.0,
        balance,
        0L,
        EnumSet.of(User.UserRole.BIDDER),
        false,
        0,
        null);
  }

  protected List<NormalUser> buildBidders(int count) {
    List<NormalUser> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      list.add(
          buildUser(
              "bidder_" + i + "_" + UUID.randomUUID().toString().substring(0, 4), USER_BALANCE));
    }
    return list;
  }

  protected Auction buildRunningAuction() {
    return buildRunningAuction(STARTING_PRICE, RESERVE_PRICE, LocalDateTime.now().plusHours(2));
  }

  protected Auction buildRunningAuction(
      long startingPrice, long reservePrice, LocalDateTime endTime) {
    NormalUser seller = buildUser("seller-" + UUID.randomUUID().toString().substring(0, 4), 0L);
    Item item =
        Electronics.reconstitute(
            UUID.randomUUID().toString(),
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now(),
            "Item-" + UUID.randomUUID().toString().substring(0, 4),
            "desc",
            startingPrice,
            seller,
            "Brand",
            12,
            "New");
    return Auction.reconstitute(
        UUID.randomUUID().toString(),
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now(),
        item,
        LocalDateTime.now().minusMinutes(30),
        endTime,
        startingPrice,
        Auction.AuctionStatus.RUNNING,
        reservePrice);
  }

  /**
   * AuctionObserver rỗng – implement đủ 2 methods để không compile lỗi. Dùng thay cho lambda khi
   * chỉ cần một stub không làm gì.
   */
  protected static AuctionObserver noopObserver() {
    return new AuctionObserver() {
      @Override
      public void onBidPlaced(AuctionEvent e) {}

      @Override
      public void onAuctionEnded(AuctionEvent e) {}
    };
  }

  /**
   * Tạo AuctionObserver đếm số lần được gọi (dùng AtomicInteger bên ngoài). Vì AuctionObserver
   * không phải functional interface, helper này tách rõ concern.
   */
  protected static AuctionObserver countingObserver(
      java.util.concurrent.atomic.AtomicInteger counter) {
    return new AuctionObserver() {
      @Override
      public void onBidPlaced(AuctionEvent e) {
        counter.incrementAndGet();
      }

      @Override
      public void onAuctionEnded(AuctionEvent e) {
        counter.incrementAndGet();
      }
    };
  }

  /** Add SELLER role vào user qua reflection (roles là EnumSet private final trong NormalUser). */
  protected void addSellerRole(NormalUser user) {
    try {
      java.lang.reflect.Field f = NormalUser.class.getDeclaredField("roles");
      f.setAccessible(true);
      @SuppressWarnings("unchecked")
      java.util.Set<User.UserRole> roles = (java.util.Set<User.UserRole>) f.get(user);
      roles.add(User.UserRole.SELLER);
    } catch (Exception e) {
      log.warn("[TEST WARN] Không add SELLER role: {}", e.getMessage());
    }
  }

  /** Xóa in-memory AutoBidRegistry để tránh state leak giữa các concurrency test. */
  protected void clearAutoBidRegistry() throws Exception {
    AutoBidRegistry registry = AutoBidRegistry.getInstance();
    Field registryField = AutoBidRegistry.class.getDeclaredField("registry");
    registryField.setAccessible(true);
    ((ConcurrentHashMap<?, ?>) registryField.get(registry)).clear();
  }

  /** Reset allUsers map trong AuctionManager Singleton để tránh state leak. */
  protected void resetAuctionManagerUsers() {
    try {
      java.lang.reflect.Field f =
          com.group13.auction.manager.AuctionManager.class.getDeclaredField("allUsers");
      f.setAccessible(true);
      ((java.util.Map<?, ?>) f.get(com.group13.auction.manager.AuctionManager.getInstance()))
          .clear();
    } catch (Exception e) {
      log.warn("[TEST WARN] Không reset AuctionManager.allUsers: {}", e.getMessage());
    }
  }
}
