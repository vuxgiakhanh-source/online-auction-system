# Auto-Bid Subsystem Class Diagram

Auto-bid: `AutoBidRegistry` (memory + DB), `AutoBidProcessor` (chuỗi async), `BidStrategy` / `AutoBidStrategy`, liên kết `BidHandler` và `BidService`.

**Mục đích:** Hiểu cấu trúc auto-bid tách khỏi bid thủ công.  
**Use case:** Debug counter-bid, đăng ký/hủy auto-bid, performance chain sau `PLACE_BID`.  
**Trong code:** `com.group13.auction.strategy.AutoBid*`, `BidHandler` handlers REGISTER/UPDATE/CANCEL_AUTO_BID.

```mermaid
flowchart LR
    subgraph API
        BH["BidHandler"]
    end
    subgraph Service
        BS["BidService"]
    end
    subgraph Domain
        REG["AutoBidRegistry"]
        LOCK["AuctionLockRegistry"]
    end
    subgraph Infrastructure
        ABP["AutoBidProcessor"]
    end
    subgraph Database
        ADAO["AutoBidDAO"]
    end

    BH --> REG & ABP & BS
    ABP --> REG & BS
    REG --> ADAO
    BS --> LOCK
```

`BidStrategy` · `StandardBidStrategy` · `AutoBidStrategy` — sequence: [AutoBidSequenceDiagram.md](./AutoBidSequenceDiagram.md)
