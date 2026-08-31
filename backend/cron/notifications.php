<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') { http_response_code(403); exit("CLI only\n"); }
require_once dirname(__DIR__).'/includes/bootstrap.php';
ensure_v13_schema($pdo);

/**
 * V13: plan today's automatic member reminders once, using the existing
 * scheduled_notifications + Firebase sender. A DB row lock prevents duplicate
 * planning if two Cron processes overlap.
 */
function plan_v13_member_push(PDO $pdo): void
{
    $today=date('Y-m-d');
    $now=time();

    $reminders=[
        ['มีคนลงพระใหม่ เผื่อคุณสนใจ','แวะดูประกาศใหม่ล่าสุดได้เลย อาจมีองค์ที่คุณกำลังตามหา'],
        ['วันนี้คุณลงขายพระหรือยัง?','มีพระที่อยากปล่อยอยู่ไหม ลงประกาศได้ง่าย ๆ ในไม่กี่ขั้นตอน'],
        ['พระใหม่เข้ามาแล้ว','เปิดดูรายการล่าสุดจากสมาชิกในตลาดพระออนไลน์'],
        ['เผื่อเจอองค์ที่ถูกใจ','วันนี้มีพระหลายรายการน่าสนใจ ลองแวะเข้ามาดูสักนิด'],
        ['อย่าพลาดประกาศใหม่','สมาชิกกำลังลงพระเพิ่มเรื่อย ๆ แตะเพื่อดูรายการล่าสุด'],
        ['มีพระอยู่ในมือ อย่าเก็บไว้เฉย ๆ','ลองลงขายวันนี้ เพิ่มโอกาสให้คนที่กำลังตามหาได้เห็น'],
        ['แวะมาดูพระกันสักหน่อย','ตลาดมีรายการใหม่ให้ชม แตะเพื่อเปิดแอป'],
        ['องค์ที่คุณตามหาอาจมาแล้ว','ลองเช็กพระมาใหม่วันนี้ เผื่อเจอรุ่นหรือพิมพ์ที่กำลังหา'],
        ['ลงประกาศฟรี ใช้เวลาไม่นาน','ถ้ามีพระอยากขาย วันนี้เป็นอีกวันที่ดีสำหรับการลงประกาศ'],
        ['มีอะไรใหม่ในตลาดพระออนไลน์?','แตะเข้ามาดูประกาศล่าสุดจากสมาชิกได้เลย'],
        ['วันนี้แวะเข้าตลาดหรือยัง?','พระใหม่ ๆ กำลังรอให้คุณเข้ามาชม'],
        ['โอกาสดี ๆ อาจอยู่ในประกาศล่าสุด','ลองเปิดดูรายการใหม่ เผื่อเจอพระที่เหมาะกับคุณ'],
    ];

    try {
        $pdo->beginTransaction();
        $st=$pdo->query("SELECT * FROM member_push_settings WHERE id=1 FOR UPDATE");
        $settings=$st->fetch();
        if(!$settings || (int)$settings['enabled']!==1 || (string)($settings['last_planned_date']??'')===$today){
            $pdo->commit();
            return;
        }

        $windows=[];
        for($i=1;$i<=3;$i++){
            $start=trim((string)($settings['window'.$i.'_start']??''));
            $end=trim((string)($settings['window'.$i.'_end']??''));
            if($start==='' || $end==='') continue;
            $startTs=strtotime($today.' '.$start);
            $endTs=strtotime($today.' '.$end);
            if($startTs===false || $endTs===false || $endTs<=$startTs) continue;
            $windows[]=[$startTs,$endTs];
        }

        $slots=[];
        foreach($windows as [$startTs,$endTs]){
            // Keep at least a 2-minute margin so the row is committed before it is due.
            $from=max($startTs,(int)(ceil(($now+120)/60)*60));
            for($t=$from;$t<=$endTs;$t+=60){
                $slots[$t]=$t; // key also deduplicates overlapping windows
            }
        }

        $dailyCount=max(1,min(6,(int)$settings['daily_count']));
        $slotValues=array_values($slots);
        if($slotValues){
            shuffle($slotValues);
            $picked=array_slice($slotValues,0,min($dailyCount,count($slotValues)));
            sort($picked);

            $insert=$pdo->prepare("INSERT INTO scheduled_notifications
                (title,body,source,scheduled_at,created_by)
                VALUES(?,?,'auto_member_v13',?,NULL)");
            foreach($picked as $ts){
                $message=$reminders[array_rand($reminders)];
                $insert->execute([$message[0],$message[1],date('Y-m-d H:i:s',$ts)]);
            }
        }

        // Mark today as planned even if deployment/Cron starts after all windows.
        // The next day will plan normally from the configured windows.
        $up=$pdo->prepare("UPDATE member_push_settings SET last_planned_date=? WHERE id=1");
        $up->execute([$today]);
        $pdo->commit();

        echo "planned V13 member push: ".count($slotValues ? ($picked ?? []) : [])." reminder(s)\n";
    } catch(Throwable $e){
        if($pdo->inTransaction()) $pdo->rollBack();
        fwrite(STDERR,"V13 planner failed: ".$e->getMessage()."\n");
    }
}

plan_v13_member_push($pdo);

$rows=$pdo->query("SELECT * FROM scheduled_notifications
    WHERE status='pending' AND scheduled_at<=NOW()
    ORDER BY scheduled_at,id LIMIT 20")->fetchAll();

foreach($rows as $row){
    $id=(int)$row['id'];
    try {
        $pdo->beginTransaction();
        $st=$pdo->prepare("SELECT status FROM scheduled_notifications WHERE id=? FOR UPDATE");
        $st->execute([$id]);
        if($st->fetchColumn()!=='pending'){ $pdo->rollBack(); continue; }
        $pdo->prepare("UPDATE scheduled_notifications SET status='sent',sent_at=NOW() WHERE id=?")->execute([$id]);
        $pdo->commit();

        $sent=firebase_push_all_active($pdo,[
            'type'=>'admin_notification',
            'title'=>(string)$row['title'],
            'body'=>(string)$row['body'],
        ]);
        $pdo->prepare("UPDATE scheduled_notifications SET sent_count=? WHERE id=?")->execute([$sent,$id]);
        echo "sent #$id to $sent devices\n";
    } catch(Throwable $e){
        if($pdo->inTransaction()) $pdo->rollBack();
        $pdo->prepare("UPDATE scheduled_notifications SET status='failed',error_message=? WHERE id=?")
            ->execute([mb_substr($e->getMessage(),0,500),$id]);
        fwrite(STDERR,"failed #$id: ".$e->getMessage()."\n");
    }
}
