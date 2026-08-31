-- KhaiPhraBan V3: หน้าแรกแก้จาก Admin + ระบบแชท
-- รันได้ซ้ำอย่างปลอดภัย

CREATE TABLE IF NOT EXISTS home_content (
    id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
    brand_title VARCHAR(80) NOT NULL,
    headline VARCHAR(160) NOT NULL,
    subheadline VARCHAR(255) NOT NULL,
    trust_text VARCHAR(255) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO home_content(id,brand_title,headline,subheadline,trust_text,is_active)
VALUES(1,'ขายพระบ้าน','ตลาดพระเครื่องสำหรับคนรักพระ','ลงขายง่าย • ดูรูปชัด • ติดต่อผู้ขายโดยตรง','ประกาศใหม่ผ่านการตรวจจากแอดมินก่อนเผยแพร่',1);

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  listing_id BIGINT UNSIGNED NOT NULL,
  buyer_id INT UNSIGNED NOT NULL,
  sender_id INT UNSIGNED NOT NULL,
  message VARCHAR(1000) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_chat_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
  CONSTRAINT fk_chat_buyer FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_chat_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_chat_thread (listing_id,buyer_id,created_at),
  INDEX idx_chat_sender (sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
