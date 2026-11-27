# Ragnarok Battle Offline · Android Port

<img width="2340" height="1080" alt="Screenshot_20261127_165211" src="https://github.com/user-attachments/assets/6793a5d0-fc05-403b-b986-e1c2335aaecf" />
<img width="2340" height="1080" alt="Screenshot_20261127_165221" src="https://github.com/user-attachments/assets/1c57f012-5119-41c7-ac85-2d25e7033d50" />
<img width="2340" height="1080" alt="Screenshot_20261127_115428" src="https://github.com/user-attachments/assets/9ddb3db2-9888-4808-b824-8cd55e56baa1" />
<img width="2340" height="1080" alt="Screenshot_20261127_115438" src="https://github.com/user-attachments/assets/32e46849-17de-4e9b-af8a-b9d255b464f5" />


The whole thing was vibe-coded using Codex.

[ภาษาไทย](#ภาษาไทย) · [English](#english) 

**[Android 0.5.9 source and build instructions](Android/vulkan/README.md)** · [Changelog / รายการเปลี่ยนแปลง](CHANGELOG.md)

ซอร์ส 0.5.9 เพิ่ม Move List สำหรับเครื่องจอเดียวและสองจอ และรวมเมนู ตัวเรนเดอร์ Vulkan และการสลับไดรเวอร์ Turnip สคริปต์ใช้ APK 0.5.2 เป็นฐานเพื่อรวมเอนจินและฟีเจอร์เดิม

The 0.5.9 source adds the Move List for single- and dual-screen devices and includes the launcher menus, Vulkan renderer, and Turnip driver selection. Its build uses the 0.5.2 APK for the existing engine and features. The original Android project remains in [Android](Android/README.md).


## ภาษาไทย

พอร์ตเวอร์ชันทดลองสำหรับเล่น **Ragnarok Battle Offline Expansion 3** บน Android ที่ใช้ ARM64 โปรเจกต์นี้ต่อยอดจากพอร์ต iOS ของ [RBOios/RBOFFios](https://github.com/RBOios/RBOFFios) ตัว APK ต้องใช้ไฟล์เกมต้นฉบับที่คุณเป็นเจ้าของ

### วิธีติดตั้งและเล่น

1. ดาวน์โหลดและติดตั้ง APK บนเครื่อง Android
2. คัดลอกโฟลเดอร์เกมที่ติดตั้ง Expansion 3 แล้วไปไว้บนเครื่อง ให้มี `RBO_Ex3.exe` และโฟลเดอร์ `DATA` อยู่ในระดับเดียวกัน
3. เปิด **RBO Android** กด **Import game folder** แล้วเลือกโฟลเดอร์ดังกล่าว
4. กด **Play** เพื่อเข้าเกม

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
4. Tap **Play**.

### Controls and audio

- Open **Control settings** before starting the game to remap Player 1’s **Game key** and **Controller** bindings. Press a controller button or choose from the list. Changes save immediately, and the left stick follows the D-pad bindings.
- By default, use the D-pad or left stick to move. **A / B / X / Y / Start** map to **A / S / D / F / 1** respectively.
- Use **Test audio** in the launcher to check sound output. If the game has a problem, use **Share last game log** to share the latest session log.
- An in-place update signed with the same key keeps the imported game and saves.
