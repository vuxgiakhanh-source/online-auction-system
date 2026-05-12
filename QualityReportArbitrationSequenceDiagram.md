# Quality Report Arbitration Sequence Diagram

```mermaid
sequenceDiagram
    participant Buyer
    participant AuctionService / ReportService
    participant Admin
    participant Seller
    participant WalletService
    participant RatingService

    Buyer->>ReportService: submitQualityReport(auctionId, issues, evidence)
    ReportService->>AuctionService: validateReport()
    ReportService-->>Buyer: Report submitted (PENDING)

    Admin->>ReportService: reviewReport(reportId, decision: APPROVE/REJECT)
    
    alt APPROVE (có vấn đề)
        ReportService->>WalletService: refundBuyer() + penalizeSeller()
        ReportService->>RatingService: decreaseSellerRating()
        ReportService->>Seller: notify penalty / refund info
    else REJECT
        ReportService-->>Buyer: report rejected
        ReportService->>Seller: notify cleared
    end

    ReportService-->>All: broadcast update status
```