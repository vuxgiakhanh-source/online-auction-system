package com.group13.auction.service;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.iservice.IPaymentService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.AutoBidRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;

/**
 * Scheduler tự động quản lý vòng đời phiên đấu giá.
 *
 * <h3>Cải tiến v2:</h3>
 * <ul>
 *   <li>Logging chuẩn SLF4J (xóa java.util.logging.Logger).</li>
 *   <li>Dùng {@link AuctionLockRegistry#tryLock} với timeout thay vì lock vô hạn.</li>
 *   <li>Double-check anti-sniping sau khi lấy lock.</li>
 *   <li>Resource cleanup (AutoBidRegistry, LockRegistry) khi phiên kết thúc.</li>
 * </ul>
 */
public class AuctionTimerService {

    private static final Logger log = LoggerFactory.getLogger(AuctionTimerService.class);
    private static final int SCAN_INTERVAL_SECONDS = 1;
    private static final long CLOSE_LOCK_TIMEOUT_SECONDS = 5L;

    private static final AuctionTimerService INSTANCE = new AuctionTimerService();

    private ScheduledExecutorService scheduler;
    private AuctionService auctionService;
    private IPaymentService paymentService;
    private SessionManager sessionManager;
    private final AuctionLockRegistry lockRegistry = AuctionLockRegistry.getInstance();
    private final AutoBidRegistry autoBidRegistry = AutoBidRegistry.getInstance();
    private volatile boolean running = false;

    private AuctionTimerService() {}

    public static AuctionTimerService getInstance() {
        return INSTANCE;
    }

    /**
     * Khởi động scheduler. Gọi một lần khi server start.
     */
    public synchronized void start(AuctionService auctionService,
                                   IPaymentService paymentService,
                                   SessionManager sessionManager) {
        if (running) {
            log.warn("AuctionTimerService đã chạy, bỏ qua lệnh start.");
            return;
        }
        this.auctionService = auctionService;
        this.paymentService = paymentService;
        this.sessionManager = sessionManager;

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
        log.info("AuctionTimerService khởi động — quét mỗi {}s.", SCAN_INTERVAL_SECONDS);
    }

    public synchronized void stop() {
        if (!running || scheduler == null) return;
        scheduler.shutdownNow();
        running = false;
        log.info("AuctionTimerService đã dừng.");
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void scanAndProcess() {
        try {
            LocalDateTime now = LocalDateTime.now();
            startPendingAuctions(now);
            closeExpiredAuctions(now);
        } catch (Exception e) {
            log.error("Lỗi không mong muốn trong scan:", e);
        }
    }

    private void startPendingAuctions(LocalDateTime now) {
        List<Auction> openAuctions = AuctionManager.getInstance()
                .getAuctionsByStatus(Auction.AuctionStatus.OPEN);

        for (Auction auction : openAuctions) {
            if (auction.getStartTime() == null || auction.getStartTime().isAfter(now)) continue;

            boolean locked = lockRegistry.tryLock(auction.getId(), CLOSE_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("startPendingAuctions: lock timeout auctionId={}", auction.getId());
                continue;
            }
            try {
                if (auction.getStatus() != Auction.AuctionStatus.OPEN) continue;

                MDC.put("auctionId", auction.getId());
                auctionService.startAuction(auction);
                broadcastUpdate(auction, PacketType.AUCTION_STARTED_UPDATE);
                log.info("Auction started: auctionId={}", auction.getId());
            } catch (Exception e) {
                log.warn("Không thể start phiên: auctionId={} reason={}", auction.getId(), e.getMessage());
            } finally {
                MDC.remove("auctionId");
                lockRegistry.unlock(auction.getId());
            }
        }
    }

    private void closeExpiredAuctions(LocalDateTime now) {
        List<Auction> runningAuctions = AuctionManager.getInstance()
                .getAuctionsByStatus(Auction.AuctionStatus.RUNNING);

        for (Auction auction : runningAuctions) {
            if (auction.getEndTime() == null || auction.getEndTime().isAfter(now)) continue;

            boolean locked = lockRegistry.tryLock(auction.getId(), CLOSE_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("closeExpiredAuctions: lock timeout auctionId={}", auction.getId());
                continue;
            }

            boolean releaseLock = false;
            try {
                // CRITICAL DOUBLE-CHECK: anti-sniping có thể đã gia hạn trong khi chờ lock
                if (auction.getStatus() != Auction.AuctionStatus.RUNNING) continue;
                if (auction.getEndTime().isAfter(now)) {
                    log.info("Auction vừa được gia hạn, bỏ qua kết thúc: auctionId={}", auction.getId());
                    continue;
                }

                MDC.put("auctionId", auction.getId());

                NormalUser leaderBeforeClose = auction.getCurrentLeader();
                boolean reserveMetBeforeClose = auction.isReserveMet();

                auctionService.closeAuction(auction);

                PacketType packetType;
                if (auction.getStatus() == Auction.AuctionStatus.FINISHED) {
                    packetType = PacketType.AUCTION_ENDED_UPDATE;
                } else {
                    if (leaderBeforeClose == null) {
                        packetType = PacketType.AUCTION_NO_WINNER_UPDATE;
                    } else if (!reserveMetBeforeClose) {
                        packetType = PacketType.AUCTION_RESERVE_NOT_MET_UPDATE;
                    } else {
                        packetType = PacketType.AUCTION_CANCELED_UPDATE;
                    }

                    try {
                        paymentService.refundDeposits(auction);
                    } catch (Exception refundEx) {
                        log.error("Lỗi hoàn cọc: auctionId={}", auction.getId(), refundEx);
                    }
                }

                broadcastUpdate(auction, packetType);
                autoBidRegistry.clearAuction(auction.getId());
                releaseLock = true;

                log.info("Auction closed: auctionId={} status={}", auction.getId(), auction.getStatus());
            } catch (Exception e) {
                log.error("Không thể close phiên: auctionId={}", auction.getId(), e);
            } finally {
                MDC.remove("auctionId");
                lockRegistry.unlock(auction.getId());
                // Luôn release khỏi registry khi phiên đã được xử lý (kể cả lỗi)
                // để tránh memory leak với phiên không thể close
                if (releaseLock) {
                    lockRegistry.release(auction.getId());
                }
            }
        }
    }

    private void broadcastUpdate(Auction auction, PacketType type) {
        AuctionDTOs.AuctionUpdateDTO dto = DTOMapper.toAuctionUpdateDTO(auction, null);
        sessionManager.broadcastToAuction(auction.getId(), Packet.of(type, dto));
    }
}