<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v9_schema($pdo);
$pageTitle='ยืนยันตัวตนสมาชิก';
$msg=''; $error='';

if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    $userId=(int)($_POST['user_id']??0);
    $action=(string)($_POST['action']??'');
    $submittedAt=(string)($_POST['submitted_at']??'');
    $documentPath=(string)($_POST['document_path']??'');
    try {
        if($userId<=0 || $submittedAt==='' || $documentPath==='') throw new RuntimeException('ข้อมูลคำขอไม่ครบ');
        if(!in_array($action,['approve','reject'],true)) throw new RuntimeException('คำสั่งไม่ถูกต้อง');
        $decision=$action==='approve'?'approved':'rejected';
        $reason=(string)($_POST['rejection_reason']??'');
        admin_review_verification(
            $pdo,(int)$_SESSION['admin_id'],$userId,$decision,$reason,$submittedAt,$documentPath
        );
        $msg=$decision==='approved'?'อนุมัติการยืนยันตัวตนแล้ว':'ปฏิเสธคำขอแล้ว';
    } catch(Throwable $e){ $error=$e->getMessage(); }
}

$status=(string)($_GET['status']??'');
$params=[];
$where='';
if(in_array($status,['pending','verified','rejected'],true)){
    $where=' WHERE iv.status=?';
    $params[]=$status;
}
$st=$pdo->prepare("SELECT iv.*,u.username,u.email,reviewer.username reviewer_username
    FROM identity_verifications iv
    JOIN users u ON u.id=iv.user_id
    LEFT JOIN users reviewer ON reviewer.id=iv.reviewed_by
    $where
    ORDER BY CASE iv.status WHEN 'pending' THEN 0 WHEN 'rejected' THEN 1 ELSE 2 END,iv.submitted_at DESC
    LIMIT 500");
$st->execute($params); $rows=$st->fetchAll();

include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>

<p>
  <a class="btn" href="verifications.php">ทั้งหมด</a>
  <a class="btn" href="verifications.php?status=pending">รอตรวจ</a>
  <a class="btn" href="verifications.php?status=verified">ยืนยันแล้ว</a>
  <a class="btn" href="verifications.php?status=rejected">ไม่ผ่าน</a>
</p>

<table>
<tr><th>สมาชิก / วันที่ส่ง</th><th>บัญชีรับเงิน</th><th>หลักฐาน</th><th>สถานะ</th><th>ตรวจสอบ</th></tr>
<?php foreach($rows as $r):?>
<tr>
  <td>
    <strong><?=e($r['username'])?></strong><br>
    <?=e($r['email'])?><br>
    <span class="muted">ส่ง <?=e($r['submitted_at'])?></span>
  </td>
  <td>
    ธนาคาร: <strong><?=e($r['bank_name'])?></strong><br>
    ชื่อบัญชี: <?=e($r['account_name'])?><br>
    เลขบัญชี: <strong><?=e($r['account_number'])?></strong>
  </td>
  <td>
    <a class="btn" target="_blank" rel="noopener" href="identity_document.php?user_id=<?=$r['user_id']?>">เปิดรูปหลักฐาน</a>
    <br><span class="muted">ไฟล์จริงไม่เปิดผ่าน public URL</span>
  </td>
  <td>
    <span class="status-badge status-<?=e($r['status'])?>"><?=e(verification_status_label((string)$r['status']))?></span><br>
    <?php if($r['rejection_reason']):?><span style="color:#a82620"><?=e($r['rejection_reason'])?></span><br><?php endif;?>
    <?php if($r['reviewed_at']):?><span class="muted">ตรวจ <?=e($r['reviewed_at'])?> โดย <?=e($r['reviewer_username']??'Admin')?></span><?php endif;?>
  </td>
  <td style="min-width:240px">
    <form method="post" style="margin-bottom:8px">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="user_id" value="<?=$r['user_id']?>">
      <input type="hidden" name="submitted_at" value="<?=e($r['submitted_at'])?>">
      <input type="hidden" name="document_path" value="<?=e($r['document_path'])?>">
      <button class="approve" name="action" value="approve">อนุมัติ</button>
    </form>
    <form method="post">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="user_id" value="<?=$r['user_id']?>">
      <input type="hidden" name="submitted_at" value="<?=e($r['submitted_at'])?>">
      <input type="hidden" name="document_path" value="<?=e($r['document_path'])?>">
      <input name="rejection_reason" maxlength="500" placeholder="เหตุผลที่ไม่ผ่าน" style="padding:7px;width:190px">
      <button class="danger" name="action" value="reject">ไม่ผ่าน</button>
    </form>
  </td>
</tr>
<?php endforeach;?>
</table>
<?php if(!$rows):?><p class="muted">ยังไม่มีคำขอในรายการนี้</p><?php endif;?>
<?php include '_footer.php';?>
