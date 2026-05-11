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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * WebSocket Server chính của hệ thống đấu giá.
 *
 * FIX: AuctionHandler constructor đã nhận thêm ItemDAO nội bộ (không cần truyền từ ngoài).
 *      Không cần thay đổi signature constructor của AuctionWebSocketServer.
 */
public class AuctionWebSocketServer extends WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(AuctionWebSocketServer.class);

    private final SessionManager sessionManager;
    private final PacketRouter router;

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

        router.register(new AuthHandler(accountService, userService, sessionManager));

        // AuctionHandler tự khởi tạo ItemDAO bên trong — không cần truyền thêm tham số.
        // Item sẽ được persist vào DB TRƯỚC khi createAuction() chạy (fix FK constraint).
        router.register(new AuctionHandler(auctionService, accountService, sessionManager, itemFactory));

        // FIX Bug #1: BidHandler không nhận BidTransactionDAO — BidService tự persist.
        router.register(new BidHandler(bidService, ratingService, sessionManager));

        router.register(new PaymentHandler(paymentService, accountService, sessionManager));

        // UserAdminHandler tự khởi tạo UserDAO bên trong — dùng cho ADMIN_UNBAN persist.
        router.register(new UserAdminHandler(accountService, ratingService,
                qualityReportService, sessionManager));

        log.info("AuctionWebSocketServer initialized: port={}", port);
    }

    // ── WebSocketServer lifecycle callbacks ───────────────────────────────────

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        sessionManager.register(conn);
        log.info("WebSocket connection opened: remoteAddress={}, connectedCount={}",
                conn.getRemoteSocketAddress(), sessionManager.getConnectedCount());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientSession session = sessionManager.getByConnection(conn);
        String username = session != null ? session.getUsername() : "unknown";
        sessionManager.unregister(conn);
        log.info("WebSocket connection closed: username={}, code={}, reason={}, remote={}, connectedCount={}",
                username, code, reason, remote, sessionManager.getConnectedCount());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        ClientSession session = sessionManager.getByConnection(conn);
        if (session == null) {
            log.warn("WebSocket message ignored because session was not found: remoteAddress={}",
                    conn.getRemoteSocketAddress());
            return;
        }
        log.debug("WebSocket message received: username={}, bytes={}",
                session.getUsername(), message != null ? message.length() : 0);
        router.route(session, message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            ClientSession session = sessionManager.getByConnection(conn);
            String username = session != null ? session.getUsername() : "unknown";
            log.error("WebSocket connection error: username={}", username, ex);
        } else {
            log.error("WebSocket server-level error", ex);
        }
    }

    @Override
    public void onStart() {
        log.info("AuctionWebSocketServer started: port={}, startedAt={}",
                getPort(), java.time.LocalDateTime.now());
        setConnectionLostTimeout(60);
    }

    public void broadcastSystemAnnouncement(String message, String severity) {
        AdminDTOs.SystemAnnouncementDTO dto = new AdminDTOs.SystemAnnouncementDTO();
        dto.setMessage(message);
        dto.setSeverity(severity);
        sessionManager.broadcastAll(Packet.of(PacketType.SYSTEM_ANNOUNCEMENT, dto));
        log.info("System announcement sent: severity={}, messageLength={}",
                severity, message != null ? message.length() : 0);
    }

    public void broadcastShutdownWarning(String reason, int shutdownInSeconds) {
        AdminDTOs.ServerShutdownDTO dto = new AdminDTOs.ServerShutdownDTO();
        dto.setReason(reason);
        dto.setShutdownInSeconds(shutdownInSeconds);
        sessionManager.broadcastAll(Packet.of(PacketType.SERVER_SHUTDOWN_NOTIFY, dto));
        log.info("Shutdown warning sent: reason={}, shutdownInSeconds={}", reason, shutdownInSeconds);
    }

    public PacketRouter getRouter() { return router; }
}
