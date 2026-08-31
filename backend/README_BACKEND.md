# หลังบ้าน PHP/MySQL — ตลาดพระออนไลน์ V8

V8 ต่อจาก V7 โดยคง PHP API, Admin, MySQL schema และระบบเดิมไว้ แล้วเพิ่ม Premium/Boost ที่ทำงานจริงและระบบคำสั่งซื้อแบบง่าย

## ความต้องการ
- PHP 8.1+ พร้อม PDO MySQL, fileinfo, mbstring และ OpenSSL
- MySQL 5.7+ หรือ MariaDB 10.4+ ที่ใช้ InnoDB/row locking
- Apache/LiteSpeed และ HTTPS
- โฟลเดอร์ `uploads/` ต้องเขียนได้ แต่ห้าม execute PHP ตาม `.htaccess`
- Firebase Service Account / การตั้งค่า FCM เดิมของ V7 สำหรับ Push Notification

## อัปเดตจาก V7 -> V8
1. สำรองฐานข้อมูลและไฟล์ V7 ก่อน
2. อัปโหลดโฟลเดอร์ `backend` ของ V8 ทับโครงสร้างเดิม
3. **อย่าลบ `uploads/` และอย่าทับค่าลับ production โดยไม่ตรวจสอบ**
4. เข้า Admin > **อัปเดตระบบ** แล้วกด **ตรวจและติดตั้ง V8** หนึ่งครั้ง
5. หรือรัน `database/migration_v8.sql` ด้วยสิทธิ์ ALTER/CREATE หากต้องการอัปเดตผ่าน SQL โดยตรง
6. เข้า Admin > **พรีเมียม/ดันโพสต์** แล้วตรวจ:
   - เวลา PHP และ MySQL session ต้องตรงกัน
   - ราคา Premium
   - ราคา Boost
   - cooldown Boost
   - เปิด/ปิด Boost
7. ทดสอบ Premium, Boost, Order และ Push บนฐานข้อมูล staging ก่อน production

Migration ออกแบบให้รันซ้ำได้: ตรวจ column/index ก่อน ALTER และใช้ `CREATE TABLE IF NOT EXISTS` สำหรับตารางใหม่

## ติดตั้งใหม่
แพ็กโปรเจกต์นี้ไม่มี `install.php` และ V8 ไม่สร้าง installer ใหม่เพื่อหลีกเลี่ยงการเปลี่ยนระบบเดิมโดยไม่จำเป็น

1. สร้าง database/user
2. import `database/schema.sql`
3. copy `config/config.example.php` เป็น `config/config.php`
4. ตั้ง DB, `base_url`, `app_key`, timezone (`Asia/Bangkok`) และค่าที่ deployment เดิมต้องใช้
5. สร้างสมาชิกคนแรกผ่าน Android/API ตามระบบปกติ แล้วกำหนด `role='admin'` ให้บัญชีนั้นจากฐานข้อมูลโดยผู้ดูแล Server เช่น:
   `UPDATE users SET role='admin' WHERE email='อีเมลผู้ดูแล';`
   (ใช้เฉพาะอีเมลที่คุณควบคุมและเปลี่ยนรหัสผ่านที่แข็งแรง)
6. ตั้ง Firebase Service Account ตาม path ที่ระบบใช้งาน
7. ตรวจ permission `uploads/`
8. เปิด Admin ที่ `/admin/login.php`

API สำหรับ Android อยู่ที่ `/api/`

## Premium และ Boost ใน V8
- PDO ทุก connection ตั้ง MySQL session `time_zone` ให้ตรงกับ app timezone
- Premium ใช้ MySQL `NOW()` / `DATE_ADD()` ใน transaction เดียว
- Premium ใช้ `request_key` ป้องกัน retry เดิมหักแต้มซ้ำ
- Boost มี `listings.boosted_at`, `listing_boosts`, Server-side point check และ transaction
- หน้า Home เรียง Premium/Boost จากข้อมูลจริงใน Server ไม่ได้เลื่อนเฉพาะบน Android
- Admin แสดงเวลา PHP/MySQL และตั้งค่า Boost ได้

## Order V8
สถานะมี 5 แบบ:
- `pending_confirmation` — รอผู้ขายยืนยัน
- `preparing` — กำลังเตรียมสินค้า
- `shipped` — จัดส่งแล้ว
- `completed` — สำเร็จ
- `cancelled` — ยกเลิก

ข้อมูลราคา/ชื่อสินค้า/seller/buyer มาจาก Server และ snapshot ลง Order ตอนสร้าง ไม่รับราคาจาก Android

ที่อยู่จัดส่งอยู่ใน `orders` เท่านั้น และ API ตรวจ participant ทุกครั้ง:
- Buyer ดู Order ของตัวเอง
- Seller ดู Order ของสินค้าตัวเอง
- Admin ที่ล็อกอินและมีสิทธิ์ดูผ่าน Admin
- Public listing API ไม่ส่ง shipping address

การสร้างและยืนยัน Order ใช้ transaction/row lock เพื่อป้องกันสินค้าชิ้นเดียวถูกขายซ้ำ
Order เชื่อม Chat เดิม และทั้งผู้ซื้อ/ผู้ขายใน Order สามารถเข้าห้องสนทนาคู่นั้นได้แม้ยังไม่มีข้อความแรก

## Push Notification
Order ส่ง data push หลัง transaction commit พร้อม `type=order` และ `order_id` เพื่อให้ Android เปิด Order ที่เกี่ยวข้องได้

Cron เดิมของ V7 สำหรับ Scheduled Notification ต้องคงไว้ เช่นทุก 1 นาที:

```bash
php /PATH_TO_BACKEND/cron/notifications.php
```

V8 ไม่ต้องมี cron ใหม่สำหรับ Premium, Boost หรือ Order

## ความปลอดภัย
- Bearer token/session ตรวจใน endpoint ที่ต้องล็อกอิน
- Admin ตรวจ admin session/สิทธิ์
- Prepared statements ผ่าน PDO
- CSRF ใน Admin form เดิม
- ตรวจ MIME/ขนาดไฟล์ upload ตามระบบเดิม
- Premium/Boost ตรวจ owner และแต้มจาก Server
- Order ไม่รับ `buyer_id`, `seller_id` หรือราคาจาก client
- Order detail/action ตรวจสิทธิ์ participant
- การกดซื้อ Premium/Boost/Create Order ซ้ำใช้ request key/idempotency
- หลีกเลี่ยง hard-delete ผู้ใช้/ประกาศที่มีประวัติ Order

## Online
V8 **ไม่สร้างจำนวนผู้ใช้ออนไลน์ปลอม** และยังใช้ heartbeat/`last_seen` ตามระบบ V7 (หน้าจอระบุช่วง 15 นาทีล่าสุด)

ดูขั้นตอนอัปเดตและ checklist เต็มที่ `../READ_ME_FIRST_V8_TH.txt`
