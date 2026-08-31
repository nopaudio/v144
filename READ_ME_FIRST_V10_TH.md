# READ ME FIRST — ตลาดพระออนไลน์ V10

## V9 → V10: Admin Mobile + Admin Notification + In-App Admin

ฐานงานของชุดนี้คือ **V9 Stable ที่ผู้ใช้แนบในรอบนี้** ไม่ใช่ Project ใหม่  
V10 ต่อจาก Android Compose + Retrofit/Repository/ViewModel + PHP API/Admin + MySQL เดิม

> สำคัญมาก: อย่าลบ Database, อย่าลบ `uploads/`, อย่า overwrite `backend/config/config.php` ของ Production และอย่าย้าย Firebase Service Account เดิม

---

## ลำดับอัปเดตที่แนะนำสำหรับมือใหม่

### ขั้นที่ 1 — Backup ก่อนทุกครั้ง

1. Export Database Production V9 เป็นไฟล์ `.sql`
2. Backup โฟลเดอร์ Backend เดิมทั้งหมด
3. Backup `uploads/` โดยเฉพาะรูปประกาศ/แชท/สลิป
4. Backup `backend/config/config.php`
5. Backup Firebase Service Account / environment variables เดิม
6. ถ้ามี APK/AAB V9 ที่ใช้งานอยู่ ให้เก็บไว้สำหรับ rollback

**ห้าม DROP / reset / ลบฐานข้อมูล V9**

### ขั้นที่ 2 — Run SQL migration V10 ก่อน Deploy code ใหม่

ไฟล์:

`backend/database/migration_v10.sql`

ใน phpMyAdmin:

1. เลือก Database Production V9 ให้ถูกฐาน
2. Import/SQL ไฟล์ `migration_v10.sql`
3. ตรวจว่าไม่มี error
4. ตรวจว่ามี:
   - `listings.approved_by`
   - `listings.approved_at`
   - `admin_notifications`
   - `admin_notification_reads`

Migration ตรวจของเดิมก่อนเพิ่มและไม่มี DROP/TRUNCATE/DELETE FROM

> `backend/database/schema.sql` เป็น **fresh install/reference** เท่านั้น ห้าม Import ทับ Production Database

### ขั้นที่ 3 — Upload Backend V10 ตาม path เดิม

ให้ `[SERVER_BACKEND_ROOT]` = โฟลเดอร์ `backend` ที่ใช้งานจริงบน Server

#### ไฟล์เดิมที่ต้องทับ

- `[SERVER_BACKEND_ROOT]/admin/_footer.php`
- `[SERVER_BACKEND_ROOT]/admin/_header.php`
- `[SERVER_BACKEND_ROOT]/admin/index.php`
- `[SERVER_BACKEND_ROOT]/admin/listings.php`
- `[SERVER_BACKEND_ROOT]/admin/login.php`
- `[SERVER_BACKEND_ROOT]/admin/points.php`
- `[SERVER_BACKEND_ROOT]/admin/reports.php`
- `[SERVER_BACKEND_ROOT]/admin/verifications.php`
- `[SERVER_BACKEND_ROOT]/api/index.php`
- `[SERVER_BACKEND_ROOT]/includes/firebase_push.php`
- `[SERVER_BACKEND_ROOT]/includes/helpers.php`

#### ไฟล์ใหม่ที่ต้องเพิ่ม

- `[SERVER_BACKEND_ROOT]/admin/admin_alerts.php`
- `[SERVER_BACKEND_ROOT]/admin/slip_image.php`
- `[SERVER_BACKEND_ROOT]/api/admin_media.php`
- `[SERVER_BACKEND_ROOT]/uploads/slips/.htaccess`
- `[SERVER_BACKEND_ROOT]/uploads/slips/index.html`

#### ไฟล์ SQL ที่ควรเก็บบนเครื่อง/Server สำหรับอ้างอิง

- `[SERVER_BACKEND_ROOT]/database/migration_v10.sql`

#### ไม่ต้องทับ Production

- `backend/config/config.php`
- Firebase Service Account
- ไฟล์ข้อมูลใน `uploads/` เดิม

**สำคัญสำหรับสลิป:** ต้องอัปโหลด hidden file `uploads/slips/.htaccess` ด้วย  
บน Apache/cPanel จะปิด direct HTTP access แล้ว Admin เปิดผ่าน endpoint ที่ตรวจสิทธิ์แทน

ถ้า Server เป็น Nginx/IIS `.htaccess` จะไม่มีผล ให้ตั้ง web-server rule ปิด direct access ของ `/uploads/slips/` เพิ่มเองก่อน Production

### ขั้นที่ 4 — ทดสอบ Web Admin ก่อน

Login ด้วย Admin เดิม แล้วตรวจ:

1. Dashboard
   - จำนวน Pending มาจาก DB จริง
   - Top-up / Identity / Listing / Report
   - unread notification
2. Notification Center
   - เมนู `งานแจ้งเตือน`
   - mark read / mark all
   - กดเปิดงานถูกหน้า
3. Top-up
   - เปิดสลิปผ่าน `slip_image.php`
   - approve/reject
4. Identity
   - เปิด private document
   - approve/reject + เหตุผล
5. Listings / Reports
   - action เดิมยังทำงาน
6. เปิด Admin ด้วยมือถือ
   - menu ใช้ง่าย
   - table กลายเป็น card
   - ไม่มี horizontal overflow
   - ปุ่ม action ใหญ่พอกด
   - desktop ยังแสดง table ตามเดิม

### ขั้นที่ 5 — ตรวจ Admin Role

V10 **ไม่ได้สร้าง admin flag ใหม่** เพราะ V9 มี `users.role` อยู่แล้ว

Admin ที่ถูกต้องต้องเป็น:

- `users.role = 'admin'`
- `users.status = 'active'`

ห้ามแก้ Android ให้ส่ง `is_admin=true` เพราะ Server ไม่ใช้ค่านั้นอนุญาตสิทธิ์

ถ้าต้องเปลี่ยนสมาชิกเป็น Admin ให้ใช้วิธีจัดการ Database/Admin account ที่คุณใช้อยู่เดิมและตรวจให้แน่ใจว่าเป็นบัญชีที่ต้องการจริง

### ขั้นที่ 6 — Build Android V10

โฟลเดอร์:

`KhaiPhraBan2/android-app`

V10 ในชุดนี้:

- `versionCode = 9`
- `versionName = V10.0`

Android Studio:

1. เปิด `KhaiPhraBan2/android-app`
2. ตรวจ `API_BASE_URL` ให้ชี้ Backend Production เดิม
3. Gradle Sync
4. Build > Make Project
5. ทดสอบ Debug APK บนเครื่องจริงก่อน
6. Build APK/AAB Release ตาม keystore/process เดิมของ V9
7. ติดตั้งทับ V9 บนเครื่องทดสอบ
8. Login Admin จริง -> ต้องเห็นเมนู `ผู้ดูแลระบบ`
9. Login Member -> ต้องไม่เห็น/ใช้ Admin API ได้

### ขั้นที่ 7 — ทดสอบ Admin Push

ต้องมี Firebase/Push เดิมของ V9 ทำงานอยู่ก่อน

1. Login Admin บน Android
2. ให้ token ถูก register ตาม flow Push เดิม
3. ใช้ Member อีกบัญชีส่ง:
   - Top-up
   - Identity
   - Listing
   - Report
4. Admin device ควรได้ `admin_task` push
5. แตะ Push -> เปิด Native Admin route
6. User Push/Chat Push/Order Push เดิมต้องยังทำงาน

---

## Checklist มือถือจริง

### A. Mobile Web Admin

- [ ] iPhone/Android ความกว้างประมาณ 360–430px เปิด Login ไม่ต้อง zoom
- [ ] Dashboard card อ่านง่าย
- [ ] hamburger menu เปิด/ปิดได้
- [ ] bottom navigation ไม่บัง content
- [ ] Users/Listings/Points/Orders/Reports/Premium หน้า table ไม่ล้นแนวนอน
- [ ] ปุ่ม Approve/Reject เห็นชัด
- [ ] Slip เปิดเต็มรูปได้
- [ ] Identity document เปิดได้เฉพาะ Admin
- [ ] Desktop 1024px+ ยังใช้งานปกติ

### B. Top-up Flow

- [ ] Member ส่งสลิป
- [ ] Dashboard pending +1
- [ ] Notification unread +1
- [ ] Android Admin badge +1
- [ ] Admin เปิด slip
- [ ] Admin approve
- [ ] wallet เพิ่มแต้มครั้งเดียว
- [ ] transaction topup ถูกสร้าง
- [ ] reviewed_by/reviewed_at ถูกบันทึก
- [ ] pending ลด
- [ ] unread ของ task ถูก clear
- [ ] Member เปิด protected Admin media -> 403

### C. Identity Flow

- [ ] Member ส่ง Identity
- [ ] Admin pending/notification/push
- [ ] Admin เปิด private document
- [ ] approve -> verified badge
- [ ] reject -> ใส่เหตุผล
- [ ] Member ส่งใหม่หลัง reject -> ได้ notification ใหม่
- [ ] non-admin เปิด identity media -> 403

### D. Listing Flow

- [ ] Member ลงประกาศ -> pending
- [ ] Dashboard/notification +1
- [ ] Admin approve
- [ ] `approved_by/approved_at` มีค่า
- [ ] Listing แสดง public ตาม rule เดิม
- [ ] Admin reject ได้
- [ ] Premium/Boost/Order ที่เกี่ยวข้องยังไม่เสีย

### E. Report Flow

- [ ] Member report -> open
- [ ] Admin notification
- [ ] reviewing
- [ ] resolve/dismiss + note
- [ ] resolved_by/resolved_at เมื่อปิดเรื่อง
- [ ] count ลดตาม DB

### F. Permission / Forgery

- [ ] Member token เรียก `admin_dashboard` -> HTTP 403
- [ ] Member token เรียก `admin_topups` -> HTTP 403
- [ ] Member token เรียก `admin_verifications` -> HTTP 403
- [ ] Member token เรียก `admin_users` -> HTTP 403
- [ ] Member token เรียก `/api/admin_media.php` -> HTTP 403
- [ ] แก้ local app role/is_admin ให้เป็น Admin -> Server ยัง 403
- [ ] Admin token จริง -> ใช้งานได้
- [ ] เปลี่ยน Admin role เป็น member/suspended -> request ถัดไปถูกปฏิเสธ

### G. Admin Badge

- [ ] Admin profile แสดง ADMIN/ผู้ดูแลระบบ
- [ ] Listing ของ Admin แสดง badge ข้าง Seller
- [ ] Chat กับ Admin แสดง ADMIN
- [ ] Member ปกติไม่แสดง badge

### H. Regression V9

- [ ] Register/Login/Logout/Profile
- [ ] Banner หลายรูป + Home text
- [ ] Listing + รูป + Favorite + Share
- [ ] Chat + unread + รูป + Push
- [ ] Premium
- [ ] Boost
- [ ] Wallet/Points/Top-up
- [ ] Order + bank snapshot
- [ ] Rating
- [ ] Identity/Verified Bank/Verified Badge
- [ ] Member Profile
- [ ] Report/Block/Suspend
- [ ] Scheduled/User Push เดิม
- [ ] Admin desktop

---

## ข้อจำกัดของผลตรวจในชุดงานนี้

- PHP syntax: ตรวจจริงผ่านทั้งหมด
- Android: พยายาม `:app:compileDebugKotlin` แล้ว แต่ environment ไม่มี Gradle 8.13 cache และไม่มี network จึงยังไม่ถึง Kotlin compiler
- MySQL: environment ไม่มี mysql/mariadb client/server จึงไม่ได้ execute migration จริงกับ DB
- FCM: ไม่มี Production device/service-account/network context จึงไม่ได้ยิง push end-to-end
- UI มือถือจริง: ต้องตรวจบน device/browser staging

ดูรายละเอียดที่:

- `TEST_REPORT_V10.txt`
- `CHANGED_FILES_V10.txt`
- `NEW_FILES_V10.txt`
- `V10_IMPLEMENTATION_SUMMARY_TH.md`

---

## Rollback แบบปลอดภัย

ถ้าต้อง rollback:

1. หยุด deploy Android V10
2. Restore Backend V9 จาก backup
3. **ไม่ต้องลบ table/column V10** เพื่อ rollback UI/API — column/table เพิ่มเติมไม่รบกวน V9
4. ถ้าจำเป็นต้องย้อน Database จริง ให้ใช้ backup ที่ทำก่อนเริ่ม และทำโดยผู้ดูแล DB เท่านั้น
5. ห้าม DROP table V10 แบบฉุกเฉินบน Production โดยไม่มี backup

