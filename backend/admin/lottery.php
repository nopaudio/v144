<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
$pageTitle='ร่วมสนุกลุ้นพระ';
$msg=''; $error='';

try {
    ensure_v14_schema($pdo);
} catch(Throwable $e) {
    $error='เตรียมระบบ V14 ไม่สำเร็จ กรุณาเข้า Admin > อัปเดตระบบ ก่อน: '.$e->getMessage();
}

if($_SERVER['REQUEST_METHOD']==='POST' && $error===''){
    verify_csrf();
    $action=(string)($_POST['action']??'');
    try {
        if($action==='create_round'){
            $title=mb_substr(trim((string)($_POST['title']??'')),0,160);
            $prizeName=mb_substr(trim((string)($_POST['prize_name']??'')),0,160);
            $description=mb_substr(trim((string)($_POST['prize_description']??'')),0,2000);
            $drawDate=trim((string)($_POST['draw_date']??''));
            $pointsCost=(int)($_POST['points_cost']??0);
            if(mb_strlen($title)<3) throw new RuntimeException('กรุณาระบุชื่อรอบอย่างน้อย 3 ตัวอักษร');
            if(mb_strlen($prizeName)<2) throw new RuntimeException('กรุณาระบุชื่อพระ/รางวัล');
            if(!preg_match('/^\d{4}-\d{2}-\d{2}$/',$drawDate) || strtotime($drawDate)===false) {
                throw new RuntimeException('วันที่งวดไม่ถูกต้อง');
            }
            if($pointsCost<1 || $pointsCost>1000000) throw new RuntimeException('แต้มต่อเลขต้องอยู่ระหว่าง 1–1,000,000');

            $imagePath=null;
            if(!empty($_FILES['prize_image']) && ($_FILES['prize_image']['error']??UPLOAD_ERR_NO_FILE)!==UPLOAD_ERR_NO_FILE){
                $imagePath=save_lottery_prize_image($config,$_FILES['prize_image']);
            }
            $st=$pdo->prepare("INSERT INTO lottery_rounds(
                    title,prize_name,prize_description,prize_image_path,draw_date,points_cost,status,created_by
                ) VALUES(?,?,?,?,?,?,'draft',?)");
            $st->execute([$title,$prizeName,$description?:null,$imagePath,$drawDate,$pointsCost,(int)$_SESSION['admin_id']]);
            $msg='สร้างรอบร่วมสนุกแล้ว กรุณาตรวจข้อมูลก่อนกดเปิดรับเลข';
        } elseif($action==='update_round'){
            $id=(int)($_POST['id']??0);
            $st=$pdo->prepare("SELECT * FROM lottery_rounds WHERE id=? LIMIT 1");
            $st->execute([$id]); $round=$st->fetch();
            if(!$round) throw new RuntimeException('ไม่พบรอบ');
            if($round['status']!=='draft') throw new RuntimeException('แก้รายละเอียดได้เฉพาะรอบฉบับร่าง เพื่อไม่เปลี่ยนเงื่อนไขหลังสมาชิกซื้อเลข');

            $title=mb_substr(trim((string)($_POST['title']??'')),0,160);
            $prizeName=mb_substr(trim((string)($_POST['prize_name']??'')),0,160);
            $description=mb_substr(trim((string)($_POST['prize_description']??'')),0,2000);
            $drawDate=trim((string)($_POST['draw_date']??''));
            $pointsCost=(int)($_POST['points_cost']??0);
            if(mb_strlen($title)<3 || mb_strlen($prizeName)<2) throw new RuntimeException('กรุณากรอกชื่อรอบและชื่อรางวัลให้ครบ');
            if(!preg_match('/^\d{4}-\d{2}-\d{2}$/',$drawDate) || strtotime($drawDate)===false) throw new RuntimeException('วันที่งวดไม่ถูกต้อง');
            if($pointsCost<1 || $pointsCost>1000000) throw new RuntimeException('แต้มต่อเลขไม่ถูกต้อง');

            $imagePath=$round['prize_image_path'];
            if(!empty($_FILES['prize_image']) && ($_FILES['prize_image']['error']??UPLOAD_ERR_NO_FILE)!==UPLOAD_ERR_NO_FILE){
                $imagePath=save_lottery_prize_image($config,$_FILES['prize_image']);
            }
            $pdo->prepare("UPDATE lottery_rounds
                SET title=?,prize_name=?,prize_description=?,prize_image_path=?,draw_date=?,points_cost=?
                WHERE id=?")->execute([$title,$prizeName,$description?:null,$imagePath,$drawDate,$pointsCost,$id]);
            $msg='บันทึกรายละเอียดรอบแล้ว';
        } elseif($action==='set_status'){
            $id=(int)($_POST['id']??0);
            $status=(string)($_POST['status']??'');
            if(!in_array($status,['open','closed'],true)) throw new RuntimeException('สถานะไม่ถูกต้อง');

            $pdo->beginTransaction();
            $st=$pdo->prepare("SELECT id,status FROM lottery_rounds WHERE id=? LIMIT 1 FOR UPDATE");
            $st->execute([$id]); $round=$st->fetch();
            if(!$round) throw new RuntimeException('ไม่พบรอบ');
            if($round['status']==='announced') throw new RuntimeException('รอบที่ประกาศผลแล้วไม่สามารถเปิด/ปิดรับเลขใหม่ได้');
            if($status==='open'){
                // V14 exposes one active round at a time; older open rounds are safely closed.
                $pdo->prepare("UPDATE lottery_rounds SET status='closed' WHERE status='open' AND id<>?")->execute([$id]);
            }
            $pdo->prepare("UPDATE lottery_rounds SET status=? WHERE id=?")->execute([$status,$id]);
            $pdo->commit();
            $msg=$status==='open'?'เปิดรับเลขรอบนี้แล้ว':'ปิดรับเลขรอบนี้แล้ว';
        } elseif($action==='announce'){
            $id=(int)($_POST['id']??0);
            $winningRaw=trim((string)($_POST['winning_number']??''));
            if(!preg_match('/^\d{2}$/',$winningRaw)) throw new RuntimeException('กรุณากรอกเลขผลรางวัล 2 ตัว เช่น 07');
            $winning=(int)$winningRaw;

            $pdo->beginTransaction();
            $st=$pdo->prepare("SELECT * FROM lottery_rounds WHERE id=? LIMIT 1 FOR UPDATE");
            $st->execute([$id]); $round=$st->fetch();
            if(!$round) throw new RuntimeException('ไม่พบรอบ');
            if($round['status']==='draft') throw new RuntimeException('รอบยังเป็นฉบับร่าง กรุณาเปิดรับเลขก่อนประกาศผล');
            if($round['status']==='announced') throw new RuntimeException('รอบนี้ประกาศผลแล้ว ไม่สามารถเปลี่ยนเลขรางวัลย้อนหลังได้');

            $winnerSt=$pdo->prepare("SELECT e.id,e.user_id,COALESCE(NULLIF(u.display_name,''),u.username) display_name
                FROM lottery_entries e JOIN users u ON u.id=e.user_id
                WHERE e.round_id=? AND e.number=? LIMIT 1");
            $winnerSt->execute([$id,$winning]); $winner=$winnerSt->fetch();

            $pdo->prepare("UPDATE lottery_rounds
                SET winning_number=?,status='announced',announced_at=NOW() WHERE id=?")
                ->execute([$winning,$id]);
            $pdo->commit();

            $winnerText=$winner ? 'ผู้ชนะ: '.(string)$winner['display_name'] : 'เลขนี้ไม่มีสมาชิกซื้อ';
            $msg='ประกาศผลเลข '.$winningRaw.' แล้ว — '.$winnerText;

            try {
                // Reuse the existing member-wide notification type so V13 clients
                // can still display the result without changing notification routing.
                firebase_push_all_active($pdo,[
                    'type'=>'admin_notification',
                    'title'=>'ประกาศผลร่วมสนุกลุ้นพระ',
                    'body'=>(string)$round['title'].' เลขรางวัล '.$winningRaw.' — '.$winnerText,
                    'lottery_round_id'=>(string)$id,
                ]);
            } catch(Throwable $pushError) {
                $msg.=' (บันทึกผลสำเร็จ แต่ส่ง Push ไม่สำเร็จ)';
                error_log('V14 lottery result push failed: '.$pushError->getMessage());
            }
        }
    } catch(Throwable $e){
        if($pdo->inTransaction()) $pdo->rollBack();
        $error=$e->getMessage();
    }
}

$rounds=[];
$entriesByRound=[];
if($error==='' || lottery_schema_ready($pdo)){
    try {
        $rounds=$pdo->query("SELECT r.*,
                (SELECT COUNT(*) FROM lottery_entries e WHERE e.round_id=r.id) sold_count
            FROM lottery_rounds r ORDER BY r.id DESC LIMIT 30")->fetchAll();
        if($rounds){
            $ids=array_map(static fn(array $r): int => (int)$r['id'],$rounds);
            $placeholders=implode(',',array_fill(0,count($ids),'?'));
            $st=$pdo->prepare("SELECT e.round_id,e.number,e.points_spent,e.created_at,
                    COALESCE(NULLIF(u.display_name,''),u.username) display_name
                FROM lottery_entries e JOIN users u ON u.id=e.user_id
                WHERE e.round_id IN ($placeholders)
                ORDER BY e.round_id DESC,e.number");
            $st->execute($ids);
            foreach($st->fetchAll() as $row){
                $entriesByRound[(int)$row['round_id']][]=$row;
            }
        }
    } catch(Throwable $e){
        if($error==='') $error='โหลดข้อมูลรอบไม่ได้: '.$e->getMessage();
    }
}

$statusLabel=['draft'=>'ฉบับร่าง','open'=>'เปิดรับเลข','closed'=>'ปิดรับเลข','announced'=>'ประกาศผลแล้ว'];
include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>

<div class="card" style="max-width:860px">
  <h2>สร้างรอบร่วมสนุก</h2>
  <p class="muted">สมาชิกใช้แต้มเดิมซื้อเลข 00–99 เลขหนึ่งเลขมีเจ้าของได้เพียงคนเดียวต่อรอบ ระบบหักแต้มด้วย transaction และล็อกเลขป้องกันการซื้อชนกัน</p>
  <form method="post" enctype="multipart/form-data">
    <input type="hidden" name="csrf" value="<?=csrf_token()?>">
    <input type="hidden" name="action" value="create_round">
    <label>ชื่อรอบ</label>
    <input name="title" required maxlength="160" placeholder="เช่น ลุ้นพระสมเด็จ งวด 16 ส.ค. 2569" style="width:100%;padding:9px;margin:5px 0 10px">
    <label>ชื่อพระ / รางวัล</label>
    <input name="prize_name" required maxlength="160" style="width:100%;padding:9px;margin:5px 0 10px">
    <label>รายละเอียดรางวัล</label>
    <textarea name="prize_description" maxlength="2000" rows="4" style="width:100%;padding:9px;margin:5px 0 10px"></textarea>
    <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px">
      <label>วันที่งวด
        <input type="date" name="draw_date" required style="width:100%;padding:9px;margin-top:5px">
      </label>
      <label>แต้มต่อ 1 เลข
        <input type="number" name="points_cost" required min="1" max="1000000" value="10" style="width:100%;padding:9px;margin-top:5px">
      </label>
    </div>
    <label style="display:block;margin-top:10px">รูปรางวัล (JPG/PNG/WEBP)
      <input type="file" name="prize_image" accept="image/jpeg,image/png,image/webp" style="width:100%;padding:9px;margin-top:5px">
    </label>
    <button type="submit" style="margin-top:12px">สร้างเป็นฉบับร่าง</button>
  </form>
</div>

<?php foreach($rounds as $round):
  $rid=(int)$round['id'];
  $entries=$entriesByRound[$rid]??[];
  $winning=$round['winning_number']===null?null:str_pad((string)(int)$round['winning_number'],2,'0',STR_PAD_LEFT);
  $winnerName='';
  if($winning!==null){
      foreach($entries as $entry){
          if((int)$entry['number']===(int)$round['winning_number']) { $winnerName=(string)$entry['display_name']; break; }
      }
  }
?>
<div class="card" style="max-width:860px;margin-top:16px">
  <div style="display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap">
    <div>
      <h2 style="margin:0 0 4px">#<?=$rid?> <?=e((string)$round['title'])?></h2>
      <span class="status-badge status-<?=e((string)$round['status'])?>"><?=e($statusLabel[$round['status']]??(string)$round['status'])?></span>
      <span class="muted"> • งวด <?=e((string)$round['draw_date'])?> • <?=number_format((int)$round['points_cost'])?> แต้ม/เลข • ขายแล้ว <?=number_format((int)$round['sold_count'])?>/100</span>
    </div>
    <?php if(!empty($round['prize_image_path'])):?>
      <img class="evidence-thumb" src="../<?=e((string)$round['prize_image_path'])?>" alt="รูปรางวัล">
    <?php endif;?>
  </div>

  <p><strong>รางวัล:</strong> <?=e((string)$round['prize_name'])?></p>
  <?php if(!empty($round['prize_description'])):?><p><?=nl2br(e((string)$round['prize_description']))?></p><?php endif;?>

  <?php if($round['status']==='draft'):?>
  <details style="margin:12px 0">
    <summary><strong>แก้รายละเอียดฉบับร่าง</strong></summary>
    <form method="post" enctype="multipart/form-data" style="margin-top:10px">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="action" value="update_round">
      <input type="hidden" name="id" value="<?=$rid?>">
      <label>ชื่อรอบ</label><input name="title" required maxlength="160" value="<?=e((string)$round['title'])?>" style="width:100%;padding:8px;margin:4px 0 8px">
      <label>ชื่อรางวัล</label><input name="prize_name" required maxlength="160" value="<?=e((string)$round['prize_name'])?>" style="width:100%;padding:8px;margin:4px 0 8px">
      <label>รายละเอียด</label><textarea name="prize_description" maxlength="2000" rows="3" style="width:100%;padding:8px;margin:4px 0 8px"><?=e((string)$round['prize_description'])?></textarea>
      <label>วันที่งวด</label><input type="date" name="draw_date" required value="<?=e((string)$round['draw_date'])?>" style="padding:8px;margin:4px 10px 8px 0">
      <label>แต้ม/เลข</label><input type="number" name="points_cost" required min="1" max="1000000" value="<?=e((string)$round['points_cost'])?>" style="padding:8px;margin:4px 0 8px">
      <label style="display:block">เปลี่ยนรูปรางวัล (ไม่เลือก = ใช้รูปเดิม)<input type="file" name="prize_image" accept="image/jpeg,image/png,image/webp" style="display:block;margin:5px 0 10px"></label>
      <button type="submit">บันทึกฉบับร่าง</button>
    </form>
  </details>
  <?php endif;?>

  <?php if($round['status']!=='announced'):?>
    <form method="post" style="display:inline">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="action" value="set_status">
      <input type="hidden" name="id" value="<?=$rid?>">
      <input type="hidden" name="status" value="<?=$round['status']==='open'?'closed':'open'?>">
      <button class="<?=$round['status']==='open'?'warning':'success'?>" type="submit"><?=$round['status']==='open'?'ปิดรับเลข':'เปิดรับเลขรอบนี้'?></button>
    </form>
    <?php if($round['status']!=='draft'):?>
    <form method="post" style="display:inline-flex;align-items:center;gap:6px;flex-wrap:wrap">
      <input type="hidden" name="csrf" value="<?=csrf_token()?>">
      <input type="hidden" name="action" value="announce">
      <input type="hidden" name="id" value="<?=$rid?>">
      <input name="winning_number" required inputmode="numeric" pattern="\d{2}" maxlength="2" placeholder="00" style="width:74px;padding:8px;text-align:center">
      <button class="approve" type="submit" onclick="return confirm('ยืนยันประกาศผลรางวัลรอบนี้?')">ประกาศผู้ชนะ</button>
    </form>
    <?php endif;?>
  <?php else:?>
    <p class="ok"><strong>เลขรางวัล <?=$winning?></strong> — <?=$winnerName!==''?'ผู้ชนะ '.e($winnerName):'ไม่มีสมาชิกซื้อเลขนี้'?></p>
  <?php endif;?>

  <details style="margin-top:14px" <?=$round['status']==='open'?'open':''?>>
    <summary><strong>เลขที่มีเจ้าของแล้ว <?=count($entries)?> เลข</strong></summary>
    <?php if(!$entries):?>
      <p class="muted">ยังไม่มีสมาชิกซื้อเลข</p>
    <?php else:?>
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(145px,1fr));gap:7px;margin-top:10px">
        <?php foreach($entries as $entry):?>
          <div style="border:1px solid #e8ded1;border-radius:9px;padding:8px;background:#faf7f2">
            <strong><?=str_pad((string)(int)$entry['number'],2,'0',STR_PAD_LEFT)?></strong>
            <span class="muted"> — <?=e((string)$entry['display_name'])?></span>
          </div>
        <?php endforeach;?>
      </div>
    <?php endif;?>
  </details>
</div>
<?php endforeach;?>

<?php if(!$rounds && $error===''):?>
  <div class="card" style="max-width:860px;margin-top:16px"><p class="muted">ยังไม่มีรอบร่วมสนุก</p></div>
<?php endif;?>
<?php include '_footer.php';?>
