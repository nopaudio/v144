# ตลาดพระออนไลน์ V12 — สรุปการพัฒนาจาก Stable V11

Stable Base ที่ใช้: สำเนา V11 ชุดด้านในของ ZIP เดิม (`KhaiPhraBan_V11/KhaiPhraBan V11/...`) ซึ่งเป็นชุดที่มี `online.gif`, Coil GIF และ Home/Order ล่าสุดกว่าอีกสำเนา

## สิ่งที่เปลี่ยนใน V12

1. หน้า Premium
   - เปลี่ยนการเลือกประกาศจาก dropdown เป็นรายการขนาดกะทัดรัด
   - เพิ่มช่องค้นหาจากชื่อโพสต์/ตำบล/อำเภอ/จังหวัด
   - แต่ละโพสต์มีปุ่ม `ดู`, `ดัน`, `พรีเมียม`
   - ถ้ามี Premium plan เดียว จะซื้อจากปุ่มได้ทันที
   - ถ้ามีหลาย plan จะแสดงตัวเลือก plan ของโพสต์นั้น
   - ใช้ API เดิม `purchase_boost`, `purchase_premium`, `wallet_summary`, `my_listings`

2. ประวัติในหน้า Premium
   - รวมประวัติดันโพสต์, พรีเมียม, เติมแต้ม และ point transaction ไว้ในรายการเดียว
   - เรียงรายการล่าสุดก่อน
   - ใช้ข้อมูลเดิมจาก `wallet_summary` ไม่เพิ่ม endpoint

3. หน้า Home / Listing card
   - ลด padding และจำนวนบรรทัดใต้ภาพ
   - หัวข้อโพสต์เหลือ layout กระชับ
   - ราคาและที่อยู่จัดในบรรทัดเดียวเมื่อพื้นที่พอ
   - ปรับทั้ง Grid card และ List row

4. เสียงแจ้งเตือน Order
   - เพิ่มเสียง `res/raw/money_in.wav`
   - Order ใช้ Notification Channel ใหม่ `khaiphraban_orders_v12`
   - จำเป็นต้องใช้ Channel ID ใหม่ เพราะ Android ไม่ยอมเปลี่ยนเสียงของ Channel ที่เคยถูกสร้างแล้ว
   - FCM/API เดิมยังส่ง `type=order` เหมือน V11
   - Chat/Admin/Reminder ยังใช้เสียงแจ้งเตือนมาตรฐานเดิม
   - เสียงนี้หมายถึง “มีเหตุการณ์คำสั่งซื้อ” ไม่ใช่หลักฐานว่าได้รับเงินจริงแล้ว

5. Android version
   - `versionCode = 11`
   - `versionName = "V12.0"`

## Database / Migration

V12 รอบนี้ **ไม่มีการเปลี่ยน Database schema** และไม่แก้ migration เก่า ดังนั้น **ไม่มี `migration_v12.sql`** เพราะไม่จำเป็น

Migration เดิมที่คงไว้:
- migration_announcements.sql
- migration_chat.sql
- migration_v3.sql
- migration_v5.sql
- migration_v6.sql
- migration_v7.sql
- migration_v8.sql
- migration_v9.sql
- migration_v10.sql
- migration_v11.sql
- schema.sql

## API เดิม

Backend `api/index.php` มี 54 actions และ V12 ใช้ endpoint เดิมต่อทั้งหมด ไม่มี endpoint ใหม่

## ไฟล์ที่แก้

- `KhaiPhraBan2/android-app/app/src/main/java/com/khaiphraban/marketplace/ui/screens/PremiumScreen.kt`
- `KhaiPhraBan2/android-app/app/src/main/java/com/khaiphraban/marketplace/ui/components/ListingCard.kt`
- `KhaiPhraBan2/android-app/app/src/main/java/com/khaiphraban/marketplace/notifications/NotificationHelper.kt`
- `KhaiPhraBan2/android-app/app/build.gradle.kts`
- `KhaiPhraBan2/android-app/README_ANDROID.md`

ไฟล์ใหม่:
- `KhaiPhraBan2/android-app/app/src/main/res/raw/money_in.wav`
- `READ_ME_FIRST_V12_TH.md`

## ผลตรวจในสภาพแวดล้อมนี้

- PHP syntax: ผ่าน 34/34 ไฟล์
- Android XML parse: ผ่าน 9/9 ไฟล์
- WAV resource: PCM 16-bit mono 44,100 Hz, 0.95 วินาที
- ตรวจ reference `R.raw.money_in`: พบ resource ตรงกัน
- ตรวจ Android version source: V12.0 / versionCode 11
- ตรวจ Backend/API/Database แบบ static: ไม่มีการแก้ Backend, API หรือ SQL เดิม

## ส่วนที่ทดสอบจริงไม่ได้ในสภาพแวดล้อมนี้

Gradle compile/build ไม่สามารถเริ่มได้ เพราะ Gradle wrapper ต้องดาวน์โหลด Gradle 8.13 แต่ sandbox ไม่มี network และไม่มี Android SDK ที่ใช้งานได้ (`local.properties` ของโปรเจกต์ชี้ไป SDK บน Windows ของเครื่องเดิม)

ดังนั้นต้องเปิด `KhaiPhraBan2/android-app` ใน Android Studio เครื่องที่มี Android SDK + network/cache แล้วทำ Gradle Sync และ Build V12 ก่อนปล่อยจริง

Binary/build output เก่า V11 ไม่ควรใช้เป็น V12 และถูกตัดออกจาก ZIP V12 เพื่อป้องกันการนำไฟล์ build เก่าไปปล่อยผิดเวอร์ชัน
รวมถึงลบ cache/IDE-generated folders (`app/build`, `.gradle`, `.idea`, `.kotlin`) ซึ่งไม่ใช่ source ของระบบและสร้างใหม่ได้จาก Android Studio

## ขั้นตอนอัปเดตบนโฮสต์ V12

- อัปโหลดไฟล์ Backend V12 ทับไฟล์เดิมตามขั้นตอนที่ใช้กับเวอร์ชันก่อน
- เข้า Admin > อัปเดตระบบ
- กด `ตรวจและอัปเดต V12`
- V12 ไม่มี schema ใหม่ จึงไม่มี `migration_v12.sql`; ปุ่มนี้จะตรวจและเติม schema เดิมที่ V11 ต้องใช้แบบ safe
- ระบบไม่ DROP / TRUNCATE / reset ข้อมูลเดิม

## V12 Startup Hotfix

แก้กรณี Android Build/Install สำเร็จ แต่แอปเด้งกลับหน้า Home ก่อนแสดงหน้าจอ:
- ป้องกัน Notification Channel / background initialization ทำให้ Application crash
- ยังคงเสียง money_in.wav สำหรับคำสั่งซื้อ หากอุปกรณ์รองรับ
- หากอุปกรณ์ไม่รับ custom sound จะ fallback เป็นเสียงแจ้งเตือนระบบแทน
- ไม่แก้ API, Login หรือ Database/Migration
