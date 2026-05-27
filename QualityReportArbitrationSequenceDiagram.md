# Quality Report Arbitration Sequence Diagram

Winner gửi báo cáo chất lượng (sau ITEM_RECEIVED); admin approve → hoàn tiền winner từ bank, phạt seller; reject → thông báo từ chối.  
Toàn bộ qua WebSocket `UserAdminHandler`, không gọi service trực tiếp từ client.

**Mục đích:** Trace khiếu nại hàng không đúng mô tả và quyết định admin.  
**Use case:** Submit report có ảnh, admin duyệt/refund, seller bị trừ rating/ban.  
**Trong code:** `SUBMIT_QUALITY_REPORT`, `ADMIN_APPROVE_QUALITY_REPORT`, `QualityReportService`.

## Submit

```mermaid
sequenceDiagram
    box Client
        actor W as Winner
    end
    box API
        participant UAH as UserAdminHandler
        participant SM as SessionManager
    end
    box Service
        participant QR as QualityReportService
    end

    W->>UAH: SUBMIT_QUALITY_REPORT
    UAH->>QR: submitReport
    UAH->>W: SUCCESS
    UAH->>SM: QUALITY_REPORT_RECEIVED_NOTIFY → seller
```

## Admin approve

```mermaid
sequenceDiagram
    box Client
        actor A as Admin
    end
    box API
        participant UAH as UserAdminHandler
    end
    box Service
        participant QR as QualityReportService
        participant PS as PaymentService
    end
    box Domain
        participant BANK as SystemBank
    end

    A->>UAH: ADMIN_APPROVE_QUALITY_REPORT
    UAH->>QR: approveReport
    QR->>PS: refundToWinnerFromBank
    PS->>BANK: refundToWinner
    UAH->>A: SUCCESS
```
