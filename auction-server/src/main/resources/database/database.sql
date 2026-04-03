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

-- Bảng lưu trữ thông tin người dùng hệ thống
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'SELLER', 'BIDDER') NOT NULL DEFAULT 'BIDDER',
    email VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bảng lưu trữ thông tin sản phẩm được đăng lên
CREATE TABLE items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    seller_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    starting_price DECIMAL(15, 2) NOT NULL,
    category_type ENUM('ELECTRONICS', 'ART', 'VEHICLE', 'OTHER') DEFAULT 'OTHER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Bảng quản lý các phiên đấu giá
CREATE TABLE auctions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    item_id INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status ENUM('OPEN', 'CLOSED', 'CANCELLED') DEFAULT 'OPEN',
    current_highest_price DECIMAL(15, 2) DEFAULT 0.00,
    winning_bidder_id INT DEFAULT NULL,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (winning_bidder_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Bảng lưu trữ lịch sử đặt giá (Bid) của người dùng
CREATE TABLE bid_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    auction_id INT NOT NULL,
    bidder_id INT NOT NULL,
    bid_amount DECIMAL(15, 2) NOT NULL,
    bid_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE
);