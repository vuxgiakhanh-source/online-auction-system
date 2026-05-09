package com.group13.auction.network.server;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.handler.*;
import com.group13.auction.network.server.router.PacketRouter;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.*;
import com.group13.auction.model.item.ItemFactory;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * WebSocket Server chính của hệ thống đấu giá.
 *
 * <p>Kế thừa {@link WebSocketServer} từ thư viện java-websocket.
 * Tất cả logic xử lý được delegate sang {@link PacketRouter} → {@link PacketHandler}.
 *
 * <p>Luồng message:
 * <pre>
 * Client → onMessage → PacketRouter.route() → PacketHandler.handle() → session.send()
 * </pre>
 *
 * <p>Cách khởi động:
 * <pre>
 *   AuctionWebSocketServer server = new AuctionWebSocketServer(8080, services...);
 *   server.start();
 * </pre>
 */
public class AuctionWebSocketServer extends WebSocketServer {

    private static final Logger log = Logger.getLogger(AuctionWebSocketServer.class.getName());

    private final SessionManager sessionManager;
    private final PacketRouter router;

    /**
     * Constructor nhận toàn bộ service qua DI.
     *
     * @param port            cổng lắng nghe
     * @param accountService  service quản lý tài khoản
     * @param auctionService  service quản lý phiên
     * @param bidService      service đặt giá
     * @param paymentService  service thanh toán
     * @param ratingService   service rating
     * @param qualityReportService service báo cáo chất lượng
     * @param userService     service đăng nhập/đăng ký
     * @param itemFactory     factory tạo item
     */
    public AuctionWebSocketServer(int port,
                                  AccountService accountService,
                                  AuctionService auctionService,
                                  BidService bidService,
                                  PaymentService paymentService,
                                  RatingService ratingService,
                                  QualityReportService qualityReportService,
                                  com.group13.auction.service.UserService userService,
                                  ItemFactory itemFactory) {
        super(new InetSocketAddress(port));

        this.sessionManager = SessionManager.getInstance();
        this.router = new PacketRouter();

        // Đăng ký handlers theo thứ tự ưu tiên kiểm tra
        router.register(new AuthHandler(accountService, userService, sessionManager));
        router.register(new AuctionHandler(auctionService, accountService, sessionManager, itemFactory));

        // FIX Bug #3: truyền đúng constructor 4-arg có ratingService.
        // Trước đây dùng BidHandler(bidService, sessionManager) → ratingService = null
        // → NullPointerException ngay khi bất kỳ user nào gọi joinAuction/placeBid.
        router.register(new BidHandler(bidService, ratingService, sessionManager,
                new com.group13.auction.dao.BidTransactionDAO()));

        router.register(new PaymentHandler(paymentService, accountService, sessionManager));
        router.register(new UserAdminHandler(accountService, ratingService,
                qualityReportService, sessionManager));

        log.info("[SERVER] AuctionWebSocketServer khởi tạo trên port " + port);
    }

    // ── WebSocketServer lifecycle callbacks ───────────────────────────────────

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        sessionManager.register(conn);
        log.info("[SERVER] New connection: " + conn.getRemoteSocketAddress()
                + " | Total: " + sessionManager.getConnectedCount());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientSession session = sessionManager.getByConnection(conn);
        String username = session != null ? session.getUsername() : "unknown";
        sessionManager.unregister(conn);
        log.info("[SERVER] Closed: " + username
                + " | code=" + code + " | reason=" + reason
                + " | Total: " + sessionManager.getConnectedCount());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        ClientSession session = sessionManager.getByConnection(conn);
        if (session == null) {
            log.warning("[SERVER] onMessage: không tìm thấy session cho connection "
                    + conn.getRemoteSocketAddress());
            return;
        }
        router.route(session, message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            ClientSession session = sessionManager.getByConnection(conn);
            String username = session != null ? session.getUsername() : "unknown";
            log.severe("[SERVER] onError: " + username + " | " + ex.getMessage());
        } else {
            log.severe("[SERVER] Server-level error: " + ex.getMessage());
        }
    }

    @Override
    public void onStart() {
        log.info("[SERVER] ✅ AuctionWebSocketServer started on port "
                + getPort() + " | " + java.time.LocalDateTime.now());
        setConnectionLostTimeout(60); // 60s heartbeat timeout
    }

    // ── Server-side push utilities ────────────────────────────────────────────

    /**
     * Broadcast thông báo hệ thống tới toàn bộ client (bảo trì, shutdown, v.v.).
     *
     * @param message  nội dung thông báo
     * @param severity "INFO" | "WARNING" | "CRITICAL"
     */
    public void broadcastSystemAnnouncement(String message, String severity) {
        AdminDTOs.SystemAnnouncementDTO dto = new AdminDTOs.SystemAnnouncementDTO();
        dto.setMessage(message);
        dto.setSeverity(severity);
        sessionManager.broadcastAll(Packet.of(PacketType.SYSTEM_ANNOUNCEMENT, dto));
        log.info("[SERVER] System announcement sent: " + message);
    }

    /**
     * Thông báo server sắp shutdown.
     *
     * @param reason          lý do
     * @param shutdownInSeconds thời gian còn lại tính bằng giây
     */
    public void broadcastShutdownWarning(String reason, int shutdownInSeconds) {
        AdminDTOs.ServerShutdownDTO dto = new AdminDTOs.ServerShutdownDTO();
        dto.setReason(reason);
        dto.setShutdownInSeconds(shutdownInSeconds);
        sessionManager.broadcastAll(Packet.of(PacketType.SERVER_SHUTDOWN_NOTIFY, dto));
    }

    public SessionManager getSessionManager() { return sessionManager; }
    public PacketRouter getRouter() { return router; }
}