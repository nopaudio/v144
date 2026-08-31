<?php
declare(strict_types=1);

function e(?string $value): string { return htmlspecialchars($value ?? '', ENT_QUOTES, 'UTF-8'); }
function app_base(array $config): string { return rtrim((string)$config['app']['base_url'], '/'); }
function image_url(array $config, string $path): string { return app_base($config) . '/' . ltrim($path, '/'); }

function json_out(bool $success, string $message = '', mixed $data = null, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['success' => $success, 'message' => $message, 'data' => $data], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function post_value(string $key, string $default = ''): string { return trim((string)($_POST[$key] ?? $default)); }

function bearer_token(): ?string
{
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    return preg_match('/Bearer\s+(\S+)/i', $header, $m) ? $m[1] : null;
}

function api_user(PDO $pdo): ?array
{
    $token = bearer_token();
    if (!$token) return null;
    $stmt = $pdo->prepare('SELECT u.* FROM api_tokens t JOIN users u ON u.id=t.user_id WHERE t.token_hash=? AND t.expires_at>NOW() LIMIT 1');
    $stmt->execute([hash('sha256', $token)]);
    $user = $stmt->fetch();
    return ($user && $user['status'] === 'active') ? $user : null;
}

function require_api_user(PDO $pdo): array
{
    $user = api_user($pdo);
    if (!$user) json_out(false, 'กรุณาเข้าสู่ระบบใหม่', null, 401);
    return $user;
}

function issue_api_token(PDO $pdo, int $userId): string
{
    $plain = bin2hex(random_bytes(32));
    $stmt = $pdo->prepare('INSERT INTO api_tokens(user_id, token_hash, expires_at) VALUES(?,?,DATE_ADD(NOW(), INTERVAL 90 DAY))');
    $stmt->execute([$userId, hash('sha256', $plain)]);
    return $plain;
}

function issue_captcha(PDO $pdo, array $config): array
{
    $a = random_int(2, 20); $b = random_int(1, 12); $op = random_int(0,1) ? '+' : '-';
    if ($op === '-' && $b > $a) [$a, $b] = [$b, $a];
    $answer = $op === '+' ? $a + $b : $a - $b;
    $token = bin2hex(random_bytes(24));
    $hash = hash_hmac('sha256', (string)$answer, $config['app']['app_key']);
    $pdo->prepare('DELETE FROM captcha_challenges WHERE expires_at<NOW() OR used_at IS NOT NULL')->execute();
    $stmt = $pdo->prepare('INSERT INTO captcha_challenges(token, answer_hash, expires_at) VALUES(?,?,DATE_ADD(NOW(), INTERVAL 10 MINUTE))');
    $stmt->execute([$token, $hash]);
    return ['token' => $token, 'question' => "$a $op $b = ?", 'expires_at' => date('Y-m-d H:i:s', time()+600)];
}

function verify_captcha(PDO $pdo, array $config, string $token, string $answer): bool
{
    $stmt = $pdo->prepare('SELECT * FROM captcha_challenges WHERE token=? AND used_at IS NULL AND expires_at>NOW() LIMIT 1');
    $stmt->execute([$token]);
    $row = $stmt->fetch();
    if (!$row) return false;
    $valid = hash_equals($row['answer_hash'], hash_hmac('sha256', trim($answer), $config['app']['app_key']));
    $pdo->prepare('UPDATE captcha_challenges SET used_at=NOW() WHERE token=?')->execute([$token]);
    return $valid;
}

function client_ip(): string { return $_SERVER['REMOTE_ADDR'] ?? 'unknown'; }

function enforce_rate_limit(PDO $pdo, string $action, int $userId, int $seconds, string $message = 'กรุณารอสักครู่ก่อนส่งประกาศใหม่'): void
{
    $key = hash('sha256', $action . '|' . $userId . '|' . client_ip());
    $stmt = $pdo->prepare('SELECT TIMESTAMPDIFF(SECOND,last_action_at,NOW()) elapsed FROM rate_limits WHERE rate_key=?');
    $stmt->execute([$key]);
    $row = $stmt->fetch();
    if ($row && (int)$row['elapsed'] < $seconds) {
        json_out(false, $message, null, 429);
    }
    $pdo->prepare('INSERT INTO rate_limits(rate_key,last_action_at) VALUES(?,NOW()) ON DUPLICATE KEY UPDATE last_action_at=NOW()')->execute([$key]);
}

function normalize_files(array $files): array
{
    $out = [];
    if (!isset($files['name'])) return $out;
    if (!is_array($files['name'])) return [$files];
    foreach ($files['name'] as $i => $name) {
        $out[] = ['name'=>$name, 'type'=>$files['type'][$i] ?? '', 'tmp_name'=>$files['tmp_name'][$i] ?? '', 'error'=>$files['error'][$i] ?? UPLOAD_ERR_NO_FILE, 'size'=>$files['size'][$i] ?? 0];
    }
    return $out;
}

function save_listing_images(PDO $pdo, array $config, int $listingId, array $files): array
{
    $files = normalize_files($files);
    if (count($files) < 1) throw new RuntimeException('กรุณาแนบรูปอย่างน้อย 1 รูป');
    if (count($files) > (int)$config['app']['max_images']) throw new RuntimeException('แนบรูปได้ไม่เกิน 5 รูป');
    $dir = dirname(__DIR__) . '/uploads/listings/' . date('Y/m');
    if (!is_dir($dir) && !mkdir($dir, 0755, true) && !is_dir($dir)) throw new RuntimeException('สร้างโฟลเดอร์รูปไม่ได้');
    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $allowed = ['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp'];
    $saved = [];
    try {
        foreach ($files as $i => $file) {
            if ($file['error'] !== UPLOAD_ERR_OK) throw new RuntimeException('อัปโหลดรูปไม่สำเร็จ');
            if ((int)$file['size'] > (int)$config['app']['upload_max_bytes']) throw new RuntimeException('รูปมีขนาดเกิน 5 MB');
            $mime = $finfo->file($file['tmp_name']);
            if (!isset($allowed[$mime])) throw new RuntimeException('รองรับเฉพาะ JPG, PNG และ WEBP');
            $name = bin2hex(random_bytes(16)) . '.' . $allowed[$mime];
            $full = $dir . '/' . $name;
            if (!move_uploaded_file($file['tmp_name'], $full)) throw new RuntimeException('บันทึกรูปไม่สำเร็จ');
            $relative = 'uploads/listings/' . date('Y/m') . '/' . $name;
            $pdo->prepare('INSERT INTO listing_images(listing_id,file_path,sort_order) VALUES(?,?,?)')->execute([$listingId,$relative,$i]);
            $saved[] = $full;
        }
        return $saved;
    } catch (Throwable $e) {
        foreach ($saved as $full) @unlink($full);
        throw $e;
    }
}

function fetch_listing(PDO $pdo, array $config, int $id, bool $publicOnly = true): ?array
{
    ensure_v10_schema($pdo);
    $sql = "SELECT l.*,COALESCE(NULLIF(u.display_name,''),u.username) seller_username,u.phone seller_phone,u.line_id seller_line,u.created_at seller_created_at,u.role seller_role,
        u.admin_stars seller_admin_stars,u.special_icon seller_special_icon,
        (SELECT MAX(pp.ends_at) FROM premium_promotions pp
         WHERE pp.listing_id=l.id AND pp.status='active' AND pp.starts_at<=NOW() AND pp.ends_at>NOW()) premium_until,
        EXISTS(SELECT 1 FROM orders o
         WHERE o.listing_id=l.id AND o.status IN ('pending_confirmation','preparing','shipped')) has_active_order,
        EXISTS(SELECT 1 FROM identity_verifications iv
         WHERE iv.user_id=l.user_id AND iv.status='verified') seller_is_verified,
        COALESCE((SELECT ROUND(AVG(sr.rating),1) FROM seller_reviews sr WHERE sr.seller_id=l.user_id),0) seller_rating_average,
        (SELECT COUNT(*) FROM seller_reviews sr2 WHERE sr2.seller_id=l.user_id) seller_rating_count
        FROM listings l JOIN users u ON u.id=l.user_id WHERE l.id=?";
    if ($publicOnly) $sql .= " AND l.status IN ('approved','sold')";
    $stmt = $pdo->prepare($sql . ' LIMIT 1'); $stmt->execute([$id]);
    $row = $stmt->fetch();
    return $row ? hydrate_listing($pdo, $config, $row) : null;
}

function hydrate_listing(PDO $pdo, array $config, array $row): array
{
    $stmt = $pdo->prepare('SELECT id,file_path,sort_order FROM listing_images WHERE listing_id=? ORDER BY sort_order,id');
    $stmt->execute([$row['id']]);
    $images = array_map(fn($im) => ['id'=>(int)$im['id'],'url'=>image_url($config,$im['file_path']),'sort_order'=>(int)$im['sort_order']], $stmt->fetchAll());
    $sellerSummary = [
        'is_verified'=>(bool)($row['seller_is_verified'] ?? false),
        'rating_average'=>(float)($row['seller_rating_average'] ?? 0),
        'rating_count'=>(int)($row['seller_rating_count'] ?? 0),
    ];
    return [
        'id'=>(int)$row['id'], 'title'=>$row['title'], 'description'=>$row['description'], 'price'=>(float)$row['price'],
        'province'=>$row['province'], 'amphoe'=>$row['amphoe'], 'tambon'=>$row['tambon'], 'status'=>$row['status'], 'created_at'=>$row['created_at'],
        'seller'=>[
            'id'=>(int)($row['user_id'] ?? 0),
            'username'=>$row['seller_username'] ?? '',
            'phone'=>$row['seller_phone'] ?? null,
            'line_id'=>$row['seller_line'] ?? null,
            'member_since'=>$row['seller_created_at'] ?? null,
            'role'=>$row['seller_role'] ?? 'member',
            'is_admin'=>($row['seller_role'] ?? 'member') === 'admin',
            'admin_stars'=>(int)($row['seller_admin_stars'] ?? 0),
            'special_icon'=>$row['seller_special_icon'] ?? null,
            'is_verified'=>$sellerSummary['is_verified'],
            'rating_average'=>$sellerSummary['rating_average'],
            'rating_count'=>$sellerSummary['rating_count'],
        ],
        'images'=>$images,
        'share_url'=>app_base($config).'/share.php?id='.(int)$row['id'],
        'is_premium'=>!empty($row['premium_until']),
        'premium_until'=>$row['premium_until'] ?? null,
        'boosted_at'=>$row['boosted_at'] ?? null,
        'allow_meetup'=>(bool)($row['allow_meetup'] ?? false),
        'allow_buy_now'=>(bool)($row['allow_buy_now'] ?? true),
        'allow_cod'=>(bool)($row['allow_cod'] ?? false),
        'chat_first'=>(bool)($row['chat_first'] ?? true),
        'has_active_order'=>(bool)($row['has_active_order'] ?? false),
        'can_buy'=>($row['status'] ?? '') === 'approved'
            && (bool)($row['allow_buy_now'] ?? true)
            && !(bool)($row['has_active_order'] ?? false)
            && ((bool)($row['seller_is_verified'] ?? false) || (bool)($row['allow_cod'] ?? false))
    ];
}

function fetch_listings(PDO $pdo, array $config, string $where, array $params, string $order, int $limit = 20): array
{
    ensure_v10_schema($pdo);
    $sql = "SELECT l.*,COALESCE(NULLIF(u.display_name,''),u.username) seller_username,u.phone seller_phone,u.line_id seller_line,u.created_at seller_created_at,u.role seller_role,
        u.admin_stars seller_admin_stars,u.special_icon seller_special_icon,
        (SELECT MAX(pp.ends_at) FROM premium_promotions pp
         WHERE pp.listing_id=l.id AND pp.status='active' AND pp.starts_at<=NOW() AND pp.ends_at>NOW()) premium_until,
        EXISTS(SELECT 1 FROM orders o
         WHERE o.listing_id=l.id AND o.status IN ('pending_confirmation','preparing','shipped')) has_active_order,
        EXISTS(SELECT 1 FROM identity_verifications iv
         WHERE iv.user_id=l.user_id AND iv.status='verified') seller_is_verified,
        COALESCE((SELECT ROUND(AVG(sr.rating),1) FROM seller_reviews sr WHERE sr.seller_id=l.user_id),0) seller_rating_average,
        (SELECT COUNT(*) FROM seller_reviews sr2 WHERE sr2.seller_id=l.user_id) seller_rating_count
        FROM listings l JOIN users u ON u.id=l.user_id WHERE $where ORDER BY $order LIMIT " . (int)$limit;
    $stmt = $pdo->prepare($sql); $stmt->execute($params);
    return array_map(fn($row) => hydrate_listing($pdo,$config,$row), $stmt->fetchAll());
}

function csrf_token(): string
{
    if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(24));
    return $_SESSION['csrf'];
}
function verify_csrf(): void
{
    if (!hash_equals($_SESSION['csrf'] ?? '', $_POST['csrf'] ?? '')) die('CSRF token ไม่ถูกต้อง');
}
function require_admin(): void
{
    $adminId = (int)($_SESSION['admin_id'] ?? 0);
    $pdo = $GLOBALS['pdo'] ?? null;
    if ($adminId <= 0 || !($pdo instanceof PDO)) {
        header('Location: login.php');
        exit;
    }

    // Never trust the session flag by itself. The database role/status is the
    // source of truth on every protected Admin page, so demotion/suspension
    // takes effect immediately without waiting for the browser session to end.
    $st = $pdo->prepare("SELECT id,username FROM users WHERE id=? AND role='admin' AND status='active' LIMIT 1");
    $st->execute([$adminId]);
    $admin = $st->fetch();
    if (!$admin) {
        unset($_SESSION['admin_id'], $_SESSION['admin_username']);
        header('Location: login.php');
        exit;
    }
    $_SESSION['admin_username'] = (string)$admin['username'];
}

function require_api_admin(PDO $pdo): array
{
    $user = require_api_user($pdo);
    if (($user['role'] ?? 'member') !== 'admin') {
        json_out(false, 'ไม่มีสิทธิ์ใช้งานระบบผู้ดูแล', null, 403);
    }
    return $user;
}

function default_home_content(): array
{
    return [
        'brand_title' => 'ตลาดพระออนไลน์',
        'headline' => 'ตลาดพระเครื่องสำหรับคนรักพระ',
        'subheadline' => 'ลงขายง่าย • ดูรูปชัด • ติดต่อผู้ขายโดยตรง',
        'trust_title' => 'ซื้อขายมั่นใจ ปลอดภัย',
        'trust_text' => 'ประกาศใหม่ผ่านการตรวจจากแอดมินก่อนเผยแพร่',
        'enabled' => true,
        'updated_at' => null,
    ];
}

function ensure_home_content_schema(PDO $pdo): void
{
    $pdo->exec("CREATE TABLE IF NOT EXISTS home_content (
        id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
        brand_title VARCHAR(80) NOT NULL,
        headline VARCHAR(160) NOT NULL,
        subheadline VARCHAR(255) NOT NULL,
        trust_title VARCHAR(160) NOT NULL DEFAULT 'ซื้อขายมั่นใจ ปลอดภัย',
        trust_text VARCHAR(255) NOT NULL,
        is_active TINYINT(1) NOT NULL DEFAULT 1,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $column = $pdo->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='home_content' AND COLUMN_NAME='trust_title'");
    $column->execute();
    if ((int)$column->fetchColumn() === 0) {
        $pdo->exec("ALTER TABLE home_content ADD COLUMN trust_title VARCHAR(160) NOT NULL DEFAULT 'ซื้อขายมั่นใจ ปลอดภัย' AFTER subheadline");
    }
    $defaults = default_home_content();
    $stmt = $pdo->prepare("INSERT IGNORE INTO home_content(id,brand_title,headline,subheadline,trust_title,trust_text,is_active) VALUES(1,?,?,?,?,?,1)");
    $stmt->execute([$defaults['brand_title'],$defaults['headline'],$defaults['subheadline'],$defaults['trust_title'],$defaults['trust_text']]);
}

function fetch_home_content(PDO $pdo): array
{
    $defaults = default_home_content();
    try {
        $row = $pdo->query("SELECT brand_title,headline,subheadline,trust_title,trust_text,is_active,updated_at FROM home_content WHERE id=1 LIMIT 1")->fetch();
        if (!$row) return $defaults;
        return [
            'brand_title' => (string)$row['brand_title'],
            'headline' => (string)$row['headline'],
            'subheadline' => (string)$row['subheadline'],
            'trust_title' => (string)($row['trust_title'] ?? $defaults['trust_title']),
            'trust_text' => (string)$row['trust_text'],
            'enabled' => (bool)$row['is_active'],
            'updated_at' => $row['updated_at'] ?? null,
        ];
    } catch (Throwable $e) {
        // Existing installations may not have run the V3 migration yet.
        // Keep the public API working with safe defaults until admin opens the settings page.
        return $defaults;
    }
}

function ensure_chat_schema(PDO $pdo): void
{
    static $ready = false;
    if ($ready) return;
    try {
        $pdo->query('SELECT id FROM chat_messages LIMIT 1');
        $ready = true;
        return;
    } catch (Throwable $e) {
        // Older installs did not always run migration_chat.sql.
    }
    $pdo->exec("CREATE TABLE IF NOT EXISTS chat_messages (
        id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        listing_id BIGINT UNSIGNED NOT NULL,
        buyer_id INT UNSIGNED NOT NULL,
        sender_id INT UNSIGNED NOT NULL,
        message VARCHAR(1000) NOT NULL DEFAULT '',
        image_path VARCHAR(255) NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT fk_chat_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
        CONSTRAINT fk_chat_buyer FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE CASCADE,
        CONSTRAINT fk_chat_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
        INDEX idx_chat_thread (listing_id,buyer_id,created_at),
        INDEX idx_chat_sender (sender_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $ready = true;
}



/**
 * V5 schema is intentionally self-healing so an existing installation can be
 * upgraded by replacing the backend files without asking the owner to run SQL.
 */
function ensure_v5_schema(PDO $pdo): void
{
    static $ready = false;
    if ($ready) return;

    ensure_chat_schema($pdo);

    $pdo->exec("CREATE TABLE IF NOT EXISTS push_tokens (
        id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        user_id INT UNSIGNED NOT NULL,
        token VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY uq_push_token (token),
        INDEX idx_push_user (user_id),
        CONSTRAINT fk_push_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS favorites (
        user_id INT UNSIGNED NOT NULL,
        listing_id BIGINT UNSIGNED NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (user_id, listing_id),
        INDEX idx_favorites_listing (listing_id),
        CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        CONSTRAINT fk_favorites_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // Older chat tables do not have image_path. Avoid ADD COLUMN IF NOT EXISTS
    // because some shared-hosting MySQL/MariaDB versions do not support it.
    $check = $pdo->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='chat_messages' AND COLUMN_NAME='image_path'");
    $check->execute();
    if ((int)$check->fetchColumn() === 0) {
        $pdo->exec("ALTER TABLE chat_messages ADD COLUMN image_path VARCHAR(255) NULL AFTER message");
    }

    $ready = true;
}

function save_chat_image(array $config, array $file): string
{
    if (!$file || ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
        throw new RuntimeException('กรุณาเลือกรูปภาพ');
    }
    if ((int)($file['size'] ?? 0) > (int)$config['app']['upload_max_bytes']) {
        throw new RuntimeException('รูปมีขนาดเกิน 5 MB');
    }

    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime = $finfo->file((string)$file['tmp_name']);
    $allowed = ['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp'];
    if (!isset($allowed[$mime])) {
        throw new RuntimeException('รองรับเฉพาะ JPG, PNG และ WEBP');
    }

    $dir = dirname(__DIR__) . '/uploads/chat/' . date('Y/m');
    if (!is_dir($dir) && !mkdir($dir, 0755, true) && !is_dir($dir)) {
        throw new RuntimeException('สร้างโฟลเดอร์รูปแชทไม่ได้');
    }

    $name = bin2hex(random_bytes(16)) . '.' . $allowed[$mime];
    $full = $dir . '/' . $name;
    if (!move_uploaded_file((string)$file['tmp_name'], $full)) {
        throw new RuntimeException('บันทึกรูปแชทไม่สำเร็จ');
    }
    return 'uploads/chat/' . date('Y/m') . '/' . $name;
}

function chat_message_payload(array $config, array $row): array
{
    $imagePath = $row['image_path'] ?? null;
    return [
        'id' => (int)$row['id'],
        'sender_id' => (int)$row['sender_id'],
        'message' => (string)($row['message'] ?? ''),
        'image_url' => $imagePath ? image_url($config, (string)$imagePath) : null,
        'read_at' => $row['read_at'] ?? null,
        'is_read' => !empty($row['read_at']),
        'created_at' => (string)$row['created_at'],
    ];
}

function is_listing_favorite(PDO $pdo, int $userId, int $listingId): bool
{
    ensure_v5_schema($pdo);
    $stmt = $pdo->prepare('SELECT 1 FROM favorites WHERE user_id=? AND listing_id=? LIMIT 1');
    $stmt->execute([$userId, $listingId]);
    return (bool)$stmt->fetchColumn();
}


/**
 * V6: points wallet + manual top-up requests + premium listing placement.
 * Kept separate from users/listings so balances and spending remain auditable.
 */
function ensure_v6_schema(PDO $pdo): void
{
    static $ready = false;
    if ($ready) return;

    // Fast path for normal requests after V6 has been installed. Avoid DDL on every API call.
    try {
        $pdo->query("SELECT user_id,balance FROM point_wallets LIMIT 1");
        $pdo->query("SELECT id FROM point_transactions LIMIT 1");
        $pdo->query("SELECT id FROM point_topup_packages LIMIT 1");
        $pdo->query("SELECT id FROM point_topup_requests LIMIT 1");
        $pdo->query("SELECT id FROM premium_plans LIMIT 1");
        $pdo->query("SELECT id FROM premium_promotions LIMIT 1");
        $pdo->exec("UPDATE premium_promotions SET status='expired' WHERE status='active' AND ends_at<=NOW()");
        $ready = true;
        return;
    } catch (Throwable $e) {
        // First request after upgrading from V5: fall through and create the V6 schema.
    }

    ensure_v5_schema($pdo);

    $pdo->exec("CREATE TABLE IF NOT EXISTS point_wallets (
        user_id INT UNSIGNED NOT NULL PRIMARY KEY,
        balance INT UNSIGNED NOT NULL DEFAULT 0,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        CONSTRAINT fk_point_wallet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS point_transactions (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS point_topup_packages (
        id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        points INT UNSIGNED NOT NULL,
        price DECIMAL(10,2) NOT NULL,
        is_active TINYINT(1) NOT NULL DEFAULT 1,
        sort_order INT NOT NULL DEFAULT 0,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS point_topup_requests (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS premium_plans (
        id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        points_cost INT UNSIGNED NOT NULL,
        duration_days INT UNSIGNED NOT NULL,
        is_active TINYINT(1) NOT NULL DEFAULT 1,
        sort_order INT NOT NULL DEFAULT 0,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS premium_promotions (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // Seed defaults only once; admins can edit/disable them later.
    $count = (int)$pdo->query("SELECT COUNT(*) FROM point_topup_packages")->fetchColumn();
    if ($count === 0) {
        $pdo->exec("INSERT INTO point_topup_packages(name,points,price,sort_order) VALUES
            ('เริ่มต้น 100 แต้ม',100,100.00,10),
            ('คุ้มค่า 300 แต้ม',330,300.00,20),
            ('ร้านจริงจัง 500 แต้ม',575,500.00,30)");
    }

    $count = (int)$pdo->query("SELECT COUNT(*) FROM premium_plans")->fetchColumn();
    if ($count === 0) {
        $pdo->exec("INSERT INTO premium_plans(name,points_cost,duration_days,sort_order) VALUES
            ('ดันเด่น 1 วัน',20,1,10),
            ('พรีเมียม 3 วัน',50,3,20),
            ('พรีเมียม 7 วัน',100,7,30)");
    }

    // Make sure every current user has a wallet; new users are lazily created too.
    $pdo->exec("INSERT IGNORE INTO point_wallets(user_id,balance) SELECT id,0 FROM users");

    // Keep campaign state tidy without needing a cron job.
    $pdo->exec("UPDATE premium_promotions SET status='expired' WHERE status='active' AND ends_at<=NOW()");
    $ready = true;
}



/**
 * V7: arbitrary point top-up with bank transfer slip, real presence,
 * member reports and scheduled admin notifications.
 */
function ensure_v7_schema(PDO $pdo): void
{
    static $ready = false;
    if ($ready) return;

    ensure_v6_schema($pdo);

    $pdo->exec("CREATE TABLE IF NOT EXISTS payment_settings (
        id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
        bank_name VARCHAR(120) NOT NULL,
        account_name VARCHAR(160) NOT NULL,
        account_number VARCHAR(80) NOT NULL,
        points_per_baht DECIMAL(10,4) NOT NULL DEFAULT 1.0000,
        min_amount DECIMAL(10,2) NOT NULL DEFAULT 20.00,
        is_active TINYINT(1) NOT NULL DEFAULT 1,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $pdo->exec("INSERT IGNORE INTO payment_settings(id,bank_name,account_name,account_number,points_per_baht,min_amount,is_active)
        VALUES(1,'กรุณาตั้งค่าในแอดมิน','กรุณาตั้งชื่อบัญชี','-',1.0000,20.00,1)");

    $check = $pdo->prepare("SELECT IS_NULLABLE FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='point_topup_requests' AND COLUMN_NAME='package_id'");
    $check->execute();
    $nullable = (string)$check->fetchColumn();
    if ($nullable !== 'YES') {
        $pdo->exec("ALTER TABLE point_topup_requests MODIFY package_id INT UNSIGNED NULL");
    }

    foreach ([
        'slip_path' => "ALTER TABLE point_topup_requests ADD COLUMN slip_path VARCHAR(255) NULL AFTER note",
        'payment_snapshot' => "ALTER TABLE point_topup_requests ADD COLUMN payment_snapshot VARCHAR(500) NULL AFTER slip_path"
    ] as $column => $sql) {
        $c = $pdo->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='point_topup_requests' AND COLUMN_NAME=?");
        $c->execute([$column]);
        if ((int)$c->fetchColumn() === 0) $pdo->exec($sql);
    }

    $pdo->exec("CREATE TABLE IF NOT EXISTS app_presence (
        client_id CHAR(64) NOT NULL PRIMARY KEY,
        user_id INT UNSIGNED NULL,
        last_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_presence_last_seen (last_seen),
        INDEX idx_presence_user (user_id),
        CONSTRAINT fk_presence_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS user_reports (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS scheduled_notifications (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // Only replace the original default brand; custom admin branding is preserved.
    $pdo->exec("UPDATE home_content SET brand_title='ตลาดพระออนไลน์' WHERE brand_title='ขายพระบ้าน'");
    $pdo->exec("DELETE FROM app_presence WHERE last_seen < DATE_SUB(NOW(), INTERVAL 7 DAY)");
    $ready = true;
}


/**
 * V8: fixes the V7 mixed PHP/MySQL clock issue, adds a real boost timestamp,
 * and adds simple one-item orders.  The migration remains in-place and keeps
 * all V7 data.
 */
function ensure_v8_schema(PDO $pdo): void
{
    static $ready = false;
    if ($ready) return;

    ensure_v7_schema($pdo);

    // Fast path: normal requests only verify the V8 objects, never run ALTERs.
    try {
        $pdo->query("SELECT boosted_at FROM listings LIMIT 1");
        $pdo->query("SELECT request_key FROM premium_promotions LIMIT 1");
        $pdo->query("SELECT id FROM boost_settings LIMIT 1");
        $pdo->query("SELECT id FROM listing_boosts LIMIT 1");
        $pdo->query("SELECT order_id FROM orders LIMIT 1");
        $enum = (string)$pdo->query("SELECT COLUMN_TYPE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='point_transactions' AND COLUMN_NAME='type'")->fetchColumn();
        if (strpos($enum, 'boost_purchase') === false) throw new RuntimeException('V8 point enum missing');
        $pdo->exec("UPDATE premium_promotions SET status='expired' WHERE status='active' AND ends_at<=NOW()");
        $ready = true;
        return;
    } catch (Throwable $e) {
        // First request after upgrading V7: apply only missing objects below.
    }

    $columnExists = static function(PDO $db, string $table, string $column): bool {
        $st = $db->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?");
        $st->execute([$table,$column]);
        return (int)$st->fetchColumn() > 0;
    };
    $indexExists = static function(PDO $db, string $table, string $index): bool {
        $st = $db->prepare("SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?");
        $st->execute([$table,$index]);
        return (int)$st->fetchColumn() > 0;
    };

    if (!$columnExists($pdo,'listings','boosted_at')) {
        $pdo->exec("ALTER TABLE listings ADD COLUMN boosted_at DATETIME NULL AFTER status");
    }
    if (!$indexExists($pdo,'listings','idx_listing_status_boosted')) {
        $pdo->exec("ALTER TABLE listings ADD INDEX idx_listing_status_boosted (status,boosted_at,created_at)");
    }
    if (!$columnExists($pdo,'premium_promotions','request_key')) {
        $pdo->exec("ALTER TABLE premium_promotions ADD COLUMN request_key CHAR(36) NULL AFTER points_spent");
    }
    if (!$indexExists($pdo,'premium_promotions','uq_premium_request')) {
        $pdo->exec("ALTER TABLE premium_promotions ADD UNIQUE KEY uq_premium_request (request_key)");
    }

    $pdo->exec("ALTER TABLE point_transactions
        MODIFY type ENUM('topup','premium_purchase','boost_purchase','admin_adjustment','refund') NOT NULL");

    $pdo->exec("CREATE TABLE IF NOT EXISTS boost_settings (
        id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
        points_cost INT UNSIGNED NOT NULL DEFAULT 20,
        cooldown_minutes INT UNSIGNED NOT NULL DEFAULT 10,
        is_active TINYINT(1) NOT NULL DEFAULT 1,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $pdo->exec("INSERT IGNORE INTO boost_settings(id,points_cost,cooldown_minutes,is_active) VALUES(1,20,10,1)");

    $pdo->exec("CREATE TABLE IF NOT EXISTS listing_boosts (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS orders (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->prepare("UPDATE premium_plans SET name='พรีเมียมเด่น 1 วัน' WHERE name='ดันเด่น 1 วัน' AND points_cost=20 AND duration_days=1")->execute();
    $pdo->exec("UPDATE premium_promotions SET status='expired' WHERE status='active' AND ends_at<=NOW()");
    $ready = true;
}


/**
 * V9 adds multi-banner Home content, member identity verification, seller
 * ratings, chat unread state, and seller payment snapshots on orders.
 * It upgrades in place and never drops V8 data.
 */
function ensure_v9_schema(PDO $pdo): void
{
    static $ready = false;
    if ($ready) return;

    ensure_v8_schema($pdo);

    $columnExists = static function(PDO $db, string $table, string $column): bool {
        $st = $db->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?");
        $st->execute([$table,$column]);
        return (int)$st->fetchColumn() > 0;
    };
    $indexExists = static function(PDO $db, string $table, string $index): bool {
        $st = $db->prepare("SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?");
        $st->execute([$table,$index]);
        return (int)$st->fetchColumn() > 0;
    };
    $tableExists = static function(PDO $db, string $table): bool {
        $st = $db->prepare("SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?");
        $st->execute([$table]);
        return (int)$st->fetchColumn() > 0;
    };
    $homeBannersExisted = $tableExists($pdo,'home_banners');

    try {
        $pdo->query("SELECT trust_title FROM home_content LIMIT 1");
        $pdo->query("SELECT id FROM home_banners LIMIT 1");
        $pdo->query("SELECT user_id FROM identity_verifications LIMIT 1");
        $pdo->query("SELECT id FROM seller_reviews LIMIT 1");
        $pdo->query("SELECT read_at FROM chat_messages LIMIT 1");
        $pdo->query("SELECT seller_verified,seller_account_number_snapshot FROM orders LIMIT 1");
        if (!$indexExists($pdo,'chat_messages','idx_chat_thread_read')) {
            throw new RuntimeException('V9 unread index missing');
        }
        $ready = true;
        return;
    } catch (Throwable $e) {
        // Apply only the missing V9 pieces below.
    }

    ensure_home_content_schema($pdo);

    $pdo->exec("CREATE TABLE IF NOT EXISTS home_banners (
        id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        image_path VARCHAR(255) NOT NULL,
        is_active TINYINT(1) NOT NULL DEFAULT 1,
        sort_order INT NOT NULL DEFAULT 0,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX idx_home_banners_active_sort (is_active,sort_order,id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    if (!$homeBannersExisted && is_file(dirname(__DIR__).'/uploads/banners/default_home_v8.png')) {
        $pdo->prepare("INSERT INTO home_banners(image_path,is_active,sort_order) VALUES(?,1,0)")
            ->execute(['uploads/banners/default_home_v8.png']);
    }

    $pdo->exec("CREATE TABLE IF NOT EXISTS identity_verifications (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS seller_reviews (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $addedReadAt = false;
    if (!$columnExists($pdo,'chat_messages','read_at')) {
        $pdo->exec("ALTER TABLE chat_messages ADD COLUMN read_at DATETIME NULL AFTER image_path");
        $addedReadAt = true;
    }
    if ($addedReadAt) {
        // Old V8 messages have no unread semantics. Do this once only.
        $pdo->exec("UPDATE chat_messages SET read_at=created_at WHERE read_at IS NULL");
    }
    if (!$indexExists($pdo,'chat_messages','idx_chat_thread_read')) {
        $pdo->exec("ALTER TABLE chat_messages ADD INDEX idx_chat_thread_read (listing_id,buyer_id,read_at,sender_id)");
    }

    foreach ([
        'seller_verified' => "ALTER TABLE orders ADD COLUMN seller_verified TINYINT(1) NOT NULL DEFAULT 0 AFTER cover_path_snapshot",
        'seller_bank_name_snapshot' => "ALTER TABLE orders ADD COLUMN seller_bank_name_snapshot VARCHAR(120) NULL AFTER seller_verified",
        'seller_account_name_snapshot' => "ALTER TABLE orders ADD COLUMN seller_account_name_snapshot VARCHAR(160) NULL AFTER seller_bank_name_snapshot",
        'seller_account_number_snapshot' => "ALTER TABLE orders ADD COLUMN seller_account_number_snapshot VARCHAR(80) NULL AFTER seller_account_name_snapshot",
        'seller_verified_at_snapshot' => "ALTER TABLE orders ADD COLUMN seller_verified_at_snapshot DATETIME NULL AFTER seller_account_number_snapshot",
    ] as $column => $sql) {
        if (!$columnExists($pdo,'orders',$column)) $pdo->exec($sql);
    }

    $ready = true;
}

function home_banners(PDO $pdo, array $config, bool $activeOnly = true): array
{
    ensure_v9_schema($pdo);
    $sql = "SELECT id,image_path,is_active,sort_order,created_at,updated_at FROM home_banners";
    if ($activeOnly) $sql .= " WHERE is_active=1";
    $sql .= " ORDER BY sort_order ASC,id ASC";
    $rows = $pdo->query($sql)->fetchAll();
    return array_map(static fn(array $row): array => [
        'id'=>(int)$row['id'],
        'image_url'=>image_url($config,(string)$row['image_path']),
        'is_active'=>(bool)$row['is_active'],
        'sort_order'=>(int)$row['sort_order'],
        'created_at'=>$row['created_at'],
        'updated_at'=>$row['updated_at'],
    ], $rows);
}

function save_banner_image(array $config, array $file): string
{
    if (!$file || ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
        throw new RuntimeException('กรุณาเลือกรูป Banner');
    }
    $maxBytes = min(max((int)($config['app']['upload_max_bytes'] ?? 5242880), 1024 * 1024), 8 * 1024 * 1024);
    if ((int)($file['size'] ?? 0) > $maxBytes) throw new RuntimeException('รูป Banner มีขนาดใหญ่เกินไป');

    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime = $finfo->file((string)$file['tmp_name']);
    $allowed = ['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp'];
    if (!isset($allowed[$mime])) throw new RuntimeException('Banner รองรับ JPG, PNG และ WEBP');

    $dir = dirname(__DIR__) . '/uploads/banners/' . date('Y/m');
    if (!is_dir($dir) && !mkdir($dir,0755,true) && !is_dir($dir)) {
        throw new RuntimeException('สร้างโฟลเดอร์ Banner ไม่ได้');
    }
    $name = bin2hex(random_bytes(18)).'.'.$allowed[$mime];
    $full = $dir.'/'.$name;
    if (!move_uploaded_file((string)$file['tmp_name'],$full)) throw new RuntimeException('บันทึก Banner ไม่สำเร็จ');
    return 'uploads/banners/'.date('Y/m').'/'.$name;
}


/**
 * V14 prize image upload. Uses the same public uploads protection and MIME
 * validation policy as Home banners, but stores prize assets separately.
 */
function save_lottery_prize_image(array $config, array $file): string
{
    if (!$file || ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
        throw new RuntimeException('กรุณาเลือกรูปรางวัล');
    }
    $maxBytes = min(max((int)($config['app']['upload_max_bytes'] ?? 5242880), 1024 * 1024), 8 * 1024 * 1024);
    if ((int)($file['size'] ?? 0) > $maxBytes) throw new RuntimeException('รูปรางวัลมีขนาดใหญ่เกินไป');

    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime = $finfo->file((string)$file['tmp_name']);
    $allowed = ['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp'];
    if (!isset($allowed[$mime])) throw new RuntimeException('รูปรางวัลรองรับ JPG, PNG และ WEBP');

    $relativeDir = 'uploads/lottery/'.date('Y/m');
    $dir = dirname(__DIR__).'/'.$relativeDir;
    if (!is_dir($dir) && !mkdir($dir,0755,true) && !is_dir($dir)) {
        throw new RuntimeException('สร้างโฟลเดอร์รูปรางวัลไม่ได้');
    }
    $name = bin2hex(random_bytes(18)).'.'.$allowed[$mime];
    $full = $dir.'/'.$name;
    if (!move_uploaded_file((string)$file['tmp_name'],$full)) throw new RuntimeException('บันทึกรูปรางวัลไม่สำเร็จ');
    return $relativeDir.'/'.$name;
}

function private_storage_root(array $config): string
{
    $configured = trim((string)($config['app']['private_storage_path'] ?? ''));
    // Default one level ABOVE the backend/document root. Production should set
    // an explicit absolute path outside public_html when the hosting layout differs.
    $root = $configured !== '' ? rtrim($configured,'/\\') : dirname(dirname(__DIR__)).'/khaiphraban_private';
    if (!is_dir($root) && !mkdir($root,0750,true) && !is_dir($root)) {
        throw new RuntimeException('สร้างพื้นที่เก็บเอกสารส่วนตัวไม่ได้');
    }

    // Defence in depth if a hosting layout accidentally places this directory
    // under a web root. Apache/LiteSpeed will refuse direct HTTP access.
    $denyFile=$root.'/.htaccess';
    if(!is_file($denyFile)) {
        @file_put_contents($denyFile,"Options -Indexes\n<IfModule mod_authz_core.c>\nRequire all denied\n</IfModule>\n<IfModule !mod_authz_core.c>\nDeny from all\n</IfModule>\n");
        @chmod($denyFile,0640);
    }
    return $root;
}

function identity_document_full_path(array $config, string $relative): string
{
    $relative = ltrim(str_replace('\\','/',$relative),'/');
    if ($relative === '' || str_contains($relative,'..') || !preg_match('#^identity/[0-9]{4}/[0-9]{2}/[a-f0-9]{36}\.(jpg|png|webp)$#',$relative)) {
        throw new RuntimeException('path เอกสารยืนยันตัวตนไม่ถูกต้อง');
    }
    return private_storage_root($config).'/'.$relative;
}

function save_identity_document(array $config, array $file): string
{
    if (!$file || ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
        throw new RuntimeException('กรุณาแนบรูปถ่ายคู่กับสมุดบัญชีธนาคาร');
    }
    $maxBytes = min(max((int)($config['app']['upload_max_bytes'] ?? 5242880), 1024 * 1024), 8 * 1024 * 1024);
    if ((int)($file['size'] ?? 0) > $maxBytes) throw new RuntimeException('รูปหลักฐานมีขนาดใหญ่เกินไป');

    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime = $finfo->file((string)$file['tmp_name']);
    $allowed = ['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp'];
    if (!isset($allowed[$mime])) throw new RuntimeException('หลักฐานรองรับ JPG, PNG และ WEBP');

    $relative = 'identity/'.date('Y/m');
    $dir = private_storage_root($config).'/'.$relative;
    if (!is_dir($dir) && !mkdir($dir,0750,true) && !is_dir($dir)) {
        throw new RuntimeException('สร้างโฟลเดอร์หลักฐานไม่ได้');
    }
    $name = bin2hex(random_bytes(18)).'.'.$allowed[$mime];
    $full = $dir.'/'.$name;
    if (!move_uploaded_file((string)$file['tmp_name'],$full)) throw new RuntimeException('บันทึกหลักฐานไม่สำเร็จ');
    @chmod($full,0640);
    return $relative.'/'.$name;
}

function order_payment_slip_full_path(array $config, string $relative): string
{
    $relative = ltrim(str_replace('\\','/',$relative),'/');
    if ($relative === '' || str_contains($relative,'..') || !preg_match('#^order_slips/[0-9]{4}/[0-9]{2}/[a-f0-9]{36}\.(jpg|png|webp)$#',$relative)) {
        throw new RuntimeException('path สลิปคำสั่งซื้อไม่ถูกต้อง');
    }
    return private_storage_root($config).'/'.$relative;
}

function save_order_payment_slip(array $config, array $file): string
{
    if (!$file || ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
        throw new RuntimeException('กรุณาแนบสลิปการโอนเงิน');
    }
    $maxBytes = min(max((int)($config['app']['upload_max_bytes'] ?? 5242880), 1024 * 1024), 8 * 1024 * 1024);
    if ((int)($file['size'] ?? 0) > $maxBytes) throw new RuntimeException('รูปสลิปมีขนาดใหญ่เกินไป');

    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime = $finfo->file((string)$file['tmp_name']);
    $allowed = ['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp'];
    if (!isset($allowed[$mime])) throw new RuntimeException('สลิปรองรับ JPG, PNG และ WEBP');

    $relative = 'order_slips/'.date('Y/m');
    $dir = private_storage_root($config).'/'.$relative;
    if (!is_dir($dir) && !mkdir($dir,0750,true) && !is_dir($dir)) {
        throw new RuntimeException('สร้างโฟลเดอร์สลิปคำสั่งซื้อไม่ได้');
    }
    $name = bin2hex(random_bytes(18)).'.'.$allowed[$mime];
    $full = $dir.'/'.$name;
    if (!move_uploaded_file((string)$file['tmp_name'],$full)) throw new RuntimeException('บันทึกสลิปไม่สำเร็จ');
    @chmod($full,0640);
    return $relative.'/'.$name;
}

function normalize_display_name(string $value): string
{
    $value = preg_replace('/\s+/u',' ',trim($value)) ?? trim($value);
    if (mb_strlen($value) < 2 || mb_strlen($value) > 40) {
        throw new RuntimeException('ชื่อที่แสดงต้องมี 2–40 ตัวอักษร');
    }
    if (!preg_match('/^[\p{L}\p{M}\p{N} ._\-]+$/u',$value)) {
        throw new RuntimeException('ชื่อที่แสดงใช้ได้เฉพาะภาษาไทย/ตัวอักษร ตัวเลข เว้นวรรค จุด ขีด และ _');
    }
    return $value;
}

function pending_display_name_request(PDO $pdo, int $userId): ?array
{
    ensure_v10_schema($pdo);
    $st=$pdo->prepare("SELECT id,requested_name,reason,status,admin_note,created_at,reviewed_at
        FROM display_name_change_requests
        WHERE user_id=? AND status='pending' ORDER BY id DESC LIMIT 1");
    $st->execute([$userId]);
    $row=$st->fetch();
    if(!$row) return null;
    $row['id']=(int)$row['id'];
    return $row;
}

function verification_status_label(string $status): string
{
    return match($status) {
        'pending' => 'รอตรวจสอบ',
        'verified' => 'ยืนยันแล้ว',
        'rejected' => 'ไม่ผ่าน',
        default => 'ยังไม่ยืนยัน',
    };
}

function identity_verification_payload(PDO $pdo, int $userId, bool $includeBank = true): array
{
    ensure_v9_schema($pdo);
    $st = $pdo->prepare("SELECT user_id,bank_name,account_name,account_number,status,rejection_reason,
            submitted_at,reviewed_at,verified_at,updated_at
        FROM identity_verifications WHERE user_id=? LIMIT 1");
    $st->execute([$userId]);
    $row = $st->fetch();
    if (!$row) {
        return [
            'status'=>'unverified','status_label'=>verification_status_label('unverified'),
            'is_verified'=>false,'bank_name'=>'','account_name'=>'','account_number'=>'',
            'rejection_reason'=>null,'submitted_at'=>null,'reviewed_at'=>null,'verified_at'=>null,
        ];
    }
    return [
        'status'=>(string)$row['status'],
        'status_label'=>verification_status_label((string)$row['status']),
        'is_verified'=>$row['status']==='verified',
        'bank_name'=>$includeBank ? (string)$row['bank_name'] : '',
        'account_name'=>$includeBank ? (string)$row['account_name'] : '',
        'account_number'=>$includeBank ? (string)$row['account_number'] : '',
        'rejection_reason'=>$row['rejection_reason'],
        'submitted_at'=>$row['submitted_at'],
        'reviewed_at'=>$row['reviewed_at'],
        'verified_at'=>$row['verified_at'],
        'updated_at'=>$row['updated_at'],
    ];
}

function verified_bank_account(PDO $pdo, int $userId): ?array
{
    ensure_v9_schema($pdo);
    $st = $pdo->prepare("SELECT bank_name,account_name,account_number,verified_at
        FROM identity_verifications WHERE user_id=? AND status='verified' LIMIT 1");
    $st->execute([$userId]);
    $row = $st->fetch();
    if (!$row) return null;
    return [
        'is_verified'=>true,
        'bank_name'=>(string)$row['bank_name'],
        'account_name'=>(string)$row['account_name'],
        'account_number'=>(string)$row['account_number'],
        'verified_at'=>$row['verified_at'],
    ];
}

function seller_public_summary(PDO $pdo, int $userId): array
{
    ensure_v9_schema($pdo);
    if ($userId <= 0) return ['is_verified'=>false,'rating_average'=>0.0,'rating_count'=>0];
    $st = $pdo->prepare("SELECT
        EXISTS(SELECT 1 FROM identity_verifications iv WHERE iv.user_id=? AND iv.status='verified') is_verified,
        COALESCE((SELECT ROUND(AVG(sr.rating),1) FROM seller_reviews sr WHERE sr.seller_id=?),0) rating_average,
        (SELECT COUNT(*) FROM seller_reviews sr2 WHERE sr2.seller_id=?) rating_count");
    $st->execute([$userId,$userId,$userId]);
    $row = $st->fetch() ?: [];
    return [
        'is_verified'=>(bool)($row['is_verified'] ?? false),
        'rating_average'=>(float)($row['rating_average'] ?? 0),
        'rating_count'=>(int)($row['rating_count'] ?? 0),
    ];
}

function member_profile_payload(PDO $pdo, array $config, int $userId): ?array
{
    ensure_v10_schema($pdo);
    $st = $pdo->prepare("SELECT id,username,display_name,admin_stars,special_icon,role,created_at,status FROM users WHERE id=? LIMIT 1");
    $st->execute([$userId]);
    $user = $st->fetch();
    if (!$user || $user['status'] !== 'active') return null;
    $summary = seller_public_summary($pdo,$userId);
    $listings = fetch_listings(
        $pdo,$config,"l.user_id=? AND l.status IN ('approved','sold')",[$userId],
        "l.created_at DESC",100
    );
    return [
        'id'=>(int)$user['id'],
        'username'=>(string)($user['display_name'] ?: $user['username']),
        'display_name'=>(string)($user['display_name'] ?: $user['username']),
        'admin_stars'=>(int)($user['admin_stars'] ?? 0),
        'special_icon'=>$user['special_icon'] ?: null,
        'member_since'=>$user['created_at'],
        'role'=>(string)($user['role'] ?? 'member'),
        'is_admin'=>(($user['role'] ?? 'member') === 'admin'),
        'is_verified'=>$summary['is_verified'],
        'verification_label'=>$summary['is_verified'] ? 'ยืนยันแล้ว' : 'ยังไม่ยืนยันตัวตน',
        'rating_average'=>$summary['rating_average'],
        'rating_count'=>$summary['rating_count'],
        'listings'=>$listings,
    ];
}

function chat_unread_count(PDO $pdo, int $userId): int
{
    ensure_v9_schema($pdo);
    $st = $pdo->prepare("SELECT COUNT(*)
        FROM chat_messages cm
        JOIN listings l ON l.id=cm.listing_id
        WHERE cm.read_at IS NULL AND cm.sender_id<>?
          AND (cm.buyer_id=? OR l.user_id=?)");
    $st->execute([$userId,$userId,$userId]);
    return (int)$st->fetchColumn();
}


function boost_settings(PDO $pdo): array
{
    ensure_v8_schema($pdo);
    $row = $pdo->query("SELECT points_cost,cooldown_minutes,is_active,updated_at FROM boost_settings WHERE id=1 LIMIT 1")->fetch();
    return [
        'points_cost'=>(int)($row['points_cost'] ?? 20),
        'cooldown_minutes'=>(int)($row['cooldown_minutes'] ?? 10),
        'is_active'=>(bool)($row['is_active'] ?? true),
        'updated_at'=>$row['updated_at'] ?? null,
    ];
}

function valid_request_key(string $value): string
{
    $value = strtolower(trim($value));
    if (!preg_match('/^[a-f0-9-]{20,36}$/', $value)) {
        // Old clients may not send a key. Generate one server-side; concurrency
        // is still protected by row locks and active-state checks.
        return bin2hex(random_bytes(16));
    }
    return $value;
}

function order_status_label(string $status): string
{
    return match($status) {
        'pending_confirmation' => 'รอผู้ขายยืนยัน',
        'preparing' => 'กำลังเตรียมสินค้า',
        'shipped' => 'จัดส่งแล้ว',
        'completed' => 'สำเร็จ',
        'cancelled' => 'ยกเลิก',
        default => $status,
    };
}

function order_payload(PDO $pdo, array $config, int $orderId, ?int $viewerUserId = null): ?array
{
    ensure_v10_schema($pdo);
    $st = $pdo->prepare("SELECT o.*,
        COALESCE(NULLIF(buyer.display_name,''),buyer.username) buyer_username,
        COALESCE(NULLIF(seller.display_name,''),seller.username) seller_username
        FROM orders o
        JOIN users buyer ON buyer.id=o.buyer_id
        JOIN users seller ON seller.id=o.seller_id
        WHERE o.order_id=? LIMIT 1");
    $st->execute([$orderId]);
    $row = $st->fetch();
    if (!$row) return null;

    $viewerRole = null;
    if ($viewerUserId !== null) {
        if ((int)$row['seller_id'] === $viewerUserId) {
            $viewerRole = 'seller';
        } elseif ((int)$row['buyer_id'] === $viewerUserId) {
            $viewerRole = 'buyer';
        }
    }

    $reviewSt = $pdo->prepare("SELECT rating,review_text,created_at FROM seller_reviews WHERE order_id=? LIMIT 1");
    $reviewSt->execute([$orderId]);
    $review = $reviewSt->fetch();
    $sellerSummary = seller_public_summary($pdo,(int)$row['seller_id']);
    $canRate = $viewerRole === 'buyer' && $row['status'] === 'completed' && !$review;

    return [
        'order_id'=>(int)$row['order_id'],
        'listing_id'=>(int)$row['listing_id'],
        'buyer_id'=>(int)$row['buyer_id'],
        'seller_id'=>(int)$row['seller_id'],
        'viewer_role'=>$viewerRole,
        'buyer_username'=>(string)$row['buyer_username'],
        'seller_username'=>(string)$row['seller_username'],
        'price'=>(float)$row['price_snapshot'],
        'title'=>(string)$row['title_snapshot'],
        'cover_url'=>!empty($row['cover_path_snapshot']) ? image_url($config,(string)$row['cover_path_snapshot']) : null,
        'seller_verified'=>(bool)($row['seller_verified'] ?? false),
        'seller_bank_name'=>$row['seller_bank_name_snapshot'] ?? null,
        'seller_account_name'=>$row['seller_account_name_snapshot'] ?? null,
        'seller_account_number'=>$row['seller_account_number_snapshot'] ?? null,
        'seller_verified_at'=>$row['seller_verified_at_snapshot'] ?? null,
        'payment_method'=>$row['payment_method'] ?? null,
        'payment_method_label'=>match((string)($row['payment_method'] ?? '')) {
            'bank_transfer' => 'ชำระเงินทันที (โอนเงิน)',
            'cod' => 'เก็บเงินปลายทาง',
            default => 'การชำระเงินแบบเดิม',
        },
        'has_payment_slip'=>!empty($row['payment_slip_path']),
        'seller_rating_average'=>$sellerSummary['rating_average'],
        'seller_rating_count'=>$sellerSummary['rating_count'],
        'can_rate'=>$canRate,
        'review_rating'=>$review ? (int)$review['rating'] : null,
        'review_text'=>$review['review_text'] ?? null,
        'reviewed_at'=>$review['created_at'] ?? null,
        'recipient_name'=>(string)$row['recipient_name'],
        'phone'=>(string)$row['phone'],
        'house_no_moo'=>(string)$row['house_no_moo'],
        'soi'=>$row['soi'],
        'road'=>$row['road'],
        'subdistrict'=>(string)$row['subdistrict'],
        'district'=>(string)$row['district'],
        'province'=>(string)$row['province'],
        'postal_code'=>(string)$row['postal_code'],
        'note'=>$row['note'],
        'status'=>(string)$row['status'],
        'status_label'=>order_status_label((string)$row['status']),
        'tracking_number'=>$row['tracking_number'],
        'created_at'=>$row['created_at'],
        'updated_at'=>$row['updated_at'],
        'confirmed_at'=>$row['confirmed_at'],
        'shipped_at'=>$row['shipped_at'],
        'completed_at'=>$row['completed_at'],
    ];
}

function require_order_participant(PDO $pdo, array $config, int $orderId, int $userId): array
{
    $order = order_payload($pdo,$config,$orderId,$userId);
    if (!$order) json_out(false,'ไม่พบคำสั่งซื้อ',null,404);
    if ($order['buyer_id'] !== $userId && $order['seller_id'] !== $userId) {
        json_out(false,'ไม่มีสิทธิ์ดูคำสั่งซื้อนี้',null,403);
    }
    return $order;
}

function has_order_relationship(PDO $pdo, int $listingId, int $buyerId, int $sellerId): bool
{
    $st = $pdo->prepare("SELECT order_id FROM orders
        WHERE listing_id=? AND buyer_id=? AND seller_id=? LIMIT 1");
    $st->execute([$listingId,$buyerId,$sellerId]);
    return (bool)$st->fetchColumn();
}


function payment_settings(PDO $pdo): array
{
    ensure_v7_schema($pdo);
    $row = $pdo->query("SELECT bank_name,account_name,account_number,points_per_baht,min_amount,is_active,updated_at
        FROM payment_settings WHERE id=1 LIMIT 1")->fetch();
    return [
        'bank_name'=>(string)($row['bank_name'] ?? ''),
        'account_name'=>(string)($row['account_name'] ?? ''),
        'account_number'=>(string)($row['account_number'] ?? ''),
        'points_per_baht'=>(float)($row['points_per_baht'] ?? 1),
        'min_amount'=>(float)($row['min_amount'] ?? 20),
        'is_active'=>(bool)($row['is_active'] ?? true),
        'updated_at'=>$row['updated_at'] ?? null,
    ];
}

function save_topup_slip(array $config, array $file): string
{
    if (!$file || ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
        throw new RuntimeException('กรุณาแนบรูปสลิป');
    }
    // The Android app resizes large source images before upload. The backend
    // still keeps a sane ceiling for malformed/direct requests.
    $maxBytes = max((int)($config['app']['upload_max_bytes'] ?? 5242880), 8 * 1024 * 1024);
    if ((int)($file['size'] ?? 0) > $maxBytes) {
        throw new RuntimeException('สลิปมีขนาดใหญ่เกินไป กรุณาเลือกใหม่');
    }

    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime = $finfo->file((string)$file['tmp_name']);
    $allowed = ['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp'];
    if (!isset($allowed[$mime])) throw new RuntimeException('สลิปรองรับ JPG, PNG และ WEBP');

    $dir = dirname(__DIR__) . '/uploads/slips/' . date('Y/m');
    if (!is_dir($dir) && !mkdir($dir, 0755, true) && !is_dir($dir)) {
        throw new RuntimeException('สร้างโฟลเดอร์สลิปไม่ได้');
    }
    $name = bin2hex(random_bytes(18)) . '.' . $allowed[$mime];
    $full = $dir . '/' . $name;
    if (!move_uploaded_file((string)$file['tmp_name'], $full)) {
        throw new RuntimeException('บันทึกสลิปไม่สำเร็จ');
    }
    return 'uploads/slips/' . date('Y/m') . '/' . $name;
}

function touch_presence(PDO $pdo, string $clientId, ?int $userId): int
{
    ensure_v7_schema($pdo);
    $clientId = strtolower(trim($clientId));
    if (!preg_match('/^[a-f0-9]{32,64}$/', $clientId)) {
        throw new RuntimeException('client id ไม่ถูกต้อง');
    }
    $pdo->prepare("INSERT INTO app_presence(client_id,user_id,last_seen) VALUES(?,?,NOW())
        ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),last_seen=NOW()")->execute([$clientId,$userId]);
    return (int)$pdo->query("SELECT COUNT(*) FROM app_presence WHERE last_seen >= DATE_SUB(NOW(), INTERVAL 15 MINUTE)")->fetchColumn();
}

function current_online_count(PDO $pdo): int
{
    ensure_v7_schema($pdo);
    return (int)$pdo->query("SELECT COUNT(*) FROM app_presence WHERE last_seen >= DATE_SUB(NOW(), INTERVAL 15 MINUTE)")->fetchColumn();
}


function point_balance(PDO $pdo, int $userId): int
{
    ensure_v6_schema($pdo);
    $pdo->prepare("INSERT IGNORE INTO point_wallets(user_id,balance) VALUES(?,0)")->execute([$userId]);
    $st = $pdo->prepare("SELECT balance FROM point_wallets WHERE user_id=? LIMIT 1");
    $st->execute([$userId]);
    return (int)$st->fetchColumn();
}

function wallet_summary(PDO $pdo, int $userId): array
{
    ensure_v8_schema($pdo);

    $plans = $pdo->query("SELECT id,name,points_cost,duration_days FROM premium_plans WHERE is_active=1 ORDER BY sort_order,id")->fetchAll();
    $payment = payment_settings($pdo);
    $boost = boost_settings($pdo);

    $st = $pdo->prepare("SELECT r.id,r.package_id,r.points,r.amount,r.note,r.status,r.created_at,
            COALESCE(p.name,'เติมแต้มตามจำนวน') package_name
        FROM point_topup_requests r
        LEFT JOIN point_topup_packages p ON p.id=r.package_id
        WHERE r.user_id=? ORDER BY r.id DESC LIMIT 20");
    $st->execute([$userId]);
    $requests = $st->fetchAll();

    $st = $pdo->prepare("SELECT pp.id,pp.listing_id,l.title,pl.name plan_name,pp.points_spent,pp.starts_at,pp.ends_at,pp.status
        FROM premium_promotions pp
        JOIN listings l ON l.id=pp.listing_id
        JOIN premium_plans pl ON pl.id=pp.plan_id
        WHERE pp.user_id=? ORDER BY pp.id DESC LIMIT 30");
    $st->execute([$userId]);
    $promotions = $st->fetchAll();

    $st = $pdo->prepare("SELECT b.id,b.listing_id,l.title,b.points_spent,b.boosted_at,b.created_at
        FROM listing_boosts b
        JOIN listings l ON l.id=b.listing_id
        WHERE b.user_id=? ORDER BY b.id DESC LIMIT 30");
    $st->execute([$userId]);
    $boosts = $st->fetchAll();

    $st = $pdo->prepare("SELECT id,amount,type,description,listing_id,created_at
        FROM point_transactions WHERE user_id=? ORDER BY id DESC LIMIT 30");
    $st->execute([$userId]);
    $transactions = $st->fetchAll();

    return [
        'balance'=>point_balance($pdo,$userId),
        'payment'=>$payment,
        'boost'=>$boost,
        'plans'=>$plans,
        'topup_requests'=>$requests,
        'promotions'=>$promotions,
        'boosts'=>$boosts,
        'transactions'=>$transactions,
    ];
}

function admin_adjust_points(PDO $pdo, int $userId, int $amount, string $description, ?int $adminId): int
{
    ensure_v6_schema($pdo);
    if ($amount === 0) throw new RuntimeException('จำนวนแต้มต้องไม่เป็น 0');

    $pdo->beginTransaction();
    try {
        $pdo->prepare("INSERT IGNORE INTO point_wallets(user_id,balance) VALUES(?,0)")->execute([$userId]);
        $st = $pdo->prepare("SELECT balance FROM point_wallets WHERE user_id=? FOR UPDATE");
        $st->execute([$userId]);
        $balance = (int)$st->fetchColumn();
        $newBalance = $balance + $amount;
        if ($newBalance < 0) throw new RuntimeException('แต้มคงเหลือไม่พอสำหรับการหัก');

        $pdo->prepare("UPDATE point_wallets SET balance=? WHERE user_id=?")->execute([$newBalance,$userId]);
        $pdo->prepare("INSERT INTO point_transactions(user_id,amount,type,description,admin_id) VALUES(?,?,'admin_adjustment',?,?)")
            ->execute([$userId,$amount,$description,$adminId]);
        $pdo->commit();
        return $newBalance;
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $e;
    }
}


/**
 * V10: mobile Admin + Admin task notifications.
 *
 * V9 already has users.role and push_tokens. V10 intentionally reuses both
 * instead of adding a second Admin identity/device system.
 */
function ensure_v10_schema(PDO $pdo): void
{
    static $ready = false;
    if ($ready) return;

    ensure_v9_schema($pdo);

    $columnExists = static function(PDO $db, string $table, string $column): bool {
        $st = $db->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?");
        $st->execute([$table,$column]);
        return (int)$st->fetchColumn() > 0;
    };
    $indexExists = static function(PDO $db, string $table, string $index): bool {
        $st = $db->prepare("SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?");
        $st->execute([$table,$index]);
        return (int)$st->fetchColumn() > 0;
    };
    $constraintExists = static function(PDO $db, string $table, string $constraint): bool {
        $st = $db->prepare("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=?");
        $st->execute([$table,$constraint]);
        return (int)$st->fetchColumn() > 0;
    };

    // Stable extension fields: user display/admin decoration, listing selling
    // options and order payment evidence. All additions are backwards-compatible.
    foreach ([
        'display_name' => "ALTER TABLE users ADD COLUMN display_name VARCHAR(80) NULL AFTER username",
        'display_name_change_count' => "ALTER TABLE users ADD COLUMN display_name_change_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER display_name",
        'admin_stars' => "ALTER TABLE users ADD COLUMN admin_stars TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER display_name_change_count",
        'special_icon' => "ALTER TABLE users ADD COLUMN special_icon VARCHAR(16) NULL AFTER admin_stars",
    ] as $column => $sql) {
        if (!$columnExists($pdo,'users',$column)) $pdo->exec($sql);
    }

    foreach ([
        'allow_meetup' => "ALTER TABLE listings ADD COLUMN allow_meetup TINYINT(1) NOT NULL DEFAULT 0 AFTER description",
        'allow_buy_now' => "ALTER TABLE listings ADD COLUMN allow_buy_now TINYINT(1) NOT NULL DEFAULT 1 AFTER allow_meetup",
        'allow_cod' => "ALTER TABLE listings ADD COLUMN allow_cod TINYINT(1) NOT NULL DEFAULT 0 AFTER allow_buy_now",
        'chat_first' => "ALTER TABLE listings ADD COLUMN chat_first TINYINT(1) NOT NULL DEFAULT 1 AFTER allow_cod",
    ] as $column => $sql) {
        if (!$columnExists($pdo,'listings',$column)) $pdo->exec($sql);
    }

    foreach ([
        'payment_method' => "ALTER TABLE orders ADD COLUMN payment_method ENUM('bank_transfer','cod') NULL AFTER seller_verified_at_snapshot",
        'payment_slip_path' => "ALTER TABLE orders ADD COLUMN payment_slip_path VARCHAR(255) NULL AFTER payment_method",
    ] as $column => $sql) {
        if (!$columnExists($pdo,'orders',$column)) $pdo->exec($sql);
    }

    $pdo->exec("CREATE TABLE IF NOT EXISTS display_name_change_requests (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    if (!$columnExists($pdo,'listings','approved_by')) {
        $pdo->exec("ALTER TABLE listings ADD COLUMN approved_by INT UNSIGNED NULL AFTER status");
    }
    if (!$columnExists($pdo,'listings','approved_at')) {
        $pdo->exec("ALTER TABLE listings ADD COLUMN approved_at DATETIME NULL AFTER approved_by");
    }
    if (!$indexExists($pdo,'listings','idx_listing_approved_by')) {
        $pdo->exec("ALTER TABLE listings ADD INDEX idx_listing_approved_by (approved_by)");
    }
    if (!$constraintExists($pdo,'listings','fk_listings_approved_by')) {
        $pdo->exec("ALTER TABLE listings ADD CONSTRAINT fk_listings_approved_by
            FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL");
    }

    $pdo->exec("CREATE TABLE IF NOT EXISTS admin_notifications (
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
        CONSTRAINT fk_admin_notifications_user FOREIGN KEY (related_user_id)
            REFERENCES users(id) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS admin_notification_reads (
        notification_id BIGINT UNSIGNED NOT NULL,
        admin_user_id INT UNSIGNED NOT NULL,
        read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (notification_id,admin_user_id),
        INDEX idx_admin_reads_admin (admin_user_id,read_at),
        CONSTRAINT fk_admin_reads_notification FOREIGN KEY (notification_id)
            REFERENCES admin_notifications(id) ON DELETE CASCADE,
        CONSTRAINT fk_admin_reads_user FOREIGN KEY (admin_user_id)
            REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $ready = true;
}


/**
 * V13: seller address stored on the member account + configurable automatic
 * member Push timing. Additive only; existing users/data remain untouched.
 */
function ensure_v13_schema(PDO $pdo): void
{
    static $ready = false;
    if ($ready) return;

    ensure_v10_schema($pdo);

    $columnExists = static function(PDO $db, string $table, string $column): bool {
        $st = $db->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?");
        $st->execute([$table,$column]);
        return (int)$st->fetchColumn() > 0;
    };

    foreach ([
        'province' => "ALTER TABLE users ADD COLUMN province VARCHAR(100) NULL AFTER line_id",
        'amphoe' => "ALTER TABLE users ADD COLUMN amphoe VARCHAR(100) NULL AFTER province",
        'tambon' => "ALTER TABLE users ADD COLUMN tambon VARCHAR(100) NULL AFTER amphoe",
    ] as $column => $sql) {
        if (!$columnExists($pdo,'users',$column)) $pdo->exec($sql);
    }

    if (!$columnExists($pdo,'scheduled_notifications','source')) {
        $pdo->exec("ALTER TABLE scheduled_notifications
            ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'manual' AFTER body");
    }

    $pdo->exec("CREATE TABLE IF NOT EXISTS member_push_settings (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("INSERT IGNORE INTO member_push_settings(
        id,enabled,daily_count,window1_start,window1_end,window2_start,window2_end
    ) VALUES(1,1,2,'09:00:00','12:00:00','15:00:00','21:00:00')");

    $ready = true;
}


/**
 * V14: two-digit prize activity using the existing point wallet.
 * Additive/idempotent only; no V13 table/data is removed.
 */
function ensure_v14_schema(PDO $pdo): void
{
    static $ready = false;
    if ($ready) return;

    ensure_v13_schema($pdo);

    $enum = (string)$pdo->query("SELECT COLUMN_TYPE FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='point_transactions' AND COLUMN_NAME='type'")->fetchColumn();
    if ($enum === '') {
        throw new RuntimeException('ไม่พบคอลัมน์ point_transactions.type กรุณาตรวจ migration ระบบแต้มเดิมก่อน');
    }
    if (strpos($enum,'lottery_purchase') === false) {
        // Preserve every existing ENUM value and append only the V14 value.
        $enumWithLottery = substr($enum,0,-1).",'lottery_purchase')";
        $pdo->exec("ALTER TABLE point_transactions MODIFY type ".$enumWithLottery." NOT NULL");
    }

    $pdo->exec("CREATE TABLE IF NOT EXISTS lottery_rounds (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS lottery_entries (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $ready = true;
}

function lottery_schema_ready(PDO $pdo): bool
{
    try {
        $st=$pdo->prepare("SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('lottery_rounds','lottery_entries')");
        $st->execute();
        if ((int)$st->fetchColumn() < 2) return false;
        $enum=(string)$pdo->query("SELECT COLUMN_TYPE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='point_transactions' AND COLUMN_NAME='type'")->fetchColumn();
        return strpos($enum,'lottery_purchase') !== false;
    } catch (Throwable $e) {
        return false;
    }
}

function lottery_round_payload(PDO $pdo, array $config, array $round): array
{
    $winner=null;
    if ($round['winning_number'] !== null && $round['winning_number'] !== '') {
        $st=$pdo->prepare("SELECT e.id entry_id,e.user_id,
                COALESCE(NULLIF(u.display_name,''),u.username) display_name
            FROM lottery_entries e
            JOIN users u ON u.id=e.user_id
            WHERE e.round_id=? AND e.number=? LIMIT 1");
        $st->execute([(int)$round['id'],(int)$round['winning_number']]);
        $row=$st->fetch();
        if($row){
            $winner=[
                'entry_id'=>(int)$row['entry_id'],
                'user_id'=>(int)$row['user_id'],
                'display_name'=>(string)$row['display_name'],
            ];
        }
    }

    return [
        'id'=>(int)$round['id'],
        'title'=>(string)$round['title'],
        'prize_name'=>(string)$round['prize_name'],
        'prize_description'=>(string)($round['prize_description']??''),
        'prize_image_url'=>!empty($round['prize_image_path']) ? image_url($config,(string)$round['prize_image_path']) : null,
        'draw_date'=>(string)$round['draw_date'],
        'points_cost'=>(int)$round['points_cost'],
        'status'=>(string)$round['status'],
        'winning_number'=>($round['winning_number'] === null || $round['winning_number'] === '')
            ? null : str_pad((string)(int)$round['winning_number'],2,'0',STR_PAD_LEFT),
        'announced_at'=>$round['announced_at'] ?? null,
        'winner'=>$winner,
    ];
}

function member_push_settings(PDO $pdo): array
{
    ensure_v13_schema($pdo);
    $row = $pdo->query("SELECT * FROM member_push_settings WHERE id=1 LIMIT 1")->fetch();
    return $row ?: [
        'id'=>1,'enabled'=>1,'daily_count'=>2,
        'window1_start'=>'09:00:00','window1_end'=>'12:00:00',
        'window2_start'=>'15:00:00','window2_end'=>'21:00:00',
        'window3_start'=>null,'window3_end'=>null,
        'last_planned_date'=>null,'updated_by'=>null,'updated_at'=>null,
    ];
}

function admin_notification_create(
    PDO $pdo,
    string $type,
    string $title,
    string $message,
    ?int $relatedUserId = null,
    ?string $entityType = null,
    ?int $entityId = null,
    ?string $actionPath = null,
    ?string $mobileRoute = null,
    ?string $eventKey = null
): ?int {
    // Public member actions can create an Admin task, but they must never be
    // allowed to trigger schema DDL. Production deploy runs migration_v11.sql
    // after the V10 migration; Admin-only pages/APIs retain the idempotent ensure as a fallback.
    $type = mb_substr(trim($type),0,50);
    $title = mb_substr(trim($title),0,160);
    $message = mb_substr(trim($message),0,1000);
    $entityType = $entityType !== null ? mb_substr(trim($entityType),0,50) : null;
    $actionPath = $actionPath !== null ? mb_substr(trim($actionPath),0,255) : null;
    $mobileRoute = $mobileRoute !== null ? mb_substr(trim($mobileRoute),0,120) : null;
    $eventKey = $eventKey !== null ? mb_substr(trim($eventKey),0,160) : null;
    if ($type === '' || $title === '' || $message === '') return null;

    $st = $pdo->prepare("INSERT IGNORE INTO admin_notifications(
            type,title,message,related_user_id,entity_type,entity_id,action_path,mobile_route,event_key
        ) VALUES(?,?,?,?,?,?,?,?,?)");
    $st->execute([
        $type,$title,$message,
        $relatedUserId && $relatedUserId > 0 ? $relatedUserId : null,
        $entityType ?: null,
        $entityId && $entityId > 0 ? $entityId : null,
        $actionPath ?: null,
        $mobileRoute ?: null,
        $eventKey ?: null
    ]);

    if ($st->rowCount() === 0) {
        if ($eventKey) {
            $find = $pdo->prepare("SELECT id FROM admin_notifications WHERE event_key=? LIMIT 1");
            $find->execute([$eventKey]);
            $existing = (int)$find->fetchColumn();
            return $existing > 0 ? $existing : null;
        }
        return null;
    }

    $id = (int)$pdo->lastInsertId();
    try {
        if (function_exists('firebase_push_to_admins')) {
            firebase_push_to_admins($pdo,[
                'type'=>'admin_task',
                'title'=>$title,
                'body'=>$message,
                'admin_notification_id'=>(string)$id,
                'mobile_route'=>$mobileRoute ?: 'admin',
            ]);
        }
    } catch (Throwable $pushError) {
        error_log('Admin push failed: '.$pushError->getMessage());
    }
    return $id;
}

function admin_unread_count(PDO $pdo, int $adminId): int
{
    ensure_v10_schema($pdo);
    $st = $pdo->prepare("SELECT COUNT(*)
        FROM admin_notifications n
        LEFT JOIN admin_notification_reads r
          ON r.notification_id=n.id AND r.admin_user_id=?
        WHERE r.notification_id IS NULL");
    $st->execute([$adminId]);
    return (int)$st->fetchColumn();
}

function admin_notification_list(PDO $pdo, int $adminId, int $limit = 200, bool $unreadOnly = false): array
{
    ensure_v10_schema($pdo);
    $limit = max(1,min($limit,500));
    $sql = "SELECT n.id,n.type,n.title,n.message,n.related_user_id,n.entity_type,n.entity_id,
            n.action_path,n.mobile_route,n.created_at,u.username related_username,
            CASE WHEN r.notification_id IS NULL THEN 0 ELSE 1 END is_read,
            r.read_at
        FROM admin_notifications n
        LEFT JOIN users u ON u.id=n.related_user_id
        LEFT JOIN admin_notification_reads r
          ON r.notification_id=n.id AND r.admin_user_id=?
        ".($unreadOnly ? "WHERE r.notification_id IS NULL " : "").
        "ORDER BY n.id DESC LIMIT ".$limit;
    $st = $pdo->prepare($sql);
    $st->execute([$adminId]);
    return array_map(static function(array $row): array {
        $row['id']=(int)$row['id'];
        $row['related_user_id']=$row['related_user_id']!==null?(int)$row['related_user_id']:null;
        $row['entity_id']=$row['entity_id']!==null?(int)$row['entity_id']:null;
        $row['is_read']=(bool)$row['is_read'];
        return $row;
    },$st->fetchAll());
}

function admin_mark_notification_read(PDO $pdo, int $adminId, int $notificationId): void
{
    ensure_v10_schema($pdo);
    if ($notificationId <= 0) return;
    $st=$pdo->prepare("INSERT IGNORE INTO admin_notification_reads(notification_id,admin_user_id)
        SELECT id,? FROM admin_notifications WHERE id=?");
    $st->execute([$adminId,$notificationId]);
}

function admin_mark_all_notifications_read(PDO $pdo, int $adminId): void
{
    ensure_v10_schema($pdo);
    $st=$pdo->prepare("INSERT IGNORE INTO admin_notification_reads(notification_id,admin_user_id)
        SELECT id,? FROM admin_notifications");
    $st->execute([$adminId]);
}

function admin_mark_entity_notifications_read(PDO $pdo, int $adminId, string $entityType, int $entityId): void
{
    ensure_v10_schema($pdo);
    if ($entityId <= 0) return;
    $st=$pdo->prepare("INSERT IGNORE INTO admin_notification_reads(notification_id,admin_user_id)
        SELECT id,? FROM admin_notifications WHERE entity_type=? AND entity_id=?");
    $st->execute([$adminId,$entityType,$entityId]);
}

function admin_resolve_entity_notifications(PDO $pdo, string $entityType, int $entityId): void
{
    ensure_v10_schema($pdo);
    if ($entityId <= 0) return;

    // Once one Admin finishes an actionable task, clear that task from every
    // active Admin's unread queue. This prevents stale badges on a second
    // Admin device while ordinary "mark read" remains per-Admin.
    $st=$pdo->prepare("INSERT IGNORE INTO admin_notification_reads(notification_id,admin_user_id)
        SELECT n.id,u.id
        FROM admin_notifications n
        JOIN users u ON u.role='admin' AND u.status='active'
        WHERE n.entity_type=? AND n.entity_id=?");
    $st->execute([$entityType,$entityId]);
}

function admin_pending_counts(PDO $pdo, int $adminId): array
{
    ensure_v10_schema($pdo);
    $counts = [
        'users'=>(int)$pdo->query("SELECT COUNT(*) FROM users")->fetchColumn(),
        'pending_listings'=>(int)$pdo->query("SELECT COUNT(*) FROM listings WHERE status='pending'")->fetchColumn(),
        'pending_topups'=>(int)$pdo->query("SELECT COUNT(*) FROM point_topup_requests WHERE status='pending'")->fetchColumn(),
        'pending_verifications'=>(int)$pdo->query("SELECT COUNT(*) FROM identity_verifications WHERE status='pending'")->fetchColumn(),
        'open_reports'=>(int)$pdo->query("SELECT COUNT(*) FROM user_reports WHERE status='open'")->fetchColumn(),
        // V9 orders are acted on by buyer/seller. There is no Admin-review
        // state in the real source, so V10 does not invent one.
        'orders_need_admin'=>0,
        'active_orders'=>(int)$pdo->query("SELECT COUNT(*) FROM orders WHERE status IN ('pending_confirmation','preparing','shipped')")->fetchColumn(),
        'unread_notifications'=>admin_unread_count($pdo,$adminId),
    ];
    $counts['pending_total']=$counts['pending_listings']+$counts['pending_topups']+
        $counts['pending_verifications']+$counts['open_reports'];
    return $counts;
}

function admin_safe_action_path(?string $path): string
{
    $path = trim((string)$path);
    if ($path === '' || str_contains($path,'://') || str_starts_with($path,'//')) return 'index.php';
    $allowed = [
        'index.php','listings.php','points.php','verifications.php','reports.php',
        'orders.php','users.php','home_content.php','premium.php','notifications.php',
        'announcements.php','admin_alerts.php'
    ];
    $file = basename((string)parse_url($path,PHP_URL_PATH));
    return in_array($file,$allowed,true) ? $path : 'index.php';
}

function admin_review_topup(PDO $pdo, int $adminId, int $requestId, string $decision): array
{
    ensure_v10_schema($pdo);
    if (!in_array($decision,['approved','rejected'],true)) throw new RuntimeException('คำสั่งไม่ถูกต้อง');

    $pdo->beginTransaction();
    try {
        $st=$pdo->prepare("SELECT * FROM point_topup_requests WHERE id=? FOR UPDATE");
        $st->execute([$requestId]);
        $req=$st->fetch();
        if(!$req) throw new RuntimeException('ไม่พบคำขอ');
        if($req['status']!=='pending') throw new RuntimeException('คำขอนี้ถูกตรวจแล้ว');

        if($decision==='approved'){
            $uid=(int)$req['user_id'];
            $points=(int)$req['points'];
            $pdo->prepare("INSERT IGNORE INTO point_wallets(user_id,balance) VALUES(?,0)")->execute([$uid]);
            $pdo->prepare("UPDATE point_wallets SET balance=balance+? WHERE user_id=?")->execute([$points,$uid]);
            $desc='อนุมัติเติมแต้มคำขอ #'.$requestId;
            $pdo->prepare("INSERT INTO point_transactions(user_id,amount,type,description,admin_id)
                VALUES(?,?,'topup',?,?)")->execute([$uid,$points,$desc,$adminId]);
        }

        $pdo->prepare("UPDATE point_topup_requests SET status=?,reviewed_by=?,reviewed_at=NOW() WHERE id=?")
            ->execute([$decision,$adminId,$requestId]);
        $pdo->commit();
    } catch(Throwable $e) {
        if($pdo->inTransaction()) $pdo->rollBack();
        throw $e;
    }

    admin_resolve_entity_notifications($pdo,'topup',$requestId);
    try {
        firebase_push_to_user($pdo,(int)$req['user_id'],[
            'type'=>'admin_notification',
            'title'=>$decision==='approved'?'เติมแต้มสำเร็จ':'คำขอเติมแต้มไม่ผ่าน',
            'body'=>$decision==='approved'
                ? 'แอดมินอนุมัติสลิปแล้ว เพิ่ม '.(int)$req['points'].' แต้มเข้าบัญชีเรียบร้อย'
                : 'แอดมินตรวจคำขอเติมแต้มแล้วและยังไม่อนุมัติ กรุณาตรวจสอบและส่งใหม่หากจำเป็น',
        ]);
    } catch(Throwable $pushError) {
        error_log($pushError->getMessage());
    }
    $req['status']=$decision;
    return $req;
}

function admin_review_verification(
    PDO $pdo,
    int $adminId,
    int $userId,
    string $decision,
    string $reason = '',
    ?string $expectedSubmittedAt = null,
    ?string $expectedDocumentPath = null
): array {
    ensure_v10_schema($pdo);
    if (!in_array($decision,['approved','rejected'],true)) throw new RuntimeException('คำสั่งไม่ถูกต้อง');
    $reason=mb_substr(trim($reason),0,500);
    if($decision==='rejected' && mb_strlen($reason)<3) throw new RuntimeException('กรุณาระบุเหตุผลที่ไม่ผ่าน');

    $pdo->beginTransaction();
    try {
        $st=$pdo->prepare("SELECT * FROM identity_verifications WHERE user_id=? LIMIT 1 FOR UPDATE");
        $st->execute([$userId]);
        $row=$st->fetch();
        if(!$row) throw new RuntimeException('ไม่พบคำขอยืนยันตัวตน');
        if($expectedSubmittedAt !== null && $expectedSubmittedAt !== '' && $row['submitted_at'] !== $expectedSubmittedAt) {
            throw new RuntimeException('คำขอนี้ถูกส่งใหม่แล้ว กรุณารีเฟรช');
        }
        if($expectedDocumentPath !== null && $expectedDocumentPath !== '' && $row['document_path'] !== $expectedDocumentPath) {
            throw new RuntimeException('หลักฐานถูกเปลี่ยนแล้ว กรุณารีเฟรช');
        }
        if($decision==='approved' && !in_array((string)$row['status'],['pending','rejected'],true)) {
            throw new RuntimeException('คำขอนี้ไม่ได้อยู่ในสถานะที่อนุมัติได้');
        }

        if($decision==='approved'){
            $pdo->prepare("UPDATE identity_verifications
                SET status='verified',rejection_reason=NULL,reviewed_by=?,reviewed_at=NOW(),verified_at=NOW()
                WHERE user_id=?")->execute([$adminId,$userId]);
        } else {
            $pdo->prepare("UPDATE identity_verifications
                SET status='rejected',rejection_reason=?,reviewed_by=?,reviewed_at=NOW(),verified_at=NULL
                WHERE user_id=?")->execute([$reason,$adminId,$userId]);
        }
        $pdo->commit();
    } catch(Throwable $e) {
        if($pdo->inTransaction()) $pdo->rollBack();
        throw $e;
    }

    admin_resolve_entity_notifications($pdo,'identity',$userId);
    try {
        firebase_push_to_user($pdo,$userId,[
            'type'=>'admin_notification',
            'title'=>$decision==='approved'?'ยืนยันตัวตนสำเร็จ':'ยืนยันตัวตนไม่ผ่าน',
            'body'=>$decision==='approved'
                ? 'แอดมินตรวจและยืนยันบัญชีของคุณแล้ว'
                : 'กรุณาตรวจข้อมูลและส่งคำขอยืนยันใหม่ เหตุผล: '.$reason,
        ]);
    } catch(Throwable $pushError) {
        error_log($pushError->getMessage());
    }
    return identity_verification_payload($pdo,$userId,true);
}

function admin_update_listing_status(PDO $pdo, int $adminId, int $listingId, string $status): void
{
    ensure_v10_schema($pdo);
    if(!in_array($status,['pending','approved','hidden','rejected','sold'],true)) {
        throw new RuntimeException('สถานะประกาศไม่ถูกต้อง');
    }
    if($status==='approved'){
        $chk=$pdo->prepare("SELECT order_id FROM orders
            WHERE listing_id=? AND status IN ('preparing','shipped','completed') LIMIT 1");
        $chk->execute([$listingId]);
        if($chk->fetchColumn()) {
            throw new RuntimeException('ประกาศนี้มีคำสั่งซื้อที่ยืนยัน/สำเร็จแล้ว จึงเปิดขายซ้ำไม่ได้');
        }
        $st=$pdo->prepare("UPDATE listings SET status='approved',approved_by=?,approved_at=NOW() WHERE id=?");
        $st->execute([$adminId,$listingId]);
    } else {
        $st=$pdo->prepare("UPDATE listings SET status=? WHERE id=?");
        $st->execute([$status,$listingId]);
    }
    if($st->rowCount()===0){
        $chk=$pdo->prepare("SELECT id FROM listings WHERE id=?");
        $chk->execute([$listingId]);
        if(!$chk->fetchColumn()) throw new RuntimeException('ไม่พบประกาศ');
    }
    if($status!=='pending') admin_resolve_entity_notifications($pdo,'listing',$listingId);
}

function admin_update_report(PDO $pdo, int $adminId, int $reportId, string $action, string $note = ''): void
{
    ensure_v10_schema($pdo);
    $note=mb_substr(trim($note),0,1000);
    $st=$pdo->prepare("SELECT * FROM user_reports WHERE id=? LIMIT 1");
    $st->execute([$reportId]);
    $report=$st->fetch();
    if(!$report) throw new RuntimeException('ไม่พบรายงาน');

    if($action==='reviewing'){
        $pdo->prepare("UPDATE user_reports SET status='reviewing' WHERE id=?")->execute([$reportId]);
    } elseif($action==='resolve' || $action==='dismiss'){
        $status=$action==='resolve'?'resolved':'dismissed';
        $pdo->prepare("UPDATE user_reports SET status=?,admin_note=?,resolved_by=?,resolved_at=NOW() WHERE id=?")
            ->execute([$status,$note?:null,$adminId,$reportId]);
    } elseif($action==='suspend_user'){
        $reported=(int)($report['reported_user_id']??0);
        if($reported<=0) throw new RuntimeException('รายงานนี้ไม่มีบัญชีผู้ถูกรายงาน');
        if($reported===$adminId) throw new RuntimeException('ไม่สามารถระงับบัญชีแอดมินที่กำลังใช้งาน');
        $pdo->beginTransaction();
        try {
            $pdo->prepare("UPDATE users SET status='suspended' WHERE id=?")->execute([$reported]);
            $finalNote=$note!==''?$note:'ระงับจากรายงาน #'.$reportId;
            $pdo->prepare("UPDATE user_reports SET status='resolved',admin_note=?,resolved_by=?,resolved_at=NOW() WHERE id=?")
                ->execute([$finalNote,$adminId,$reportId]);
            $pdo->prepare("DELETE FROM api_tokens WHERE user_id=?")->execute([$reported]);
            $pdo->commit();
        } catch(Throwable $e) {
            if($pdo->inTransaction()) $pdo->rollBack();
            throw $e;
        }
        try {
            firebase_push_to_user($pdo,$reported,[
                'type'=>'admin_notification',
                'title'=>'บัญชีถูกระงับการใช้งาน',
                'body'=>'แอดมินระงับบัญชีของคุณ กรุณาติดต่อผู้ดูแลหากต้องการสอบถาม',
            ]);
        } catch(Throwable $pushError) {
            error_log($pushError->getMessage());
        }
    } else {
        throw new RuntimeException('คำสั่งไม่ถูกต้อง');
    }

    admin_resolve_entity_notifications($pdo,'report',$reportId);
}
