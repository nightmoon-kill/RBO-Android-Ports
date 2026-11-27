# Ragnarok Battle Offline · Android Port

<img width="2340" height="1080" alt="Screenshot_20261118_165615" src="https://github.com/user-attachments/assets/dc142020-bac0-44d2-8fc8-e1fd3412e71b" />
<img width="2340" height="1080" alt="Screenshot_20261118_165639" src="https://github.com/user-attachments/assets/7d5a5a4c-22c0-40b5-a590-5580e69ab4b4" />
<img width="2340" height="1080" alt="Screenshot_20261118_165703" src="https://github.com/user-attachments/assets/85c1434f-5556-447e-a8b8-8317150e2f1f" />

The whole thing was vibe-coded using Codex.

[ภาษาไทย](#ภาษาไทย) · [English](#english) 

**[Android 0.5.3 source and build instructions](Android/vulkan/README.md)** · [Changelog / รายการเปลี่ยนแปลง](CHANGELOG.md)

**Base APK: [RBO Android 0.5.2 · ARM64](downloads/RBO-Android-0.5.2-ARM64.apk?raw=true)**

SHA-256: `2c08c144ce4b69011630d9224df516d18c599b8ae03518015c00ab8f17df990b`

ซอร์ส 0.5.3 ครอบคลุมเมนูใหม่ ตัวเรนเดอร์ Vulkan และการสลับไดรเวอร์ Turnip สคริปต์ใช้ APK 0.5.2 เป็นฐานเพื่อรวมเอนจินและฟีเจอร์เดิม

The 0.5.3 source covers the new launcher menus, Vulkan renderer, and Turnip driver selection. Its build uses the 0.5.2 APK for the existing engine and features. The original Android project remains in [Android](Android/README.md).

ขั้นตอนด้านล่างอ้างอิง APK ฐาน 0.5.2 สำหรับ 0.5.3 ให้เข้า **Options → Import Game Folder** แล้วเลือก **Play**

The steps below apply to the 0.5.2 base APK. On 0.5.3, open **Options → Import Game Folder** and then choose **Play**.

## ภาษาไทย

พอร์ตเวอร์ชันทดลองสำหรับเล่น **Ragnarok Battle Offline Expansion 3** บน Android ที่ใช้ ARM64 โปรเจกต์นี้ต่อยอดจากพอร์ต iOS ของ [RBOios/RBOFFios](https://github.com/RBOios/RBOFFios) ตัว APK ต้องใช้ไฟล์เกมต้นฉบับที่คุณเป็นเจ้าของ

### วิธีติดตั้งและเล่น

1. ดาวน์โหลดและติดตั้ง APK บนเครื่อง Android
2. คัดลอกโฟลเดอร์เกมที่ติดตั้ง Expansion 3 แล้วไปไว้บนเครื่อง ให้มี `RBO_Ex3.exe` และโฟลเดอร์ `DATA` อยู่ในระดับเดียวกัน
3. เปิด **RBO Android** กด **Import game folder** แล้วเลือกโฟลเดอร์ดังกล่าว
4. กด **Play Expansion 3** เพื่อเข้าเกม

### ตั้งค่าปุ่มและเสียง

- เปิด **Control settings** ก่อนเข้าเกมเพื่อเปลี่ยนปุ่มของผู้เล่น 1 แยกได้ทั้ง **Game key** และ **Controller** กดปุ่มจริงบนจอยหรือเลือกจากรายการได้ แอปบันทึกทันที และอนาล็อกซ้ายใช้การตั้งค่าเดียวกับ D-pad
- ค่าเริ่มต้นใช้ D-pad หรืออนาล็อกซ้ายเพื่อเดิน และปุ่ม **A / B / X / Y / Start** ตรงกับ **A / S / D / F / 1** ตามลำดับ
- ใช้ **Test audio** ทดสอบเสียงจากหน้าแรก หากเกมมีปัญหา ใช้ **Share last game log** เพื่อส่งบันทึกจากการเล่นล่าสุด
- การติดตั้งทับด้วย APK ที่ใช้ลายเซ็นเดียวกันจะเก็บเกมที่นำเข้าและเซฟเดิมไว้

## English

An experimental **Ragnarok Battle Offline Expansion 3** port for ARM64 devices running Android 9 or later. It builds on the [RBOios/RBOFFios iOS port](https://github.com/RBOios/RBOFFios). The APK requires your own Windows game files.

### Install and play

1. Download and install the ARM64 APK on your Android device.
2. Copy your installed Expansion 3 folder to the device. It should contain `RBO_Ex3.exe` and the `DATA` directory side by side.
3. Open **RBO Android**, tap **Import game folder**, and select that folder.
4. Tap **Play Expansion 3**.

### Controls and audio

- Open **Control settings** before starting the game to remap Player 1’s **Game key** and **Controller** bindings. Press a controller button or choose from the list. Changes save immediately, and the left stick follows the D-pad bindings.
- By default, use the D-pad or left stick to move. **A / B / X / Y / Start** map to **A / S / D / F / 1** respectively.
- Use **Test audio** in the launcher to check sound output. If the game has a problem, use **Share last game log** to share the latest session log.
- An in-place update signed with the same key keeps the imported game and saves.
