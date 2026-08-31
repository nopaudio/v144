<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
$pageTitle='อัปเดตระบบ';
$msg=''; $error='';

$firebasePath = firebase_service_account_path();
$firebaseFileOk = is_file($firebasePath) && is_readable($firebasePath);
$opensslOk = function_exists('openssl_sign');
$httpOk = function_exists('curl_init') || (bool)ini_get('allow_url_fopen');
$pushTokenCount = 0;

try {
    $tableCheck = $pdo->query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='push_tokens'")->fetchColumn();
    if ((int)$tableCheck > 0) {
        $pushTokenCount = (int)$pdo->query('SELECT COUNT(*) FROM push_tokens')->fetchColumn();
    }
} catch (Throwable $e) {
    $pushTokenCount = 0;
}

$collectSchemaStatus = static function(PDO $db): array {
    $columnExists = static function(PDO $pdo, string $table, string $column): bool {
        $st = $pdo->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?");
        $st->execute([$table,$column]);
        return (int)$st->fetchColumn() > 0;
    };
    $tableExists = static function(PDO $pdo, string $table): bool {
        $st = $pdo->prepare("SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?");
        $st->execute([$table]);
        return (int)$st->fetchColumn() > 0;
    };
    $columnTypeHas = static function(PDO $pdo, string $table, string $column, string $needle): bool {
        $st = $pdo->prepare("SELECT COLUMN_TYPE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=? LIMIT 1");
        $st->execute([$table,$column]);
        return str_contains((string)$st->fetchColumn(),$needle);
    };

    return [
        'ชื่อสมาชิก/ข้อมูล Admin V11' => $columnExists($db,'users','display_name')
            && $columnExists($db,'users','admin_stars'),
        'ตัวเลือกการขายของประกาศ V11' => $columnExists($db,'listings','allow_buy_now')
            && $columnExists($db,'listings','allow_cod'),
        'ระบบคำสั่งซื้อ/สลิป V11' => $columnExists($db,'orders','payment_method')
            && $columnExists($db,'orders','payment_slip_path'),
        'คำขอเปลี่ยนชื่อ V11' => $tableExists($db,'display_name_change_requests'),
        'แจ้งเตือน Admin V10/V11' => $tableExists($db,'admin_notifications')
            && $tableExists($db,'admin_notification_reads'),
        'ที่อยู่สมาชิก V13' => $columnExists($db,'users','province')
            && $columnExists($db,'users','amphoe')
            && $columnExists($db,'users','tambon'),
        'ตั้งค่า Push สมาชิก V13' => $tableExists($db,'member_push_settings')
            && $columnExists($db,'scheduled_notifications','source'),
        'ร่วมสนุกลุ้นพระ V14' => $tableExists($db,'lottery_rounds')
            && $tableExists($db,'lottery_entries')
            && $columnTypeHas($db,'point_transactions','type','lottery_purchase'),
    ];
};

if($_SERVER['REQUEST_METHOD']==='POST'){
    verify_csrf();
    try {
        // V14 preserves all V13 migrations/data and adds only the new
        // idempotent lottery tables/point transaction value.
        ensure_home_content_schema($pdo);
        ensure_v14_schema($pdo);

        $pushWarning='';
        if (!$firebaseFileOk || !$opensslOk || !$httpOk) {
            $pushWarning=' (อัปเดตฐานข้อมูลเดิมแล้ว แต่ระบบ Push ยังไม่พร้อม กรุณาตรวจ Firebase/OpenSSL/HTTPS)';
        } else {
            // Validate JSON/private key without sending any notification yet.
            firebase_credentials();
        }

        $msg='ตรวจและอัปเดต V14 สำเร็จ: ระบบเดิม V13 ยังอยู่ครบ และเพิ่มระบบร่วมสนุกลุ้นพระแบบปลอดภัยแล้ว'.$pushWarning;
    } catch(Throwable $e) {
        $error='อัปเดต V14 ไม่สำเร็จ: '.$e->getMessage();
    }
}

$schemaStatus = [];
try {
    $schemaStatus = $collectSchemaStatus($pdo);
} catch (Throwable $e) {
    $schemaStatus = [];
    if ($error === '') {
        $error = 'ตรวจสถานะฐานข้อมูลไม่สำเร็จ: '.$e->getMessage();
    }
}
$schemaReady = !empty($schemaStatus) && !in_array(false,$schemaStatus,true);

include '_header.php';
?>
<?php if($msg):?><p class="ok"><?=e($msg)?></p><?php endif;?>
<?php if($error):?><p class="bad"><?=e($error)?></p><?php endif;?>

<div class="card" style="max-width:760px">
  <h2>ตรวจและอัปเดตระบบ V14</h2>
  <p>
    V14 รักษา V13 ทั้งหมดไว้ และเพิ่มระบบร่วมสนุกลุ้นพระด้วยแต้ม/เลข 2 ตัว
    โดยไม่แก้ <code>backend/database/migration_v13.sql</code> เดิม และใช้
    <code>backend/database/migration_v14.sql</code> สำหรับอัปเกรด V13 → V14
    แบบ additive/idempotent โดยไม่ DROP/TRUNCATE/reset ข้อมูลเดิม
  </p>

  <p>
    สถานะ Database:
    <strong style="color:<?=$schemaReady?'#16803b':'#b42318'?>">
      <?=$schemaReady?'พร้อมสำหรับ V14':'ควรกดตรวจและอัปเดต V14'?>
    </strong>
  </p>

  <?php if($schemaStatus):?>
    <div style="display:grid;gap:6px;margin:12px 0 16px">
      <?php foreach($schemaStatus as $label=>$ok):?>
        <div>
          <strong style="color:<?=$ok?'#16803b':'#b42318'?>"><?=$ok?'✓':'✗'?></strong>
          <?=e($label)?>
        </div>
      <?php endforeach;?>
    </div>
  <?php endif;?>

  <p>
    Firebase Service Account:
    <strong style="color:<?=$firebaseFileOk?'#16803b':'#b42318'?>">
      <?=$firebaseFileOk?'พบไฟล์แล้ว':'ยังไม่พบไฟล์'?>
    </strong>
  </p>
  <p>
    เครื่องที่ลงทะเบียน Push แล้ว:
    <strong><?=e((string)$pushTokenCount)?></strong> เครื่อง
  </p>
  <p>
    OpenSSL:
    <strong style="color:<?=$opensslOk?'#16803b':'#b42318'?>">
      <?=$opensslOk?'พร้อม':'ยังไม่พร้อม'?>
    </strong>
    &nbsp; | &nbsp;
    การเชื่อมต่อ HTTPS:
    <strong style="color:<?=$httpOk?'#16803b':'#b42318'?>">
      <?=$httpOk?'พร้อม':'ยังไม่พร้อม'?>
    </strong>
  </p>

  <form method="post">
    <input type="hidden" name="csrf" value="<?=csrf_token()?>">
    <button type="submit">ตรวจและอัปเดต V14</button>
  </form>
</div>

<div class="card" style="max-width:760px;margin-top:16px">
  <h3>Firebase key ที่ระบบจะใช้</h3>
  <code><?=e($firebasePath)?></code>
  <p style="margin-top:10px">ไฟล์นี้ต้องอยู่นอก public_html ตามที่ตั้งไว้ ไม่ต้องย้ายกลับเข้าหน้าเว็บ</p>
</div>
<?php include '_footer.php';?>
