# Auto-Bid Sequence Diagram

Đăng ký `REGISTER_AUTO_BID` (có thể bid lần đầu nếu chưa là leader) và chuỗi counter-bid async qua `AutoBidProcessor`.  
Registry persist `AutoBidDAO`; chain chạy trên executor riêng từng phiên.

**Mục đích:** Hiểu thứ tự đăng ký và bid tự động sau `PLACE_BID`.  
**Use case:** User bật/tắt auto-bid, bị vượt giá tự counter, leader đăng ký khi đang dẫn.  
**Trong code:** `BidHandler` + `AutoBidRegistry` + `AutoBidProcessor.submit`.

## Đăng ký

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
    end
    box Domain
        participant REG as AutoBidRegistry
    end
    box Database
        participant DAO as AutoBidDAO
    end
    box Infrastructure
        participant ABP as AutoBidProcessor
    end

    C->>BH: REGISTER_AUTO_BID
    BH->>REG: register
    REG->>DAO: upsert
    alt đã là leader
        BH->>C: SUCCESS (không bid lần đầu)
    else chưa leader
        BH->>BS: placeBid(AutoBidStrategy)
        BH->>SM: broadcastToAuction
        BH->>C: SUCCESS
    end
    BH->>ABP: submit
```

## Chuỗi async (`AutoBidProcessor`)

```mermaid
sequenceDiagram
    box Infrastructure
        participant ABP as AutoBidProcessor
    end
    box Domain
        participant REG as AutoBidRegistry
    end
    box Service
        participant BS as BidService
    end

    ABP->>ABP: runChain trên SingleThreadExecutor
    loop counter-bid
        ABP->>REG: getEntriesForAuction
        ABP->>BS: placeBid(AutoBidStrategy)
    end
```

Class: [AutoBidSubsystemClassDiagram.md](./AutoBidSubsystemClassDiagram.md)
