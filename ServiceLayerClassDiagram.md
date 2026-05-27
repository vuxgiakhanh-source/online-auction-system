# Service Layer Class Diagram

Các service nghiệp vụ và phụ thuộc tới `AuctionManager`, DAO, `SystemBank`, `AuctionTimerService`, `ServerBroadcastNotifier`.  
Thể hiện quan hệ `BidService` → `AuctionService`, `PaymentService` → `WalletService`, v.v.

**Mục đích:** Nắm ranh giới service và dependency khi sửa logic hoặc thêm API.  
**Use case:** Refactor service, viết integration test, tránh gọi chéo tầng sai.  
**Trong code:** `com.group13.auction.service.*`, composition root `ServerMain`.

```mermaid
flowchart LR
    subgraph Service
        AUC["AuctionService"]
        BID["BidService"]
        PAY["PaymentService"]
        WAL["WalletService"]
        RTG["RatingService"]
        QR["QualityReportService"]
    end
    subgraph Domain
        AM["AuctionManager"]
        BANK["SystemBank"]
    end
    subgraph Infrastructure
        TMR["AuctionTimerService"]
        SBN["ServerBroadcastNotifier"]
    end
    subgraph Database
        DAO["DAOs"]
    end

    BID --> AUC
    PAY --> AUC & WAL
    AUC --> AM & SBN & DAO
    WAL --> BANK & DAO
    TMR --> AUC & PAY
```

| Interface | Class |
|-----------|--------|
| `IAuctionService` | `AuctionService` |
| `IBidService` | `BidService` |
| `IPaymentService` | `PaymentService` |
| `IWalletService` | `WalletService` |
| `IAuctionTimerService` | `AuctionTimerService` |
| `IScheduler` | `TaskScheduler` |
