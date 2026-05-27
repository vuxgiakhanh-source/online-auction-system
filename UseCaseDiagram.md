# Use Case Diagram

Góc nhìn nghiệp vụ (actor → capability), không thay sequence diagram.  
Dùng để đối chiếu phạm vi sản phẩm với implementation.

**Mục đích:** Tổng quan chức năng.  
**Use case:** Review scope dự án, map use case → file sequence/class diagram bên dưới.  
**Chi tiết kỹ thuật:** Các file `*SequenceDiagram.md`, `*ClassDiagram.md`.

## Quản lý chung

```mermaid
flowchart LR
    U["Bidder / Seller"] --> UC1["Auth"] & UC2["Wallet"] & UC3["Notifications"]
    ADM["Admin"] --> UC4["User admin"]
```

## Đấu giá

```mermaid
flowchart LR
    B["Bidder"] --> UC5["Browse"] & UC7["Join"] & UC8["Bid"] & UC9["Auto-bid"]
    S["Seller"] --> UC6["Create auction"]
```

## Hậu mãi

```mermaid
flowchart LR
    T["AuctionTimerService"] --> UC10["Lifecycle jobs"]
    W["Winner"] --> UC11["Pay"] & UC13["Confirm receipt"] & UC14["Quality report"]
```

| Use case | Diagram |
|----------|---------|
| Bid | [PlaceBidSequenceDiagram.md](./PlaceBidSequenceDiagram.md) |
| Lifecycle | [AuctionLifecycleSequenceDiagram.md](./AuctionLifecycleSequenceDiagram.md) |
| Payment | [PaymentAndDepositEscrowSequenceDiagram.md](./PaymentAndDepositEscrowSequenceDiagram.md) |
