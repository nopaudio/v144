<?php
declare(strict_types=1);

/**
 * Firebase Cloud Messaging HTTP v1 helper.
 *
 * The private service-account file stays OUTSIDE public_html:
 * /home/rodtiidc/firebase_private/firebase-service-account.json
 *
 * No Composer package is required. The helper signs the OAuth2 JWT with
 * OpenSSL and calls the official FCM HTTP v1 endpoint.
 */

function firebase_service_account_path(): string
{
    $fromEnv = getenv('FIREBASE_SERVICE_ACCOUNT');
    if (is_string($fromEnv) && $fromEnv !== '') return $fromEnv;
    return '/home/rodtiidc/firebase_private/firebase-service-account.json';
}

function firebase_base64url(string $value): string
{
    return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
}

function firebase_http_post(string $url, array|string $body, array $headers = [], bool $form = false): array
{
    $payload = is_array($body)
        ? ($form ? http_build_query($body) : json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES))
        : $body;

    if ($payload === false) throw new RuntimeException('สร้างข้อมูล Firebase ไม่สำเร็จ');

    if (function_exists('curl_init')) {
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $payload,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 15,
            CURLOPT_CONNECTTIMEOUT => 8,
            CURLOPT_HTTPHEADER => $headers,
        ]);
        $response = curl_exec($ch);
        $status = (int)curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
        $error = curl_error($ch);
        curl_close($ch);
        if ($response === false) throw new RuntimeException('เชื่อมต่อ Firebase ไม่สำเร็จ: ' . $error);
        return [$status, (string)$response];
    }

    $context = stream_context_create([
        'http' => [
            'method' => 'POST',
            'header' => implode("\r\n", $headers),
            'content' => $payload,
            'timeout' => 15,
            'ignore_errors' => true,
        ],
    ]);
    $response = @file_get_contents($url, false, $context);
    if ($response === false) throw new RuntimeException('เซิร์ฟเวอร์ไม่รองรับการเชื่อมต่อ Firebase');

    $status = 0;
    foreach ($http_response_header ?? [] as $line) {
        if (preg_match('/^HTTP\/\S+\s+(\d{3})/', $line, $m)) {
            $status = (int)$m[1];
            break;
        }
    }
    return [$status, (string)$response];
}

function firebase_credentials(): array
{
    static $credentials = null;
    if (is_array($credentials)) return $credentials;

    $path = firebase_service_account_path();
    if (!is_file($path) || !is_readable($path)) {
        throw new RuntimeException('ไม่พบไฟล์ Firebase Service Account ที่ ' . $path);
    }

    $raw = file_get_contents($path);
    $json = is_string($raw) ? json_decode($raw, true) : null;
    if (!is_array($json) || empty($json['client_email']) || empty($json['private_key']) || empty($json['project_id'])) {
        throw new RuntimeException('ไฟล์ Firebase Service Account ไม่ถูกต้อง');
    }
    $credentials = $json;
    return $credentials;
}

function firebase_access_token(): string
{
    static $memoryToken = null;
    static $memoryExpiry = 0;

    if (is_string($memoryToken) && $memoryExpiry > time() + 60) return $memoryToken;

    $credentials = firebase_credentials();
    $cacheFile = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR)
        . DIRECTORY_SEPARATOR
        . 'khaiphraban_fcm_' . sha1((string)$credentials['client_email']) . '.json';

    if (is_file($cacheFile) && is_readable($cacheFile)) {
        $cached = json_decode((string)@file_get_contents($cacheFile), true);
        if (is_array($cached)
            && !empty($cached['access_token'])
            && (int)($cached['expires_at'] ?? 0) > time() + 60
        ) {
            $memoryToken = (string)$cached['access_token'];
            $memoryExpiry = (int)$cached['expires_at'];
            return $memoryToken;
        }
    }

    $now = time();
    $header = ['alg' => 'RS256', 'typ' => 'JWT'];
    $claims = [
        'iss' => (string)$credentials['client_email'],
        'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
        'aud' => 'https://oauth2.googleapis.com/token',
        'iat' => $now,
        'exp' => $now + 3600,
    ];

    $unsigned = firebase_base64url((string)json_encode($header))
        . '.'
        . firebase_base64url((string)json_encode($claims));

    $privateKey = openssl_pkey_get_private((string)$credentials['private_key']);
    if ($privateKey === false) throw new RuntimeException('อ่าน Private Key ของ Firebase ไม่สำเร็จ');

    $signature = '';
    $signed = openssl_sign($unsigned, $signature, $privateKey, OPENSSL_ALGO_SHA256);
    if (!$signed) throw new RuntimeException('เซ็น Firebase OAuth token ไม่สำเร็จ');

    $assertion = $unsigned . '.' . firebase_base64url($signature);
    [$status, $raw] = firebase_http_post(
        'https://oauth2.googleapis.com/token',
        [
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion' => $assertion,
        ],
        ['Content-Type: application/x-www-form-urlencoded'],
        true
    );

    $response = json_decode($raw, true);
    if ($status < 200 || $status >= 300 || !is_array($response) || empty($response['access_token'])) {
        throw new RuntimeException('ขอ Firebase access token ไม่สำเร็จ');
    }

    $memoryToken = (string)$response['access_token'];
    $memoryExpiry = time() + max(300, ((int)($response['expires_in'] ?? 3600)) - 120);

    @file_put_contents(
        $cacheFile,
        json_encode(['access_token' => $memoryToken, 'expires_at' => $memoryExpiry]),
        LOCK_EX
    );

    return $memoryToken;
}

function firebase_send_data_message(string $deviceToken, array $data): array
{
    $credentials = firebase_credentials();
    $accessToken = firebase_access_token();

    $cleanData = [];
    foreach ($data as $key => $value) {
        $cleanData[(string)$key] = (string)$value;
    }

    $payload = [
        'message' => [
            'token' => $deviceToken,
            'data' => $cleanData,
            'android' => [
                'priority' => 'HIGH',
                'ttl' => '86400s',
            ],
        ],
    ];

    return firebase_http_post(
        'https://fcm.googleapis.com/v1/projects/' . rawurlencode((string)$credentials['project_id']) . '/messages:send',
        $payload,
        [
            'Authorization: Bearer ' . $accessToken,
            'Content-Type: application/json; charset=UTF-8',
        ]
    );
}

/**
 * Push failures never make chat sending fail. A message is still stored even if
 * Firebase is temporarily unavailable. Invalid tokens are removed automatically.
 */
function firebase_push_to_user(PDO $pdo, int $userId, array $data): void
{
    if ($userId <= 0) return;

    try {
        ensure_v5_schema($pdo);
        $stmt = $pdo->prepare('SELECT id,token FROM push_tokens WHERE user_id=? ORDER BY updated_at DESC LIMIT 10');
        $stmt->execute([$userId]);
        $tokens = $stmt->fetchAll();
        if (!$tokens) return;

        foreach ($tokens as $row) {
            try {
                [$status, $body] = firebase_send_data_message((string)$row['token'], $data);

                if ($status >= 200 && $status < 300) continue;

                $decoded = json_decode($body, true);
                $errorStatus = (string)($decoded['error']['status'] ?? '');
                $detailsText = $body;

                if (
                    $status === 404
                    || $errorStatus === 'NOT_FOUND'
                    || str_contains($detailsText, 'UNREGISTERED')
                    || str_contains($detailsText, 'registration-token-not-registered')
                ) {
                    $delete = $pdo->prepare('DELETE FROM push_tokens WHERE id=?');
                    $delete->execute([(int)$row['id']]);
                }

                error_log('FCM send failed HTTP ' . $status . ': ' . mb_substr($body, 0, 500));
            } catch (Throwable $e) {
                error_log('FCM token send error: ' . $e->getMessage());
            }
        }
    } catch (Throwable $e) {
        error_log('FCM push error: ' . $e->getMessage());
    }
}


/**
 * Broadcast a data notification to active registered devices.
 * Returns successful device sends. Invalid tokens are cleaned up.
 */
function firebase_push_all_active(PDO $pdo, array $data, int $limit = 2000): int
{
    ensure_v5_schema($pdo);
    // Validate service-account configuration once. Token-specific delivery
    // errors are still isolated below, but a server configuration error
    // should be visible to admin/cron instead of silently marking 0 sent.
    firebase_credentials();
    $limit = max(1, min($limit, 5000));
    $rows = $pdo->query("SELECT pt.id,pt.token
        FROM push_tokens pt
        JOIN users u ON u.id=pt.user_id
        WHERE u.status='active'
        ORDER BY pt.updated_at DESC
        LIMIT ".$limit)->fetchAll();
    $sent = 0;
    foreach ($rows as $row) {
        try {
            [$status,$body] = firebase_send_data_message((string)$row['token'],$data);
            if ($status >= 200 && $status < 300) {
                $sent++;
                continue;
            }
            $decoded=json_decode($body,true);
            $errorStatus=(string)($decoded['error']['status']??'');
            if ($status===404 || $errorStatus==='NOT_FOUND' || str_contains($body,'UNREGISTERED')) {
                $pdo->prepare('DELETE FROM push_tokens WHERE id=?')->execute([(int)$row['id']]);
            }
            error_log('FCM broadcast failed HTTP '.$status.': '.mb_substr($body,0,300));
        } catch(Throwable $e) {
            error_log('FCM broadcast token error: '.$e->getMessage());
        }
    }
    return $sent;
}


/**
 * V10 Admin push. Reuses the existing push_tokens table, but the recipient
 * list is selected by the server from users.role='admin'. Client-supplied
 * is_admin/role values are never consulted.
 */
function firebase_push_to_admins(PDO $pdo, array $data): int
{
    ensure_v5_schema($pdo);
    $admins = $pdo->query("SELECT id FROM users WHERE role='admin' AND status='active' ORDER BY id")->fetchAll();
    $sentAdmins = 0;
    foreach ($admins as $row) {
        $adminId = (int)$row['id'];
        if ($adminId <= 0) continue;
        $payload = $data;
        $payload['recipient_user_id'] = (string)$adminId;
        firebase_push_to_user($pdo,$adminId,$payload);
        $sentAdmins++;
    }
    return $sentAdmins;
}
