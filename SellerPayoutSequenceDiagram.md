# Seller Payout Sequence Diagram

Sau PAID: winner `CONFIRM_ITEM_RECEIVED` mở cửa sổ báo cáo chất lượng; timer có thể `releaseToSeller` nếu quá hạn xác nhận hoặc report.  
Giải ngân từ `SystemBank` sang ví seller (sau thuế).

**Mục đích:** Trace khi nào seller nhận tiền thực sự.  
**Use case:** Winner bấm đã nhận hàng, auto-release 7 ngày / 3 ngày report, seller balance tăng.  
**Trong code:** `PaymentService.confirmItemReceived`, `releaseToSeller`, `AuctionTimerService` auto-release.

## Winner xác nhận nhận hàng

`PaymentHandler` → `PaymentService.confirmItemReceived`

```mermaid
sequenceDiagram
    box Client
        actor W as Winner
    end
    box API
        participant PH as PaymentHandler
    end
    box Service
        participant PS as PaymentService
    end
    box Infrastructure
        participant SBN as ServerBroadcastNotifier
    end

    W->>PH: CONFIRM_ITEM_RECEIVED
    PH->>PS: confirmItemReceived
    PS->>SBN: notifyItemReceived
    PH->>W: SUCCESS
```

## Auto-release (timer ~60s)

`AuctionTimerService` → `PaymentService.releaseToSeller` → `SystemBank.payoutToSeller`

```mermaid
sequenceDiagram
    box Infrastructure
        participant TMR as AuctionTimerService
    end
    box Service
        participant PS as PaymentService
    end
    box Domain
        participant BANK as SystemBank
    end

    TMR->>PS: releaseToSeller
    PS->>BANK: payoutToSeller
```
