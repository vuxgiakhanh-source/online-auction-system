-- =================================================================
-- Integration Test Schema
-- Dùng cho Testcontainers (MySQLContainer.withInitScript)
-- Database đã được tạo bởi Testcontainers → KHÔNG dùng CREATE/USE DATABASE
-- =================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Bảng Users
CREATE TABLE IF NOT EXISTS users (
                                     id                     VARCHAR(36)  PRIMARY KEY,
                                     username               VARCHAR(50)  UNIQUE NOT NULL,
                                     password_hash          VARCHAR(255) NOT NULL,
                                     email                  VARCHAR(100) UNIQUE NOT NULL,
                                     created_at             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                                     updated_at             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     rating                 DECIMAL(3,2) DEFAULT 3.00,
                                     balance                BIGINT       DEFAULT 0,
                                     locked_balance         BIGINT       DEFAULT 0,
                                     status                 ENUM('ACTIVE','BANNED','SUSPENDED','DELETED') DEFAULT 'ACTIVE',
                                     has_ever_been_penalized BOOLEAN     DEFAULT FALSE,
                                     has_ever_been_restored  BOOLEAN     DEFAULT FALSE,
                                     suspended_at           DATETIME     NULL
);

-- 2. Bảng Sellers
CREATE TABLE IF NOT EXISTS sellers (
                                       user_id         VARCHAR(36) PRIMARY KEY,
                                       approval_status ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
                                       request_date    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                                       approved_date   TIMESTAMP   NULL,
                                       rating          INT         DEFAULT 3,
                                       FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Bảng Admins
CREATE TABLE IF NOT EXISTS admins (
                                      id            VARCHAR(36)  PRIMARY KEY,
                                      username      VARCHAR(50)  UNIQUE,
                                      password_hash VARCHAR(255),
                                      email         VARCHAR(100) UNIQUE,
                                      level         ENUM('MASTER','STAFF'),
                                      created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 4. Bảng Password Resets
CREATE TABLE IF NOT EXISTS password_resets (
                                               id         INT          AUTO_INCREMENT PRIMARY KEY,
                                               email      VARCHAR(100) NOT NULL,
                                               token      VARCHAR(255) NOT NULL UNIQUE,
                                               expires_at DATETIME     NOT NULL,
                                               created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                                               FOREIGN KEY (email) REFERENCES users(email) ON DELETE CASCADE
);

-- 5. Bảng Items
CREATE TABLE IF NOT EXISTS items (
                                     id             VARCHAR(36)  PRIMARY KEY,
                                     seller_id      VARCHAR(36)  NOT NULL,
                                     name           VARCHAR(255) NOT NULL,
                                     description    TEXT,
                                     starting_price BIGINT       NOT NULL,
                                     category_type  ENUM('ELECTRONICS','ART','VEHICLE','OTHER') DEFAULT 'OTHER',
    -- ELECTRONICS
                                     brand           VARCHAR(255) NULL,
                                     warranty_months INT          NULL,
                                     `condition`     VARCHAR(255) NULL,
    -- ART
                                     artist          VARCHAR(255) NULL,
                                     year_created    INT          NULL,
                                     medium          VARCHAR(255) NULL,
    -- VEHICLE
                                     manufacturer    VARCHAR(255) NULL,
                                     `year`          INT          NULL,
                                     mileage         DOUBLE       NULL,
                                     created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                                     FOREIGN KEY (seller_id) REFERENCES sellers(user_id) ON DELETE CASCADE
);

-- 6. Bảng Auctions
CREATE TABLE IF NOT EXISTS auctions (
                                        id                    VARCHAR(36) PRIMARY KEY,
                                        item_id               VARCHAR(36) NOT NULL,
                                        start_time            DATETIME    NOT NULL,
                                        end_time              DATETIME    NOT NULL,
                                        status                ENUM('OPEN','RUNNING','FINISHED','PAID','CANCELED') DEFAULT 'OPEN',
                                        reserve_price         BIGINT      NOT NULL DEFAULT 1,
                                        current_price         BIGINT      DEFAULT 0,
                                        current_leader_id     VARCHAR(36) DEFAULT NULL,
                                        current_highest_price BIGINT      DEFAULT 0,
                                        winning_bidder_id     VARCHAR(36) DEFAULT NULL,
                                        viewer_count          INT         DEFAULT 0,
                                        created_at            TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                                        updated_at            TIMESTAMP   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                        FOREIGN KEY (item_id)           REFERENCES items(id)       ON DELETE CASCADE,
                                        FOREIGN KEY (current_leader_id) REFERENCES users(id)       ON DELETE SET NULL,
                                        FOREIGN KEY (winning_bidder_id) REFERENCES users(id)       ON DELETE SET NULL
);

-- 7. Bảng Bid Transactions
CREATE TABLE IF NOT EXISTS bid_transactions (
                                                id          VARCHAR(36) PRIMARY KEY,
                                                auction_id  VARCHAR(36) NOT NULL,
                                                bidder_id   VARCHAR(36) NOT NULL,
                                                bid_amount  BIGINT      NOT NULL,
                                                result      ENUM('ACCEPTED','REJECTED','ACCEPTED_RESERVE_NOT_MET') NOT NULL DEFAULT 'ACCEPTED',
                                                bid_time    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                                                FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
                                                FOREIGN KEY (bidder_id)  REFERENCES users(id)    ON DELETE CASCADE
);

-- 8. Bảng User Auction Activity
CREATE TABLE IF NOT EXISTS user_auction_activity (
                                                     user_id       VARCHAR(36) NOT NULL,
                                                     auction_id    VARCHAR(36) NOT NULL,
                                                     activity_type ENUM('WATCHING','JOINED') NOT NULL,
                                                     created_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                                                     PRIMARY KEY (user_id, auction_id),
                                                     FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
                                                     FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE
);

-- 9. Bảng Auction Winners
CREATE TABLE IF NOT EXISTS auction_winners (
                                               id             VARCHAR(36) PRIMARY KEY,
                                               auction_id     VARCHAR(36) NOT NULL UNIQUE,
                                               winner_id      VARCHAR(36) NOT NULL,
                                               final_price    BIGINT      NOT NULL,
                                               deposit_paid   BIGINT      NOT NULL,
                                               payment_status ENUM('PENDING','COMPLETED','EXPIRED', 'FUNDS_HELD') DEFAULT 'PENDING',
                                               created_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                                               FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
                                               FOREIGN KEY (winner_id)  REFERENCES users(id)    ON DELETE CASCADE
);

-- 10. Bảng Second Chance Offers
CREATE TABLE IF NOT EXISTS second_chance_offers (
                                                    id           VARCHAR(36) PRIMARY KEY,
                                                    auction_id   VARCHAR(36) NOT NULL,
                                                    runner_up_id VARCHAR(36) NOT NULL,
                                                    offer_price  BIGINT      NOT NULL,
                                                    deposit_paid BIGINT      NOT NULL,
                                                    status       ENUM('PENDING','ACCEPTED','DECLINED','EXPIRED') DEFAULT 'PENDING',
                                                    deadline     DATETIME    NOT NULL,
                                                    created_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                                                    FOREIGN KEY (auction_id)   REFERENCES auctions(id) ON DELETE CASCADE,
                                                    FOREIGN KEY (runner_up_id) REFERENCES users(id)    ON DELETE CASCADE
);

-- 11. Bảng Financial Transactions
CREATE TABLE IF NOT EXISTS financial_transactions (
                                                      id               VARCHAR(36) PRIMARY KEY,
                                                      sender_id        VARCHAR(36) NOT NULL,
                                                      receiver_id      VARCHAR(36) NOT NULL,
                                                      amount           BIGINT      NOT NULL,
                                                      transaction_type ENUM(
                                                          'DEPOSIT_LOCK',
                                                          'DEPOSIT_UNLOCK',
                                                          'DEPOSIT_FORFEIT',
                                                          'PAYMENT_FROM_WINNER',
                                                          'TAX_COLLECTED',
                                                          'PAYOUT_TO_SELLER',
                                                          'REFUND_TO_WINNER',
                                                          'SECOND_CHANCE_PAYMENT'
                                                          ) NOT NULL,
                                                      auction_id  VARCHAR(36) NULL,
                                                      created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                                                      FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE SET NULL
);

-- 12. Bảng Quality Reports  ← ĐÃ SỬA
CREATE TABLE IF NOT EXISTS quality_reports (
                                               id                     VARCHAR(36)  PRIMARY KEY,
                                               auction_id             VARCHAR(36)  NOT NULL,
                                               reporter_id            VARCHAR(36)  NOT NULL,
                                               description            TEXT         NOT NULL,           -- bỏ DEFAULT ''
                                               image_urls             TEXT         NOT NULL,           -- bỏ DEFAULT '[]'
                                               status                 ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
                                               seller_refund_deadline DATETIME     NULL,
                                               refund_completed       BOOLEAN      NOT NULL DEFAULT FALSE,
                                               created_at             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                                               FOREIGN KEY (auction_id)  REFERENCES auctions(id) ON DELETE CASCADE,
                                               FOREIGN KEY (reporter_id) REFERENCES users(id)    ON DELETE CASCADE
);

-- 13. Bảng Auto Bids
CREATE TABLE IF NOT EXISTS auto_bids (
                                         user_id       VARCHAR(36) NOT NULL,
                                         auction_id    VARCHAR(36) NOT NULL,
                                         max_bid       BIGINT      NOT NULL,
                                         registered_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         PRIMARY KEY (user_id, auction_id),
                                         FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
                                         FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE
);

CREATE INDEX idx_auto_bids_auction_id ON auto_bids(auction_id);

SET FOREIGN_KEY_CHECKS = 1;