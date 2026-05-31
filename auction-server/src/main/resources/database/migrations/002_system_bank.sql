-- Migration: system bank admin screen support for existing databases.
-- Run manually on databases that were created before schema.sql contained system_bank.
-- Example:
--   mysql -u root -p auction_db < auction-server/src/main/resources/database/migrations/002_system_bank.sql

CREATE TABLE IF NOT EXISTS system_bank (
    id             VARCHAR(36) PRIMARY KEY,
    total_balance  BIGINT      NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

ALTER TABLE financial_transactions
    MODIFY transaction_type ENUM(
        'DEPOSIT_LOCK',
        'DEPOSIT_UNLOCK',
        'DEPOSIT_FORFEIT',
        'PAYMENT_FROM_WINNER',
        'TAX_COLLECTED',
        'PAYOUT_TO_SELLER',
        'REFUND_TO_WINNER',
        'SECOND_CHANCE_PAYMENT'
    ) NOT NULL;

-- Backfill seller payout rows for auctions already completed before this audit was added.
INSERT INTO financial_transactions (
    id, sender_id, receiver_id, amount, transaction_type, auction_id
)
SELECT
    UUID(),
    'SYSTEM_BANK',
    completed.seller_id,
    completed.payout_amount,
    'PAYOUT_TO_SELLER',
    completed.auction_id
FROM (
    SELECT
        aw.auction_id,
        i.seller_id,
        aw.final_price
            - CASE
                WHEN aw.final_price < 1000000 THEN ROUND(aw.final_price * 0.05)
                WHEN aw.final_price <= 10000000 THEN ROUND(aw.final_price * 0.03)
                ELSE ROUND(aw.final_price * 0.02)
              END AS payout_amount
    FROM auction_winners aw
    JOIN auctions a ON a.id = aw.auction_id
    JOIN items i ON i.id = a.item_id
    LEFT JOIN quality_reports qr
        ON qr.auction_id = aw.auction_id
       AND qr.status = 'APPROVED'
       AND qr.refund_completed = TRUE
    WHERE aw.payment_status = 'COMPLETED'
      AND qr.id IS NULL
) completed
WHERE completed.payout_amount > 0
  AND NOT EXISTS (
      SELECT 1
      FROM financial_transactions ft
      WHERE ft.auction_id = completed.auction_id
        AND ft.transaction_type = 'PAYOUT_TO_SELLER'
  );

-- Backfill tax rows for those seller payouts.
INSERT INTO financial_transactions (
    id, sender_id, receiver_id, amount, transaction_type, auction_id
)
SELECT
    UUID(),
    'SYSTEM_BANK',
    'SYSTEM_BANK',
    completed.tax_amount,
    'TAX_COLLECTED',
    completed.auction_id
FROM (
    SELECT
        aw.auction_id,
        CASE
            WHEN aw.final_price < 1000000 THEN ROUND(aw.final_price * 0.05)
            WHEN aw.final_price <= 10000000 THEN ROUND(aw.final_price * 0.03)
            ELSE ROUND(aw.final_price * 0.02)
        END AS tax_amount
    FROM auction_winners aw
    LEFT JOIN quality_reports qr
        ON qr.auction_id = aw.auction_id
       AND qr.status = 'APPROVED'
       AND qr.refund_completed = TRUE
    WHERE aw.payment_status = 'COMPLETED'
      AND qr.id IS NULL
) completed
WHERE completed.tax_amount > 0
  AND NOT EXISTS (
      SELECT 1
      FROM financial_transactions ft
      WHERE ft.auction_id = completed.auction_id
        AND ft.transaction_type = 'TAX_COLLECTED'
  );

-- Backfill winner refunds for approved quality reports.
INSERT INTO financial_transactions (
    id, sender_id, receiver_id, amount, transaction_type, auction_id
)
SELECT
    UUID(),
    'SYSTEM_BANK',
    aw.winner_id,
    aw.final_price,
    'REFUND_TO_WINNER',
    aw.auction_id
FROM auction_winners aw
JOIN quality_reports qr
    ON qr.auction_id = aw.auction_id
   AND qr.status = 'APPROVED'
   AND qr.refund_completed = TRUE
WHERE aw.payment_status = 'COMPLETED'
  AND NOT EXISTS (
      SELECT 1
      FROM financial_transactions ft
      WHERE ft.auction_id = aw.auction_id
        AND ft.transaction_type = 'REFUND_TO_WINNER'
  );

-- Initialize singleton balance if it is missing. Existing rows are left untouched.
INSERT INTO system_bank (id, total_balance)
SELECT
    'SYSTEM',
    GREATEST(
        0,
        COALESCE(SUM(
            CASE
                WHEN transaction_type IN (
                    'PAYMENT_FROM_WINNER',
                    'SECOND_CHANCE_PAYMENT',
                    'DEPOSIT_FORFEIT'
                ) THEN amount
                WHEN transaction_type IN (
                    'PAYOUT_TO_SELLER',
                    'REFUND_TO_WINNER'
                ) THEN -amount
                ELSE 0
            END
        ), 0)
    )
FROM financial_transactions
ON DUPLICATE KEY UPDATE id = id;
