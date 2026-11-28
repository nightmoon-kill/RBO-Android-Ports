# RBO Android 0.7.0 — LAN experiment

## ภาษาไทย

- ติดตั้ง APK รุ่นทดลองเดียวกันทั้งสองเครื่อง และนำเข้าเกม EX3 ของแต่ละเครื่อง เชื่อมต่อ Wi-Fi วงเดียวกัน
- หน้าแรก → LAN multiplayer → สร้างห้อง เครื่องนี้เป็น **P1** จด IP และรหัสห้อง 6 หลัก
- อีกเครื่องเลือกเข้าร่วมห้อง กรอก IPv4 และรหัส เครื่องนี้เป็น **P2**
- แอปไม่ตรวจเทียบไฟล์เกม และเตรียมสำเนา SaveData จากเจ้าห้อง เมื่อ P2 พร้อม เจ้าห้องกดเริ่มเกม
- ทั้งสองเครื่องรันเกมเอง ส่งปุ่มให้กันตามลำดับการอ่านคีย์บอร์ด ปุ่มจอยและสัมผัสของเครื่องที่เข้าร่วมควบคุม P2
- ในหน้าจอเลือกผู้เล่น/ตัวละคร ให้แต่ละคนกดปุ่มเข้าร่วมของตน (Start / ปุ่มที่ผูกกับ Pause) แล้วเลือกตัวละครตามขั้นตอนเกม
- ใช้เซฟชั่วคราวของเจ้าห้อง ไม่รวมผลการเล่นกลับเข้าเซฟที่นำเข้าไว้ เซฟของผู้เข้าร่วมไม่ถูกแทนที่ สำเนาเก่าจะถูกล้างเมื่อเริ่มเตรียมห้องครั้งต่อไป
- โหมด LAN รุ่นทดลองใช้ตัวเรนเดอร์ตาม Options พร้อมเปิดตัวเร่งประสิทธิภาพและนาฬิกาเสมือน และข้ามอินโทร ปิดสูตร Save State และหน้าข้อมูลแบบพักเกมบนจอเดียว จอรองยังดูข้อมูลได้
- การออกจากเกมหรือส่งแอปไปเบื้องหลังจะตัดห้อง การเชื่อมต่อขาดหรือข้อมูลตรวจสถานะไม่ตรงกันจะหยุดเซสชัน ไม่มีการเข้าร่วมกลางด่านหรือเชื่อมต่อกลับ
- เชื่อมต่อด้วย IP โดยตรงที่ TCP 27667 ยังไม่มีค้นหาห้องอัตโนมัติ ใช้บน LAN ที่ไว้ใจได้ รหัสห้องไม่ได้เข้ารหัสข้อมูลการรับส่ง

**สถานะ:** สร้างและตรวจลายเซ็น APK แล้ว ทดสอบโปรโตคอลผ่าน TCP loopback และทางรับปุ่ม native แล้ว ผู้ใช้งานยืนยันว่าเชื่อมเล่นร่วมกันได้ ส่วนการแก้เสียงและประสิทธิภาพใน 0.7.0 ยังรอผลจากอุปกรณ์จริง การตรวจสถานะเปรียบเทียบ CPU และข้อมูลโปรไฟล์บางส่วน ไม่ใช่หลักฐานว่าสถานะเกมทั้งหมดตรงกันตลอดด่าน

## English

Install the same experimental APK on both devices, import your EX3 game files, and connect both devices to the same Wi-Fi LAN.

1. Open LAN multiplayer → Create room. The host is **P1**; share its IPv4 address
   and six-digit room code with the other player.
2. The other device chooses Join room, enters that address and code, and becomes **P2**.
3. No game-file comparison is performed. Both prepare a disposable copy of the host's saves.
   When P2 is ready, the host selects Start game.
4. Use each device's Start / Pause binding to join the game's character-selection
   flow, then choose characters as usual. The joining device controls P2.

Each machine runs the game and exchanges ordered keyboard inputs. This is input
lockstep, not video streaming. LAN uses a virtual clock, honors the selected renderer, enables performance acceleration and skips
the intro. Cheats, Save States and the paused single-screen companion are disabled.
Secondary-display information remains available.

The session uses temporary host SaveData on both machines. Progress is not merged
back into imported saves. Temporary copies are cleaned at the next room setup.
Leaving or backgrounding the game disconnects the session. Transport failures or
CPU/profile checksum mismatches stop play; no reconnect or mid-stage join is provided.
Rooms use direct IPv4 on TCP 27667, without automatic discovery. Use a trusted LAN;
the room code does not encrypt traffic.

**Validation:** APK build/signature, TCP loopback protocol tests and native keyboard
hook tests pass. Users confirmed successful LAN play; the final 0.7.0 audio and performance changes still need two-device verification. Checksums compare
the CPU and selected profile data; they do not establish full-world determinism.

## 0.7.0 validation notes

After deferred LAN startup, the normal activity synchronization now activates
GameAudio and requests audio focus. Input packets use a reusable 16-byte buffer
and one write per exchange, preserving their field layout and validation.
LAN enables performance acceleration and honors the selected renderer/driver.
Install this build on both peers; the protocol version rejects mixed builds.
Two-device audio and performance still require device verification.
