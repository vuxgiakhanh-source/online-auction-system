package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.INotifier;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.notification.ConsoleNotifier;
import java.util.Objects;

/** Observer dành cho Seller - nhận notify về phiên đấu giá của mình. */
public class SellerObserver implements AuctionObserver {
  private final NormalUser seller;
  private final IRatingService ratingService;
  private INotifier notifier = new ConsoleNotifier();

  public SellerObserver(NormalUser seller, IRatingService ratingService) {
    this.seller = seller;
    this.ratingService = ratingService;
  }

  public void setNotifier(INotifier notifier) {
    this.notifier = Objects.requireNonNull(notifier, "notifier must not be null");
  }

  public NormalUser getSeller() {
    return seller;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    switch (event.getEventType()) {
      case BID_PLACED:
        notifier.notify(
            seller.getUsername(),
            "BID_PLACED",
            String.format(
                "Bid mới: %d | Phiên: %s", event.getBidAmount(), event.getAuction().getId()));
        break;
      case BID_RESERVE_NOT_MET:
        notifier.notify(
            seller.getUsername(),
            "BID_RESERVE_NOT_MET",
            String.format(
                "Bid %d chưa đạt reserve price (%d).",
                event.getBidAmount(), event.getAuction().getReservePrice()));
        break;
      default:
        break;
    }
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        notifier.notify(
            seller.getUsername(), "AUCTION_STARTED", "Phiên đấu giá của bạn đã bắt đầu!");
        break;
      case AUCTION_UPCOMING:
        break;
      case AUCTION_ENDED:
        if (event.getBidder() != null) {
          notifier.notify(
              seller.getUsername(),
              "AUCTION_ENDED",
              String.format(
                  "Phiên kết thúc. Winner: %s | Giá: %d. Chờ thanh toán.",
                  event.getBidder().getUsername(), event.getBidAmount()));
        }
        break;
      case AUCTION_NO_WINNER:
        notifier.notify(
            seller.getUsername(),
            "AUCTION_NO_WINNER",
            "Phiên kết thúc không có ai đặt giá. Phiên đã bị hủy.");
        break;
      case RESERVE_NOT_MET_CLOSED:
        notifier.notify(
            seller.getUsername(),
            "RESERVE_NOT_MET_CLOSED",
            String.format(
                "Phiên '%s' kết thúc với mức giá cao nhất là %d nhưng chưa đạt mức giá tối thiểu"
                    + " của bạn. Phiên đã bị hủy.",
                event.getAuction().getItem().getName(), event.getBidAmount()));
        break;
      case PAYMENT_COMPLETED:
        notifier.notify(
            seller.getUsername(),
            "PAYMENT_COMPLETED",
            String.format(
                "Sản phẩm '%s' đã được bán thành công với giá %d! Tiền đã được chuyển vào tài khoản"
                    + " (sau thuế).",
                event.getAuction().getItem().getName(), event.getBidAmount()));
        ratingService.rewardSeller(seller);
        break;
      case AUCTION_CANCELED:
        notifier.notify(seller.getUsername(), "AUCTION_CANCELED", "Phiên đấu giá đã bị hủy.");
        break;
      case SECOND_CHANCE_OFFERED:
        // Inbox seller đã được gửi qua ServerBroadcastNotifier.notifySecondChanceOffered — tránh
        // trùng.
        break;
      case SELLER_CANCEL_REQUEST_ACCEPTED:
        notifier.notify(
            seller.getUsername(),
            "SELLER_CANCEL_REQUEST_ACCEPTED",
            "Yêu cầu hủy phiên của bạn đã được chấp thuận.");
        break;
      default:
        break;
    }
  }
}
