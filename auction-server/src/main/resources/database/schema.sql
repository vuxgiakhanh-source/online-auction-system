-- =================================================================
-- Online Auction System — Canonical schema (DDL + indexes)
--
-- Dùng chung cho:
--   • Integration tests (Testcontainers): database/schema.sql
--   • Docker: docker-entrypoint-initdb.d (cùng seed.sql)
--   • MySQL local: import schema.sql rồi seed.sql sau khi CREATE DATABASE
--
-- Không chứa DROP/CREATE DATABASE — DB phải đã tồn tại và được USE trước khi chạy.
-- =================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Users
CREATE TABLE IF NOT EXISTS users (
    id                      VARCHAR(36)  PRIMARY KEY,
    username                VARCHAR(50)  UNIQUE NOT NULL,
    password_hash           VARCHAR(255) NOT NULL,
    email                   VARCHAR(100) UNIQUE NOT NULL,
    created_at              TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    rating                  DECIMAL(3,2) DEFAULT 3.00,
    balance                 BIGINT       DEFAULT 0,
    locked_balance          BIGINT       DEFAULT 0,
    status                  ENUM('ACTIVE', 'BANNED', 'SUSPENDED', 'DELETED') DEFAULT 'ACTIVE',
    has_ever_been_penalized BOOLEAN      DEFAULT FALSE,
    times_restored          INT          DEFAULT 0,
    suspended_at            DATETIME     NULL
);

-- 2. Sellers
CREATE TABLE IF NOT EXISTS sellers (
    user_id         VARCHAR(36) PRIMARY KEY,
    approval_status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    request_date    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    approved_date   TIMESTAMP   NULL,
    rating          INT         DEFAULT 3,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Admins
CREATE TABLE IF NOT EXISTS admins (
    id            VARCHAR(36)  PRIMARY KEY,
    username      VARCHAR(50)  UNIQUE,
    password_hash VARCHAR(255),
    email         VARCHAR(100) UNIQUE,
    level         ENUM('MASTER', 'STAFF'),
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 4. Account bans (admin / system lock audit)
CREATE TABLE IF NOT EXISTS account_bans (
    id                      VARCHAR(36)  PRIMARY KEY,
    user_id                 VARCHAR(36)  NOT NULL,
    admin_id                VARCHAR(36)  NULL,
    banned_by_username      VARCHAR(50)  NOT NULL,
    reason                  ENUM(
        'FRAUD',
        'LOW_RATING',
        'POLICY_VIOLATION',
        'SELLER_REFUND_DEFAULT',
        'OTHER',
        'SYSTEM_AUTO'
    ) NOT NULL,
    note                    TEXT         NULL,
    banned_at               TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    unbanned_at             TIMESTAMP    NULL,
    unbanned_by_admin_id    VARCHAR(36)  NULL,
    unbanned_by_username    VARCHAR(50)  NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE SET NULL,
    FOREIGN KEY (unbanned_by_admin_id) REFERENCES admins(id) ON DELETE SET NULL
);

-- 5. Password resets
CREATE TABLE IF NOT EXISTS password_resets (
    id         INT          AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(100) NOT NULL,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at DATETIME     NOT NULL,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (email) REFERENCES users(email) ON DELETE CASCADE
);

-- 5. Items
CREATE TABLE IF NOT EXISTS items (
    id              VARCHAR(36)  PRIMARY KEY,
    seller_id       VARCHAR(36)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    starting_price  BIGINT       NOT NULL,
    category_type   ENUM('ELECTRONICS', 'ART', 'VEHICLE', 'OTHER') DEFAULT 'OTHER',
    brand           VARCHAR(255) NULL,
    warranty_months INT          NULL,
    `condition`     VARCHAR(255) NULL,
    artist          VARCHAR(255) NULL,
    year_created    INT          NULL,
    medium          VARCHAR(255) NULL,
    manufacturer    VARCHAR(255) NULL,
    `year`          INT          NULL,
    mileage         DOUBLE       NULL,
    image_urls      TEXT         NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (seller_id) REFERENCES sellers(user_id) ON DELETE CASCADE
);

-- 6. Auctions
CREATE TABLE IF NOT EXISTS auctions (
    id                    VARCHAR(36) PRIMARY KEY,
    item_id               VARCHAR(36) NOT NULL,
    start_time            DATETIME    NOT NULL,
    end_time              DATETIME    NOT NULL,
    status                ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') DEFAULT 'OPEN',
    reserve_price         BIGINT      NOT NULL DEFAULT 1,
    current_price         BIGINT      DEFAULT 0,
    current_leader_id     VARCHAR(36) DEFAULT NULL,
    current_highest_price BIGINT      DEFAULT 0,
    winning_bidder_id     VARCHAR(36) DEFAULT NULL,
    viewer_count          INT         DEFAULT 0,
    created_at            TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id)           REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (current_leader_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (winning_bidder_id) REFERENCES users(id) ON DELETE SET NULL
);

-- 7. Bid transactions
CREATE TABLE IF NOT EXISTS bid_transactions (
    seq        BIGINT       AUTO_INCREMENT UNIQUE,
    id         VARCHAR(36)  PRIMARY KEY,
    auction_id VARCHAR(36)  NOT NULL,
    bidder_id  VARCHAR(36)  NOT NULL,
    bid_amount BIGINT       NOT NULL,
    result     ENUM('ACCEPTED', 'REJECTED', 'ACCEPTED_RESERVE_NOT_MET', 'CANCELLED_BY_LEAVE')
                            NOT NULL DEFAULT 'ACCEPTED',
    bid_time   TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id)  REFERENCES users(id)    ON DELETE CASCADE
);

-- 8. User auction activity
CREATE TABLE IF NOT EXISTS user_auction_activity (
    user_id       VARCHAR(36) NOT NULL,
    auction_id    VARCHAR(36) NOT NULL,
    activity_type ENUM('WATCHING', 'JOINED') NOT NULL,
    created_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, auction_id),
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE
);

-- 9. Auction winners
CREATE TABLE IF NOT EXISTS auction_winners (
    id                       VARCHAR(36)  PRIMARY KEY,
    auction_id               VARCHAR(36)  NOT NULL UNIQUE,
    winner_id                VARCHAR(36)  NOT NULL,
    final_price              BIGINT       NOT NULL,
    deposit_paid             BIGINT       NOT NULL,
    payment_status           ENUM('PENDING', 'COMPLETED', 'EXPIRED', 'FUNDS_HELD', 'ITEM_RECEIVED')
                                                    DEFAULT 'PENDING',
    payment_deadline         DATETIME     NULL,
    confirm_receipt_deadline DATETIME     NULL,
    report_deadline          DATETIME     NULL,
    is_second_offer          TINYINT(1)   NOT NULL DEFAULT 0,
    created_at               TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
    FOREIGN KEY (winner_id)  REFERENCES users(id)    ON DELETE CASCADE
);

-- 10. Second chance offers
CREATE TABLE IF NOT EXISTS second_chance_offers (
    id           VARCHAR(36) PRIMARY KEY,
    auction_id   VARCHAR(36) NOT NULL,
    runner_up_id VARCHAR(36) NOT NULL,
    offer_price  BIGINT      NOT NULL,
    deposit_paid BIGINT      NOT NULL,
    status       ENUM('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED') DEFAULT 'PENDING',
    deadline     DATETIME    NOT NULL,
    created_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id)   REFERENCES auctions(id) ON DELETE CASCADE,
    FOREIGN KEY (runner_up_id) REFERENCES users(id)    ON DELETE CASCADE
);

-- 11. Financial transactions
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

-- 12. Quality reports
CREATE TABLE IF NOT EXISTS quality_reports (
    id                     VARCHAR(36)  PRIMARY KEY,
    auction_id             VARCHAR(36)  NOT NULL,
    reporter_id            VARCHAR(36)  NOT NULL,
    description            TEXT         NOT NULL,
    image_urls             TEXT         NOT NULL,
    status                 ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    seller_refund_deadline DATETIME     NULL,
    refund_completed       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id)  REFERENCES auctions(id) ON DELETE CASCADE,
    FOREIGN KEY (reporter_id) REFERENCES users(id)    ON DELETE CASCADE
);

-- 13. Auto bids
CREATE TABLE IF NOT EXISTS auto_bids (
    user_id       VARCHAR(36) NOT NULL,
    auction_id    VARCHAR(36) NOT NULL,
    max_bid       BIGINT      NOT NULL,
    registered_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, auction_id),
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE
);

-- 14. Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id                VARCHAR(36)  PRIMARY KEY,
    user_id           VARCHAR(36)  NOT NULL,
    auction_id        VARCHAR(36)  NULL,
    notification_type VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    title             VARCHAR(255) NOT NULL,
    body              TEXT         NOT NULL,
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE SET NULL
);

-- =================================================================
-- Indexes
-- =================================================================

CREATE INDEX idx_items_name ON items(name);

CREATE INDEX idx_bid_tx_auction_result_amount ON bid_transactions(auction_id, result, bid_amount DESC);
CREATE INDEX idx_bid_tx_auction_bidder_result  ON bid_transactions(auction_id, bidder_id, result);

CREATE INDEX idx_auctions_status ON auctions(status);

CREATE INDEX idx_auction_winners_winner_status ON auction_winners(winner_id, payment_status);

CREATE INDEX idx_fin_tx_sender_auction_type ON financial_transactions(sender_id, auction_id, transaction_type);

CREATE INDEX idx_auto_bids_auction_id     ON auto_bids(auction_id);
CREATE INDEX idx_notifications_user_id    ON notifications(user_id);
CREATE INDEX idx_notifications_user_read  ON notifications(user_id, is_read);

CREATE INDEX idx_quality_reports_status ON quality_reports(status);

CREATE INDEX idx_account_bans_user_active ON account_bans(user_id, unbanned_at);
CREATE INDEX idx_account_bans_banned_at   ON account_bans(banned_at);

SET FOREIGN_KEY_CHECKS = 1;
