-- =================================================================
-- Seed data — chạy sau schema.sql (DB đã được USE)
-- =================================================================

INSERT INTO admins (id, username, password_hash, email, level)
VALUES (
    UUID(),
    'superadmin',
    -- SHA-256 hash của 'admin123' — đổi password ngay sau lần đầu login!
    '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
    'system@auction.internal',
    'MASTER'
);
