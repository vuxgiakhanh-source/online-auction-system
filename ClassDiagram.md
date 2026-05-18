# Class Diagram - Domain Model + Service Layer

```mermaid
classDiagram
    %% ==================== DOMAIN MODEL ====================

    class User {
        +String id
        +String username
        +String passwordHash
        +double rating
        +boolean isBanned
        +getRole() Role
    }
    class NormalUser {
        +Wallet wallet
        +List~Auction~ watchList
        +List~Bid~ bids
        +requestCancelAuction(Auction, String)
    }
    class Admin {
        +CancelReason
        +cancelAuction(Auction, CancelReason)
    }
    class SystemAdmin
    class Staff

    User <|-- NormalUser
    User <|-- Admin
    Admin <|-- SystemAdmin
    Admin <|-- Staff

    class Wallet {
        +double balance
        +double lockedDeposit
        +lockDeposit(double)
        +unlockDeposit(double)
        +deduct(double)
        +add(double)
    }
    NormalUser "1" --> "1" Wallet : owns

    class Item {
        +String id
        +String name
        +String description
        +double startingPrice
        +Category category
        +List~String~ imageUrls
        +NormalUser seller
    }
    class Auction {
        +String id
        +Item item
        +NormalUser seller
        +LocalDateTime startTime
        +LocalDateTime endTime
        +double reservePrice
        +double currentHighestBid
        +NormalUser highestBidder
        +AuctionStatus status
        +extendEndTime(Duration)
    }
    Item "1" --> "1" Auction : belongsTo

    class Bid {
        +String id
        +double amount
        +LocalDateTime time
        +NormalUser bidder
        +Auction auction
    }
    Auction "1" o-- "0..*" Bid : has
    NormalUser "1" --> "0..*" Bid : places

    class Payment {
        +String id
        +double amount
        +PaymentStatus status
        +PaymentType type
    }
    Auction "1" --> "0..1" Payment : winnerPayment

    %% ==================== REPOSITORIES / DAOs ====================

    class AuctionDAO
    class ItemDAO
    class UserDAO
    class BidDAO
    class WalletDAO
    class PaymentDAO

    %% ==================== SERVICES ====================

    class AccountService {
        +register()
        +login()
        +requestCancelAuction(NormalUser, Auction, String)
        +updateRating(User, int)
    }

    class AuctionService {
        +createAuction(NormalUser, Item, LocalDateTime, LocalDateTime, double)
        +cancelAuction(Admin, Auction, CancelReason)
        +findById(String)
    }

    class BidService {
        +placeBid(NormalUser, Auction, double)
        +joinAuction(NormalUser, Auction)
        +processAutoBids()
    }

    class PaymentService {
        +processWinnerPayment(Auction)
        +refundDeposits(Auction)
        +lockDeposit(NormalUser, double)
    }

    class RatingService {
        +rateUser(User, int, String)
        +applyPenalty(User)
    }

    class AuctionTimerService {
        +scheduleEnd(Auction)
        +cancelTimer(Auction)
    }

    %% ==================== MANAGERS (Shared State) ====================

    class AuctionManager {
        +List~Auction~ auctions
        +findAuctionById(String)
        +getAllAuctions()
        +getAuctionsByStatus(AuctionStatus)
        +Singleton
    }

    class SessionManager {
        +broadcastToAuction(Auction, Packet)
        +broadcastToAdmins(Packet)
    }

    %% ==================== PATTERNS ====================

    class ItemFactory {
        +create(Category, String, String, double, NormalUser, Map, List~String~)
    }

    class BidStrategy {
        <<interface>>
        +shouldPlaceBid(Auction, double) boolean
        +calculateNextBid(Auction) double
    }
    class StandardBidStrategy
    class AutoBidStrategy

    BidStrategy <|.. StandardBidStrategy
    BidStrategy <|.. AutoBidStrategy

    class AuctionState {
        <<interface>>
        +handleBid(Bid)
        +onTimerExpire()
    }
    class OpenState
    class ClosedState
    class PaymentPendingState

    AuctionState <|.. OpenState
    AuctionState <|.. ClosedState
    AuctionState <|.. PaymentPendingState

    Auction "1" --> "1" AuctionState : currentState

    %% ==================== RELATIONSHIPS ====================

    AuctionService --> AuctionDAO : uses
    AuctionService --> ItemDAO : uses
    AuctionService --> AuctionManager : uses
    AuctionService --> ItemFactory : uses

    BidService --> AuctionManager : uses
    BidService --> BidDAO : uses
    BidService --> PaymentService : uses
    BidService --> BidStrategy : uses
    BidService --> AuctionTimerService : uses

    PaymentService --> WalletDAO : uses
    PaymentService --> PaymentDAO : uses
    PaymentService --> AuctionManager : uses

    AccountService --> UserDAO : uses
    AccountService --> WalletDAO : uses
    AccountService --> RatingService : uses

    AuctionManager --> Auction : manages
    AuctionManager --> User : manages

    AuctionHandler ..> AuctionService : depends
    AuctionHandler ..> AccountService : depends
```

## Giải thích Kiến trúc

1. Domain Model
Hệ thống được xây dựng xung quanh các thực thể cốt lõi sau:

* User là lớp gốc, được kế thừa bởi NormalUser (người dùng thông thường) và Admin (quản trị viên).  
* NormalUser sở hữu Wallet để quản lý số dư và tiền cọc (locked deposit).  
* Item đại diện cho sản phẩm đấu giá, chứa thông tin mô tả và hình ảnh.  
* Auction là trung tâm của domain, quản lý vòng đời phiên đấu giá, trạng thái và thông tin người thắng cuộc. 
* Bid ghi nhận các lần ra giá.

**Mối quan hệ chính:**

* Một Auction thuộc về một Item và một NormalUser (người bán).  
* Một Auction có thể chứa nhiều Bid.

2. Service Layer & Business Logic
Các service được thiết kế theo nguyên tắc Single Responsibility và high cohesion:

* AuctionService: Quản lý vòng đời phiên đấu giá (tạo, cập nhật, hủy).
* BidService: Xử lý logic ra giá, kiểm tra điều kiện tham gia, phối hợp với auto-bid và thanh toán.
* PaymentService: Xử lý thanh toán cho người thắng cuộc và hoàn tiền cọc cho người thua.
* AccountService & RatingService: Quản lý tài khoản và hệ thống đánh giá uy tín.
* AuctionTimerService: Quản lý bộ đếm thời gian kết thúc phiên.
* AuctionManager (Singleton) đóng vai trò trung tâm, giữ trạng thái trong bộ nhớ của tất cả các phiên đấu giá đang hoạt động.

3. Design Patterns được áp dụng

* Factory Pattern: ItemFactory, UserFactory.
* Strategy Pattern: BidStrategy (hỗ trợ linh hoạt các chiến lược tự động ra giá).
* State Pattern: AuctionState cho phép Auction thay đổi hành vi theo từng trạng thái (Open, Closed, PaymentPending…).
* Observer Pattern: Thông qua SessionManager để broadcast thông báo realtime.
* Repository/DAO Pattern: Tách biệt logic truy xuất dữ liệu.