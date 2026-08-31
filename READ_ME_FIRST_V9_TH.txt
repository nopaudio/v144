ตลาดพระออนไลน์ — V9 (พัฒนาต่อจาก Stable V8 ที่แนบมา)
=============================================================
ฐานงาน: KhaiPhraBan_V8.zip ที่แนบในแชทนี้เท่านั้น
แนวทาง: ไม่ Rewrite, ไม่ย้าย Architecture, แก้ต่อจาก Android Compose + PHP API/Admin + MySQL เดิม

สรุป Source V8 ที่ตรวจจริงก่อนแก้
-----------------------------------
- Android: KhaiPhraBan2/android-app
- PHP API หลัก: backend/api/index.php
- Helper/Schema self-healing เดิม: backend/includes/helpers.php
- Admin เดิม: backend/admin/
- Database fresh schema: backend/database/schema.sql
- V8 migration เดิม: backend/database/migration_v8.sql
- Home config เดิม: home_content (brand_title/headline/subheadline/trust_text/is_active)
- Hero/Banner V8: รูป home_hero_v8.png ถูกอ้างตรงใน Android และไม่มีตาราง Banner หลายรูป
- Chat เดิม: chat_messages มี listing_id/buyer_id/sender_id/message/image_path/created_at แต่ไม่มี read_at
- Order เดิม: snapshot ราคา/ชื่อ/รูปแล้ว แต่ยังไม่มี snapshot บัญชีรับเงิน
- V8 ยังไม่มี identity_verifications และ seller_reviews
- V8 ไม่มีระบบรูป Profile/Avatar ของสมาชิก จึง V9 ไม่สร้าง subsystem อัปโหลด Avatar ใหม่โดยไม่จำเป็น
  หน้า Profile ใช้ไอคอน Account เดิม/มาตรฐานแทน เพื่อรักษาขอบเขตและลด regression

สิ่งที่เพิ่ม/ต่อยอดใน V9
-------------------------
1) Home Banner จาก Server/Admin
- เพิ่ม home_banners
- Admin เพิ่ม/เปลี่ยนรูป/ลบ/เปิด-ปิด/กำหนด sort_order ได้
- Android ใช้ HorizontalPager: ปัดด้วยนิ้ว, auto-slide ประมาณ 5 วินาทีเมื่อมีมากกว่า 1 รูป
- มี indicator เมื่อมีมากกว่า 1 รูป
- 1 รูปแสดงได้ตามปกติ
- 0 รูปไม่สร้างช่องว่าง Banner
- Android ไม่อ้าง R.drawable.home_hero_v8 อีกแล้ว
- รูป Hero V8 เดิมถูก copy ไปเป็น backend/uploads/banners/default_home_v8.png
  และ seed เป็น Banner ฝั่ง Server ตอนอัปเกรดครั้งแรก เพื่อให้หน้าตาเดิมไม่หาย
- V8 Hero เดิมกดแล้วเปิดโพสต์ Premium แรกได้เมื่อมี Premium; V9 รักษาพฤติกรรมนี้ไว้
- V8 ไม่มี per-banner URL/action ในฐานข้อมูล จึงไม่ได้สร้างระบบ link/action ใหม่ที่ซับซ้อน

2) ข้อความ Home “ซื้อขายมั่นใจ ปลอดภัย”
- ใช้ home_content เดิม
- เพิ่มเฉพาะ trust_title ลงตารางเดิม
- Admin แก้ trust_title และ trust_text ได้จากหน้า Home เดิม
- Android อ่าน trust_title/trust_text จาก Server
- ไม่สร้าง settings table ใหม่

3) Identity Verification + Verified Receiving Bank
- ตาราง identity_verifications ผูก 1 record ต่อ user
- status: unverified / pending / verified / rejected
- เก็บ bank_name / account_name / account_number / submitted/reviewed/verified timestamps
- reviewed_by อ้าง Admin user เดิม
- สมาชิกส่งข้อมูลด้วย token ของตัวเองเท่านั้น; Backend ไม่เชื่อ user_id จาก Android
- ส่งใหม่เมื่อเปลี่ยนบัญชี => status กลับ pending, verified_at/reviewer เดิมถูกล้าง
- Admin อนุมัติ/ปฏิเสธ/ใส่เหตุผลได้
- Admin ใช้ session + CSRF เดิม
- ป้องกัน stale approval: Admin action ตรวจทั้ง submitted_at และ document_path ของคำขอที่กำลังดู

4) ความปลอดภัยเอกสารยืนยันตัวตน
- หลักฐานไม่ถูกเก็บเป็น public URL ใน DB/API
- ชื่อไฟล์สุ่มด้วย random_bytes
- Backend ตรวจ MIME จริง: JPG/PNG/WEBP และจำกัดขนาด
- path ถูก validate ก่อนเปิด ป้องกัน path traversal
- Admin เปิดหลักฐานผ่าน backend/admin/identity_document.php ที่ require_admin() เท่านั้น
- response ใช้ Cache-Control: private, no-store
- ค่า private_storage_path รองรับ path นอก public_html
- production แนะนำอย่างยิ่งให้ตั้ง absolute path นอก public_html ใน backend/config/config.php
- ถ้าไม่ตั้ง ระบบมี fallback + .htaccess deny-all เป็น defense-in-depth
- ไม่แก้ระบบ upload รูปประกาศ/รูปแชท/สลิปเติมแต้มเดิม

5) Seller Verified Status ใน Checkout/Order
- public Home/Member profile ไม่ส่งเลขบัญชี
- Checkout รับข้อมูล seller_payment จาก Server เท่านั้น
- Server อ่านเฉพาะ identity_verifications.status='verified'
- Android ไม่ส่ง seller_id/verified/bank_account_id/เลขบัญชีเพื่อให้ Server เชื่อ
- ตอน create_order Server lock listing และ snapshot สถานะ/ธนาคาร/ชื่อบัญชี/เลขบัญชี/verified_at
  ลง Order ใหม่
- Order เก่าจึงไม่เปลี่ยนตามบัญชี Seller ในอนาคต
- Seller ไม่ verified => แสดงชัด “ผู้ขายรายนี้ยังไม่ได้ยืนยันตัวตน”
- ยังไม่บังคับห้ามขายสำหรับสมาชิกที่ไม่ verified เพราะ V8 ไม่มี rule นี้

6) Verified Badge + Member Profile
- Listing Detail แสดง badge verified/unverified + rating + จำนวนรีวิว
- หน้า Member Profile แสดง username/status/rating/review count/member since + ประกาศ approved/sold
- หน้า “ของฉัน” มีส่วน Profile และเมนู “ยืนยันตัวตน” พร้อม status
- ใช้ Theme/Compose เดิม ไม่รื้อหน้าเดิมทั้งหมด

7) Seller Rating
- ตาราง seller_reviews
- 1 Order ต่อ 1 Rating ด้วย UNIQUE(order_id)
- Server ตรวจ current authenticated user ต้องเป็น buyer ของ Order นั้น
- Order ต้อง status='completed'
- Seller ให้ตัวเองไม่ได้
- คะแนน 1–5 เท่านั้น
- review_text สูงสุด 500 ตัวอักษร (ไม่บังคับ)
- Profile/Listing Detail แสดง average จริง + count จริง
- ไม่มีคะแนน => “ยังไม่มีคะแนน”
- Android เป็น UI; สิทธิ์ตัดสินที่ Server

8) Chat Unread
- ใช้ chat_messages เดิม เพิ่ม read_at เท่านั้น
- migration ครั้งแรก mark ข้อความ V8 เก่าเป็น read เพื่อไม่ให้ติด badge ย้อนหลังจำนวนมาก
- ข้อความใหม่จากอีกฝ่าย read_at=NULL จนผู้รับเปิดห้อง
- chat_messages endpoint ตรวจสิทธิ์ห้องเดิมก่อน แล้ว mark เฉพาะ sender_id != current user เป็น read
- Bottom Navigation แสดง unread รวมจริงจาก Server
- Chat list แสดง unread ต่อห้องจริงจาก Server
- เปิดห้องแล้ว refresh total/thread badge
- send_message / send_chat_image / Push flow เดิมไม่ได้สร้างใหม่หรือแทนของเดิม

Database V9
-----------
ไฟล์: backend/database/migration_v9.sql

เพิ่ม/แก้แบบ in-place:
- home_content.trust_title
- home_banners
- identity_verifications
- seller_reviews
- chat_messages.read_at + idx_chat_thread_read
- orders.seller_verified
- orders.seller_bank_name_snapshot
- orders.seller_account_name_snapshot
- orders.seller_account_number_snapshot
- orders.seller_verified_at_snapshot

Migration ไม่มี DROP / TRUNCATE / DELETE ข้อมูลเดิม
หมายเหตุ: คำว่า ON DELETE ใน FOREIGN KEY เป็นกติกาความสัมพันธ์ ไม่ใช่คำสั่งลบข้อมูลตอน migration

============================================================
วิธีอัปเดตแบบ “สำหรับมือใหม่” V8 -> V9 (แนะนำ)
============================================================

ขั้นที่ 1 — สำรองก่อน
----------------------
1. Export Database V8 เป็น .sql จาก phpMyAdmin
2. Download/Backup โฟลเดอร์เว็บไซต์ Backend เดิมทั้งหมด
3. Backup uploads/ โดยเฉพาะรูปประกาศ/แชท/สลิป
4. Backup backend/config/config.php และ Firebase Service Account เดิม
5. ห้ามลบฐานข้อมูลหรือ uploads เดิม

ขั้นที่ 2 — อัปโหลดไฟล์ Backend V9
-----------------------------------
อัปโหลดไฟล์ตาม path เดิม โดยให้ [SERVER_BACKEND_ROOT] หมายถึงโฟลเดอร์ Backend ที่ใช้งานจริงบน Server:

ไฟล์เดิมที่ต้องทับ:
- [SERVER_BACKEND_ROOT]/api/index.php
- [SERVER_BACKEND_ROOT]/includes/helpers.php
- [SERVER_BACKEND_ROOT]/admin/_header.php
- [SERVER_BACKEND_ROOT]/admin/home_content.php
- [SERVER_BACKEND_ROOT]/admin/update_system.php

ไฟล์ใหม่ที่ต้องเพิ่ม:
- [SERVER_BACKEND_ROOT]/admin/verifications.php
- [SERVER_BACKEND_ROOT]/admin/identity_document.php
- [SERVER_BACKEND_ROOT]/database/migration_v9.sql
- [SERVER_BACKEND_ROOT]/uploads/banners/default_home_v8.png

ไฟล์สำหรับ fresh install/reference (ไม่ต้อง Run ทับ DB production):
- [SERVER_BACKEND_ROOT]/database/schema.sql
- [SERVER_BACKEND_ROOT]/config/config.example.php

สำคัญ:
- อย่า overwrite backend/config/config.php ของ Production ด้วย config.example.php
- อย่าลบ uploads/
- อย่าย้าย/ลบ Firebase Service Account เดิม
- Cron Scheduled Notification เดิมของ V8/V7 ให้คงเหมือนเดิม

ขั้นที่ 3 — ตั้งพื้นที่เก็บหลักฐาน Identity
--------------------------------------------
ใน backend/config/config.php ภายใน array 'app' แนะนำเพิ่ม:

'private_storage_path' => '/ABSOLUTE/PATH/OUTSIDE/public_html/khaiphraban_private',

ให้ใช้ path จริงของ Hosting ของคุณที่ “อยู่นอก public_html”
ถ้าไม่ทราบ path ให้ดู File Manager ของ Hosting หรือถามผู้ให้บริการ Hosting
อย่าคัดลอกตัวอย่าง /ABSOLUTE/PATH/... ไปใช้ตรง ๆ

ถ้ายังไม่ตั้ง:
- ระบบมี fallback ที่พยายามวาง khaiphraban_private ไว้เหนือ application root
- สร้าง .htaccess deny-all เพิ่มอีกชั้น
แต่ Production ควรตั้ง path นอก public_html ชัดเจนที่สุด

ขั้นที่ 4 — Run SQL
-------------------
วิธีแนะนำ:
1. เข้า phpMyAdmin
2. เลือก Database ตลาดพระ “ตัวจริงให้ถูกฐาน”
3. ไป Import หรือ SQL
4. Run ไฟล์:
   backend/database/migration_v9.sql
5. ตรวจว่าไม่มี error

ไฟล์ migration ออกแบบให้ Run ซ้ำได้โดยตรวจ table/column/index ก่อนเพิ่ม
และไม่ลบข้อมูล V8 เดิม

ทางเลือก:
Admin > อัปเดตระบบ > “ตรวจและติดตั้ง V9”
จะเรียก schema self-healing เดิมต่อยอดถึง V9
ไม่จำเป็นต้องใช้ทั้ง 2 วิธี แต่ถ้าเผลอใช้ทั้งคู่ migration/ensure ถูกออกแบบให้ตรวจของเดิมก่อน

ลำดับที่ปลอดภัย:
UPLOAD Backend + default banner -> ตั้ง private_storage_path -> RUN migration_v9.sql -> ตรวจ Admin -> Build Android

ขั้นที่ 5 — ตั้งค่าใน Admin
----------------------------
1. Login Admin เดิม
2. หน้า “หน้าแรก”
   - ตรวจข้อความ trust title/text
   - ตรวจ Banner V8 เดิมถูกแสดงจาก Server
   - เพิ่ม Banner 2–3 รูป
   - กำหนดลำดับ
   - ทดลองปิด 1 รูป
3. หน้า “ยืนยันตัวตน”
   - ตรวจคำขอ pending
   - เปิดหลักฐาน
   - Approve/Reject + เหตุผล
4. หน้า “อัปเดตระบบ”
   - ใช้ตรวจ schema V9 ได้
5. หน้า Premium/Boost/Order/Points เดิมควรยังใช้งานตามเดิม

ขั้นที่ 6 — Build Android
--------------------------
โฟลเดอร์:
KhaiPhraBan2/android-app

V9:
- versionCode = 8
- versionName = V9.0

ใน Android Studio:
1. เปิดโฟลเดอร์ KhaiPhraBan2/android-app
2. ให้ Gradle Sync สำเร็จ
3. ตรวจ API_BASE_URL ให้ชี้ Backend Production เดิม
4. Build > Make Project
5. Build APK/AAB ตามวิธีเดิมของ V8
6. ติดตั้งทับรุ่น V8 บนเครื่องทดสอบก่อน Production

============================================================
Checklist มือถือจริง
============================================================

A. Banner / Home
[ ] Admin เพิ่ม Banner 3 รูป
[ ] Home refresh แล้วเห็น 3 รูป
[ ] ปัดซ้าย/ขวาได้
[ ] ปล่อยไว้แล้ว auto-slide
[ ] indicator ตรงจำนวน
[ ] Admin ปิด 1 รูป -> Home refresh เหลือ 2
[ ] เหลือ 1 รูป -> แสดงปกติ ไม่วนผิด
[ ] ปิด/ลบทุก Banner -> ไม่มีช่องว่างและไม่ crash
[ ] เปลี่ยน “ซื้อขายมั่นใจ ปลอดภัย” ใน Admin -> refresh Android แล้วเปลี่ยนตาม Server
[ ] ถ้ามี Premium กด Banner แล้วยังเปิด Premium แรกได้เหมือน Hero V8

B. Verification
[ ] User A หน้า “ของฉัน” ขึ้นยังไม่ยืนยัน
[ ] กรอกธนาคาร/ชื่อบัญชี/เลขบัญชี + รูปคู่สมุดบัญชี
[ ] ส่ง -> pending/รอตรวจสอบ
[ ] Admin เปิดรูปได้หลัง Login
[ ] เปิด URL หลักฐานโดยไม่ Login Admin -> เปิดไม่ได้
[ ] Admin approve -> User refresh แล้ว verified
[ ] User ส่งบัญชีใหม่ -> กลับ pending ทันที
[ ] Admin reject พร้อมเหตุผล -> User เห็นเหตุผล
[ ] User แก้และส่งใหม่ -> pending ใหม่

C. Order / Verified Bank Snapshot
[ ] Seller A verified -> Buyer checkout เห็น verified + ธนาคาร/ชื่อบัญชี/เลขบัญชี
[ ] สร้าง Order -> Order Detail เห็น snapshot เดิม
[ ] Seller เปลี่ยนบัญชีภายหลัง -> Order เก่ายังแสดง snapshot เดิม
[ ] Seller D ไม่ verified -> Checkout แสดง “ผู้ขายรายนี้ยังไม่ได้ยืนยันตัวตน”
[ ] Android request ปลอม seller_id/verified/bank ไม่สามารถกำหนดข้อมูล Order ได้

D. Rating
[ ] Buyer B กด “ได้รับสินค้าแล้ว” -> Order completed
[ ] Buyer B ให้ 5 ดาว -> สำเร็จ
[ ] Buyer B ให้ซ้ำ Order เดิม -> Server ปฏิเสธ
[ ] User C ไม่ใช่ Buyer -> Server ปฏิเสธ
[ ] Seller A ให้ตัวเอง -> Server ปฏิเสธ
[ ] Profile Seller A average/count อัปเดต
[ ] Listing Detail Seller A แสดง rating
[ ] Seller ที่ไม่มี rating แสดง “ยังไม่มีคะแนน”

E. Chat unread
[ ] A ส่ง B 3 ข้อความ
[ ] B ยังไม่เปิด -> Bottom “แชท” มี unread > 0
[ ] Chat list ห้องนั้นมี badge
[ ] B เปิดห้อง -> ข้อความถึง B ถูก mark read
[ ] Bottom badge ลด/หาย
[ ] ข้อความที่ B ส่งเองไม่เพิ่ม unread ของ B
[ ] Push Chat เดิมยังเข้าเครื่อง
[ ] Push เปิด Chat เดิมได้

F. Regression V8 (ต้องทดสอบ)
[ ] Register / Login / Logout
[ ] ลงประกาศ + upload รูป
[ ] Admin อนุมัติประกาศ
[ ] แก้/ขายแล้ว/ลบตาม rule เดิม
[ ] Favorite
[ ] Chat ข้อความ + รูป
[ ] Premium ซื้อ/หมดอายุ
[ ] Boost ซื้อ/cooldown
[ ] เติมแต้ม + สลิป + Admin approve
[ ] Order create/seller confirm/reject/ship/buyer received
[ ] Listing sold
[ ] Push Chat/Order/Notification
[ ] Report / Block / Suspend
[ ] Share
[ ] Scheduled Notification cron เดิม
[ ] Online heartbeat เดิม

============================================================
ผลตรวจใน Environment นี้
============================================================
ผ่านจริง:
- แตกและเทียบกับ ZIP V8 ต้นฉบับก่อน/หลัง
- ไม่ลบไฟล์เดิม V8
- API action V8 เดิม 30 action ยังอยู่ครบ; V9 เพิ่ม 5 action
- Admin page V8 เดิมยังอยู่ครบ; เพิ่ม 2 หน้า Identity
- php -l ผ่าน Backend/Admin PHP ทั้งหมด 31 ไฟล์
- Static Kotlin delimiter/structure check ผ่าน Kotlin/KTS ที่ตรวจ
- Android ApiService -> Repository -> AppViewModel method wiring ไม่พบ method ขาด
- API action ที่ Android เรียกมี Backend case ครบ
- ไม่พบ duplicate top-level Kotlin type
- Core case Premium/Boost/send_message/send_chat_image/register_push_token/order_action
  เทียบ V8 แล้ว logic เดิมเหมือนเดิมเมื่อไม่นับการเปลี่ยน ensure schema เป็น V9
- helper upload V8 เดิม save_topup_slip/save_chat_image/save_listing_images เหมือน V8
- production backend/config/config.php ในชุดงานไม่ได้ถูกแก้
- migration_v9.sql static check: ไม่มี DROP/TRUNCATE/DELETE statement ล้างข้อมูล
- fresh schema ไม่มี duplicate table definition ของ V9

ยังทดสอบจริงไม่ได้ใน Environment นี้:
- Android compileDebugKotlin/APK: Gradle Wrapper 8.13 ไม่มี cache และ environment ไม่มี network/Android SDK
  จึงหยุดก่อน Kotlin compiler ทำงานจริง
- MySQL/MariaDB migration runtime: ไม่มี mysql/mariadb client/server
- FCM delivery: ไม่มี Production Firebase/device/network context สำหรับทดสอบ end-to-end
- Production API/DB: ไม่เชื่อม/แก้ฐานข้อมูลจริงของผู้ใช้
- Mobile UI/gesture/camera/file picker จริง: ต้องทดสอบบน Android device

ดูรายละเอียดเพิ่มใน TEST_REPORT_V9.txt และ CHANGED_FILES_V9.txt
