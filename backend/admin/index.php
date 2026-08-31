<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v10_schema($pdo);
$pageTitle='ภาพรวม';
$adminId=(int)$_SESSION['admin_id'];
$counts=admin_pending_counts($pdo,$adminId);
$latest=$pdo->query("SELECT l.id,l.title,l.status,l.created_at,u.username
    FROM listings l JOIN users u ON u.id=l.user_id
    ORDER BY l.created_at DESC LIMIT 10")->fetchAll();
$latestAlerts=admin_notification_list($pdo,$adminId,6,false);
include '_header.php';
?>
<div id="pending" class="cards">
  <a class="card card-link" href="points.php?status=pending">
    <div class="num"><?=$counts['pending_topups']?></div>
    <div class="metric-label">เติมแต้มรออนุมัติ</div>
    <div class="metric-note">เปิดตรวจสลิป</div>
  </a>
  <a class="card card-link" href="verifications.php?status=pending">
    <div class="num"><?=$counts['pending_verifications']?></div>
    <div class="metric-label">ยืนยันตัวตนรอตรวจ</div>
    <div class="metric-note">หลักฐานอยู่ Private Storage</div>
  </a>
  <a class="card card-link" href="listings.php?status=pending">
    <div class="num"><?=$counts['pending_listings']?></div>
    <div class="metric-label">ประกาศรออนุมัติ</div>
    <div class="metric-note">ตรวจแล้วเปิดขาย</div>
  </a>
  <a class="card card-link" href="reports.php?status=open">
    <div class="num"><?=$counts['open_reports']?></div>
    <div class="metric-label">รายงานใหม่</div>
    <div class="metric-note">เรื่องที่ยังไม่ได้รับตรวจ</div>
  </a>
  <a class="card card-link" href="admin_alerts.php?filter=unread">
    <div class="num"><?=$counts['unread_notifications']?></div>
    <div class="metric-label">Notification ยังไม่อ่าน</div>
    <div class="metric-note">งาน Admin ล่าสุด</div>
  </a>
  <a class="card card-link" href="orders.php">
    <div class="num"><?=$counts['active_orders']?></div>
    <div class="metric-label">Order ที่กำลังดำเนินการ</div>
    <div class="metric-note">V9 ให้ผู้ซื้อ/ผู้ขายเป็นผู้ดำเนินการ จึงไม่มี Admin approval state เพิ่ม</div>
  </a>
  <a class="card card-link" href="users.php">
    <div class="num"><?=$counts['users']?></div>
    <div class="metric-label">สมาชิกทั้งหมด</div>
    <div class="metric-note">จัดการ role และสถานะ</div>
  </a>
</div>

<h2>งานแจ้งเตือนล่าสุด</h2>
<table>
<tr><th>สถานะ</th><th>งาน</th><th>สมาชิก/เรื่อง</th><th>เวลา</th></tr>
<?php foreach($latestAlerts as $r):?>
<tr>
  <td><?=$r['is_read']?'<span class="status-badge status-resolved">อ่านแล้ว</span>':'<span class="status-badge status-pending">ใหม่</span>'?></td>
  <td><a href="admin_alerts.php"><strong><?=e($r['title'])?></strong></a><br><span class="muted"><?=e($r['message'])?></span></td>
  <td><?=e($r['related_username']??'-')?><?php if($r['entity_type']):?><br><span class="muted"><?=e($r['entity_type'])?> #<?=e((string)($r['entity_id']??''))?></span><?php endif;?></td>
  <td><?=e($r['created_at'])?></td>
</tr>
<?php endforeach;?>
<?php if(!$latestAlerts):?><tr><td colspan="4" class="muted">ยังไม่มีงานแจ้งเตือน</td></tr><?php endif;?>
</table>

<h2>ประกาศล่าสุด</h2>
<table>
<tr><th>ID</th><th>หัวข้อ</th><th>สมาชิก</th><th>สถานะ</th><th>วันที่</th></tr>
<?php foreach($latest as $r):?>
<tr>
  <td><?=$r['id']?></td>
  <td><a href="listings.php?q=<?=urlencode($r['title'])?>"><?=e($r['title'])?></a></td>
  <td><?=e($r['username'])?></td>
  <td><span class="status-badge status-<?=e($r['status'])?>"><?=e($r['status'])?></span></td>
  <td><?=e($r['created_at'])?></td>
</tr>
<?php endforeach;?>
</table>
<?php include '_footer.php';?>
