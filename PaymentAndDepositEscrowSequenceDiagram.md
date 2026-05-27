# Payment and Deposit Escrow Sequence Diagram

Winner gửi `PAYMENT_REQUEST` sau khi phiên FINISHED: trừ ví, chuyển tiền vào `SystemBank`, phiên chuyển PAID (`FUNDS_HELD`).  
Cọc winner đã vào bank khi `closeAuction` (tham chiếu chung escrow).

**Mục đích:** Trace thanh toán thắng cuộc và trạng thái giữ tiền.  
**Use case:** Winner thanh toán trong 24h, seller chờ nhận hàng/xác nhận, debug PAYMENT_FAILED.  
**Trong code:** `PaymentHandler` → `PaymentService.completePayment` → `AuctionService.markAsPaid`.

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
        participant WAL as WalletService
        participant AS as AuctionService
    end
    box Domain
        participant BANK as SystemBank
    end
    box Infrastructure
        participant SBN as ServerBroadcastNotifier
    end

    W->>PH: PAYMENT_REQUEST
    PH->>PS: completePayment
    PS->>WAL: executePaymentToBank
    WAL->>BANK: receive
    PS->>AS: markAsPaid
    PS->>SBN: notifyPaymentSuccess
    PH->>W: PAYMENT_SUCCESS
```

Cọc winner khi đóng phiên: `AuctionService.closeAuction` → `SystemBank.receive(deposit)`.
