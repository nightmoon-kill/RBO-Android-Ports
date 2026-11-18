# RBO Android 0.5.2

## ภาษาไทย

รายการด้านล่างอ้างอิงบันทึก build และการทดสอบจากแชตที่สร้าง APK นี้

### ปรับปรุงความลื่น

- รวมคำสั่งวาดภาพเป็นชุด แล้วกระจายงานให้เธรดเกมและเธรดช่วยวาดสูงสุด 2 ตัว เพื่อลดการรอกันระหว่างเธรดในฉากร่ายเวทและเอฟเฟกต์โปร่งใสซ้อนกัน งานวาดขนาดเล็กยังใช้เธรดเดียว
- ลดงานแปลงสีและผสมพิกเซล พร้อมแยกการส่งเฟรมขึ้นจอออกจากเธรดเกม
- การทดสอบบน Odin 3 ในแชตก่อนหน้ารายงานว่าเล่นลื่นขึ้นจาก 0.4.9

### เมนู Cheat

- เพิ่มปุ่ม Cheat แบบโปร่งใสข้างปุ่ม Pad ระหว่างเล่นเกม
- เปิดหรือปิด Inf HP และ Inf SP แยกกันได้สำหรับผู้เล่น 1
- เมื่อเปิดจะเติมค่าที่เลือกจนเต็มและคงไว้ที่ค่าสูงสุด เมื่อปิดจะกลับมาลดตามปกติ

### ไฟล์ build

- ชื่อไฟล์: `RBO-Android-0.5.2-ARM64.apk`
- ชื่อแอป: **RBO Android**
- Version code: `17`; package: `com.nakpaaiyaa.rbo.perftst`
- รองรับ ARM64 และ Android 9 ขึ้นไป
- ซอร์สโค้ดใน repo ยังเป็นรุ่น 0.4; การอัปเดตนี้เพิ่ม APK ที่สร้างไว้แล้ว

## English

The changes below are based on the build notes and testing recorded in the chat that produced this APK.

### Performance

- Batches draw calls across the game thread and up to two rendering workers to reduce synchronization overhead during spell casting and overlapping translucent effects. Small draws still use one thread.
- Reduces per-pixel color conversion and blending work and moves frame presentation off the game thread.
- Testing on Odin 3 in the earlier chat reported smoother gameplay than 0.4.9.

### Cheats

- Adds a translucent Cheat button next to Pad during gameplay.
- Allows independent Inf HP and Inf SP toggles for Player 1.
- Enabling a cheat immediately refills the selected stat and keeps it at its maximum; disabling it restores normal consumption.

### Build

- Filename: `RBO-Android-0.5.2-ARM64.apk`
- App name: **RBO Android**
- Version code: `17`; package: `com.nakpaaiyaa.rbo.perftst`
- Requires ARM64 and Android 9 or later.
- Repository source remains at 0.4; this update adds the existing compiled APK.

## SHA-256

`2c08c144ce4b69011630d9224df516d18c599b8ae03518015c00ab8f17df990b`
