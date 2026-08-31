# แอป Android — ตลาดพระออนไลน์ V14

Package: `com.khaiphraban.marketplace`

เวอร์ชัน V14:
- `versionCode 13`
- `versionName V14.0`

## เปิดและ Build
1. เปิด Android Studio
2. เลือก **Open**
3. เปิดโฟลเดอร์ `KhaiPhraBan2/android-app`
4. รอ Gradle Sync ให้เสร็จ
5. ตรวจ API base URL ให้ชี้ Server ที่ติดตั้ง backend จากโปรเจกต์ชุดนี้
6. ตรวจ `app/google-services.json` ให้เป็น Firebase project ของ production
7. Build APK/App Bundle ตาม workflow เดิม

ครั้งแรกที่ Gradle ยังไม่มี dependency ใน cache จำเป็นต้องมีอินเทอร์เน็ต

## V14
- แก้การตั้งชื่อภาษาไทยให้รองรับสระ/วรรณยุกต์ Unicode (`\p{M}`) และแสดง validation ภายในหน้าต่างตั้งชื่อ
- เพิ่มหน้า **ร่วมสนุกลุ้นพระ** จากหน้า “ของฉัน”
- สมาชิกใช้แต้มเดิมเลือกเลข 00–99 โดยเลขเดียวกันมีเจ้าของได้เพียงบัญชีเดียวต่อรอบ
- หน้าเลขแสดงเลขที่ถูกซื้อแล้ว เลขของสมาชิก แต้มคงเหลือ รางวัล วันที่งวด และผลผู้ชนะ
- ซื้อเลขใช้ request key + Database transaction เพื่อป้องกันการหักแต้มซ้ำเมื่อ network retry
- Admin เว็บเพิ่มเมนู **ลุ้นพระ** สำหรับสร้างรางวัล ตั้งแต้ม/เลข เปิด-ปิดรับเลข และประกาศเลขผู้ชนะ
- Database V14 เพิ่มแบบ additive ผ่าน `backend/database/migration_v14.sql`; ไม่แก้ `migration_v13.sql`

## V13
- หน้าสมัครสมาชิกเพิ่มจังหวัด / อำเภอ / ตำบล และบันทึกไว้ในบัญชีสมาชิก
- หน้าโพสต์ใช้ที่อยู่จากบัญชีอัตโนมัติ ไม่ต้องเลือกซ้ำ
- บัญชีเก่าจาก V12 ที่ยังไม่มีที่อยู่จะเลือกเพียงครั้งเดียวตอนโพสต์แรกหลังอัปเดต แล้ว Backend บันทึกไว้ในบัญชี
- หน้ารายละเอียดประกาศมีหัวข้อ **เผื่อคุณสนใจ** โดยใช้รายการ `random` จาก API `home` เดิม
- Admin > แจ้งเตือน ตั้ง Push สมาชิกอัตโนมัติได้ทั้งจำนวนรอบต่อวัน (1–6) และช่วงเวลาสูงสุด 3 ช่วง
- Push อัตโนมัติ V13 ใช้ Firebase + Cron + `scheduled_notifications` เดิม และปิด local random reminder ของ V12 เพื่อไม่ให้แจ้งเตือนซ้ำ
- Database เพิ่มเฉพาะแบบ additive ผ่าน `backend/database/migration_v13.sql`
- Order notification ยังใช้เสียง `money_in.wav` และ channel `khaiphraban_orders_v12` เดิมเพื่อรักษาพฤติกรรมเสียงของ Android

## ระบบเดิมที่ยังคงไว้
- Premium / Boost / Wallet
- Chat + รูป + Push
- Order / สลิป / COD / Rating
- Favorite
- Admin mobile + Admin notification
- Verification
- Report
- Home content / Banner / Announcement
- Firebase token registration
- Login และบัญชีเดิม

## Firebase / Cron
- ใช้ Firebase Cloud Messaging
- `app/google-services.json` อยู่ในโปรเจกต์ตามระบบเดิม
- หลังล็อกอิน แอปลงทะเบียน push token กับ backend
- ตั้ง Cron ให้รัน `php backend/cron/notifications.php` ทุก 1 นาที
- Admin ตั้งค่าจำนวนรอบและช่วงเวลาได้ที่ **Admin > แจ้งเตือน**

## ขั้นตอนอัปเกรด V13 -> V14
1. สำรองฐานข้อมูลตาม workflow เดิม
2. อัปโหลด Backend V14 ทับไฟล์เดิม
3. เข้า **Admin > อัปเดตระบบ**
4. กด **ตรวจและอัปเดต V14**
5. ตรวจว่า Database V14 ขึ้นพร้อม และเปิด **Admin > ลุ้นพระ**
6. สร้างรอบแบบฉบับร่าง ตรวจรางวัล/แต้ม/วันที่ แล้วจึงกดเปิดรับเลข
7. Build Android V14 ใหม่จาก source ชุดนี้
