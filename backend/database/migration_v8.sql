-- ตลาดพระออนไลน์ V8
-- Upgrade V7 in-place. Safe to run more than once.
-- IMPORTANT: V8 backend also sets the MySQL session timezone to the app timezone
-- (+07:00 for Asia/Bangkok) so NOW()/CURRENT_TIMESTAMP and PHP use one clock.

-- 1) Listing boost timestamp
SET @has_boosted_at := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='listings' AND COLUMN_NAME='boosted_at'
);
SET @sql := IF(@has_boosted_at=0,
  'ALTER TABLE listings ADD COLUMN boosted_at DATETIME NULL AFTER status',
  'SELECT 1');
PREPARE v8_stmt FROM @sql; EXECUTE v8_stmt; DEALLOCATE PREPARE v8_stmt;

SET @has_boost_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='listings' AND INDEX_NAME='idx_listing_status_boosted'
);
SET @sql := IF(@has_boost_idx=0,
  'ALTER TABLE listings ADD INDEX idx_listing_status_boosted (status,boosted_at,created_at)',
  'SELECT 1');
PREPARE v8_stmt FROM @sql; EXECUTE v8_stmt; DEALLOCATE PREPARE v8_stmt;


-- Rename only V7's built-in misleading plan label; it was Premium, not a real Boost.
UPDATE premium_plans SET name='พรีเมียมเด่น 1 วัน' WHERE name='ดันเด่น 1 วัน' AND points_cost=20 AND duration_days=1;

-- 2) Premium request idempotency
SET @has_premium_request_key := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='premium_promotions' AND COLUMN_NAME='request_key'
);
SET @sql := IF(@has_premium_request_key=0,
  'ALTER TABLE premium_promotions ADD COLUMN request_key CHAR(36) NULL AFTER points_spent',
  'SELECT 1');
PREPARE v8_stmt FROM @sql; EXECUTE v8_stmt; DEALLOCATE PREPARE v8_stmt;

SET @has_premium_request_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='premium_promotions' AND INDEX_NAME='uq_premium_request'
);
SET @sql := IF(@has_premium_request_idx=0,
  'ALTER TABLE premium_promotions ADD UNIQUE KEY uq_premium_request (request_key)',
  'SELECT 1');
PREPARE v8_stmt FROM @sql; EXECUTE v8_stmt; DEALLOCATE PREPARE v8_stmt;

-- V8 adds boost purchases to the auditable point ledger.
ALTER TABLE point_transactions
  MODIFY type ENUM('topup','premium_purchase','boost_purchase','admin_adjustment','refund') NOT NULL;

CREATE TABLE IF NOT EXISTS boost_settings (
  id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
  points_cost INT UNSIGNED NOT NULL DEFAULT 20,
  cooldown_minutes INT UNSIGNED NOT NULL DEFAULT 10,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO boost_settings(id,points_cost,cooldown_minutes,is_active)
VALUES(1,20,10,1);

CREATE TABLE IF NOT EXISTS listing_boosts (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  listing_id BIGINT UNSIGNED NOT NULL,
  user_id INT UNSIGNED NOT NULL,
  points_spent INT UNSIGNED NOT NULL,
  boosted_at DATETIME NOT NULL,
  request_key CHAR(36) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_listing_boost_request (request_key),
  INDEX idx_listing_boost_listing (listing_id,boosted_at),
  INDEX idx_listing_boost_user (user_id,created_at),
  CONSTRAINT fk_listing_boost_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
  CONSTRAINT fk_listing_boost_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) Simple one-item order workflow. Shipping address exists only here, never
-- in public listing payloads.
CREATE TABLE IF NOT EXISTS orders (
  order_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  listing_id BIGINT UNSIGNED NOT NULL,
  buyer_id INT UNSIGNED NOT NULL,
  seller_id INT UNSIGNED NOT NULL,
  price_snapshot DECIMAL(12,2) NOT NULL,
  title_snapshot VARCHAR(160) NOT NULL,
  cover_path_snapshot VARCHAR(255) NULL,
  recipient_name VARCHAR(160) NOT NULL,
  phone VARCHAR(30) NOT NULL,
  house_no_moo VARCHAR(190) NOT NULL,
  soi VARCHAR(120) NULL,
  road VARCHAR(120) NULL,
  subdistrict VARCHAR(100) NOT NULL,
  district VARCHAR(100) NOT NULL,
  province VARCHAR(100) NOT NULL,
  postal_code VARCHAR(10) NOT NULL,
  note VARCHAR(1000) NULL,
  status ENUM('pending_confirmation','preparing','shipped','completed','cancelled') NOT NULL DEFAULT 'pending_confirmation',
  tracking_number VARCHAR(120) NULL,
  request_key CHAR(36) NOT NULL,
  confirmed_at DATETIME NULL,
  shipped_at DATETIME NULL,
  completed_at DATETIME NULL,
  cancelled_at DATETIME NULL,
  cancelled_by_user_id INT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_order_request (request_key),
  INDEX idx_orders_listing_status (listing_id,status),
  INDEX idx_orders_buyer_created (buyer_id,created_at),
  INDEX idx_orders_seller_created (seller_id,created_at),
  INDEX idx_orders_status_created (status,created_at),
  CONSTRAINT fk_orders_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE RESTRICT,
  CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_orders_seller FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_orders_cancelled_by FOREIGN KEY (cancelled_by_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
