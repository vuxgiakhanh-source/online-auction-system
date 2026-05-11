package com.group13.auction.strategy;

import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.BidService;
import com.group13.auction.manager.AuctionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Engine xử lý Auto-Bid chain sau mỗi bid thành công.
 *
 * <h3>Cải tiến v2:</h3>
 * <ul>
 *   <li>Logging chuẩn SLF4J (xóa toàn bộ System.out.printf).</li>
 *   <li>Giới hạn chain depth để tránh infinite loop / stack overflow.</li>
 *   <li>User lookup có fallback DB đúng cách.</li>
 * </ul>
 *
 * <h3>QUAN TRỌNG — Thread-safety:</h3>
 * <p>Caller (BidHandler.handlePlaceBid) phải đang GIỮ LOCK khi gọi {@link #process()}.
 * AutoBidProcessor KHÔNG tự lock để tránh deadlock.
 */
public class AutoBidProcessor {

    private static final Logger log = LoggerFactory.getLogger(AutoBidProcessor.class);

    /** Giới hạn cứng số counter-bid liên tiếp để tránh stack overflow. */
    private static final int MAX_CHAIN_DEPTH = 20;

    private final BidService      bidService;
    private final SessionManager  sessionManager;
    private final AutoBidRegistry registry = AutoBidRegistry.getInstance();
    private final UserDAO         userDAO;

    public AutoBidProcessor(BidService bidService, SessionManager sessionManager) {
        this.bidService     = bidService;
        this.sessionManager = sessionManager;
        this.userDAO        = new UserDAO();
    }

    /**
     * Kích hoạt chuỗi auto-bid sau khi một bid vừa thành công.
     *
     * <p><b>Precondition:</b> Caller đang giữ lock của phiên. KHÔNG self-lock ở đây.
     *
     * @param auction            phiên vừa có bid mới
     * @param triggeredByUserId  userId vừa bid (để bỏ qua họ khi tìm counter)
     */
    public void process(Auction auction, String triggeredByUserId) {
        String auctionId = auction.getId();

        Collection<AutoBidRegistry.AutoBidEntry> allEntries = registry.getEntriesForAuction(auctionId);
        // maxIterations = max(MAX_CHAIN_DEPTH, allEntries.size() * 2 + 2)
        int maxIterations = Math.min(MAX_CHAIN_DEPTH,
                allEntries.isEmpty() ? 2 : allEntries.size() * 2 + 2);

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Collection<AutoBidRegistry.AutoBidEntry> entries = registry.getEntriesForAuction(auctionId);

            NormalUser currentLeader = auction.getCurrentLeader();
            String leaderId = (currentLeader != null) ? currentLeader.getId() : null;

            // Tìm candidates: bị vượt, có đủ budget, sort theo (maxBid DESC, registeredAt ASC)
            List<AutoBidRegistry.AutoBidEntry> candidates = new ArrayList<>();
            for (AutoBidRegistry.AutoBidEntry entry : entries) {
                if (entry.getUserId().equals(leaderId)) continue;
                long nextBid = entry.calculateNextBid(auction.getCurrentPrice());
                if (nextBid > 0) {
                    candidates.add(entry);
                }
            }

            if (candidates.isEmpty()) break;

            candidates.sort(
                    Comparator.comparingLong(AutoBidRegistry.AutoBidEntry::getMaxBid).reversed()
                            .thenComparing(AutoBidRegistry.AutoBidEntry::getRegisteredAt)
            );

            AutoBidRegistry.AutoBidEntry winner = candidates.get(0);
            long nextBid = winner.calculateNextBid(auction.getCurrentPrice());

            NormalUser autoBidder = findNormalUserById(winner.getUserId());
            if (autoBidder == null) {
                log.warn("auto-bid user not found, cancelling entry: userId={} auctionId={}",
                        winner.getUserId(), auctionId);
                registry.cancel(winner.getUserId(), auctionId);
                continue;
            }

            try {
                AutoBidStrategy strategy = new AutoBidStrategy(winner.getMaxBid());
                bidService.placeBid(autoBidder, auction, nextBid, strategy);

                sendAutoBidTriggeredNotify(winner, auction, nextBid);

                BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, nextBid);
                sessionManager.broadcastToAuction(auctionId,
                        Packet.of(PacketType.BID_UPDATE, update));

                BidDTOs.BidChartPointDTO chartPoint = DTOMapper.toBidChartPoint(
                        auctionId, nextBid, autoBidder.getUsername(), true);
                sessionManager.broadcastToAuction(auctionId,
                        Packet.of(PacketType.BID_CHART_POINT_UPDATE, chartPoint));

                log.info("auto-bid triggered: userId={} username={} auctionId={} amount={} iteration={}",
                        autoBidder.getId(), autoBidder.getUsername(), auctionId, nextBid, iteration + 1);

            } catch (Exception e) {
                log.warn("auto-bid failed, cancelling entry: userId={} auctionId={} reason={}",
                        winner.getUserId(), auctionId, e.getMessage());
                registry.cancel(winner.getUserId(), auctionId);
            }
        }

        notifyExhaustedBidders(auction);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void sendAutoBidTriggeredNotify(
            AutoBidRegistry.AutoBidEntry entry, Auction auction, long bidAmount) {
        BidDTOs.AutoBidTriggeredDTO dto = new BidDTOs.AutoBidTriggeredDTO();
        dto.setAuctionId(auction.getId());
        dto.setBidAmount(bidAmount);
        dto.setNewCurrentPrice(auction.getCurrentPrice());
        dto.setRemainingMaxBid(entry.getMaxBid() - bidAmount);
        dto.setNowLeading(auction.getCurrentLeader() != null
                && auction.getCurrentLeader().getId().equals(entry.getUserId()));
        dto.setTimestamp(LocalDateTime.now());

        sessionManager.sendToUser(entry.getUserId(),
                Packet.of(PacketType.AUTO_BID_TRIGGERED_NOTIFY, dto));
    }

    private void notifyExhaustedBidders(Auction auction) {
        String auctionId = auction.getId();
        Collection<AutoBidRegistry.AutoBidEntry> entries = registry.getEntriesForAuction(auctionId);

        NormalUser leader = auction.getCurrentLeader();
        String leaderId   = (leader != null) ? leader.getId() : null;

        for (AutoBidRegistry.AutoBidEntry entry : entries) {
            if (entry.getUserId().equals(leaderId)) continue;

            long nextBid = entry.calculateNextBid(auction.getCurrentPrice());
            if (nextBid < 0) {
                BidDTOs.AutoBidExhaustedDTO dto = new BidDTOs.AutoBidExhaustedDTO();
                dto.setAuctionId(auctionId);
                dto.setMaxBid(entry.getMaxBid());
                dto.setCurrentPrice(auction.getCurrentPrice());
                dto.setLeadingBidderUsername(leader != null ? leader.getUsername() : "Unknown");

                sessionManager.sendToUser(entry.getUserId(),
                        Packet.of(PacketType.AUTO_BID_EXHAUSTED_NOTIFY, dto));

                registry.cancel(entry.getUserId(), auctionId);

                log.info("auto-bid exhausted: userId={} auctionId={} maxBid={}",
                        entry.getUserId(), auctionId, entry.getMaxBid());
            }
        }
    }

    /**
     * Tìm NormalUser theo userId: ưu tiên in-memory, fallback DB.
     */
    private NormalUser findNormalUserById(String userId) {
        for (User u : AuctionManager.getInstance().getAllUsers()) {
            if (u.getId().equals(userId) && u instanceof NormalUser) {
                return (NormalUser) u;
            }
        }
        NormalUser fromDb = userDAO.findNormalUserById(userId);
        if (fromDb != null) {
            AuctionManager.getInstance().addToUserList(fromDb);
        }
        return fromDb;
    }
}