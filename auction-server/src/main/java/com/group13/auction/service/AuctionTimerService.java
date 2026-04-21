package com.group13.auction.service;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Scheduler tự động quản lý vòng đời phiên đấu giá theo thời gian thực.
 *
 * <h3>Nhiệm vụ:</h3>
 * <ul>
 *   <li>Quét các phiên OPEN có {@code startTime <= now()} → gọi
 *       {@link AuctionService#startAuction(Auction)} → broadcast {@code AUCTION_STARTED_UPDATE}</li>
 *   <li>Quét các phiên RUNNING có {@code endTime <= now()} → gọi
 *       {@link AuctionService#closeAuction(Auction)} → broadcast {@code AUCTION_ENDED_UPDATE}
 *       hoặc {@code AUCTION_CANCELED_UPDATE} tùy kết quả</li>
 * </ul>
 *
 * <h3>Thiết kế:</h3>
 * <ul>
 *   <li>Singleton eager init — thread-safe từ đầu</li>
 *   <li>1 daemon thread, chu kỳ quét 5 giây</li>
 *   <li>Bắt exception per-auction: 1 phiên lỗi không dừng scheduler</li>
 * </ul>
 *
 * <h3>Cách dùng trong server bootstrap:</h3>
 * <pre>{@code
 *   AuctionTimerService.getInstance().start(auctionService, sessionManager);
 *   // Khi shutdown:
 *   AuctionTimerService.getInstance().stop();
 * }</pre>
 */
public class AuctionTimerService {

    private static final Logger log = Logger.getLogger(AuctionTimerService.class.getName());
    private static final int SCAN_INTERVAL_SECONDS = 5;

    private static final AuctionTimerService INSTANCE = new AuctionTimerService();

    private ScheduledExecutorService scheduler;
    private AuctionService auctionService;
    private SessionManager sessionManager;
    private volatile boolean running = false;

    private AuctionTimerService() {}

    public static AuctionTimerService getInstance() {
        return INSTANCE;
    }

    /**
     * Khởi động scheduler. Gọi một lần duy nhất khi server start.
     *
     * @param auctionService service xử lý nghiệp vụ phiên
     * @param sessionManager để broadcast kết quả tới client
     */
    public synchronized void start(AuctionService auctionService, SessionManager sessionManager) {
        if (running) {
            log.warning("[TIMER] AuctionTimerService đã chạy, bỏ qua lệnh start.");
            return;
        }
        this.auctionService = auctionService;
        this.sessionManager = sessionManager;

        // daemon=true: tự tắt khi JVM shutdown, không block server stop
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auction-timer");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                this::scanAndProcess,
                0,
                SCAN_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        running = true;
        log.info("[TIMER] AuctionTimerService khởi động — quét mỗi " + SCAN_INTERVAL_SECONDS + "s.");
    }

    /**
     * Dừng scheduler khi server shutdown.
     */
    public synchronized void stop() {
        if (!running || scheduler == null) return;
        scheduler.shutdownNow();
        running = false;
        log.info("[TIMER] AuctionTimerService đã dừng.");
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Một chu kỳ quét: xử lý OPEN→RUNNING rồi RUNNING→FINISHED/CANCELED.
     * Bắt exception per-auction để 1 phiên lỗi không dừng scheduler.
     */
    private void scanAndProcess() {
        try {
            LocalDateTime now = LocalDateTime.now();
            startPendingAuctions(now);
            closeExpiredAuctions(now);
        } catch (Exception e) {
            log.severe("[TIMER] Lỗi không mong muốn trong scan: " + e.getMessage());
        }
    }

    /**
     * Tìm tất cả phiên OPEN có startTime <= now → chuyển sang RUNNING
     * và broadcast {@code AUCTION_STARTED_UPDATE} tới tất cả watcher.
     */
    private void startPendingAuctions(LocalDateTime now) {
        List<Auction> openAuctions = AuctionManager.getInstance()
                .getAuctionsByStatus(Auction.AuctionStatus.OPEN);

        for (Auction auction : openAuctions) {
            if (auction.getStartTime() == null || auction.getStartTime().isAfter(now)) {
                continue;
            }
            try {
                auctionService.startAuction(auction);

                // Dùng AuctionUpdateDTO + AUCTION_STARTED_UPDATE (đều tồn tại trong codebase)
                AuctionDTOs.AuctionUpdateDTO dto =
                        DTOMapper.toAuctionUpdateDTO(auction, null);
                sessionManager.broadcastToAuction(
                        auction.getId(),
                        Packet.of(PacketType.AUCTION_STARTED_UPDATE, dto)
                );

                log.info("[TIMER] Phiên bắt đầu: " + auction.getId());
            } catch (Exception e) {
                log.warning("[TIMER] Không thể start phiên " + auction.getId()
                        + ": " + e.getMessage());
            }
        }
    }

    /**
     * Tìm tất cả phiên RUNNING có endTime <= now → đóng phiên
     * và broadcast kết quả tới tất cả watcher.
     *
     * <ul>
     *   <li>FINISHED → {@code AUCTION_ENDED_UPDATE}</li>
     *   <li>CANCELED → {@code AUCTION_CANCELED_UPDATE}</li>
     * </ul>
     */
    private void closeExpiredAuctions(LocalDateTime now) {
        List<Auction> runningAuctions = AuctionManager.getInstance()
                .getAuctionsByStatus(Auction.AuctionStatus.RUNNING);

        for (Auction auction : runningAuctions) {
            if (auction.getEndTime() == null || auction.getEndTime().isAfter(now)) {
                continue;
            }
            try {
                auctionService.closeAuction(auction);

                // Chọn PacketType đúng theo kết quả thực tế của phiên
                PacketType packetType;
                switch (auction.getStatus()) {
                    case FINISHED:
                        packetType = PacketType.AUCTION_ENDED_UPDATE;
                        break;
                    case CANCELED:
                        packetType = PacketType.AUCTION_CANCELED_UPDATE;
                        break;
                    default:
                        packetType = PacketType.AUCTION_ENDED_UPDATE;
                        break;
                }

                AuctionDTOs.AuctionUpdateDTO dto =
                        DTOMapper.toAuctionUpdateDTO(auction, null);
                sessionManager.broadcastToAuction(
                        auction.getId(),
                        Packet.of(packetType, dto)
                );

                log.info("[TIMER] Phiên đóng: " + auction.getId()
                        + " | Status: " + auction.getStatus());
            } catch (Exception e) {
                log.warning("[TIMER] Không thể close phiên " + auction.getId()
                        + ": " + e.getMessage());
            }
        }
    }
}
