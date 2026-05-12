package com.group13.auction;

import com.group13.auction.dao.AdminDAO;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.QualityReportDAO;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.dao.SellerDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.item.ElectronicsFactory;
import com.group13.auction.model.item.ItemFactory;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.network.server.AuctionWebSocketServer;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.AuctionTimerService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.PaymentService;
import com.group13.auction.service.QualityReportService;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.UserService;
import com.group13.auction.service.WalletService;
import com.group13.auction.strategy.AutoBidRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point của Auction WebSocket Server.
 *
 * <p>Đọc cấu hình từ env vars (Docker/CI) hoặc data.properties (local dev),
 * khởi tạo toàn bộ dependency, start server và AuctionTimerService.
 *
 * <h3>Env vars:</h3>
 * <ul>
 *   <li>{@code SERVER_PORT} — cổng WebSocket (mặc định 8080)</li>
 *   <li>{@code DB_URL}      — JDBC URL (bắt buộc trong Docker)</li>
 *   <li>{@code DB_USERNAME} — username DB</li>
 *   <li>{@code DB_PASSWORD} — password DB</li>
 * </ul>
 */
public class ServerMain {

    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) throws Exception {
        log.info("=== Auction WebSocket Server starting... ===");

        // ── 1. Cấu hình cổng ──────────────────────────────────────────────────
        int port = 8080;
        String portEnv = System.getenv("SERVER_PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            try {
                port = Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException e) {
                log.warn("SERVER_PORT '{}' không hợp lệ, dùng mặc định 8080", portEnv);
            }
        }

        // ── 2. Bootstrap SystemAdmin (phải chạy trước mọi thứ khác) ──────────
        // SystemAdmin.bootstrap() tự tạo INSTANCE, đăng ký SystemAdminObserver
        // vào AuctionManager, và lưu vào DB nếu chưa có.
        SystemAdmin systemAdmin = SystemAdmin.bootstrap("system_secret");

        // ── 3. Khởi tạo DAOs ──────────────────────────────────────────────────
        UserDAO                 userDAO               = new UserDAO();
        AuctionDAO              auctionDAO            = new AuctionDAO();
        BidTransactionDAO       bidTransactionDAO     = new BidTransactionDAO();
        AuctionWinnerDAO        auctionWinnerDAO      = new AuctionWinnerDAO();
        SecondChanceOfferDAO    secondChanceOfferDAO  = new SecondChanceOfferDAO();
        FinancialTransactionDAO financialTxDAO        = new FinancialTransactionDAO();
        SellerDAO               sellerDAO             = new SellerDAO();
        AdminDAO                adminDAO              = new AdminDAO();
        QualityReportDAO        qualityReportDAO      = new QualityReportDAO();

        // ── 4. Khởi tạo Services ──────────────────────────────────────────────
        RatingService  ratingService  = new RatingService(userDAO);
        WalletService  walletService  = new WalletService(financialTxDAO, userDAO, ratingService);
        AuctionService auctionService = new AuctionService(ratingService, auctionDAO);
        AccountService accountService = new AccountService(
                ratingService, userDAO, sellerDAO, adminDAO, auctionDAO, auctionWinnerDAO);
        UserService    userService    = new UserService(userDAO);

        BidService bidService = new BidService(
                auctionService, ratingService, walletService,
                bidTransactionDAO, auctionDAO, userDAO);

        PaymentService paymentService = new PaymentService(
                auctionService, ratingService, walletService,
                auctionWinnerDAO, secondChanceOfferDAO, bidTransactionDAO, userDAO);

        QualityReportService qualityReportService = new QualityReportService(
                ratingService, paymentService, qualityReportDAO, userDAO);

        // ── 5. Tải dữ liệu từ DB vào in-memory ───────────────────────────────
        AuctionManager.getInstance().loadDataFromDatabase();

        // Khôi phục auto-bid từ DB (tránh mất khi restart)
        AutoBidRegistry.getInstance().loadFromDatabase();

        // ── 6. Khởi động Timer (auto start/close auctions) ───────────────────
        AuctionTimerService.getInstance().start(
                auctionService, paymentService, SessionManager.getInstance());

        // ── 7. Khởi động WebSocket Server ────────────────────────────────────
        // ItemFactory là abstract — dùng ElectronicsFactory làm delegate chính;
        // AuctionHandler.handleCreate() gọi itemFactory.create() tự dispatch sang
        // đúng factory con theo itemCategory string trong DTO.
        ItemFactory itemFactory = new ElectronicsFactory(ratingService);

        AuctionWebSocketServer server = new AuctionWebSocketServer(
                port, accountService, auctionService, bidService,
                paymentService, ratingService, qualityReportService,
                userService, itemFactory);

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received — stopping timer and server...");
            AuctionTimerService.getInstance().stop();
            try { server.stop(3000); } catch (Exception e) {
                log.warn("Error during server shutdown", e);
            }
            log.info("Server stopped.");
        }, "shutdown-hook"));

        server.start();
        log.info("=== Auction WebSocket Server running on port {} ===", port);
    }
}