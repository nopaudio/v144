# ตลาดพระออนไลน์ V10 — สรุปการพัฒนา

ฐานงาน: **V9 Stable ZIP ที่แนบในแชทนี้**  
แนวทาง: ต่อจาก Android Compose + PHP API/Admin + MySQL เดิม ไม่สร้าง Project ใหม่ ไม่ Rewrite Architecture

## 1) สิ่งที่ตรวจพบจาก V9 ก่อนแก้

- `users.role ENUM('member','admin')` มีอยู่แล้ว จึง **reuse role เดิม** และไม่เพิ่ม `is_admin` ในฐานข้อมูล
- `push_tokens` ผูก `user_id` อยู่แล้ว จึง **reuse token เดิม** สำหรับ Admin Push และ User Push
- `identity_verifications` มี `reviewed_by`, `reviewed_at`, `rejection_reason`
- `point_topup_requests` มี `reviewed_by`, `reviewed_at`
- `listings` ยังไม่มี approval audit จึง V10 เพิ่มเฉพาะ `approved_by`, `approved_at`
- V9 มี scheduled/user notification เดิม แต่ไม่มี actionable Admin Notification Center จึงเพิ่มระบบ Admin task notification แยก โดยไม่แทน Push เดิม
- Order V9 เป็น flow ผู้ซื้อ/ผู้ขาย ไม่มีสถานะที่ต้องให้ Admin approve จึง V10 แสดง Orders ให้ Admin ตรวจแบบ read-only และ **ไม่สร้าง Admin task ปลอม**
- Android เป็น Native Compose + Retrofit + Repository + AppViewModel + Navigation Compose อยู่แล้ว จึงเพิ่ม Native Admin ตาม architecture เดิม และไม่ใช้ WebView

## 2) Admin Mobile Web

ปรับผ่าน `_header.php`/`_footer.php` กลางของ Admin เดิมเพื่อให้ทุกหน้าเดิมได้ responsive behavior โดยไม่สร้าง Admin ใหม่:

- viewport สำหรับมือถือ
- mobile hamburger menu
- bottom navigation: ภาพรวม / งานรอ / แจ้งเตือน / สมาชิก / เพิ่มเติม
- table เดิมถูกเปลี่ยน presentation เป็น card เมื่อจอเล็ก โดย JavaScript ใส่ label จาก `<th>` ให้ `<td>`
- ปุ่ม/ฟอร์ม/รูป/สถานะใหญ่และอ่านง่ายขึ้น
- approve = เขียว, reject = แดง, pending = ส้ม
- desktop layout เดิมยังเป็น table
- หน้า Login Admin ปรับให้ mobile friendly และ input 16px ป้องกัน browser zoom บนมือถือ

## 3) Dashboard

Dashboard ใช้ `admin_pending_counts()` และ query Database จริง:

- ประกาศ pending
- เติมแต้ม pending
- Identity pending
- Report open
- Admin Notification unread
- สมาชิกทั้งหมด
- Active orders เพื่อดูสถานะ (ไม่ถูกนับเป็น Admin task)
- recent notifications / recent pending listings

Card กดเปิด queue ที่เกี่ยวข้องได้ทันที ไม่มี hardcode count

## 4) Admin Notification Center

เพิ่ม:

- `admin_notifications`
- `admin_notification_reads`

รองรับ:

- type/title/message
- related user
- entity type/id
- web action path
- native mobile route
- created_at
- unread/read **แยกต่อ Admin แต่ละบัญชี**
- mark read / mark all read
- เมื่อ Admin คนหนึ่ง resolve งาน actionable ระบบ mark notification ของ entity นั้นเป็น read ให้ active Admin ทุกคน เพื่อไม่ให้ badge งานค้าง

เหตุการณ์ที่ V10 สร้าง notification เพราะ Source จริงต้องให้ Admin ทำงาน:

1. ส่งสลิปเติมแต้ม
2. ส่ง Identity Verification ครั้งแรก
3. ส่ง Identity ใหม่หลังถูก Reject
4. ลงประกาศ pending
5. ส่ง Report

ไม่สร้าง notification สำหรับ Order/Premium/Boost/User registration เพราะ Source V9 ไม่มี Admin action ที่ต้องดำเนินการในเหตุการณ์เหล่านั้น

## 5) Admin Push

เพิ่ม `firebase_push_to_admins()` บน helper Firebase เดิม:

- Server query `users.role='admin' AND status='active'`
- reuse `push_tokens` เดิม
- Server ใส่ `recipient_user_id` เอง
- data type ใหม่: `admin_task`
- User/Chat/Order/Admin-notification Push เดิมยังอยู่
- Android จะโชว์ Admin Push ก็ต่อเมื่อ session ปัจจุบันเป็น Admin และ `userId` ตรงกับ recipient ที่ Server ส่ง

ไม่มี endpoint ที่เชื่อ `is_admin`, `admin_id`, `role`, `permission` จาก Android

## 6) Native Admin ใน Android

เพิ่ม `AdminScreens.kt` และ route เดิมของ Navigation Compose:

- Admin Dashboard
- Admin Notifications
- Top-up pending + เปิด slip ผ่าน protected API + approve/reject
- Identity pending + เปิดเอกสารผ่าน protected API + approve/reject + เหตุผล
- Listing pending + approve/reject
- Reports + reviewing/resolve/dismiss + note
- Orders read-only ตาม flow V9 จริง
- Users/search/read-only + ADMIN badge

ทุก action เรียก Retrofit API ที่ Backend ตรวจ Bearer token → DB user → role admin

## 7) Admin Badge

ใช้ค่าจาก Server:

- Member Profile
- Listing Detail ข้าง Seller
- Chat list / Chat header
- หน้าของฉัน

`my_profile` ยังใช้เพื่อ refresh role ใน local session สำหรับ UI แต่ไม่ได้ใช้เป็นตัวอนุญาต Admin API

## 8) Security

- Web Admin `require_admin()` re-query DB ทุก protected page: id ต้องเป็น `role='admin'` และ `status='active'`
- API Admin ทุกตัวเรียก `require_api_admin()`
- API token ถูก join กลับ `users` และรับเฉพาะ active user
- ไม่ใช้ `username == admin`, `email == ...`, `user_id == 1`
- ไม่รับ client `is_admin/admin_id/role/permission` เพื่ออนุญาต Admin
- Identity document เดิมยัง private
- V10 เพิ่ม protected slip viewer สำหรับ Web/Native Admin
- `uploads/slips/.htaccess` deny direct HTTP บน Apache
- Wallet API ไม่ส่ง `slip_path`/`slip_url` ให้สมาชิกทั่วไปอีก
- protected media ส่ง `Cache-Control: private, no-store`

## 9) Database V10

ไฟล์: `backend/database/migration_v10.sql`

เพิ่มแบบ safe/idempotent:

- `listings.approved_by`
- `listings.approved_at`
- index/FK สำหรับ `approved_by`
- `admin_notifications`
- `admin_notification_reads`

ไม่มี DROP / TRUNCATE / reset / DELETE FROM ใน migration  
`ON DELETE` ที่เห็นเป็น Foreign Key rule ไม่ใช่คำสั่งล้างข้อมูลตอน migration

## 10) Approval audit

- Top-up: ใช้ `reviewed_by/reviewed_at` เดิม
- Identity: ใช้ `reviewed_by/reviewed_at/verified_at` เดิม
- Report: ใช้ `resolved_by/resolved_at` เดิม
- Listing: V10 เพิ่ม `approved_by/approved_at`

