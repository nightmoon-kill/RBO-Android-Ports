# Ragnarok Battle Offline · RBO Android

> **[0.7.0 LAN Experimental — release](https://github.com/nightmoon-kill/RBO-Android-Ports/releases/tag/RBO_Android_0.7.0)** · [คู่มือ LAN / LAN guide](Android/vulkan/LAN.md)
>
> รุ่นทดลอง LAN: เจ้าห้อง P1 / ผู้เข้าร่วม P2 · Experimental LAN: host P1 / joining player P2.

**Ragnarok Battle Offline Recomp for Android**

Special thanks to [https://github.com/RBOios/RBOFFios](https://github.com/RBOios/RBOFFios) as well for letting me fork their project and build on it!
All content in this repository, including the game itself, was written with Codex AI.

## Screenshots / ภาพตัวอย่าง
<img width="5712" height="4284" alt="IMG_0100" src="https://github.com/user-attachments/assets/d4beee92-758f-4d1b-a52a-8326ba7c3585" />
<img width="5712" height="4284" alt="IMG_0101" src="https://github.com/user-attachments/assets/9af6e13e-ba69-4639-9c5f-475a741d68a6" />
<img width="1920" height="1080" alt="Screenshot_20261128-124614" src="https://github.com/user-attachments/assets/f32d64db-ddc1-4a70-8858-91321cb076fa" />
<img width="1920" height="1080" alt="Screenshot_20261128-124605" src="https://github.com/user-attachments/assets/51b4629f-b285-4946-b360-bb4ced51593d" />

[Download APK](https://github.com/nightmoon-kill/RBO-Android-Ports/releases) · [Changelog / รายการอัปเดต](CHANGELOG.md) · [Source & build](Android/vulkan/README.md)

[ภาษาไทย](#ภาษาไทย) · [English](#english)

## ภาษาไทย

**RBO Android** นำ **Ragnarok Battle Offline Expansion 3** มาเล่นบนมือถือ แท็บเล็ต และเครื่องเกมพกพา Android พร้อมปุ่มสัมผัส จอยคอนโทรลเลอร์ และข้อมูลช่วยเล่นบนจอรอง

โปรเจกต์นี้เป็น **Android port** ที่ต่อยอดจาก [RBOios/RBOFFios](https://github.com/RBOios/RBOFFios) โดยรันคำสั่ง x86 ของเกมผ่านตัวจำลอง ร่วมกับระบบกราฟิกและโค้ด native สำหรับเร่งงานบางส่วน จึงใช้คำว่า Android Port เพื่ออธิบายวิธีทำงานให้ตรงกว่าการเรียกว่า Recomp

### ฟีเจอร์

| ฟีเจอร์ | รายละเอียด |
| --- | --- |
| **Vulkan** | เลือกตัวเรนเดอร์ Vulkan และไดรเวอร์ระบบได้จาก Options |
| **Turnip** | นำเข้าไดรเวอร์ Turnip แบบ ZIP และเลือกใช้งานกับ Vulkan บนอุปกรณ์ที่รองรับ |
| **Performance mode** | รวมคำสั่งวาดที่ใช้สถานะเดียวกันเป็นชุด ใช้ native code เตรียมเอฟเฟกต์ และเก็บข้อมูลทรัพยากรในแคช 32 MiB พร้อมอ่านล่วงหน้า เปิดเป็นค่าเริ่มต้น |
| **สูตร HP / SP** | เปิด Inf HP และ Inf SP แยกกัน เพื่อเติมกลับตามค่าสูงสุดขณะอยู่ในด่าน |
| **สูตรแต้ม 999** | เปิด Stat Point 999 และ Skill Point 999 แยกกันได้ เปิดไว้ก่อนเลือกตัวละครได้ และเริ่มเติมเมื่อตัวละครพร้อม |
| **Save State / Load State** | บันทึกและโหลดสถานะระหว่างเล่นได้ 10 ช่อง พร้อมเวลาและภาพย่อ เปิดจากไอคอนแผ่นดิสก์ |
| **Move List อัตโนมัติ** | แสดงรายการท่าตามอาชีพและเพศของตัวละครที่เล่น เลือกดูตัวละครอื่นเองได้ แล้วกด Auto เพื่อกลับมาติดตาม |
| **Monsters** | แสดงจำนวนรวมของมอนสเตอร์ที่เกิดแล้วและยังมีชีวิตในพื้นที่ปัจจุบันด้วยตัวเลขขนาดใหญ่ |
| **Status** | แสดงอาชีพ เลเวล EXP, HP/SP, STR/AGI/VIT/INT/DEX/LUK และแต้ม Stat / Skill ที่เหลือ |
| **จอรอง** | แสดงแท็บ Monsters / Move List / Status บนจอรองที่ Android ตรวจพบ โดยเปิดหน้า Monsters เป็นหน้าหลัก |
| **จอเดียว** | เปิดแท็บข้อมูลช่วยเล่นจากปุ่ม Move List ระหว่างเล่น พร้อมพักเกมชั่วคราว |
| **ปุ่มสัมผัสและจอย** | ตั้งค่าปุ่มในเกมและปุ่มคอนโทรลเลอร์ได้ พร้อมตัวเลือกคืนค่าเริ่มต้น |
| **ENG / THAI** | เลือกภาษาเมนูใน Options → Language ค่าเริ่มต้น ENG และจดจำภาษาที่เลือก |
| **เสียงอินโทร** | รองรับเสียง MPEG Layer I ของ opening.mpg พร้อมเสียงเพลงหน้าเมนูและในเกม |
| **แพตช์เสริม** | มีเมนูจัดการแพตช์ภาษาไทยและ Widescreen แยกกัน |
| **ตรวจสอบปัญหา** | มี Test audio และ Share last game log สำหรับทดสอบเสียงและส่งบันทึกการเล่น |

### เริ่มเล่น

1. ดาวน์โหลด APK จาก [Releases](https://github.com/nightmoon-kill/RBO-Android-Ports/releases) และติดตั้งบน **Android 9 ขึ้นไปที่รองรับ ARM64**
2. เตรียมโฟลเดอร์เกม **Expansion 3** ของคุณเอง โดยมี `RBO_Ex3.exe` และโฟลเดอร์ `DATA` อยู่ในระดับเดียวกัน
3. เปิด **RBO Android** เลือก **Import game folder** แล้วเลือกโฟลเดอร์เกม
4. ตั้งค่าปุ่ม ภาษา และตัวเรนเดอร์ใน Options ตามต้องการ จากนั้นกด **Play**

### ข้อควรรู้

- แอปต้องใช้ไฟล์เกมต้นฉบับของผู้เล่นเอง ไม่ได้รวมตัวเกม Windows หรือข้อมูลเกมต้นฉบับมาให้
- สูตรแต้ม 999 จะหยุดเติมเมื่อปิดสวิตช์ แต่แต้มที่ได้รับไปแล้วยังคงอยู่
- จำนวน Monsters ไม่นับศัตรูที่จะเกิดในรอบถัดไป และจำนวนศูนย์ไม่ได้หมายความว่าจบด่านทั้งหมด ไม่มีรูปมอนสเตอร์หรือเปอร์เซ็นต์ความคืบหน้าด่าน
- Turnip ต้องใช้รุ่นที่เข้ากับอุปกรณ์ หากเริ่มไดรเวอร์ไม่ได้ แอปจะลองไดรเวอร์ Vulkan ของระบบ แล้วจึงใช้ตัวเรนเดอร์ CPU
- เปลี่ยน Performance mode ก่อนเริ่มเกม ประสิทธิภาพจริงขึ้นกับเครื่อง ความร้อน ฉาก และไดรเวอร์
- การติดตั้งทับจะเก็บเกมที่นำเข้าและเซฟไว้เมื่อรหัสแพ็กเกจและลายเซ็นตรงกัน Save State ที่มีส่วนเร่ง native ต้องใช้แอปที่รองรับรูปแบบนั้น
- มีรายงานการทดสอบจากผู้ใช้งานบน **AYN Odin 3 และ Retroid Pocket Duo**

## English

**RBO Android** brings **Ragnarok Battle Offline Expansion 3** to Android phones, tablets and handhelds, with touch controls, controller support and a companion interface for secondary displays.

This **Android port** builds on [RBOios/RBOFFios](https://github.com/RBOios/RBOFFios). It runs the game's x86 instructions through an interpreter, with native graphics and selected native acceleration paths. Android Port describes the implementation more accurately than Recomp.

### Features

| Feature | Details |
| --- | --- |
| **Vulkan rendering** | Select Vulkan and the system graphics driver in Options |
| **Turnip drivers** | Import compatible Turnip ZIP packages and select them for Vulkan on supported hardware |
| **Performance mode** | Batches compatible draws, prepares effects with native code, and reuses asset data in a 32 MiB cache with background read-ahead; enabled by default |
| **HP / SP cheats** | Independent Inf HP and Inf SP switches refill the character to its maximum during a stage |
| **999-point cheats** | Independent Stat Point 999 and Skill Point 999 switches; enable before character selection and refilling starts when the character is ready |
| **Save State / Load State** | Ten slots with timestamps and gameplay thumbnails, accessible through the floppy-disk icon |
| **Automatic Move List** | Follows the active character's class and gender; browse manually and select Auto to resume following |
| **Monsters** | A large total shows living enemies already spawned in the current area |
| **Status** | Class, level, EXP, HP/SP, STR/AGI/VIT/INT/DEX/LUK, and remaining Stat / Skill Points |
| **Secondary display** | Monsters / Move List / Status tabs on a compatible display detected by Android, opening on Monsters by default |
| **Single-screen companion** | Open the same information through the in-game Move List button while gameplay pauses |
| **Touch and controller controls** | Remap game keys and controller buttons, with an option to restore defaults |
| **ENG / THAI interface** | Choose the menu language under Options → Language; defaults to ENG and remembers the selection |
| **Intro audio** | MPEG Layer I soundtrack support for opening.mpg, alongside menu and in-game music |
| **Optional patches** | Separate controls for Thai translation and widescreen patches |
| **Diagnostics** | Test audio and Share last game log for troubleshooting |

### Getting started

1. Download an APK from [Releases](https://github.com/nightmoon-kill/RBO-Android-Ports/releases) and install it on an **ARM64 device running Android 9 or later**.
2. Prepare your own **Expansion 3** game folder, with `RBO_Ex3.exe` and `DATA` side by side.
3. Open **RBO Android**, select **Import game folder**, and choose that folder.
4. Configure controls, language and rendering options as needed, then select **Play**.

### Things to know

- You must supply your own original game files. The Windows game and original game data are not included.
- Turning a 999-point cheat off stops refilling; points already granted remain available.
- Monsters excludes future waves. A zero count does not establish completion of the entire stage. Monster pictures and stage completion percentages are not implemented.
- Turnip compatibility depends on the device and driver package. If initialization fails, the app tries the system Vulkan driver, followed by the CPU renderer.
- Change Performance mode before starting the game. Performance depends on hardware, heat, scene complexity and the driver.
- An in-place update preserves imported game data and saves when the package ID and signing certificate match. States containing native acceleration hooks require a compatible app implementation.
- User testing has been reported on **AYN Odin 3 and Retroid Pocket Duo**.

## Source & credits / ซอร์สและเครดิต

- [Android source and build instructions](Android/vulkan/README.md)
- [Companion and implementation notes](Android/vulkan/COMPANION.md)
- [Changelog](CHANGELOG.md)
- Based on the [RBOios/RBOFFios iOS port](https://github.com/RBOios/RBOFFios).
- Widescreen patch: **marvinelo**. Thai translation patch: **Monster แปล**.
- Bundled library licenses are included in their source directories.

The whole thing was vibe-coded using Codex.
