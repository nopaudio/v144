<?php
declare(strict_types=1);
require_once dirname(__DIR__) . '/includes/bootstrap.php';
require_admin();
$msg='';
if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    ensure_chat_schema($pdo);
    $msg='ติดตั้งฐานข้อมูลแชทสำเร็จแล้ว';
}
require __DIR__.'/_header.php';
?>
<h1>อัปเดตระบบแชท</h1>
<?php if($msg): ?><div class="alert success"><?=htmlspecialchars($msg)?></div><?php endif; ?>
<p>กดปุ่มครั้งเดียวเพื่อสร้างตารางข้อความสำหรับระบบแชทในแอป</p>
<form method="post"><input type="hidden" name="csrf" value="<?=htmlspecialchars(csrf_token())?>"><button type="submit">ติดตั้งระบบแชท</button></form>
<?php require __DIR__.'/_footer.php'; ?>
