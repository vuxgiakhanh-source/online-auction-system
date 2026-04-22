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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Engine xử lý Auto-Bid: sau mỗi bid thành công (manual hoặc auto),
 * tìm tất cả auto-bid đang hoạt động trong phiên và kích hoạt counter-bid
 * cho những ai bị vượt qua.
 *
 * <h3>Luồng xử lý (gọi sau mỗi bid thành công):</h3>
 * <ol>
 *   <li>Lấy snapshot tất cả auto-bid của phiên từ {@link AutoBidRegistry}.</li>
 *   <li>Lọc ra những người KHÔNG đang dẫn đầu (bị vượt), có maxBid đủ để counter.</li>
 *   <li>Sort theo {@code registeredAt} tăng dần → người đăng ký sớm nhất được
 *       ưu tiên counter trước (tie-breaking như spec yêu cầu).</li>
 *   <li>Lấy người đầu tiên trong danh sách (ưu tiên cao nhất), tính nextBid,
 *       đặt bid qua {@link BidService#placeBid()} bên trong lock.</li>
 *   <li>Broadcast BID_UPDATE + BID_CHART_POINT_UPDATE + AUTO_BID_TRIGGERED_NOTIFY
 *       (chỉ gửi notify cho người vừa được tự bid).</li>
 *   <li>Lặp lại cho đến khi không còn ai có thể counter hoặc chỉ còn 1 người dẫn đầu.</li>
 * </ol>
 *
 * <h3>Xử lý tie (2 người cùng maxBid):</h3>
 * <p>Sort theo {@code registeredAt} → người đăng ký auto-bid trước thắng tie.
 * Điều này đúng với spec: "Ưu tiên theo thời điểm đăng ký auto-bid."
 *
 * <h3>Tránh infinite loop:</h3>
 * <p>Vòng lặp dừng khi {@code auction.getCurrentLeader()} là chính người
 * auto-bid đang xét (họ đã dẫn đầu), hoặc không còn ai đủ maxBid để counter.
 * Max iteration = số auto-bid trong phiên (bounded).
 *
 * <h3>Thread-safety:</h3>
 * <p>Mỗi lần gọi {@link #process()} đều acquire {@link AuctionLockRegistry}
 * lock của phiên → đảm bảo không có bid nào khác chen vào giữa auto-bid chain.
 * Lock được truyền vào từ caller (BidHandler) nên không re-lock (tránh deadlock).
 *
 * <p><b>QUAN TRỌNG:</b> Caller (BidHandler.handlePlaceBid) phải đang GIỮ LOCK
 * khi gọi {@link #process()} — AutoBidProcessor sẽ thực hiện bên trong
 * lock đó (không lock lại để tránh deadlock).
 */
public class AutoBidProcessor {

    private final BidService      bidService;
    private final SessionManager  sessionManager;
    private final AutoBidRegistry registry = AutoBidRegistry.getInstance();
    // FIX Bug #1: inject UserDAO để fallback DB khi user không tìm thấy in-memory
    private final UserDAO         userDAO;

    public AutoBidProcessor(BidService bidService, SessionManager sessionManager) {
        this.bidService     = bidService;
        this.sessionManager = sessionManager;
        this.userDAO        = new UserDAO();
    }

    /**
     * Kích hoạt chuỗi auto-bid sau khi một bid (manual hoặc auto) vừa thành công.
     *
     * <p><b>Precondition:</b> Caller đang giữ lock của phiên ({@link AuctionLockRegistry}).
     * Method này KHÔNG tự lock để tránh deadlock.
     *
     * @param auction phiên đấu giá vừa có bid mới
     * @param triggeredByUserId userId của người vừa bid (để bỏ qua họ trong danh sách counter)
     */
    public void process(Auction auction, String triggeredByUserId) {
        String auctionId = auction.getId();

        // Lấy snapshot để tránh ConcurrentModification khi iterate
        Collection<AutoBidRegistry.AutoBidEntry> allEntries = registry.getEntriesForAuction(auctionId);

        // Tối đa N lần lặp = số auto-bid trong phiên (bounded, không infinite loop)
        int maxIterations = allEntries.size();
        int iteration = 0;

        while (iteration < maxIterations) {
            iteration++;

            // Re-fetch snapshot mỗi vòng vì currentLeader có thể đã thay đổi
            Collection<AutoBidRegistry.AutoBidEntry> entries = registry.getEntriesForAuction(auctionId);

            NormalUser currentLeader = auction.getCurrentLeader();
            String leaderId = (currentLeader != null) ? currentLeader.getId() : null;

            // Tìm những auto-bidder bị vượt (không phải leader hiện tại),
            // sort theo registeredAt tăng dần → tie-breaking: đăng ký trước được ưu tiên
            List<AutoBidRegistry.AutoBidEntry> candidates = new ArrayList<>();
            for (AutoBidRegistry.AutoBidEntry entry : entries) {
                // Bỏ qua người đang dẫn đầu (họ không cần counter)
                if (entry.getUserId().equals(leaderId)) continue;
                long nextBid = Math.round(entry.calculateNextBid(auction.getCurrentPrice()));
                if (nextBid > 0) {
                    candidates.add(entry);
                }
            }

            if (candidates.isEmpty()) {
                // Không ai còn có thể counter → chuỗi kết thúc
                break;
            }

            // Sort: maxBid cao nhất trước (để người sẵn sàng trả nhiều hơn counter);
            // nếu maxBid bằng nhau → registeredAt sớm hơn được ưu tiên (đúng spec)
            candidates.sort(
                    Comparator.comparingDouble(AutoBidRegistry.AutoBidEntry::getMaxBid).reversed()
                            .thenComparing(AutoBidRegistry.AutoBidEntry::getRegisteredAt)
            );

            AutoBidRegistry.AutoBidEntry winner = candidates.get(0);
            long nextBid = Math.round(winner.calculateNextBid(auction.getCurrentPrice()));

            // FIX Bug #1: xóa dòng dead code findUserByUsername(null).
            // Dùng findNormalUserById với fallback DB đúng cách.
            NormalUser autoBidder = findNormalUserById(winner.getUserId());

            if (autoBidder == null) {
                // User không tìm thấy (đã bị xóa?) → xóa entry khỏi registry
                registry.cancel(winner.getUserId(), auctionId);
                continue;
            }

            // Đặt auto-bid (đã đang trong lock, không lock lại)
            try {
                AutoBidStrategy strategy = new AutoBidStrategy(Math.round(winner.getMaxBid()));
                bidService.placeBid(autoBidder, auction, nextBid, strategy);

                // Notify riêng cho người vừa được tự bid
                sendAutoBidTriggeredNotify(winner, auction, nextBid);

                // Broadcast BID_UPDATE cho toàn bộ watcher
                BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, nextBid);
                sessionManager.broadcastToAuction(auctionId,
                        Packet.of(PacketType.BID_UPDATE, update));

                // Chart point (isAutoBid = true)
                BidDTOs.BidChartPointDTO chartPoint = DTOMapper.toBidChartPoint(
                        auctionId, nextBid, autoBidder.getUsername(), true);
                sessionManager.broadcastToAuction(auctionId,
                        Packet.of(PacketType.BID_CHART_POINT_UPDATE, chartPoint));

                System.out.printf(
                        "[AUTO-BID PROCESSOR] %s tự counter-bid %.0f (phiên %s, vòng %d)%n",
                        autoBidder.getUsername(), nextBid, auctionId, iteration);

            } catch (Exception e) {
                // Bid thất bại (phiên đóng, maxBid cạn, ...) → xóa entry
                System.out.printf(
                        "[AUTO-BID PROCESSOR] %s counter-bid thất bại: %s → xóa entry.%n",
                        winner.getUserId(), e.getMessage());
                registry.cancel(winner.getUserId(), auctionId);
            }

            // Sau mỗi auto-bid thành công, kiểm tra xem còn ai bị vượt không.
            // Nếu leader mới (autoBidder) là người auto-bid cao nhất,
            // không ai còn có thể counter → vòng tiếp theo sẽ break.
        }

        // Sau khi chuỗi kết thúc, notify những người đã cạn maxBid
        notifyExhaustedBidders(auction);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Gửi AUTO_BID_TRIGGERED_NOTIFY cho người vừa được hệ thống tự bid.
     */
    private void sendAutoBidTriggeredNotify(
            AutoBidRegistry.AutoBidEntry entry, Auction auction, long bidAmount) {
        BidDTOs.AutoBidTriggeredDTO dto = new BidDTOs.AutoBidTriggeredDTO();
        dto.setAuctionId(auction.getId());
        dto.setBidAmount(bidAmount);
        dto.setNewCurrentPrice(auction.getCurrentPrice());
        dto.setRemainingMaxBid(Math.round(entry.getMaxBid()) - bidAmount);
        dto.setNowLeading(auction.getCurrentLeader() != null
                && auction.getCurrentLeader().getId().equals(entry.getUserId()));
        dto.setTimestamp(LocalDateTime.now());

        sessionManager.sendToUser(entry.getUserId(),
                Packet.of(PacketType.AUTO_BID_TRIGGERED_NOTIFY, dto));
    }

    /**
     * Sau khi chuỗi auto-bid kết thúc, tìm những auto-bid đã bị vượt và không thể
     * counter nữa (maxBid cạn), gửi AUTO_BID_EXHAUSTED_NOTIFY cho họ rồi xóa entry.
     */
    private void notifyExhaustedBidders(Auction auction) {
        String auctionId = auction.getId();
        Collection<AutoBidRegistry.AutoBidEntry> entries = registry.getEntriesForAuction(auctionId);

        NormalUser leader = auction.getCurrentLeader();
        String leaderId   = (leader != null) ? leader.getId() : null;

        for (AutoBidRegistry.AutoBidEntry entry : entries) {
            // Bỏ qua người đang dẫn đầu
            if (entry.getUserId().equals(leaderId)) continue;

            long nextBid = Math.round(entry.calculateNextBid(auction.getCurrentPrice()));
            if (nextBid < 0) {
                // maxBid đã cạn, không thể counter tiếp
                BidDTOs.AutoBidExhaustedDTO dto = new BidDTOs.AutoBidExhaustedDTO();
                dto.setAuctionId(auctionId);
                dto.setMaxBid(Math.round(entry.getMaxBid()));
                dto.setCurrentPrice(auction.getCurrentPrice());
                dto.setLeadingBidderUsername(leader != null ? leader.getUsername() : "Unknown");

                sessionManager.sendToUser(entry.getUserId(),
                        Packet.of(PacketType.AUTO_BID_EXHAUSTED_NOTIFY, dto));

                registry.cancel(entry.getUserId(), auctionId);

                System.out.printf(
                        "[AUTO-BID PROCESSOR] %s đã cạn maxBid (%.0f) → xóa auto-bid entry.%n",
                        entry.getUserId(), entry.getMaxBid());
            }
        }
    }

    /**
     * FIX Bug #1: Tìm NormalUser theo userId.
     * Ưu tiên in-memory (AuctionManager) trước, fallback xuống DB (UserDAO) nếu không thấy.
     * Trước đây gọi findUserByUsername(null) — dead code không hoạt động.
     *
     * @param userId userId cần tìm
     * @return NormalUser nếu tìm thấy, null nếu không tồn tại
     */
    private NormalUser findNormalUserById(String userId) {
        // Bước 1: tìm trong in-memory trước (nhanh hơn)
        for (User u : AuctionManager.getInstance().getAllUsers()) {
            if (u.getId().equals(userId) && u instanceof NormalUser) {
                return (NormalUser) u;
            }
        }
        // Bước 2: fallback xuống DB — trường hợp user chưa được nạp vào memory
        NormalUser fromDb = userDAO.findNormalUserById(userId);
        if (fromDb != null) {
            // Thêm vào memory cache để lần sau không phải query DB nữa
            AuctionManager.getInstance().addToUserList(fromDb);
        }
        return fromDb;
    }
}
