-- 1. Chỉ định sử dụng database auction_db
USE auction_db;

-- 2. Xóa các bảng cũ nếu đã lỡ tạo lỗi (để làm lại từ đầu cho sạch)
DROP TABLE IF EXISTS BidTransactions;
DROP TABLE IF EXISTS Auctions;
DROP TABLE IF EXISTS Items;
DROP TABLE IF EXISTS Users;

-- 3. Tạo bảng Users
CREATE TABLE Users (
    user_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('BIDDER', 'SELLER', 'ADMIN') NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Tạo bảng Items
CREATE TABLE Items (
    item_id VARCHAR(50) PRIMARY KEY,
    seller_id VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    starting_price DECIMAL(15, 2) NOT NULL,
    category ENUM('ELECTRONICS', 'ART', 'VEHICLE', 'OTHER') NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (seller_id) REFERENCES Users(user_id) ON DELETE CASCADE
);

-- 5. Tạo bảng Auctions
CREATE TABLE Auctions (
    auction_id VARCHAR(50) PRIMARY KEY,
    item_id VARCHAR(50) NOT NULL UNIQUE,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    current_highest_bid DECIMAL(15, 2) DEFAULT 0.00,
    winning_bidder_id VARCHAR(50),
    status ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') DEFAULT 'OPEN',
    FOREIGN KEY (item_id) REFERENCES Items(item_id) ON DELETE CASCADE,
    FOREIGN KEY (winning_bidder_id) REFERENCES Users(user_id) ON DELETE SET NULL
);

-- 6. Tạo bảng BidTransactions
CREATE TABLE BidTransactions (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    auction_id VARCHAR(50) NOT NULL,
    bidder_id VARCHAR(50) NOT NULL,
    bid_amount DECIMAL(15, 2) NOT NULL,
    bid_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id) REFERENCES Auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id) REFERENCES Users(user_id) ON DELETE CASCADE
);

-- 7. Tạo Index (Chỉ mục)
CREATE INDEX idx_auction_bid_time ON BidTransactions(auction_id, bid_time);