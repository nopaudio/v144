-- KhaiPhraBan V6 migration


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
  type ENUM('topup','premium_purchase','admin_adjustment','refund') NOT NULL,
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
  package_id INT UNSIGNED NOT NULL,
  points INT UNSIGNED NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  note VARCHAR(255) NULL,
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
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL,
  status ENUM('active','expired','cancelled') NOT NULL DEFAULT 'active',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_premium_active (status,starts_at,ends_at),
  INDEX idx_premium_listing (listing_id,ends_at),
  INDEX idx_premium_user (user_id,created_at),
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
SELECT 'ดันเด่น 1 วัน',20,1,10 WHERE NOT EXISTS (SELECT 1 FROM premium_plans);
INSERT INTO premium_plans(name,points_cost,duration_days,sort_order)
SELECT 'พรีเมียม 3 วัน',50,3,20 WHERE (SELECT COUNT(*) FROM premium_plans)=1;
INSERT INTO premium_plans(name,points_cost,duration_days,sort_order)
SELECT 'พรีเมียม 7 วัน',100,7,30 WHERE (SELECT COUNT(*) FROM premium_plans)=2;

INSERT IGNORE INTO point_wallets(user_id,balance) SELECT id,0 FROM users;
