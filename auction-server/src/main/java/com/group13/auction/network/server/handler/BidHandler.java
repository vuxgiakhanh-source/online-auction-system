package com.group13.auction.network.server.handler;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
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
import com.group13.auction.strategy.StandardBidStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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
 */
public class BidHandler implements PacketHandler {

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

    private final BidService          bidService;
    private final IRatingService      ratingService;
    private final SessionManager      sessionManager;
    private final BidTransactionDAO   bidTransactionDAO;
    private final AutoBidRegistry     autoBidRegistry = AutoBidRegistry.getInstance();
    private final AuctionLockRegistry lockRegistry    = AuctionLockRegistry.getInstance();
    private final AutoBidProcessor    autoBidProcessor;

    /**
     * Constructor đầy đủ — dùng khi muốn truyền ratingService vào BidderObserver.
     */
    public BidHandler(BidService bidService, IRatingService ratingService,
                      SessionManager sessionManager, BidTransactionDAO bidTransactionDAO) {
        this.bidService        = bidService;
        this.ratingService     = ratingService;
        this.sessionManager    = sessionManager;
        this.bidTransactionDAO = bidTransactionDAO;
        this.autoBidProcessor  = new AutoBidProcessor(bidService, sessionManager);
    }

    /**
     * Backward-compat constructor — giữ nguyên chữ ký cũ từ AuctionWebSocketServer.
     * ratingService = null (BidderObserver sẽ nhận null, hoạt động bình thường
     * vì ratingService chưa được dùng trong body observer).
     */
    public BidHandler(BidService bidService, SessionManager sessionManager) {
        this(bidService, null, sessionManager, new BidTransactionDAO());
    }

    @Override
    public boolean supports(PacketType type) { return SUPPORTED.contains(type); }

    @Override
    public void handle(ClientSession session, PacketType type,
                       JsonElement payload, String requestId) {
        if (!session.isAuthenticated()) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chưa đăng nhập.", requestId)));
            return;
        }

        switch (type) {
            case JOIN_AUCTION        -> handleJoin(session, payload, requestId);
            case WATCH_AUCTION       -> handleWatch(session, payload, requestId);
            case LEAVE_AUCTION       -> handleLeave(session, payload, requestId);
            case PLACE_BID           -> handlePlaceBid(session, payload, requestId);
            case REGISTER_AUTO_BID   -> handleRegisterAutoBid(session, payload, requestId);
            case UPDATE_AUTO_BID     -> handleUpdateAutoBid(session, payload, requestId);
            case CANCEL_AUTO_BID     -> handleCancelAutoBid(session, payload, requestId);
            case GET_AUTO_BID_STATUS -> handleGetAutoBidStatus(session, payload, requestId);
            case GET_BID_HISTORY     -> handleGetBidHistory(session, payload, requestId);
            default -> {}
        }
    }

    // ── JOIN ──────────────────────────────────────────────────────────────────

    private void handleJoin(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction  = requireAuction(session, auctionId, requestId);
            if (auction == null) return;

            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            BidderObserver observer = new BidderObserver(bidder, ratingService);
            bidService.joinAuction(bidder, auction, observer);
            sessionManager.addAuctionWatcher(session.getConnection(), auctionId);

            long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;
            AuctionDTOs.JoinAuctionResponseDTO response = new AuctionDTOs.JoinAuctionResponseDTO();
            response.setAuction(DTOMapper.toAuctionDTO(auction));
            response.setDepositAmount(depositAmount);
            response.setNewAvailableBalance(bidder.getAvailableBalance());

            session.send(Packet.of(PacketType.JOIN_AUCTION_SUCCESS, response, requestId));

        } catch (AuctionBusinessException e) {
            session.send(Packet.of(PacketType.JOIN_AUCTION_FAILED,
                    ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.JOIN_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── WATCH ─────────────────────────────────────────────────────────────────

    private void handleWatch(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction  = requireAuction(session, auctionId, requestId);
            if (auction == null) return;

            NormalUser user = requireNormalUser(session, requestId);
            if (user == null) return;

            BidderObserver observer = new BidderObserver(user, ratingService);
            bidService.watchAuction(user, auction, observer);
            sessionManager.addAuctionWatcher(session.getConnection(), auctionId);

            session.send(Packet.of(PacketType.WATCH_AUCTION_SUCCESS,
                    DTOMapper.toAuctionDTO(auction), requestId));

        } catch (Exception e) {
            session.send(Packet.of(PacketType.WATCH_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── LEAVE ─────────────────────────────────────────────────────────────────

    private void handleLeave(ClientSession session, JsonElement payload, String requestId) {
        String auctionId = PacketCodec.fromElement(payload, String.class);
        sessionManager.removeAuctionWatcher(session.getConnection(), auctionId);
        session.send(Packet.of(PacketType.LEAVE_AUCTION_SUCCESS, null, requestId));
    }

    // ── PLACE BID ─────────────────────────────────────────────────────────────

    /**
     * Đặt giá thủ công — toàn bộ critical section nằm trong per-auction lock.
     *
     * <h3>Quy trình:</h3>
     * <ol>
     *   <li>Acquire lock (block nếu thread khác đang bid cùng phiên).</li>
     *   <li>{@link BidService#placeBid()} — validate + update price + anti-sniping.</li>
     *   <li>PLACE_BID_SUCCESS về cho người bid.</li>
     *   <li>Broadcast BID_UPDATE kèm newEndTime nếu anti-sniping kích hoạt.</li>
     *   <li>{@link AutoBidProcessor#process()} trigger counter-bid chain.</li>
     *   <li>Release lock trong finally.</li>
     * </ol>
     */
    private void handlePlaceBid(ClientSession session, JsonElement payload, String requestId) {
        BidDTOs.BidRequestDTO req;
        try {
            req = PacketCodec.fromElement(payload, BidDTOs.BidRequestDTO.class);
        } catch (Exception e) {
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Payload không hợp lệ.", requestId)));
            return;
        }

        ReentrantLock lock = lockRegistry.getLock(req.getAuctionId());
        lock.lock();
        try {
            Auction auction = requireAuction(session, req.getAuctionId(), requestId);
            if (auction == null) return;

            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            LocalDateTime endTimeBefore = auction.getEndTime();
            bidService.placeBid(bidder, auction, req.getAmount(), new StandardBidStrategy());

            // Phản hồi riêng cho người vừa bid
            BidDTOs.BidResultDTO result = new BidDTOs.BidResultDTO();
            result.setAuctionId(req.getAuctionId());
            result.setAmount(req.getAmount());
            result.setCurrentPrice(auction.getCurrentPrice());
            result.setReserveMet(auction.isReserveMet());
            result.setTimestamp(LocalDateTime.now());
            session.send(Packet.of(PacketType.PLACE_BID_SUCCESS, result, requestId));

            // Broadcast BID_UPDATE — kèm newEndTime nếu anti-sniping kích hoạt
            BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, req.getAmount());
            LocalDateTime endTimeAfter = auction.getEndTime();
            if (!endTimeAfter.equals(endTimeBefore)) {
                update.setNewEndTime(endTimeAfter);
            }
            PacketType broadcastType = auction.isReserveMet()
                    ? PacketType.BID_UPDATE
                    : PacketType.BID_RESERVE_NOT_MET_UPDATE;
            sessionManager.broadcastToAuction(req.getAuctionId(), Packet.of(broadcastType, update));

            // Chart point (isAutoBid = false)
            BidDTOs.BidChartPointDTO chartPoint = DTOMapper.toBidChartPoint(
                    req.getAuctionId(), req.getAmount(), bidder.getUsername(), false);
            sessionManager.broadcastToAuction(req.getAuctionId(),
                    Packet.of(PacketType.BID_CHART_POINT_UPDATE, chartPoint));

            // ★ Trigger auto-bid chain (trong lock để đảm bảo atomicity)
            autoBidProcessor.process(auction, bidder.getId());

        } catch (AuctionClosedException e) {
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.AUCTION_CLOSED, e.getMessage(), requestId)));
        } catch (InvalidBidException e) {
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.BID_TOO_LOW, e.getMessage(), requestId)));
        } catch (AuctionBusinessException e) {
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.PLACE_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        } finally {
            lock.unlock();
        }
    }

    // ── REGISTER AUTO-BID ─────────────────────────────────────────────────────

    /**
     * Đăng ký auto-bid: lưu registry → đặt bid đầu tiên → trigger counter chain.
     * Chạy trong per-auction lock.
     */
    private void handleRegisterAutoBid(ClientSession session, JsonElement payload, String requestId) {
        BidDTOs.AutoBidRequestDTO req;
        try {
            req = PacketCodec.fromElement(payload, BidDTOs.AutoBidRequestDTO.class);
        } catch (Exception e) {
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Payload không hợp lệ.", requestId)));
            return;
        }

        ReentrantLock lock = lockRegistry.getLock(req.getAuctionId());
        lock.lock();
        try {
            Auction auction = requireAuction(session, req.getAuctionId(), requestId);
            if (auction == null) return;

            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            AutoBidStrategy strategy = new AutoBidStrategy(req.getMaxBid());
            long nextBid = strategy.calculateNextBid(auction);

            if (nextBid < 0) {
                session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                        ErrorDTO.of(ErrorDTO.MAX_BID_TOO_LOW,
                                String.format("maxBid %d quá thấp, phải > giá hiện tại %d + bước giá.",
                                        req.getMaxBid(), auction.getCurrentPrice()),
                                requestId)));
                return;
            }

            autoBidRegistry.register(bidder.getId(), req.getAuctionId(), req.getMaxBid());
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

            // ★ Trigger counter-bid nếu có người khác bị vượt
            autoBidProcessor.process(auction, bidder.getId());

        } catch (AuctionBusinessException e) {
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                    ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        } finally {
            lock.unlock();
        }
    }

    // ── UPDATE AUTO-BID ───────────────────────────────────────────────────────

    /**
     * Cập nhật maxBid. Chỉ update registry, không đặt bid ngay.
     */
    private void handleUpdateAutoBid(ClientSession session, JsonElement payload, String requestId) {
        BidDTOs.AutoBidRequestDTO req;
        try {
            req = PacketCodec.fromElement(payload, BidDTOs.AutoBidRequestDTO.class);
        } catch (Exception e) {
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Payload không hợp lệ.", requestId)));
            return;
        }

        ReentrantLock lock = lockRegistry.getLock(req.getAuctionId());
        lock.lock();
        try {
            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            AutoBidEntry existing = autoBidRegistry.get(bidder.getId(), req.getAuctionId());
            if (existing == null) {
                session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                        ErrorDTO.of("NO_AUTO_BID",
                                "Chưa có auto-bid trong phiên này. Hãy dùng REGISTER_AUTO_BID trước.",
                                requestId)));
                return;
            }

            Auction auction = requireAuction(session, req.getAuctionId(), requestId);
            if (auction == null) return;

            if (req.getMaxBid() <= existing.getMaxBid()) {
                session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                        ErrorDTO.of("INVALID_MAX_BID",
                                String.format("maxBid mới (%d) phải lớn hơn maxBid hiện tại (%d).",
                                        req.getMaxBid(), existing.getMaxBid()),
                                requestId)));
                return;
            }

            autoBidRegistry.register(bidder.getId(), req.getAuctionId(), req.getMaxBid());

            BidDTOs.AutoBidRegistrationDTO reg = new BidDTOs.AutoBidRegistrationDTO();
            reg.setAuctionId(req.getAuctionId());
            reg.setMaxBid(req.getMaxBid());
            reg.setCurrentSystemBid(auction.getCurrentPrice());
            reg.setActive(true);
            reg.setRegisteredAt(LocalDateTime.now());
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_SUCCESS, reg, requestId));

            System.out.printf("[BID HANDLER] %s cập nhật auto-bid: %d → %d%n",
                    bidder.getUsername(), existing.getMaxBid(), req.getMaxBid());

        } catch (Exception e) {
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        } finally {
            lock.unlock();
        }
    }

    // ── CANCEL AUTO-BID ───────────────────────────────────────────────────────

    private void handleCancelAutoBid(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            ReentrantLock lock = lockRegistry.getLock(auctionId);
            lock.lock();
            try {
            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            boolean cancelled = autoBidRegistry.cancel(bidder.getId(), auctionId);
            if (!cancelled) {
                System.out.printf("[BID HANDLER] %s cancel auto-bid nhưng không có entry (auction=%s).%n",
                        bidder.getUsername(), auctionId);
            }
            session.send(Packet.of(PacketType.CANCEL_AUTO_BID_SUCCESS, auctionId, requestId));
            } finally {
                lock.unlock();
            }

        } catch (Exception e) {
            session.send(Packet.of(PacketType.CANCEL_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
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
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── GET BID HISTORY ───────────────────────────────────────────────────────

    private void handleGetBidHistory(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction  = requireAuction(session, auctionId, requestId);
            if (auction == null) return;

            List<BidTransaction> txList = bidTransactionDAO.findBidHistoryByAuction(auctionId);
            List<BidDTOs.BidChartPointDTO> points = new ArrayList<>();
            for (BidTransaction tx : txList) {
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
            resp.setReservePrice(auction.getReserveStrategy().getReservePrice());

            session.send(Packet.of(PacketType.GET_BID_HISTORY_SUCCESS, resp, requestId));

        } catch (Exception e) {
            session.send(Packet.of(PacketType.GET_BID_HISTORY_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Auction requireAuction(ClientSession session, String auctionId, String requestId) {
        Auction auction = AuctionManager.getInstance().findAuctionById(auctionId);
        if (auction == null) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND,
                            "Phiên đấu giá không tồn tại: " + auctionId, requestId)));
        }
        return auction;
    }

    private NormalUser requireNormalUser(ClientSession session, String requestId) {
        com.group13.auction.model.user.User user =
                AuctionManager.getInstance().findUserByUsername(session.getUsername());
        if (!(user instanceof NormalUser)) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                            "Chỉ NormalUser mới có thể thực hiện hành động này.", requestId)));
            return null;
        }
        return (NormalUser) user;
    }
}

