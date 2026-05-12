# UseCase Diagram

```mermaid
usecaseDiagram

actor Guest as "Guest"
actor User as "Registered User"
actor Seller as "Seller"
actor Bidder as "Bidder"
actor Admin as "Admin / Staff"
actor System as "System Scheduler"
actor AutoBid as "Auto Bid Engine"

rectangle Authentication {
    usecase UC1 as "Register"
    usecase UC2 as "Login / Logout"
    usecase UC3 as "View Profile"
    usecase UC4 as "Request Seller Role"
}

rectangle "Wallet Management" {
    usecase UC5 as "Deposit Money"
    usecase UC6 as "Withdraw Money"
    usecase UC7 as "View Wallet Balance"
}

rectangle "Auction Management" {
    usecase UC8 as "Create Auction"
    usecase UC9 as "Browse Auctions"
    usecase UC10 as "View Auction Detail"
    usecase UC11 as "Update Auction"
    usecase UC12 as "Cancel Auction Request"
}

rectangle Bidding {
    usecase UC13 as "Join Auction (Deposit)"
    usecase UC14 as "Place Manual Bid"
    usecase UC15 as "Register Auto Bid"
    usecase UC16 as "View Bid History"
    usecase UC17 as "Watch Auction (Realtime)"
}

rectangle "Auction Lifecycle" {
    usecase UC18 as "Start Auction"
    usecase UC19 as "Close Auction"
    usecase UC20 as "Anti-Sniping Extension"
}

rectangle "Post-Auction" {
    usecase UC21 as "Make Payment"
    usecase UC22 as "Second Chance Offer"
    usecase UC23 as "Submit Quality Report"
    usecase UC24 as "Rate Participant"
}

rectangle Administration {
    usecase UC25 as "Ban / Unban User"
    usecase UC26 as "Approve Seller Role"
    usecase UC27 as "Admin Cancel Auction"
    usecase UC28 as "Review Quality Reports"
}

rectangle Notification {
    usecase UC29 as "Receive Notifications"
}

%% Relationships

Guest --> UC1
Guest --> UC2

User --> UC2
User --> UC3
User --> UC4
User --> UC5
User --> UC6
User --> UC7
User --> UC9
User --> UC10
User --> UC17
User --> UC21
User --> UC22
User --> UC23
User --> UC24
User --> UC29

Seller --> UC8
Seller --> UC11
Seller --> UC12
Seller --> UC29

Bidder --> UC13
Bidder --> UC14
Bidder --> UC15
Bidder --> UC16
Bidder --> UC29

Admin --> UC25
Admin --> UC26
Admin --> UC27
Admin --> UC28
Admin --> UC29

System --> UC18
System --> UC19
System --> UC20

AutoBid --> UC15

%% Include / Extend Relationships

UC13 ..> UC5 : <<include>>
UC21 ..> UC7 : <<include>>

UC19 ..> UC21 : <<extend>>
UC19 ..> UC22 : <<extend>>

UC14 ..> UC17 : <<include>>

UC15 ..> UC13 : <<include>>
UC15 ..> UC14 : <<extend>>

%% Inheritance

Seller --|> User
Bidder --|> User
```