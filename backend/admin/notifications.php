<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v13_schema($pdo);
$pageTitle='แจ้งเตือน';
$msg=''; $error='';

$normalizeTime = static function(string $value, bool $required=false): ?string {
    $value=trim($value);
    if($value==='') {
        if($required) throw new RuntimeException('กรุณากำหนดช่วงเวลาอย่างน้อย 1 ช่วง');
        return null;
    }
    if(!preg_match('/^(?:[01]\d|2[0-3]):[0-5]\d$/',$value)) {
        throw new RuntimeException('รูปแบบเวลาไม่ถูกต้อง');
    }
    return $value.':00';
};

if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    $action=(string)($_POST['action']??'');
    try {
        if($action==='send_now'){
            $title=mb_substr(trim((string)($_POST['title']??'')),0,160);
            $body=mb_substr(trim((string)($_POST['body']??'')),0,1000);
            if(mb_strlen($title)<2||$body==='') throw new RuntimeException('กรุณากรอกหัวข้อและข้อความ');
            $sent=firebase_push_all_active($pdo,[
                'type'=>'admin_notification',
                'title'=>$title,
                'body'=>$body,
            ]);
            $msg='ส่งแจ้งเตือนแล้ว '.$sent.' เครื่อง';
        } elseif($action==='schedule'){
            $title=mb_substr(trim((string)($_POST['title']??'')),0,160);
            $body=mb_substr(trim((string)($_POST['body']??'')),0,1000);
            $when=trim((string)($_POST['scheduled_at']??''));
            if(mb_strlen($title)<2||$body==='') throw new RuntimeException('กรุณากรอกหัวข้อและข้อความ');
            $ts=strtotime($when);
            if(!$ts) throw new RuntimeException('วัน/เวลาไม่ถูกต้อง');
            if($ts < time()-60) throw new RuntimeException('กรุณาเลือกเวลาในอนาคต');
            $st=$pdo->prepare("INSERT INTO scheduled_notifications(title,body,scheduled_at,created_by,source) VALUES(?,?,?,?, 'manual')");
            $st->execute([$title,$body,date('Y-m-d H:i:s',$ts),(int)$_SESSION['admin_id']]);
            $msg='ตั้งเวลาแจ้งเตือนแล้ว';
        } elseif($action==='save_auto_push'){
            $enabled=isset($_POST['auto_enabled']) ? 1 : 0;
            $dailyCount=(int)($_POST['daily_count']??2);
            if($dailyCount<1 || $dailyCount>6) {
                throw new RuntimeException('จำนวนรอบต่อวันต้องอยู่ระหว่าง 1–6 รอบ');
            }

            $w1s=$normalizeTime((string)($_POST['window1_start']??''),true);
            $w1e=$normalizeTime((string)($_POST['window1_end']??''),true);
            $w2s=$normalizeTime((string)($_POST['window2_start']??''));
            $w2e=$normalizeTime((string)($_POST['window2_end']??''));
            $w3s=$normalizeTime((string)($_POST['window3_start']??''));
            $w3e=$normalizeTime((string)($_POST['window3_end']??''));

            foreach([[$w1s,$w1e],[$w2s,$w2e],[$w3s,$w3e]] as $index=>$pair){
                [$start,$end]=$pair;
                if(($start===null) xor ($end===null)) {
                    throw new RuntimeException('ช่วงเวลา '.($index+1).' ต้องใส่ทั้งเวลาเริ่มและเวลาสิ้นสุด');
                }
                if($start!==null && $end!==null && $start >= $end) {
                    throw new RuntimeException('ช่วงเวลา '.($index+1).' ต้องให้เวลาสิ้นสุดมากกว่าเวลาเริ่ม');
                }
            }

            $pdo->beginTransaction();
            try {
                $st=$pdo->prepare("UPDATE member_push_settings SET
                    enabled=?,daily_count=?,
                    window1_start=?,window1_end=?,
                    window2_start=?,window2_end=?,
                    window3_start=?,window3_end=?,
                    last_planned_date=NULL,updated_by=?
                    WHERE id=1");
                $st->execute([
                    $enabled,$dailyCount,$w1s,$w1e,$w2s,$w2e,$w3s,$w3e,
                    (int)$_SESSION['admin_id']
                ]);

                // Remove only future V13 auto reminders. Manual Admin schedules stay intact.
                $pdo->exec("UPDATE scheduled_notifications
                    SET status='cancelled'
                    WHERE source='auto_member_v13' AND status='pending' AND scheduled_at>=NOW()");
                $pdo->commit();
            } catch(Throwable $e) {
                if($pdo->inTransaction()) $pdo->rollBack();
                throw $e;
            }
            $msg='บันทึกช่วงเวลา Push สมาชิกแล้ว ระบบจะสุ่มรอบใหม่ตามค่าที่ตั้ง';
        } elseif($action==='cancel'){
            $id=(int)($_POST['id']??0);
            $pdo->prepare("UPDATE scheduled_notifications SET status='cancelled' WHERE id=? AND status='pending'")->execute([$id]);
            $msg='ยกเลิกรายการแล้ว';
        }
    } catch(Throwable $e){ $error=$e->getMessage(); }
}

$auto=member_push_settings($pdo);
$rows=$pdo->query("SELECT sn.*,u.username creator
    FROM scheduled_notifications sn LEFT JOIN users u ON u.id=sn.created_by
    ORDER BY sn.id DESC LIMIT 200")->fetchAll();

$timeValue = static function($value): string {
    $value=trim((string)$value);
    return $value==='' ? '' : substr($value,0,5);
};

include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>

<div class="cards">
  <div class="card">
    <h2>Push สมาชิกอัตโนมัติ V13</h2>
    <p class="muted">
      ตั้งจำนวนรอบต่อวันและช่วงเวลาที่อนุญาต ระบบ Cron จะสุ่มเวลาในช่วงเหล่านี้แล้วส่งผ่าน Firebase
      โดยไม่ใช้แจ้งเตือนสุ่มในเครื่องแบบ V12 เพื่อลดการเด้งซ้ำ
    </p>
    <form method="post">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="action" value="save_auto_push">

      <label style="display:flex;gap:8px;align-items:center;margin-bottom:10px">
        <input type="checkbox" name="auto_enabled" value="1" <?=((int)$auto['enabled']===1)?'checked':''?>>
        เปิด Push สมาชิกอัตโนมัติ
      </label>

      <label>จำนวนรอบต่อวัน</label>
      <input type="number" name="daily_count" min="1" max="6" required
             value="<?=e((string)$auto['daily_count'])?>"
             style="width:100%;box-sizing:border-box;padding:9px;margin:6px 0 10px">

      <?php for($i=1;$i<=3;$i++):
        $start=$timeValue($auto['window'.$i.'_start']??'');
        $end=$timeValue($auto['window'.$i.'_end']??'');
      ?>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:10px">
          <label>ช่วง <?=$i?> เริ่ม
            <input type="time" name="window<?=$i?>_start" value="<?=e($start)?>"
                   <?=$i===1?'required':''?>
                   style="width:100%;box-sizing:border-box;padding:9px;margin-top:6px">
          </label>
          <label>ช่วง <?=$i?> สิ้นสุด
            <input type="time" name="window<?=$i?>_end" value="<?=e($end)?>"
                   <?=$i===1?'required':''?>
                   style="width:100%;box-sizing:border-box;padding:9px;margin-top:6px">
          </label>
        </div>
      <?php endfor;?>

      <button onclick="return confirm('บันทึกการตั้งค่า Push สมาชิกอัตโนมัติ?')">บันทึกการตั้งค่า</button>
    </form>
    <p class="muted" style="margin-top:10px">
      ตัวอย่าง: 2 รอบ/วัน, 09:00–12:00 และ 17:00–21:00 ระบบจะสุ่มเวลา 2 จุดจากช่วงที่กำหนด
      (Cron ควรรันทุก 1 นาที)
    </p>
  </div>

  <div class="card">
    <h2>ส่งแจ้งเตือนทันที</h2>
    <p class="muted">ส่งผ่าน Firebase ไปยังเครื่องที่ลงทะเบียน Push ไว้</p>
    <form method="post">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="action" value="send_now">
      <label>หัวข้อ</label><input name="title" maxlength="160" required style="width:100%;box-sizing:border-box;padding:9px;margin:6px 0 10px">
      <label>ข้อความ</label><textarea name="body" maxlength="1000" rows="5" required style="width:100%;box-sizing:border-box;padding:9px;margin:6px 0 10px"></textarea>
      <button onclick="return confirm('ส่งแจ้งเตือนตอนนี้?')">ส่งตอนนี้</button>
    </form>
  </div>

  <div class="card">
    <h2>ตั้งเวลาแจ้งเตือนเอง</h2>
    <p class="muted">ใช้สำหรับข้อความที่แอดมินกำหนดวัน/เวลาเอง ระบบอัตโนมัติด้านบนทำงานแยกจากส่วนนี้</p>
    <form method="post">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="action" value="schedule">
      <label>หัวข้อ</label><input name="title" maxlength="160" required style="width:100%;box-sizing:border-box;padding:9px;margin:6px 0 10px">
      <label>ข้อความ</label><textarea name="body" maxlength="1000" rows="4" required style="width:100%;box-sizing:border-box;padding:9px;margin:6px 0 10px"></textarea>
      <label>วันและเวลา</label><input type="datetime-local" name="scheduled_at" required style="width:100%;box-sizing:border-box;padding:9px;margin:6px 0 10px">
      <button>บันทึกเวลา</button>
    </form>
    <p class="muted"><strong>เซิร์ฟเวอร์:</strong> ตั้ง Cron ให้รัน <code>php backend/cron/notifications.php</code> ทุก 1 นาที</p>
  </div>
</div>

<h2 style="margin-top:22px">รายการที่ตั้งไว้</h2>
<table>
<tr><th>#</th><th>ข้อความ</th><th>เวลา</th><th>ประเภท</th><th>สถานะ</th><th>ส่งสำเร็จ</th><th>จัดการ</th></tr>
<?php foreach($rows as $r):?>
<tr>
<td><?=$r['id']?></td>
<td><strong><?=e($r['title'])?></strong><br><span class="muted"><?=nl2br(e($r['body']))?></span></td>
<td><?=e($r['scheduled_at'])?><br><span class="muted">โดย <?=e($r['creator']??($r['source']==='auto_member_v13'?'ระบบอัตโนมัติ':'-'))?></span></td>
<td><?=($r['source']==='auto_member_v13')?'Push สมาชิกอัตโนมัติ':'แอดมินตั้งเอง'?></td>
<td><?=e($r['status'])?><?php if($r['error_message']):?><br><span style="color:#a82620"><?=e($r['error_message'])?></span><?php endif;?></td>
<td><?=e((string)$r['sent_count'])?></td>
<td><?php if($r['status']==='pending'):?>
<form method="post"><input type="hidden" name="csrf" value="<?=csrf_token()?>"><input type="hidden" name="id" value="<?=$r['id']?>"><button class="danger" name="action" value="cancel">ยกเลิก</button></form>
<?php endif;?></td>
</tr>
<?php endforeach;?>
<?php if(!$rows):?><tr><td colspan="7" class="muted">ยังไม่มีรายการ</td></tr><?php endif;?>
</table>
<?php include '_footer.php';?>
