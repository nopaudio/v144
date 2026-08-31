ตลาดพระออนไลน์ — V8
=================================
ต่อยอดจาก FIXED V7 โดยคงโครงสร้าง Android + PHP API + Admin + MySQL เดิมไว้
และเพิ่มเฉพาะส่วนที่จำเป็นสำหรับ Premium/Boost ที่ทำงานจริงและระบบ “ซื้อเลย”

สรุปสาเหตุปัญหา V7
-------------------
1) Premium แสดงช้าหลายชั่วโมง
- V7 ตั้ง PHP เป็น Asia/Bangkok แต่ connection MySQL ไม่ได้ตั้ง session time_zone
- ตอนซื้อ V7 สร้าง starts_at / ends_at ด้วย PHP date() (เวลาไทย)
- แต่ตอนตรวจว่า Premium ใช้งานอยู่หรือไม่ ใช้ MySQL NOW()
- ถ้า MySQL server/session เป็น UTC เวลาจะต่างจากไทยประมาณ 7 ชั่วโมง
  จึงเกิดอาการซื้อช่วงบ่าย แต่เพิ่งเข้าเงื่อนไข Premium ตอนค่ำ
- V8 แก้ที่ต้นเหตุ: ทุก PDO connection ตั้ง MySQL session timezone ให้ตรงกับ
  app timezone และการซื้อ Premium ใช้ MySQL NOW()/DATE_ADD() ทั้งหมดใน transaction เดียว

2) “ดันโพสต์” ใน V7 ไม่ใช่ระบบ Boost จริง
- V7 ไม่มี purchase_boost API
- ไม่มี listings.boosted_at
- ไม่มี listing_boosts history
- query หน้าแรกไม่ได้เรียงตามเวลา Boost
- แพ็กเกจชื่อ “ดันเด่น 1 วัน” ของ V7 เป็น premium plan เท่านั้น
- V8 เพิ่ม Boost จริงแยกจาก Premium และเปลี่ยนชื่อ default plan เดิมเป็น
  “พรีเมียมเด่น 1 วัน” เพื่อไม่ให้สับสน

สิ่งที่แก้/เพิ่มใน V8
----------------------
Premium
- ตรวจแต้มจาก Server
- lock ประกาศและ wallet
- หักแต้ม + เพิ่ม premium promotion + point ledger ใน DB transaction เดียว
- request_key ป้องกัน retry เดิมหักแต้มซ้ำ และ Android จะ reuse key เดิมเมื่อ request ล้มเหลว/ตอบกลับหาย
- starts_at/ends_at มาจาก MySQL clock เดียวกัน
- หน้า Android อัปเดตทันทีหลัง API สำเร็จ แล้ว refresh ยืนยันจาก Server
- หมดอายุแล้ว query จะคืนเป็นประกาศปกติอัตโนมัติ
- Admin แสดงเวลา PHP และ MySQL session เพื่อเช็ก timezone ได้
- ไอคอน Premium มี pulse เบา ๆ ตาม UI เดิมที่พัฒนาต่อ

Boost
- เพิ่ม API purchase_boost
- เพิ่ม listings.boosted_at และตาราง listing_boosts
- ตรวจ owner/status/แต้ม/cooldown จาก Server
- หักแต้ม + อัปเดต boosted_at + history + point ledger ใน transaction เดียว
- request_key ป้องกัน retry ซ้ำ และ Android reuse key เดิมจนกว่าจะสำเร็จ
- Admin ตั้งราคาแต้ม / cooldown / เปิด-ปิด Boost ได้
- หน้า Home เรียง:
  Premium ที่ถูกดันล่าสุด -> Premium อื่น -> ประกาศปกติที่ถูกดันล่าสุด
  -> ประกาศทั่วไปตามเวลา
- Android refresh Home/My Listings/Detail/Wallet หลังดันสำเร็จ

ระบบ “ซื้อเลย”
- เพิ่มปุ่ม “ซื้อเลย” ในหน้ารายละเอียดประกาศ
- หน้า Checkout เก็บชื่อผู้รับ เบอร์ บ้านเลขที่/หมู่ ซอย ถนน ตำบล อำเภอ
  จังหวัด รหัสไปรษณีย์ และหมายเหตุ
- แอปยังไม่รับเงิน ไม่มี Payment Gateway / Wallet ซื้อสินค้า / Escrow
- ราคา/ชื่อสินค้า/seller_id/buyer_id ถูกกำหนดจาก Server ไม่รับราคาจาก Android
- Order snapshot ราคา ชื่อ และรูปแรก ณ เวลาสั่งซื้อ
- สถานะ 5 แบบ:
  รอผู้ขายยืนยัน / กำลังเตรียมสินค้า / จัดส่งแล้ว / สำเร็จ / ยกเลิก
- ผู้ขายมีเมนู “คำสั่งซื้อที่ได้รับ”
- ผู้ซื้อมีเมนู “คำสั่งซื้อของฉัน”
- ผู้ขายยืนยัน -> กำลังเตรียมสินค้า
- ผู้ขายกรอกเลขพัสดุ -> จัดส่งแล้ว
- ผู้ซื้อกด “ได้รับสินค้าแล้ว” -> สำเร็จ
- เชื่อมจาก Order ไป Chat เดิมของ V7; ผู้ขายใน Order เริ่มแชทกับผู้ซื้อได้แม้ห้องยังไม่มีข้อความ
- Push Order เปิดเข้า Order ที่เกี่ยวข้องโดยตรง

ป้องกันการขายซ้ำ
----------------
- ตอน create_order ใช้ transaction และ FOR UPDATE lock ที่ listing
- ประกาศ 1 รายการมี active order ได้เพียง 1 รายการในสถานะ
  pending_confirmation / preparing / shipped
- เมื่อมี active order API public ส่ง has_active_order=true และ can_buy=false
- เมื่อผู้ขายยืนยัน Order ประกาศเปลี่ยนเป็น sold
- ประกาศ sold เปิดดูรายละเอียดได้แบบ read-only แต่ไม่ซื้อซ้ำได้
- ถ้าผู้ขายปฏิเสธ/ผู้ซื้อยกเลิกตอน pending Order เป็น cancelled และ listing
  ที่ยัง approved กลับมาซื้อได้
- ประกาศหรือสมาชิกที่มีประวัติ Order จะไม่ถูก hard-delete เพื่อรักษาประวัติ

ความเป็นส่วนตัว / Security
---------------------------
- ที่อยู่จัดส่งเก็บใน orders เท่านั้น ไม่เพิ่มเข้า public listing payload
- my_orders ตรวจ buyer จาก token
- received_orders ตรวจ seller จาก token
- order_detail และ order_action ตรวจว่า user เป็น buyer หรือ seller ทุกครั้ง
- ผู้ใช้อื่นรู้ order_id ก็อ่าน Order ไม่ได้
- buyer_id / seller_id / ราคา / ชื่อสินค้า / listing owner อ้างจาก Server
- Premium/Boost ตรวจ owner และ wallet บน Server
- ใช้ prepared statements ใน query ที่รับ input
- upload รูป/สลิปเดิมยังตรวจ MIME/ขนาดและสุ่มชื่อไฟล์
- Admin page/endpoint เดิมตรวจ admin session + CSRF
- V8 ไม่สร้างจำนวน Online ปลอม: ยังใช้ heartbeat/last_seen 15 นาทีของ V7

UI V8
-----
- หน้า Home ปรับโทนตามภาพอ้างอิงที่แนบ: ขาว/ครีม/น้ำตาลทอง
- ใช้ภาพปกที่ผู้ใช้แนบเป็น hero จริง
- เพิ่มหัวแอป tagline, ช่องค้นหาในรายการหน้าแรก, trust card, online จริง
- ค่าเริ่มต้นแสดงพระมาใหม่แบบตาราง 2 คอลัมน์
- Premium/Boost/สถานะมีผู้สั่งซื้อ/ขายแล้วเห็นชัดบน card
- หน้ารายละเอียดปรับ action หลักเป็น “แชท” + “ซื้อเลย” + Favorite
- หน้าคำสั่งซื้อใช้คำไทยตรงไปตรงมาและไม่ทำเป็นระบบหลังบ้าน

วิธีอัปเดต V7 -> V8 (แนะนำ)
----------------------------
1. BACKUP ฐานข้อมูลและไฟล์เว็บไซต์ V7 ก่อนทุกครั้ง
2. อัปโหลดไฟล์ backend ของ V8 ทับตามโครงสร้างเดิม
   - อย่าลบ uploads/
   - รักษา config/config.php ของ production และ Firebase Service Account เดิม
3. เข้า Admin > “อัปเดตระบบ”
4. กด “ตรวจและติดตั้ง V8” 1 ครั้ง
5. เข้า Admin > “พรีเมียม/ดันโพสต์”
   - ตรวจเวลา PHP และ MySQL session ว่าตรงกัน
   - ตั้งราคา Boost
   - ตั้ง cooldown
   - เปิดใช้งาน Boost
   - ตรวจ/แก้ราคาแพ็กเกจ Premium
6. ถ้าต้องการอัปเดต DB ด้วย SQL โดยตรง ให้รัน:
   backend/database/migration_v8.sql
   ไฟล์นี้ออกแบบให้ตรวจ column/index ก่อนเพิ่ม และ CREATE TABLE IF NOT EXISTS
7. Build Android จาก KhaiPhraBan2/android-app
   - versionCode 6
   - versionName V8
   - ตรวจ API_BASE_URL ให้ตรง production ก่อน release
8. ติดตั้ง APK ใหม่ทับรุ่นเดิมและทดสอบ checklist ด้านล่าง

ติดตั้งใหม่ตั้งแต่ศูนย์
-----------------------
แพ็ก V7 เดิมไม่มี install.php จริง แม้ README เก่าเคยกล่าวถึง จึงไม่สร้าง installer
ใหม่ที่อาจเปลี่ยนโครงสร้าง/ความลับโดยไม่จำเป็น

วิธีติดตั้งใหม่:
1. สร้าง MySQL database/user
2. import backend/database/schema.sql
3. copy backend/config/config.example.php เป็น backend/config/config.php
4. ใส่ DB host/name/user/password, base_url, app_key และ timezone=Asia/Bangkok
5. สร้างสมาชิกคนแรกผ่าน Android/API แล้วกำหนด role='admin' ให้บัญชีนั้นจากฐานข้อมูล
   โดยผู้ดูแล Server (เลือกเฉพาะบัญชีที่คุณควบคุม)
6. ตั้งสิทธิ์ uploads ให้ PHP เขียนได้และป้องกัน executable ตาม .htaccess
7. ตั้ง Firebase Service Account นอก public_html เหมือน V7
8. Build Android ให้ API_BASE_URL ชี้มายัง /api/

Cron / Server
-------------
V8 ไม่ต้องมี Cron ใหม่สำหรับ Premium, Boost หรือ Order:
- Premium active/expired คำนวณจาก Server time ตอน query
- Boost เป็น timestamp จริง
- Order เป็น event ตาม action
- Push Order ส่งทันทีหลัง transaction commit

Cron เดิมของ V7 สำหรับ Scheduled Notification ต้องคงไว้และรันทุก 1 นาที:
  php /PATH_TO_YOUR_BACKEND/cron/notifications.php

Server ควรมี:
- PHP 8.1+ พร้อม PDO MySQL, mbstring, fileinfo, OpenSSL
- cURL หรือ allow_url_fopen สำหรับ FCM
- MySQL/MariaDB ที่รองรับ InnoDB + row locking
- HTTPS
- Firebase Service Account ตาม path เดิมของระบบ

สิ่งที่ตรวจใน environment นี้
------------------------------
ผ่าน:
- ตรวจโครงสร้าง ZIP V7 ก่อนแก้ และสร้าง V8 จากสำเนา V7
- ไม่ลบไฟล์เดิมของ V7
- API action เดิมของ V7 ยังอยู่ครบ และเพิ่ม action V8 เท่านั้น
- Admin page เดิมยังอยู่ครบ และเพิ่ม orders.php
- PHP syntax (php -l) ทุกไฟล์ backend
- timezone helper สำหรับ Asia/Bangkok คืน +07:00
- static check วงเล็บ/quote ของ Kotlin ทุกไฟล์
- static check signature/route ของ Android -> Repository -> API สำหรับ Premium/Boost/Order
- ตรวจ migration ไม่มีตาราง V8 ซ้ำใน fresh schema
- ตรวจ transaction/lock/idempotency ของ Premium/Boost/Order จาก source
- ตรวจ address ไม่ถูกเติมเข้า public listing payload

ยังทดสอบจริงไม่ได้ใน environment นี้:
- Android compileDebugKotlin / APK:
  Gradle Wrapper ต้องดาวน์โหลด Gradle 8.13 แต่ environment ไม่มี network
  และไม่มี Android SDK จึงไม่สามารถ compile จริงได้
- MySQL migration/runtime:
  environment ไม่มี MySQL/MariaDB server/client จึงตรวจได้เฉพาะ SQL/schema/static logic
- FCM delivery:
  ต้องใช้ Firebase credential/อินเทอร์เน็ต/เครื่องจริง
- End-to-end API กับ production DB:
  ไม่เชื่อม production เพื่อหลีกเลี่ยงการแก้ข้อมูลจริงโดยไม่ได้รับอนุญาต

Checklist ทดสอบมือถือจริง
--------------------------
A. Premium
[ ] Admin > พรีเมียม/ดันโพสต์ แสดง PHP time และ MySQL session time ตรงกัน
[ ] สมาชิกมีแต้มพอ -> ซื้อ Premium -> แต้มลดครั้งเดียว
[ ] icon/สถานะ Premium เปลี่ยนทันที
[ ] กลับ Home / refresh / ปิดเปิดแอป -> ยัง Premium ตาม Server
[ ] แต้มไม่พอ -> ไม่ถูกหักและไม่ Premium
[ ] กดเร็ว/ retry -> ไม่ถูกหักซ้ำ
[ ] Premium หมดเวลา -> กลับเป็นปกติ
[ ] Admin เห็นช่วง starts_at / ends_at ถูกต้องตามเวลาไทย

B. Boost
[ ] ตั้งราคา/cooldown ใน Admin
[ ] ดันประกาศ -> แต้มลดครั้งเดียว
[ ] boosted_at อัปเดตทันที
[ ] Home แสดงประกาศขึ้นตามลำดับ Server
[ ] refresh/ปิดเปิดแอป -> ลำดับยังถูก
[ ] ดันซ้ำก่อน cooldown -> ถูกปฏิเสธและไม่หักแต้ม
[ ] แต้มไม่พอ -> ไม่หักแต้ม
[ ] Admin เห็น Boost history

C. Buy Now / Order
[ ] ผู้ซื้อกดซื้อประกาศของคนอื่น
[ ] กรอกที่อยู่ไม่ครบ -> สร้าง Order ไม่ได้
[ ] ยืนยัน -> Order รอผู้ขายยืนยัน
[ ] ผู้ขายได้ Push และกดแล้วเข้า Order ถูกใบ
[ ] หลังมี pending order ผู้ซื้อคนที่สองซื้อซ้ำไม่ได้
[ ] ผู้ขายกดยืนยัน -> preparing และ listing เป็นขายแล้ว
[ ] ผู้ขายกรอกเลขพัสดุ -> shipped
[ ] ผู้ซื้อเห็นเลขพัสดุและได้ Push
[ ] ผู้ซื้อกดได้รับสินค้าแล้ว -> completed
[ ] ผู้ขายได้ Push ว่ารายการสำเร็จ
[ ] ผู้ขาย reject ตอน pending -> cancelled และ listing กลับมาซื้อได้
[ ] ผู้ซื้อ cancel ตอน pending -> cancelled และอีกฝ่ายได้ Push
[ ] Order เปิด Chat เดิมได้ทั้ง buyer/seller

D. Privacy / Security
[ ] login user C แล้วลองเรียก order_detail ของ A/B -> HTTP ปฏิเสธ
[ ] เปลี่ยน buyer_id/seller_id/price ใน request create_order -> Server ไม่ใช้ค่าปลอม
[ ] ผู้ขายอื่นยืนยัน/ship Order ไม่ได้
[ ] ผู้ซื้ออื่น received Order ไม่ได้
[ ] public home/listing response ไม่มี recipient_name/phone/address ของ Order
[ ] Admin login เห็น Order; member เปิด admin page ไม่ได้

E. Regression V7
[ ] สมัคร/Login
[ ] ลงประกาศ + upload รูป
[ ] Admin อนุมัติ
[ ] แก้ไข/ขายแล้ว/ลบประกาศที่ไม่มี Order
[ ] Favorite
[ ] Chat ข้อความ + รูป
[ ] เติมแต้มด้วยสลิป + Admin อนุมัติ
[ ] Push แชท
[ ] Push ประกาศ/แจ้งเตือน
[ ] Scheduled notification cron
[ ] Report user / suspend
[ ] Share Facebook
[ ] Online count เปลี่ยนตาม heartbeat จริง

ไฟล์สำคัญ V8
-------------
- backend/database/migration_v8.sql
- backend/database/schema.sql
- backend/includes/db.php
- backend/includes/helpers.php
- backend/api/index.php
- backend/admin/orders.php
- backend/admin/premium.php
- Android OrderScreens.kt
- Android HomeScreen.kt / DetailScreen.kt / PremiumScreen.kt
- Android Models.kt / ApiService.kt / MarketplaceRepository.kt / AppViewModel.kt

หมายเหตุ Production
--------------------
- ZIP นี้มี config เดิมจากโปรเจกต์ที่ได้รับเพื่อให้โครงสร้างอัปเกรดไม่แตก
  อย่าเผยแพร่ไฟล์ production config ต่อสาธารณะ และควร rotate secret ถ้า ZIP
  ถูกส่งต่อให้บุคคลที่ไม่ควรเห็น
- Backup DB ก่อน migration เสมอ
