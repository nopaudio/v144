<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v7_schema($pdo);
$pageTitle='แต้ม / เติมแต้ม / สลิป';
$msg=''; $error='';

if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    $action=$_POST['action']??'';
    try {
        if($action==='save_payment'){
            $bank=mb_substr(trim((string)($_POST['bank_name']??'')),0,120);
            $accountName=mb_substr(trim((string)($_POST['account_name']??'')),0,160);
            $accountNumber=mb_substr(trim((string)($_POST['account_number']??'')),0,80);
            $rate=(float)($_POST['points_per_baht']??1);
            $min=(float)($_POST['min_amount']??20);
            $active=isset($_POST['is_active'])?1:0;
            if($bank===''||$accountName===''||$accountNumber==='') throw new RuntimeException('กรุณากรอกข้อมูลบัญชีให้ครบ');
            if($rate<=0||$rate>10000) throw new RuntimeException('อัตราแต้มต่อบาทไม่ถูกต้อง');
            if($min<1||$min>1000000) throw new RuntimeException('ยอดขั้นต่ำไม่ถูกต้อง');
            $pdo->prepare("INSERT INTO payment_settings(id,bank_name,account_name,account_number,points_per_baht,min_amount,is_active)
                VALUES(1,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE bank_name=VALUES(bank_name),account_name=VALUES(account_name),
                account_number=VALUES(account_number),points_per_baht=VALUES(points_per_baht),
                min_amount=VALUES(min_amount),is_active=VALUES(is_active)")
                ->execute([$bank,$accountName,$accountNumber,$rate,$min,$active]);
            $msg='บันทึกบัญชีรับโอนแล้ว';
        }

        if($action==='review_topup'){
            $id=(int)($_POST['id']??0);
            $decision=(string)($_POST['decision']??'');
            admin_review_topup($pdo,(int)$_SESSION['admin_id'],$id,$decision);
            $msg=$decision==='approved'?'อนุมัติสลิปและเพิ่มแต้มแล้ว':'ปฏิเสธคำขอแล้ว';
        }

        if($action==='adjust'){
            $userId=(int)($_POST['user_id']??0);
            $amount=(int)($_POST['amount']??0);
            $description=trim((string)($_POST['description']??''));
            if($userId<=0) throw new RuntimeException('กรุณาระบุสมาชิก');
            if($description==='') $description='ปรับแต้มโดยแอดมิน';
            $newBalance=admin_adjust_points($pdo,$userId,$amount,$description,(int)$_SESSION['admin_id']);
            $msg='ปรับแต้มแล้ว ยอดใหม่ '.$newBalance.' แต้ม';
        }
    } catch(Throwable $e){
        if($pdo->inTransaction()) $pdo->rollBack();
        $error=$e->getMessage();
    }
}

$payment=payment_settings($pdo);
$status=trim((string)($_GET['status']??'pending'));
$allowed=['pending','approved','rejected','all'];
if(!in_array($status,$allowed,true)) $status='pending';
$where=$status==='all'?'1=1':'r.status=?';
$params=$status==='all'?[]:[$status];
$st=$pdo->prepare("SELECT r.*,u.username,COALESCE(p.name,'เติมแต้มตามจำนวน') package_name
    FROM point_topup_requests r
    JOIN users u ON u.id=r.user_id
    LEFT JOIN point_topup_packages p ON p.id=r.package_id
    WHERE $where ORDER BY r.id DESC LIMIT 300");
$st->execute($params); $requests=$st->fetchAll();

$users=$pdo->query("SELECT u.id,u.username,u.email,COALESCE(w.balance,0) balance
    FROM users u LEFT JOIN point_wallets w ON w.user_id=u.id
    WHERE u.status='active' ORDER BY u.username LIMIT 500")->fetchAll();

include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>

<div class="cards">
  <div class="card">
    <h2>บัญชีรับโอน</h2>
    <p class="muted">ข้อมูลนี้จะแสดงในแอปทันทีเมื่อลูกค้ากดเติมแต้ม</p>
    <form method="post">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="action" value="save_payment">
      <label>ธนาคาร</label><input name="bank_name" required maxlength="120" value="<?=e($payment['bank_name'])?>" style="width:100%;box-sizing:border-box;padding:9px;margin:5px 0 10px">
      <label>ชื่อบัญชี</label><input name="account_name" required maxlength="160" value="<?=e($payment['account_name'])?>" style="width:100%;box-sizing:border-box;padding:9px;margin:5px 0 10px">
      <label>เลขบัญชี / PromptPay</label><input name="account_number" required maxlength="80" value="<?=e($payment['account_number'])?>" style="width:100%;box-sizing:border-box;padding:9px;margin:5px 0 10px">
      <label>แต้มต่อ 1 บาท</label><input name="points_per_baht" type="number" min="0.0001" step="0.0001" value="<?=e((string)$payment['points_per_baht'])?>" style="width:100%;box-sizing:border-box;padding:9px;margin:5px 0 10px">
      <label>ยอดเติมขั้นต่ำ (บาท)</label><input name="min_amount" type="number" min="1" step="0.01" value="<?=e((string)$payment['min_amount'])?>" style="width:100%;box-sizing:border-box;padding:9px;margin:5px 0 10px">
      <label><input type="checkbox" name="is_active" <?=$payment['is_active']?'checked':''?>> เปิดรับการเติมแต้ม</label><br><br>
      <button>บันทึกบัญชี</button>
    </form>
  </div>

  <div class="card">
    <h2>ปรับแต้มสมาชิก</h2>
    <form method="post">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="action" value="adjust">
      <label>สมาชิก</label><br>
      <select name="user_id" required style="padding:9px;width:100%;margin:6px 0 10px">
        <option value="">เลือกสมาชิก</option>
        <?php foreach($users as $u):?>
          <option value="<?=$u['id']?>"><?=e($u['username'])?> — <?=e($u['email'])?> — <?=$u['balance']?> แต้ม</option>
        <?php endforeach;?>
      </select>
      <label>จำนวนแต้ม (+ เพิ่ม / - หัก)</label>
      <input name="amount" type="number" required style="padding:9px;width:100%;box-sizing:border-box;margin:6px 0 10px">
      <label>หมายเหตุ</label>
      <input name="description" maxlength="255" style="padding:9px;width:100%;box-sizing:border-box;margin:6px 0 10px">
      <button>บันทึก</button>
    </form>
  </div>
</div>

<div class="card" style="margin-top:18px">
  <h2>คำขอเติมแต้ม / สลิป</h2>
  <p class="muted">ตรวจชื่อบัญชี ยอดเงิน และรูปสลิปก่อนอนุมัติ เมื่ออนุมัติระบบเพิ่มแต้มและบันทึกประวัติให้ทันที</p>
  <p>
    <?php foreach(['pending'=>'รอตรวจ','approved'=>'อนุมัติแล้ว','rejected'=>'ปฏิเสธ','all'=>'ทั้งหมด'] as $k=>$label):?>
      <a class="btn" href="?status=<?=$k?>"><?=$label?></a>
    <?php endforeach;?>
  </p>
</div>

<table style="margin-top:12px">
<tr><th>#</th><th>สมาชิก</th><th>ยอด / แต้ม</th><th>สลิป</th><th>หมายเหตุ</th><th>สถานะ</th><th>จัดการ</th></tr>
<?php foreach($requests as $r):?>
<tr>
  <td><?=$r['id']?></td>
  <td><strong><?=e($r['username'])?></strong><br><span class="muted"><?=e($r['created_at'])?></span></td>
  <td><strong><?=number_format((float)$r['amount'],2)?> บาท</strong><br><?=$r['points']?> แต้ม</td>
  <td>
    <?php if(!empty($r['slip_path'])):?>
      <a href="slip_image.php?id=<?=$r['id']?>" target="_blank" rel="noopener">
        <img class="thumb" src="slip_image.php?id=<?=$r['id']?>" alt="สลิป">
      </a><br><a href="slip_image.php?id=<?=$r['id']?>" target="_blank" rel="noopener">เปิดรูปเต็ม</a>
    <?php else:?><span class="muted">คำขอเก่า ไม่มีสลิป</span><?php endif;?>
  </td>
  <td><?=e($r['note'])?></td>
  <td><span class="status-badge status-<?=e($r['status'])?>"><?=e($r['status'])?></span></td>
  <td>
    <?php if($r['status']==='pending'):?>
    <form method="post" style="display:inline">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="action" value="review_topup">
      <input type="hidden" name="id" value="<?=$r['id']?>">
      <button class="approve" name="decision" value="approved" onclick="return confirm('ตรวจสลิปและยอดเงินจริงแล้ว ยืนยันอนุมัติ?')">อนุมัติ</button>
      <button class="danger" name="decision" value="rejected">ปฏิเสธ</button>
    </form>
    <?php endif;?>
  </td>
</tr>
<?php endforeach;?>
<?php if(!$requests):?><tr><td colspan="7" class="muted">ไม่มีรายการ</td></tr><?php endif;?>
</table>
<?php include '_footer.php';?>
