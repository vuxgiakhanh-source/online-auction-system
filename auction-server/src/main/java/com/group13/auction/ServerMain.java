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
import com.group13.auction.network.server.image.ImageUploadServer;
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
 *   <li>{@code SERVER_PORT}       — cổng WebSocket (mặc định 8080)</li>
 *   <li>{@code IMAGE_SERVER_PORT} — cổng HTTP upload ảnh (mặc định 8081)</li>
 *   <li>{@code IMAGE_UPLOAD_DIR}  — thư mục lưu ảnh (mặc định "uploads/items")</li>
 *   <li>{@code DB_URL}            — JDBC URL (bắt buộc trong Docker)</li>
 *   <li>{@code DB_USERNAME}       — username DB</li>
 *   <li>{@code DB_PASSWORD}       — password DB</li>
 * </ul>
 */
public class ServerMain {

    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) throws Exception {
        log.info("=== Auction WebSocket Server starting... ===");

        // ── 1. Cấu hình cổng WebSocket ────────────────────────────────────────
        int port = 8080;
        String portEnv = System.getenv("SERVER_PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            try { port = Integer.parseInt(portEnv.trim()); }
            catch (NumberFormatException e) {
                log.warn("SERVER_PORT '{}' không hợp lệ, dùng mặc định 8080", portEnv);
            }
        }

        // ── 2. Cấu hình cổng Image HTTP server ───────────────────────────────
        int imagePort = 8081;
        String imagePortEnv = System.getenv("IMAGE_SERVER_PORT");
        if (imagePortEnv != null && !imagePortEnv.isBlank()) {
            try { imagePort = Integer.parseInt(imagePortEnv.trim()); }
            catch (NumberFormatException e) {
                log.warn("IMAGE_SERVER_PORT '{}' không hợp lệ, dùng mặc định 8081", imagePortEnv);
            }
        }

        String uploadDir = System.getenv("IMAGE_UPLOAD_DIR");
        if (uploadDir == null || uploadDir.isBlank()) uploadDir = "uploads/items";

        // ── 3. Bootstrap SystemAdmin ──────────────────────────────────────────
        SystemAdmin systemAdmin = SystemAdmin.bootstrap("system_secret");

        // ── 4. Khởi tạo DAOs ─────────────────────────────────────────────────
        UserDAO                 userDAO              = new UserDAO();
        AuctionDAO              auctionDAO           = new AuctionDAO();
        BidTransactionDAO       bidTransactionDAO    = new BidTransactionDAO();
        AuctionWinnerDAO        auctionWinnerDAO     = new AuctionWinnerDAO();
        SecondChanceOfferDAO    secondChanceOfferDAO = new SecondChanceOfferDAO();
        FinancialTransactionDAO financialTxDAO       = new FinancialTransactionDAO();
        SellerDAO               sellerDAO            = new SellerDAO();
        AdminDAO                adminDAO             = new AdminDAO();
        QualityReportDAO        qualityReportDAO     = new QualityReportDAO();

        // ── 5. Khởi tạo Services ──────────────────────────────────────────────
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

        // ── 6. Tải dữ liệu vào in-memory ─────────────────────────────────────
        AuctionManager.getInstance().loadDataFromDatabase();
        AutoBidRegistry.getInstance().loadFromDatabase();

        // ── 7. Khởi động AuctionTimerService ─────────────────────────────────
        AuctionTimerService.getInstance().start(
                auctionService, paymentService, SessionManager.getInstance());

        // ── 8. Khởi động ImageUploadServer (HTTP, cổng 8081) ─────────────────
        ImageUploadServer imageServer = new ImageUploadServer(imagePort, uploadDir);
        imageServer.start();

        // ── 9. Khởi động WebSocket Server ─────────────────────────────────────
        ItemFactory itemFactory = new ElectronicsFactory(ratingService);

        AuctionWebSocketServer server = new AuctionWebSocketServer(
                port, accountService, auctionService, bidService,
                paymentService, ratingService, qualityReportService,
                userService, itemFactory);

        // ── 10. Graceful shutdown ─────────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received — stopping servers...");
            AuctionTimerService.getInstance().stop();
            imageServer.stop();
            try { server.stop(3000); } catch (Exception e) {
                log.warn("Error during WebSocket server shutdown", e);
            }
            log.info("Server stopped.");
        }, "shutdown-hook"));

        server.start();
        log.info("=== WebSocket server on port {}, Image HTTP server on port {} ===",
                port, imagePort);
    }
}