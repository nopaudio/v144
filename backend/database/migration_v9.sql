-- ตลาดพระออนไลน์ V9
-- Upgrade V8 in-place. Safe to run more than once. Never drops existing data.

-- 1) Existing Home config: add the editable trust-card title.
SET @has_trust_title := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='home_content' AND COLUMN_NAME='trust_title'
);
SET @sql := IF(@has_trust_title=0,
  'ALTER TABLE home_content ADD COLUMN trust_title VARCHAR(160) NOT NULL DEFAULT ''ซื้อขายมั่นใจ ปลอดภัย'' AFTER subheadline',
  'SELECT 1');
PREPARE v9_stmt FROM @sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

SET @had_home_banners := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='home_banners'
);

CREATE TABLE IF NOT EXISTS home_banners (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  image_path VARCHAR(255) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_home_banners_active_sort (is_active,sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Preserve the V8 home artwork as a server-owned Banner on first upgrade.
-- Android no longer references this drawable directly; Admin can replace/delete it.
INSERT INTO home_banners(image_path,is_active,sort_order)
SELECT 'uploads/banners/default_home_v8.png',1,0
WHERE @had_home_banners=0 AND NOT EXISTS (SELECT 1 FROM home_banners);

-- 2) Identity verification / verified receiving bank account.
CREATE TABLE IF NOT EXISTS identity_verifications (
  user_id INT UNSIGNED NOT NULL PRIMARY KEY,
  bank_name VARCHAR(120) NOT NULL,
  account_name VARCHAR(160) NOT NULL,
  account_number VARCHAR(80) NOT NULL,
  document_path VARCHAR(255) NOT NULL,
  status ENUM('unverified','pending','verified','rejected') NOT NULL DEFAULT 'pending',
  rejection_reason VARCHAR(500) NULL,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_by INT UNSIGNED NULL,
  reviewed_at DATETIME NULL,
  verified_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_identity_status_submitted (status,submitted_at),
  INDEX idx_identity_reviewer (reviewed_by),
  CONSTRAINT fk_identity_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_identity_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) One rating per real completed order.
CREATE TABLE IF NOT EXISTS seller_reviews (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  buyer_id INT UNSIGNED NOT NULL,
  seller_id INT UNSIGNED NOT NULL,
  rating TINYINT UNSIGNED NOT NULL,
  review_text VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_seller_review_order (order_id),
  INDEX idx_seller_reviews_seller (seller_id,created_at),
  INDEX idx_seller_reviews_buyer (buyer_id,created_at),
  CONSTRAINT fk_seller_review_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE RESTRICT,
  CONSTRAINT fk_seller_review_buyer FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_seller_review_seller FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) Chat read state: keep old messages and mark only future/opened messages.
SET @has_read_at := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_messages' AND COLUMN_NAME='read_at'
);
SET @sql := IF(@has_read_at=0,
  'ALTER TABLE chat_messages ADD COLUMN read_at DATETIME NULL AFTER image_path',
  'SELECT 1');
PREPARE v9_stmt FROM @sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

SET @has_chat_read_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_messages' AND INDEX_NAME='idx_chat_thread_read'
);
SET @sql := IF(@has_chat_read_idx=0,
  'ALTER TABLE chat_messages ADD INDEX idx_chat_thread_read (listing_id,buyer_id,read_at,sender_id)',
  'SELECT 1');
PREPARE v9_stmt FROM @sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- Existing V8 messages predate unread tracking. Backfill only on the first
-- upgrade; rerunning V9 must not mark genuinely new unread messages as read.
SET @sql := IF(@has_read_at=0,
  'UPDATE chat_messages SET read_at=created_at WHERE read_at IS NULL',
  'SELECT 1');
PREPARE v9_stmt FROM @sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- 5) Snapshot the seller verification/payment state used by each new order.
SET @has_order_verified := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND COLUMN_NAME='seller_verified'
);
SET @sql := IF(@has_order_verified=0,
  'ALTER TABLE orders ADD COLUMN seller_verified TINYINT(1) NOT NULL DEFAULT 0 AFTER cover_path_snapshot',
  'SELECT 1');
PREPARE v9_stmt FROM @sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

SET @has_order_bank_name := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND COLUMN_NAME='seller_bank_name_snapshot'
);
SET @sql := IF(@has_order_bank_name=0,
  'ALTER TABLE orders ADD COLUMN seller_bank_name_snapshot VARCHAR(120) NULL AFTER seller_verified',
  'SELECT 1');
PREPARE v9_stmt FROM @sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

SET @has_order_account_name := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND COLUMN_NAME='seller_account_name_snapshot'
);
SET @sql := IF(@has_order_account_name=0,
  'ALTER TABLE orders ADD COLUMN seller_account_name_snapshot VARCHAR(160) NULL AFTER seller_bank_name_snapshot',
  'SELECT 1');
PREPARE v9_stmt FROM @sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

SET @has_order_account_number := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND COLUMN_NAME='seller_account_number_snapshot'
);
SET @sql := IF(@has_order_account_number=0,
  'ALTER TABLE orders ADD COLUMN seller_account_number_snapshot VARCHAR(80) NULL AFTER seller_account_name_snapshot',
  'SELECT 1');
PREPARE v9_stmt FROM @sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

SET @has_order_verified_at := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND COLUMN_NAME='seller_verified_at_snapshot'
);
SET @sql := IF(@has_order_verified_at=0,
  'ALTER TABLE orders ADD COLUMN seller_verified_at_snapshot DATETIME NULL AFTER seller_account_number_snapshot',
  'SELECT 1');
PREPARE v9_stmt FROM @sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;
