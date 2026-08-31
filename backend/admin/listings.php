<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v8_schema($pdo);
$pageTitle='จัดการประกาศ'; $msg=''; $error='';

if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    $id=(int)($_POST['id']??0); $action=$_POST['action']??'';
    try {
        if(in_array($action,['pending','approved','hidden','rejected','sold'],true)){
            admin_update_listing_status($pdo,(int)$_SESSION['admin_id'],$id,(string)$action);
            $msg='อัปเดตสถานะแล้ว';
        }
        if($action==='delete'){
            $chk=$pdo->prepare('SELECT order_id FROM orders WHERE listing_id=? LIMIT 1'); $chk->execute([$id]);
            if($chk->fetchColumn()) throw new RuntimeException('ลบประกาศที่มีประวัติ Order ไม่ได้ กรุณาเปลี่ยนสถานะแทน');
            $st=$pdo->prepare('SELECT file_path FROM listing_images WHERE listing_id=?');$st->execute([$id]);$files=$st->fetchAll();
            $pdo->prepare('DELETE FROM listings WHERE id=?')->execute([$id]);
            foreach($files as $f) @unlink(dirname(__DIR__).'/'.$f['file_path']);
            $msg='ลบประกาศแล้ว';
        }
    } catch(Throwable $e){ $error=$e->getMessage(); }
}

$q=trim((string)($_GET['q']??'')); $status=trim((string)($_GET['status']??''));
$where=[];$params=[];
if($q!==''){$where[]='(l.title LIKE ? OR u.username LIKE ?)';$params[]="%$q%";$params[]="%$q%";}
if($status!==''){$where[]='l.status=?';$params[]=$status;}
$sql='SELECT l.*,u.username,
 (SELECT file_path FROM listing_images WHERE listing_id=l.id ORDER BY sort_order LIMIT 1) cover,
 (SELECT MAX(pp.ends_at) FROM premium_promotions pp WHERE pp.listing_id=l.id AND pp.status=\'active\' AND pp.starts_at<=NOW() AND pp.ends_at>NOW()) premium_until,
 (SELECT COUNT(*) FROM orders o WHERE o.listing_id=l.id AND o.status IN (\'pending_confirmation\',\'preparing\',\'shipped\')) active_orders
 FROM listings l JOIN users u ON u.id=l.user_id'.
 ($where?' WHERE '.implode(' AND ',$where):'').' ORDER BY l.created_at DESC LIMIT 300';
$st=$pdo->prepare($sql);$st->execute($params);$rows=$st->fetchAll();
include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>
<form method="get"><input name="q" value="<?=e($q)?>" placeholder="ค้นหัวข้อ/สมาชิก" style="padding:9px"><select name="status" style="padding:9px"><option value="">ทุกสถานะ</option><?php foreach(['pending','approved','hidden','rejected','sold'] as $s):?><option <?=$status===$s?'selected':''?>><?=$s?></option><?php endforeach;?></select><button>ค้นหา</button></form>
<table><tr><th>รูป</th><th>ประกาศ</th><th>ที่อยู่/ราคา</th><th>สถานะ</th><th>จัดการ</th></tr>
<?php foreach($rows as $r):?><tr>
<td><?php if($r['cover']):?><img class="thumb" src="../<?=e($r['cover'])?>"><?php endif;?></td>
<td><strong><?=e($r['title'])?></strong><br><span class="muted">#<?=$r['id']?> โดย <?=e($r['username'])?><br><?=e($r['created_at'])?></span><details><summary>รายละเอียด</summary><?=nl2br(e($r['description']))?></details></td>
<td><?=number_format((float)$r['price'],2)?> บาท<br><?=e($r['tambon'])?> <?=e($r['amphoe'])?> <?=e($r['province'])?></td>
<td><span class="status-badge status-<?=e($r['status'])?>"><?=e($r['status'])?></span>
<?php if($r['premium_until']):?><br><strong style="color:#a06b00">★ พรีเมียม</strong><br><span class="muted">ถึง <?=e($r['premium_until'])?></span><?php endif;?>
<?php if($r['boosted_at']):?><br><strong>↑ ดันล่าสุด</strong><br><span class="muted"><?=e($r['boosted_at'])?></span><?php endif;?>
<?php if((int)$r['active_orders']>0):?><br><strong style="color:#8b4b00">มีผู้สั่งซื้อแล้ว</strong><?php endif;?>
</td>
<td><form method="post"><input type="hidden" name="csrf" value="<?=csrf_token()?>"><input type="hidden" name="id" value="<?=$r['id']?>"><?php foreach(['approved'=>'อนุมัติ','hidden'=>'ซ่อน','rejected'=>'ไม่อนุมัติ','sold'=>'ขายแล้ว','pending'=>'รอตรวจ'] as $a=>$label):?><button class="<?=$a==='approved'?'approve':($a==='rejected'?'danger':($a==='pending'?'warning':''))?>" name="action" value="<?=$a?>"><?=$label?></button><?php endforeach;?><button class="danger" name="action" value="delete" onclick="return confirm('ลบถาวร?')">ลบ</button></form></td>
</tr><?php endforeach;?></table>
<?php include '_footer.php';?>
