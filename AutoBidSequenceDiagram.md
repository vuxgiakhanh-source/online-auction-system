# Auto-Bid Engine Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Bidder
    participant Handler as BidHandler
    participant Registry as AutoBidRegistry
    participant AutoBidDAO
    participant BidSvc as BidService
    participant Processor as AutoBidProcessor
    participant Executor as PerAuctionExecutor
    participant Auction
    participant Manager as AuctionManager
    participant Sessions as SessionManager
    participant UserDAO

    Bidder->>Handler: REGISTER_AUTO_BID {auctionId, maxBid}
    Handler->>Registry: register(userId, auctionId, maxBid)
    Registry->>AutoBidDAO: upsert(userId, auctionId, maxBid)

    alt bidder is already leader
        Handler-->>Bidder: REGISTER_AUTO_BID_SUCCESS
    else initial auto bid possible
        Handler->>BidSvc: placeBid(bidder, auction, nextBid, AutoBidStrategy)
        Handler->>Sessions: broadcastToAuction(BID_UPDATE)
        Handler-->>Bidder: REGISTER_AUTO_BID_SUCCESS
    end

    Handler->>Processor: submit(auction, userId)
    Processor->>Executor: enqueue or mark recheck

    loop active auto bids
        Executor->>Processor: runChain(auction)
        Processor->>Registry: getEntriesForAuction(auctionId)
        Processor->>Auction: currentLeader and currentPrice
        Processor->>Processor: detectPhase() and buildCandidates()

        alt no online candidate or auction closed
            Processor-->>Executor: stop chain
        else candidate selected
            Processor->>Manager: resolve user from memory
            alt user not in memory
                Processor->>UserDAO: findNormalUserById(userId)
                Processor->>Manager: addToUserList(user)
            end
            Processor->>BidSvc: placeBid(autoBidder, auction, smartNextBid, AutoBidStrategy)
            Processor->>Sessions: broadcastToAuctionAsync(BID_UPDATE)
            Processor->>Sessions: broadcastToAuctionAsync(BID_CHART_POINT_UPDATE)
        end
    end

    loop exhausted auto bids
        Processor->>Sessions: sendToUser(AUTO_BID_EXHAUSTED_NOTIFY)
        Processor->>Registry: cancel(userId, auctionId)
        Registry->>AutoBidDAO: delete(userId, auctionId)
    end
```
