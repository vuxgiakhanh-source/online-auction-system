![Logo](OmniBid.png)
# 💸 OminiBid - Hệ thống đấu giá Online 💸

## 🧾 Mục lục
* [Giới thiệu](#-giới-thiệu)


* [Đặc điểm kĩ thuật nổi bật](#-đặc-điểm-kĩ-thuật-nổi-bật)


* [Chức năng hệ thống](#-chức-năng-hệ-thống)


* [Hướng dẫn cài đặt](#-hướng-dẫn-cài-đặt-later)


* [Cách sử dụng](#-cách-sử-dụng-later)


* [Công nghệ & Công cụ sử dụng](#-công-nghệ--công-cụ-sử-dụng)


* [Đội ngũ & Phân công nhiệm vụ (Project Roadmap)](#-đội-ngũ--phân-công-nhiệm-vụ-project-roadmap)


* [Liên hệ](#-liên-hệ)


___

## 🚀 Giới thiệu
**OmniBid** là một nền tảng đấu giá trực tuyến mạnh mẽ và hiện đại, 
được xây dựng để cung cấp một môi trường giao dịch minh bạch, 
công bằng và cập nhật theo thời gian thực. Hệ thống cho phép 
người bán (Seller) đăng tải sản phẩm và người mua (Bidder) 
tham gia đấu thầu cạnh tranh để xác định giá trị thực của tài 
sản thông qua cơ chế thị trường.  
Dự án tập trung giải quyết các bài toán phức tạp trong hệ thống 
phân tán như xử lý tranh chấp dữ liệu khi đấu giá đồng thời, tối 
ưu hóa trải nghiệm người dùng qua kết nối Socket và áp dụng các 
mẫu thiết kế (Design Patterns) để đảm bảo khả năng mở rộng hệ thống.
> Add ảnh demo mô phỏng ứng dụng hoạt động

---

## ✨ Đặc điểm kĩ thuật nổi bật
Hệ thống được phát triển với các tiêu chuẩn kỹ thuật:
* __Real-time Engine__: Sử dụng mô hình ___Observer Pattern___ kết hợp với 
___Socket___ để cập nhật biến động giá ngay lập tức tới tất cả các client 
mà không cần tải lại trang.


* __Concurrency Control__: Giải quyết triệt để các vấn đề Lost Update 
và Race Condition trong kịch bản nhiều người cùng đặt giá tại một 
mili giây.


* __Cấu trúc hướng đối tượng (OOP)__: Áp dụng chặt chẽ 4 nguyên lý OOP 
___(Đóng gói, Kế thừa, Đa hình, Trừu tượng)___ cùng các mẫu thiết kế 
___Factory Method, Singleton, Strategy và Observer___ để quản lý 
logic nghiệp vụ phức tạp.


* __Kiến trúc MVC Phân tầng:__ Tách biệt hoàn toàn giao diện (Client side) 
và logic xử lý dữ liệu (Server side) qua ___mô hình Client-Server___.

---

## 👷 Chức năng hệ thống
### Chức năng cốt lõi
* __Quản lý đa vai trò__: Phân quyền chi tiết cho Bidder (Người mua), Seller 
(người bán) và Admin (Quản trị viên).


* __Phiên đấu giá linh hoạt__: Tự động mở/đóng phiên theo thời gian thực 
nhờ vào Scheduler, quản lý trạng thái sản phẩm từ khi bắt đầu đến khi thanh toán, 
cung ấp cơ chế __báo cáo__ và __hoàn trả tiền__ cho người dùng nếu sản phẩm không đảm
bảo về mặt chất lượng.


* __Giao diện trực quan__: Hỗ trợ màn hình theo dõi đấu giá trực tiếp, danh sách sản 
pẩm và biểu đồ biến động giá.
### Chức năng nâng cao
* __Auto-Bidding (Đấu giá tự động)__: Cho phép người dùng thiết lập mức giá tối đa
và bước giá để hệ thống tự động trả giá thay thế khi có đối thủ mới.


* __Thuật toán Anti-Sniping__: Tự động gia hạn thơ gian kết thúc nếu có lượt đặt giá
phát sinh vào những giây cuối cùng, đảm bảo tính công bằng cho người dùng.


* __Trực quan hóa dữ liệu__: Hiển thị biểu đồ đường (Line Chart) biểu diễn lịch sử đấu
giá theo thời gian thực.

---

## 💡 Hướng dẫn cài đặt (Later)

---

## 🔎 Cách sử dụng (Later)

---

## ⚙️ Công nghệ & Công cụ sử dụng
* __Ngôn ngữ__: Java


* __Giao diện__: JavaFx (MVC Pattern)


* __Quản lý dự án__: Maven


* __Code convention__: Google Java Style Guide


* __Kiểm thử__: (Later)

---

## 👥 Đội ngũ & Phân công nhiệm vụ (Project Roadmap)
|                                                    Thành viên                                                    | Vai trò                                       | Nhiệm vụ chính                                                                                                          |               Tiến độ                | Trạng thái |
|:----------------------------------------------------------------------------------------------------------------:|:----------------------------------------------|:------------------------------------------------------------------------------------------------------------------------|:------------------------------------:| :---: |
|                  <img src="https://github.com/hchyy.png" width="50px;"/><br />**Hồ Huyền Chi**                   | **__Trưởng nhóm__ <br> OOP design**           | • Code logic đấu giá chính <br> • Hỗ trợ design giao diện <br> • Review và Refactor code <br> • Viết tài liệu hướng dẫn | ![50%](https://geps.dev/progress/50) | 🏗️ *Processing* |
|      <img src="https://github.com/identicons/vuxgiakhanh-source.png" width="50px;"/><br />**Vũ Gia Khánh**       | **__Thành viên__ <br> Concurrency / Testing** | • Cài đặt Network và Concurrency <br> • Phát triển chức năng nâng cao <br> • Viết test                                  | ![50%](https://geps.dev/progress/60) | 🏗️ *Processing* |
|            <img src="https://github.com/thebrosaythree.png" width="50px;"/><br />**Bạch Quốc Thịnh**             | **__Thành viên__ <br> Backend**               | • Cài đặt DataBase và Backend <br> • Tích hợp API <br> • Cài đặt Anti-Snipping                                          | ![60%](https://geps.dev/progress/50) | 🏗️ *Processing* |
|          <img src="https://github.com/identicons/bingbongg.png" width="50px;"/><br />**Trần Thảo Nhi**           | **__Thành viên__ <br> Frontend**              | • Đảm nhiệm toàn bộ Frontend và Client <br> • Viết tài liệu hướng dẫn                                                   | ![40%](https://geps.dev/progress/40) | 🏗️ *Processing* |

---

## 📞 Liên hệ
* [Hồ Huyền Chi : Core Logic (OOP)](https://www.facebook.com/hchy07/) - chidinhhoi1709@gmail.com
* [Trần Thảo Nhi : Frontend](https://www.facebook.com/thao.nhi.377035) - tthaonhi0127@gmail.com
* [Vũ Gia Khánh : Concurrency + Testing](https://www.facebook.com/khanh.vu.416010) - vuxgiakhanh@gmail.com
* [Bạch Quốc Thịnh : Database + Backend](https://www.facebook.com/ven.is.me.3305) - iamven56@gmail.com 

