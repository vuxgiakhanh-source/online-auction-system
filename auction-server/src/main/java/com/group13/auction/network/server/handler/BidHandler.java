package com.group13.auction.network.server.handler;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.exception.AuctionClosedException;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.service.BidService;
import com.group13.auction.service.WalletService;
import com.group13.auction.strategy.AutoBidStrategy;
import com.group13.auction.strategy.StandardBidStrategy;

import java.util.EnumSet;
import java.util.Set;

/**
 * Xử lý các packet liên quan đến đấu giá trực tiếp:
 * JOIN_AUCTION, WATCH_AUCTION, LEAVE_AUCTION,
 * PLACE_BID, REGISTER_AUTO_BID, UPDATE_AUTO_BID,
 * CANCEL_AUTO_BID, GET_AUTO_BID_STATUS,
 * GET_BID_HISTORY.
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

    private final BidService bidService;
    private final SessionManager sessionManager;

    public BidHandler(BidService bidService, SessionManager sessionManager) {
        this.bidService = bidService;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean supports(PacketType type) { return SUPPORTED.contains(type); }

    @Override
    public void handle(ClientSession session, PacketType type,
                       JsonElement payload, String requestId) {
        // Mọi bid action đều yêu cầu xác thực
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
            Auction auction = requireAuction(session, auctionId, requestId);
            if (auction == null) return;

            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            // Observer bridge: khi server nhận event từ AuctionService,
            // nó sẽ push packet tới client qua session
            BidderObserver observer = new BidderObserver(bidder);

            bidService.joinAuction(bidder, auction, observer);

            // Đăng ký session watching auction này
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
            Auction auction = requireAuction(session, auctionId, requestId);
            if (auction == null) return;

            NormalUser user = requireNormalUser(session, requestId);
            if (user == null) return;

            BidderObserver observer = new BidderObserver(user);
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

    private void handlePlaceBid(ClientSession session, JsonElement payload, String requestId) {
        try {
            BidDTOs.BidRequestDTO req = PacketCodec.fromElement(payload, BidDTOs.BidRequestDTO.class);
            Auction auction = requireAuction(session, req.getAuctionId(), requestId);
            if (auction == null) return;

            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            bidService.placeBid(bidder, auction, req.getAmount(), new StandardBidStrategy());

            BidDTOs.BidResultDTO result = new BidDTOs.BidResultDTO();
            result.setAuctionId(req.getAuctionId());
            result.setAmount(req.getAmount());
            result.setCurrentPrice(auction.getCurrentPrice());
            result.setReserveMet(auction.isReserveMet());
            result.setTimestamp(java.time.LocalDateTime.now());

            session.send(Packet.of(PacketType.PLACE_BID_SUCCESS, result, requestId));

            // Broadcast tới tất cả client xem phiên này
            BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, req.getAmount());
            PacketType broadcastType = auction.isReserveMet()
                    ? PacketType.BID_UPDATE
                    : PacketType.BID_RESERVE_NOT_MET_UPDATE;
            sessionManager.broadcastToAuction(req.getAuctionId(),
                    Packet.of(broadcastType, update));

            // Cập nhật chart point cho tất cả watcher
            BidDTOs.BidChartPointDTO chartPoint = DTOMapper.toBidChartPoint(
                    req.getAuctionId(), req.getAmount(), bidder.getUsername(), false);
            sessionManager.broadcastToAuction(req.getAuctionId(),
                    Packet.of(PacketType.BID_CHART_POINT_UPDATE, chartPoint));

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
        }
    }

    // ── REGISTER AUTO-BID ────────────────────────────────────────────────────

    private void handleRegisterAutoBid(ClientSession session, JsonElement payload, String requestId) {
        try {
            BidDTOs.AutoBidRequestDTO req = PacketCodec.fromElement(
                    payload, BidDTOs.AutoBidRequestDTO.class);
            Auction auction = requireAuction(session, req.getAuctionId(), requestId);
            if (auction == null) return;

            NormalUser bidder = requireNormalUser(session, requestId);
            if (bidder == null) return;

            AutoBidStrategy strategy = new AutoBidStrategy(req.getMaxBid());

            // Ngay lập tức tính và đặt bid nếu maxBid > giá hiện tại
            long nextBid = strategy.calculateNextBid(auction);
            if (nextBid < 0) {
                session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                        ErrorDTO.of(ErrorDTO.MAX_BID_TOO_LOW,
                                "maxBid thấp hơn hoặc bằng giá hiện tại + bước giá.", requestId)));
                return;
            }

            // Đặt bid đầu tiên ngay
            bidService.placeBid(bidder, auction, nextBid, strategy);

            BidDTOs.AutoBidRegistrationDTO reg = new BidDTOs.AutoBidRegistrationDTO();
            reg.setAuctionId(req.getAuctionId());
            reg.setMaxBid(req.getMaxBid());
            reg.setCurrentSystemBid(nextBid);
            reg.setActive(true);
            reg.setRegisteredAt(java.time.LocalDateTime.now());

            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_SUCCESS, reg, requestId));

            // Broadcast bid update
            BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, nextBid);
            sessionManager.broadcastToAuction(req.getAuctionId(),
                    Packet.of(PacketType.BID_UPDATE, update));

            BidDTOs.BidChartPointDTO chartPoint = DTOMapper.toBidChartPoint(
                    req.getAuctionId(), nextBid, bidder.getUsername(), true);
            sessionManager.broadcastToAuction(req.getAuctionId(),
                    Packet.of(PacketType.BID_CHART_POINT_UPDATE, chartPoint));

        } catch (AuctionBusinessException e) {
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                    ErrorDTO.of(e.getReason().name(), e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.REGISTER_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── UPDATE AUTO-BID ───────────────────────────────────────────────────────

    private void handleUpdateAutoBid(ClientSession session, JsonElement payload, String requestId) {
        // Logic tương tự register — chỉ update maxBid
        // Trong hệ thống hiện tại AutoBidStrategy là immutable, cần re-register
        handleRegisterAutoBid(session, payload, requestId);
        // TODO: thay REGISTER_AUTO_BID_SUCCESS bằng UPDATE_AUTO_BID_SUCCESS sau khi refactor
    }

    // ── CANCEL AUTO-BID ───────────────────────────────────────────────────────

    private void handleCancelAutoBid(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            // AutoBid trong hệ thống hiện tại không có cancel entity — chỉ notify client
            // TODO: khi implement AutoBidRegistry thì gọi autoBidRegistry.cancel(userId, auctionId)
            session.send(Packet.of(PacketType.CANCEL_AUTO_BID_SUCCESS, auctionId, requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.CANCEL_AUTO_BID_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── GET AUTO-BID STATUS ───────────────────────────────────────────────────

    private void handleGetAutoBidStatus(ClientSession session, JsonElement payload, String requestId) {
        // TODO: khi implement AutoBidRegistry thì query registry.get(userId, auctionId)
        session.send(Packet.of(PacketType.GET_AUTO_BID_STATUS_SUCCESS,
                null, requestId));
    }

    // ── GET BID HISTORY ───────────────────────────────────────────────────────

    private void handleGetBidHistory(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction = requireAuction(session, auctionId, requestId);
            if (auction == null) return;

            // TODO: query BidTransactionDAO.findByAuction(auctionId) để lấy lịch sử
            // Hiện tại trả về empty list
            BidDTOs.BidHistoryResponseDTO resp = new BidDTOs.BidHistoryResponseDTO();
            resp.setAuctionId(auctionId);
            resp.setPoints(java.util.Collections.emptyList());
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
