# Backend Class Diagrams

Backend được chia thành nhiều class diagram theo từng subsystem riêng biệt

- [CoreDomainClassDiagram.md](./CoreDomainClassDiagram.md)
- [ServiceLayerClassDiagram.md](./ServiceLayerClassDiagram.md)
- [PaymentSubsystemClassDiagram.md](./PaymentSubsystemClassDiagram.md)
- [AutoBidSubsystemClassDiagram.md](./AutoBidSubsystemClassDiagram.md)
- [RealtimeObserverClassDiagram.md](./RealtimeObserverClassDiagram.md)

```mermaid
classDiagram
    direction LR

    class AuctionWebSocketServer
    class PacketRouter
    class PacketHandler {
        <<interface>>
        +supports(PacketType) boolean
        +handle(ClientSession, PacketType, JsonElement, String)
    }
    class AuctionManager {
        <<Singleton>>
        +registerAuction(Auction)
        +findAuctionById(String) Auction
        +getAuctionsByStatus(AuctionStatus) List~Auction~
        +notifyGlobalObservers(AuctionEvent)
        +notifyStaffObservers(AuctionEvent)
    }
    class AuctionService {
        +createAuction(...)
        +startAuction(Auction)
        +closeAuction(Auction)
        +markAsPaid(Auction)
        +cancelAuction(Auction, CancelReason)
        +notify(Auction, AuctionEventType, NormalUser, long)
    }
    class BidService {
        +joinAuction(User, Auction, AuctionObserver)
        +watchAuction(User, Auction, AuctionObserver)
        +placeBid(NormalUser, Auction, long, BidStrategy)
        +leaveAuction(User, Auction) LeaveResult
    }
    class PaymentService {
        +completePayment(Auction)
        +expirePayment(Auction)
        +refundDeposits(Auction)
        +acceptSecondChanceOffer(SecondChanceOffer, Auction)
        +releaseToSeller(Auction)
    }
    class AuctionTimerService {
        <<Singleton>>
        +start(IAuctionService, IPaymentService, SessionManager)
        +stop()
    }
    class SessionManager {
        <<Singleton>>
        +authenticate(WebSocket, String, String, String)
        +broadcastToAuction(String, Packet)
        +broadcastToAuctionAsync(String, Packet)
        +sendToUser(String, Packet)
    }
    class ServerBroadcastNotifier {
        <<Singleton>>
        +notifyBidUpdate(Auction, long, String, boolean)
        +notifyAuctionEnded(Auction)
        +notifyPaymentSuccess(Auction, PaymentResultDTO)
        +notifySecondChanceOffered(Auction, NormalUser, SecondChanceOffer)
        +notifyJoinedParticipantsForEvent(AuctionEvent)
    }

    AuctionWebSocketServer --> PacketRouter
    PacketRouter --> PacketHandler
    PacketHandler <|.. AuctionHandler
    PacketHandler <|.. BidHandler
    PacketHandler <|.. PaymentHandler
    PacketHandler <|.. UserAdminHandler
    PacketHandler <|.. AuthHandler
    AuctionHandler --> AuctionService
    BidHandler --> BidService
    BidHandler --> AutoBidProcessor
    PaymentHandler --> PaymentService
    AuctionTimerService --> AuctionManager
    AuctionTimerService --> AuctionService
    AuctionTimerService --> PaymentService
    AuctionService --> AuctionManager
    AuctionService --> ServerBroadcastNotifier
    BidService --> AuctionService
    PaymentService --> AuctionService
    ServerBroadcastNotifier --> SessionManager
```
