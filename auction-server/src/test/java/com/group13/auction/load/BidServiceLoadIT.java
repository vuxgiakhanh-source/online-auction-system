package com.group13.auction.load;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.ItemDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
import com.group13.auction.strategy.BidIncrementCalculator;
import com.group13.auction.strategy.StandardBidStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Load / stress nhẹ trên {@link BidService} + MySQL thật (Testcontainers).
 * Chạy phase {@code verify} (Failsafe), không chạy trong {@code mvn test}.
 */
@RequiresDocker
@Testcontainers
@DisplayName("BidServiceLoadIT — tải đồng thời placeBid (DB thật)")
class BidServiceLoadIT extends IntegrationTestBase {

    // CI-safe: 15 bidder × 4 vòng = 60 ops tổng.
    // Đủ để expose race condition nhưng hoàn thành nhanh với pool kết nối đủ lớn.
    private static final int BIDDER_COUNT    = 15;
    private static final int BIDS_PER_BIDDER = 4;

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0")
            .withDatabaseName("omnibid_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("database/schema.sql");

    private UserDAO userDAO;
    private ItemDAO itemDAO;
    private AuctionDAO auctionDAO;
    private BidTransactionDAO bidTransactionDAO;
    private FinancialTransactionDAO financialTransactionDAO;

    private RatingService ratingService;
    private WalletService walletService;
    private AuctionService auctionService;
    private BidService bidService;

    @BeforeAll
    static void configureDataSource() throws Exception {
        configureTestcontainer(mysql);
    }

    @BeforeEach
    void setUp() throws Exception {
        userDAO = new UserDAO();
        itemDAO = new ItemDAO();
        auctionDAO = new AuctionDAO();
        bidTransactionDAO = new BidTransactionDAO();
        financialTransactionDAO = new FinancialTransactionDAO();

        ratingService = new RatingService(userDAO);
        TestFixture.bootstrapSystemAdmin();
        walletService = new WalletService(financialTransactionDAO, userDAO, ratingService);
        auctionService = new AuctionService(ratingService, auctionDAO);
        bidService = new BidService(auctionService, ratingService, walletService,
                bidTransactionDAO, auctionDAO, userDAO);
        resetTracking();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanupDB();
        TestFixture.resetSystemAdmin();
    }

    @Test
    @DisplayName("Nhiều bidder × nhiều vòng placeBid — không deadlock, giá DB khớp RAM")
    void concurrentLoad_manyBiddersManyRounds() throws Exception {
        NormalUser seller = buildUserWithBalance("load_seller", 80_000_000L, userDAO);
        String itemId = buildItem(seller.getId(), "LoadTest Item", 1_000_000L, itemDAO);
        Item item = itemDAO.findItemById(itemId);
        Auction auction = Auction.create(item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(3),
                5_000_000L);
        auctionDAO.createAuction(auction);
        auctionService.startAuction(auction);
        trackAuction(auction.getId());

        List<NormalUser> bidders = new ArrayList<>(BIDDER_COUNT);
        for (int i = 0; i < BIDDER_COUNT; i++) {
            NormalUser u = buildUserWithBalance("load_b_" + i, 200_000_000L, userDAO);
            bidders.add(u);
            AuctionObserver obs = new BidderObserver(u, null);
            bidService.joinAuction(u, auction, obs);
        }

        AtomicInteger successes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(BIDDER_COUNT, 16));

        for (int i = 0; i < BIDDER_COUNT; i++) {
            final NormalUser bidder = bidders.get(i);
            final int idx = i;
            pool.submit(() -> {
                for (int r = 0; r < BIDS_PER_BIDDER; r++) {
                    // FIX: Dùng gap = 2×increment + idx offset nhỏ để đảm bảo amount
                    // luôn vượt minBid ngay cả khi 1 bid khác vừa được chấp nhận đúng
                    // lúc này. Retry tối đa 5 lần nếu bị reject do race condition.
                    boolean placed = false;
                    int attempts = 0;
                    while (!placed && attempts < 5) {
                        attempts++;
                        long current   = auction.getCurrentPrice();
                        long increment = BidIncrementCalculator.calculate(current);
                        long amount    = current + 2 * increment + (idx * 1_000L);
                        try {
                            bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                            successes.incrementAndGet();
                            placed = true;
                        } catch (com.group13.auction.exception.InvalidBidException e) {
                            // Race: price vừa tăng — retry với price mới
                        } catch (Exception ignored) {
                            break; // business rule khác — không retry
                        }
                    }
                }
            });
        }

        // Đợi tất cả futures hoàn thành với timeout tổng thể 60 giây.
        // Cách cũ (f.get(120s) tuần tự cho từng future) có thể chờ tới
        // BIDDER_COUNT × 120s = hàng chục phút nếu DB contention cao.
        pool.shutdown();
        boolean finished = pool.awaitTermination(60, TimeUnit.SECONDS);
        if (!finished) {
            pool.shutdownNow(); // hủy các thread còn lại để tránh treo CI
        }
        assertThat(finished)
                .as("Load test phải hoàn thành trong 60 giây — kiểm tra deadlock/contention")
                .isTrue();

        assertThat(successes.get())
                .as("Phải có đủ bid được chấp nhận khi chịu tải")
                .isGreaterThan(BIDDER_COUNT);

        Auction fromDb = auctionDAO.findAuctionById(auction.getId());
        assertThat(fromDb).isNotNull();

        long ramPrice = auction.getCurrentPrice();
        long dbPrice  = fromDb.getCurrentPrice();

        // FIX RACE CONDITION CHECK:
        // Với conditional UPDATE (WHERE current_price < ?), DB luôn giữ giá CAO NHẤT.
        // RAM cũng luôn = giá cao nhất (cập nhật atomic trong lock).
        // → DB price phải == RAM price sau khi tất cả threads hoàn tất.
        assertThat(dbPrice)
                .as("Giá trong DB (%d) phải khớp trạng thái RAM (%d) sau tải — nếu fail là stale-write bug",
                        dbPrice, ramPrice)
                .isEqualTo(ramPrice);

        // Giá phải > starting price (ít nhất 1 bid thành công)
        assertThat(dbPrice).isGreaterThan(auction.getItem().getStartingPrice());

        // DB price phải >= RAM price không bao giờ sai (DB = MAX trong mọi tình huống)
        // Thêm assertion: số bid thành công hợp lý
        assertThat(successes.get())
                .as("Số bid thành công trong load test phải >= BIDDER_COUNT (mỗi bidder ít nhất 1 bid)")
                .isGreaterThanOrEqualTo(BIDDER_COUNT);
    }
}