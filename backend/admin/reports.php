<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v7_schema($pdo);
$pageTitle='แจ้งปัญหา / รายงานผู้ใช้';
$msg=''; $error='';

if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    $action=(string)($_POST['action']??'');
    $id=(int)($_POST['id']??0);
    try {
        $note=(string)($_POST['admin_note']??'');
        admin_update_report($pdo,(int)$_SESSION['admin_id'],$id,$action,$note);
        $msg=match($action){
            'reviewing'=>'รับเรื่องตรวจสอบแล้ว',
            'resolve'=>'ปิดเรื่องแล้ว',
            'dismiss'=>'ยกเลิกรายงานแล้ว',
            'suspend_user'=>'ระงับผู้ใช้และปิดรายงานแล้ว',
            default=>'อัปเดตรายงานแล้ว'
        };
    } catch(Throwable $e){
        if($pdo->inTransaction()) $pdo->rollBack();
        $error=$e->getMessage();
    }
}

$status=trim((string)($_GET['status']??'open'));
if(!in_array($status,['open','reviewing','resolved','dismissed','all'],true)) $status='open';
$where=$status==='all'?'1=1':'r.status=?';
$params=$status==='all'?[]:[$status];
$st=$pdo->prepare("SELECT r.*, reporter.username reporter_name, reported.username reported_name,
        l.title listing_title, reported.status reported_status
    FROM user_reports r
    JOIN users reporter ON reporter.id=r.reporter_user_id
    LEFT JOIN users reported ON reported.id=r.reported_user_id
    LEFT JOIN listings l ON l.id=r.listing_id
    WHERE $where ORDER BY r.id DESC LIMIT 300");
$st->execute($params); $rows=$st->fetchAll();

$labels=['fraud'=>'สงสัยโกง','payment'=>'ปัญหาการชำระเงิน','inappropriate'=>'พฤติกรรมไม่เหมาะสม','fake_listing'=>'ประกาศน่าสงสัย','other'=>'อื่น ๆ'];
include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>
<div class="card">
<p>กรอง:
<?php foreach(['open'=>'เรื่องใหม่','reviewing'=>'กำลังตรวจ','resolved'=>'ปิดแล้ว','dismissed'=>'ยกเลิก','all'=>'ทั้งหมด'] as $k=>$v):?>
<a class="btn" href="?status=<?=$k?>"><?=$v?></a>
<?php endforeach;?>
</p>
</div>
<table style="margin-top:14px">
<tr><th># / ผู้แจ้ง</th><th>ผู้ถูกรายงาน</th><th>เรื่อง</th><th>รายละเอียด</th><th>สถานะ</th><th>จัดการ</th></tr>
<?php foreach($rows as $r):?>
<tr>
<td>#<?=$r['id']?><br><strong><?=e($r['reporter_name'])?></strong><br><span class="muted"><?=e($r['created_at'])?></span></td>
<td><strong><?=e($r['reported_name']??'-')?></strong><br><span class="muted">สถานะบัญชี: <?=e($r['reported_status']??'-')?></span></td>
<td><?=e($labels[$r['category']]??$r['category'])?><?php if($r['listing_id']):?><br>ประกาศ #<?=$r['listing_id']?> <?=e($r['listing_title']??'')?><?php endif;?></td>
<td><?=nl2br(e($r['details']))?><?php if($r['admin_note']):?><hr><strong>หมายเหตุแอดมิน:</strong> <?=nl2br(e($r['admin_note']))?><?php endif;?></td>
<td><span class="status-badge status-<?=e($r['status'])?>"><?=e($r['status'])?></span></td>
<td>
<form method="post">
<input type="hidden" name="csrf" value="<?=csrf_token()?>"><input type="hidden" name="id" value="<?=$r['id']?>">
<textarea name="admin_note" rows="2" maxlength="1000" placeholder="หมายเหตุแอดมิน" style="width:220px"></textarea><br>
<?php if(in_array($r['status'],['open','reviewing'],true)):?>
<button name="action" value="reviewing">กำลังตรวจ</button>
<button name="action" value="resolve">ปิดเรื่อง</button>
<button name="action" value="dismiss">ยกเลิก</button>
<?php if(!empty($r['reported_user_id']) && $r['reported_status']==='active'):?>
<button class="danger" name="action" value="suspend_user" onclick="return confirm('ระงับบัญชีผู้ถูกรายงานทันที?')">ระงับผู้ใช้</button>
<?php endif;?>
<?php endif;?>
</form>
</td>
</tr>
<?php endforeach;?>
<?php if(!$rows):?><tr><td colspan="6" class="muted">ไม่มีรายงานในสถานะนี้</td></tr><?php endif;?>
</table>
<?php include '_footer.php';?>
