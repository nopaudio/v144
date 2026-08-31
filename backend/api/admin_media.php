<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
$admin=require_api_admin($pdo);
ensure_v10_schema($pdo);

$kind=(string)($_GET['kind']??'');
$full='';
$filename='admin-media';

if($kind==='identity'){
    $userId=(int)($_GET['user_id']??0);
    $st=$pdo->prepare("SELECT document_path FROM identity_verifications WHERE user_id=? LIMIT 1");
    $st->execute([$userId]); $relative=(string)($st->fetchColumn()?:'');
    if($relative===''){ http_response_code(404); exit('ไม่พบเอกสาร'); }
    try { $full=identity_document_full_path($config,$relative); }
    catch(Throwable $e){ http_response_code(404); exit('ไม่พบเอกสาร'); }
    $filename='identity-proof';
} elseif($kind==='topup'){
    $id=(int)($_GET['id']??0);
    $st=$pdo->prepare("SELECT slip_path FROM point_topup_requests WHERE id=? LIMIT 1");
    $st->execute([$id]); $relative=(string)($st->fetchColumn()?:'');
    if($relative==='' || !str_starts_with($relative,'uploads/slips/')){
        http_response_code(404); exit('ไม่พบสลิป');
    }
    $slipsRoot=realpath(dirname(__DIR__).'/uploads/slips');
    $candidate=realpath(dirname(__DIR__).'/'.$relative);
    if(!$slipsRoot || !$candidate || !str_starts_with($candidate,$slipsRoot.DIRECTORY_SEPARATOR)){
        http_response_code(404); exit('ไม่พบสลิป');
    }
    $full=$candidate;
    $filename='topup-slip';
} else {
    http_response_code(400); exit('ชนิดไฟล์ไม่ถูกต้อง');
}

if(!is_file($full) || !is_readable($full)){ http_response_code(404); exit('ไม่พบไฟล์'); }
$finfo=new finfo(FILEINFO_MIME_TYPE);
$mime=(string)$finfo->file($full);
if(!in_array($mime,['image/jpeg','image/png','image/webp'],true)){ http_response_code(415); exit('ชนิดไฟล์ไม่รองรับ'); }

header('Content-Type: '.$mime);
header('Content-Length: '.filesize($full));
header('Content-Disposition: inline; filename="'.$filename.'"');
header('Cache-Control: private, no-store, no-cache, must-revalidate');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
readfile($full);
exit;
