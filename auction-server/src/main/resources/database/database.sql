-- =================================================================
-- 1. CHÚ THÍCH
-- Dự án: Online Auction System (Hệ thống đấu giá trực tuyến)
-- Mục đích: Khởi tạo cơ sở dữ liệu và các bảng cần thiết
-- =================================================================

-- =================================================================
-- 2. KHỞI TẠO VÀ CHỌN CƠ SỞ DỮ LIỆU
-- =================================================================
DROP DATABASE IF EXISTS auction_db;
CREATE DATABASE auction_db;
USE auction_db;

-- 3. TẠO BẢNG (DDL)
-- =================================================================

-- 1. Bảng Users (Mặc định ai tạo tài khoản xong cũng là Bidder)
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    rating INT DEFAULT 3
);

-- 2. Bảng Sellers (Dùng để lưu yêu cầu làm người bán và thông tin shop)
CREATE TABLE sellers (
    user_id VARCHAR(36) PRIMARY KEY, -- Vừa làm Khóa chính, vừa làm Khóa ngoại
    -- Cột trạng thái để Admin duyệt
    approval_status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',

    request_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approved_date TIMESTAMP NULL, -- Bỏ trống, khi nào Admin duyệt mới cập nhật giờ vào đây
    rating INT DEFAULT 3,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


-- Bảng lưu trữ thông tin Admin
CREATE TABLE admins (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE,
    password_hash VARCHAR(255),
    email VARCHAR(100) UNIQUE,
    level ENUM('MASTER', 'STAFF'),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE password_resets (
     id INT AUTO_INCREMENT PRIMARY KEY,
     email VARCHAR(100) NOT NULL,
     token VARCHAR(255) NOT NULL UNIQUE,
     expires_at DATETIME NOT NULL,
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     FOREIGN KEY (email) REFERENCES users(email) ON DELETE CASCADE
);

-- Bảng lưu trữ thông tin sản phẩm được đăng lên
CREATE TABLE items (
     id VARCHAR(36) PRIMARY KEY,
     seller_id VARCHAR(36) NOT NULL,
     name VARCHAR(255) NOT NULL,
     description TEXT,
     starting_price BIGINT NOT NULL,
     category_type ENUM('ELECTRONICS', 'ART', 'VEHICLE', 'OTHER') DEFAULT 'OTHER',
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     FOREIGN KEY (seller_id) REFERENCES sellers(user_id) ON DELETE CASCADE
);

-- Bảng quản lý các phiên đấu giá
CREATE TABLE auctions (
     id VARCHAR(36) PRIMARY KEY,
     item_id VARCHAR(36) NOT NULL,
     start_time DATETIME NOT NULL,
     end_time DATETIME NOT NULL,
     status ENUM('OPEN', 'CLOSED', 'CANCELLED') DEFAULT 'OPEN',
     current_highest_price BIGINT DEFAULT 0,
     winning_bidder_id VARCHAR(36) DEFAULT NULL,
     FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
     FOREIGN KEY (winning_bidder_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Bảng lưu trữ lịch sử đặt giá (Bid) của người dùng
CREATE TABLE bid_transactions (
     id VARCHAR(36) PRIMARY KEY,
     auction_id VARCHAR(36) NOT NULL,
     bidder_id VARCHAR(36) NOT NULL,
     bid_amount BIGINT NOT NULL,
     bid_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
     FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO admins (id, username, password_hash, email, level)
VALUES (
    UUID(), 
    'superadmin', 
    'chuoi_ma_hoa_cua_mat_khau', -- Cần thay thế bằng mã hash thực tế từ code của bạn
    'system@aution.internal', 
    'MASTER ADMIN'
);
