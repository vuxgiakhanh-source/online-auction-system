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
import java.util.concurrent.Future;
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

    private static final int BIDDER_COUNT = 24;
    private static final int BIDS_PER_BIDDER = 6;

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0")
            .withDatabaseName("omnibid_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("integration/schema.sql");

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

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < BIDDER_COUNT; i++) {
            final NormalUser bidder = bidders.get(i);
            final int idx = i;
            futures.add(pool.submit(() -> {
                for (int r = 0; r < BIDS_PER_BIDDER; r++) {
                    long amount = 1_200_000L + (idx * 10_000L) + (r * 50_000L);
                    try {
                        bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                        successes.incrementAndGet();
                    } catch (Exception ignored) {
                        // Bid bị từ chối do tranh chấp / bước giá — chấp nhận dưới tải
                    }
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get())
                .as("Phải có đủ bid được chấp nhận khi chịu tải")
                .isGreaterThan(BIDDER_COUNT);

        Auction fromDb = auctionDAO.findAuctionById(auction.getId());
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getCurrentPrice())
                .as("Giá trong DB phải khớp trạng thái RAM sau tải")
                .isEqualTo(auction.getCurrentPrice());
        assertThat(fromDb.getCurrentPrice()).isGreaterThanOrEqualTo(1_000_000L);
    }
}
