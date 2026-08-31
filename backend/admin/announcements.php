<?php require_once dirname(__DIR__).'/includes/bootstrap.php'; require_admin(); ensure_v7_schema($pdo); $pageTitle='ประกาศข่าวสาร'; $msg=''; $error='';
if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    $id=(int)($_POST['id']??0);
    $action=$_POST['action']??'';
    if($action==='save'){
        $title=trim((string)($_POST['title']??''));
        $body=trim((string)($_POST['body']??''));
        $isActive=isset($_POST['is_active'])?1:0;
        $sendPush=isset($_POST['send_push']);
        if(mb_strlen($title)<3||mb_strlen($title)>160){
            $error='หัวข้อต้องมี 3–160 ตัวอักษร';
        } elseif($body===''){
            $error='กรุณากรอกเนื้อหาประกาศ';
        } else {
            if($id>0){
                $pdo->prepare('UPDATE announcements SET title=?,body=?,is_active=? WHERE id=?')->execute([$title,$body,$isActive,$id]);
                $msg='บันทึกการแก้ไขแล้ว';
            } else {
                $pdo->prepare('INSERT INTO announcements(title,body,is_active) VALUES(?,?,?)')->execute([$title,$body,$isActive]);
                $msg='เพิ่มประกาศใหม่แล้ว';
            }
            if($sendPush && $isActive){
                try {
                    $sent=firebase_push_all_active($pdo,[
                        'type'=>'admin_notification',
                        'title'=>$title,
                        'body'=>$body,
                    ]);
                    $msg .= ' / ส่ง Push '.$sent.' เครื่อง';
                } catch(Throwable $pushError) {
                    $msg .= ' / บันทึกประกาศแล้ว แต่ Push ยังส่งไม่ได้: '.$pushError->getMessage();
                }
            }
        }
    } elseif($action==='toggle'){
        $pdo->prepare('UPDATE announcements SET is_active=1-is_active WHERE id=?')->execute([$id]);
        $msg='เปลี่ยนสถานะการแสดงผลแล้ว';
    } elseif($action==='delete'){
        $pdo->prepare('DELETE FROM announcements WHERE id=?')->execute([$id]);
        $msg='ลบประกาศแล้ว';
    }
}
$editRow=null;
if(isset($_GET['edit'])){
    $st=$pdo->prepare('SELECT * FROM announcements WHERE id=?');
    $st->execute([(int)$_GET['edit']]);
    $editRow=$st->fetch();
}
$rows=$pdo->query('SELECT * FROM announcements ORDER BY created_at DESC LIMIT 200')->fetchAll();
include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if(!empty($error)):?><p class="bad"><?=e($error)?></p><?php endif;?>

<div class="card" style="max-width:640px;padding:18px;margin-bottom:22px">
    <h2 style="margin-top:0"><?=$editRow?'แก้ไขประกาศ #'.(int)$editRow['id']:'เพิ่มประกาศใหม่'?></h2>
    <p class="muted">ข้อความที่แอดมินเพิ่ม/แก้ไขที่นี่ จะไปแสดงในแอปของสมาชิกโดยอัตโนมัติ</p>
    <form method="post">
        <input type="hidden" name="csrf" value="<?=csrf_token()?>">
        <input type="hidden" name="action" value="save">
        <?php if($editRow):?><input type="hidden" name="id" value="<?=$editRow['id']?>"><?php endif;?>
        <label>หัวข้อ</label>
        <input name="title" required maxlength="160" value="<?=e($editRow['title']??'')?>" style="width:100%;box-sizing:border-box;padding:10px;margin:6px 0 14px">
        <label>เนื้อหา</label>
        <textarea name="body" required rows="4" style="width:100%;box-sizing:border-box;padding:10px;margin:6px 0 14px"><?=e($editRow['body']??'')?></textarea>
        <label style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
            <input type="checkbox" name="is_active" <?=(!$editRow||$editRow['is_active'])?'checked':''?>> แสดงในแอปทันที
        </label>
        <label style="display:flex;align-items:center;gap:8px;margin-bottom:14px">
            <input type="checkbox" name="send_push" checked> ส่ง Push แจ้งสมาชิกด้วย
        </label>
        <button><?=$editRow?'บันทึกการแก้ไข':'เพิ่มประกาศ'?></button>
        <?php if($editRow):?><a class="btn" href="announcements.php" style="background:#756b62">ยกเลิก</a><?php endif;?>
    </form>
</div>

<table>
    <tr><th>หัวข้อ / เนื้อหา</th><th>สถานะ</th><th>วันที่</th><th>จัดการ</th></tr>
    <?php foreach($rows as $r):?>
        <tr>
            <td><strong><?=e($r['title'])?></strong><br><span class="muted"><?=nl2br(e(mb_strimwidth($r['body'],0,140,'…')))?></span></td>
            <td><?=$r['is_active']?'<span style="color:#1c7a2f">แสดงอยู่</span>':'<span class="muted">ซ่อนอยู่</span>'?></td>
            <td><?=e($r['created_at'])?></td>
            <td>
                <a class="btn" href="announcements.php?edit=<?=$r['id']?>">แก้ไข</a>
                <form method="post" style="display:inline">
                    <input type="hidden" name="csrf" value="<?=csrf_token()?>">
                    <input type="hidden" name="id" value="<?=$r['id']?>">
                    <button name="action" value="toggle"><?=$r['is_active']?'ซ่อน':'เผยแพร่'?></button>
                    <button class="danger" name="action" value="delete" onclick="return confirm('ลบประกาศนี้ถาวร?')">ลบ</button>
                </form>
            </td>
        </tr>
    <?php endforeach;?>
    <?php if(!$rows):?><tr><td colspan="4" class="muted">ยังไม่มีประกาศข่าวสาร</td></tr><?php endif;?>
</table>
<?php include '_footer.php';?>
