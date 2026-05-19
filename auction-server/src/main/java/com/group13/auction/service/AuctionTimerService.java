package com.group13.auction.service;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.AuctionWinner.PaymentStatus;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IAuctionTimerService;
import com.group13.auction.service.iservice.IPaymentService;
import com.group13.auction.service.iservice.IScheduler;
import com.group13.auction.service.scheduler.TaskScheduler;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.AutoBidRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler nền tự động quản lý vòng đời phiên đấu giá.
 *
 * <p>Phụ thuộc vào abstraction ({@link IScheduler}, {@link IAuctionService},
 * {@link IPaymentService}) — không gắn cứng implementation (DIP).
 */
public class AuctionTimerService implements IAuctionTimerService {

    private static final Logger log = LoggerFactory.getLogger(AuctionTimerService.class);
    private static final int SCAN_INTERVAL_SECONDS = 1;
    private static final long CLOSE_LOCK_TIMEOUT_SECONDS = 5L;
    private static final String TIMER_THREAD_NAME = "auction-timer";

    private static final AuctionTimerService INSTANCE = new AuctionTimerService();

    private IScheduler scheduler;
    private IAuctionService auctionService;
    private IPaymentService paymentService;
    private SessionManager sessionManager;
    private final AuctionLockRegistry lockRegistry = AuctionLockRegistry.getInstance();
    private final AutoBidRegistry autoBidRegistry = AutoBidRegistry.getInstance();
    private final SecondChanceOfferDAO secondChanceOfferDAO = new SecondChanceOfferDAO();
    private volatile boolean running = false;

    private AuctionTimerService() {}

    public static AuctionTimerService getInstance() {
        return INSTANCE;
    }

    @Override
    public synchronized void start(IAuctionService auctionService,
                                   IPaymentService paymentService,
                                   SessionManager sessionManager) {
        start(auctionService, paymentService, sessionManager,
                new TaskScheduler(1, TIMER_THREAD_NAME));
    }

    /**
     * Khởi động với scheduler tùy chỉnh (dùng trong test hoặc thay implementation).
     */
    public synchronized void start(IAuctionService auctionService,
                                   IPaymentService paymentService,
                                   SessionManager sessionManager,
                                   IScheduler scheduler) {
        if (running) {
            log.warn("AuctionTimerService đã chạy, bỏ qua lệnh start.");
            return;
        }
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        this.auctionService = auctionService;
        this.paymentService = paymentService;
        this.sessionManager = sessionManager;
        this.scheduler = scheduler;

        scheduler.scheduleAtFixedRate(
                this::scanAndProcess,
                0,
                SCAN_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        running = true;
        log.info("AuctionTimerService khởi động - quét mỗi {}s.", SCAN_INTERVAL_SECONDS);
    }

    @Override
    public synchronized void stop() {
        if (!running || scheduler == null) {
            return;
        }
        scheduler.shutdownNow();
        scheduler = null;
        running = false;
        log.info("AuctionTimerService đã dừng.");
    }

    private void scanAndProcess() {
        try {
            LocalDateTime now = LocalDateTime.now();
            startPendingAuctions(now);
            closeExpiredAuctions(now);
            expirePendingWinnerPayments();
            expirePendingSecondChanceOffers(now);
        } catch (Exception e) {
            log.error("Lỗi không mong muốn trong scan:", e);
        }
    }

    private void startPendingAuctions(LocalDateTime now) {
        List<Auction> openAuctions = AuctionManager.getInstance()
                .getAuctionsByStatus(Auction.AuctionStatus.OPEN);

        for (Auction auction : openAuctions) {
            if (auction.getStartTime() == null || auction.getStartTime().isAfter(now)) {
                continue;
            }

            boolean locked = lockRegistry.tryLock(auction.getId(), CLOSE_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("startPendingAuctions: lock timeout auctionId={}", auction.getId());
                continue;
            }

            try {
                if (auction.getStatus() != Auction.AuctionStatus.OPEN) {
                    continue;
                }

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
            if (auction.getEndTime() == null || auction.getEndTime().isAfter(now)) {
                continue;
            }

            boolean locked = lockRegistry.tryLock(auction.getId(), CLOSE_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("closeExpiredAuctions: lock timeout auctionId={}", auction.getId());
                continue;
            }

            boolean releaseLock = false;
            try {
                // Anti-sniping có thể đã gia hạn trong khi chờ lock.
                if (auction.getStatus() != Auction.AuctionStatus.RUNNING) {
                    continue;
                }
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
                if (releaseLock) {
                    lockRegistry.release(auction.getId());
                }
            }
        }
    }

    /**
     * Winner (first / second chance) quá hạn 24h thanh toán — đồng bộ với DB qua PaymentService.
     */
    private void expirePendingWinnerPayments() {
        List<Auction> finished = AuctionManager.getInstance()
                .getAuctionsByStatus(Auction.AuctionStatus.FINISHED);

        for (Auction auction : finished) {
            AuctionWinner winner = auction.getWinner();
            if (winner == null || winner.getPaymentStatus() != PaymentStatus.PENDING) {
                continue;
            }
            if (!winner.isExpired()) {
                continue;
            }

            boolean locked = lockRegistry.tryLock(auction.getId(), CLOSE_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("expirePendingWinnerPayments: lock timeout auctionId={}", auction.getId());
                continue;
            }
            try {
                AuctionWinner w2 = auction.getWinner();
                if (w2 == null || w2.getPaymentStatus() != PaymentStatus.PENDING || !w2.isExpired()) {
                    continue;
                }
                MDC.put("auctionId", auction.getId());
                paymentService.expirePayment(auction);
                if (auction.getStatus() == Auction.AuctionStatus.CANCELED) {
                    broadcastUpdate(auction, PacketType.AUCTION_CANCELED_UPDATE);
                }
            } catch (Exception e) {
                log.error("expirePendingWinnerPayments: auctionId={}", auction.getId(), e);
            } finally {
                MDC.remove("auctionId");
                lockRegistry.unlock(auction.getId());
            }
        }
    }

    /**
     * Runner-up không chấp nhận trong thời hạn offer — quét theo deadline trên DB.
     */
    private void expirePendingSecondChanceOffers(LocalDateTime now) {
        List<String> auctionIds = secondChanceOfferDAO.findAuctionIdsWithExpiredPendingOffers(now);
        for (String auctionId : auctionIds) {
            Auction auction = AuctionManager.getInstance().findAuctionById(auctionId);
            if (auction == null) {
                continue;
            }

            boolean locked = lockRegistry.tryLock(auctionId, CLOSE_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("expirePendingSecondChanceOffers: lock timeout auctionId={}", auctionId);
                continue;
            }
            try {
                MDC.put("auctionId", auctionId);
                paymentService.expireSecondChanceOfferIfDue(auction);
                if (auction.getStatus() == Auction.AuctionStatus.CANCELED) {
                    broadcastUpdate(auction, PacketType.AUCTION_CANCELED_UPDATE);
                }
            } catch (Exception e) {
                log.error("expirePendingSecondChanceOffers: auctionId={}", auctionId, e);
            } finally {
                MDC.remove("auctionId");
                lockRegistry.unlock(auctionId);
            }
        }
    }

    private void broadcastUpdate(Auction auction, PacketType type) {
        AuctionDTOs.AuctionUpdateDTO dto = DTOMapper.toAuctionUpdateDTO(auction, null);
        sessionManager.broadcastToAuction(auction.getId(), Packet.of(type, dto));
    }
}
