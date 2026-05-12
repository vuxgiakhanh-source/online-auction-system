package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.INotifier;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.notification.ConsoleNotifier;

import java.util.Objects;

/**
 * Observer dành cho Bidder - nhận notify về bid và kết quả phiên.
 */
public class BidderObserver implements AuctionObserver {
  private final NormalUser bidder;
  private final IRatingService ratingService;
  private INotifier notifier = new ConsoleNotifier();

  public BidderObserver(NormalUser bidder, IRatingService ratingService) {
    this.bidder = bidder;
    this.ratingService = ratingService;
  }

  public void setNotifier(INotifier notifier) {
    this.notifier = Objects.requireNonNull(notifier, "notifier must not be null");
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    if (event.getEventType() == AuctionEvent.AuctionEventType.BID_PLACED) {
      String msg = String.format("Bid mới: %s đặt %d | Phiên: %s",
              event.getBidder() != null ? event.getBidder().getUsername() : "?",
              event.getBidAmount(), event.getAuction().getId());
      notifier.notify(bidder.getUsername(), "BID_PLACED", msg);
    } else if (event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
      String msg = String.format("Bid %d chưa đạt reserve price.", event.getBidAmount());
      notifier.notify(bidder.getUsername(), "BID_RESERVE_NOT_MET", msg);
    }
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        notifier.notify(bidder.getUsername(), "AUCTION_STARTED", "Phiên đã bắt đầu!");
        break;
      case AUCTION_EXTENDED:
        String msgExt = String.format("Phiên %s được gia hạn. EndTime mới: %s. %s",
                event.getAuction().getId(), event.getAuction().getEndTime(),
                event.getMessage() != null ? event.getMessage() : "");
        notifier.notify(bidder.getUsername(), "AUCTION_EXTENDED", msgExt);
        break;
      case AUCTION_UPCOMING:
        notifier.notify(bidder.getUsername(), "AUCTION_UPCOMING", "Phiên sắp bắt đầu - chuẩn bị sẵn sàng.");
        break;
      case AUCTION_ENDED:
        if (event.getBidder() != null && event.getBidder().getUsername().equals(bidder.getUsername())) {
          notifier.notify(bidder.getUsername(), "AUCTION_ENDED_WIN",
                  String.format("Chúc mừng! Bạn thắng phiên với giá %d. Hãy thanh toán trong 24h.",
                          event.getBidAmount()));
        } else {
          notifier.notify(bidder.getUsername(), "AUCTION_ENDED",
                  String.format("Phiên kết thúc. Winner: %s.",
                          event.getBidder() != null ? event.getBidder().getUsername() : "Không có"));
        }
        break;
      case AUCTION_NO_WINNER:
        notifier.notify(bidder.getUsername(), "AUCTION_NO_WINNER",
                "Phiên kết thúc không có ai đặt giá. Cọc sẽ được hoàn trả.");
        break;
      case RESERVE_NOT_MET_CLOSED:
        notifier.notify(bidder.getUsername(), "RESERVE_NOT_MET_CLOSED",
                String.format("Phiên kết thúc - giá cao nhất %d chưa đạt reserve. Cọc sẽ được hoàn trả.",
                        event.getBidAmount()));
        break;
      case AUCTION_CANCELED:
        notifier.notify(bidder.getUsername(), "AUCTION_CANCELED",
                "Phiên đấu giá đã bị hủy. Cọc sẽ được hoàn trả.");
        break;
      default:
        break;
    }
  }
}
