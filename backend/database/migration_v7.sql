-- KhaiPhraBan / ตลาดพระออนไลน์ V7
-- เพิ่มเติมจาก V6: เติมแต้มพร้อมสลิป, บัญชีรับโอน, presence, รายงานผู้ใช้, แจ้งเตือนตั้งเวลา

CREATE TABLE IF NOT EXISTS payment_settings (
  id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
  bank_name VARCHAR(120) NOT NULL,
  account_name VARCHAR(160) NOT NULL,
  account_number VARCHAR(80) NOT NULL,
  points_per_baht DECIMAL(10,4) NOT NULL DEFAULT 1.0000,
  min_amount DECIMAL(10,2) NOT NULL DEFAULT 20.00,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO payment_settings(id,bank_name,account_name,account_number,points_per_baht,min_amount,is_active)
VALUES(1,'กรุณาตั้งค่าในแอดมิน','กรุณาตั้งชื่อบัญชี','-',1.0000,20.00,1);

ALTER TABLE point_topup_requests MODIFY package_id INT UNSIGNED NULL;
ALTER TABLE point_topup_requests ADD COLUMN slip_path VARCHAR(255) NULL AFTER note;
ALTER TABLE point_topup_requests ADD COLUMN payment_snapshot VARCHAR(500) NULL AFTER slip_path;

CREATE TABLE IF NOT EXISTS app_presence (
  client_id CHAR(64) NOT NULL PRIMARY KEY,
  user_id INT UNSIGNED NULL,
  last_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_presence_last_seen (last_seen),
  INDEX idx_presence_user (user_id),
  CONSTRAINT fk_presence_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_reports (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  reporter_user_id INT UNSIGNED NOT NULL,
  reported_user_id INT UNSIGNED NULL,
  listing_id BIGINT UNSIGNED NULL,
  category VARCHAR(40) NOT NULL,
  details VARCHAR(1000) NOT NULL,
  status ENUM('open','reviewing','resolved','dismissed') NOT NULL DEFAULT 'open',
  admin_note VARCHAR(1000) NULL,
  resolved_by INT UNSIGNED NULL,
  resolved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_reports_status_created (status,created_at),
  INDEX idx_reports_reported_user (reported_user_id,created_at),
  CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_report_reported FOREIGN KEY (reported_user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_report_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE SET NULL,
  CONSTRAINT fk_report_admin FOREIGN KEY (resolved_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS scheduled_notifications (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(160) NOT NULL,
  body VARCHAR(1000) NOT NULL,
  scheduled_at DATETIME NOT NULL,
  status ENUM('pending','sent','cancelled','failed') NOT NULL DEFAULT 'pending',
  sent_count INT UNSIGNED NOT NULL DEFAULT 0,
  created_by INT UNSIGNED NULL,
  sent_at DATETIME NULL,
  error_message VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_scheduled_status_time (status,scheduled_at),
  CONSTRAINT fk_scheduled_admin FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE home_content SET brand_title='ตลาดพระออนไลน์' WHERE brand_title='ขายพระบ้าน';
