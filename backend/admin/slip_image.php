<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v10_schema($pdo);

$id=(int)($_GET['id']??0);
$st=$pdo->prepare("SELECT slip_path FROM point_topup_requests WHERE id=? LIMIT 1");
$st->execute([$id]); $relative=(string)($st->fetchColumn()?:'');
if($relative==='' || !str_starts_with($relative,'uploads/slips/')){
    http_response_code(404); exit('ไม่พบสลิป');
}

$backendRoot=realpath(dirname(__DIR__));
$slipsRoot=realpath(dirname(__DIR__).'/uploads/slips');
$full=realpath(dirname(__DIR__).'/'.$relative);
if(!$backendRoot || !$slipsRoot || !$full || !str_starts_with($full,$slipsRoot.DIRECTORY_SEPARATOR) || !is_file($full) || !is_readable($full)){
    http_response_code(404); exit('ไม่พบสลิป');
}

$finfo=new finfo(FILEINFO_MIME_TYPE);
$mime=(string)$finfo->file($full);
if(!in_array($mime,['image/jpeg','image/png','image/webp'],true)){ http_response_code(415); exit('ชนิดไฟล์ไม่รองรับ'); }

header('Content-Type: '.$mime);
header('Content-Length: '.filesize($full));
header('Content-Disposition: inline; filename="topup-slip"');
header('Cache-Control: private, no-store, no-cache, must-revalidate');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
readfile($full);
exit;
