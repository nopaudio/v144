<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v10_schema($pdo);
$pageTitle='งานแจ้งเตือน Admin';
$adminId=(int)$_SESSION['admin_id'];
$msg=''; $error='';

if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    $action=(string)($_POST['action']??'');
    try{
        if($action==='mark_all'){
            admin_mark_all_notifications_read($pdo,$adminId);
            $msg='ทำเครื่องหมายอ่านทั้งหมดแล้ว';
        } elseif($action==='mark_read'){
            $id=(int)($_POST['id']??0);
            admin_mark_notification_read($pdo,$adminId,$id);
            $msg='ทำเครื่องหมายอ่านแล้ว';
        } elseif($action==='open'){
            $id=(int)($_POST['id']??0);
            $st=$pdo->prepare("SELECT action_path FROM admin_notifications WHERE id=? LIMIT 1");
            $st->execute([$id]); $path=$st->fetchColumn();
            if(!$path) throw new RuntimeException('ไม่พบ Notification');
            admin_mark_notification_read($pdo,$adminId,$id);
            header('Location: '.admin_safe_action_path((string)$path));
            exit;
        }
    } catch(Throwable $e){ $error=$e->getMessage(); }
}

$unreadOnly=(string)($_GET['filter']??'')==='unread';
$rows=admin_notification_list($pdo,$adminId,300,$unreadOnly);
$unread=admin_unread_count($pdo,$adminId);
include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>

<div class="cards" style="margin-bottom:16px">
  <div class="card">
    <div class="num"><?=$unread?></div>
    <div class="metric-label">ยังไม่ได้อ่าน</div>
    <div class="metric-note">งานที่สมาชิกส่งเข้ามาและต้องให้ผู้ดูแลตรวจ</div>
  </div>
  <div class="card">
    <strong>ตัวกรอง</strong><br><br>
    <a class="btn" href="admin_alerts.php">ทั้งหมด</a>
    <a class="btn warning" href="admin_alerts.php?filter=unread">ยังไม่ได้อ่าน</a>
  </div>
  <div class="card">
    <strong>จัดการ</strong><br><br>
    <form method="post">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <button class="secondary" name="action" value="mark_all" <?=$unread===0?'disabled':''?>>อ่านทั้งหมด</button>
    </form>
  </div>
</div>

<table>
<tr><th>สถานะ</th><th>งาน</th><th>สมาชิก / เรื่อง</th><th>เวลา</th><th>เปิด</th></tr>
<?php foreach($rows as $r):?>
<tr>
  <td>
    <?php if($r['is_read']):?>
      <span class="status-badge status-resolved">อ่านแล้ว</span>
    <?php else:?>
      <span class="status-badge status-pending">ใหม่</span>
    <?php endif;?>
  </td>
  <td>
    <strong><?=e($r['title'])?></strong><br>
    <span class="muted"><?=e($r['message'])?></span><br>
    <small class="muted"><?=e($r['type'])?></small>
  </td>
  <td>
    <?=e($r['related_username']??'-')?>
    <?php if($r['entity_type']):?><br><span class="muted"><?=e($r['entity_type'])?> #<?=e((string)($r['entity_id']??''))?></span><?php endif;?>
  </td>
  <td><?=e($r['created_at'])?></td>
  <td>
    <?php if(!$r['is_read']):?>
    <form method="post" style="display:inline">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="id" value="<?=$r['id']?>">
      <button class="secondary" name="action" value="mark_read">อ่านแล้ว</button>
    </form>
    <?php endif;?>
    <form method="post" style="display:inline">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="id" value="<?=$r['id']?>">
      <button name="action" value="open">เปิดงาน</button>
    </form>
  </td>
</tr>
<?php endforeach;?>
<?php if(!$rows):?><tr><td colspan="5" class="muted">ยังไม่มี Notification ในรายการนี้</td></tr><?php endif;?>
</table>
<?php include '_footer.php';?>
