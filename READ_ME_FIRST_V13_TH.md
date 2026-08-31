# ตลาดพระออนไลน์ V13 — พัฒนาต่อจาก Stable V12 Startup Hotfix

Stable Base: `KhaiPhraBan_V12_STARTUP_HOTFIX/.../KhaiPhraBan_FIXED_V12_Admin_Mobile_Notifications`

## สิ่งที่เปลี่ยนใน V13

### 1) ที่อยู่ผู้ขายจากบัญชีสมาชิก
- หน้าสมัครสมาชิกเพิ่ม จังหวัด / อำเภอ / ตำบล โดยใช้ `ThaiAddressData` และ dropdown เดิม
- เก็บใน `users.province`, `users.amphoe`, `users.tambon`
- หน้าโพสต์โหลด `my_profile` แล้วใช้ที่อยู่บัญชีอัตโนมัติ
- Backend `create_listing` ยึดที่อยู่ในบัญชีเป็นหลัก
- บัญชี V12 เดิมที่ไม่มีที่อยู่: หน้าโพสต์ให้เลือกครั้งเดียว และ Backend บันทึกกลับเข้าบัญชี
- ไม่เปลี่ยนโครงสร้าง Login/Token เดิม

### 2) “เผื่อคุณสนใจ” ท้ายหน้ารายละเอียด
- ใช้ `HomeData.random` และ API `home` เดิม ซึ่ง V12 สุ่มประกาศ approved ไว้อยู่แล้ว 12 รายการ
- ไม่เพิ่ม endpoint ใหม่
- แสดงสูงสุด 6 รายการ และตัดประกาศที่กำลังเปิดอยู่ออก

### 3) Push สมาชิกตั้งจำนวนรอบ/ช่วงเวลาได้
- ต่อจาก `scheduled_notifications`, Firebase Push และ Cron เดิม
- เพิ่มตาราง `member_push_settings`
- Admin > แจ้งเตือน ตั้งได้:
  - เปิด/ปิด
  - 1–6 รอบต่อวัน
  - ช่วงเวลาสูงสุด 3 ช่วง
- Cron สุ่มเวลาภายในช่วงที่กำหนด และสร้างรายการใน `scheduled_notifications`
- รายการอัตโนมัติใช้ `source=auto_member_v13` เพื่อไม่กระทบรายการที่ Admin ตั้งเอง
- V13 ยกเลิก local random reminder ของ V12 ใน Android ตอนเปิดแอป เพื่อไม่ให้แจ้งเตือนซ้ำ
- Chat / Order / Admin task Push เดิมไม่ถูกเปลี่ยน

## Database
ไฟล์ใหม่:
- `backend/database/migration_v13.sql`

เป็น additive/idempotent:
- เพิ่ม `users.province`
- เพิ่ม `users.amphoe`
- เพิ่ม `users.tambon`
- เพิ่ม `scheduled_notifications.source`
- เพิ่ม `member_push_settings`

ไม่มี DROP / TRUNCATE / reset และไม่แก้ migration เก่า

## Android version
- `versionCode = 12`
- `versionName = "V13.0"`

## Admin Update
`backend/admin/update_system.php` แสดง V13 และเรียก `ensure_v13_schema()` แบบ safe

## Cron
ให้รัน:
`php backend/cron/notifications.php`
ทุก 1 นาที ตามระบบเดิม
