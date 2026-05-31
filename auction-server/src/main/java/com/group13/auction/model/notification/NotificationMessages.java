package com.group13.auction.model.notification;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Nội dung thông báo inbox chi tiết (tiếng Việt). */
public final class NotificationMessages {

  private static final DateTimeFormatter DEADLINE_FMT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  private NotificationMessages() {}

  public static String formatVnd(long amount) {
    return String.format("%,d VND", amount).replace(',', '.');
  }

  public static String itemName(Auction auction) {
    if (auction == null) {
      return "Phiên đấu giá";
    }
    if (auction.getItem() != null && auction.getItem().getName() != null) {
      return auction.getItem().getName();
    }
    return auction.getId();
  }

  // Auction flow

  public static String outbidTitle() {
    return "Bạn vừa bị vượt giá";
  }

  public static String outbidBody(
      Auction auction, String newBidderName, long newAmount, long yourAmount) {
    return String.format(
        "Phiên \"%s\" (mã %s): %s đặt giá %s, cao hơn mức %s của bạn. "
            + "Hãy đặt giá cao hơn nếu bạn vẫn muốn thắng.",
        itemName(auction),
        auction.getId(),
        newBidderName,
        formatVnd(newAmount),
        formatVnd(yourAmount));
  }

  public static String autoBidExhaustedTitle() {
    return "Auto-bid đã hết hiệu lực";
  }

  public static String autoBidExhaustedBody(
      Auction auction, long maxBid, long currentPrice, String leadingBidderName) {
    return String.format(
        "Phiên \"%s\" (mã %s): giá hiện tại %s đã vượt max bid auto-bid của bạn (%s). Người dẫn"
            + " đầu: %s. Hãy đặt giá thủ công hoặc đăng ký auto-bid mới nếu vẫn muốn tham gia.",
        itemName(auction),
        auction.getId(),
        formatVnd(currentPrice),
        formatVnd(maxBid),
        leadingBidderName);
  }

  public static String auctionEndingSoonTitle(int minutesLeft) {
    return String.format("Phiên sắp kết thúc — còn %d phút", minutesLeft);
  }

  public static String auctionEndingSoonBody(Auction auction, int minutesLeft) {
    String endText = auction.getEndTime() != null ? auction.getEndTime().format(DEADLINE_FMT) : "—";
    return String.format(
        "Phiên \"%s\" (mã %s) sẽ kết thúc sau khoảng %d phút (dự kiến lúc %s). "
            + "Giá hiện tại: %s. Hãy kiểm tra và đặt giá kịp thời nếu cần.",
        itemName(auction),
        auction.getId(),
        minutesLeft,
        endText,
        formatVnd(auction.getCurrentPrice()));
  }

  public static String auctionWonTitle() {
    return "Chúc mừng — bạn thắng phiên đấu giá";
  }

  public static String auctionWonBody(Auction auction, long finalPrice, long depositPaid) {
    return String.format(
        "Bạn là người thắng phiên \"%s\" (mã %s) với giá chốt %s. "
            + "Tiền cọc đã khóa: %s. Vui lòng thanh toán phần còn lại trong vòng 24 giờ "
            + "để hoàn tất giao dịch.",
        itemName(auction), auction.getId(), formatVnd(finalPrice), formatVnd(depositPaid));
  }

  public static String auctionLostTitle() {
    return "Bạn chưa thắng phiên đấu giá";
  }

  public static String auctionLostBody(Auction auction, String winnerName, long finalPrice) {
    return String.format(
        "Phiên \"%s\" (mã %s) đã kết thúc. Người thắng: %s với giá %s. "
            + "Tiền cọc của bạn (nếu có) sẽ được hoàn theo quy định hệ thống.",
        itemName(auction), auction.getId(), winnerName, formatVnd(finalPrice));
  }

  public static String auctionEndedSellerTitle() {
    return "Phiên đấu giá đã kết thúc";
  }

  public static String auctionEndedSellerBody(Auction auction, String winnerName, long finalPrice) {
    return String.format(
        "Phiên \"%s\" (mã %s) đã có người thắng: %s với giá %s. "
            + "Chờ người mua thanh toán trong thời hạn quy định.",
        itemName(auction), auction.getId(), winnerName, formatVnd(finalPrice));
  }

  // Second chance offer

  public static String scoReceivedTitle() {
    return "Bạn nhận Second Chance Offer";
  }

  public static String scoReceivedBody(
      Auction auction, long offerPrice, long depositRequired, LocalDateTime deadline) {
    return String.format(
        "Winner không thanh toán đúng hạn. Bạn (người đặt giá cao thứ hai) được đề nghị mua "
            + "\"%s\" (mã %s) với giá %s. Tiền cọc yêu cầu: %s. "
            + "Hạn chấp nhận/từ chối: %s. Mở mục Second Chance để phản hồi.",
        itemName(auction),
        auction.getId(),
        formatVnd(offerPrice),
        formatVnd(depositRequired),
        deadline.format(DEADLINE_FMT));
  }

  public static String scoSentToSellerTitle() {
    return "Second Chance Offer đã gửi";
  }

  public static String scoSentToSellerBody(
      Auction auction, String runnerUpName, long offerPrice, LocalDateTime deadline) {
    return String.format(
        "Winner không thanh toán. Hệ thống đã gửi đề nghị mua thứ cấp cho %s — sản phẩm \"%s\" (mã"
            + " %s), giá %s, hạn phản hồi %s. Bạn sẽ được thông báo khi họ chấp nhận hoặc từ chối.",
        runnerUpName,
        itemName(auction),
        auction.getId(),
        formatVnd(offerPrice),
        deadline.format(DEADLINE_FMT));
  }

  public static String scoAcceptedSellerTitle() {
    return "Runner-up chấp nhận Second Chance";
  }

  public static String scoAcceptedSellerBody(
      Auction auction, String runnerUpName, long offerPrice) {
    return String.format(
        "%s đã chấp nhận mua \"%s\" (mã %s) với giá %s. "
            + "Họ cần thanh toán trong 24 giờ — bạn sẽ nhận thông báo khi thanh toán hoàn tất.",
        runnerUpName, itemName(auction), auction.getId(), formatVnd(offerPrice));
  }

  public static String scoAcceptedRunnerUpTitle() {
    return "Bạn đã chấp nhận Second Chance";
  }

  public static String scoAcceptedRunnerUpBody(Auction auction, long offerPrice) {
    return String.format(
        "Bạn là người thắng mới của \"%s\" (mã %s) với giá %s. "
            + "Hãy thanh toán trong vòng 24 giờ để tránh mất quyền mua và bị phạt cọc.",
        itemName(auction), auction.getId(), formatVnd(offerPrice));
  }

  public static String scoDeclinedSellerTitle() {
    return "Runner-up từ chối Second Chance";
  }

  public static String scoDeclinedSellerBody(Auction auction, String runnerUpName) {
    return String.format(
        "%s đã từ chối đề nghị mua thứ cấp cho \"%s\" (mã %s). "
            + "Phiên đấu giá đã bị hủy theo quy định.",
        runnerUpName, itemName(auction), auction.getId());
  }

  public static String scoDeclinedRunnerUpTitle() {
    return "Bạn đã từ chối Second Chance";
  }

  public static String scoDeclinedRunnerUpBody(Auction auction) {
    return String.format(
        "Bạn đã từ chối mua \"%s\" (mã %s). Phiên đã đóng — không cần thao tác thêm.",
        itemName(auction), auction.getId());
  }

  public static String scoExpiredTitle() {
    return "Second Chance Offer hết hạn";
  }

  public static String scoExpiredSellerBody(Auction auction, String runnerUpName) {
    return String.format(
        "%s không phản hồi đề nghị mua \"%s\" (mã %s) trong 24 giờ. "
            + "Offer đã hết hạn và phiên bị hủy.",
        runnerUpName, itemName(auction), auction.getId());
  }

  public static String scoExpiredRunnerUpBody(Auction auction, long offerPrice) {
    return String.format(
        "Đề nghị mua \"%s\" (mã %s) với giá %s đã hết hạn vì bạn không phản hồi đúng hạn. "
            + "Phiên đã đóng.",
        itemName(auction), auction.getId(), formatVnd(offerPrice));
  }

  // Payment & order

  public static String paymentSuccessWinnerTitle() {
    return "Thanh toán thành công";
  }

  public static String paymentSuccessWinnerBody(
      Auction auction, long finalPrice, long depositPaid) {
    long remaining = Math.max(0, finalPrice - depositPaid);
    return String.format(
        "Bạn đã thanh toán thành công cho \"%s\" (mã %s). Tổng giá: %s "
            + "(đã dùng cọc %s, thanh toán thêm %s). "
            + "Tiền đang được giữ an toàn — hãy xác nhận khi đã nhận hàng.",
        itemName(auction),
        auction.getId(),
        formatVnd(finalPrice),
        formatVnd(depositPaid),
        formatVnd(remaining));
  }

  public static String paymentSuccessSellerTitle() {
    return "Người mua đã thanh toán";
  }

  public static String paymentSuccessSellerBody(
      Auction auction, String buyerName, long finalPrice) {
    return String.format(
        "%s đã thanh toán đầy đủ cho \"%s\" (mã %s) — số tiền %s. "
            + "Vui lòng giao hàng; tiền sẽ được giải ngân sau khi người mua xác nhận nhận hàng "
            + "(hoặc theo thời hạn hệ thống).",
        buyerName, itemName(auction), auction.getId(), formatVnd(finalPrice));
  }

  public static String paymentFailedTitle() {
    return "Thanh toán thất bại / quá hạn";
  }

  public static String paymentFailedWinnerBody(Auction auction, long forfeitedDeposit) {
    return String.format(
        "Bạn không thanh toán đúng hạn cho \"%s\" (mã %s). "
            + "Cọc %s đã bị tịch thu và điểm uy tín có thể bị trừ. "
            + "Hệ thống có thể mở Second Chance Offer cho người đặt giá cao thứ hai.",
        itemName(auction), auction.getId(), formatVnd(forfeitedDeposit));
  }

  public static String itemReceivedSellerTitle() {
    return "Người mua đã xác nhận nhận hàng";
  }

  public static String itemReceivedSellerBody(Auction auction, String buyerName) {
    return String.format(
        "%s xác nhận đã nhận hàng cho \"%s\" (mã %s). "
            + "Bạn có 3 ngày chờ khiếu nại chất lượng (nếu có) trước khi tiền được giải ngân.",
        buyerName, itemName(auction), auction.getId());
  }

  public static String itemReceivedWinnerTitle() {
    return "Đã xác nhận nhận hàng";
  }

  public static String itemReceivedWinnerBody(Auction auction) {
    return String.format(
        "Bạn đã xác nhận nhận \"%s\" (mã %s). "
            + "Bạn có thể gửi báo cáo chất lượng trong 3 ngày nếu sản phẩm không đúng mô tả.",
        itemName(auction), auction.getId());
  }

  public static String sellerMessageTitle(String sellerName) {
    return "Tin nhắn mới từ người bán";
  }

  public static String sellerMessageBody(
      Auction auction, String sellerName, String messagePreview) {
    String preview =
        messagePreview != null && messagePreview.length() > 200
            ? messagePreview.substring(0, 200) + "…"
            : messagePreview;
    return String.format(
        "%s gửi tin nhắn về phiên \"%s\" (mã %s): \"%s\"",
        sellerName, itemName(auction), auction.getId(), preview);
  }

  public static String formatDeadline(SecondChanceOffer offer) {
    return offer.getDeadline() != null ? offer.getDeadline().format(DEADLINE_FMT) : "—";
  }

  public static String username(NormalUser user) {
    return user != null && user.getUsername() != null ? user.getUsername() : "Người dùng";
  }

  // Leader promoted after previous leader left

  public static String leaderPromotedTitle() {
    return "Bạn đang dẫn đầu phiên đấu giá";
  }

  public static String leaderPromotedBody(
      Auction auction, String previousLeaderName, long currentPrice) {
    return String.format(
        "Người dẫn đầu trước (%s) đã rời phiên \"%s\" (mã %s). "
            + "Bạn đang dẫn đầu với giá %s. Phiên vẫn đang diễn ra — đây chưa phải kết quả thắng/thua.",
        previousLeaderName,
        itemName(auction),
        auction.getId(),
        formatVnd(currentPrice));
  }

  // Leave auction

  public static String leaveAuctionTitle() {
    return "Bạn đã thoát phiên đấu giá";
  }

  public static String leaveAuctionForfeitBody(
      Auction auction, long forfeitedAmount, boolean ratingPenalized) {
    String ratingLine = ratingPenalized ? " Điểm uy tín có thể đã bị trừ theo quy định." : "";
    return String.format(
        "Bạn đã rời phiên \"%s\" (mã %s). Tiền cọc không được hoàn lại: %s.%s",
        itemName(auction), auction.getId(), formatVnd(forfeitedAmount), ratingLine);
  }

  public static String leaveAuctionRefundBody(Auction auction) {
    return String.format(
        "Bạn đã rời phiên \"%s\" (mã %s). Tiền cọc đã được hoàn lại theo quy định.",
        itemName(auction), auction.getId());
  }
}
