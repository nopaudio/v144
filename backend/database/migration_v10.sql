-- ตลาดพระออนไลน์ V10
-- Admin Mobile + Admin Notification + In-App Admin
-- Safe/idempotent migration: no DROP, TRUNCATE, reset, or data deletion.

SET @db := DATABASE();

-- listings.approved_by
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='listings' AND COLUMN_NAME='approved_by'
);
SET @sql := IF(
  @has_col=0,
  'ALTER TABLE listings ADD COLUMN approved_by INT UNSIGNED NULL AFTER status',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- listings.approved_at
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='listings' AND COLUMN_NAME='approved_at'
);
SET @sql := IF(
  @has_col=0,
  'ALTER TABLE listings ADD COLUMN approved_at DATETIME NULL AFTER approved_by',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- index for approval audit lookup
SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='listings' AND INDEX_NAME='idx_listing_approved_by'
);
SET @sql := IF(
  @has_idx=0,
  'ALTER TABLE listings ADD INDEX idx_listing_approved_by (approved_by)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- FK is safe because the new column starts NULL for existing rows.
SET @has_fk := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='listings'
    AND CONSTRAINT_TYPE='FOREIGN KEY' AND CONSTRAINT_NAME='fk_listings_approved_by'
);
SET @sql := IF(
  @has_fk=0,
  'ALTER TABLE listings ADD CONSTRAINT fk_listings_approved_by FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS admin_notifications (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  type VARCHAR(50) NOT NULL,
  title VARCHAR(160) NOT NULL,
  message VARCHAR(1000) NOT NULL,
  related_user_id INT UNSIGNED NULL,
  entity_type VARCHAR(50) NULL,
  entity_id BIGINT UNSIGNED NULL,
  action_path VARCHAR(255) NULL,
  mobile_route VARCHAR(120) NULL,
  event_key VARCHAR(160) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_admin_notification_event (event_key),
  INDEX idx_admin_notifications_created (created_at),
  INDEX idx_admin_notifications_entity (entity_type,entity_id),
  INDEX idx_admin_notifications_related_user (related_user_id),
  CONSTRAINT fk_admin_notifications_user
    FOREIGN KEY (related_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_notification_reads (
  notification_id BIGINT UNSIGNED NOT NULL,
  admin_user_id INT UNSIGNED NOT NULL,
  read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (notification_id,admin_user_id),
  INDEX idx_admin_reads_admin (admin_user_id,read_at),
  CONSTRAINT fk_admin_reads_notification
    FOREIGN KEY (notification_id) REFERENCES admin_notifications(id) ON DELETE CASCADE,
  CONSTRAINT fk_admin_reads_user
    FOREIGN KEY (admin_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
