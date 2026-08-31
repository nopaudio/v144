<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v8_schema($pdo);

$pageTitle='พรีเมียม / ดันโพสต์';
$msg=''; $error='';

if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    $action=$_POST['action']??'';
    try{
        if($action==='save_plan'){
            $id=(int)($_POST['id']??0);
            $name=trim((string)($_POST['name']??''));
            $cost=max(1,(int)($_POST['points_cost']??0));
            $days=max(1,(int)($_POST['duration_days']??0));
            $active=isset($_POST['is_active'])?1:0;
            if($name==='') throw new RuntimeException('กรุณากรอกชื่อแพ็กเกจ');
            if($id>0){
                $pdo->prepare("UPDATE premium_plans SET name=?,points_cost=?,duration_days=?,is_active=? WHERE id=?")
                    ->execute([$name,$cost,$days,$active,$id]);
            }else{
                $pdo->prepare("INSERT INTO premium_plans(name,points_cost,duration_days,is_active,sort_order) VALUES(?,?,?,?,100)")
                    ->execute([$name,$cost,$days,$active]);
            }
            $msg='บันทึกแพ็กเกจพรีเมียมแล้ว';
        }

        if($action==='save_boost'){
            $cost=max(1,(int)($_POST['points_cost']??0));
            $cooldown=max(0,(int)($_POST['cooldown_minutes']??0));
            $active=isset($_POST['is_active'])?1:0;
            $pdo->prepare("UPDATE boost_settings SET points_cost=?,cooldown_minutes=?,is_active=? WHERE id=1")
                ->execute([$cost,$cooldown,$active]);
            $msg='บันทึกค่าดันโพสต์แล้ว';
        }

        if($action==='cancel_premium'){
            $id=(int)($_POST['id']??0);
            $pdo->prepare("UPDATE premium_promotions SET status='cancelled',ends_at=LEAST(ends_at,NOW()) WHERE id=? AND status='active'")
                ->execute([$id]);
            $msg='ยกเลิกพรีเมียมแล้ว';
        }
    }catch(Throwable $e){$error=$e->getMessage();}
}

$plans=$pdo->query("SELECT * FROM premium_plans ORDER BY sort_order,id")->fetchAll();
$boost=boost_settings($pdo);
$campaigns=$pdo->query("SELECT pp.*,l.title,u.username,pl.name plan_name
    FROM premium_promotions pp
    JOIN listings l ON l.id=pp.listing_id
    JOIN users u ON u.id=pp.user_id
    JOIN premium_plans pl ON pl.id=pp.plan_id
    ORDER BY pp.id DESC LIMIT 300")->fetchAll();
$boosts=$pdo->query("SELECT b.*,l.title,u.username
    FROM listing_boosts b
    JOIN listings l ON l.id=b.listing_id
    JOIN users u ON u.id=b.user_id
    ORDER BY b.id DESC LIMIT 200")->fetchAll();

include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>

<div class="cards">
<div class="card">
<h2>ดันโพสต์</h2>
<p class="muted">ดันโพสต์จะบันทึกเวลา <code>boosted_at</code> จริงในฐานข้อมูล และหน้าแรกเรียงจากค่านี้</p>
<form method="post">
<input type="hidden" name="csrf" value="<?=csrf_token()?>">
<input type="hidden" name="action" value="save_boost">
<label>ราคา <input name="points_cost" type="number" min="1" value="<?=$boost['points_cost']?>" style="padding:7px;width:90px"> แต้ม</label><br><br>
<label>เวลารอก่อนดันซ้ำ <input name="cooldown_minutes" type="number" min="0" value="<?=$boost['cooldown_minutes']?>" style="padding:7px;width:90px"> นาที</label><br><br>
<label><input type="checkbox" name="is_active" <?=$boost['is_active']?'checked':''?>> เปิดใช้ดันโพสต์</label>
<button>บันทึก</button>
</form>
</div>

<div class="card">
<h2>เวลาเซิร์ฟเวอร์</h2>
<p>PHP: <strong><?=e(date('Y-m-d H:i:s T'))?></strong></p>
<p>MySQL session: <strong><?=e((string)$pdo->query("SELECT CONCAT(NOW(),' / ',@@session.time_zone)")->fetchColumn())?></strong></p>
<p class="muted">V8 บังคับ session MySQL ให้ตรงกับ timezone ของแอป เพื่อไม่ให้ Premium/Boost แสดงช้าหลายชั่วโมง</p>
</div>
</div>

<div class="card" style="margin-top:14px">
<h2>แพ็กเกจพรีเมียม</h2>
<p class="muted">กำหนดราคาเป็นแต้มและจำนวนวันที่ประกาศอยู่พื้นที่พรีเมียม</p>
<?php foreach($plans as $p):?>
<form method="post" style="border-bottom:1px solid #eee;padding:8px 0">
<input type="hidden" name="csrf" value="<?=csrf_token()?>">
<input type="hidden" name="action" value="save_plan">
<input type="hidden" name="id" value="<?=$p['id']?>">
<input name="name" value="<?=e($p['name'])?>" style="padding:7px;width:min(95%,420px)"><br>
<input name="points_cost" type="number" min="1" value="<?=$p['points_cost']?>" style="padding:7px;width:90px"> แต้ม
<input name="duration_days" type="number" min="1" value="<?=$p['duration_days']?>" style="padding:7px;width:80px"> วัน
<label><input type="checkbox" name="is_active" <?=$p['is_active']?'checked':''?>> เปิดใช้</label>
<button>บันทึก</button>
</form>
<?php endforeach;?>
<form method="post" style="padding-top:10px">
<input type="hidden" name="csrf" value="<?=csrf_token()?>">
<input type="hidden" name="action" value="save_plan">
<input name="name" placeholder="แพ็กเกจใหม่" required style="padding:7px">
<input name="points_cost" type="number" min="1" placeholder="แต้ม" required style="padding:7px;width:90px">
<input name="duration_days" type="number" min="1" placeholder="วัน" required style="padding:7px;width:80px">
<label><input type="checkbox" name="is_active" checked> เปิดใช้</label>
<button>เพิ่ม</button>
</form>
</div>

<h2 style="margin-top:22px">ประวัติดันโพสต์</h2>
<table><tr><th>#</th><th>ประกาศ</th><th>สมาชิก</th><th>แต้ม</th><th>เวลาที่ดัน</th></tr>
<?php foreach($boosts as $b):?><tr>
<td><?=$b['id']?></td>
<td>#<?=$b['listing_id']?> <?=e($b['title'])?></td>
<td><?=e($b['username'])?></td>
<td><?=$b['points_spent']?></td>
<td><?=e($b['boosted_at'])?></td>
</tr><?php endforeach;?></table>

<h2 style="margin-top:22px">แคมเปญพรีเมียม</h2>
<table><tr><th>#</th><th>ประกาศ</th><th>สมาชิก</th><th>แพ็กเกจ</th><th>ช่วงเวลา</th><th>สถานะ</th><th>จัดการ</th></tr>
<?php foreach($campaigns as $c):?><tr>
<td><?=$c['id']?></td>
<td>#<?=$c['listing_id']?> <?=e($c['title'])?></td>
<td><?=e($c['username'])?></td>
<td><?=e($c['plan_name'])?><br><?=$c['points_spent']?> แต้ม</td>
<td><?=e($c['starts_at'])?><br>ถึง <?=e($c['ends_at'])?></td>
<td><?=e($c['status'])?></td>
<td><?php if($c['status']==='active'):?>
<form method="post">
<input type="hidden" name="csrf" value="<?=csrf_token()?>">
<input type="hidden" name="action" value="cancel_premium">
<input type="hidden" name="id" value="<?=$c['id']?>">
<button class="danger">ยกเลิก</button>
</form>
<?php endif;?></td>
</tr><?php endforeach;?></table>
<?php include '_footer.php';?>
