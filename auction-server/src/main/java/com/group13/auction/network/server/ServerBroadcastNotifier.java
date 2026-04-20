package com.group13.auction.network.server;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.model.auction.Auction;

import java.time.LocalDateTime;

/**
 * Cầu nối giữa Observer pattern phía server và WebSocket broadcast.
 *
 * <p>Khi {@link com.group13.auction.observer.AuctionObserver} nhận event từ service,
 * nó gọi {@link ServerBroadcastNotifier} để push packet tới đúng client(s).
 *
 * <p>Đây là lớp được inject vào Observer để tránh observer biết về WebSocket trực tiếp.
 *
 * <p>Singleton — toàn server dùng 1 instance.
 */
public class ServerBroadcastNotifier {

    private static final ServerBroadcastNotifier INSTANCE = new ServerBroadcastNotifier();

    private final SessionManager sessionManager = SessionManager.getInstance();

    private ServerBroadcastNotifier() {}

    public static ServerBroadcastNotifier getInstance() { return INSTANCE; }

    // ── Bid events ────────────────────────────────────────────────────────────

    /**
     * Broadcast khi có bid mới hợp lệ.
     * Gửi BID_UPDATE hoặc BID_RESERVE_NOT_MET_UPDATE tới tất cả watcher của phiên.
     * Gửi BID_CHART_POINT_UPDATE kèm theo.
     *
     * @param auction      phiên vừa được bid
     * @param bidAmount    số tiền bid
     * @param bidderUsername username người bid
     * @param isAutoBid    true nếu là auto-bid
     */
    public void notifyBidUpdate(Auction auction, double bidAmount,
                                String bidderUsername, boolean isAutoBid) {
        BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, bidAmount);
        PacketType type = auction.isReserveMet()
                ? PacketType.BID_UPDATE
                : PacketType.BID_RESERVE_NOT_MET_UPDATE;

        sessionManager.broadcastToAuction(auction.getId(), Packet.of(type, update));

        // Chart point
        BidDTOs.BidChartPointDTO chartPoint = DTOMapper.toBidChartPoint(
                auction.getId(), bidAmount, bidderUsername, isAutoBid);
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.BID_CHART_POINT_UPDATE, chartPoint));
    }

    /**
     * Push AUTO_BID_TRIGGERED_NOTIFY tới đúng user có auto-bid.
     *
     * @param userId     userId của người có auto-bid
     * @param auctionId  ID phiên
     * @param bidAmount  số tiền hệ thống vừa bid
     * @param newPrice   giá hiện tại mới
     * @param maxBid     maxBid còn lại
     * @param isLeading  có đang dẫn đầu không
     */
    public void notifyAutoBidTriggered(String userId, String auctionId,
                                       double bidAmount, double newPrice,
                                       double maxBid, boolean isLeading) {
        BidDTOs.AutoBidTriggeredDTO dto = new BidDTOs.AutoBidTriggeredDTO();
        dto.setAuctionId(auctionId);
        dto.setBidAmount(bidAmount);
        dto.setNewCurrentPrice(newPrice);
        dto.setRemainingMaxBid(maxBid - bidAmount);
        dto.setNowLeading(isLeading);
        dto.setTimestamp(LocalDateTime.now());
        sessionManager.sendToUser(userId, Packet.of(PacketType.AUTO_BID_TRIGGERED_NOTIFY, dto));
    }

    /**
     * Push AUTO_BID_EXHAUSTED_NOTIFY khi auto-bid đã cạn kiệt.
     */
    public void notifyAutoBidExhausted(String userId, String auctionId,
                                       double maxBid, double currentPrice,
                                       String leadingUsername) {
        BidDTOs.AutoBidExhaustedDTO dto = new BidDTOs.AutoBidExhaustedDTO();
        dto.setAuctionId(auctionId);
        dto.setMaxBid(maxBid);
        dto.setCurrentPrice(currentPrice);
        dto.setLeadingBidderUsername(leadingUsername);
        sessionManager.sendToUser(userId, Packet.of(PacketType.AUTO_BID_EXHAUSTED_NOTIFY, dto));
    }

    // ── Auction lifecycle ─────────────────────────────────────────────────────

    /** Broadcast AUCTION_STARTED_UPDATE khi phiên bắt đầu (OPEN → RUNNING). */
    public void notifyAuctionStarted(Auction auction) {
        AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, null);
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.AUCTION_STARTED_UPDATE, update));
    }

    /** Broadcast AUCTION_ENDED_UPDATE khi phiên kết thúc có winner. */
    public void notifyAuctionEnded(Auction auction) {
        AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, null);
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.AUCTION_ENDED_UPDATE, update));
    }

    /** Broadcast AUCTION_NO_WINNER_UPDATE khi không có ai bid. */
    public void notifyAuctionNoWinner(Auction auction) {
        AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, "NO_WINNER");
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.AUCTION_NO_WINNER_UPDATE, update));
    }

    /** Broadcast AUCTION_RESERVE_NOT_MET_UPDATE khi giá chưa đạt reserve. */
    public void notifyAuctionReserveNotMet(Auction auction) {
        AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, "RESERVE_NOT_MET");
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.AUCTION_RESERVE_NOT_MET_UPDATE, update));
    }

    /** Broadcast AUCTION_CANCELED_UPDATE khi phiên bị hủy. */
    public void notifyAuctionCanceled(Auction auction, String reason) {
        AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, reason);
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.AUCTION_CANCELED_UPDATE, update));
    }

    /**
     * Broadcast AUCTION_EXTENDED_NOTIFY khi anti-sniping kéo dài phiên.
     *
     * @param auction           phiên được gia hạn
     * @param newEndTime        thời điểm kết thúc mới
     * @param extendedBySeconds số giây gia hạn thêm
     */
    public void notifyAuctionExtended(Auction auction,
                                      LocalDateTime newEndTime, int extendedBySeconds) {
        AuctionDTOs.AuctionExtendedDTO dto = new AuctionDTOs.AuctionExtendedDTO();
        dto.setAuctionId(auction.getId());
        dto.setNewEndTime(newEndTime);
        dto.setExtendedBySeconds(extendedBySeconds);
        sessionManager.broadcastToAuction(auction.getId(),
                Packet.of(PacketType.AUCTION_EXTENDED_NOTIFY, dto));
    }

    /**
     * Push AUCTION_UPCOMING_END_NOTIFY khi sắp hết giờ.
     *
     * @param auctionId        ID phiên
     * @param remainingSeconds giây còn lại
     */
    public void notifyAuctionUpcomingEnd(String auctionId, long remainingSeconds) {
        AuctionDTOs.AuctionUpcomingEndDTO dto = new AuctionDTOs.AuctionUpcomingEndDTO();
        dto.setAuctionId(auctionId);
        dto.setRemainingSeconds(remainingSeconds);
        sessionManager.broadcastToAuction(auctionId,
                Packet.of(PacketType.AUCTION_UPCOMING_END_NOTIFY, dto));
    }

    // ── Payment ───────────────────────────────────────────────────────────────

    /** Push DEPOSIT_REFUND_NOTIFY cho bidder thua khi phiên kết thúc. */
    public void notifyDepositRefund(String userId, String auctionId,
                                    double refundAmount, double newBalance) {
        PaymentDTOs.DepositRefundDTO dto = new PaymentDTOs.DepositRefundDTO();
        dto.setAuctionId(auctionId);
        dto.setRefundAmount(refundAmount);
        dto.setNewBalance(newBalance);
        sessionManager.sendToUser(userId, Packet.of(PacketType.DEPOSIT_REFUND_NOTIFY, dto));
    }

    /** Push DEPOSIT_FORFEITED_NOTIFY cho winner không trả tiền. */
    public void notifyDepositForfeited(String userId, String auctionId,
                                       double forfeitedAmount, double newBalance) {
        PaymentDTOs.DepositForfeitedDTO dto = new PaymentDTOs.DepositForfeitedDTO();
        dto.setAuctionId(auctionId);
        dto.setForfeitedAmount(forfeitedAmount);
        dto.setNewBalance(newBalance);
        sessionManager.sendToUser(userId, Packet.of(PacketType.DEPOSIT_FORFEITED_NOTIFY, dto));
    }

    /** Push SECOND_CHANCE_OFFER_NOTIFY cho runner-up. */
    public void notifySecondChanceOffer(String runnerUpUserId,
                                        PaymentDTOs.SecondChanceOfferDTO offer) {
        sessionManager.sendToUser(runnerUpUserId,
                Packet.of(PacketType.SECOND_CHANCE_OFFER_NOTIFY, offer));
    }

    /** Push PAYMENT_COMPLETED_NOTIFY cho Seller. */
    public void notifyPaymentCompleted(String sellerId,
                                       PaymentDTOs.PaymentResultDTO result) {
        sessionManager.sendToUser(sellerId,
                Packet.of(PacketType.PAYMENT_COMPLETED_NOTIFY, result));
    }

    // ── Account ───────────────────────────────────────────────────────────────

    /** Push ACCOUNT_SUSPENDED_NOTIFY tới user bị suspend. */
    public void notifyAccountSuspended(String userId, double currentRating,
                                       double threshold, String reason) {
        RatingDTOs.AccountSuspendedDTO dto = new RatingDTOs.AccountSuspendedDTO();
        dto.setCurrentRating(currentRating);
        dto.setThreshold(threshold);
        dto.setReason(reason);
        sessionManager.sendToUser(userId, Packet.of(PacketType.ACCOUNT_SUSPENDED_NOTIFY, dto));
    }

    /** Push ACCOUNT_RESTORED_NOTIFY tới user được phục hồi sau 3 tháng. */
    public void notifyAccountRestored(String userId, double newRating, String newStatus) {
        RatingDTOs.AccountRestoredDTO dto = new RatingDTOs.AccountRestoredDTO();
        dto.setNewRating(newRating);
        dto.setNewStatus(newStatus);
        sessionManager.sendToUser(userId, Packet.of(PacketType.ACCOUNT_RESTORED_NOTIFY, dto));
    }
}