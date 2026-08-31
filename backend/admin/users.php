<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v10_schema($pdo);

$pageTitle='จัดการสมาชิก';
$msg='';
$error='';

if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    $id=(int)($_POST['id']??0);
    $action=(string)($_POST['action']??'');

    try {
        if($id===(int)$_SESSION['admin_id'] && in_array($action,['suspend','member','delete'],true)){
            throw new RuntimeException('ไม่สามารถระงับ ลดสิทธิ์ หรือลบบัญชีที่กำลังใช้งาน');
        }

        if($action==='active') $pdo->prepare("UPDATE users SET status='active' WHERE id=?")->execute([$id]);
        if($action==='suspend') $pdo->prepare("UPDATE users SET status='suspended' WHERE id=?")->execute([$id]);
        if($action==='admin') $pdo->prepare("UPDATE users SET role='admin' WHERE id=?")->execute([$id]);
        if($action==='member') $pdo->prepare("UPDATE users SET role='member' WHERE id=?")->execute([$id]);

        if($action==='save_profile'){
            $displayRaw=trim((string)($_POST['display_name']??''));
            $displayName=$displayRaw!=='' ? normalize_display_name($displayRaw) : null;
            $stars=max(0,min(5,(int)($_POST['admin_stars']??0)));
            $specialIcon=mb_substr(trim((string)($_POST['special_icon']??'')),0,16);
            if(preg_match('/[\r\n<>]/u',$specialIcon)) throw new RuntimeException('ไอคอนพิเศษไม่ถูกต้อง');
            $pointsDelta=(int)($_POST['points_delta']??0);
            if(abs($pointsDelta)>1000000) throw new RuntimeException('จำนวนแต้มที่ปรับมากเกินไป');

            $pdo->prepare("UPDATE users SET display_name=?,admin_stars=?,special_icon=? WHERE id=?")
                ->execute([$displayName,$stars,$specialIcon?:null,$id]);
            if($pointsDelta!==0){
                admin_adjust_points($pdo,$id,$pointsDelta,'แอดมินปรับแต้มจากหน้าสมาชิก',(int)$_SESSION['admin_id']);
            }
        }

        if(in_array($action,['approve_name','reject_name'],true)){
            $requestId=(int)($_POST['request_id']??0);
            $adminNote=mb_substr(trim((string)($_POST['admin_note']??'')),0,500);
            $decision=$action==='approve_name'?'approved':'rejected';

            $pdo->beginTransaction();
            try{
                $st=$pdo->prepare("SELECT * FROM display_name_change_requests WHERE id=? LIMIT 1 FOR UPDATE");
                $st->execute([$requestId]); $request=$st->fetch();
                if(!$request || $request['status']!=='pending') throw new RuntimeException('คำขอนี้ถูกดำเนินการแล้วหรือไม่พบ');
                if($decision==='approved'){
                    $approvedName=normalize_display_name((string)$request['requested_name']);
                    $pdo->prepare("UPDATE users SET display_name=?,display_name_change_count=display_name_change_count+1 WHERE id=?")
                        ->execute([$approvedName,(int)$request['user_id']]);
                }
                $pdo->prepare("UPDATE display_name_change_requests
                    SET status=?,reviewed_by=?,reviewed_at=NOW(),admin_note=? WHERE id=?")
                    ->execute([$decision,(int)$_SESSION['admin_id'],$adminNote?:null,$requestId]);
                $pdo->commit();
            }catch(Throwable $e){
                if($pdo->inTransaction()) $pdo->rollBack();
                throw $e;
            }
        }

        if($action==='delete'){
            $orderCheck=$pdo->prepare('SELECT COUNT(*) FROM orders WHERE buyer_id=? OR seller_id=?');
            $orderCheck->execute([$id,$id]);
            if((int)$orderCheck->fetchColumn()>0){
                throw new RuntimeException('สมาชิกนี้มีประวัติคำสั่งซื้อ จึงลบบัญชีไม่ได้ กรุณาใช้ “ระงับ” แทน');
            }

            $images=$pdo->prepare('SELECT i.file_path FROM listing_images i JOIN listings l ON l.id=i.listing_id WHERE l.user_id=?');
            $images->execute([$id]);
            $paths=$images->fetchAll();
            $pdo->prepare('DELETE FROM users WHERE id=?')->execute([$id]);
            foreach($paths as $image){
                if(!empty($image['file_path'])) @unlink(dirname(__DIR__).'/'.$image['file_path']);
            }
        }
        $msg='อัปเดตสมาชิกแล้ว';
    } catch(Throwable $e){
        $error=$e->getMessage();
    }
}

$q=trim((string)($_GET['q']??''));
$sql="SELECT u.*,
    COALESCE(pw.balance,0) points_balance,
    (SELECT COUNT(*) FROM listings l WHERE l.user_id=u.id) listing_count
    FROM users u LEFT JOIN point_wallets pw ON pw.user_id=u.id";
$params=[];
if($q!==''){
    $sql.=" WHERE u.username LIKE ? OR u.display_name LIKE ? OR u.email LIKE ?";
    $params=["%$q%","%$q%","%$q%"];
}
$sql.=" ORDER BY u.created_at DESC LIMIT 300";
$st=$pdo->prepare($sql);
$st->execute($params);
$rows=$st->fetchAll();

$pending=$pdo->query("SELECT r.*,u.username,u.display_name,u.email
    FROM display_name_change_requests r
    JOIN users u ON u.id=r.user_id
    WHERE r.status='pending' ORDER BY r.created_at ASC LIMIT 100")->fetchAll();

include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>

<?php if($pending):?>
<h2>คำขอเปลี่ยนชื่อที่รออนุมัติ</h2>
<table>
<tr><th>สมาชิก</th><th>ชื่อที่ขอ</th><th>เหตุผล</th><th>จัดการ</th></tr>
<?php foreach($pending as $r):?>
<tr>
<td><strong><?=e(($r['display_name']?:$r['username']))?></strong><br><?=e($r['email'])?></td>
<td><?=e($r['requested_name'])?></td>
<td><?=e($r['reason'])?><br><span class="muted"><?=e($r['created_at'])?></span></td>
<td>
<form method="post">
<input type="hidden" name="csrf" value="<?=csrf_token()?>">
<input type="hidden" name="id" value="<?=$r['user_id']?>">
<input type="hidden" name="request_id" value="<?=$r['id']?>">
<input name="admin_note" maxlength="500" placeholder="หมายเหตุ (ถ้ามี)" style="padding:7px;max-width:220px">
<button name="action" value="approve_name">อนุมัติ</button>
<button class="danger" name="action" value="reject_name">ไม่อนุมัติ</button>
</form>
</td>
</tr>
<?php endforeach;?>
</table>
<?php endif;?>

<h2>สมาชิกทั้งหมด</h2>
<form>
<input name="q" value="<?=e($q)?>" placeholder="ค้นชื่อ ชื่อที่แสดง หรืออีเมล" style="padding:9px">
<button>ค้นหา</button>
</form>
<table>
<tr><th>สมาชิก</th><th>ติดต่อ</th><th>สิทธิ์/สถานะ</th><th>ประกาศ/แต้ม</th><th>ปรับโปรไฟล์</th><th>จัดการ</th></tr>
<?php foreach($rows as $u):?>
<tr>
<td>
<strong><?=e(($u['special_icon']?:'').($u['display_name']?:$u['username']))?></strong>
<?php if((int)$u['admin_stars']>0):?><br><span style="color:#b57600"><?=str_repeat('★',(int)$u['admin_stars'])?></span><?php endif;?>
<br><span class="muted">Login: <?=e($u['username'])?> • #<?=$u['id']?></span>
<br><?=e($u['email'])?><br><span class="muted">สมัคร <?=e($u['created_at'])?></span>
</td>
<td><?=e($u['phone'])?><br><?=e($u['line_id'])?></td>
<td><?=e($u['role'])?> / <?=e($u['status'])?></td>
<td><?=$u['listing_count']?> ประกาศ<br><strong><?=number_format((int)$u['points_balance'])?> แต้ม</strong></td>
<td>
<form method="post">
<input type="hidden" name="csrf" value="<?=csrf_token()?>">
<input type="hidden" name="id" value="<?=$u['id']?>">
<label>ชื่อที่แสดง</label><br>
<input name="display_name" maxlength="40" value="<?=e((string)($u['display_name']??''))?>" placeholder="รองรับชื่อภาษาไทย" style="padding:6px;width:150px"><br>
<label>ดาวแอดมิน 0–5</label><br>
<input name="admin_stars" type="number" min="0" max="5" value="<?=(int)$u['admin_stars']?>" style="padding:6px;width:70px"><br>
<label>ไอคอนพิเศษ</label><br>
<input name="special_icon" maxlength="16" value="<?=e((string)($u['special_icon']??''))?>" placeholder="เช่น ⭐ 🏆" style="padding:6px;width:100px"><br>
<label>บวก/ลบแต้ม</label><br>
<input name="points_delta" type="number" value="0" style="padding:6px;width:90px">
<button name="action" value="save_profile">บันทึก</button>
</form>
</td>
<td>
<form method="post">
<input type="hidden" name="csrf" value="<?=csrf_token()?>">
<input type="hidden" name="id" value="<?=$u['id']?>">
<button name="action" value="active">เปิดใช้</button>
<button name="action" value="suspend">ระงับ</button>
<button name="action" value="admin">ให้ Admin</button>
<button name="action" value="member">ให้ Member</button>
<button class="danger" name="action" value="delete" onclick="return confirm('ลบสมาชิกและประกาศทั้งหมด?')">ลบ</button>
</form>
</td>
</tr>
<?php endforeach;?>
</table>
<?php include '_footer.php';?>
