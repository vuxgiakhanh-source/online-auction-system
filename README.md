![Logo](OmniBid.png)

<div align="center">

# OminiBid - Hệ thống đấu giá Online 

![Java](https://img.shields.io/badge/Java-ED8B00?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/Apache_Maven-C71A36?logo=apachemaven&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-4A8CFF?logo=java&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-00BFFF?logo=websocket&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white)
![JUnit](https://img.shields.io/badge/JUnit-25A162?logo=junit5&logoColor=white)
![Lombok](https://img.shields.io/badge/Lombok-9C27B0?logo=lombok&logoColor=white)

</div>

## 🧾 Mục lục

* [Giới thiệu](#-giới-thiệu)
* [Đặc điểm kĩ thuật nổi bật](#-đặc-điểm-kĩ-thuật-nổi-bật)
* [Chức năng hệ thống](#-chức-năng-hệ-thống)
    * [Chức năng cốt lõi](#chức-năng-cốt-lõi)
    * [Chức năng nâng cao](#chức-năng-nâng-cao)
* [Kiến trúc hệ thống](#-kiến-trúc-hệ-thống)
    * [Kiến trúc tổng quan](#kiến-trúc-tổng-quan)
    * [Sơ đồ thiết kế hướng đối tượng OOP](#sơ-đồ-thiết-kế-hướng-đối-tượng-oop)
    * [Design Patterns áp dụng](#design-patterns-áp-dụng)
* [Hướng dẫn cài đặt](#-hướng-dẫn-cài-đặt-later)
* [Cách sử dụng](#-cách-sử-dụng-later)
* [Công nghệ & Công cụ sử dụng](#-công-nghệ--công-cụ-sử-dụng)
* [Đội ngũ & Phân công nhiệm vụ (Project Roadmap)](#-đội-ngũ--phân-công-nhiệm-vụ-project-roadmap)
* [Liên hệ](#-liên-hệ)
* [License](#-license)

___

## 🚀 Giới thiệu
**OmniBid** là một nền tảng đấu giá trực tuyến mạnh mẽ và hiện đại, được xây dựng để mang đến một môi trường giao dịch **minh bạch, công bằng và đầy kịch tính**.

Hệ thống cho phép người bán (Seller) dễ dàng đăng tải các sản phẩm đa dạng, trong khi người mua (Bidder) có thể tham gia đấu thầu cạnh tranh gay gắt để sở hữu món đồ với mức giá phù hợp nhất thông qua cơ chế thị trường thực thụ.

Không dừng lại ở việc đơn thuần kết nối người mua và người bán, **OmniBid** tập trung giải quyết triệt để những bài toán khó của hệ thống phân tán: xử lý **đấu giá đồng thời** an toàn, ngăn chặn Race Condition, **cập nhật giá realtime** cho hàng trăm người dùng cùng lúc thông qua WebSocket, cùng với **quy trình hậu mãi** chuyên nghiệp (escrow payment, Quality Report, hoàn tiền tự động, Second Chance Offer…).

Dự án được phát triển bằng Java theo mô hình Client-Server, áp dụng sâu các nguyên lý **OOP** cùng nhiều **Design Patterns** quan trọng (State, Observer, Strategy, Factory, Singleton). Nhờ đó, **OmniBid** không chỉ hoạt động mượt mà mà còn có kiến trúc sạch, dễ mở rộng và bảo trì.
> Add ảnh demo mô phỏng ứng dụng hoạt động (ghép 2-3 màn hình (Login, 
> Auction List, Bidding Detail) vào một khung ảnh)

---

## ✨ Đặc điểm kĩ thuật nổi bật
Hệ thống được phát triển với các tiêu chuẩn kỹ thuật:
* __Real-time Engine__: Sử dụng mô hình __Observer Pattern__ kết hợp với 
__Socket__ để cập nhật biến động giá ngay lập tức tới tất cả các client 
mà không cần tải lại trang.
> Add ảnh mô tả luồng hoạt động của 1 Bidder (Bidder → Server → BroadCast
> tới các Bidders khác) (GIF)


* __Concurrency Control__: Giải quyết triệt để các vấn đề Lost Update và Race Condition trong kịch bản nhiều người cùng đặt giá tại một mili giây.


* __Cấu trúc hướng đối tượng (OOP)__: Áp dụng chặt chẽ 4 nguyên lý OOP __(Đóng gói, Kế thừa, Đa hình, Trừu tượng)__ cùng các mẫu thiết kế __Factory Method, Singleton, Strategy, Observer và State__ để quản lý logic nghiệp vụ phức tạp.


* __Kiến trúc MVC Phân tầng:__ Tách biệt hoàn toàn giao diện (Client side) và logic xử lý dữ liệu (Server side) qua ___mô hình Client-Server___. Tách biệt rõ ràng các tầng (Network, Handler, Service, Domain, DAO, Observer).
> Add ảnh MVC Diagram
---

## 👷 Chức năng hệ thống
### Chức năng cốt lõi
* __Quản lý tài khoản và Phân quyền__: Hệ thống phân quyền chi tiết cho 03 nhóm đối tượng: Bidder (Người mua), Seller (Người bán) và Admin (Quản trị viên), đảm bảo tính bảo mật và đúng vai trò trong mọi tác vụ.


* __Phiên đấu giá linh hoạt (Smart Scheduler)__: Điều phối trạng thái phiên đấu giá hoàn toàn tự động theo thời gian thực (từ __OPEN → RUNNING → FINISHED__). Hệ thống đảm bảo tính chính xác tuyệt đối trong việc đóng/mở thầu.
> Add ảnh log của Server hiển thị chuyển trạng thái tự động (GIF)

* __Đấu giá Realtime & Thông báo__: Tích hợp cập nhật giá thầu tức thì (Realtime) trên toàn bộ client. Hệ thống thông báo giúp người dùng cập nhật trạng thái thắng / thua thầu ngay cả khi đang offline / online.
```mermaid
sequenceDiagram
    autonumber
    participant B as Bidder
    participant S as AuctionWebSocketServer
    participant L as AuctionLockRegistry
    participant SVC as BidService
    participant DB as Database
    participant OB as Other Bidders

    Note over B, OB: Quy trình đặt giá thầu Realtime (OmniBid)

    B->>S: Gửi Packet [PlaceBid]
    
    rect rgb(40, 45, 50)
        Note right of S: Concurrency Control
        S->>L: Acquire Lock (per-auction)
        L-->>S: Lock Acquired
    end

    S->>SVC: placeBid()
    
    activate SVC
    SVC->>SVC: Validate Bid (amount, status, session...)
    SVC->>SVC: Apply Strategy (Anti-Sniping + AutoBid)
    
    SVC->>DB: Update price & Save BidTransaction
    DB-->>SVC: Success
    
    SVC-->>S: Return Success Result
    deactivate SVC

    par Broadcast Realtime
        S->>OB: Notify via Observer Pattern (Price Update)
        S->>B: Notify Bid Result (Success)
    end

    S->>L: Release Lock
    Note over S, L: Mở khóa cho lượt bid tiếp theo
```
> Add ảnh 2 màn hình Client đang đấu giá với nhau và giá nhảy realtime (GIF)

* __Hệ thống Tài chính & Hậu mãi__: Tích hợp ví nội bộ xử lý thanh toán tự động khi kết thúc phiên (PAID). Cung cấp cơ chế __Báo cáo chất lượng (Quality Report)__ và __Hoàn tiền (Refund)__ tự động nếu sản phẩm không đúng cam kết, bảo vệ tối đa quyền lợi người mua.

### Chức năng nâng cao
* __Auto-Bidding (Đấu giá tự động)__: Cho phép người dùng thiết lập mức giá tối đa và bước giá để hệ thống tự động trả giá thay thế khi có đối thủ mới mà không cần trực tuyến liên tục.
> Add ảnh chụp giao diện người dùng thiết lập AutoBidding


* __Thuật toán Anti-Sniping__: Tự động gia hạn thời gian kết thúc nếu có lượt đặt giá phát sinh vào những giây cuối cùng, đảm bảo tính công bằng cho người dùng.


* __Trực quan hóa dữ liệu__: Hiển thị biểu đồ đường (Line Chart) biểu diễn lịch sử đấu giá theo thời gian thực, giúp người dùng phân tích xu hướng và đưa ra quyết định đặt giá chính xác.


* __Đề nghị Cơ hội Thứ hai (Second Chance Offer)__: Khi người thắng cuộc không thực hiện thanh toán đúng hạn, hệ thống sẽ tự động gửi đề nghị cho người xếp thứ hai với mức giá cao nhất tiếp theo.


* __Thanh toán Ký quỹ & Xử lý Khiếu nại__: Áp dụng cơ chế Escrow (giữ tiền qua SystemBank) để bảo vệ người mua. Sau khi nhận hàng, người mua có thể gửi **Báo cáo chất lượng (Quality Report)**. Admin duyệt báo cáo hợp lệ sẽ tiến hành hoàn tiền tự động cho người mua và trừ tiền người bán.
```mermaid
sequenceDiagram
    autonumber
    participant W as Winner
    participant S as AuctionWebSocketServer
    participant PS as PaymentService
    participant DB as Database
    participant BNK as SystemBank
    participant SEL as Seller

    Note over W, SEL: Quy trình Thanh toán Ký quỹ (Escrow Payment)

    S->>S: Auction Finished (Timer Trigger)
    S->>PS: processAuctionCompletion()
    
    activate PS
    PS->>DB: Lấy thông tin Winner & Final Price
    
    rect rgb(30, 40, 50)
        Note right of PS: Giai đoạn Ký quỹ (Escrow)
        PS->>BNK: Hold Escrow Amount (Tạm giữ tiền)
        BNK-->>PS: Escrow Success
    end
    
    PS->>DB: Create FinancialTransaction
    PS-->>W: Notify "Vui lòng xác nhận thanh toán"
    deactivate PS

    W->>S: Gửi Packet [ConfirmPayment]
    S->>PS: completePayment()

    activate PS
    PS->>BNK: Transfer to Seller (Sau khi trừ phí hệ thống)
    BNK-->>PS: Transfer Success
    PS->>DB: Update Auction Status = PAID
    
    par Thông báo kết quả
        PS-->>W: Notify "Thanh toán thành công"
        PS-->>SEL: Notify "Đã nhận tiền từ phiên đấu giá"
    end
    deactivate PS
```

* __Hệ thống Đánh giá Người dùng (Rating Service)__: Tự động theo dõi và cập nhật điểm đánh giá của người dùng dựa trên lịch sử giao dịch. Người dùng vi phạm nhiều lần (không giao hàng, khiếu nại không hợp lý...) sẽ bị tạm ngưng hoặc khóa tài khoản theo quy định.
> Ảnh chụp LineChart trong chương trình
---

## 🏗️ Kiến trúc hệ thống
Hệ thống được thiết kế theo kiến trúc __Layered Architecture__ kết hợp __Event-Driven Architecture__ mạnh mẽ trên mô hình __Client-Server__
### Kiến trúc tổng quan
```mermaid
graph TD

%% Nodes
Client["Client JavaFX + WebSocket"]
Server["AuctionWebSocketServer"]
Handlers["Packet Handlers"]
Service["Service Layer"]
Models["Domain Models + State/Strategy"]
DAO["DAO Layer + MySQL"]
Observer["Observer Pattern - Realtime Notification"]

%% Connections
Client --- Server
Server --> Handlers
Handlers --> Service
Service --> Models
Service <--> DAO
Service --> Observer
Observer --> Client

%% Styling
style Client fill:#242424,stroke:#666,stroke-width:1px,color:#fff
style Server fill:#242424,stroke:#666,stroke-width:1px,color:#fff
style Handlers fill:#242424,stroke:#666,stroke-width:1px,color:#fff
style Service fill:#242424,stroke:#666,stroke-width:1px,color:#fff
style Models fill:#242424,stroke:#666,stroke-width:1px,color:#fff
style DAO fill:#242424,stroke:#666,stroke-width:1px,color:#fff
style Observer fill:#242424,stroke:#666,stroke-width:1px,color:#fff
```

### Sơ đồ thiết kế hướng đối tượng OOP

```plaintext
online-auction-system/
├── auction-common/          # Shared: DTO, Protocol (Packet, PacketType, PacketCodec)
├── auction-server/          # Server: Business logic, DAO, WebSocket server
│   ├── model/
│   │   ├── bank/            # SystemBank
│   │   ├── entity/          # Entity (abstract base)
│   │   ├── user/            # User → NormalUser, Admin, SystemAdmin + Factory
│   │   ├── item/            # Item → Electronics, Art, Vehicle + Factory
│   │   ├── auction/         # Auction + State pattern (Open/Running/Finished/Paid/Canceled), AuctionWinner, SecondChanceOffer
│   │   └── bid/             # BidTransaction, FinancialTransaction, QualityReport
│   ├── service/             # AccountService, AuctionService, BidService, PaymentService, RatingService...
│   ├── dao/                 # AuctionDAO, UserDAO, ItemDAO, SecondChanceOfferDAO...
│   ├── network/server/      # AuctionWebSocketServer, PacketRouter, Handlers (AuthHandler, BidHandler...)
│   ├── observer/            # AuctionObserver, BidderObserver, SellerObserver...
│   ├── strategy/            # BidStrategy, AutoBidStrategy, AuctionLockRegistry...
│   └── manager/             # AuctionManager (Singleton)
└── auction-client/          # Client: JavaFX UI, WebSocket client
    └── network/client/      # AuctionWebSocketClient, ClientPacketDispatcher, Session...
```
> Add ảnh Class Diagram  
> Add ảnh DB Schema (Bảng User, ...)
### Design Patterns áp dụng:
| Pattern            | Implementation                                                                              | Mục đích hệ thống |
|--------------------|---------------------------------------------------------------------------------------------|----------------------------------|
| **State**          | `AuctionState` → `OpenState`, `RunningState`, `FinishedState`, `PaidState`, `CanceledState` | Quản lý logic chuyển đổi trạng thái phiên đấu giá (Mở → Chạy → Kết thúc) một cách tự động và rõ ràng. |
| **Observer**       | `AuctionObserver` → `BidderObserver`, `SellerObserver`, `AdminObserver`, `StaffObserver`    | Đẩy thông báo thay đổi giá và trạng thái đến toàn bộ người tham gia ngay lập tức (Realtime). |
| **Strategy**       | `BidStrategy`                                                                               | Linh hoạt giữa các chế độ đặt giá thủ công và Auto-Bidding. |
| **Factory Method** | `ItemFactory`, `UserFactory`                                                                | Chuẩn hóa việc tạo các loại Item (Electronics, Art, Vehicle...) và User. |
| **Singleton**      | `AuctionManager`, `DatabaseConnection`                                                      | Đảm bảo chỉ tồn tại duy nhất một instance cho các thành phần quản lý toàn cục. |

## 💡 Hướng dẫn cài đặt (Later)

### Yêu cầu :
* Java 17+

* Maven 3.8+

* MySQL 8.0+

### 1. Clone Repo
```
git clone https://github.com/vuxgiakhanh-source/online-auction-system.git
cd online-auction-system
```

### 2. Tạo Database
```
mysql -u root -p < auction-server/src/main/resources/database/database.sql
```
> Add ảnh chụp MySQL Workbench hoặc terminal sau khi import thành công.

### 3. Cấu hình kết nối
#### Chỉnh sửa file `auction-server/src/main/resources/data.properties:`
```
db.host=localhost
db.port=3306
db.name=auction_db
db.user=root
db.password=your_password
server.port=8887
```

### 4. Build toàn bộ project
```
mvn clean install
```
### 5. Khởi động Server
```
cd auction-server
mvn spring-boot:run
```

### 6. Khởi động Client
```
cd auction-client
mvn javafx:run
```
---

## 🔎 Cách sử dụng (Later)

---

## ⚙️ Công nghệ & Công cụ sử dụng
### Công nghệ theo lớp kiến trúc

| Lớp kiến trúc      | Công nghệ                                                                                  | Vai trò                                               |
|--------------------|--------------------------------------------------------------------------------------------|-------------------------------------------------------|
| **Language**       | ![Java](https://img.shields.io/badge/Java-007396?logo=openjdk&logoColor=white)             | Ngôn ngữ chủ đạo triển khai logic OOP.                |
| **Build Tool**     | ![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white)       | Quản lý dự án Multi-module và dependencies.           |
| **Frontend**       | ![JavaFX](https://img.shields.io/badge/JavaFX-4A8CFF?logo=java&logoColor=white)            | Xây dựng giao diện Desktop theo mô hình MVC.          |
| **Networking**     | ![WebSocket](https://img.shields.io/badge/WebSocket-00BFFF?logo=websocket&logoColor=white) | Truyền tải dữ liệu Real-time (Giá thầu/Thông báo).    |
| **Database**       | ![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white)             | Lưu trữ bền vững dữ liệu phiên đấu giá và người dùng. |
| **Serialization**  | ![Gson](https://img.shields.io/badge/Gson-4285F4?logo=google&logoColor=white)              | Chuyển đổi đối tượng để truyền tin qua mạng.          |
| **Boilerplate**    | ![Lombok](https://img.shields.io/badge/Lombok-9C27B0?logo=lombok&logoColor=white)          | Tối ưu mã nguồn, giảm code thừa (Clean Code).         |
| **Testing**        | ![JUnit](https://img.shields.io/badge/JUnit-25A162?logo=junit5&logoColor=white)            | Kiểm thử đơn vị (Unit Test) cho logic nghiệp vụ.      |
| **Convention**     | ![Google](https://img.shields.io/badge/Google_Java_Style-4285F4?logo=google&logoColor=white) | Tuân thủ quy chuẩn viết code đồng nhất cho cả dự án.  |

---

## 👥 Đội ngũ & Phân công nhiệm vụ (Project Roadmap)
|                                                    Thành viên                                                    | Vai trò                                       | Nhiệm vụ chính                                                                                                          |               Tiến độ                | Trạng thái |
|:----------------------------------------------------------------------------------------------------------------:|:----------------------------------------------|:------------------------------------------------------------------------------------------------------------------------|:------------------------------------:| :---: |
|                  <img src="https://github.com/hchyy.png" width="50px;"/><br />**Hồ Huyền Chi**                   | **__Trưởng nhóm__ <br> OOP design**           | • Code logic đấu giá chính <br> • Hỗ trợ design giao diện <br> • Review và Refactor code <br> • Viết tài liệu hướng dẫn | ![50%](https://geps.dev/progress/50) | 🏗️ *Processing* |
|      <img src="https://github.com/identicons/vuxgiakhanh-source.png" width="50px;"/><br />**Vũ Gia Khánh**       | **__Thành viên__ <br> Concurrency / Testing** | • Cài đặt Network và Concurrency <br> • Phát triển chức năng nâng cao <br> • Viết test                                  | ![50%](https://geps.dev/progress/60) | 🏗️ *Processing* |
|            <img src="https://github.com/thebrosaythree.png" width="50px;"/><br />**Bạch Quốc Thịnh**             | **__Thành viên__ <br> Backend**               | • Cài đặt DataBase và Backend <br> • Cài đặt Anti-Sniping                                                               | ![60%](https://geps.dev/progress/50) | 🏗️ *Processing* |
|          <img src="https://github.com/identicons/bingbongg.png" width="50px;"/><br />**Trần Thảo Nhi**           | **__Thành viên__ <br> Frontend**              | • Đảm nhiệm toàn bộ Frontend và Client <br> • Viết tài liệu hướng dẫn                                                   | ![40%](https://geps.dev/progress/40) | 🏗️ *Processing* |

---

## 📞 Liên hệ
* [Hồ Huyền Chi : Core Logic (OOP)](https://www.facebook.com/hchy07/) - `chidinhhoi1709@gmail.com`
* [Trần Thảo Nhi : Frontend](https://www.facebook.com/thao.nhi.377035) - `tthaonhi0127@gmail.com`
* [Vũ Gia Khánh : Concurrency + Testing](https://www.facebook.com/khanh.vu.416010) - `vuxgiakhanh@gmail.com`
* [Bạch Quốc Thịnh : Database + Backend](https://www.facebook.com/ven.is.me.3305) - `iamven56@gmail.com`

## 📄 License
__MIT © 2026 Group 13__
![img.png](img.png)