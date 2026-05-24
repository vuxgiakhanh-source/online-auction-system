# Auto-Bid Engine Sequence Diagram

```mermaid
sequenceDiagram
    participant Bidder as Client (Bidder)
    participant BidHandler
    participant AutoBidRegistry
    participant AutoBidDAO
    participant AutoBidProcessor
    participant AuctionManager
    participant BidService
    participant AuctionLockRegistry
    participant Auction
    participant BidTransactionDAO
    participant SessionManager as SessionManager (broadcast)

    Bidder->>BidHandler: REGISTER_AUTO_BID {auctionId, maxBid}
    BidHandler->>AutoBidRegistry: register(userId, auctionId, maxBid)
    AutoBidRegistry->>AutoBidDAO: upsert(userId, auctionId, maxBid)
    AutoBidDAO-->>AutoBidRegistry: OK
    AutoBidRegistry-->>BidHandler: registered
    BidHandler-->>Bidder: AUTO_BID_REGISTERED

    Note over BidHandler,AutoBidProcessor: Khi có bid mới (placeBid thành công)

    BidHandler->>AutoBidProcessor: submit(auction, triggeredByUserId) [non-blocking ~1µs]

    Note over AutoBidProcessor: Task coalescing:<br/>chainRunning=false → submit task<br/>chainRunning=true → set needsRecheck=true

    Note over AutoBidProcessor: getOrCreateExecutor(auctionId).submit(runChain)

    loop runChain — SingleThreadExecutor per auction
        AutoBidProcessor->>AutoBidRegistry: getCandidates(auctionId)
        AutoBidRegistry-->>AutoBidProcessor: List~AutoBidEntry~ (sorted by maxBid desc)

        AutoBidProcessor->>AuctionManager: findAuctionById(auctionId)
        AuctionManager-->>AutoBidProcessor: Auction

        loop For each candidate
            Note over AutoBidProcessor: AutoBidStrategy.calculateNextBid(auction)
            
            alt nextBid <= maxBid && auction.isAcceptingBids()
                AutoBidProcessor->>BidService: placeBid(candidate, auction, nextBid, AutoBidStrategy)

                BidService->>AuctionLockRegistry: acquireLock(auctionId)
                BidService->>Auction: isValidBid / updateBid(nextBid, candidate)
                BidTransactionDAO-->>BidService: saveTransaction(tx) [outside lock]
                Note over BidService: notify(BID_PLACED) via AuctionService
                BidService->>AutoBidProcessor: submit(auction, candidate.id) [recursive trigger]
                BidService->>AuctionLockRegistry: releaseLock(auctionId)

                BidService->>SessionManager: broadcast BID_UPDATE to auction room
                BidService-->>AutoBidProcessor: OK
            else nextBid > maxBid || !isAcceptingBids
                Note over AutoBidProcessor: skip candidate
            end
        end

        alt needsRecheck == true
            Note over AutoBidProcessor: recheck.set(false) and runChain() again
        end
    end
```
