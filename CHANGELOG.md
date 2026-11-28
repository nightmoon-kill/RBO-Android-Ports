# RBO Android 0.6.7

## ประสิทธิภาพการเรนเดอร์และเอฟเฟกต์

- รวมคำสั่งวาด Vulkan ที่อยู่ติดกันและใช้สถานะเดียวกันเป็นชุด ลดภาระ CPU ในการเตรียมและส่งคำสั่งไปยัง GPU โดยรักษาลำดับภาพโปร่งใส การผสมสี และขอบเขตการวาด
- เร่งการเตรียมจุดภาพเอฟเฟกต์ด้วยโค้ด native ครอบคลุมพิกัด ความลึก สี และการตรวจขอบจอ ลดงานจำลองคำสั่ง x86 ในชุดคำนวณที่ถูกเรียกบ่อยขณะใช้สกิล โดยคงผลการคำนวณเดิม
- เก็บบล็อกข้อมูลไฟล์ทรัพยากรในแคช LRU สูงสุด 32 MiB และใช้เธรดผู้ช่วยความสำคัญต่ำอ่านข้อมูลล่วงหน้า รองรับไฟล์ PAC, DAT, IMG, FOB, BMP และ WAV แบบอ่านอย่างเดียวใน DATA
- แคชเก็บข้อมูลไฟล์เพื่อใช้ซ้ำ ส่วนภาพที่ถอดแล้วและภาพบน GPU ใช้ระบบทรัพยากรของตัวเรนเดอร์ เธรดผู้ช่วยไม่แก้ข้อมูลตัวละครหรือสถานะเกม
- **Options → Performance mode** เปิดเป็นค่าเริ่มต้น สามารถเลือกเปิดหรือปิดก่อนเริ่มเกมได้
- เพิ่มข้อมูลตรวจประสิทธิภาพใน **Debugging → Share last game log** ได้แก่ `[Vulkan perf]`, `[Asset cache]`, `[Asset I/O]`, `[CPU hotspots]` และ `[Skill native]`

## สูตรช่วยเล่น

- **Cheat → HP / SP** มี Inf HP และ Inf SP เติมกลับตามค่าสูงสุดของตัวละครขณะอยู่ในด่าน
- **Cheat → Stat Point / Skill Point** มีสวิตช์แยกสำหรับคง **Stat Point 999** และ **Skill Point 999** เปิดไว้ก่อนเลือกตัวละครได้ และจะเริ่มเติมเมื่อตัวละครพร้อม
- การปิดสวิตช์จะหยุดเติม แต้มที่ได้รับไปแล้วยังคงอยู่ เกมใช้ขั้นตอนบันทึกเซฟตามปกติ

## จอรองและข้อมูลตัวละคร

รองรับตัวละครที่กำลังเล่นหนึ่งตัว พร้อมเมนูสามแท็บ:

- **Monsters** — หน้าหลักเมื่อเปิดจอรอง แสดงจำนวนรวมของมอนสเตอร์ที่เกิดแล้วและยังมีชีวิตในพื้นที่ปัจจุบันด้วยตัวเลขขนาดใหญ่ เมื่อเหลือศูนย์จะแสดงสถานะพื้นที่ว่าง โดยไม่นับศัตรูที่จะเกิดในรอบถัดไป และไม่มีรูปมอนสเตอร์
- **Move List** — เลือกอาชีพและเพศตามตัวละครที่กำลังเล่นอัตโนมัติ สามารถเลือกดูอาชีพอื่นเอง และกด **Auto** เพื่อกลับมาติดตามตัวละคร
- **Status** — แสดงอาชีพ เลเวล EXP, HP/SP ปัจจุบันและค่าสูงสุด, STR/AGI/VIT/INT/DEX/LUK รวมถึง Stat Point และ Skill Point ที่เหลือ ในธีมเข้มขอบทอง

อุปกรณ์จอเดียวเปิดดูแท็บเหล่านี้ได้จากปุ่ม Move List ระหว่างเล่น เมื่อยังไม่มีข้อมูลตัวละครหรือพื้นที่ แอปจะแสดงสถานะรอข้อมูล ไม่แสดงค่าศูนย์แทนข้อมูลที่ยังไม่พร้อม

## เสียงอินโทร

- รองรับเสียง **MPEG Layer I** ใน `DATA/Movie/opening.mpg` ผ่านตัวถอดเสียงซอฟต์แวร์ และส่งเสียงเข้าระบบผสมเสียงของเกมตามเวลาของวิดีโอ
- จัดการระดับเสียง การหยุดชั่วคราว การเริ่มเล่นซ้ำ และการล้างเสียงค้างเมื่ออินโทรหยุดหรือจบ

## ภาษา การติดตั้ง และการใช้งาน

- **Options → Language** เลือก **ENG / THAI** ได้ ค่าเริ่มต้นเป็น ENG และจดจำภาษาที่เลือก เมนู กล่องข้อความ และข้อมูลจอรองใช้ภาษาเดียวกัน ส่วนชื่อท่าใช้ชื่อเฉพาะตามรายการเกม
- ชื่อแอปแสดงเป็น **RBO Android** รองรับ **Android 9 ขึ้นไป / ARM64** และต้องใช้ไฟล์เกม Expansion 3 ที่ผู้เล่นมีสิทธิ์ใช้งานเอง
- APK ใช้รหัสแพ็กเกจ `com.nakpaaiyaa.rbo.cmp0600` การติดตั้งทับจะเก็บข้อมูลเกมและเซฟเมื่อรหัสแพ็กเกจและลายเซ็นตรงกับแอปที่ติดตั้งอยู่
- มี Save State / Load State 10 ช่อง พร้อมเวลาและภาพย่อของเกม การบันทึกสถานะระหว่างเล่นอาจหยุดเกมชั่วคราวขณะเขียนข้อมูล
- รองรับตัวเรนเดอร์ Vulkan พร้อมไดรเวอร์ระบบและการติดตั้งไดรเวอร์ Turnip ที่เข้ากันได้

## การตรวจสอบและขอบเขต

- เทียบชุดคำนวณเอฟเฟกต์กับการรันคำสั่ง EX3 ในตัวจำลอง 30,000 กรณี ตรวจตำแหน่งภาพ รีจิสเตอร์ ธง และสถานะ FPU รวมถึงการเปิด/ปิดทางเร่ง การกลับไปใช้คำสั่งเกม และการโหลดสถานะที่หยุดกลางคำสั่ง
- ทดสอบแคช การอ่านพร้อมกัน การอ่านล่วงหน้า ขอบเขตหน่วยความจำ ลำดับคำสั่งวาด ตัวอ่านข้อมูลจอรอง ภาษา และความครบถ้วนของไฟล์ใน APK
- ทดสอบถอดเสียงไฟล์อินโทรจริงครบประมาณ 96 วินาที รวมการปิดเสียง การเริ่มซ้ำ และการจบไฟล์
- ได้รับการยืนยันผลการเล่นจากผู้ใช้งานบน **Snapdragon 7 Gen 4** ประสิทธิภาพจริงขึ้นกับอุปกรณ์ ความร้อน ฉาก เอฟเฟกต์ และไดรเวอร์ จึงไม่ได้รับประกัน 60 FPS ทุกสถานการณ์
- จำนวนมอนสเตอร์เป็นจำนวนในพื้นที่ที่เกิดแล้ว ไม่ใช่จำนวนศัตรูทั้งหมดของด่าน และยังไม่มีเปอร์เซ็นต์ความคืบหน้าด่าน

ซอร์สและวิธีสร้างแอปอยู่ใน [Android/vulkan](Android/vulkan/README.md) พร้อมสิทธิ์การใช้งานไลบรารีที่รวมมาด้วย ตัวติดตั้งและไฟล์ SHA-256 อยู่ใน Assets ของ release

---

# RBO Android 0.5.9

## ภาษาไทย

### เพิ่มจาก 0.5.7

- **Save State / Load State 10 ช่อง** แตะไอคอนแผ่นดิสก์ระหว่างเล่นเพื่อเซฟกลางด่านหรือโหลดกลับไปยังจุดที่บันทึกไว้ แต่ละช่องแสดงเวลาเซฟ พร้อมถามยืนยันก่อนเซฟทับหรือโหลด
- **ภาพย่อในช่องเซฟ** แอปจับเฉพาะภาพเกมและแสดงข้างเวลาเซฟเมื่อบันทึกสำเร็จ เซฟเดิมจาก 0.5.8 โหลดได้ตามเดิม และจะมีภาพย่อเมื่อเซฟลงช่องนั้นอีกครั้ง
- **ไอคอนระหว่างเล่น** ปุ่ม Touch, Cheat, Move List และ Save State ใช้ไอคอนลายเส้นในกรอบจัตุรัสโปร่งใส
- **ชื่อแอป** แสดงเป็น **RBO Android** ใต้ไอคอน ส่วนเวอร์ชัน **0.5.9** ยังแสดงในหน้าแรกของแอป

ไฟล์ติดตั้งรองรับ ARM64 บน Android 9 ขึ้นไป และใช้แพ็กเกจกับลายเซ็นเดียวกับ 0.5.7 และ 0.5.8 สำหรับอัปเดตทับ

## English

### Changes since 0.5.7

- **10 Save State / Load State slots.** Tap the floppy-disk icon during gameplay to save mid-stage or restore a saved point. Each slot shows its save time, and the app asks for confirmation before overwriting or loading a state.
- **Save-state thumbnails.** A gameplay-only screenshot appears alongside the save time after a successful save. Existing 0.5.8 states can still be loaded and gain a thumbnail the next time you save to that slot.
- **In-game icons.** Touch, Cheat, Move List, and Save State now use outline icons in transparent square buttons.
- **App name.** Android displays **RBO Android** under the app icon. The app's home screen still shows version **0.5.9**.

The ARM64 APK requires Android 9 or later and uses the same package ID and signing certificate as 0.5.7 and 0.5.8 for in-place updates.

# RBO Android 0.5.7

## ภาษาไทย

**RBO Android 0.5.7** เพิ่ม Move List สำหรับเครื่องจอเดียวและสองจอ เทียบกับรีลีส 0.5.3

- **เครื่องสองจอ** แสดง Move List บนจอรองเมื่อ Android ตรวจพบจอที่รองรับ เล่นเกมต่อบนจอหลักได้ตามปกติ
- **เครื่องจอเดียว** แตะ **Moves** ข้างปุ่ม Cheat เพื่อเปิดรายการและพักเกมชั่วคราว ปุ่ม Moves จะซ่อนระหว่างดูรายการเพื่อไม่ให้ทับช่องเลือกตัวละคร กด **×** หรือ **Back** เพื่อกลับไปเล่นต่อ
- **ข้อมูลท่า** เลือกดูได้ครบทั้ง 7 อาชีพและสลับตัวละครชาย/หญิง รายการแสดงคำสั่งและเงื่อนไขค่าสถานะในหน้าเดียว พร้อมพื้นหลังซีเปียตามตัวละคร และแสดงข้อมูลสำหรับอ้างอิงระหว่างเล่น

ไฟล์ติดตั้ง **ARM64** สำหรับ Android 9 ขึ้นไป ใช้แพ็กเกจและลายเซ็นเดียวกับ 0.5.3 จึงอัปเดตทับได้ ตัวแอปยังต้องใช้ไฟล์เกมเดิม

## English

**RBO Android 0.5.7** adds a Move List for single- and dual-screen devices since the 0.5.3 release.

- **Dual-screen devices** show the Move List on a compatible secondary display detected by Android while gameplay continues on the main display.
- **Single-screen devices** can tap **Moves** next to Cheat to open the list and pause the game. The Moves button stays hidden while the list is open so the character selector is unobstructed. Press **×** or **Back** to resume.
- **Move reference** covers all seven classes and male/female variants. Commands and stat requirements appear on one screen with sepia character backgrounds.

The **ARM64** APK requires Android 9 or later. It uses the same package ID and signing certificate as 0.5.3 for an in-place update. Original game files are still required.

# RBO Android 0.5.3

## ภาษาไทย

- จัดหน้าแรกเป็น Play, Options, Patch และ Debugging พร้อมแสดงเลขเวอร์ชัน
- เพิ่มตัวเรนเดอร์ Vulkan และตัวเลือกนำเข้าและสลับไดรเวอร์ Turnip ใน Options
- ย้ายการติดตั้งและลบแพตช์ภาษาไทยและ Widescreen ไปที่ Patch พร้อมภาพและเครดิต Monster แปล
- ใช้ APK 0.5.2 เป็นฐานเพื่อรวมเอนจินและฟีเจอร์เดิม

## English

- Groups the launcher into Play, Options, Patch, and Debugging and displays the app version.
- Adds Vulkan rendering and Turnip driver import and selection in Options.
- Groups Thai language and widescreen patch actions under Patch with the Monster translation credit.
- Builds on the original 0.5.2 APK for existing engine features.

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
