CREATE TABLE IF NOT EXISTS users (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(30) NOT NULL UNIQUE,
    display_name VARCHAR(80) NULL,
    display_name_change_count INT UNSIGNED NOT NULL DEFAULT 0,
    admin_stars TINYINT UNSIGNED NOT NULL DEFAULT 0,
    special_icon VARCHAR(16) NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NULL,
    line_id VARCHAR(80) NULL,
    province VARCHAR(100) NULL,
    amphoe VARCHAR(100) NULL,
    tambon VARCHAR(100) NULL,
    role ENUM('member','admin') NOT NULL DEFAULT 'member',
    status ENUM('active','suspended') NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS api_tokens (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_tokens_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT NULL,
    allow_meetup TINYINT(1) NOT NULL DEFAULT 0,
    allow_buy_now TINYINT(1) NOT NULL DEFAULT 1,
    allow_cod TINYINT(1) NOT NULL DEFAULT 0,
    chat_first TINYINT(1) NOT NULL DEFAULT 1,
    price DECIMAL(12,2) NOT NULL,
    province VARCHAR(100) NOT NULL,
    amphoe VARCHAR(100) NOT NULL,
    tambon VARCHAR(100) NOT NULL,
    status ENUM('pending','approved','hidden','rejected','sold') NOT NULL DEFAULT 'pending',
    approved_by INT UNSIGNED NULL,
    approved_at DATETIME NULL,
    boosted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_listings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_listing_status_created (status, created_at),
    INDEX idx_listing_status_boosted (status, boosted_at, created_at),
    INDEX idx_listing_user (user_id),
    INDEX idx_listing_approved_by (approved_by),
    CONSTRAINT fk_listings_approved_by FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_images (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT UNSIGNED NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    sort_order TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_images_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    INDEX idx_images_listing (listing_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS captcha_challenges (
    token CHAR(48) PRIMARY KEY,
    answer_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_captcha_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rate_limits (
    rate_key CHAR(64) PRIMARY KEY,
    last_action_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS announcements (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_announcements_active (is_active, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS home_content (
    id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
    brand_title VARCHAR(80) NOT NULL,
    headline VARCHAR(160) NOT NULL,
    subheadline VARCHAR(255) NOT NULL,
    trust_title VARCHAR(160) NOT NULL DEFAULT 'ซื้อขายมั่นใจ ปลอดภัย',
    trust_text VARCHAR(255) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO home_content(id,brand_title,headline,subheadline,trust_title,trust_text,is_active)
VALUES(1,'ตลาดพระออนไลน์','ตลาดพระเครื่องสำหรับคนรักพระ','ลงขายง่าย • ดูรูปชัด • ติดต่อผู้ขายโดยตรง','ซื้อขายมั่นใจ ปลอดภัย','ประกาศใหม่ผ่านการตรวจจากแอดมินก่อนเผยแพร่',1);

CREATE TABLE IF NOT EXISTS home_banners (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  image_path VARCHAR(255) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_home_banners_active_sort (is_active,sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO home_banners(image_path,is_active,sort_order)
SELECT 'uploads/banners/default_home_v8.png',1,0
WHERE NOT EXISTS (
  SELECT 1 FROM home_banners WHERE image_path='uploads/banners/default_home_v8.png'
);

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  listing_id BIGINT UNSIGNED NOT NULL,
  buyer_id INT UNSIGNED NOT NULL,
  sender_id INT UNSIGNED NOT NULL,
  message VARCHAR(1000) NOT NULL DEFAULT '',
  image_path VARCHAR(255) NULL,
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_chat_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
  CONSTRAINT fk_chat_buyer FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_chat_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_chat_thread (listing_id,buyer_id,created_at),
  INDEX idx_chat_sender (sender_id),
  INDEX idx_chat_thread_read (listing_id,buyer_id,read_at,sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS push_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL,
  token VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_push_token (token),
  INDEX idx_push_user (user_id),
  CONSTRAINT fk_push_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS favorites (
  user_id INT UNSIGNED NOT NULL,
  listing_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, listing_id),
  INDEX idx_favorites_listing (listing_id),
  CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_favorites_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- V6: points wallet, top-up requests and premium posts
CREATE TABLE IF NOT EXISTS point_wallets (
  user_id INT UNSIGNED NOT NULL PRIMARY KEY,
  balance INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_point_wallet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS point_transactions (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL,
  amount INT NOT NULL,
  type ENUM('topup','premium_purchase','boost_purchase','admin_adjustment','refund','lottery_purchase') NOT NULL,
  description VARCHAR(255) NOT NULL,
  listing_id BIGINT UNSIGNED NULL,
  admin_id INT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_point_tx_user (user_id,created_at),
  INDEX idx_point_tx_listing (listing_id),
  CONSTRAINT fk_point_tx_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_point_tx_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE SET NULL,
  CONSTRAINT fk_point_tx_admin FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V14: ร่วมสนุกลุ้นพระด้วยแต้มและเลขรัฐบาล 2 ตัว
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

CREATE TABLE IF NOT EXISTS point_topup_packages (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  points INT UNSIGNED NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS point_topup_requests (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL,
  package_id INT UNSIGNED NULL,
  points INT UNSIGNED NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  note VARCHAR(255) NULL,
  slip_path VARCHAR(255) NULL,
  payment_snapshot VARCHAR(500) NULL,
  status ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
  reviewed_by INT UNSIGNED NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_topup_status_created (status,created_at),
  INDEX idx_topup_user (user_id,created_at),
  CONSTRAINT fk_topup_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_topup_package FOREIGN KEY (package_id) REFERENCES point_topup_packages(id),
  CONSTRAINT fk_topup_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS premium_plans (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  points_cost INT UNSIGNED NOT NULL,
  duration_days INT UNSIGNED NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS premium_promotions (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  listing_id BIGINT UNSIGNED NOT NULL,
  user_id INT UNSIGNED NOT NULL,
  plan_id INT UNSIGNED NOT NULL,
  points_spent INT UNSIGNED NOT NULL,
  request_key CHAR(36) NULL,
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL,
  status ENUM('active','expired','cancelled') NOT NULL DEFAULT 'active',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_premium_active (status,starts_at,ends_at),
  INDEX idx_premium_listing (listing_id,ends_at),
  INDEX idx_premium_user (user_id,created_at),
  UNIQUE KEY uq_premium_request (request_key),
  CONSTRAINT fk_premium_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
  CONSTRAINT fk_premium_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_premium_plan FOREIGN KEY (plan_id) REFERENCES premium_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO point_topup_packages(name,points,price,sort_order)
SELECT 'เริ่มต้น 100 แต้ม',100,100.00,10 WHERE NOT EXISTS (SELECT 1 FROM point_topup_packages);
INSERT INTO point_topup_packages(name,points,price,sort_order)
SELECT 'คุ้มค่า 300 แต้ม',330,300.00,20 WHERE (SELECT COUNT(*) FROM point_topup_packages)=1;
INSERT INTO point_topup_packages(name,points,price,sort_order)
SELECT 'ร้านจริงจัง 500 แต้ม',575,500.00,30 WHERE (SELECT COUNT(*) FROM point_topup_packages)=2;

INSERT INTO premium_plans(name,points_cost,duration_days,sort_order)
SELECT 'พรีเมียมเด่น 1 วัน',20,1,10 WHERE NOT EXISTS (SELECT 1 FROM premium_plans);
INSERT INTO premium_plans(name,points_cost,duration_days,sort_order)
SELECT 'พรีเมียม 3 วัน',50,3,20 WHERE (SELECT COUNT(*) FROM premium_plans)=1;
INSERT INTO premium_plans(name,points_cost,duration_days,sort_order)
SELECT 'พรีเมียม 7 วัน',100,7,30 WHERE (SELECT COUNT(*) FROM premium_plans)=2;

INSERT IGNORE INTO point_wallets(user_id,balance) SELECT id,0 FROM users;


-- V7: transfer settings, presence, reports, scheduled notifications
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
  source VARCHAR(30) NOT NULL DEFAULT 'manual',
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

-- V13: configurable automatic member Push windows/count.
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
  CONSTRAINT fk_member_push_settings_admin FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO member_push_settings(
  id,enabled,daily_count,window1_start,window1_end,window2_start,window2_end
) VALUES(1,1,2,'09:00:00','12:00:00','15:00:00','21:00:00');

UPDATE home_content SET brand_title='ตลาดพระออนไลน์' WHERE brand_title='ขายพระบ้าน';


-- V8: real boost ordering + simple Buy Now orders
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

CREATE TABLE IF NOT EXISTS orders (
  order_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  listing_id BIGINT UNSIGNED NOT NULL,
  buyer_id INT UNSIGNED NOT NULL,
  seller_id INT UNSIGNED NOT NULL,
  price_snapshot DECIMAL(12,2) NOT NULL,
  title_snapshot VARCHAR(160) NOT NULL,
  cover_path_snapshot VARCHAR(255) NULL,
  seller_verified TINYINT(1) NOT NULL DEFAULT 0,
  seller_bank_name_snapshot VARCHAR(120) NULL,
  seller_account_name_snapshot VARCHAR(160) NULL,
  seller_account_number_snapshot VARCHAR(80) NULL,
  seller_verified_at_snapshot DATETIME NULL,
  payment_method ENUM('bank_transfer','cod') NULL,
  payment_slip_path VARCHAR(255) NULL,
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


-- V9: member identity verification and seller ratings
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


-- V10: Admin task notification center (per-admin read state)
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
  CONSTRAINT fk_admin_notifications_user FOREIGN KEY (related_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_notification_reads (
  notification_id BIGINT UNSIGNED NOT NULL,
  admin_user_id INT UNSIGNED NOT NULL,
  read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (notification_id,admin_user_id),
  INDEX idx_admin_reads_admin (admin_user_id,read_at),
  CONSTRAINT fk_admin_reads_notification FOREIGN KEY (notification_id) REFERENCES admin_notifications(id) ON DELETE CASCADE,
  CONSTRAINT fk_admin_reads_user FOREIGN KEY (admin_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
