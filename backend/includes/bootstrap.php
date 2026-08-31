<?php
declare(strict_types=1);

header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: SAMEORIGIN');
header('Referrer-Policy: strict-origin-when-cross-origin');

$configFile = dirname(__DIR__) . '/config/config.php';
if (!is_file($configFile)) {
    if (str_contains($_SERVER['SCRIPT_NAME'] ?? '', '/api/')) {
        header('Content-Type: application/json; charset=utf-8');
        http_response_code(503);
        echo json_encode(['success' => false, 'message' => 'ระบบยังไม่ได้ติดตั้ง', 'data' => null], JSON_UNESCAPED_UNICODE);
        exit;
    }
    http_response_code(503);
    header('Content-Type: text/plain; charset=utf-8');
    echo "ระบบยังไม่ได้ตั้งค่า backend\n";
    echo "กรุณาสร้าง config/config.php จาก config/config.example.php และ import database/schema.sql\n";
    exit;
}
$config = require $configFile;
date_default_timezone_set($config['app']['timezone'] ?? 'Asia/Bangkok');
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/helpers.php';
require_once __DIR__ . '/firebase_push.php';
$pdo = create_pdo($config);

if (session_status() !== PHP_SESSION_ACTIVE && PHP_SAPI !== 'cli') {
    session_name('khaiphraban_admin');
    session_start([
        'cookie_httponly' => true,
        'cookie_samesite' => 'Lax',
        'cookie_secure' => (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off'),
    ]);
}
