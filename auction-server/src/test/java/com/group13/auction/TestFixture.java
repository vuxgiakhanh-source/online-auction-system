package com.group13.auction;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.item.Electronics;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.item.Vehicle;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.NormalUserFactory;
import com.group13.auction.model.user.User;
import com.group13.auction.service.iservice.IRatingService;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Các helper/fixture dùng chung cho toàn bộ unit test OOP.
 *
 * <p>Cung cấp:
 * <ul>
 *   <li>Fake {@link IRatingService} cho phép kiểm soát kết quả trả về.</li>
 *   <li>Builder nhanh để tạo {@link NormalUser} giả không cần DB.</li>
 *   <li>Builder nhanh để tạo {@link Auction} và {@link Item} con.</li>
 *   <li>Builder nhanh để tạo {@link AuctionWinner} với các trạng thái khác nhau.</li>
 *   <li>Builder nhanh để tạo {@link SecondChanceOffer} với các trạng thái khác nhau.</li>
 *   <li>Builder nhanh để tạo {@link QualityReport} với các trạng thái khác nhau.</li>
 *   <li>Utility reset {@link SystemBank} balance cho test isolation.</li>
 *   <li>Factory helpers không cần DB.</li>
 * </ul>
 *
 * <p>Tất cả đều không chạm DB, không cần network.
 */
public final class TestFixture {

    private TestFixture() {}

    // =========================================================================
    // Fake IRatingService
    // =========================================================================

    /**
     * Fake IRatingService: mọi Seller đều được phép tạo phiên đấu giá.
     * Dùng cho test luồng bình thường (happy path).
     */
    public static IRatingService ratingServiceAllowAll() {
        return new IRatingService() {
            @Override public boolean isEligible(User user) { return true; }
            @Override public boolean canSellerCreateAuction(User seller) { return true; }
            @Override public void rewardBidder(NormalUser bidder) {}
            @Override public void rewardSeller(User seller) {}
            @Override public void penalizeLatePayment(NormalUser bidder) {}
            @Override public void penalizeSeller(User seller) {}
            @Override public void checkAndRestoreSuspended(User user) {}
        };
    }

    /**
     * Fake IRatingService: mọi Seller đều bị từ chối tạo phiên đấu giá.
     * Dùng để test trường hợp Seller bị khoá.
     */
    public static IRatingService ratingServiceDenyAll() {
        return new IRatingService() {
            @Override public boolean isEligible(User user) { return false; }
            @Override public boolean canSellerCreateAuction(User seller) { return false; }
            @Override public void rewardBidder(NormalUser bidder) {}
            @Override public void rewardSeller(User seller) {}
            @Override public void penalizeLatePayment(NormalUser bidder) {}
            @Override public void penalizeSeller(User seller) {}
            @Override public void checkAndRestoreSuspended(User user) {}
        };
    }

    // =========================================================================
    // NormalUser builder (không cần DB)
    // =========================================================================

    /**
     * Tạo NormalUser với role BIDDER mặc định, balance = 0, rating = 3.0.
     * username phải >= 8 ký tự.
     */
    public static NormalUser normalBidder(String username) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0,
                0L,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                false,
                false,
                null);
    }

    /**
     * Tạo NormalUser có đầy đủ cả role BIDDER lẫn SELLER.
     * Có sẵn balance 10_000_000 để tham gia đấu giá.
     */
    public static NormalUser normalSeller(String username) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0,
                10_000_000L,
                0L,
                EnumSet.of(User.UserRole.BIDDER, User.UserRole.SELLER),
                false,
                false,
                null);
    }

    /**
     * Tạo NormalUser với balance tùy chỉnh và role BIDDER.
     */
    public static NormalUser bidderWithBalance(String username, long balance) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0,
                balance,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                false,
                false,
                null);
    }

    /**
     * Tạo NormalUser với rating tùy chỉnh và role BIDDER.
     * Dùng khi test các ngưỡng rating (suspend/ban/eligible).
     */
    public static NormalUser bidderWithRating(String username, double rating) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                rating,
                1_000_000L,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                false,
                false,
                null);
    }

    /**
     * Tạo NormalUser với trạng thái BANNED.
     */
    public static NormalUser bannedBidder(String username) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.BANNED,
                3.0,
                0L,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                false,
                false,
                null);
    }

    /**
     * Tạo NormalUser với trạng thái SUSPENDED và suspendedAt = now.
     */
    public static NormalUser suspendedBidder(String username) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.SUSPENDED,
                1.5,
                0L,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                true,
                false,
                LocalDateTime.now());
    }

    /**
     * Tạo NormalUser đã từng bị penalize (hasEverBeenPenalized = true).
     * Dùng để test điều kiện tự động duyệt role Seller.
     */
    public static NormalUser penalizedBidder(String username) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0,
                1_000_000L,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                true,   // hasEverBeenPenalized
                false,
                null);
    }

    // =========================================================================
    // Item builder (bypass ItemFactory, không cần IRatingService)
    // =========================================================================

    /**
     * Tạo Art trực tiếp qua reconstitute — dùng khi chỉ cần Item đơn giản.
     */
    public static Art art(String name, long startingPrice, NormalUser seller) {
        return Art.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                name,
                "Mô tả " + name,
                startingPrice,
                seller,
                "Nghệ sĩ Test",
                2020,
                "Sơn dầu");
    }

    /**
     * Tạo Electronics trực tiếp qua reconstitute.
     */
    public static Electronics electronics(String name, long startingPrice, NormalUser seller) {
        return Electronics.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                name,
                "Mô tả " + name,
                startingPrice,
                seller,
                "Samsung",
                12,
                "Mới 100%");
    }

    /**
     * Tạo Vehicle trực tiếp qua reconstitute.
     */
    public static Vehicle vehicle(String name, long startingPrice, NormalUser seller) {
        return Vehicle.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                name,
                "Mô tả " + name,
                startingPrice,
                seller,
                "Toyota",
                2019,
                50000.0);
    }

    // =========================================================================
    // Auction builder
    // =========================================================================

    /**
     * Tạo Auction OPEN với Art item, reserve = startingPrice * 2.
     * startTime = 1 phút trước, endTime = 1 giờ sau.
     */
    public static Auction openAuction(NormalUser seller, long startingPrice) {
        Item item = art("Tranh Test", startingPrice, seller);
        return Auction.create(
                item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                startingPrice * 2);
    }

    /**
     * Tạo Auction RUNNING (OPEN → RUNNING).
     */
    public static Auction runningAuction(NormalUser seller, long startingPrice) {
        Auction auction = openAuction(seller, startingPrice);
        auction.transitionToRunning();
        return auction;
    }

    /**
     * Tạo Auction FINISHED: có winner và reserve đã đạt.
     * currentPrice = winningPrice, currentLeader = winner.
     */
    public static Auction finishedAuction(NormalUser seller, NormalUser winner,
                                          long startingPrice, long winningPrice) {
        Auction auction = runningAuction(seller, startingPrice);
        auction.updateBid(winningPrice, winner);
        auction.transitionToClose(true);   // → FINISHED
        return auction;
    }

    /**
     * Tạo Auction CANCELED từ OPEN (hủy trước khi bắt đầu).
     */
    public static Auction canceledFromOpenAuction(NormalUser seller, long startingPrice) {
        Auction auction = openAuction(seller, startingPrice);
        auction.transitionToCancel();       // → CANCELED
        return auction;
    }

    /**
     * Tạo Auction CANCELED từ RUNNING (không có winner).
     */
    public static Auction canceledFromRunningAuction(NormalUser seller, long startingPrice) {
        Auction auction = runningAuction(seller, startingPrice);
        auction.transitionToClose(false);   // → CANCELED
        return auction;
    }

    /**
     * Tạo Auction ở bất kỳ trạng thái nào qua reconstitute.
     * Dùng khi cần kiểm soát chính xác currentPrice và status.
     */
    public static Auction auctionWithStatus(NormalUser seller, long startingPrice,
                                            long currentPrice, Auction.AuctionStatus status) {
        Item item = art("Tranh Test", startingPrice, seller);
        return Auction.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                currentPrice,
                status,
                startingPrice * 2);
    }

    // =========================================================================
    // AuctionWinner builder
    // =========================================================================

    /**
     * Tạo AuctionWinner PENDING với hạn thanh toán trong tương lai (24h từ now).
     * Dùng cho happy path: winner chưa thanh toán, chưa hết hạn.
     */
    public static AuctionWinner pendingWinner(NormalUser winner, String auctionId,
                                              long finalPrice, long depositPaid) {
        return AuctionWinner.create(winner, auctionId, finalPrice, depositPaid, false);
    }

    /**
     * Tạo AuctionWinner PENDING đã quá hạn thanh toán (paymentDeadline đã qua).
     * Dùng để test {@code isExpired() == true}.
     */
    public static AuctionWinner expiredPendingWinner(NormalUser winner, String auctionId,
                                                     long finalPrice, long depositPaid) {
        return AuctionWinner.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                winner,
                auctionId,
                finalPrice,
                depositPaid,
                LocalDateTime.now().minusHours(1),  // paymentDeadline đã qua
                null,
                null,
                AuctionWinner.PaymentStatus.PENDING,
                false);
    }

    /**
     * Tạo AuctionWinner FUNDS_HELD với confirmReceiptDeadline còn trong tương lai.
     * Dùng để test luồng: thanh toán xong, chờ winner xác nhận nhận hàng.
     */
    public static AuctionWinner fundsHeldWinner(NormalUser winner, String auctionId,
                                                long finalPrice, long depositPaid) {
        return AuctionWinner.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                winner,
                auctionId,
                finalPrice,
                depositPaid,
                LocalDateTime.now().plusDays(30),   // paymentDeadline không còn liên quan
                LocalDateTime.now().plusDays(7),    // confirmReceiptDeadline chưa qua
                null,
                AuctionWinner.PaymentStatus.FUNDS_HELD,
                false);
    }

    /**
     * Tạo AuctionWinner FUNDS_HELD đã quá hạn confirmReceipt.
     * Dùng để test {@code isConfirmReceiptOverdue() == true}.
     */
    public static AuctionWinner overdueConfirmReceiptWinner(NormalUser winner, String auctionId,
                                                            long finalPrice, long depositPaid) {
        return AuctionWinner.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                winner,
                auctionId,
                finalPrice,
                depositPaid,
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().minusHours(1),  // confirmReceiptDeadline đã qua
                null,
                AuctionWinner.PaymentStatus.FUNDS_HELD,
                false);
    }

    /**
     * Tạo AuctionWinner COMPLETED (đã hoàn tất thanh toán).
     * Dùng để test các điều kiện terminal state.
     */
    public static AuctionWinner completedWinner(NormalUser winner, String auctionId,
                                                long finalPrice, long depositPaid) {
        return AuctionWinner.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                winner,
                auctionId,
                finalPrice,
                depositPaid,
                LocalDateTime.now().plusDays(30),
                null,
                null,
                AuctionWinner.PaymentStatus.COMPLETED,
                false);
    }

    /**
     * Tạo AuctionWinner là second-chance offer (isSecondOffer = true).
     */
    public static AuctionWinner secondOfferWinner(NormalUser runnerUp, String auctionId,
                                                  long offerPrice, long depositPaid) {
        return AuctionWinner.create(runnerUp, auctionId, offerPrice, depositPaid, true);
    }

    // =========================================================================
    // SecondChanceOffer builder
    // =========================================================================

    /**
     * Tạo SecondChanceOffer PENDING với deadline trong tương lai (24h từ now).
     * Dùng cho happy path: offer mới tạo, runner-up chưa quyết định.
     */
    public static SecondChanceOffer pendingOffer(NormalUser runnerUp, String auctionId,
                                                 long offerPrice, long depositPaid) {
        return SecondChanceOffer.create(runnerUp, auctionId, offerPrice, depositPaid);
    }

    /**
     * Tạo SecondChanceOffer PENDING đã hết hạn (deadline đã qua).
     * Dùng để test {@code isExpired() == true}.
     */
    public static SecondChanceOffer expiredPendingOffer(NormalUser runnerUp, String auctionId,
                                                        long offerPrice, long depositPaid) {
        return SecondChanceOffer.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                runnerUp,
                auctionId,
                offerPrice,
                depositPaid,
                LocalDateTime.now().minusHours(1),  // deadline đã qua
                SecondChanceOffer.OfferStatus.PENDING);
    }

    /**
     * Tạo SecondChanceOffer đã ACCEPTED.
     * Dùng để test transition invalid hoặc idempotent behavior.
     */
    public static SecondChanceOffer acceptedOffer(NormalUser runnerUp, String auctionId,
                                                  long offerPrice, long depositPaid) {
        return SecondChanceOffer.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                runnerUp,
                auctionId,
                offerPrice,
                depositPaid,
                LocalDateTime.now().plusHours(24),
                SecondChanceOffer.OfferStatus.ACCEPTED);
    }

    /**
     * Tạo SecondChanceOffer đã DECLINED.
     */
    public static SecondChanceOffer declinedOffer(NormalUser runnerUp, String auctionId,
                                                  long offerPrice, long depositPaid) {
        return SecondChanceOffer.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                runnerUp,
                auctionId,
                offerPrice,
                depositPaid,
                LocalDateTime.now().plusHours(24),
                SecondChanceOffer.OfferStatus.DECLINED);
    }

    // =========================================================================
    // QualityReport builder
    // =========================================================================

    /**
     * Tạo QualityReport PENDING với 1 ảnh minh chứng.
     * Dùng cho happy path: winner vừa gửi báo cáo, chờ admin xét duyệt.
     */
    public static QualityReport pendingReport(NormalUser reporter, String auctionId) {
        return QualityReport.create(
                reporter,
                auctionId,
                "Hàng không đúng mô tả",
                List.of("http://evidence1.jpg"));
    }

    /**
     * Tạo QualityReport PENDING với nhiều ảnh minh chứng.
     */
    public static QualityReport pendingReportWithImages(NormalUser reporter, String auctionId,
                                                        List<String> imageUrls) {
        return QualityReport.create(reporter, auctionId, "Hàng không đúng mô tả", imageUrls);
    }

    /**
     * Tạo QualityReport đã APPROVED với sellerRefundDeadline còn trong tương lai (24h).
     * Dùng để test {@code isSellerRefundOverdue() == false}.
     */
    public static QualityReport approvedReport(NormalUser reporter, String auctionId) {
        return QualityReport.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                reporter,
                auctionId,
                "Hàng không đúng mô tả",
                List.of("http://evidence1.jpg"),
                QualityReport.ReportStatus.APPROVED,
                LocalDateTime.now().plusHours(24),  // deadline chưa qua
                false);
    }

    /**
     * Tạo QualityReport APPROVED đã quá hạn refund (sellerRefundDeadline đã qua).
     * Dùng để test {@code isSellerRefundOverdue() == true}.
     */
    public static QualityReport overdueSellerRefundReport(NormalUser reporter, String auctionId) {
        return QualityReport.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                reporter,
                auctionId,
                "Hàng không đúng mô tả",
                List.of("http://evidence1.jpg"),
                QualityReport.ReportStatus.APPROVED,
                LocalDateTime.now().minusHours(1),  // sellerRefundDeadline đã qua
                false);
    }

    /**
     * Tạo QualityReport APPROVED đã quá hạn nhưng refund đã hoàn tất.
     * Dùng để test: overdue + refundCompleted → không trigger ban seller.
     */
    public static QualityReport overdueButRefundCompletedReport(NormalUser reporter,
                                                                String auctionId) {
        return QualityReport.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                reporter,
                auctionId,
                "Hàng không đúng mô tả",
                List.of("http://evidence1.jpg"),
                QualityReport.ReportStatus.APPROVED,
                LocalDateTime.now().minusHours(1),
                true);   // refundCompleted = true
    }

    /**
     * Tạo QualityReport đã REJECTED.
     */
    public static QualityReport rejectedReport(NormalUser reporter, String auctionId) {
        return QualityReport.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                reporter,
                auctionId,
                "Hàng không đúng mô tả",
                List.of("http://evidence1.jpg"),
                QualityReport.ReportStatus.REJECTED,
                null,
                false);
    }

    // =========================================================================
    // SystemBank utility
    // =========================================================================

    /**
     * Reset totalBalance của SystemBank về 0 qua reflection.
     *
     * <p>Bắt buộc gọi trong {@code @BeforeEach} của mọi test class dùng SystemBank,
     * để tránh state rò rỉ giữa các test (Singleton isolation).
     *
     * @throws Exception nếu reflection thất bại
     */
    public static void resetSystemBankBalance() throws Exception {
        Field field = SystemBank.class.getDeclaredField("totalBalance");
        field.setAccessible(true);
        AtomicLong balance = (AtomicLong) field.get(SystemBank.getInstance());
        balance.set(0L);
    }

    // =========================================================================
    // Factory helpers (không cần DB)
    // =========================================================================

    /**
     * Tạo NormalUserFactory không có UserDAO.
     * Khi userDAO = null: bỏ qua kiểm tra unique username/email với DB.
     * Dùng để test validation logic của factory trong môi trường unit test.
     */
    public static NormalUserFactory normalUserFactory() {
        return new NormalUserFactory();  // UserFactory() no-arg → userDAO = null
    }
}