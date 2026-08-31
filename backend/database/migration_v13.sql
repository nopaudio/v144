-- ตลาดพระออนไลน์ V13
-- V12 -> V13: member seller address + configurable automatic member Push schedule.
-- Safe/idempotent: no DROP, TRUNCATE, reset, or data deletion.

SET @db := DATABASE();

-- users.province
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='users' AND COLUMN_NAME='province'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE users ADD COLUMN province VARCHAR(100) NULL AFTER line_id',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- users.amphoe
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='users' AND COLUMN_NAME='amphoe'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE users ADD COLUMN amphoe VARCHAR(100) NULL AFTER province',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- users.tambon
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='users' AND COLUMN_NAME='tambon'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE users ADD COLUMN tambon VARCHAR(100) NULL AFTER amphoe',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- scheduled_notifications.source distinguishes Admin manual schedules from
-- V13 automatic member reminders so settings can be changed without touching
-- an Admin-created future notification.
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='scheduled_notifications' AND COLUMN_NAME='source'
);
SET @sql := IF(@has_col=0,
  'ALTER TABLE scheduled_notifications ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT ''manual'' AFTER body',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS member_push_settings (
  id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  daily_count TINYINT UNSIGNED NOT NULL DEFAULT 2,
  window1_start TIME NOT NULL DEFAULT '09:00:00',
  window1_end TIME NOT NULL DEFAULT '12:00:00',
  window2_start TIME NULL DEFAULT '15:00:00',
  window2_end TIME NULL DEFAULT '21:00:00',
  window3_start TIME NULL,
  window3_end TIME NULL,
  last_planned_date DATE NULL,
  updated_by INT UNSIGNED NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_member_push_settings_admin
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO member_push_settings(
  id,enabled,daily_count,window1_start,window1_end,window2_start,window2_end
) VALUES(1,1,2,'09:00:00','12:00:00','15:00:00','21:00:00');
