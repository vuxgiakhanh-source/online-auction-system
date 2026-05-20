package com.group13.auction.network.server.handler;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.exception.AuctionClosedException;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.service.BidService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.AutoBidProcessor;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.strategy.AutoBidRegistry.AutoBidEntry;
import com.group13.auction.strategy.AutoBidStrategy;
import com.group13.auction.strategy.BidRateLimiter;
import com.group13.auction.strategy.StandardBidStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Xử lý các packet liên quan đến đấu giá trực tiếp:
 * JOIN_AUCTION, WATCH_AUCTION, LEAVE_AUCTION,
 * PLACE_BID, REGISTER_AUTO_BID, UPDATE_AUTO_BID,
 * CANCEL_AUTO_BID, GET_AUTO_BID_STATUS, GET_BID_HISTORY.
 *
 * <h3>Concurrent Bidding — cơ chế lock:</h3>
 * <p>Mỗi phiên đấu giá có một {@link ReentrantLock} riêng biệt từ
 * {@link AuctionLockRegistry}. Toàn bộ chuỗi validate → update price →
 * trigger auto-bid phải nằm trong cùng 1 critical section để tránh:
 * <ul>
 *   <li>Lost update (hai thread cùng pass validate với cùng currentPrice)</li>
 *   <li>Hai người cùng thắng</li>
 *   <li>Giá bị rollback do race condition</li>
 * </ul>
 *
 * <h3>Auto-Bid trigger chain:</h3>
 * <p>Sau mỗi {@code placeBid()} thành công, {@link AutoBidProcessor} được gọi
 * bên trong lock để trigger counter-bid cho những người bị vượt qua.
 * Chuỗi tiếp tục cho đến khi không còn ai có thể counter hoặc chỉ còn 1 leader.
 *
 * <h3>Anti-sniping:</h3>
 * <p>Xử lý trong {@link BidService#placeBid()} — nếu có bid trong X giây cuối
 * thì phiên được gia hạn thêm Y giây. {@code newEndTime} được nhúng vào
 * {@code BidUpdateDTO} broadcast ngay.
 *
 * FIX Bug #1: Xóa field bidTransactionDAO vì BidService đã tự persist
 *             qua bidTransactionDAO.saveTransaction(tx) bên trong placeBid().
 *             Handler không cần và không nên truy cập DAO layer trực tiếp.
 */
public class BidHandler implements PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(BidHandler.class);
    private static final long BID_LOCK_TIMEOUT_SECONDS = 5L;

    private static final Set<PacketType> SUPPORTED = EnumSet.of(
            PacketType.JOIN_AUCTION,
            PacketType.WATCH_AUCTION,
            PacketType.LEAVE_AUCTION,
            PacketType.PLACE_BID,
            PacketType.REGISTER_AUTO_BID,
            PacketType.UPDATE_AUTO_BID,
            PacketType.CANCEL_AUTO_BID,
            PacketType.GET_AUTO_BID_STATUS,
            PacketType.GET_BID_HISTORY
    );

    private final BidService bidService;
    private final IRatingService ratingService;
    private final SessionManager sessionManager;
    private final AuctionDAO auctionDAO;
    // FIX Bug #1: bidTransactionDAO đã bị xóa — BidService.placeBid() tự persist.
    private final AutoBidRegistry autoBidRegistry = AutoBidRegistry.getInstance();
    private final AuctionLockRegistry lockRegistry = AuctionLockRegistry.getInstance();
    private final BidRateLimiter rateLimiter = BidRateLimiter.getInstance();
    private final AutoBidProcessor autoBidProcessor;
    private final BidTransactionDAO bidTransactionDAO;

    /**
     * Constructor — BidTransactionDAO dùng cho GET_BID_HISTORY (query DB thay vì scan memory).
     * BidService đã inject BidTransactionDAO riêng và tự persist mỗi bid.
     */
    public BidHandler(BidService bidService, IRatingService ratingService,
                      SessionManager sessionManager) {
        this.bidService = bidService;
        this.ratingService = ratingService;
        this.sessionManager = sessionManager;
        this.auctionDAO = new AuctionDAO();
        this.autoBidProcessor = new AutoBidProcessor(bidService, sessionManager);
        this.bidTransactionDAO = new BidTransactionDAO();
    }

    @Override
    public boolean supports(PacketType type) {
        return SUPPORTED.contains(type);
    }

    @Override
    public void handle(ClientSession session, PacketType type,
                       JsonElement payload, String requestId) {
        if (!session.isAuthenticated()) {
            log.warn("Reject bid packet from unauthenticated session: type={}, requestId={}", type, requestId);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chưa đăng nhập.", requestId), requestId));
            return;
        }

        switch (type) {
            case JOIN_AUCTION -> handleJoin(session, payload, requestId);
            case WATCH_AUCTION -> handleWatch(session, payload, requestId);
            case LEAVE_AUCTION -> handleLeave(session, payload, requestId);
            case PLACE_BID -> handlePlaceBid(session, payload, requestId);
            case REGISTER_AUTO_BID -> handleRegisterAutoBid(session, payload, requestId);
            case UPDATE_AUTO_BID -> handleUpdateAutoBid(session, payload, requestId);
            case CANCEL_AUTO_BID -> handleCancelAutoBid(session, payload, requestId);
            case GET_AUTO_BID_STATUS -> handleGetAutoBidStatus(session, payload, requestId);
            case GET_BID_HISTORY -> handleGetBidHistory(session, payload, requestId);
            default -> {
                log.warn("Unsupported packet reached BidHandler: type={}, requestId={}", type, requestId);
            }
        }
    }

    // ── JOIN ──────────────────────────────────────────────────────────────────

    private void handleJoin(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction = requireAuction(session, auctionId, requestId);
            if (auction == null) return;

            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            BidderObserver observer = new BidderObserver(bidder, ratingService);
            bidService.joinAuction(bidder, auction, observer);
            sessionManager.addAuctionWatcher(session.getConnection(), auctionId);
            log.info("Join auction handled: auctionId={}, bidderId={}, username={}, requestId={}",
                    auctionId, bidder.getId(), bidder.getUsername(), requestId);

            // TOTAL ACCESS COUNT: tăng counter lịch sử mỗi lần join, persist DB
            auction.incrementViewerCount();
            auctionDAO.updateViewerCount(auctionId, auction.getViewerCount());

            long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;
            AuctionDTOs.JoinAuctionResponseDTO response = new AuctionDTOs.JoinAuctionResponseDTO();

            AuctionDTOs.AuctionDTO auctionDto = DTOMapper.toAuctionDTO(auction); // viewerCount từ auction.getViewerCount()
            response.setAuction(auctionDto);
            response.setDepositAmount(depositAmount);
            response.setNewAvailableBalance(bidder.getAvailableBalance());

            session.send(Packet.of(PacketType.JOIN_AUCTION_SUCCESS, response, requestId));

        } catch (AuctionBusinessException e) {
            log.warn("Join auction rejected: username={}, requestId={}, reason={}",
                    session.getUsername(), requestId, e.getReason());
            session.send(Packet.of(PacketType.JOIN_AUCTION_FAILED,
                    ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("Join auction failed: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.JOIN_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── WATCH ─────────────────────────────────────────────────────────────────

    private void handleWatch(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction = requireAuction(session, auctionId, requestId);
            if (auction == null) return;

            NormalUser user = requireNormalUser(session, requestId);
            if (user == null) return;

            BidderObserver observer = new BidderObserver(user, ratingService);
            bidService.watchAuction(user, auction, observer);
            sessionManager.addAuctionWatcher(session.getConnection(), auctionId);
            log.info("Watch auction handled: auctionId={}, userId={}, username={}, requestId={}",
                    auctionId, user.getId(), user.getUsername(), requestId);

            // TOTAL ACCESS COUNT: tăng counter lịch sử mỗi lần watch, persist DB
            auction.incrementViewerCount();
            auctionDAO.updateViewerCount(auctionId, auction.getViewerCount());

            AuctionDTOs.AuctionDTO auctionDto = DTOMapper.toAuctionDTO(auction); // viewerCount từ auction.getViewerCount()
            session.send(Packet.of(PacketType.WATCH_AUCTION_SUCCESS, auctionDto, requestId));

        } catch (Exception e) {
            log.error("Watch auction failed: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.WATCH_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── LEAVE ─────────────────────────────────────────────────────────────────

    private void handleLeave(ClientSession session, JsonElement payload, String requestId) {
        String auctionId = PacketCodec.fromElement(payload, String.class);
        sessionManager.removeAuctionWatcher(session.getConnection(), auctionId);

        // FIX TC-WS-04d root cause: dùng bidService.leaveAuction() thay vì gọi
        // removeJoinedAuction() trực tiếp. Lý do: AuctionManager.findUserByUsername()
        // luôn load user mới từ DB mỗi lần gọi — nếu chỉ xóa in-memory thì
        // PLACE_BID (dùng user object khác load từ DB) vẫn thấy JOINED và cho bid tiếp.
        // leaveAuction() xóa cả in-memory lẫn DB (DELETE user_auction_activity).
        NormalUser bidder = requireNormalUser(session, requestId);
        if (bidder != null) {
            bidService.leaveAuction(bidder, auctionId);
            log.info("Leave auction handled: auctionId={}, username={}, bidderId={}, requestId={}",
                    auctionId, session.getUsername(), bidder.getId(), requestId);
        } else {
            log.info("Leave auction handled (non-normal user): auctionId={}, username={}, requestId={}",
                    auctionId, session.getUsername(), requestId);
        }

        session.send(Packet.of(PacketType.LEAVE_AUCTION_SUCCESS, null, requestId));
        // TOTAL ACCESS COUNT: count chỉ tăng, không giảm khi leave — không cần broadcast
    }

    // ── PLACE BID ─────────────────────────────────────────────────────────────

    private void handlePlaceBid(ClientSession session, JsonElement payload, String requestId) {
        BidDTOs.BidRequestDTO req;
        try {
            req = PacketCodec.fromElement(payload, BidDTOs.BidRequestDTO.class);
        } catch (Exception e) {
            log.warn("Invalid place bid payload: username={}, requestId={}", session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Payload không hợp lệ.", requestId), requestId));
            return;
        }

        // Rate limit theo userId — trước mọi thứ để chặn flood sớm nhất
        String rateLimitUserId = session.getUserId();
        if (rateLimitUserId != null && !rateLimiter.tryConsume(rateLimitUserId)) {
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of("RATE_LIMIT_EXCEEDED",
                            "Bạn đang đặt giá quá nhanh. Vui lòng thử lại sau 1 giây.", requestId), requestId));
            return;
        }

        // FIX PERFORMANCE #1: resolve user từ session cache — không hit DB mỗi bid.
        // Lần đầu tiên: load từ DB và cache vào session.
        // Các lần sau: trả về object trong memory (~0µs thay vì ~5ms DB round-trip).
        NormalUser bidder = requireNormalUser(session, requestId);
        if (bidder == null) return;

        // Resolve auction từ in-memory map (không cần lock, không cần DB)
        Auction auction = requireAuction(session, req.getAuctionId(), requestId);
        if (auction == null) return;

        // Capture state TRƯỚC bid để tính delta cho broadcast
        long previousPrice       = auction.getCurrentPrice();
        LocalDateTime endTimeBefore = auction.getEndTime();

        // FIX PERFORMANCE #2: BidHandler KHÔNG hold outer per-auction lock nữa.
        //
        // Vấn đề cũ: BidHandler acquire lock → gọi bidService.placeBid() (bên trong
        // cũng lock, reentrant) → sau khi bidService unlock nội bộ, BidHandler vẫn
        // giữ lock trong khi: DB write (saveTransactionAndUpdatePrice ~10ms) +
        // session.send (network I/O ~1ms) + build broadcast payloads.
        //
        // Kết quả: lock hold ~11ms per bid → max throughput ~90 bid/s per auction.
        //
        // Fix: BidHandler không lock gì cả. BidService.placeBid() tự quản lý
        // per-auction lock nội bộ, CHỈ trong phạm vi RAM critical section (~0.1ms).
        // DB write + notify + session.send + broadcast chạy NGOÀI lock hoàn toàn.
        //
        // Kết quả: lock hold ~0.1ms per bid → max throughput ~10.000 bid/s per auction.
        try {
            bidService.placeBid(bidder, auction, req.getAmount(), new StandardBidStrategy());
            log.info("Place bid handled: auctionId={}, bidderId={}, username={}, amount={}, requestId={}",
                    req.getAuctionId(), bidder.getId(), bidder.getUsername(), req.getAmount(), requestId);
        } catch (AuctionClosedException e) {
            log.warn("Place bid rejected — auction closed: auctionId={}, username={}, requestId={}",
                    req.getAuctionId(), session.getUsername(), requestId);
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.AUCTION_CLOSED, e.getMessage(), requestId), requestId));
            return;
        } catch (InvalidBidException e) {
            log.warn("Place bid rejected — invalid amount: auctionId={}, username={}, amount={}, requestId={}",
                    req.getAuctionId(), session.getUsername(), req.getAmount(), requestId);
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.BID_TOO_LOW, e.getMessage(), requestId), requestId));
            return;
        } catch (AuctionBusinessException e) {
            log.warn("Place bid rejected — business rule: auctionId={}, username={}, amount={}, requestId={}, reason={}",
                    req.getAuctionId(), session.getUsername(), req.getAmount(), requestId, e.getReason());
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId), requestId));
            return;
        } catch (Exception e) {
            log.error("Place bid failed: auctionId={}, username={}, amount={}, requestId={}",
                    req.getAuctionId(), session.getUsername(), req.getAmount(), requestId, e);
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
            return;
        }

        // ── Mọi thứ bên dưới chạy HOÀN TOÀN NGOÀI per-auction lock ──────────
        // bidService.placeBid() đã hoàn tất: RAM update + DB write + observer notify.

        long confirmedAmount     = req.getAmount();  // giá tại thời điểm bid được chấp nhận
        LocalDateTime endTimeAfter = auction.getEndTime();
        boolean extended         = endTimeAfter != null && !endTimeAfter.equals(endTimeBefore);

        // Gửi PLACE_BID_SUCCESS cho người vừa bid
        BidDTOs.BidResultDTO result = new BidDTOs.BidResultDTO();
        result.setAuctionId(req.getAuctionId());
        result.setAmount(confirmedAmount);
        result.setCurrentPrice(confirmedAmount);  // giá tại thời điểm bid accepted
        result.setReserveMet(auction.isReserveMet());
        result.setTimestamp(LocalDateTime.now());
        session.send(Packet.of(PacketType.PLACE_BID_SUCCESS, result, requestId));

        // Chuẩn bị và broadcast async (không block bid tiếp theo)
        BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, confirmedAmount, previousPrice);
        if (extended) {
            update.setNewEndTime(endTimeAfter);
            AuctionDTOs.AuctionExtendedDTO extDto = new AuctionDTOs.AuctionExtendedDTO();
            extDto.setAuctionId(req.getAuctionId());
            extDto.setNewEndTime(endTimeAfter);
            extDto.setExtendedBySeconds(60);
            sessionManager.broadcastToAuctionAsync(req.getAuctionId(),
                    Packet.of(PacketType.AUCTION_EXTENDED_NOTIFY, extDto));
            log.info("Auction extension broadcast queued: auctionId={}, requestId={}", req.getAuctionId(), requestId);
        }
        PacketType broadcastType = auction.isReserveMet()
                ? PacketType.BID_UPDATE : PacketType.BID_RESERVE_NOT_MET_UPDATE;
        sessionManager.broadcastToAuctionAsync(req.getAuctionId(), Packet.of(broadcastType, update));
        sessionManager.broadcastToAuctionAsync(req.getAuctionId(),
                Packet.of(PacketType.BID_CHART_POINT_UPDATE,
                        DTOMapper.toBidChartPoint(req.getAuctionId(), confirmedAmount, bidder.getUsername(), false)));

        // AutoBid ngoài lock — tự acquire lock nội bộ cho từng bid trong chain
        autoBidProcessor.process(auction, bidder.getId());
    }


    // ── REGISTER AUTO-BID ─────────────────────────────────────────────────────

    private void handleRegisterAutoBid(ClientSession session, JsonElement payload, String requestId) {
        BidDTOs.AutoBidRequestDTO req;
        try {
            req = PacketCodec.fromElement(payload, BidDTOs.AutoBidRequestDTO.class);
        } catch (Exception e) {
            log.warn("Invalid register auto-bid payload: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Payload không hợp lệ.", requestId), requestId));
            return;
        }

        Auction registerAutoBidAuction  = null;
        String  registerAutoBidBidderId = null;

        // FIX Bug 2: resolve user trước lock — DB read không được giữ per-auction lock
        NormalUser bidder = requireNormalUser(session, requestId);
        if (bidder == null) return;

        ReentrantLock lock = lockRegistry.getLock(req.getAuctionId());
        lock.lock();
        try {
            // bidder đã resolve trước lock
            Auction auction = requireAuction(session, req.getAuctionId(), requestId);
            if (auction == null) return;

            registerAutoBidAuction  = auction;
            registerAutoBidBidderId = bidder.getId();

            AutoBidStrategy strategy = new AutoBidStrategy(req.getMaxBid());
            long nextBid = strategy.calculateNextBid(auction);

            if (nextBid < 0) {
                log.warn("Register auto-bid rejected because maxBid is too low: auctionId={}, bidderId={}, maxBid={}, currentPrice={}",
                        req.getAuctionId(), bidder.getId(), req.getMaxBid(), auction.getCurrentPrice());
                session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                        ErrorDTO.of(ErrorDTO.MAX_BID_TOO_LOW,
                                String.format("maxBid %d quá thấp, phải > giá hiện tại %d + bước giá.",
                                        req.getMaxBid(), auction.getCurrentPrice()),
                                requestId), requestId));
                return;
            }

            autoBidRegistry.register(bidder.getId(), req.getAuctionId(), req.getMaxBid());
            log.info("Auto-bid registered: auctionId={}, bidderId={}, username={}, maxBid={}, firstBid={}",
                    req.getAuctionId(), bidder.getId(), bidder.getUsername(), req.getMaxBid(), nextBid);
            try {
                bidService.placeBid(bidder, auction, nextBid, strategy);
            } catch (Exception ex) {
                autoBidRegistry.cancel(bidder.getId(), req.getAuctionId());
                throw ex;
            }

            BidDTOs.AutoBidRegistrationDTO reg = new BidDTOs.AutoBidRegistrationDTO();
            reg.setAuctionId(req.getAuctionId());
            reg.setMaxBid(req.getMaxBid());
            reg.setCurrentSystemBid(nextBid);
            reg.setActive(true);
            reg.setRegisteredAt(LocalDateTime.now());
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_SUCCESS, reg, requestId));

            BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, nextBid);
            sessionManager.broadcastToAuction(req.getAuctionId(),
                    Packet.of(PacketType.BID_UPDATE, update));

            BidDTOs.BidChartPointDTO chartPoint = DTOMapper.toBidChartPoint(
                    req.getAuctionId(), nextBid, bidder.getUsername(), true);
            sessionManager.broadcastToAuction(req.getAuctionId(),
                    Packet.of(PacketType.BID_CHART_POINT_UPDATE, chartPoint));

        } catch (AuctionBusinessException e) {
            log.warn("Register auto-bid rejected by business rule: auctionId={}, username={}, requestId={}, reason={}",
                    req.getAuctionId(), session.getUsername(), requestId, e.getReason());
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                    ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("Register auto-bid failed: auctionId={}, username={}, requestId={}",
                    req.getAuctionId(), session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        } finally {
            lock.unlock();
        }
        // FIX DEADLOCK: process ngoài lock
        if (registerAutoBidAuction != null && registerAutoBidBidderId != null) {
            autoBidProcessor.process(registerAutoBidAuction, registerAutoBidBidderId);
        }
    }

    // ── UPDATE AUTO-BID ───────────────────────────────────────────────────────

    private void handleUpdateAutoBid(ClientSession session, JsonElement payload, String requestId) {
        BidDTOs.AutoBidRequestDTO req;
        try {
            req = PacketCodec.fromElement(payload, BidDTOs.AutoBidRequestDTO.class);
        } catch (Exception e) {
            log.warn("Invalid update auto-bid payload: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.UPDATE_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Payload không hợp lệ.", requestId), requestId));
            return;
        }

        Auction updateAutoBidAuction  = null;
        String  updateAutoBidBidderId = null;

        // FIX Bug 2: resolve user trước lock — DB read không được giữ per-auction lock
        NormalUser bidder = requireNormalUser(session, requestId);
        if (bidder == null) return;

        ReentrantLock lock = lockRegistry.getLock(req.getAuctionId());
        lock.lock();
        try {
            // bidder đã resolve trước lock
            AutoBidEntry existing = autoBidRegistry.get(bidder.getId(), req.getAuctionId());
            if (existing == null) {
                log.warn("Update auto-bid rejected because entry does not exist: auctionId={}, bidderId={}",
                        req.getAuctionId(), bidder.getId());
                session.send(Packet.of(PacketType.UPDATE_AUTO_BID_FAILED,
                        ErrorDTO.of("NO_AUTO_BID",
                                "Chưa có auto-bid trong phiên này. Hãy dùng REGISTER_AUTO_BID trước.",
                                requestId), requestId));
                return;
            }

            Auction auction = requireAuction(session, req.getAuctionId(), requestId);
            if (auction == null) return;

            updateAutoBidAuction  = auction;
            updateAutoBidBidderId = bidder.getId();

            if (req.getMaxBid() <= existing.getMaxBid()) {
                log.warn("Update auto-bid rejected because maxBid did not increase: auctionId={}, bidderId={}, oldMaxBid={}, newMaxBid={}",
                        req.getAuctionId(), bidder.getId(), existing.getMaxBid(), req.getMaxBid());
                session.send(Packet.of(PacketType.UPDATE_AUTO_BID_FAILED,
                        ErrorDTO.of("INVALID_MAX_BID",
                                String.format("maxBid mới (%d) phải lớn hơn maxBid hiện tại (%d).",
                                        req.getMaxBid(), existing.getMaxBid()),
                                requestId), requestId));
                return;
            }

            long oldMaxBid = existing.getMaxBid();
            autoBidRegistry.register(bidder.getId(), req.getAuctionId(), req.getMaxBid());

            BidDTOs.AutoBidRegistrationDTO reg = new BidDTOs.AutoBidRegistrationDTO();
            reg.setAuctionId(req.getAuctionId());
            reg.setMaxBid(req.getMaxBid());
            reg.setCurrentSystemBid(auction.getCurrentPrice());
            reg.setActive(true);
            reg.setRegisteredAt(LocalDateTime.now());
            session.send(Packet.of(PacketType.UPDATE_AUTO_BID_SUCCESS, reg, requestId));

            log.info("Auto-bid updated: auctionId={}, bidderId={}, username={}, oldMaxBid={}, newMaxBid={}",
                    req.getAuctionId(), bidder.getId(), bidder.getUsername(), oldMaxBid, req.getMaxBid());

        } catch (Exception e) {
            log.error("Update auto-bid failed: auctionId={}, username={}, requestId={}",
                    req.getAuctionId(), session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.UPDATE_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        } finally {
            lock.unlock();
        }
        // FIX DEADLOCK: process ngoài lock
        if (updateAutoBidAuction != null && updateAutoBidBidderId != null) {
            autoBidProcessor.process(updateAutoBidAuction, updateAutoBidBidderId);
        }
    }

    // ── CANCEL AUTO-BID ───────────────────────────────────────────────────────

    private void handleCancelAutoBid(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);

            // FIX Bug 2: resolve user trước lock — DB read không được giữ per-auction lock
            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            ReentrantLock lock = lockRegistry.getLock(auctionId);
            lock.lock();
            try {
                // bidder đã resolve trước lock
                boolean cancelled = autoBidRegistry.cancel(bidder.getId(), auctionId);
                if (!cancelled) {
                    log.warn("Cancel auto-bid requested but entry does not exist: auctionId={}, bidderId={}, username={}",
                            auctionId, bidder.getId(), bidder.getUsername());
                }
                log.info("Auto-bid cancel handled: auctionId={}, bidderId={}, username={}, cancelled={}",
                        auctionId, bidder.getId(), bidder.getUsername(), cancelled);
                session.send(Packet.of(PacketType.CANCEL_AUTO_BID_SUCCESS, auctionId, requestId));
            } finally {
                lock.unlock();
            }

        } catch (Exception e) {
            log.error("Cancel auto-bid failed: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.CANCEL_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── GET AUTO-BID STATUS ───────────────────────────────────────────────────

    private void handleGetAutoBidStatus(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            AutoBidEntry entry = autoBidRegistry.get(bidder.getId(), auctionId);
            BidDTOs.AutoBidRegistrationDTO dto = new BidDTOs.AutoBidRegistrationDTO();
            dto.setAuctionId(auctionId);

            if (entry != null) {
                Auction auction = AuctionManager.getInstance().findAuctionById(auctionId);
                dto.setMaxBid(entry.getMaxBid());
                dto.setCurrentSystemBid(auction != null ? auction.getCurrentPrice() : 0);
                dto.setActive(true);
                dto.setRegisteredAt(entry.getRegisteredAt());
            } else {
                dto.setMaxBid(0);
                dto.setCurrentSystemBid(0);
                dto.setActive(false);
                dto.setRegisteredAt(null);
            }

            session.send(Packet.of(PacketType.GET_AUTO_BID_STATUS_SUCCESS, dto, requestId));

        } catch (Exception e) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── GET BID HISTORY ───────────────────────────────────────────────────────

    private void handleGetBidHistory(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction = requireAuction(session, auctionId, requestId);
            if (auction == null) return;

            // Query trực tiếp từ DB để lấy đủ lịch sử, kể cả sau restart server.
            // findByAuctionId() đã được sửa để lọc REJECTED bids (Bug #1 fix).
            List<BidTransaction> txList = bidTransactionDAO.findByAuctionId(auctionId);

            List<BidDTOs.BidChartPointDTO> points = new ArrayList<>();
            for (BidTransaction tx : txList) {
                // FIX BUG #1: Defensive check — lọc thêm ở tầng handler phòng trường hợp
                // findByAuctionId() trả về cả REJECTED (ví dụ dùng phiên bản DAO cũ)
                if (tx.getResult() == BidTransaction.BidResult.REJECTED) continue;

                BidDTOs.BidChartPointDTO point = new BidDTOs.BidChartPointDTO();
                point.setAuctionId(auctionId);
                point.setPrice(tx.getAmount());
                point.setBidderUsername(tx.getBidder() != null ? tx.getBidder().getUsername() : "Unknown");
                point.setTimestamp(tx.getTimestamp());
                point.setAutoBid(false);
                points.add(point);
            }

            BidDTOs.BidHistoryResponseDTO resp = new BidDTOs.BidHistoryResponseDTO();
            resp.setAuctionId(auctionId);
            resp.setPoints(points);
            resp.setStartingPrice(auction.getItem().getStartingPrice());
            resp.setReservePrice(auction.getReservePrice());

            session.send(Packet.of(PacketType.GET_BID_HISTORY_SUCCESS, resp, requestId));

        } catch (Exception e) {
            session.send(Packet.of(PacketType.GET_BID_HISTORY_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Auction requireAuction(ClientSession session, String auctionId, String requestId) {
        Auction auction = AuctionManager.getInstance().findAuctionById(auctionId);
        if (auction == null) {
            log.warn("Auction not found while handling bid request: auctionId={}, requestId={}",
                    auctionId, requestId);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND,
                            "Phiên đấu giá không tồn tại: " + auctionId, requestId), requestId));
        }
        return auction;
    }

    private NormalUser requireNormalUser(ClientSession session, String requestId) {
        // FIX PERFORMANCE: kiểm tra session cache trước — tránh DB lookup mỗi bid.
        // Cache được set lần đầu tiên khi load từ DB, sau đó tái dùng cho mọi request
        // của cùng session (bid, join, watch, leave). Object được update in-place bởi
        // các operation như joinAuction (addJoinedAuction) và leaveAuction (removeJoinedAuction).
        NormalUser cached = session.getCachedUser();
        if (cached != null) return cached;

        com.group13.auction.model.user.User user =
                AuctionManager.getInstance().findUserByUsername(session.getUsername());
        if (!(user instanceof NormalUser)) {
            log.warn("NormalUser required but session user is invalid: username={}, requestId={}",
                    session.getUsername(), requestId);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                            "Chỉ NormalUser mới có thể thực hiện hành động này.", requestId), requestId));
            return null;
        }
        NormalUser normalUser = (NormalUser) user;
        session.setCachedUser(normalUser);  // cache để dùng lại cho các request tiếp theo
        return normalUser;
    }
}