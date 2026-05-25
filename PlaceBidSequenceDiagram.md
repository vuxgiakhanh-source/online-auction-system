# Place Bid Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Bidder
    participant WS as AuctionWebSocketServer
    participant Router as PacketRouter
    participant Handler as BidHandler
    participant Rate as BidRateLimiter
    participant Manager as AuctionManager
    participant BidSvc as BidService
    participant Rating as RatingService
    participant Wallet as WalletService
    participant Lock as AuctionLockRegistry
    participant Strategy as StandardBidStrategy
    participant Auction
    participant BidDAO as BidTransactionDAO
    participant AuctionDAO
    participant Notify as AuctionService
    participant Broadcast as ServerBroadcastNotifier
    participant Sessions as SessionManager
    participant AutoBid as AutoBidProcessor

    Bidder->>WS: PLACE_BID {auctionId, amount}
    WS->>Router: route(raw packet)
    Router->>Handler: handle(PLACE_BID)
    Handler->>Rate: tryConsume(userId)
    Handler->>Manager: findAuctionById(auctionId)

    alt invalid payload or rate limited
        Handler-->>Bidder: PLACE_BID_FAILED
    else accepted for service processing
        Handler->>BidSvc: placeBid(bidder, auction, amount, StandardBidStrategy)
        BidSvc->>Rating: isEligible(bidder)
        BidSvc->>Auction: isAcceptingBids() and hasJoined()

        alt manipulative bid
            BidSvc->>Wallet: forfeitDeposit(...)
        end

        %% Đưa activate/deactivate ra ngoài cấu trúc rẽ nhánh của isValidBid để tránh xung đột lifecycle
        BidSvc->>Lock: getLock(auctionId)
        activate Lock
        BidSvc->>Strategy: isValidBid(auction, amount)
        
        alt invalid or auction closed
            BidSvc-->>Handler: throw business exception
            Handler-->>Bidder: PLACE_BID_FAILED
        else valid bid
            BidSvc->>Auction: updateBid(amount, bidder)
            opt bid in anti-sniping window
                BidSvc->>Auction: extendEndTime(60s)
            end
            BidSvc->>Auction: addBidTransactionId(tx.id)
        end
        
        deactivate Lock  %% Đóng Lock ở cuối quy trình đồng cấp với lệnh activate
        
        %% Các logic hậu xử lý sau khi đã nhả Lock
        alt valid bid
            BidSvc->>BidDAO: saveTransactionAndUpdatePrice(tx, auctionId, amount, bidderId)
            BidSvc->>Notify: notify(BID_PLACED or BID_RESERVE_NOT_MET)
            Notify->>Broadcast: notifyJoinedParticipantsForEvent(event)
            opt previous leader exists
                BidSvc->>Broadcast: notifyOutbid(previousLeader, auction, bidder, amount)
            end
            opt auction extended
                BidSvc->>AuctionDAO: updateEndTime(auctionId, newEndTime)
                BidSvc->>Notify: notify(AUCTION_EXTENDED)
            end

            Handler-->>Bidder: PLACE_BID_SUCCESS
            Handler->>Sessions: broadcastToAuctionAsync(BID_UPDATE)
            Handler->>Sessions: broadcastToAuctionAsync(BID_CHART_POINT_UPDATE)
            opt auction extended
                Handler->>Sessions: broadcastToAuctionAsync(AUCTION_EXTENDED_NOTIFY)
            end
            Handler->>AutoBid: submit(auction, bidderId)
        end
    end
```
