<?php
declare(strict_types=1);

function mysql_timezone_offset(string $timezone): string
{
    try {
        $zone = new DateTimeZone($timezone);
        $now = new DateTimeImmutable('now', $zone);
        $seconds = $zone->getOffset($now);
    } catch (Throwable $e) {
        $seconds = 7 * 3600;
    }

    $sign = $seconds < 0 ? '-' : '+';
    $seconds = abs($seconds);
    $hours = intdiv($seconds, 3600);
    $minutes = intdiv($seconds % 3600, 60);
    return sprintf('%s%02d:%02d', $sign, $hours, $minutes);
}

function create_pdo(array $config): PDO
{
    $db = $config['db'];
    $dsn = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4', $db['host'], $db['port'], $db['name']);
    $pdo = new PDO($dsn, $db['user'], $db['pass'], [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);

    // V8: PHP already uses Asia/Bangkok, but MySQL NOW()/CURRENT_TIMESTAMP used
    // the database server/session timezone.  V7 mixed those two clocks, so a
    // premium bought in Thailand could appear roughly seven hours later when
    // the DB server ran on UTC.  Use a numeric offset (works even when MySQL
    // timezone tables are not installed) so all DATETIME comparisons use the
    // same clock as the application.
    $timezone = (string)($config['app']['timezone'] ?? 'Asia/Bangkok');
    $offset = mysql_timezone_offset($timezone);
    $pdo->exec("SET time_zone = " . $pdo->quote($offset));

    return $pdo;
}
