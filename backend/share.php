<?php
declare(strict_types=1);
require_once __DIR__.'/includes/bootstrap.php';
ensure_v8_schema($pdo);
$id=(int)($_GET['id']??0);
$listing=$id>0?fetch_listing($pdo,$config,$id,true):null;
if(!$listing){ http_response_code(404); $title='ไม่พบประกาศ'; $description='ประกาศนี้อาจถูกลบหรือยังไม่เผยแพร่'; $image=''; }
else{
    $title=$listing['title'].' — ตลาดพระออนไลน์';
    $description=mb_substr(trim((string)($listing['description']??'')),0,180);
    if($description==='') $description='ดูรายละเอียดประกาศพระเครื่อง ราคา '.number_format((float)$listing['price'],0).' บาท';
    $image=(string)($listing['images'][0]['url']??'');
}
$url=app_base($config).'/share.php?id='.$id;
?><!doctype html>
<html lang="th"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title><?=e($title)?></title>
<meta name="description" content="<?=e($description)?>">
<meta property="og:type" content="product">
<meta property="og:site_name" content="ตลาดพระออนไลน์">
<meta property="og:title" content="<?=e($title)?>">
<meta property="og:description" content="<?=e($description)?>">
<meta property="og:url" content="<?=e($url)?>">
<?php if($image):?><meta property="og:image" content="<?=e($image)?>"><?php endif;?>
<style>
body{font-family:system-ui,-apple-system,sans-serif;background:#fff9ef;color:#302116;margin:0}.wrap{max-width:720px;margin:auto;padding:22px}.card{background:#fff;border-radius:20px;padding:18px;box-shadow:0 8px 30px #5b3a1718}img{width:100%;max-height:520px;object-fit:contain;border-radius:16px;background:#f3eee8}.price{font-size:28px;font-weight:800;color:#7b4f21}.muted{color:#786c61}.pill{display:inline-block;background:#fff0c7;color:#5c3214;padding:6px 10px;border-radius:999px;font-weight:700}
</style></head><body><div class="wrap">
<div class="card">
<?php if(!$listing):?><h1>ไม่พบประกาศ</h1><p><?=$description?></p>
<?php else:?>
<?php if($listing['is_premium']):?><span class="pill">★ พรีเมียม</span><?php endif;?> <?php if($listing['status']==='sold'):?><span class="pill">ขายแล้ว</span><?php endif;?>
<h1><?=e($listing['title'])?></h1>
<?php if($image):?><img src="<?=e($image)?>" alt="<?=e($listing['title'])?>"><?php endif;?>
<p class="price">฿<?=number_format((float)$listing['price'],0)?></p>
<p><?=nl2br(e((string)($listing['description']??'')))?></p>
<p class="muted"><?=e($listing['tambon'].' • '.$listing['amphoe'].' • '.$listing['province'])?></p>
<p class="muted">ผู้ขาย: <?=e((string)($listing['seller']['username']??'สมาชิก'))?></p>
<?php endif;?>
</div></div></body></html>