ตลาดพระออนไลน์ V14 — READ ME FIRST
วันที่จัดชุด: 2026-08-08

ฐานที่ใช้
- ใช้ KhaiPhraBan_V13.zip ที่ผู้ใช้ส่งมาเป็น Stable Base
- ตรวจพบว่า ZIP V13 มีสำเนาโปรเจกต์ซ้ำซ้อนอยู่ชั้นใน KhaiPhraBan_V13/KhaiPhraBan_V13/
  โดย Backend ซ้ำเหมือนกัน และ Android ชั้นในมี .gradle/.idea/app/build ที่เป็น cache/build output
- ชุด V14 นี้ใช้ source หลักชั้นนอกเป็นฐาน และไม่แพ็กสำเนาซ้ำ/cache/build output เพื่อให้โปรเจกต์สะอาด
- ไม่ลบ source/function เดิมของ V13 และไม่แก้ migration เก่าของ V13

สิ่งที่ทำใน V14

1) แก้ตั้งชื่อภาษาไทย
- Backend เดิมตรวจชื่อด้วย Unicode Letter/Number แต่ไม่ได้รวม Unicode Mark
  ทำให้สระ/วรรณยุกต์ไทย เช่น ิ ี ึ ื ่ ้ ๊ ๋ ์ ถูกปฏิเสธ
- V14 เพิ่ม \p{M} ทั้ง Backend และ validation ใน Android
- ข้อความ validation ถูกย้ายมาแสดง “ภายใน” AlertDialog ตั้งชื่อ
  จึงไม่ไปเตือนด้านหลังหน้าต่างอีก
- Login/username เดิมไม่ได้เปลี่ยน

2) ร่วมสนุกลุ้นพระด้วยแต้ม + เลขรัฐบาล 2 ตัว
- Admin เว็บ: Admin > ลุ้นพระ
- สร้างรอบแบบฉบับร่าง ตั้งชื่อรอบ ชื่อ/รายละเอียดรางวัล รูปรางวัล วันที่งวด และแต้มต่อเลข
- เปิด/ปิดรับเลขได้ และเปิด active round ได้ครั้งละ 1 รอบ
- สมาชิกเข้าได้จากหน้า “ของฉัน” > “ร่วมสนุกลุ้นพระ • เลขรัฐบาล 2 ตัว”
- แสดงเลข 00–99, เลขที่ถูกซื้อแล้ว, เลขของสมาชิก, แต้มคงเหลือ, รางวัล และวันที่งวด
- เลขหนึ่งเลขมีเจ้าของได้เพียง 1 คนต่อรอบ
- สมาชิกหนึ่งคนซื้อได้หลายเลขถ้ามีแต้มพอ แต่ซื้อเลขที่มีเจ้าของแล้วไม่ได้
- การซื้อใช้ request_key + MySQL transaction + row lock + unique key
  เพื่อป้องกันหักแต้มซ้ำและป้องกันเลขซ้ำเมื่อมีการซื้อพร้อมกัน
- ใช้ point_wallets และ point_transactions เดิม ไม่สร้าง wallet ซ้ำ
- เพิ่ม point transaction type: lottery_purchase
- Admin กรอกเลขผลรางวัล 2 ตัวเพื่อประกาศผล ระบบหาเจ้าของเลขและแสดงผู้ชนะ
- หลังประกาศแล้ว V14 ไม่อนุญาตให้เปลี่ยนเลขผลย้อนหลัง
- การแจ้งผลใช้ Firebase push ชนิด admin_notification เดิม เพื่อให้โครง notification V13 ไม่ต้องเปลี่ยน

Database
- ไม่แก้ backend/database/migration_v13.sql หรือ migration เก่าใด ๆ
- เพิ่ม backend/database/migration_v14.sql
- migration V14 เป็น additive/idempotent ไม่มี DROP / TRUNCATE / DELETE
- เพิ่มตาราง lottery_rounds และ lottery_entries
- การเพิ่ม ENUM lottery_purchase จะคงค่า ENUM เดิมทั้งหมดไว้ แล้ว append ค่าใหม่เท่านั้น
- backend/database/schema.sql ถูกอัปเดตสำหรับการติดตั้งใหม่

Android version
- versionCode = 13
- versionName = V14.0

ขั้นตอนอัปเกรด Production
1. สำรอง Database ก่อนตาม workflow เดิม
2. อัปโหลด backend V14 ทับระบบเดิม
3. เข้า Admin > อัปเดตระบบ
4. กด “ตรวจและอัปเดต V14”
5. ตรวจว่า “ร่วมสนุกลุ้นพระ V14” ขึ้นพร้อม
6. เข้า Admin > ลุ้นพระ สร้างรอบฉบับร่าง ตรวจข้อมูล แล้วกดเปิดรับเลข
7. เปิด KhaiPhraBan2/android-app ใน Android Studio และ Build V14 ตาม workflow เดิม

ข้อจำกัดการทดสอบใน sandbox
- PHP syntax ตรวจได้จริง
- Android XML ตรวจได้จริง
- Android Gradle build จริงทำไม่ได้ เพราะ sandbox ไม่มี Android SDK และ Gradle wrapper ต้องดาวน์โหลด distribution แต่ network/DNS ถูกปิด
- ไม่มี MySQL/MariaDB server/client ใน sandbox จึงไม่ได้ execute migration กับฐานจริง
- ไม่มี Firebase credentials/production network จึงไม่ได้ส่ง push จริง
- PHP CLI ใน sandbox ไม่มี extension mbstring แต่ Backend production เดิมกำหนด mbstring อยู่แล้ว
  จึงทดสอบ regex ภาษาไทยด้วย preg_match แยกโดยตรงแทน และ syntax PHP ทั้งหมดผ่าน

แนะนำก่อนขึ้น Production
- สำรอง DB
- ทดสอบ Admin > อัปเดตระบบบน staging/สำเนาฐานก่อนถ้ามี
- สร้างรอบทดสอบแต้มต่ำ แล้วลอง 2 บัญชีซื้อเลขเดียวกันเพื่อยืนยันว่าอีกบัญชีถูกปฏิเสธ
- ทดสอบเลข 00, 07, 99 และชื่อไทยที่มีสระ/วรรณยุกต์
