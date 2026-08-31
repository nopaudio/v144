-- ตลาดพระออนไลน์ V11
-- V10 -> V11: listing contact options, Thai display names, payment choice/slip,
-- and richer Admin user controls.

SET @db := DATABASE();

-- Safe/idempotent: no DROP, TRUNCATE, reset, or data deletion.

-- users.display_name
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='users' AND COLUMN_NAME='display_name'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE users ADD COLUMN display_name VARCHAR(80) NULL AFTER username',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='users' AND COLUMN_NAME='display_name_change_count'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE users ADD COLUMN display_name_change_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER display_name',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='users' AND COLUMN_NAME='admin_stars'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE users ADD COLUMN admin_stars TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER display_name_change_count',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='users' AND COLUMN_NAME='special_icon'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE users ADD COLUMN special_icon VARCHAR(16) NULL AFTER admin_stars',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- listings selling/contact options
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='listings' AND COLUMN_NAME='allow_meetup'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE listings ADD COLUMN allow_meetup TINYINT(1) NOT NULL DEFAULT 0 AFTER description',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='listings' AND COLUMN_NAME='allow_buy_now'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE listings ADD COLUMN allow_buy_now TINYINT(1) NOT NULL DEFAULT 1 AFTER allow_meetup',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='listings' AND COLUMN_NAME='allow_cod'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE listings ADD COLUMN allow_cod TINYINT(1) NOT NULL DEFAULT 0 AFTER allow_buy_now',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='listings' AND COLUMN_NAME='chat_first'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE listings ADD COLUMN chat_first TINYINT(1) NOT NULL DEFAULT 1 AFTER allow_cod',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- order payment choice and private transfer slip
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='orders' AND COLUMN_NAME='payment_method'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE orders ADD COLUMN payment_method ENUM(''bank_transfer'',''cod'') NULL AFTER seller_verified_at_snapshot',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='orders' AND COLUMN_NAME='payment_slip_path'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE orders ADD COLUMN payment_slip_path VARCHAR(255) NULL AFTER payment_method',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS display_name_change_requests (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL,
  requested_name VARCHAR(80) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
  reviewed_by INT UNSIGNED NULL,
  reviewed_at DATETIME NULL,
  admin_note VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_display_name_requests_status (status,created_at),
  INDEX idx_display_name_requests_user (user_id,created_at),
  CONSTRAINT fk_display_name_request_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_display_name_request_admin FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

