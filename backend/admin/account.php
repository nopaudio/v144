<?php
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
$pageTitle='เปลี่ยนรหัสผ่านแอดมิน';
$msg=''; $error='';
if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    $current=(string)($_POST['current_password']??'');
    $new=(string)($_POST['new_password']??'');
    $confirm=(string)($_POST['confirm_password']??'');
    $stmt=$pdo->prepare("SELECT password_hash FROM users WHERE id=? AND role='admin' LIMIT 1");
    $stmt->execute([(int)$_SESSION['admin_id']]);
    $row=$stmt->fetch();
    if(!$row || !password_verify($current,$row['password_hash'])) $error='รหัสผ่านปัจจุบันไม่ถูกต้อง';
    elseif(strlen($new)<10) $error='รหัสผ่านใหม่ต้องมีอย่างน้อย 10 ตัว';
    elseif($new!==$confirm) $error='ยืนยันรหัสผ่านใหม่ไม่ตรงกัน';
    else {
        $pdo->prepare('UPDATE users SET password_hash=? WHERE id=?')->execute([password_hash($new,PASSWORD_DEFAULT),(int)$_SESSION['admin_id']]);
        $pdo->prepare('DELETE FROM api_tokens WHERE user_id=?')->execute([(int)$_SESSION['admin_id']]);
        session_regenerate_id(true);
        $msg='เปลี่ยนรหัสผ่านแล้ว';
    }
}
include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>
<div class="card" style="max-width:560px">
<form method="post">
<input type="hidden" name="csrf" value="<?=csrf_token()?>">
<label>รหัสผ่านปัจจุบัน</label><input type="password" name="current_password" required style="width:100%;box-sizing:border-box;padding:10px;margin:6px 0 14px">
<label>รหัสผ่านใหม่ (อย่างน้อย 10 ตัว)</label><input type="password" name="new_password" required minlength="10" style="width:100%;box-sizing:border-box;padding:10px;margin:6px 0 14px">
<label>ยืนยันรหัสผ่านใหม่</label><input type="password" name="confirm_password" required minlength="10" style="width:100%;box-sizing:border-box;padding:10px;margin:6px 0 14px">
<button>บันทึกรหัสผ่านใหม่</button>
</form>
</div>
<?php include '_footer.php';?>
