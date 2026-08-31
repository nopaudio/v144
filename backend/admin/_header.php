<?php
require_admin();
ensure_v10_schema($pdo);
$headerAdminId=(int)($_SESSION['admin_id']??0);
$headerUnread=admin_unread_count($pdo,$headerAdminId);
?>
<!doctype html>
<html lang="th">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title><?=e($pageTitle??'หลังบ้าน')?> — ตลาดพระออนไลน์</title>
<style>
:root{
  --admin-brown:#5f3c1b;--admin-brown-dark:#3f2815;--admin-gold:#d6a44b;
  --admin-bg:#f5f1eb;--admin-card:#fff;--admin-text:#2b2118;--admin-muted:#756b62;
  --admin-green:#207744;--admin-red:#a82620;--admin-orange:#a65b12;--admin-border:#e8ded1;
}
*{box-sizing:border-box}
html{background:var(--admin-bg);color:var(--admin-text)}
body{font-family:"Noto Sans Thai","Leelawadee UI",Tahoma,sans-serif;margin:0;background:var(--admin-bg);color:var(--admin-text);line-height:1.55}
a{color:#684017}
.topnav{position:sticky;top:0;z-index:50;background:linear-gradient(115deg,var(--admin-brown-dark),var(--admin-brown));color:#fff;padding:10px max(14px,4%);box-shadow:0 3px 18px #28180c2b}
.nav-row{max-width:1280px;margin:auto;display:flex;align-items:center;gap:14px}
.brand{display:flex;align-items:center;gap:9px;color:#fff;text-decoration:none;font-weight:800;white-space:nowrap}
.brand-mark{width:34px;height:34px;border-radius:10px;display:grid;place-items:center;background:#fff1;border:1px solid #ffffff36;color:#f3cf82}
.nav-toggle{display:none;margin-left:auto;background:#fff1;border:1px solid #ffffff3b;color:#fff;padding:8px 11px}
.nav-links{display:flex;align-items:center;gap:5px;flex-wrap:wrap}
.nav-links a{color:#fff;text-decoration:none;padding:7px 9px;border-radius:8px;font-size:14px}
.nav-links a:hover,.nav-links a.current{background:#ffffff18}
.nav-spacer{flex:1}
.admin-user{font-size:12px;opacity:.78;white-space:nowrap}
.badge-count{display:inline-grid;place-items:center;min-width:20px;height:20px;padding:0 6px;border-radius:999px;background:#d7352f;color:#fff;font-size:11px;font-weight:800;vertical-align:middle}
.wrap{max-width:1180px;margin:22px auto;padding:0 16px 92px}
.page-title-row{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:16px}
h1{font-size:clamp(24px,4vw,34px);line-height:1.2;margin:0}
h2{line-height:1.3}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:14px}
.card,table{background:var(--admin-card);border-radius:14px;box-shadow:0 4px 18px #3b26100d;border:1px solid var(--admin-border)}
.card{padding:18px}
.card-link{display:block;color:inherit;text-decoration:none;transition:transform .16s ease,box-shadow .16s ease}
.card-link:hover{transform:translateY(-2px);box-shadow:0 8px 24px #3b261018}
.num{font-size:34px;line-height:1.1;font-weight:800;color:var(--admin-brown-dark)}
.metric-label{font-weight:700;margin-top:7px}
.metric-note{font-size:13px;color:var(--admin-muted);margin-top:3px}
table{width:100%;border-collapse:separate;border-spacing:0;overflow:hidden}
th,td{padding:11px 12px;border-bottom:1px solid #eee;text-align:left;vertical-align:top}
th{background:#faf7f2;font-size:13px;color:#5f554c}
tr:last-child td{border-bottom:0}
button,.btn{padding:8px 11px;border:0;border-radius:8px;background:#7b4f21;color:#fff;text-decoration:none;display:inline-block;margin:2px;cursor:pointer;font:inherit;font-weight:650}
button:hover,.btn:hover{filter:brightness(.96)}
.approve,.success{background:var(--admin-green)!important}
.danger,.reject{background:var(--admin-red)!important}
.warning{background:var(--admin-orange)!important}
.secondary{background:#6f675f!important}
.muted{color:var(--admin-muted)}
.thumb{width:76px;height:76px;object-fit:cover;border-radius:10px;border:1px solid var(--admin-border);background:#f3eee7}
.evidence-thumb{width:min(150px,100%);height:auto;max-height:150px;object-fit:cover;border-radius:12px;border:1px solid var(--admin-border)}
.ok,.bad{padding:11px 13px;border-radius:10px;border:1px solid transparent}
.ok{background:#e6f5ea;color:#155c33;border-color:#cce9d5}
.bad{background:#ffe9e7;color:#8f231e;border-color:#f5cecb}
.status-badge{display:inline-flex;align-items:center;gap:5px;padding:4px 8px;border-radius:999px;font-size:12px;font-weight:800;background:#eee7df;color:#594a3e}
.status-pending,.status-open{background:#fff0cc;color:#7b4c00}
.status-approved,.status-verified,.status-resolved,.status-completed,.status-active{background:#dff2e5;color:#175f36}
.status-rejected,.status-dismissed,.status-cancelled,.status-suspended{background:#f8dddd;color:#8f211d}
.status-reviewing,.status-preparing,.status-shipped{background:#dfeafa;color:#234f83}
input,select,textarea{max-width:100%;font:inherit;border:1px solid #cfc4b7;border-radius:8px;background:#fff;color:var(--admin-text)}
input:focus,select:focus,textarea:focus{outline:2px solid #d4b37a66;border-color:#9a6b35}
details{max-width:100%}
summary{cursor:pointer}
.mobile-bottom{display:none}
.mobile-only{display:none}
@media(max-width:860px){
  .topnav{padding:8px 12px}
  .nav-row{flex-wrap:wrap}
  .nav-toggle{display:inline-block}
  .nav-links{display:none;width:100%;padding:8px 0 2px;border-top:1px solid #ffffff24}
  .nav-links.open{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px}
  .nav-links a{padding:9px 10px;background:#ffffff0a}
  .nav-spacer,.admin-user{display:none}
  .wrap{margin:14px auto;padding:0 10px 86px}
  .cards{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
  .card{padding:14px}
  .num{font-size:30px}
  h1{font-size:26px}
  form{max-width:100%}
  input:not([type=checkbox]):not([type=radio]):not([type=hidden]),select,textarea{width:100%!important;min-height:42px;margin:4px 0 8px!important}
  button,.btn{min-height:40px;padding:9px 12px}
  table{display:block;background:transparent;border:0;box-shadow:none;overflow:visible}
  tbody{display:block}
  tr{display:block;background:#fff;border:1px solid var(--admin-border);border-radius:14px;margin:0 0 11px;box-shadow:0 4px 14px #3b26100d;overflow:hidden}
  tr.table-head-row{display:none}
  td{display:grid;grid-template-columns:minmax(88px,34%) minmax(0,1fr);gap:10px;padding:10px 12px;border-bottom:1px solid #eee;overflow-wrap:anywhere}
  td::before{content:attr(data-label);font-size:12px;font-weight:800;color:#756b62}
  td:last-child{border-bottom:0}
  td[colspan]{display:block}
  td[colspan]::before{content:none}
  td form{margin:0}
  td button,td .btn{margin:3px 2px}
  .mobile-bottom{display:grid;grid-template-columns:repeat(5,1fr);position:fixed;left:0;right:0;bottom:0;z-index:70;padding:7px max(5px,env(safe-area-inset-right)) calc(7px + env(safe-area-inset-bottom));background:#fff;border-top:1px solid #ddd1c4;box-shadow:0 -4px 20px #0001}
  .mobile-bottom a{position:relative;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;min-height:48px;color:#544438;text-decoration:none;font-size:11px;font-weight:700}
  .mobile-bottom .ico{font-size:20px;line-height:1}
  .mobile-bottom .badge-count{position:absolute;top:0;right:calc(50% - 24px);font-size:9px;height:17px;min-width:17px}
  .mobile-only{display:initial}
}
@media(max-width:430px){
  .cards{grid-template-columns:1fr 1fr}
  .card{border-radius:12px}
  td{grid-template-columns:86px minmax(0,1fr);padding:9px 10px}
}
</style>
</head>
<body>
<nav class="topnav">
  <div class="nav-row">
    <a class="brand" href="index.php"><span class="brand-mark">◆</span><span>ตลาดพระออนไลน์ — Admin</span></a>
    <button type="button" class="nav-toggle" id="adminNavToggle" aria-expanded="false" aria-controls="adminNavLinks">เมนู</button>
    <div class="nav-links" id="adminNavLinks">
      <a href="index.php">ภาพรวม</a>
      <a href="listings.php">ประกาศ</a>
      <a href="home_content.php">หน้าแรก</a>
      <a href="announcements.php">ข่าวสาร</a>
      <a href="admin_alerts.php">งานแจ้งเตือน <?php if($headerUnread>0):?><span class="badge-count"><?=$headerUnread>99?'99+':$headerUnread?></span><?php endif;?></a>
      <a href="notifications.php">Push สมาชิก</a>
      <a href="reports.php">แจ้งปัญหา</a>
      <a href="users.php">สมาชิก</a>
      <a href="verifications.php">ยืนยันตัวตน</a>
      <a href="points.php">แต้ม/เติมแต้ม</a>
      <a href="lottery.php">ลุ้นพระ</a>
      <a href="premium.php">พรีเมียม/ดันโพสต์</a>
      <a href="orders.php">คำสั่งซื้อ</a>
      <a href="update_system.php">อัปเดตระบบ</a>
      <a href="account.php">รหัสผ่าน</a>
      <a href="logout.php">ออกจากระบบ</a>
    </div>
    <span class="nav-spacer"></span>
    <span class="admin-user"><?=e((string)($_SESSION['admin_username']??'Admin'))?></span>
  </div>
</nav>
<main class="wrap">
  <div class="page-title-row"><h1><?=e($pageTitle??'หลังบ้าน')?></h1></div>
