-- Migration: thêm bảng account_bans cho DB đã tồn tại (không tạo lại từ schema.sql).
-- Chạy: mysql -u root -p auction_db < auction-server/src/main/resources/database/migrations/001_account_bans.sql

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

CREATE INDEX idx_account_bans_user_active ON account_bans(user_id, unbanned_at);
CREATE INDEX idx_account_bans_banned_at   ON account_bans(banned_at);
