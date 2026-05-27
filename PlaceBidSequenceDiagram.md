# Place Bid Sequence Diagram

Luồng packet `PLACE_BID`: validate → lock per auction → ghi DB → notify → broadcast WS → kích hoạt auto-bid.  
Lock nằm trong `BidService`, không phải `BidHandler`.

**Mục đích:** Trace end-to-end một lượt đặt giá hợp lệ.  
**Use case:** Bidder đặt giá, anti-sniping gia hạn phiên, outbid, race condition / lost update.  
**Trong code:** `BidHandler.handlePlaceBid` → `BidService.placeBid` → `BidTransactionDAO.saveTransactionAndUpdatePrice`.  
WS bid: [RealtimeBroadcastViaObserverSequenceDiagram.md](./RealtimeBroadcastViaObserverSequenceDiagram.md) (Path B).

## Tổng quan

```mermaid
sequenceDiagram
    box Client
        actor C as Client
    end
    box API
        participant BH as BidHandler
        participant SM as SessionManager
    end
    box Service
        participant BS as BidService
        participant AS as AuctionService
    end
    box Infrastructure
        participant ABP as AutoBidProcessor
    end

    C->>BH: PLACE_BID
    BH->>BS: placeBid()
    BS->>AS: notify()
    BH->>C: PLACE_BID_SUCCESS
    BH->>SM: broadcastToAuctionAsync
    BH->>ABP: submit()
```

## Chi tiết — `BidService`

Lock per auction chỉ trong service; persist và notify chạy sau khi unlock.

```mermaid
sequenceDiagram
    box Service
        participant BS as BidService
        participant AS as AuctionService
    end
    box Domain
        participant LOCK as AuctionLockRegistry
        participant AUC as Auction
    end
    box Infrastructure
        participant SBN as ServerBroadcastNotifier
    end
    box Database
        participant DAO as BidTransactionDAO
    end

    BS->>LOCK: lock
    BS->>AUC: updateBid · BidTransaction
    opt anti-sniping
        BS->>AUC: extendEndTime
    end
    BS->>LOCK: unlock
    BS->>DAO: saveTransactionAndUpdatePrice
    BS->>AS: notify
    opt outbid
        BS->>SBN: notifyOutbid
    end
```

Auto-bid: [AutoBidSequenceDiagram.md](./AutoBidSequenceDiagram.md)
