# Sequence Diagram — Auction Lifecycle + Auto-Close

```mermaid
sequenceDiagram
    participant Scheduler as "AuctionTimerService / Scheduler"
    participant AuctionService
    participant Auction as "Auction (State Machine)"
    participant PaymentService
    participant Observers as "Observers (Bidder/Seller/Admin)"
    participant WebSocket as "ServerBroadcastNotifier"

    Scheduler->>AuctionService: closeAuction(auctionId) [timer trigger]
    AuctionService->>Auction: getState() / isEnded()
    
    alt Reserve Met + Has Winner
        Auction->>AuctionService: processWinner()
        AuctionService->>PaymentService: processAuctionCompletion(auction)
        PaymentService->>Auction: createAuctionWinner()
        PaymentService-->>Observers: AUCTION_ENDED_UPDATE + Payment Notify
    else No Winner / Reserve Not Met
        AuctionService->>Auction: setStatus(CANCELED / NO_WINNER)
        AuctionService-->>Observers: AUCTION_NO_WINNER_UPDATE or RESERVE_NOT_MET
    end

    Observers->>WebSocket: broadcast via WebSocket sessions
    Note over Scheduler,Auction: Anti-sniping extension nếu bid cuối cùng
```