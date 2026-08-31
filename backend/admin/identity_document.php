<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v9_schema($pdo);

$userId=(int)($_GET['user_id']??0);
$st=$pdo->prepare('SELECT document_path FROM identity_verifications WHERE user_id=? LIMIT 1');
$st->execute([$userId]); $relative=$st->fetchColumn();
if(!$relative){ http_response_code(404); exit('ไม่พบเอกสาร'); }

try { $full=identity_document_full_path($config,(string)$relative); }
catch(Throwable $e){ http_response_code(404); exit('ไม่พบเอกสาร'); }
if(!is_file($full) || !is_readable($full)){ http_response_code(404); exit('ไม่พบเอกสาร'); }

$finfo=new finfo(FILEINFO_MIME_TYPE);
$mime=(string)$finfo->file($full);
if(!in_array($mime,['image/jpeg','image/png','image/webp'],true)){ http_response_code(415); exit('ชนิดไฟล์ไม่รองรับ'); }

header('Content-Type: '.$mime);
header('Content-Length: '.filesize($full));
header('Content-Disposition: inline; filename="identity-proof"');
header('Cache-Control: private, no-store, no-cache, must-revalidate');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
readfile($full);
exit;
