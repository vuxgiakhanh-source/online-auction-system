# Realtime Broadcast & Notification

Server có **hai kênh** độc lập: (A) domain notify + inbox DB, (B) WebSocket bid trực tiếp từ `BidHandler`.  
Diagram sequence cho từng kênh; không coi `AuctionObserver` là pipeline WS cho `BID_UPDATE`.

**Mục đích:** Giải thích đúng luồng realtime khi đọc code hoặc viết tài liệu.  
**Use case:** Bidder thấy giá nhảy, inbox thông báo, outbid, auction ended trên UI.  
**Trong code:** `AuctionService.notify`, `ServerBroadcastNotifier`, `SessionManager.broadcastToAuctionAsync`.

| Kênh | Khi nào | Code |
|------|---------|------|
| **A — Domain notify** | Lifecycle, payment, inbox | `AuctionService.notify` → observers + `ServerBroadcastNotifier` → `NotificationDAO` |
| **B — Bid WebSocket** | Giá bid realtime | `BidHandler` → `SessionManager.broadcastToAuctionAsync` |

Class diagram: [RealtimeObserverClassDiagram.md](./RealtimeObserverClassDiagram.md)

## Path A — `AuctionService.notify`

```mermaid
sequenceDiagram
    box Service
        participant Src as BidService / AuctionService / PaymentService
        participant AS as AuctionService
    end
    box Domain
        participant Obs as AuctionObserver
        participant AM as AuctionManager
    end
    box Infrastructure
        participant SBN as ServerBroadcastNotifier
    end
    box Database
        participant DAO as NotificationDAO
    end

    Src->>AS: notify(event)
    AS->>Obs: onBidPlaced / onAuctionEnded
    AS->>AM: notifyGlobalObservers / notifyStaffObservers
    AS->>SBN: notifyJoinedParticipantsForEvent
    SBN->>DAO: save Notification
```

`BidderObserver` dùng `ConsoleNotifier` — **không** gửi WebSocket.

## Path B — Bid WebSocket

```mermaid
sequenceDiagram
    box Client
        participant C as Client
    end
    box API
        participant BH as BidHandler
        participant SM as SessionManager
    end
    box Service
        participant BS as BidService
    end

    C->>BH: PLACE_BID
    BH->>BS: placeBid()
    Note over BS: Path A chạy trong placeBid
    BH->>C: PLACE_BID_SUCCESS
    BH->>SM: broadcastToAuctionAsync
    SM->>C: BID_UPDATE · BID_CHART_POINT_UPDATE
```

Lifecycle/payment WS: `ServerBroadcastNotifier` → `SessionManager` (`AUCTION_ENDED_UPDATE`, `OUTBID_NOTIFY`, …). Timer: `AuctionTimerService.broadcastUpdate`.

Place bid chi tiết: [PlaceBidSequenceDiagram.md](./PlaceBidSequenceDiagram.md)
