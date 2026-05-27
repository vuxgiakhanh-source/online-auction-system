# Realtime Observer Class Diagram

Quan hệ `AuctionObserver`, `AuctionService.notify`, `ServerBroadcastNotifier`, `SessionManager` và `BidHandler` (WS bid).  
Làm rõ observer in-memory (thường `ConsoleNotifier`) khác kênh WebSocket bid.

**Mục đích:** Tránh nhầm Observer pattern = broadcast giá realtime.  
**Use case:** Sửa thông báo inbox, outbid, lifecycle WS; thêm event mới.  
**Trong code:** `com.group13.auction.observer.*`, `ServerBroadcastNotifier`, `BidHandler`.

```mermaid
flowchart LR
    subgraph Service
        AS["AuctionService"]
    end
    subgraph Domain
        Obs["AuctionObserver"]
        AM["AuctionManager"]
    end
    subgraph Infrastructure
        SBN["ServerBroadcastNotifier"]
    end
    subgraph API
        BH["BidHandler"]
        SM["SessionManager"]
    end
    subgraph Database
        NDAO["NotificationDAO"]
    end

    AS --> Obs & AM & SBN
    SBN --> NDAO & SM
    BH --> SM
```

- **Path A:** `AuctionService.notify` → inbox / observers  
- **Path B:** `BidHandler` → `SessionManager` (bid WS)  

Chi tiết: [RealtimeBroadcastViaObserverSequenceDiagram.md](./RealtimeBroadcastViaObserverSequenceDiagram.md)
