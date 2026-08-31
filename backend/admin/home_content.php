<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
$pageTitle='ตั้งค่าหน้าแรก / Banner';
$msg=''; $error='';

try {
    ensure_v9_schema($pdo);
} catch (Throwable $e) {
    $error='อัปเดตโครงสร้างหน้าแรกไม่ได้ กรุณารัน database/migration_v9.sql ใน phpMyAdmin';
}

if ($_SERVER['REQUEST_METHOD']==='POST' && !$error) {
    verify_csrf();
    $action=(string)($_POST['action']??'save_home');

    try {
        if($action==='save_home'){
            $brand=trim((string)($_POST['brand_title']??''));
            $headline=trim((string)($_POST['headline']??''));
            $subheadline=trim((string)($_POST['subheadline']??''));
            $trustTitle=trim((string)($_POST['trust_title']??''));
            $trust=trim((string)($_POST['trust_text']??''));
            $active=isset($_POST['is_active'])?1:0;

            if (mb_strlen($brand)<2 || mb_strlen($brand)>80) throw new RuntimeException('ชื่อแบรนด์ต้องมี 2–80 ตัวอักษร');
            if (mb_strlen($headline)<3 || mb_strlen($headline)>160) throw new RuntimeException('หัวข้อหลักต้องมี 3–160 ตัวอักษร');
            if (mb_strlen($subheadline)<3 || mb_strlen($subheadline)>255) throw new RuntimeException('คำโปรยต้องมี 3–255 ตัวอักษร');
            if (mb_strlen($trustTitle)<3 || mb_strlen($trustTitle)>160) throw new RuntimeException('หัวข้อความน่าเชื่อถือต้องมี 3–160 ตัวอักษร');
            if (mb_strlen($trust)<3 || mb_strlen($trust)>255) throw new RuntimeException('ข้อความความน่าเชื่อถือต้องมี 3–255 ตัวอักษร');

            $st=$pdo->prepare('UPDATE home_content SET brand_title=?,headline=?,subheadline=?,trust_title=?,trust_text=?,is_active=? WHERE id=1');
            $st->execute([$brand,$headline,$subheadline,$trustTitle,$trust,$active]);
            $msg='บันทึกข้อความหน้าแรกแล้ว แอปจะเห็นค่าใหม่เมื่อรีเฟรช';
        } elseif($action==='add_banner'){
            $path=save_banner_image($config,$_FILES['image']??[]);
            try {
                $sort=(int)($_POST['sort_order']??0);
                $active=isset($_POST['banner_active'])?1:0;
                $st=$pdo->prepare('INSERT INTO home_banners(image_path,is_active,sort_order) VALUES(?,?,?)');
                $st->execute([$path,$active,$sort]);
            } catch(Throwable $e) {
                @unlink(dirname(__DIR__).'/'.$path);
                throw $e;
            }
            $msg='เพิ่ม Banner แล้ว';
        } elseif($action==='update_banner'){
            $id=(int)($_POST['id']??0);
            $st=$pdo->prepare('SELECT image_path FROM home_banners WHERE id=? LIMIT 1');
            $st->execute([$id]); $oldPath=$st->fetchColumn();
            if(!$oldPath) throw new RuntimeException('ไม่พบ Banner');

            $sort=(int)($_POST['sort_order']??0);
            $active=isset($_POST['banner_active'])?1:0;
            $newPath=null;
            if(!empty($_FILES['image']) && ($_FILES['image']['error']??UPLOAD_ERR_NO_FILE)!==UPLOAD_ERR_NO_FILE){
                $newPath=save_banner_image($config,$_FILES['image']);
            }
            try {
                if($newPath){
                    $pdo->prepare('UPDATE home_banners SET image_path=?,is_active=?,sort_order=? WHERE id=?')
                        ->execute([$newPath,$active,$sort,$id]);
                } else {
                    $pdo->prepare('UPDATE home_banners SET is_active=?,sort_order=? WHERE id=?')
                        ->execute([$active,$sort,$id]);
                }
            } catch(Throwable $e) {
                if($newPath) @unlink(dirname(__DIR__).'/'.$newPath);
                throw $e;
            }
            if($newPath && $oldPath!==$newPath) @unlink(dirname(__DIR__).'/'.$oldPath);
            $msg='อัปเดต Banner แล้ว';
        } elseif($action==='delete_banner'){
            $id=(int)($_POST['id']??0);
            $st=$pdo->prepare('SELECT image_path FROM home_banners WHERE id=? LIMIT 1');
            $st->execute([$id]); $path=$st->fetchColumn();
            if(!$path) throw new RuntimeException('ไม่พบ Banner');
            $pdo->prepare('DELETE FROM home_banners WHERE id=?')->execute([$id]);
            @unlink(dirname(__DIR__).'/'.$path);
            $msg='ลบ Banner แล้ว';
        }
    } catch(Throwable $e) {
        $error=$e->getMessage();
    }
}

$row=fetch_home_content($pdo);
$banners=[];
if(!$error){
    $banners=$pdo->query('SELECT id,image_path,is_active,sort_order,created_at,updated_at FROM home_banners ORDER BY sort_order,id')->fetchAll();
}
include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>

<div class="card" style="max-width:760px">
  <h2>ข้อความหน้า Home</h2>
  <p class="muted">ใช้ config เดิมของ V8 และเพิ่มเฉพาะหัวข้อ Trust ที่เดิม hardcode อยู่ใน Android</p>
  <form method="post">
    <input type="hidden" name="csrf" value="<?=csrf_token()?>">
    <input type="hidden" name="action" value="save_home">
    <label>ชื่อแบรนด์</label>
    <input name="brand_title" required maxlength="80" value="<?=e($row['brand_title'])?>" style="width:100%;box-sizing:border-box;padding:10px;margin:6px 0 14px">
    <label>หัวข้อหลัก</label>
    <input name="headline" required maxlength="160" value="<?=e($row['headline'])?>" style="width:100%;box-sizing:border-box;padding:10px;margin:6px 0 14px">
    <label>คำโปรย</label>
    <input name="subheadline" required maxlength="255" value="<?=e($row['subheadline'])?>" style="width:100%;box-sizing:border-box;padding:10px;margin:6px 0 14px">
    <label>หัวข้อความน่าเชื่อถือ (เช่น “ซื้อขายมั่นใจ ปลอดภัย”)</label>
    <input name="trust_title" required maxlength="160" value="<?=e($row['trust_title'])?>" style="width:100%;box-sizing:border-box;padding:10px;margin:6px 0 14px">
    <label>ข้อความประกอบ</label>
    <textarea name="trust_text" required maxlength="255" rows="3" style="width:100%;box-sizing:border-box;padding:10px;margin:6px 0 14px"><?=e($row['trust_text'])?></textarea>
    <label style="display:flex;gap:8px;align-items:center;margin-bottom:16px">
      <input type="checkbox" name="is_active" <?=$row['enabled']?'checked':''?>> แสดง Hero ในแอป
    </label>
    <button type="submit">บันทึกข้อความหน้าแรก</button>
  </form>
</div>

<div class="card" style="max-width:960px;margin-top:18px">
  <h2>Banner หน้า Home</h2>
  <p class="muted">รองรับหลายรูป เรียงจากเลขน้อยไปมาก ปิด Banner ได้โดยไม่ต้องลบรูป</p>

  <form method="post" enctype="multipart/form-data" style="padding:12px;background:#faf7f2;border-radius:10px;margin-bottom:18px">
    <input type="hidden" name="csrf" value="<?=csrf_token()?>">
    <input type="hidden" name="action" value="add_banner">
    <label>รูป Banner (JPG/PNG/WEBP)</label><br>
    <input type="file" name="image" accept="image/jpeg,image/png,image/webp" required style="margin:8px 0"><br>
    <label>ลำดับ <input type="number" name="sort_order" value="10" style="width:90px;padding:7px"></label>
    <label style="margin-left:12px"><input type="checkbox" name="banner_active" checked> เปิดแสดง</label>
    <button type="submit">เพิ่ม Banner</button>
  </form>

  <?php if(!$banners):?>
    <p class="muted">ยังไม่มี Banner จาก Server — แอปจะไม่เว้นพื้นที่ว่างส่วนนี้</p>
  <?php else:?>
    <div style="display:grid;gap:12px">
    <?php foreach($banners as $b):?>
      <form method="post" enctype="multipart/form-data" style="display:grid;grid-template-columns:minmax(180px,300px) 1fr;gap:14px;padding:12px;border:1px solid #eee;border-radius:12px">
        <input type="hidden" name="csrf" value="<?=csrf_token()?>">
        <input type="hidden" name="id" value="<?=$b['id']?>">
        <div>
          <img src="<?=e(image_url($config,(string)$b['image_path']))?>" alt="Banner" style="width:100%;max-height:150px;object-fit:cover;border-radius:10px">
        </div>
        <div>
          <div><strong>Banner #<?=$b['id']?></strong></div>
          <label>ลำดับ <input type="number" name="sort_order" value="<?=e((string)$b['sort_order'])?>" style="width:90px;padding:7px;margin:7px"></label>
          <label><input type="checkbox" name="banner_active" <?=$b['is_active']?'checked':''?>> เปิดแสดง</label><br>
          <label>เปลี่ยนรูป (ไม่เลือก = ใช้รูปเดิม)</label><br>
          <input type="file" name="image" accept="image/jpeg,image/png,image/webp" style="margin:7px 0"><br>
          <button name="action" value="update_banner">บันทึก Banner</button>
          <button class="danger" name="action" value="delete_banner" onclick="return confirm('ลบ Banner นี้?')">ลบ</button>
        </div>
      </form>
    <?php endforeach;?>
    </div>
  <?php endif;?>
</div>
<?php include '_footer.php';?>
