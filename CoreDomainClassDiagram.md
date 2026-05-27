# Core Domain Class Diagram

Mô hình nghiệp vụ cốt lõi: `User`, `Item`, `Auction`, state pattern, `AuctionWinner`, bid và quality report.  
Kèm sơ đồ chuyển trạng thái phiên đấu giá (OPEN → RUNNING → FINISHED / PAID / CANCELED).

**Mục đích:** Hiểu entity và quy tắc chuyển trạng thái trước khi đọc service/timer.  
**Use case:** Thiết kế feature mới trên auction, debug sai status, review domain model.  
**Trong code:** `com.group13.auction.model.*`, `AuctionManager.loadDataFromDatabase()`.

```mermaid
classDiagram
    direction LR

    class Entity
    class User
    class NormalUser
    class Admin
    class SystemAdmin
    class Item
    class Auction
    class AuctionState {
        <<interface>>
    }
    class OpenState
    class RunningState
    class FinishedState
    class PaidState
    class CanceledState
    class AuctionWinner
    class BidTransaction
    class QualityReport

    Entity <|-- User
    Entity <|-- Item
    Entity <|-- Auction
    User <|-- NormalUser
    User <|-- Admin
    Admin <|-- SystemAdmin
    AuctionState <|.. OpenState
    AuctionState <|.. RunningState
    AuctionState <|.. FinishedState
    AuctionState <|.. PaidState
    AuctionState <|.. CanceledState
    Auction *-- Item
    Auction --> AuctionState
```

## Trạng thái phiên (`Auction`)

```mermaid
stateDiagram-v2
    direction LR

    [*] --> OPEN
    OPEN --> RUNNING: startAuction / timer
    OPEN --> CANCELED: cancelAuction
    RUNNING --> FINISHED: closeAuction · reserve met
    RUNNING --> CANCELED: no leader / reserve not met
    FINISHED --> PAID: completePayment
    FINISHED --> CANCELED: expirePayment · SCO decline
    PAID --> PAID: confirmItemReceived
```

Timer: [AuctionLifecycleSequenceDiagram.md](./AuctionLifecycleSequenceDiagram.md)
