-- ตลาดพระออนไลน์ V14
-- V13 -> V14: ร่วมสนุกลุ้นพระด้วยแต้ม + เลขรัฐบาล 2 ตัว
-- Additive/idempotent: ไม่มี DROP / TRUNCATE / DELETE / reset ข้อมูลเดิม

SET @db := DATABASE();

-- เพิ่ม transaction type สำหรับการใช้แต้มซื้อเลข โดยเก็บค่าเดิมทั้งหมด
SET @point_type := (
  SELECT COLUMN_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='point_transactions' AND COLUMN_NAME='type'
  LIMIT 1
);
SET @sql := IF(
  @point_type IS NOT NULL AND LOCATE('lottery_purchase', @point_type)=0,
  CONCAT(
    'ALTER TABLE point_transactions MODIFY type ',
    LEFT(@point_type, CHAR_LENGTH(@point_type)-1),
    ',''lottery_purchase'') NOT NULL'
  ),
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS lottery_rounds (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(160) NOT NULL,
  prize_name VARCHAR(160) NOT NULL,
  prize_description VARCHAR(2000) NULL,
  prize_image_path VARCHAR(255) NULL,
  draw_date DATE NOT NULL,
  points_cost INT UNSIGNED NOT NULL,
  status ENUM('draft','open','closed','announced') NOT NULL DEFAULT 'draft',
  winning_number TINYINT UNSIGNED NULL,
  announced_at DATETIME NULL,
  created_by INT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_lottery_round_status (status,id),
  INDEX idx_lottery_draw_date (draw_date),
  CONSTRAINT fk_lottery_round_admin FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lottery_entries (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  round_id BIGINT UNSIGNED NOT NULL,
  user_id INT UNSIGNED NOT NULL,
  number TINYINT UNSIGNED NOT NULL,
  points_spent INT UNSIGNED NOT NULL,
  request_key CHAR(36) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_lottery_round_number (round_id,number),
  UNIQUE KEY uq_lottery_request (request_key),
  INDEX idx_lottery_entry_user (user_id,created_at),
  INDEX idx_lottery_entry_round_user (round_id,user_id),
  CONSTRAINT fk_lottery_entry_round FOREIGN KEY (round_id) REFERENCES lottery_rounds(id) ON DELETE CASCADE,
  CONSTRAINT fk_lottery_entry_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
