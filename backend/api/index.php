<?php
declare(strict_types=1);
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Authorization, Content-Type');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(204); exit; }
require_once dirname(__DIR__) . '/includes/bootstrap.php';

$action = $_GET['action'] ?? 'home';
try {
    switch ($action) {
        case 'home':
            ensure_v9_schema($pdo);
            $premiumWhere = "l.status='approved' AND EXISTS (
                SELECT 1 FROM premium_promotions pp
                WHERE pp.listing_id=l.id AND pp.status='active' AND pp.starts_at<=NOW() AND pp.ends_at>NOW()
            )";
            $generalWhere = "l.status='approved' AND NOT EXISTS (
                SELECT 1 FROM premium_promotions pp
                WHERE pp.listing_id=l.id AND pp.status='active' AND pp.starts_at<=NOW() AND pp.ends_at>NOW()
            )";
            // V8 ordering: boosted premium first, then other premium; boosted
            // normal listings next, then ordinary listings by publish date.
            $premium = fetch_listings($pdo,$config,$premiumWhere,[],
                'CASE WHEN l.boosted_at IS NULL THEN 1 ELSE 0 END, l.boosted_at DESC, premium_until DESC, l.created_at DESC', 12);
            $latest = fetch_listings($pdo,$config,$generalWhere,[],
                'CASE WHEN l.boosted_at IS NULL THEN 1 ELSE 0 END, l.boosted_at DESC, l.created_at DESC', 30);
            $random = fetch_listings($pdo,$config,"l.status='approved'",[], 'RAND()', 12);
            $hero = fetch_home_content($pdo);
            $banners = home_banners($pdo,$config,true);
            json_out(true, '', ['premium'=>$premium,'latest'=>$latest,'random'=>$random,'hero'=>$hero,'banners'=>$banners]);

        case 'listing':
            ensure_v9_schema($pdo);
            $id = (int)($_GET['id'] ?? 0);
            $access = $pdo->prepare('SELECT user_id,status FROM listings WHERE id=? LIMIT 1');
            $access->execute([$id]);
            $row = $access->fetch();
            if (!$row) json_out(false,'ไม่พบประกาศ',null,404);
            $viewer = api_user($pdo);
            $canViewPrivate = $viewer && ((int)$viewer['id'] === (int)$row['user_id'] || $viewer['role'] === 'admin');
            if (!in_array($row['status'], ['approved','sold'], true) && !$canViewPrivate) json_out(false,'ไม่พบประกาศ',null,404);
            $listing = fetch_listing($pdo,$config,$id,false);
            if (!$listing) json_out(false,'ไม่พบประกาศ',null,404);
            if ($viewer) {
                ensure_v5_schema($pdo);
                $listing['is_favorite'] = is_listing_favorite($pdo, (int)$viewer['id'], $id);
                if ((int)$viewer['id'] !== (int)$row['user_id']) {
                    $listing['seller_payment'] = verified_bank_account($pdo,(int)$row['user_id'])
                        ?: ['is_verified'=>false,'bank_name'=>'','account_name'=>'','account_number'=>'','verified_at'=>null];
                }
            } else {
                $listing['is_favorite'] = false;
            }
            json_out(true,'',$listing);

        case 'captcha':
            json_out(true,'',issue_captcha($pdo,$config));

        case 'announcements':
            $stmt=$pdo->prepare('SELECT id,title,body,created_at FROM announcements WHERE is_active=1 ORDER BY created_at DESC LIMIT 20');
            $stmt->execute();
            json_out(true,'',$stmt->fetchAll());

        case 'register':
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $username=post_value('username'); $email=post_value('email'); $password=(string)($_POST['password']??'');
            $phone=post_value('phone'); $line=post_value('line_id');
            $province=post_value('province'); $amphoe=post_value('amphoe'); $tambon=post_value('tambon');
            if (!preg_match('/^[A-Za-z0-9_]{3,30}$/',$username)) json_out(false,'ชื่อผู้ใช้ใช้ A-Z, 0-9 หรือ _ จำนวน 3–30 ตัว',null,422);
            if (!filter_var($email,FILTER_VALIDATE_EMAIL)) json_out(false,'อีเมลไม่ถูกต้อง',null,422);
            if (strlen($password)<8) json_out(false,'รหัสผ่านต้องอย่างน้อย 8 ตัว',null,422);
            if (!$province || !$amphoe || !$tambon) json_out(false,'กรุณาเลือกจังหวัด อำเภอ และตำบล',null,422);
            $check=$pdo->prepare('SELECT id FROM users WHERE username=? OR email=?'); $check->execute([$username,$email]);
            if ($check->fetch()) json_out(false,'ชื่อผู้ใช้หรืออีเมลนี้ถูกใช้แล้ว',null,409);
            $stmt=$pdo->prepare("INSERT INTO users(username,email,password_hash,phone,line_id,province,amphoe,tambon,role,status) VALUES(?,?,?,?,?,?,?,?,'member','active')");
            $stmt->execute([$username,$email,password_hash($password,PASSWORD_DEFAULT),$phone?:null,$line?:null,$province,$amphoe,$tambon]);
            $id=(int)$pdo->lastInsertId(); $token=issue_api_token($pdo,$id);
            json_out(true,'สมัครสมาชิกสำเร็จ',['token'=>$token,'user'=>[
                'id'=>$id,'username'=>$username,'email'=>$email,'phone'=>$phone?:null,'line_id'=>$line?:null,
                'province'=>$province,'amphoe'=>$amphoe,'tambon'=>$tambon,'role'=>'member','status'=>'active'
            ]]);

        case 'login':
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $username=post_value('username'); $password=(string)($_POST['password']??'');
            $stmt=$pdo->prepare('SELECT * FROM users WHERE username=? OR email=? LIMIT 1'); $stmt->execute([$username,$username]); $user=$stmt->fetch();
            if (!$user || !password_verify($password,$user['password_hash'])) json_out(false,'ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง',null,401);
            if ($user['status']!=='active') json_out(false,'บัญชีถูกระงับ กรุณาติดต่อแอดมิน',null,403);
            $token=issue_api_token($pdo,(int)$user['id']);
            json_out(true,'เข้าสู่ระบบสำเร็จ',['token'=>$token,'user'=>['id'=>(int)$user['id'],'username'=>$user['username'],'email'=>$user['email'],'phone'=>$user['phone'],'line_id'=>$user['line_id'],'role'=>$user['role'],'status'=>$user['status']]]);

        case 'my_profile':
            ensure_v10_schema($pdo);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $summary=seller_public_summary($pdo,$uid);
            $verification=identity_verification_payload($pdo,$uid,true);
            json_out(true,'',[
                'id'=>$uid,
                'username'=>(string)$user['username'],
                'display_name'=>(string)(($user['display_name'] ?? '') ?: $user['username']),
                'display_name_change_count'=>(int)($user['display_name_change_count'] ?? 0),
                'can_change_display_name_directly'=>(int)($user['display_name_change_count'] ?? 0) < 1,
                'pending_display_name_request'=>pending_display_name_request($pdo,$uid),
                'admin_stars'=>(int)($user['admin_stars'] ?? 0),
                'special_icon'=>($user['special_icon'] ?? null) ?: null,
                'email'=>(string)$user['email'],
                'phone'=>$user['phone'],
                'line_id'=>$user['line_id'],
                'province'=>($user['province'] ?? null) ?: null,
                'amphoe'=>($user['amphoe'] ?? null) ?: null,
                'tambon'=>($user['tambon'] ?? null) ?: null,
                'member_since'=>$user['created_at'],
                'role'=>(string)($user['role'] ?? 'member'),
                'is_admin'=>(($user['role'] ?? 'member') === 'admin'),
                'is_verified'=>$summary['is_verified'],
                'rating_average'=>$summary['rating_average'],
                'rating_count'=>$summary['rating_count'],
                'verification'=>$verification,
            ]);

        case 'update_display_name':
            ensure_v10_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            enforce_rate_limit($pdo,'display_name',$uid,3,'เปลี่ยนชื่อเร็วเกินไป กรุณารอสักครู่');
            $requested=normalize_display_name(post_value('display_name'));
            $reason=mb_substr(trim(post_value('reason')),0,500);
            $current=(string)(($user['display_name'] ?? '') ?: $user['username']);
            if($requested===$current) json_out(false,'ชื่อใหม่เหมือนชื่อปัจจุบัน',null,422);

            $changeCount=(int)($user['display_name_change_count'] ?? 0);
            if($changeCount < 1){
                $pdo->prepare("UPDATE users SET display_name=?,display_name_change_count=display_name_change_count+1 WHERE id=?")
                    ->execute([$requested,$uid]);
                json_out(true,'เปลี่ยนชื่อที่แสดงแล้ว',[
                    'display_name'=>$requested,'status'=>'changed','requires_admin'=>false
                ]);
            }

            if(mb_strlen($reason)<5) json_out(false,'กรุณาระบุเหตุผลอย่างน้อย 5 ตัวอักษรเพื่อส่งให้แอดมินอนุมัติ',null,422);
            $pending=$pdo->prepare("SELECT id FROM display_name_change_requests WHERE user_id=? AND status='pending' LIMIT 1");
            $pending->execute([$uid]);
            if($pending->fetchColumn()) json_out(false,'มีคำขอเปลี่ยนชื่อที่รอแอดมินอยู่แล้ว',null,409);

            $st=$pdo->prepare("INSERT INTO display_name_change_requests(user_id,requested_name,reason,status)
                VALUES(?,?,?,'pending')");
            $st->execute([$uid,$requested,$reason]);
            $requestId=(int)$pdo->lastInsertId();
            try {
                admin_notification_create(
                    $pdo,'display_name_request','มีคำขอเปลี่ยนชื่อสมาชิก',
                    'สมาชิก '.$current.' ขอเปลี่ยนชื่อเป็น “'.$requested.'”',
                    $uid,'display_name',$requestId,
                    'users.php','admin/users',
                    'display-name:'.$requestId
                );
            } catch(Throwable $notifyError) { error_log($notifyError->getMessage()); }
            json_out(true,'ส่งคำขอเปลี่ยนชื่อให้แอดมินแล้ว',[
                'id'=>$requestId,'display_name'=>$current,'requested_name'=>$requested,
                'status'=>'pending','requires_admin'=>true
            ],201);

        case 'member_profile':
            ensure_v10_schema($pdo);
            $userId=(int)($_GET['user_id']??0);
            $profile=$userId>0 ? member_profile_payload($pdo,$config,$userId) : null;
            if(!$profile) json_out(false,'ไม่พบสมาชิก',null,404);
            json_out(true,'',$profile);

        case 'submit_verification':
            ensure_v9_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            enforce_rate_limit($pdo,'submit_verification',$uid,5,'ส่งคำขอยืนยันเร็วเกินไป กรุณารอสักครู่');

            $bankName=mb_substr(trim(post_value('bank_name')),0,120);
            $accountName=mb_substr(trim(post_value('account_name')),0,160);
            $accountNumber=mb_substr(trim(post_value('account_number')),0,80);
            if(mb_strlen($bankName)<2) json_out(false,'กรุณาระบุธนาคาร',null,422);
            if(mb_strlen($accountName)<2) json_out(false,'กรุณาระบุชื่อบัญชี',null,422);
            if(mb_strlen($accountNumber)<5 || !preg_match('/^[0-9A-Za-z\-\s.\/]+$/u',$accountNumber)) {
                json_out(false,'เลขบัญชีรับเงินไม่ถูกต้อง',null,422);
            }

            $documentPath=save_identity_document($config,$_FILES['document']??[]);
            $oldPath=null; $previousStatus=null;
            try {
                $old=$pdo->prepare("SELECT document_path,status FROM identity_verifications WHERE user_id=? LIMIT 1");
                $old->execute([$uid]); $oldRow=$old->fetch();
                $oldPath=$oldRow['document_path'] ?? null;
                $previousStatus=$oldRow['status'] ?? null;

                $pdo->beginTransaction();
                $st=$pdo->prepare("INSERT INTO identity_verifications(
                        user_id,bank_name,account_name,account_number,document_path,status,rejection_reason,
                        submitted_at,reviewed_by,reviewed_at,verified_at
                    ) VALUES(?,?,?,?,?,'pending',NULL,NOW(),NULL,NULL,NULL)
                    ON DUPLICATE KEY UPDATE
                        bank_name=VALUES(bank_name),account_name=VALUES(account_name),
                        account_number=VALUES(account_number),document_path=VALUES(document_path),
                        status='pending',rejection_reason=NULL,submitted_at=NOW(),
                        reviewed_by=NULL,reviewed_at=NULL,verified_at=NULL");
                $st->execute([$uid,$bankName,$accountName,$accountNumber,$documentPath]);
                $pdo->commit();
            } catch(Throwable $e) {
                if($pdo->inTransaction()) $pdo->rollBack();
                @unlink(identity_document_full_path($config,$documentPath));
                throw $e;
            }
            if($oldPath && $oldPath!==$documentPath) {
                try { @unlink(identity_document_full_path($config,(string)$oldPath)); } catch(Throwable $ignored) {}
            }
            try {
                $isResubmission=$previousStatus==='rejected';
                admin_notification_create(
                    $pdo,
                    $isResubmission?'identity_resubmitted':'identity_submitted',
                    $isResubmission?'มีการส่งยืนยันตัวตนใหม่':'มีสมาชิกส่งยืนยันตัวตน',
                    'สมาชิก '.(string)$user['username'].' ส่งหลักฐานยืนยันตัวตนเพื่อรอตรวจ',
                    $uid,'identity',$uid,
                    'verifications.php?status=pending','admin/verifications',
                    'identity:'.$uid.':'.sha1($documentPath)
                );
            } catch(Throwable $notifyError) { error_log($notifyError->getMessage()); }
            json_out(true,'ส่งข้อมูลแล้ว รอแอดมินตรวจสอบ',identity_verification_payload($pdo,$uid,true));

        case 'create_listing':
            ensure_v10_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo);
            if (post_value('website')!=='') json_out(false,'ตรวจพบสแปม',null,422);
            $title=post_value('title'); $description=post_value('description'); $price=(float)post_value('price');

            // V13: seller location belongs to the member account. New members
            // always have it from registration. Legacy V12 accounts may provide
            // it once from the post screen; we persist that first valid address.
            $savedProvince=trim((string)($user['province'] ?? ''));
            $savedAmphoe=trim((string)($user['amphoe'] ?? ''));
            $savedTambon=trim((string)($user['tambon'] ?? ''));
            $legacyAddressNeedsSave=false;
            if ($savedProvince!=='' && $savedAmphoe!=='' && $savedTambon!=='') {
                $province=$savedProvince; $amphoe=$savedAmphoe; $tambon=$savedTambon;
            } else {
                $province=post_value('province'); $amphoe=post_value('amphoe'); $tambon=post_value('tambon');
                $legacyAddressNeedsSave=($province!=='' && $amphoe!=='' && $tambon!=='');
            }
            $allowMeetup=post_value('allow_meetup')==='1';
            $allowBuyNow=post_value('allow_buy_now')==='1';
            $allowCod=post_value('allow_cod')==='1';
            $chatFirst=post_value('chat_first')==='1';
            if($allowCod) $allowBuyNow=true;
            if (mb_strlen($title)<3 || mb_strlen($title)>160) json_out(false,'หัวข้อต้องมี 3–160 ตัวอักษร',null,422);
            if ($price<=0) json_out(false,'กรุณากรอกราคาให้ถูกต้อง',null,422);
            if (!$province || !$amphoe || !$tambon) json_out(false,'กรุณากรอกจังหวัด อำเภอ และตำบล',null,422);
            if (!$allowMeetup && !$allowBuyNow && !$chatFirst) json_out(false,'กรุณาเลือกวิธีติดต่อหรือสั่งซื้ออย่างน้อย 1 แบบ',null,422);
            if (!verify_captcha($pdo,$config,post_value('captcha_token'),post_value('captcha_answer'))) json_out(false,'คำตอบป้องกันสแปมไม่ถูกต้องหรือหมดอายุ',null,422);
            enforce_rate_limit($pdo,'create_listing',(int)$user['id'],(int)$config['app']['post_cooldown_seconds']);
            $pdo->beginTransaction(); $saved=[];
            try {
                if ($legacyAddressNeedsSave) {
                    $saveAddress=$pdo->prepare("UPDATE users SET province=?,amphoe=?,tambon=? WHERE id=?");
                    $saveAddress->execute([$province,$amphoe,$tambon,(int)$user['id']]);
                }
                $stmt=$pdo->prepare("INSERT INTO listings(
                    user_id,title,description,allow_meetup,allow_buy_now,allow_cod,chat_first,
                    price,province,amphoe,tambon,status
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,'pending')");
                $stmt->execute([
                    $user['id'],$title,$description?:null,
                    $allowMeetup?1:0,$allowBuyNow?1:0,$allowCod?1:0,$chatFirst?1:0,
                    $price,$province,$amphoe,$tambon
                ]);
                $id=(int)$pdo->lastInsertId();
                $saved=save_listing_images($pdo,$config,$id,$_FILES['images']??[]);
                $pdo->commit();
            } catch (Throwable $e) {
                if ($pdo->inTransaction()) $pdo->rollBack();
                foreach ($saved as $file) @unlink($file);
                throw $e;
            }
            try {
                admin_notification_create(
                    $pdo,'listing_pending','มีประกาศรออนุมัติ',
                    'ประกาศ #'.$id.' “'.$title.'” จาก '.(string)$user['username'].' รอการตรวจ',
                    (int)$user['id'],'listing',$id,
                    'listings.php?status=pending','admin/listings',
                    'listing:'.$id
                );
            } catch(Throwable $notifyError) { error_log($notifyError->getMessage()); }
            $listing=fetch_listing($pdo,$config,$id,false);
            json_out(true,'ส่งประกาศแล้ว กรุณารอแอดมินตรวจ',$listing,201);

        case 'my_listings':
            ensure_v9_schema($pdo);
            $user=require_api_user($pdo);
            $items=fetch_listings($pdo,$config,'l.user_id=?',[(int)$user['id']],'l.created_at DESC',100);
            json_out(true,'',$items);


        case 'wallet_summary':
            $user=require_api_user($pdo);
            json_out(true,'',wallet_summary($pdo,(int)$user['id']));

        case 'lottery_overview':
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            if(!lottery_schema_ready($pdo)) {
                json_out(false,'ระบบลุ้นพระ V14 ยังไม่ได้อัปเดตฐานข้อมูล กรุณาให้แอดมินเข้าเมนูอัปเดตระบบ',null,503);
            }

            $pdo->prepare("INSERT IGNORE INTO point_wallets(user_id,balance) VALUES(?,0)")->execute([$uid]);
            $balSt=$pdo->prepare("SELECT balance FROM point_wallets WHERE user_id=? LIMIT 1");
            $balSt->execute([$uid]); $balance=(int)$balSt->fetchColumn();

            $roundSt=$pdo->query("SELECT * FROM lottery_rounds
                WHERE status IN ('open','closed','announced')
                ORDER BY (status='open') DESC,id DESC LIMIT 1");
            $round=$roundSt->fetch();
            $roundPayload=null; $soldNumbers=[]; $myEntries=[];
            if($round){
                $roundId=(int)$round['id'];
                $roundPayload=lottery_round_payload($pdo,$config,$round);

                $soldSt=$pdo->prepare("SELECT number FROM lottery_entries WHERE round_id=? ORDER BY number");
                $soldSt->execute([$roundId]);
                $soldNumbers=array_map('intval',$soldSt->fetchAll(PDO::FETCH_COLUMN));

                $mineSt=$pdo->prepare("SELECT id,number,points_spent,created_at
                    FROM lottery_entries WHERE round_id=? AND user_id=? ORDER BY number");
                $mineSt->execute([$roundId,$uid]);
                $myEntries=array_map(static function(array $row): array {
                    return [
                        'id'=>(int)$row['id'],
                        'number'=>str_pad((string)(int)$row['number'],2,'0',STR_PAD_LEFT),
                        'points_spent'=>(int)$row['points_spent'],
                        'created_at'=>(string)$row['created_at'],
                    ];
                },$mineSt->fetchAll());
            }

            $historyRows=$pdo->query("SELECT * FROM lottery_rounds
                WHERE status='announced' ORDER BY id DESC LIMIT 5")->fetchAll();
            $history=array_map(static fn(array $row): array => lottery_round_payload($pdo,$config,$row),$historyRows);

            json_out(true,'',[
                'balance'=>$balance,
                'round'=>$roundPayload,
                'sold_numbers'=>$soldNumbers,
                'my_entries'=>$myEntries,
                'recent_results'=>$history,
            ]);

        case 'lottery_buy_number':
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            if(!lottery_schema_ready($pdo)) {
                json_out(false,'ระบบลุ้นพระ V14 ยังไม่ได้อัปเดตฐานข้อมูล กรุณาให้แอดมินเข้าเมนูอัปเดตระบบ',null,503);
            }
            $roundId=(int)post_value('round_id');
            $numberRaw=trim(post_value('number'));
            $requestKey=valid_request_key(post_value('request_key'));
            if($roundId<=0 || !preg_match('/^\d{2}$/',$numberRaw)) {
                json_out(false,'กรุณาเลือกเลข 2 ตัวตั้งแต่ 00–99',null,422);
            }
            $number=(int)$numberRaw;
            if($number<0 || $number>99) json_out(false,'เลขต้องอยู่ระหว่าง 00–99',null,422);

            $pdo->beginTransaction();
            try {
                $roundSt=$pdo->prepare("SELECT id,title,prize_name,points_cost,status
                    FROM lottery_rounds WHERE id=? LIMIT 1 FOR UPDATE");
                $roundSt->execute([$roundId]); $round=$roundSt->fetch();
                if(!$round) throw new RuntimeException('ไม่พบรอบร่วมสนุก');

                // Idempotent retry: a lost Android response must not charge twice,
                // even if Admin closed/announced the round after the first commit.
                $existingSt=$pdo->prepare("SELECT id,round_id,user_id,number,points_spent,created_at
                    FROM lottery_entries WHERE request_key=? LIMIT 1");
                $existingSt->execute([$requestKey]); $existing=$existingSt->fetch();
                if($existing){
                    if((int)$existing['round_id']!==$roundId || (int)$existing['user_id']!==$uid || (int)$existing['number']!==$number) {
                        throw new RuntimeException('รหัสคำขอซ้ำกับเลขอื่น');
                    }
                    $pdo->prepare("INSERT IGNORE INTO point_wallets(user_id,balance) VALUES(?,0)")->execute([$uid]);
                    $balSt=$pdo->prepare("SELECT balance FROM point_wallets WHERE user_id=? LIMIT 1");
                    $balSt->execute([$uid]); $balance=(int)$balSt->fetchColumn();
                    $pdo->commit();
                    json_out(true,'ซื้อเลขแล้ว',[
                        'balance'=>$balance,
                        'entry'=>[
                            'id'=>(int)$existing['id'],
                            'number'=>str_pad((string)(int)$existing['number'],2,'0',STR_PAD_LEFT),
                            'points_spent'=>(int)$existing['points_spent'],
                            'created_at'=>(string)$existing['created_at'],
                        ]
                    ]);
                }

                if($round['status']!=='open') throw new RuntimeException('รอบนี้ปิดรับเลขแล้ว');

                $takenSt=$pdo->prepare("SELECT id FROM lottery_entries WHERE round_id=? AND number=? LIMIT 1");
                $takenSt->execute([$roundId,$number]);
                if($takenSt->fetchColumn()) throw new RuntimeException('เลข '.$numberRaw.' มีสมาชิกซื้อไปแล้ว กรุณาเลือกเลขอื่น');

                $cost=(int)$round['points_cost'];
                if($cost<=0) throw new RuntimeException('แต้มต่อเลขของรอบนี้ไม่ถูกต้อง กรุณาแจ้งแอดมิน');

                $pdo->prepare("INSERT IGNORE INTO point_wallets(user_id,balance) VALUES(?,0)")->execute([$uid]);
                $walletSt=$pdo->prepare("SELECT balance FROM point_wallets WHERE user_id=? FOR UPDATE");
                $walletSt->execute([$uid]); $balance=(int)$walletSt->fetchColumn();
                if($balance<$cost) throw new RuntimeException('แต้มไม่พอ ต้องใช้ '.$cost.' แต้มต่อเลข');

                $entrySt=$pdo->prepare("INSERT INTO lottery_entries(round_id,user_id,number,points_spent,request_key)
                    VALUES(?,?,?,?,?)");
                $entrySt->execute([$roundId,$uid,$number,$cost,$requestKey]);
                $entryId=(int)$pdo->lastInsertId();

                $pdo->prepare("UPDATE point_wallets SET balance=balance-? WHERE user_id=?")->execute([$cost,$uid]);
                $desc='ร่วมสนุกลุ้นพระรอบ #'.$roundId.' เลข '.$numberRaw.' — '.$round['prize_name'];
                $pdo->prepare("INSERT INTO point_transactions(user_id,amount,type,description)
                    VALUES(?,?,'lottery_purchase',?)")->execute([$uid,-$cost,$desc]);

                $createdSt=$pdo->prepare("SELECT created_at FROM lottery_entries WHERE id=?");
                $createdSt->execute([$entryId]); $createdAt=(string)$createdSt->fetchColumn();

                $pdo->commit();
                json_out(true,'ซื้อเลข '.$numberRaw.' สำเร็จ',[
                    'balance'=>$balance-$cost,
                    'entry'=>[
                        'id'=>$entryId,'number'=>$numberRaw,'points_spent'=>$cost,'created_at'=>$createdAt
                    ]
                ]);
            } catch(Throwable $e) {
                if($pdo->inTransaction()) $pdo->rollBack();
                throw $e;
            }

        case 'heartbeat':
            ensure_v7_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $viewer=api_user($pdo);
            $count=touch_presence($pdo,post_value('client_id'),$viewer ? (int)$viewer['id'] : null);
            json_out(true,'',['online_count'=>$count]);

        case 'request_topup':
            ensure_v7_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            enforce_rate_limit($pdo,'request_topup',$uid,5,'ส่งสลิปเร็วเกินไป กรุณารอสักครู่');

            $amount=(float)post_value('amount');
            $note=mb_substr(post_value('note'),0,255);
            $payment=payment_settings($pdo);
            if(!$payment['is_active']) json_out(false,'ระบบเติมแต้มถูกปิดชั่วคราว',null,503);
            if($amount < (float)$payment['min_amount']) {
                json_out(false,'ยอดเติมขั้นต่ำ '.number_format((float)$payment['min_amount'],2).' บาท',null,422);
            }
            if($amount > 1000000) json_out(false,'ยอดเติมสูงเกินที่ระบบรองรับ',null,422);
            $points=(int)round($amount * (float)$payment['points_per_baht']);
            if($points<=0) json_out(false,'จำนวนแต้มไม่ถูกต้อง',null,422);

            $slipPath=save_topup_slip($config,$_FILES['slip']??[]);
            try {
                $snapshot=json_encode([
                    'bank_name'=>$payment['bank_name'],
                    'account_name'=>$payment['account_name'],
                    'account_number'=>$payment['account_number'],
                    'points_per_baht'=>$payment['points_per_baht'],
                ],JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
                $st=$pdo->prepare("INSERT INTO point_topup_requests(user_id,package_id,points,amount,note,slip_path,payment_snapshot)
                    VALUES(?,NULL,?,?,?,?,?)");
                $st->execute([$uid,$points,$amount,$note?:null,$slipPath,$snapshot]);
                $id=(int)$pdo->lastInsertId();
            } catch(Throwable $e) {
                @unlink(dirname(__DIR__).'/'.$slipPath);
                throw $e;
            }
            try {
                admin_notification_create(
                    $pdo,'topup_submitted','มีคำขอเติมแต้มใหม่',
                    'สมาชิก '.(string)$user['username'].' ส่งสลิป '.number_format($amount,2).' บาท / '.$points.' แต้ม',
                    $uid,'topup',$id,
                    'points.php?status=pending','admin/topups',
                    'topup:'.$id
                );
            } catch(Throwable $notifyError) { error_log($notifyError->getMessage()); }
            json_out(true,'ส่งสลิปแล้ว รอแอดมินอนุมัติ',['id'=>$id,'points'=>$points]);

        case 'purchase_premium':
            ensure_v9_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $listingId=(int)post_value('listing_id');
            $planId=(int)post_value('plan_id');
            $requestKey=valid_request_key(post_value('request_key'));

            $pdo->beginTransaction();
            try {
                // Idempotent retry: if Android lost the first response, return
                // that same purchase without charging a second time.
                $st=$pdo->prepare("SELECT id,starts_at,ends_at,user_id,listing_id,plan_id FROM premium_promotions WHERE request_key=? LIMIT 1");
                $st->execute([$requestKey]); $existing=$st->fetch();
                if($existing){
                    if((int)$existing['user_id']!==$uid || (int)$existing['listing_id']!==$listingId || (int)$existing['plan_id']!==$planId) {
                        throw new RuntimeException('รหัสคำขอซ้ำกับรายการอื่น');
                    }
                    $pdo->prepare("INSERT IGNORE INTO point_wallets(user_id,balance) VALUES(?,0)")->execute([$uid]);
                    $bal=$pdo->prepare("SELECT balance FROM point_wallets WHERE user_id=?");
                    $bal->execute([$uid]); $currentBalance=(int)$bal->fetchColumn();
                    $pdo->commit();
                    json_out(true,'เปิดพรีเมียมสำเร็จ',[
                        'id'=>(int)$existing['id'],'balance'=>$currentBalance,
                        'starts_at'=>$existing['starts_at'],'ends_at'=>$existing['ends_at']
                    ]);
                }

                $st=$pdo->prepare("SELECT id,title,status,user_id FROM listings WHERE id=? AND user_id=? LIMIT 1 FOR UPDATE");
                $st->execute([$listingId,$uid]); $listing=$st->fetch();
                if(!$listing) throw new RuntimeException('ไม่พบประกาศของคุณ');
                if($listing['status']!=='approved') throw new RuntimeException('ซื้อพรีเมียมได้เฉพาะประกาศที่อนุมัติแล้ว');

                $st=$pdo->prepare("SELECT id,name,points_cost,duration_days FROM premium_plans WHERE id=? AND is_active=1 LIMIT 1");
                $st->execute([$planId]); $plan=$st->fetch();
                if(!$plan) throw new RuntimeException('ไม่พบแพ็กเกจพรีเมียม');

                $st=$pdo->prepare("SELECT id,ends_at FROM premium_promotions
                    WHERE listing_id=? AND status='active' AND starts_at<=NOW() AND ends_at>NOW()
                    ORDER BY ends_at DESC LIMIT 1 FOR UPDATE");
                $st->execute([$listingId]);
                if($st->fetch()) throw new RuntimeException('ประกาศนี้เป็นพรีเมียมอยู่แล้ว กรุณารอให้หมดอายุก่อน');

                $pdo->prepare("INSERT IGNORE INTO point_wallets(user_id,balance) VALUES(?,0)")->execute([$uid]);
                $st=$pdo->prepare("SELECT balance FROM point_wallets WHERE user_id=? FOR UPDATE");
                $st->execute([$uid]); $balance=(int)$st->fetchColumn();
                $cost=(int)$plan['points_cost'];
                if($balance<$cost) throw new RuntimeException('แต้มไม่พอ กรุณาเติมแต้มก่อน');

                $pdo->prepare("UPDATE point_wallets SET balance=balance-? WHERE user_id=?")->execute([$cost,$uid]);
                $days=max(1,(int)$plan['duration_days']);
                $st=$pdo->prepare("INSERT INTO premium_promotions(listing_id,user_id,plan_id,points_spent,request_key,starts_at,ends_at,status)
                    VALUES(?,?,?,?,?,NOW(),DATE_ADD(NOW(), INTERVAL ".$days." DAY),'active')");
                $st->execute([$listingId,$uid,$planId,$cost,$requestKey]);
                $promotionId=(int)$pdo->lastInsertId();

                $description='ซื้อพรีเมียม '.$plan['name'].' สำหรับ #'.$listingId.' '.$listing['title'];
                $pdo->prepare("INSERT INTO point_transactions(user_id,amount,type,description,listing_id)
                    VALUES(?,?,'premium_purchase',?,?)")->execute([$uid,-$cost,$description,$listingId]);

                $st=$pdo->prepare("SELECT starts_at,ends_at FROM premium_promotions WHERE id=?");
                $st->execute([$promotionId]); $times=$st->fetch();

                $pdo->commit();
                json_out(true,'เปิดพรีเมียมสำเร็จ',[
                    'id'=>$promotionId,'balance'=>$balance-$cost,
                    'starts_at'=>$times['starts_at']??null,'ends_at'=>$times['ends_at']??null
                ]);
            } catch(Throwable $e) {
                if($pdo->inTransaction()) $pdo->rollBack();
                throw $e;
            }

        case 'purchase_boost':
            ensure_v9_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $listingId=(int)post_value('listing_id');
            $requestKey=valid_request_key(post_value('request_key'));

            $pdo->beginTransaction();
            try {
                $st=$pdo->prepare("SELECT id,user_id,listing_id,boosted_at FROM listing_boosts WHERE request_key=? LIMIT 1");
                $st->execute([$requestKey]); $existing=$st->fetch();
                if($existing){
                    if((int)$existing['user_id']!==$uid || (int)$existing['listing_id']!==$listingId) {
                        throw new RuntimeException('รหัสคำขอซ้ำกับรายการอื่น');
                    }
                    $pdo->prepare("INSERT IGNORE INTO point_wallets(user_id,balance) VALUES(?,0)")->execute([$uid]);
                    $bal=$pdo->prepare("SELECT balance FROM point_wallets WHERE user_id=?");
                    $bal->execute([$uid]); $currentBalance=(int)$bal->fetchColumn();
                    $pdo->commit();
                    json_out(true,'ดันโพสต์สำเร็จ',[
                        'id'=>(int)$existing['id'],'balance'=>$currentBalance,'boosted_at'=>$existing['boosted_at']
                    ]);
                }

                $st=$pdo->prepare("SELECT id,title,status,user_id,boosted_at FROM listings WHERE id=? AND user_id=? LIMIT 1 FOR UPDATE");
                $st->execute([$listingId,$uid]); $listing=$st->fetch();
                if(!$listing) throw new RuntimeException('ไม่พบประกาศของคุณ');
                if($listing['status']!=='approved') throw new RuntimeException('ดันได้เฉพาะประกาศที่อนุมัติแล้ว');

                $settings=boost_settings($pdo);
                if(!$settings['is_active']) throw new RuntimeException('ระบบดันโพสต์ถูกปิดชั่วคราว');
                $cost=(int)$settings['points_cost'];
                $cooldown=(int)$settings['cooldown_minutes'];

                if(!empty($listing['boosted_at']) && $cooldown>0){
                    $st=$pdo->prepare("SELECT TIMESTAMPDIFF(MINUTE,?,NOW())");
                    $st->execute([$listing['boosted_at']]);
                    $elapsed=(int)$st->fetchColumn();
                    if($elapsed<$cooldown){
                        $wait=max(1,$cooldown-$elapsed);
                        throw new RuntimeException('ประกาศนี้เพิ่งถูกดัน กรุณารออีกประมาณ '.$wait.' นาที');
                    }
                }

                $pdo->prepare("INSERT IGNORE INTO point_wallets(user_id,balance) VALUES(?,0)")->execute([$uid]);
                $st=$pdo->prepare("SELECT balance FROM point_wallets WHERE user_id=? FOR UPDATE");
                $st->execute([$uid]); $balance=(int)$st->fetchColumn();
                if($balance<$cost) throw new RuntimeException('แต้มไม่พอ กรุณาเติมแต้มก่อน');

                $pdo->prepare("UPDATE point_wallets SET balance=balance-? WHERE user_id=?")->execute([$cost,$uid]);
                $pdo->prepare("UPDATE listings SET boosted_at=NOW() WHERE id=?")->execute([$listingId]);
                $st=$pdo->prepare("SELECT boosted_at FROM listings WHERE id=?");
                $st->execute([$listingId]); $boostedAt=(string)$st->fetchColumn();

                $st=$pdo->prepare("INSERT INTO listing_boosts(listing_id,user_id,points_spent,boosted_at,request_key)
                    VALUES(?,?,?,?,?)");
                $st->execute([$listingId,$uid,$cost,$boostedAt,$requestKey]);
                $boostId=(int)$pdo->lastInsertId();

                $description='ดันโพสต์ #'.$listingId.' '.$listing['title'];
                $pdo->prepare("INSERT INTO point_transactions(user_id,amount,type,description,listing_id)
                    VALUES(?,?,'boost_purchase',?,?)")->execute([$uid,-$cost,$description,$listingId]);

                $pdo->commit();
                json_out(true,'ดันโพสต์สำเร็จ',[
                    'id'=>$boostId,'balance'=>$balance-$cost,'boosted_at'=>$boostedAt
                ]);
            } catch(Throwable $e) {
                if($pdo->inTransaction()) $pdo->rollBack();
                throw $e;
            }

        case 'create_order':
            ensure_v10_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $listingId=(int)post_value('listing_id');
            $requestKey=valid_request_key(post_value('request_key'));
            $paymentMethod=post_value('payment_method');
            $recipient=mb_substr(post_value('recipient_name'),0,160);
            $phone=mb_substr(post_value('phone'),0,30);
            $house=mb_substr(post_value('house_no_moo'),0,190);
            $soi=mb_substr(post_value('soi'),0,120);
            $road=mb_substr(post_value('road'),0,120);
            $subdistrict=mb_substr(post_value('subdistrict'),0,100);
            $district=mb_substr(post_value('district'),0,100);
            $province=mb_substr(post_value('province'),0,100);
            $postal=mb_substr(post_value('postal_code'),0,10);

            if(!in_array($paymentMethod,['bank_transfer','cod'],true)) json_out(false,'กรุณาเลือกวิธีชำระเงิน',null,422);
            if(mb_strlen($recipient)<2) json_out(false,'กรุณากรอกชื่อผู้รับ',null,422);
            if(!preg_match('/^[0-9+\-\s]{6,30}$/',$phone)) json_out(false,'เบอร์โทรศัพท์ไม่ถูกต้อง',null,422);
            if($house==='' || $subdistrict==='' || $district==='' || $province==='') json_out(false,'กรุณากรอกที่อยู่ให้ครบ',null,422);
            if(!preg_match('/^\d{5}$/',$postal)) json_out(false,'รหัสไปรษณีย์ต้องเป็นตัวเลข 5 หลัก',null,422);

            $savedSlipPath=null;
            $pdo->beginTransaction();
            try {
                $st=$pdo->prepare("SELECT order_id,buyer_id,listing_id FROM orders WHERE request_key=? LIMIT 1");
                $st->execute([$requestKey]); $existing=$st->fetch();
                if($existing){
                    if((int)$existing['buyer_id']!==$uid || (int)$existing['listing_id']!==$listingId) {
                        throw new RuntimeException('รหัสคำขอซ้ำกับรายการอื่น');
                    }
                    $orderId=(int)$existing['order_id'];
                    $pdo->commit();
                    $payload=order_payload($pdo,$config,$orderId,$uid);
                    json_out(true,'สร้างคำสั่งซื้อแล้ว',$payload,200);
                }

                // Lock listing first so one item cannot be reserved by two buyers.
                $st=$pdo->prepare("SELECT id,user_id,title,price,status,allow_buy_now,allow_cod
                    FROM listings WHERE id=? LIMIT 1 FOR UPDATE");
                $st->execute([$listingId]); $listing=$st->fetch();
                if(!$listing) throw new RuntimeException('ไม่พบประกาศ');
                if((int)$listing['user_id']===$uid) throw new RuntimeException('ไม่สามารถซื้อประกาศของตัวเองได้');
                if($listing['status']!=='approved') throw new RuntimeException('ประกาศนี้ไม่พร้อมขายแล้ว');
                if(!(bool)$listing['allow_buy_now']) throw new RuntimeException('ผู้ขายกำหนดให้ติดต่อผ่านแชท/นัดรับ ไม่ได้เปิดสั่งซื้อผ่านระบบ');
                if($paymentMethod==='cod' && !(bool)$listing['allow_cod']) {
                    throw new RuntimeException('ประกาศนี้ไม่ได้เปิดเก็บเงินปลายทาง');
                }

                $st=$pdo->prepare("SELECT order_id FROM orders
                    WHERE listing_id=? AND status IN ('pending_confirmation','preparing','shipped')
                    ORDER BY order_id DESC LIMIT 1 FOR UPDATE");
                $st->execute([$listingId]);
                if($st->fetch()) throw new RuntimeException('มีผู้สั่งซื้อประกาศนี้แล้ว กรุณารอผู้ขายดำเนินการ');

                $coverSt=$pdo->prepare("SELECT file_path FROM listing_images WHERE listing_id=? ORDER BY sort_order,id LIMIT 1");
                $coverSt->execute([$listingId]); $cover=$coverSt->fetchColumn() ?: null;

                // Only an Admin-verified receiving account can be used for instant transfer.
                $bankSt=$pdo->prepare("SELECT bank_name,account_name,account_number,verified_at
                    FROM identity_verifications WHERE user_id=? AND status='verified' LIMIT 1 FOR UPDATE");
                $bankSt->execute([(int)$listing['user_id']]); $verifiedBank=$bankSt->fetch();
                $sellerVerified=(bool)$verifiedBank;
                if($paymentMethod==='bank_transfer' && !$sellerVerified) {
                    throw new RuntimeException('ผู้ขายยังไม่มีบัญชีรับเงินที่ยืนยันแล้ว กรุณาเลือกเก็บเงินปลายทางหรือติดต่อผู้ขาย');
                }

                if($paymentMethod==='bank_transfer'){
                    $savedSlipPath=save_order_payment_slip($config,$_FILES['slip']??[]);
                }

                $st=$pdo->prepare("INSERT INTO orders(
                    listing_id,buyer_id,seller_id,price_snapshot,title_snapshot,cover_path_snapshot,
                    seller_verified,seller_bank_name_snapshot,seller_account_name_snapshot,
                    seller_account_number_snapshot,seller_verified_at_snapshot,payment_method,payment_slip_path,
                    recipient_name,phone,house_no_moo,soi,road,subdistrict,district,province,postal_code,request_key
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                $st->execute([
                    $listingId,$uid,(int)$listing['user_id'],(float)$listing['price'],(string)$listing['title'],$cover,
                    $sellerVerified?1:0,
                    $verifiedBank['bank_name']??null,$verifiedBank['account_name']??null,
                    $verifiedBank['account_number']??null,$verifiedBank['verified_at']??null,
                    $paymentMethod,$savedSlipPath,
                    $recipient,$phone,$house,$soi?:null,$road?:null,$subdistrict,$district,$province,$postal,$requestKey
                ]);
                $orderId=(int)$pdo->lastInsertId();
                $sellerId=(int)$listing['user_id'];
                $pdo->commit();

                try {
                    firebase_push_to_user($pdo,$sellerId,[
                        'type'=>'order','title'=>'มีคำสั่งซื้อใหม่',
                        'body'=>'มีผู้ซื้อสั่ง '.$listing['title'].' กรุณาตรวจสอบและยืนยัน',
                        'order_id'=>(string)$orderId,'recipient_user_id'=>(string)$sellerId
                    ]);
                } catch(Throwable $pushError) { error_log($pushError->getMessage()); }

                json_out(true,'สร้างคำสั่งซื้อแล้ว',order_payload($pdo,$config,$orderId,$uid),201);
            } catch(Throwable $e) {
                if($pdo->inTransaction()) $pdo->rollBack();
                if($savedSlipPath){
                    try { @unlink(order_payment_slip_full_path($config,$savedSlipPath)); } catch(Throwable $ignored) {}
                }
                throw $e;
            }

        case 'my_orders':
            ensure_v9_schema($pdo);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $st=$pdo->prepare("SELECT order_id FROM orders WHERE buyer_id=? ORDER BY order_id DESC LIMIT 100");
            $st->execute([$uid]);
            $items=[];
            foreach($st->fetchAll() as $row){
                $payload=order_payload($pdo,$config,(int)$row['order_id'],$uid);
                if($payload) $items[]=$payload;
            }
            json_out(true,'',$items);

        case 'received_orders':
            ensure_v9_schema($pdo);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $st=$pdo->prepare("SELECT order_id FROM orders WHERE seller_id=? ORDER BY order_id DESC LIMIT 100");
            $st->execute([$uid]);
            $items=[];
            foreach($st->fetchAll() as $row){
                $payload=order_payload($pdo,$config,(int)$row['order_id'],$uid);
                if($payload) $items[]=$payload;
            }
            json_out(true,'',$items);

        case 'order_detail':
            ensure_v10_schema($pdo);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $orderId=(int)($_GET['order_id']??0);
            json_out(true,'',require_order_participant($pdo,$config,$orderId,$uid));

        case 'order_slip':
            ensure_v10_schema($pdo);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $orderId=(int)($_GET['order_id']??0);
            $st=$pdo->prepare("SELECT buyer_id,seller_id,payment_slip_path FROM orders WHERE order_id=? LIMIT 1");
            $st->execute([$orderId]); $order=$st->fetch();
            if(!$order) { http_response_code(404); exit('ไม่พบคำสั่งซื้อ'); }
            $isParticipant=(int)$order['buyer_id']===$uid || (int)$order['seller_id']===$uid;
            if(!$isParticipant && ($user['role']??'member')!=='admin') { http_response_code(403); exit('ไม่มีสิทธิ์เปิดสลิป'); }
            $relative=(string)($order['payment_slip_path']??'');
            if($relative==='') { http_response_code(404); exit('ไม่พบสลิป'); }
            try { $full=order_payment_slip_full_path($config,$relative); }
            catch(Throwable $e) { http_response_code(404); exit('ไม่พบสลิป'); }
            if(!is_file($full) || !is_readable($full)) { http_response_code(404); exit('ไม่พบสลิป'); }
            $finfo=new finfo(FILEINFO_MIME_TYPE); $mime=(string)$finfo->file($full);
            if(!in_array($mime,['image/jpeg','image/png','image/webp'],true)) { http_response_code(415); exit('ชนิดไฟล์ไม่รองรับ'); }
            header('Content-Type: '.$mime);
            header('Content-Length: '.filesize($full));
            header('Content-Disposition: inline; filename="order-slip"');
            header('Cache-Control: private, no-store, no-cache, must-revalidate');
            readfile($full);
            exit;

        case 'order_action':
            ensure_v10_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $orderId=(int)post_value('order_id');
            $action=post_value('order_action');
            $tracking=mb_substr(post_value('tracking_number'),0,120);

            $lookup=$pdo->prepare("SELECT listing_id FROM orders WHERE order_id=? LIMIT 1");
            $lookup->execute([$orderId]); $listingId=(int)$lookup->fetchColumn();
            if($listingId<=0) json_out(false,'ไม่พบคำสั่งซื้อ',null,404);

            $notifyUserId=0; $pushTitle=''; $pushBody='';
            $pdo->beginTransaction();
            try {
                $st=$pdo->prepare("SELECT id,status FROM listings WHERE id=? LIMIT 1 FOR UPDATE");
                $st->execute([$listingId]); $listingLock=$st->fetch();
                if(!$listingLock) throw new RuntimeException('ไม่พบประกาศของคำสั่งซื้อ');

                $st=$pdo->prepare("SELECT * FROM orders WHERE order_id=? LIMIT 1 FOR UPDATE");
                $st->execute([$orderId]); $order=$st->fetch();
                if(!$order) throw new RuntimeException('ไม่พบคำสั่งซื้อ');

                $isBuyer=(int)$order['buyer_id']===$uid;
                $isSeller=(int)$order['seller_id']===$uid;
                if(!$isBuyer && !$isSeller) throw new RuntimeException('ไม่มีสิทธิ์จัดการคำสั่งซื้อนี้');

                if($action==='confirm'){
                    if(!$isSeller || $order['status']!=='pending_confirmation') throw new RuntimeException('ไม่สามารถยืนยันคำสั่งซื้อนี้ได้');
                    if($listingLock['status']!=='approved') throw new RuntimeException('ประกาศนี้ไม่พร้อมขายแล้ว');
                    $pdo->prepare("UPDATE orders SET status='preparing',confirmed_at=NOW() WHERE order_id=?")->execute([$orderId]);
                    $pdo->prepare("UPDATE listings SET status='sold' WHERE id=? AND status='approved'")->execute([$listingId]);
                    $notifyUserId=(int)$order['buyer_id']; $pushTitle='ผู้ขายยืนยันคำสั่งซื้อแล้ว'; $pushBody='ผู้ขายกำลังเตรียมสินค้าให้คุณ';
                } elseif($action==='reject'){
                    if(!$isSeller || $order['status']!=='pending_confirmation') throw new RuntimeException('ไม่สามารถปฏิเสธคำสั่งซื้อนี้ได้');
                    $pdo->prepare("UPDATE orders SET status='cancelled',cancelled_at=NOW(),cancelled_by_user_id=? WHERE order_id=?")->execute([$uid,$orderId]);
                    $notifyUserId=(int)$order['buyer_id']; $pushTitle='ผู้ขายปฏิเสธคำสั่งซื้อ'; $pushBody='ประกาศกลับมาเปิดขายได้ตามปกติ';
                } elseif($action==='cancel'){
                    if((!$isBuyer && !$isSeller) || $order['status']!=='pending_confirmation') throw new RuntimeException('ยกเลิกได้เฉพาะช่วงรอผู้ขายยืนยัน');
                    $pdo->prepare("UPDATE orders SET status='cancelled',cancelled_at=NOW(),cancelled_by_user_id=? WHERE order_id=?")->execute([$uid,$orderId]);
                    $notifyUserId=$isBuyer?(int)$order['seller_id']:(int)$order['buyer_id'];
                    $pushTitle='คำสั่งซื้อถูกยกเลิก'; $pushBody='คำสั่งซื้อ #'.$orderId.' ถูกยกเลิก';
                } elseif($action==='ship'){
                    if(!$isSeller || $order['status']!=='preparing') throw new RuntimeException('คำสั่งซื้อนี้ยังไม่พร้อมบันทึกการจัดส่ง');
                    if(mb_strlen(trim($tracking))<3) throw new RuntimeException('กรุณากรอกเลขพัสดุ');
                    $pdo->prepare("UPDATE orders SET status='shipped',tracking_number=?,shipped_at=NOW() WHERE order_id=?")
                        ->execute([trim($tracking),$orderId]);
                    $notifyUserId=(int)$order['buyer_id']; $pushTitle='ผู้ขายจัดส่งแล้ว'; $pushBody='เลขพัสดุ: '.trim($tracking);
                } elseif($action==='received'){
                    if(!$isBuyer || $order['status']!=='shipped') throw new RuntimeException('ยืนยันรับสินค้าได้หลังผู้ขายจัดส่งแล้ว');
                    $pdo->prepare("UPDATE orders SET status='completed',completed_at=NOW() WHERE order_id=?")->execute([$orderId]);
                    $notifyUserId=(int)$order['seller_id']; $pushTitle='ผู้ซื้อได้รับสินค้าแล้ว'; $pushBody='คำสั่งซื้อ #'.$orderId.' สำเร็จ';
                } else {
                    throw new RuntimeException('คำสั่งไม่ถูกต้อง');
                }

                $pdo->commit();
            } catch(Throwable $e) {
                if($pdo->inTransaction()) $pdo->rollBack();
                throw $e;
            }

            if($notifyUserId>0){
                try {
                    firebase_push_to_user($pdo,$notifyUserId,[
                        'type'=>'order','title'=>$pushTitle,'body'=>$pushBody,
                        'order_id'=>(string)$orderId,'recipient_user_id'=>(string)$notifyUserId
                    ]);
                } catch(Throwable $pushError) { error_log($pushError->getMessage()); }
            }
            json_out(true,'อัปเดตคำสั่งซื้อแล้ว',order_payload($pdo,$config,$orderId,$uid));

        case 'submit_rating':
            ensure_v9_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $orderId=(int)post_value('order_id');
            $rating=(int)post_value('rating');
            $review=mb_substr(trim(post_value('review_text')),0,500);
            if($rating<1 || $rating>5) json_out(false,'คะแนนต้องอยู่ระหว่าง 1–5 ดาว',null,422);

            $pdo->beginTransaction();
            try {
                $st=$pdo->prepare("SELECT order_id,buyer_id,seller_id,status FROM orders WHERE order_id=? LIMIT 1 FOR UPDATE");
                $st->execute([$orderId]); $order=$st->fetch();
                if(!$order) throw new RuntimeException('ไม่พบคำสั่งซื้อ');
                if((int)$order['buyer_id']!==$uid) throw new RuntimeException('เฉพาะผู้ซื้อของ Order นี้เท่านั้นที่ให้คะแนนได้');
                if((int)$order['seller_id']===$uid) throw new RuntimeException('ไม่สามารถให้คะแนนตัวเองได้');
                if($order['status']!=='completed') throw new RuntimeException('ให้คะแนนได้หลังคำสั่งซื้อสำเร็จเท่านั้น');

                $check=$pdo->prepare("SELECT id FROM seller_reviews WHERE order_id=? LIMIT 1");
                $check->execute([$orderId]);
                if($check->fetchColumn()) {
                    if($pdo->inTransaction()) $pdo->rollBack();
                    json_out(false,'Order นี้ให้คะแนนไปแล้ว',null,409);
                }

                $st=$pdo->prepare("INSERT INTO seller_reviews(order_id,buyer_id,seller_id,rating,review_text)
                    VALUES(?,?,?,?,?)");
                $st->execute([$orderId,$uid,(int)$order['seller_id'],$rating,$review?:null]);
                $pdo->commit();
            } catch(Throwable $e) {
                if($pdo->inTransaction()) $pdo->rollBack();
                throw $e;
            }
            json_out(true,'บันทึกคะแนนแล้ว',order_payload($pdo,$config,$orderId,$uid));

        case 'chat_unread_count':
            ensure_v10_schema($pdo);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            json_out(true,'',['unread_count'=>chat_unread_count($pdo,$uid)]);

        case 'chat_threads':
            ensure_v10_schema($pdo);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $sql="SELECT cm.listing_id,cm.buyer_id,l.title,l.user_id seller_id,
                CASE WHEN l.user_id=? THEN COALESCE(NULLIF(buyer.display_name,''),buyer.username)
                     ELSE COALESCE(NULLIF(seller.display_name,''),seller.username) END other_username,
                CASE WHEN l.user_id=? THEN buyer.role ELSE seller.role END other_role,
                (SELECT CASE
                        WHEN COALESCE(m2.message,'')<>'' THEN m2.message
                        WHEN m2.image_path IS NOT NULL THEN '📷 รูปภาพ'
                        ELSE ''
                    END
                    FROM chat_messages m2
                    WHERE m2.listing_id=cm.listing_id AND m2.buyer_id=cm.buyer_id
                    ORDER BY m2.id DESC LIMIT 1) last_message,
                SUM(CASE WHEN cm.sender_id<>? AND cm.read_at IS NULL THEN 1 ELSE 0 END) unread_count,
                MAX(cm.created_at) updated_at
                FROM chat_messages cm
                JOIN listings l ON l.id=cm.listing_id
                JOIN users buyer ON buyer.id=cm.buyer_id
                JOIN users seller ON seller.id=l.user_id
                WHERE cm.buyer_id=? OR l.user_id=?
                GROUP BY cm.listing_id,cm.buyer_id,l.title,l.user_id,buyer.username,buyer.display_name,seller.username,seller.display_name,buyer.role,seller.role
                ORDER BY updated_at DESC";
            $st=$pdo->prepare($sql); $st->execute([$uid,$uid,$uid,$uid,$uid]);
            $threads=array_map(static function(array $row): array {
                $row['unread_count']=(int)($row['unread_count']??0);
                return $row;
            },$st->fetchAll());
            json_out(true,'',$threads);

        case 'chat_messages':
            ensure_v10_schema($pdo);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $listingId=(int)($_GET['listing_id']??0); $buyerId=(int)($_GET['buyer_id']??0);
            $st=$pdo->prepare('SELECT user_id,status FROM listings WHERE id=? LIMIT 1'); $st->execute([$listingId]); $listing=$st->fetch();
            if(!$listing) json_out(false,'ไม่พบประกาศ',null,404);
            $sellerId=(int)$listing['user_id'];
            $isSeller=$uid===$sellerId;
            if(!$isSeller) $buyerId=$uid;
            if($buyerId<=0) json_out(false,'ไม่พบคู่สนทนา',null,422);

            if(!$isSeller && !in_array($listing['status'],['approved','sold'],true)){
                $chk=$pdo->prepare('SELECT id FROM chat_messages WHERE listing_id=? AND buyer_id=? LIMIT 1');
                $chk->execute([$listingId,$buyerId]);
                $hasThread=(bool)$chk->fetchColumn();
                if(!$hasThread && !has_order_relationship($pdo,$listingId,$buyerId,$sellerId)) {
                    json_out(false,'ประกาศนี้ยังไม่เปิดให้เริ่มแชท',null,403);
                }
            }

            if($isSeller){
                $chk=$pdo->prepare('SELECT id FROM chat_messages WHERE listing_id=? AND buyer_id=? LIMIT 1');
                $chk->execute([$listingId,$buyerId]);
                $hasThread=(bool)$chk->fetchColumn();
                // V8 Order gives the seller a legitimate relationship with the
                // buyer even when neither side has sent the first chat message yet.
                if(!$hasThread && !has_order_relationship($pdo,$listingId,$buyerId,$sellerId)) {
                    json_out(false,'ไม่พบห้องสนทนา',null,404);
                }
            }

            // Opening an authorized room marks only messages sent TO the current
            // user as read. Messages sent by the current user are never counted.
            $mark=$pdo->prepare("UPDATE chat_messages SET read_at=NOW()
                WHERE listing_id=? AND buyer_id=? AND sender_id<>? AND read_at IS NULL");
            $mark->execute([$listingId,$buyerId,$uid]);

            $st=$pdo->prepare('SELECT id,sender_id,message,image_path,read_at,created_at FROM chat_messages WHERE listing_id=? AND buyer_id=? ORDER BY id ASC LIMIT 500');
            $st->execute([$listingId,$buyerId]);
            $messages=array_map(fn($row)=>chat_message_payload($config,$row),$st->fetchAll());
            json_out(true,'',$messages);

        case 'send_message':
            ensure_v10_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            enforce_rate_limit($pdo,'chat_text',$uid,1,'ส่งข้อความเร็วเกินไป กรุณารอสักครู่');
            $listingId=(int)post_value('listing_id'); $buyerId=(int)post_value('buyer_id'); $message=trim(post_value('message'));
            if($message==='' || mb_strlen($message)>1000) json_out(false,'ข้อความต้องมี 1–1000 ตัวอักษร',null,422);
            $st=$pdo->prepare('SELECT user_id,status FROM listings WHERE id=? LIMIT 1'); $st->execute([$listingId]); $listing=$st->fetch();
            if(!$listing) json_out(false,'ไม่พบประกาศ',null,404);
            $sellerId=(int)$listing['user_id'];
            $isSeller=$uid===$sellerId;

            if(!$isSeller){
                $buyerId=$uid;
                if(!in_array($listing['status'],['approved','sold'],true)){
                    $chk=$pdo->prepare('SELECT id FROM chat_messages WHERE listing_id=? AND buyer_id=? LIMIT 1');
                    $chk->execute([$listingId,$buyerId]);
                    $hasThread=(bool)$chk->fetchColumn();
                    if(!$hasThread && !has_order_relationship($pdo,$listingId,$buyerId,$sellerId)) {
                        json_out(false,'ประกาศนี้ยังไม่เปิดให้เริ่มแชท',null,403);
                    }
                }
            }

            if($buyerId<=0 || $buyerId===$sellerId) json_out(false,'ไม่พบคู่สนทนา',null,422);
            if($isSeller){
                $chk=$pdo->prepare('SELECT id FROM chat_messages WHERE listing_id=? AND buyer_id=? LIMIT 1');
                $chk->execute([$listingId,$buyerId]);
                $hasThread=(bool)$chk->fetchColumn();
                // V8 Order gives the seller a legitimate relationship with the
                // buyer even when neither side has sent the first chat message yet.
                if(!$hasThread && !has_order_relationship($pdo,$listingId,$buyerId,$sellerId)) {
                    json_out(false,'ไม่พบห้องสนทนา',null,404);
                }
            }
            $st=$pdo->prepare('INSERT INTO chat_messages(listing_id,buyer_id,sender_id,message) VALUES(?,?,?,?)');
            $st->execute([$listingId,$buyerId,$uid,$message]);
            $id=(int)$pdo->lastInsertId();

            $recipientId=$isSeller ? $buyerId : $sellerId;
            firebase_push_to_user($pdo,$recipientId,[
                'type'=>'chat',
                'title'=>'ข้อความใหม่จาก '.(string)($user['display_name'] ?: $user['username']),
                'body'=>mb_substr($message,0,180),
                'listing_id'=>(string)$listingId,
                'buyer_id'=>(string)$buyerId,
                'message_id'=>(string)$id,
                'recipient_user_id'=>(string)$recipientId,
            ]);
            json_out(true,'ส่งข้อความแล้ว',['id'=>$id]);


        case 'send_chat_image':
            ensure_v10_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            enforce_rate_limit($pdo,'chat_image',$uid,2,'ส่งรูปเร็วเกินไป กรุณารอสักครู่');
            $listingId=(int)post_value('listing_id'); $buyerId=(int)post_value('buyer_id');

            $st=$pdo->prepare('SELECT user_id,status FROM listings WHERE id=? LIMIT 1');
            $st->execute([$listingId]); $listing=$st->fetch();
            if(!$listing) json_out(false,'ไม่พบประกาศ',null,404);

            $sellerId=(int)$listing['user_id'];
            $isSeller=$uid===$sellerId;
            if(!$isSeller){
                $buyerId=$uid;
                if(!in_array($listing['status'],['approved','sold'],true)){
                    $chk=$pdo->prepare('SELECT id FROM chat_messages WHERE listing_id=? AND buyer_id=? LIMIT 1');
                    $chk->execute([$listingId,$buyerId]);
                    $hasThread=(bool)$chk->fetchColumn();
                    if(!$hasThread && !has_order_relationship($pdo,$listingId,$buyerId,$sellerId)) {
                        json_out(false,'ประกาศนี้ยังไม่เปิดให้เริ่มแชท',null,403);
                    }
                }
            }

            if($buyerId<=0 || $buyerId===$sellerId) json_out(false,'ไม่พบคู่สนทนา',null,422);
            if($isSeller){
                $chk=$pdo->prepare('SELECT id FROM chat_messages WHERE listing_id=? AND buyer_id=? LIMIT 1');
                $chk->execute([$listingId,$buyerId]);
                $hasThread=(bool)$chk->fetchColumn();
                // V8 Order gives the seller a legitimate relationship with the
                // buyer even when neither side has sent the first chat message yet.
                if(!$hasThread && !has_order_relationship($pdo,$listingId,$buyerId,$sellerId)) {
                    json_out(false,'ไม่พบห้องสนทนา',null,404);
                }
            }

            $imagePath=save_chat_image($config,$_FILES['image']??[]);
            try {
                $st=$pdo->prepare("INSERT INTO chat_messages(listing_id,buyer_id,sender_id,message,image_path) VALUES(?,?,?,'',?)");
                $st->execute([$listingId,$buyerId,$uid,$imagePath]);
                $id=(int)$pdo->lastInsertId();
            } catch (Throwable $e) {
                @unlink(dirname(__DIR__).'/'.$imagePath);
                throw $e;
            }

            $recipientId=$isSeller ? $buyerId : $sellerId;
            firebase_push_to_user($pdo,$recipientId,[
                'type'=>'chat',
                'title'=>'ข้อความใหม่จาก '.(string)($user['display_name'] ?: $user['username']),
                'body'=>'📷 ส่งรูปภาพ',
                'listing_id'=>(string)$listingId,
                'buyer_id'=>(string)$buyerId,
                'message_id'=>(string)$id,
                'recipient_user_id'=>(string)$recipientId,
            ]);
            json_out(true,'ส่งรูปแล้ว',['id'=>$id]);

        case 'register_push_token':
            ensure_v5_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo);
            $token=post_value('token');
            if($token==='' || strlen($token)<20 || strlen($token)>512) json_out(false,'Push token ไม่ถูกต้อง',null,422);
            $st=$pdo->prepare("INSERT INTO push_tokens(user_id,token) VALUES(?,?)
                ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),updated_at=NOW()");
            $st->execute([(int)$user['id'],$token]);
            json_out(true,'ลงทะเบียนแจ้งเตือนแล้ว',['id'=>(int)$user['id']]);

        case 'unregister_push_token':
            ensure_v5_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo);
            $token=post_value('token');
            if($token!==''){
                $st=$pdo->prepare('DELETE FROM push_tokens WHERE user_id=? AND token=?');
                $st->execute([(int)$user['id'],$token]);
            }
            json_out(true,'ยกเลิกแจ้งเตือนเครื่องนี้แล้ว',['id'=>(int)$user['id']]);

        case 'toggle_favorite':
            ensure_v5_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $listingId=(int)post_value('listing_id');
            $st=$pdo->prepare("SELECT id FROM listings WHERE id=? AND status IN ('approved','sold') LIMIT 1");
            $st->execute([$listingId]);
            if(!$st->fetch()) json_out(false,'ไม่พบประกาศ',null,404);

            $check=$pdo->prepare('SELECT 1 FROM favorites WHERE user_id=? AND listing_id=? LIMIT 1');
            $check->execute([$uid,$listingId]);
            if($check->fetchColumn()){
                $pdo->prepare('DELETE FROM favorites WHERE user_id=? AND listing_id=?')->execute([$uid,$listingId]);
                $favorite=false;
            } else {
                $pdo->prepare('INSERT IGNORE INTO favorites(user_id,listing_id) VALUES(?,?)')->execute([$uid,$listingId]);
                $favorite=true;
            }
            json_out(true,$favorite?'บันทึกไว้แล้ว':'นำออกจากรายการสนใจแล้ว',['is_favorite'=>$favorite]);

        case 'chat_latest_id':
            ensure_v10_schema($pdo);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $sql="SELECT COALESCE(MAX(cm.id),0) latest_id
                FROM chat_messages cm
                JOIN listings l ON l.id=cm.listing_id
                WHERE cm.sender_id<>? AND (cm.buyer_id=? OR l.user_id=?)";
            $st=$pdo->prepare($sql); $st->execute([$uid,$uid,$uid]);
            $row=$st->fetch();
            json_out(true,'',['latest_id'=>(int)($row['latest_id']??0)]);

        case 'chat_updates':
            ensure_v10_schema($pdo);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            $afterId=max(0,(int)($_GET['after_id']??0));
            $sql="SELECT cm.id,cm.listing_id,cm.buyer_id,cm.sender_id,
                    COALESCE(NULLIF(sender.display_name,''),sender.username) sender_username,l.title,
                    CASE WHEN COALESCE(cm.message,'')<>'' THEN cm.message
                         WHEN cm.image_path IS NOT NULL THEN '📷 รูปภาพ'
                         ELSE '' END message,
                    cm.created_at
                FROM chat_messages cm
                JOIN listings l ON l.id=cm.listing_id
                JOIN users sender ON sender.id=cm.sender_id
                WHERE cm.id>? AND cm.sender_id<>? AND (cm.buyer_id=? OR l.user_id=?)
                ORDER BY cm.id ASC
                LIMIT 50";
            $st=$pdo->prepare($sql); $st->execute([$afterId,$uid,$uid,$uid]);
            json_out(true,'',$st->fetchAll());

        case 'report_user':
            ensure_v7_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $uid=(int)$user['id'];
            enforce_rate_limit($pdo,'report_user',$uid,10,'ส่งรายงานเร็วเกินไป กรุณารอสักครู่');
            $listingId=(int)post_value('listing_id');
            $reportedUserId=(int)post_value('reported_user_id');
            $category=post_value('category');
            $details=mb_substr(trim(post_value('details')),0,1000);
            $allowedCategories=['fraud','payment','inappropriate','fake_listing','other'];
            if(!in_array($category,$allowedCategories,true)) $category='other';
            if(mb_strlen($details)<5) json_out(false,'กรุณาระบุรายละเอียดอย่างน้อย 5 ตัวอักษร',null,422);
            if($reportedUserId===$uid) json_out(false,'ไม่สามารถรายงานบัญชีของตัวเองได้',null,422);

            if($listingId>0){
                $st=$pdo->prepare('SELECT user_id FROM listings WHERE id=? LIMIT 1');
                $st->execute([$listingId]); $listingOwner=(int)$st->fetchColumn();
                if($listingOwner<=0) json_out(false,'ไม่พบประกาศ',null,404);
                if($reportedUserId<=0) $reportedUserId=$listingOwner;
                if($reportedUserId!==$listingOwner) json_out(false,'ข้อมูลผู้ถูกรายงานไม่ตรงกับประกาศ',null,422);
            }
            if($reportedUserId<=0) json_out(false,'ไม่พบผู้ใช้ที่ต้องการรายงาน',null,422);
            $st=$pdo->prepare('SELECT id FROM users WHERE id=? LIMIT 1'); $st->execute([$reportedUserId]);
            if(!$st->fetchColumn()) json_out(false,'ไม่พบผู้ใช้ที่ต้องการรายงาน',null,404);

            $st=$pdo->prepare("INSERT INTO user_reports(reporter_user_id,reported_user_id,listing_id,category,details)
                VALUES(?,?,?,?,?)");
            $st->execute([$uid,$reportedUserId,$listingId?:null,$category,$details]);
            $reportId=(int)$pdo->lastInsertId();
            try {
                admin_notification_create(
                    $pdo,'report_submitted','มีรายงานใหม่',
                    'สมาชิก '.(string)$user['username'].' ส่งรายงานประเภท '.$category,
                    $uid,'report',$reportId,
                    'reports.php?status=open','admin/reports',
                    'report:'.$reportId
                );
            } catch(Throwable $notifyError) { error_log($notifyError->getMessage()); }
            json_out(true,'ส่งเรื่องให้แอดมินตรวจสอบแล้ว',['id'=>$reportId]);


        // V10 Native Admin API. Every case below authenticates the Bearer token
        // and re-checks users.role on the server before reading Admin data.
        case 'admin_dashboard':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo); $adminId=(int)$admin['id'];
            json_out(true,'',admin_pending_counts($pdo,$adminId));

        case 'admin_pending_count':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo); $adminId=(int)$admin['id'];
            json_out(true,'',admin_pending_counts($pdo,$adminId));

        case 'admin_notifications':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo); $adminId=(int)$admin['id'];
            $unreadOnly=(string)($_GET['unread']??'')==='1';
            json_out(true,'',admin_notification_list($pdo,$adminId,200,$unreadOnly));

        case 'admin_notification_read':
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo); $adminId=(int)$admin['id'];
            $notificationId=(int)post_value('notification_id');
            if($notificationId<=0) json_out(false,'ไม่พบ Notification',null,422);
            admin_mark_notification_read($pdo,$adminId,$notificationId);
            json_out(true,'อ่านแล้ว',['id'=>$notificationId,'unread_count'=>admin_unread_count($pdo,$adminId)]);

        case 'admin_notification_read_all':
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo); $adminId=(int)$admin['id'];
            admin_mark_all_notifications_read($pdo,$adminId);
            json_out(true,'อ่านทั้งหมดแล้ว',['unread_count'=>0]);

        case 'admin_topups':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo);
            $status=(string)($_GET['status']??'pending');
            $allowed=['pending','approved','rejected','all'];
            if(!in_array($status,$allowed,true)) $status='pending';
            $where=$status==='all'?'1=1':'r.status=?';
            $params=$status==='all'?[]:[$status];
            $st=$pdo->prepare("SELECT r.id,r.user_id,u.username,u.email,r.points,r.amount,r.note,
                    r.status,r.created_at,r.reviewed_at,r.reviewed_by,
                    CASE WHEN r.slip_path IS NULL OR r.slip_path='' THEN 0 ELSE 1 END has_slip
                FROM point_topup_requests r
                JOIN users u ON u.id=r.user_id
                WHERE $where ORDER BY r.id DESC LIMIT 300");
            $st->execute($params);
            $rows=array_map(static function(array $row): array {
                $row['id']=(int)$row['id'];
                $row['user_id']=(int)$row['user_id'];
                $row['points']=(int)$row['points'];
                $row['amount']=(float)$row['amount'];
                $row['has_slip']=(bool)$row['has_slip'];
                return $row;
            },$st->fetchAll());
            json_out(true,'',$rows);

        case 'admin_review_topup':
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo); $adminId=(int)$admin['id'];
            $requestId=(int)post_value('id');
            $decision=post_value('decision');
            $req=admin_review_topup($pdo,$adminId,$requestId,$decision);
            json_out(true,$decision==='approved'?'อนุมัติสลิปและเพิ่มแต้มแล้ว':'ปฏิเสธคำขอแล้ว',[
                'id'=>$requestId,'status'=>$req['status'],'unread_count'=>admin_unread_count($pdo,$adminId)
            ]);

        case 'admin_verifications':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo);
            $status=(string)($_GET['status']??'pending');
            $allowed=['pending','verified','rejected','all'];
            if(!in_array($status,$allowed,true)) $status='pending';
            $where=$status==='all'?'1=1':'iv.status=?';
            $params=$status==='all'?[]:[$status];
            $st=$pdo->prepare("SELECT iv.user_id,u.username,u.email,iv.bank_name,iv.account_name,
                    iv.account_number,iv.status,iv.rejection_reason,iv.submitted_at,iv.reviewed_at,
                    iv.verified_at,CASE WHEN iv.document_path='' THEN 0 ELSE 1 END has_document
                FROM identity_verifications iv
                JOIN users u ON u.id=iv.user_id
                WHERE $where
                ORDER BY CASE iv.status WHEN 'pending' THEN 0 WHEN 'rejected' THEN 1 ELSE 2 END,
                    iv.submitted_at DESC LIMIT 300");
            $st->execute($params);
            $rows=array_map(static function(array $row): array {
                $row['user_id']=(int)$row['user_id'];
                $row['has_document']=(bool)$row['has_document'];
                return $row;
            },$st->fetchAll());
            json_out(true,'',$rows);

        case 'admin_review_verification':
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo); $adminId=(int)$admin['id'];
            $userId=(int)post_value('user_id');
            $decision=post_value('decision');
            $reason=post_value('rejection_reason');
            $result=admin_review_verification($pdo,$adminId,$userId,$decision,$reason);
            json_out(true,$decision==='approved'?'อนุมัติการยืนยันตัวตนแล้ว':'ปฏิเสธคำขอแล้ว',[
                'id'=>$userId,'status'=>$result['status']??$decision,
                'unread_count'=>admin_unread_count($pdo,$adminId)
            ]);

        case 'admin_listings':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo);
            $status=(string)($_GET['status']??'pending');
            $allowed=['pending','approved','hidden','rejected','sold','all'];
            if(!in_array($status,$allowed,true)) $status='pending';
            $where=$status==='all'?'1=1':'l.status=?';
            $params=$status==='all'?[]:[$status];
            $st=$pdo->prepare("SELECT l.id,l.user_id,l.title,l.description,l.price,l.province,l.amphoe,l.tambon,
                    l.status,l.created_at,l.approved_at,u.username,
                    (SELECT file_path FROM listing_images WHERE listing_id=l.id ORDER BY sort_order,id LIMIT 1) cover
                FROM listings l JOIN users u ON u.id=l.user_id
                WHERE $where ORDER BY l.id DESC LIMIT 300");
            $st->execute($params);
            $rows=array_map(function(array $row) use ($config): array {
                $row['id']=(int)$row['id'];
                $row['user_id']=(int)$row['user_id'];
                $row['price']=(float)$row['price'];
                $row['cover_url']=!empty($row['cover'])?image_url($config,(string)$row['cover']):null;
                unset($row['cover']);
                return $row;
            },$st->fetchAll());
            json_out(true,'',$rows);

        case 'admin_update_listing':
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo); $adminId=(int)$admin['id'];
            $listingId=(int)post_value('id');
            $status=post_value('status');
            admin_update_listing_status($pdo,$adminId,$listingId,$status);
            json_out(true,'อัปเดตสถานะประกาศแล้ว',[
                'id'=>$listingId,'status'=>$status,'unread_count'=>admin_unread_count($pdo,$adminId)
            ]);

        case 'admin_reports':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo);
            $status=(string)($_GET['status']??'open');
            $allowed=['open','reviewing','resolved','dismissed','all'];
            if(!in_array($status,$allowed,true)) $status='open';
            $where=$status==='all'?'1=1':'r.status=?';
            $params=$status==='all'?[]:[$status];
            $st=$pdo->prepare("SELECT r.id,r.reporter_user_id,r.reported_user_id,r.listing_id,r.category,
                    r.details,r.status,r.admin_note,r.created_at,r.updated_at,
                    reporter.username reporter_name,reported.username reported_name,
                    reported.status reported_status,l.title listing_title
                FROM user_reports r
                JOIN users reporter ON reporter.id=r.reporter_user_id
                LEFT JOIN users reported ON reported.id=r.reported_user_id
                LEFT JOIN listings l ON l.id=r.listing_id
                WHERE $where ORDER BY r.id DESC LIMIT 300");
            $st->execute($params);
            $rows=array_map(static function(array $row): array {
                foreach(['id','reporter_user_id','reported_user_id','listing_id'] as $k){
                    $row[$k]=$row[$k]!==null?(int)$row[$k]:null;
                }
                return $row;
            },$st->fetchAll());
            json_out(true,'',$rows);

        case 'admin_update_report':
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo); $adminId=(int)$admin['id'];
            $reportId=(int)post_value('id');
            $reportAction=post_value('report_action');
            $note=post_value('admin_note');
            admin_update_report($pdo,$adminId,$reportId,$reportAction,$note);
            json_out(true,'อัปเดตรายงานแล้ว',[
                'id'=>$reportId,'unread_count'=>admin_unread_count($pdo,$adminId)
            ]);

        case 'admin_orders':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo);
            $status=(string)($_GET['status']??'');
            $allowed=['pending_confirmation','preparing','shipped','completed','cancelled'];
            $where=in_array($status,$allowed,true)?'WHERE o.status=?':'';
            $params=$where!==''?[$status]:[];
            $st=$pdo->prepare("SELECT o.order_id,o.listing_id,o.buyer_id,o.seller_id,o.price_snapshot price,
                    o.title_snapshot title,o.recipient_name,o.phone,o.house_no_moo,o.soi,o.road,
                    o.subdistrict,o.district,o.province,o.postal_code,o.note,o.status,o.tracking_number,
                    o.created_at,o.updated_at,
                    COALESCE(NULLIF(buyer.display_name,''),buyer.username) buyer_username,
                    COALESCE(NULLIF(seller.display_name,''),seller.username) seller_username
                FROM orders o
                JOIN users buyer ON buyer.id=o.buyer_id
                JOIN users seller ON seller.id=o.seller_id
                $where ORDER BY o.order_id DESC LIMIT 300");
            $st->execute($params);
            $rows=array_map(static function(array $row): array {
                foreach(['order_id','listing_id','buyer_id','seller_id'] as $k) $row[$k]=(int)$row[$k];
                $row['price']=(float)$row['price'];
                return $row;
            },$st->fetchAll());
            json_out(true,'',$rows);

        case 'admin_users':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo);
            $q=trim((string)($_GET['q']??''));
            $sql="SELECT u.id,u.username,u.display_name,u.admin_stars,u.special_icon,u.email,u.phone,u.line_id,u.role,u.status,u.created_at,
                    COALESCE(pw.balance,0) points_balance,
                    (SELECT COUNT(*) FROM listings l WHERE l.user_id=u.id) listing_count,
                    (SELECT r.id FROM display_name_change_requests r WHERE r.user_id=u.id AND r.status='pending' ORDER BY r.id DESC LIMIT 1) pending_display_name_request_id,
                    (SELECT r.requested_name FROM display_name_change_requests r WHERE r.user_id=u.id AND r.status='pending' ORDER BY r.id DESC LIMIT 1) pending_display_name,
                    (SELECT r.reason FROM display_name_change_requests r WHERE r.user_id=u.id AND r.status='pending' ORDER BY r.id DESC LIMIT 1) pending_display_name_reason
                FROM users u
                LEFT JOIN point_wallets pw ON pw.user_id=u.id";
            $params=[];
            if($q!==''){
                $sql.=" WHERE u.username LIKE ? OR u.display_name LIKE ? OR u.email LIKE ?";
                $params=["%$q%","%$q%","%$q%"];
            }
            $sql.=" ORDER BY u.id DESC LIMIT 300";
            $st=$pdo->prepare($sql); $st->execute($params);
            $rows=array_map(static function(array $row): array {
                $row['id']=(int)$row['id'];
                $row['listing_count']=(int)$row['listing_count'];
                $row['points_balance']=(int)$row['points_balance'];
                $row['admin_stars']=(int)($row['admin_stars']??0);
                $row['display_name']=(string)(($row['display_name']??'') ?: $row['username']);
                $row['pending_display_name_request_id']=!empty($row['pending_display_name_request_id'])?(int)$row['pending_display_name_request_id']:null;
                $row['is_admin']=$row['role']==='admin';
                return $row;
            },$st->fetchAll());
            json_out(true,'',$rows);

        case 'admin_update_user':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $id=(int)post_value('id');
            $displayRaw=trim(post_value('display_name'));
            $displayName=$displayRaw!=='' ? normalize_display_name($displayRaw) : null;
            $stars=max(0,min(5,(int)post_value('admin_stars')));
            $specialIcon=mb_substr(trim(post_value('special_icon')),0,16);
            if(preg_match('/[\r\n<>]/u',$specialIcon)) json_out(false,'ไอคอนพิเศษไม่ถูกต้อง',null,422);
            $pointsDelta=(int)post_value('points_delta');
            $role=post_value('role');
            $status=post_value('status');
            if(!in_array($role,['member','admin'],true)) $role='member';
            if(!in_array($status,['active','suspended'],true)) $status='active';
            if($id===(int)$admin['id'] && ($role!=='admin' || $status!=='active')) {
                json_out(false,'ไม่สามารถลดสิทธิ์หรือระงับบัญชีแอดมินที่กำลังใช้งาน',null,422);
            }
            if(abs($pointsDelta)>1000000) json_out(false,'จำนวนแต้มที่ปรับมากเกินไป',null,422);
            $check=$pdo->prepare("SELECT id FROM users WHERE id=? LIMIT 1"); $check->execute([$id]);
            if(!$check->fetchColumn()) json_out(false,'ไม่พบสมาชิก',null,404);

            $pdo->prepare("UPDATE users SET display_name=?,admin_stars=?,special_icon=?,role=?,status=? WHERE id=?")
                ->execute([$displayName,$stars,$specialIcon?:null,$role,$status,$id]);
            $balance=null;
            if($pointsDelta!==0){
                $balance=admin_adjust_points($pdo,$id,$pointsDelta,'แอดมินปรับแต้มจากหน้าสมาชิก',(int)$admin['id']);
            }
            json_out(true,'อัปเดตสมาชิกแล้ว',[
                'id'=>$id,'status'=>$status,'balance'=>$balance
            ]);

        case 'admin_review_display_name':
            $admin=require_api_admin($pdo);
            ensure_v10_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $requestId=(int)post_value('request_id');
            $decision=post_value('decision');
            $adminNote=mb_substr(trim(post_value('admin_note')),0,500);
            if(!in_array($decision,['approved','rejected'],true)) json_out(false,'คำสั่งไม่ถูกต้อง',null,422);

            $pdo->beginTransaction();
            try {
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
                    ->execute([$decision,(int)$admin['id'],$adminNote?:null,$requestId]);
                $pdo->commit();
            } catch(Throwable $e){
                if($pdo->inTransaction()) $pdo->rollBack();
                throw $e;
            }
            try {
                firebase_push_to_user($pdo,(int)$request['user_id'],[
                    'type'=>'account','title'=>$decision==='approved'?'อนุมัติเปลี่ยนชื่อแล้ว':'คำขอเปลี่ยนชื่อไม่ผ่าน',
                    'body'=>$decision==='approved'?'ชื่อที่แสดงของคุณได้รับการอนุมัติแล้ว':'แอดมินไม่อนุมัติคำขอเปลี่ยนชื่อ'.($adminNote!==''?': '.$adminNote:''),
                    'recipient_user_id'=>(string)$request['user_id']
                ]);
            } catch(Throwable $pushError) { error_log($pushError->getMessage()); }
            json_out(true,$decision==='approved'?'อนุมัติเปลี่ยนชื่อแล้ว':'ปฏิเสธคำขอเปลี่ยนชื่อแล้ว',[
                'id'=>$requestId,'status'=>$decision
            ]);

        case 'delete_listing':
            ensure_v5_schema($pdo);
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $id=(int)post_value('id');
            $stmt=$pdo->prepare('SELECT l.id,i.file_path FROM listings l LEFT JOIN listing_images i ON i.listing_id=l.id WHERE l.id=? AND l.user_id=?');
            $stmt->execute([$id,$user['id']]); $rows=$stmt->fetchAll();
            if (!$rows) json_out(false,'ไม่พบประกาศของคุณ',null,404);

            ensure_v9_schema($pdo);
            $orderCheck=$pdo->prepare('SELECT order_id FROM orders WHERE listing_id=? LIMIT 1');
            $orderCheck->execute([$id]);
            if($orderCheck->fetchColumn()) json_out(false,'ประกาศที่มีประวัติคำสั่งซื้อไม่สามารถลบได้ กรุณาเปลี่ยนสถานะแทน',null,409);

            $chatFiles=$pdo->prepare('SELECT image_path FROM chat_messages WHERE listing_id=? AND image_path IS NOT NULL');
            $chatFiles->execute([$id]);
            $chatImages=$chatFiles->fetchAll();

            $pdo->prepare('DELETE FROM listings WHERE id=? AND user_id=?')->execute([$id,$user['id']]);
            foreach ($rows as $r) if ($r['file_path']) @unlink(dirname(__DIR__).'/'.$r['file_path']);
            foreach ($chatImages as $r) if ($r['image_path']) @unlink(dirname(__DIR__).'/'.$r['image_path']);
            json_out(true,'ลบประกาศแล้ว',['id'=>$id]);

        case 'mark_sold':
            if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_out(false,'Method not allowed',null,405);
            $user=require_api_user($pdo); $id=(int)post_value('id');
            ensure_v9_schema($pdo);
            $stmt=$pdo->prepare("UPDATE listings SET status='sold' WHERE id=? AND user_id=? AND status='approved'
                AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.listing_id=listings.id AND o.status IN ('pending_confirmation','preparing','shipped'))");
            $stmt->execute([$id,$user['id']]);
            if (!$stmt->rowCount()) json_out(false,'เปลี่ยนสถานะไม่ได้',null,422);
            json_out(true,'เปลี่ยนเป็นขายแล้ว',['id'=>$id]);

        default: json_out(false,'ไม่พบ API action',null,404);
    }
} catch (Throwable $e) {
    error_log($e->getMessage());
    json_out(false, $e instanceof RuntimeException ? $e->getMessage() : 'เกิดข้อผิดพลาดในระบบ', null, 500);
}
