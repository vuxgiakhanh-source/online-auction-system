# 🤝 Trách Nhiệm & Tiến Độ Thành Viên

### 🏗️ Phase 1 — Project Foundation & Architecture Design
**`24/03 – 30/03/2026`**

> Khởi động dự án, thống nhất kiến trúc, thiết lập môi trường phát triển và phân công nhiệm vụ.

**🎯 Mục tiêu:** Đặt nền tảng vững chắc về kiến trúc, database schema, và OOP domain model trước khi bắt đầu implementation.

| Công việc                                      | Thành viên   | Chi tiết                                                      |
|------------------------------------------------|--------------|---------------------------------------------------------------|
| Lên ý tưởng, họp khởi động, phân công nhiệm vụ | Cả nhóm      | Brainstorm tính năng, phân tích yêu cầu, lên kế hoạch sprint  |
| Thiết kế UML Diagrams                          | **Chi**      | Class Diagram, Sequence Diagram, Use-case Diagram             |
| Thiết kế Database Schema                       | **Thịnh**    | Thiết kế bảng, quan hệ giữa các entity                        |
| Thiết kế domain model & lớp exception          | **Chi**      | Entity hierarchy, exception taxonomy, OOP contract            |
| Khởi tạo Multi-module Maven project            | **Khánh**    | Cấu trúc `auction-common`, `auction-server`, `auction-client` |
| Phác thảo wireframe UI                         | **Nhi**      | Mockup các màn hình chính: Login, Dashboard, Auction Detail   |

**Thành phẩm:**
- [x] UML Diagrams hoàn chỉnh (Class, Sequence, Use-case)
- [x] Database schema được duyệt
- [x] Kiến trúc Layered + Event-Driven thống nhất toàn nhóm
- [x] Maven multi-module project được khởi tạo

---

### ⚙️ Phase 2 — Core Domain & Data Layer Implementation
**`31/03 – 13/04/2026`**

> Xây dựng toàn bộ nền tảng logic nghiệp vụ, tầng dữ liệu và cơ sở hạ tầng mạng.

**🎯 Mục tiêu:** Hoàn thiện domain model, DAO layer, service layer cốt lõi, và thiết lập kết nối Client-Server qua WebSocket.

| Công việc                                  | Thành viên   | Chi tiết                                                                          |
|--------------------------------------------|--------------|-----------------------------------------------------------------------------------|
| Cài đặt Database & MySQL connection        | **Thịnh**    | Schema migration, connection pooling, `DatabaseConnection` Singleton              |
| Xây dựng DAO Layer                         | **Thịnh**    | `AuctionDAO`, `UserDAO`, `ItemDAO`, `BidDAO`, `SecondChanceOfferDAO`...           |
| Inject DAO vào Server                      | **Thịnh**    | Dependency wiring giữa DAO và Service layer                                       |
| Business log & Database log                | **Thịnh**    | Log nghiệp vụ và log database phân tách rõ ràng                                   |
| Core Auction Logic (OOP + Design Patterns) | **Chi**      | `Auction`, State Machine (5 states), `AuctionManager` Singleton                   |
| User & Item Factory                        | **Chi**      | `UserFactory`, `ItemFactory`, hierarchy kế thừa đầy đủ                            |
| Service Layer cốt lõi                      | **Chi**      | `AccountService`, `AuctionService`, `BidService`, `PaymentService`                |
| Observer Pattern (Notification Engine)     | **Chi**      | `AuctionObserver` interface + `BidderObserver`, `SellerObserver`, `AdminObserver` |
| WebSocket Server & Packet Protocol         | **Khánh**    | `AuctionWebSocketServer`, `PacketRouter`, `PacketCodec` (Gson)                    |
| Xử lý đa luồng & đồng bộ hóa               | **Khánh**    | `AuctionLockRegistry`, per-auction locking, loại bỏ Race Condition                |
| Màn hình Login & Authentication UI         | **Nhi**      | JavaFX Login screen, form validation, session flow                                |
| Màn hình Dashboard / Auction List          | **Nhi**      | Danh sách phiên đấu giá, filter, trạng thái                                       |

**Thành phẩm:**
- [x] Domain model hoàn chỉnh với 5 Design Patterns đã triển khai
- [x] DAO Layer kết nối MySQL ổn định
- [x] WebSocket Client-Server giao tiếp được
- [ ] Màn hình Login và Dashboard hoạt động cơ bản __(Chưa hoàn thiện)__

---

### 🚀 Phase 3 — Advanced Features & Business Logic Completion
**`14/04 – 27/04/2026`**

> Tích hợp các tính năng nâng cao, hoàn thiện toàn bộ quy trình nghiệp vụ từ đấu giá đến hậu mãi.

**🎯 Mục tiêu:** Đưa sản phẩm từ "hoạt động được" lên "hoạt động đúng" với đầy đủ tính năng thực tế.

| Công việc                           | Thành viên   | Chi tiết                                                      |
|-------------------------------------|--------------|---------------------------------------------------------------|
| Thuật toán Anti-Sniping             | **Thịnh**    | Tự động gia hạn thời gian khi có bid vào giây cuối            |
| Scheduler tự động hóa phiên đấu giá | **Thịnh**    | Điều phối `OPEN → RUNNING → FINISHED` theo thời gian thực     |
| Notification Layer                  | **Thịnh**    | Hệ thống thông báo push đến client khi online/offline         |
| Auto-Bidding (Đấu giá tự động)      | **Khánh**    | `AutoBidStrategy`, thiết lập max price & step, trigger logic  |
| Realtime Price Chart                | **Khánh**    | Line Chart lịch sử đấu giá, cập nhật realtime theo stream     |
| Network Log                         | **Khánh**    | Log lưu lượng mạng, phân tách theo packet type                |
| Escrow Payment & Wallet             | **Chi**      | `PaymentService`, `SystemBank`, `WalletService`, escrow flow  |
| Second Chance Offer                 | **Chi**      | Tự động gửi đề nghị cho runner-up khi winner không thanh toán |
| Quality Report & Refund             | **Chi**      | `QualityReportService`, admin approval, hoàn tiền tự động     |
| Rating Service                      | **Chi**      | Tự động cập nhật điểm đánh giá, cơ chế ban tài khoản          |
| Smart Chatbot                       | **Chi**      | `ChatbotProvider` Singleton, Relevance Scoring, FAQ từ JSON   |
| Màn hình Bidding Detail             | **Nhi**      | Chi tiết phiên đấu giá, realtime bid list, đặt giá            |
| Màn hình Wallet & Payment           | **Nhi**      | Quản lý ví, lịch sử giao dịch, xác nhận thanh toán            |
| Màn hình Seller — Đăng sản phẩm     | **Nhi**      | Form tạo phiên đấu giá, quản lý item                          |
| Màn hình Admin Dashboard            | **Nhi**      | Duyệt Quality Report, quản lý tài khoản                       |

**Thành phẩm:**
- [x] Toàn bộ quy trình đấu giá end-to-end hoạt động đầy đủ
- [x] Anti-Sniping, Auto-Bid, Escrow Payment, Second Chance Offer triển khai xong
- [x] Chatbot tích hợp hoàn chỉnh
- [x] UI đủ màn hình cho cả 3 role: Bidder, Seller, Admin
- [x] Màn hình Login và Dashboard hoạt động cơ bản

---

### 🧪 Phase 4 — Testing & Quality Assurance
**`28/04 – 11/05/2026`**

> Kiểm thử toàn diện để đảm bảo tính đúng đắn, an toàn và ổn định của hệ thống dưới tải đồng thời.

**🎯 Mục tiêu:** Đạt coverage cao trên các tầng quan trọng; phát hiện và xử lý bug trước integration.

| Công việc                           | Thành viên      | Chi tiết                                                                                                  |
|-------------------------------------|-----------------|-----------------------------------------------------------------------------------------------------------|
| Unit Tests — Domain & State Machine | **Chi**         | `AuctionTest`, `AuctionStateMachineTest`, `AuctionWinnerTest`, `SecondChanceOfferTest`                    |
| Unit Tests — Strategy & Observer    | **Chi**         | `StandardBidStrategyTest`, `AutoBidStrategyTest`, `BidStrategyContractTest`, Observer contract tests      |
| Unit Tests — Service Layer          | **Chi**         | `BidServiceTest`, `PaymentServiceTest`, `AuctionServiceTest`, `WalletServiceTest`, `RatingServiceTest`... |
| Unit Tests — Factory & Singleton    | **Chi**         | `UserFactoryTest`, `ItemFactoryTest`, `AuctionManagerTest`, `SystemBankTest`                              |
| Integration Tests — Luồng nghiệp vụ | **Chi + Khánh** | End-to-end auction flow, payment flow, chatbot flow                                                       |
| Unit Tests — Concurrency            | **Khánh**       | `AuctionLockRegistryTest`, race condition scenarios, deadlock prevention                                  |
| Network & Integration Tests         | **Khánh**       | Packet serialization, WebSocket handshake, handler routing                                                |
| System Tests                        | **Khánh**       | Multi-client concurrent bidding simulation, stress test                                                   |
| UI/UX Polish & Bug Fixes            | **Nhi**         | Sửa layout, responsive, xử lý edge case trên giao diện                                                    |

**Thành phẩm:**
- [x] **40+ test classes** với **hơn 2000+ unit tests passed**
- [x] 100% tests chạy không cần database thật (Mockito mock toàn bộ)
- [x] Zero known critical bugs trên core auction flow
- [x] CI pipeline xanh ổn định

---

### 🔗 Phase 5 — Integration, CI/CD & Containerization
**`12/05 – 18/05/2026`**

> Tích hợp toàn bộ module, tự động hóa quy trình build/test và đóng gói hệ thống.

**🎯 Mục tiêu:** Đảm bảo toàn bộ hệ thống chạy ổn định end-to-end; thiết lập pipeline CI/CD cho production-readiness.

| Công việc                         | Thành viên   | Chi tiết                                                        |
|-----------------------------------|--------------|-----------------------------------------------------------------|
| GitHub Actions CI Pipeline        | **Khánh**    | Auto build & test trên mỗi push/PR; badge trạng thái            |
| Docker & Docker Compose           | **Khánh**    | Containerize Server + MySQL; `docker-compose.yml` cho local dev |
| Animation cho giao diện Client    | **Thịnh**    | Transition, loading animation                                   |
| Notification Layer hoàn thiện     | **Thịnh**    | Offline notification queue, retry mechanism                     |
| Full System Integration Test      | **Khánh**    | End-to-end test toàn luồng từ Login → Bid → Payment → Rating    |
| Code review & Refactor toàn dự án | **Chi**      | Chuẩn hóa Google Java Style, loại bỏ code smell                 |

**Thành phẩm:**
- [x] CI/CD pipeline hoạt động tự động trên GitHub Actions
- [x] Docker build thành công
- [x] Codebase clean, đồng nhất coding style toàn dự án

---

### 📝 Phase 6 — Documentation, Polish & Demo Preparation
**`19/05 – 30/05/2026`**

> Hoàn thiện tài liệu kỹ thuật, chuẩn bị cho demo và vấn đáp phản biện.

**🎯 Mục tiêu:** Sản phẩm ở trạng thái trình bày được — tài liệu đầy đủ, demo mượt mà, sẵn sàng phản biện kỹ thuật.

| Công việc                   | Thành viên   | Chi tiết                                                             |
|-----------------------------|--------------|----------------------------------------------------------------------|
| Viết README hoàn chỉnh      | **Chi**      | Architecture, Tech Stack, Installation guide, Testing, Team section  |
| Viết báo cáo dự án          | **Chi**      | Tài liệu kỹ thuật đầy đủ, phân tích thiết kế, kết quả đạt được       |
| Hoàn thiện UML Diagrams     | **Chi**      | Cập nhật Class/Sequence/Use-case Diagram theo implementation thực tế |
| Quay demo & chụp screenshot | **Cả nhóm**  | GIF demo realtime bidding, screenshot các màn hình chính             |
| Chuẩn bị slide thuyết trình | **Cả nhóm**  | Slide demo, kiến trúc hệ thống, kết quả testing                      |
| Rehearsal demo & vấn đáp    | **Cả nhóm**  | `26/05 – 30/05`: luyện tập trình bày, chuẩn bị câu hỏi phản biện     |

**Thành phẩm:**
- [x] README & báo cáo hoàn chỉnh, chuyên nghiệp
- [x] Demo chạy ổn định, có GIF minh họa
- [x] Toàn nhóm sẵn sàng cho buổi vấn đáp
