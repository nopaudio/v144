<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
if (!empty($_SESSION['admin_id'])) { header('Location: index.php'); exit; }
$error='';
if ($_SERVER['REQUEST_METHOD']==='POST') {
    $stmt=$pdo->prepare("SELECT * FROM users WHERE (username=? OR email=?) AND role='admin' LIMIT 1");
    $stmt->execute([post_value('username'),post_value('username')]);
    $u=$stmt->fetch();
    if ($u && $u['status']==='active' && password_verify((string)($_POST['password']??''),$u['password_hash'])) {
        session_regenerate_id(true);
        $_SESSION['admin_id']=$u['id'];
        $_SESSION['admin_username']=$u['username'];
        header('Location:index.php');
        exit;
    }
    $error='ข้อมูลเข้าสู่ระบบไม่ถูกต้อง หรือบัญชีถูกระงับ';
}
?><!doctype html>
<html lang="th">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>Admin Login — ตลาดพระออนไลน์</title>
<style>
:root{--brown:#4b2a12;--brown2:#6e4520;--gold:#d7aa57;--cream:#f5f1eb;--red:#9b2b24}
*{box-sizing:border-box}
html,body{min-height:100%;margin:0}
body{
  font-family:"Noto Sans Thai","Leelawadee UI",Tahoma,sans-serif;
  background:
    radial-gradient(circle at 15% 10%,#d7aa5724 0 13%,transparent 14%),
    linear-gradient(145deg,#efe7dd,#f8f5f1 55%,#eee4d7);
  color:#2c2119;display:grid;place-items:center;padding:20px;
}
.box{
  width:min(420px,100%);background:#fff;padding:clamp(22px,6vw,34px);
  border:1px solid #e8ddd0;border-radius:22px;
  box-shadow:0 18px 55px #3923101f;
}
.mark{width:52px;height:52px;border-radius:15px;display:grid;place-items:center;background:var(--brown);color:var(--gold);font-size:25px;margin-bottom:18px}
h1{margin:0 0 7px;font-size:clamp(25px,7vw,32px);line-height:1.25}
.sub{margin:0 0 20px;color:#766b62}
label{display:block;font-weight:750;margin-top:12px}
input,button{
  width:100%;box-sizing:border-box;min-height:48px;padding:12px 13px;margin:6px 0;
  border-radius:11px;font:inherit;font-size:16px;
}
input{border:1px solid #cfc1b2;background:#fff}
input:focus{outline:3px solid #d7aa5738;border-color:#9c713e}
button{margin-top:16px;background:linear-gradient(135deg,var(--brown),var(--brown2));color:#fff;border:0;font-weight:800;cursor:pointer}
button:active{transform:translateY(1px)}
.err,.ok{padding:11px 12px;border-radius:10px}
.err{color:#7f211c;background:#fde8e6;border:1px solid #f3cfca}
.ok{color:#225c35;background:#e7f4eb;border:1px solid #cce5d3}
@media(max-width:420px){body{padding:12px}.box{border-radius:18px}}
</style>
</head>
<body>
<div class="box">
  <div class="mark">◆</div>
  <h1>หลังบ้านตลาดพระออนไลน์</h1>
  <p class="sub">เข้าสู่ระบบด้วยบัญชีที่ Server กำหนดสิทธิ์เป็น Admin</p>
  <?php if(isset($_GET['installed'])):?><p class="ok">ตั้งค่าระบบสำเร็จ กรุณาเข้าสู่ระบบ</p><?php endif;?>
  <?php if($error):?><p class="err"><?=e($error)?></p><?php endif;?>
  <form method="post">
    <label for="username">Username หรือ Email</label>
    <input id="username" name="username" autocomplete="username" required autofocus>
    <label for="password">Password</label>
    <input id="password" type="password" name="password" autocomplete="current-password" required>
    <button>เข้าสู่ระบบ</button>
  </form>
</div>
</body>
</html>
