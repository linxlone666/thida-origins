# Thida Origins — 0.8.1 developer handoff

Minecraft 1.21.1 / NeoForge 21.1.233 / Java 21 / Origins IAFEnvoy 0.4-beta.2
งานรอบนี้ปรับโครงสร้าง จุดเชื่อมมานา/พลังเวทย์ และระบบภาพ Passive/Skill 1–3 โดยรักษา registry/NBT/กติกาเดิม
ULT ยังไม่ตัดสินใจ งานชื่อเล่นเซิร์ฟเวอร์ยังพักไว้ ไม่มี AAA backend จริงใน JAR นี้

## เริ่มพัฒนาต่อ

- [โครงสร้างและข้อมูลเซฟ](docs/ARCHITECTURE_TH.md)
- [เชื่อมมานา พลังเวทย์ และฮีล](docs/PERSONAL_MOD_INTEGRATION_TH.md)
- [event, mapping, lifecycle, preview และการต่อ AAA](docs/SKILL_EFFECT_ADAPTER_TH.md)
- [กติกา Passive/Skill ที่ยืนยัน](docs/SKILLS_V7_TH.md)
- [กฎหุ่นย้ายช่องได้แต่ห้ามย้ายออก](docs/DOLL_INVENTORY_0_7_1_TH.md)
- [การเปลี่ยนแปลงและผลตรวจรุ่น 0.8.1](docs/CHANGELOG_0_8_1_TH.md)

## Build / ตรวจ

ติดตั้ง JDK 21 และตั้ง JAVA_HOME หรือวาง JDK ใน .tooling/java/ ไม่ต้องใช้ path ของเครื่องผู้สร้าง

```powershell
.\tools\Build-NeoForge.ps1 -GameTests
.\gradlew.bat formatJava
```

หรือ `./gradlew test runGameTestServer jar` บนระบบอื่น
การทดสอบ AFP เพิ่ม `-FakePlayerProfile <โฟลเดอร์โปรไฟล์>`; การทดสอบม็อดแพ็กเพิ่ม `-CompatProfile <โปรไฟล์> -AllProfileMods`
GameTests/preview smoke classes อยู่ source set แยกและไม่รวมใน JAR เล่นจริง
formatter เป็น dev-only task ไม่ใช่ runtime dependency
รุ่นกำหนดที่ gradle.properties:mod_version แห่งเดียว และ expand เข้า metadata ตอน build
JAR อยู่ build-neoforge/libs/; สร้างชุดซอร์สสะอาดด้วย tools/Package-Handoff.ps1 -OutputDirectory <โฟลเดอร์ใหม่>

## Runtime

ต้องมี Origins ของ IAFEnvoy รุ่นที่ระบุและ Jupiter/dependencies ของ Origins
ไม่มีการเชื่อม Iron’s Spellbooks หรือ dependency ของระบบนั้น มานา Passive ต่อผ่าน provider ส่วนตัวเท่านั้น หากไม่มี provider จะข้ามเฉพาะการเติมมานา ไม่ต้องติดตั้ง AAA หากยังไม่ใช้ภาพจริง
Origin อยู่ใน JAR แล้ว ไม่ต้องลง datapack ซ้ำ ใช้ client/server รุ่นเดียวกัน (network protocol 10-neoforge)
อัปเกรดจาก 0.7.x ต้องเปลี่ยนทั้งสองฝั่ง ไม่มีการติดตั้งทับโปรไฟล์ DRC อัตโนมัติ

เลข 1 เปิดเมนูลูกกลิ้ง/คลิกซ้าย คลิกขวาหรือ Esc ย้อนกลับ เลข 2 เรียกกาฝาก เลข 3 ใช้/ขอยกเลิกหทัย เปลี่ยนปุ่มได้
OP ปลด Skill 2/3 แยกด้วย /thida unlock <ชื่อ> skill2 หรือ skill3
ข้อความในเกมยังเป็นไทยทั้งสอง locale และมี fallback ภาษาไทยสำหรับ dedicated server
Skill 1 ใช้ได้โดยไม่มี provider: บัฟ/ดีบัฟ 20%, เลือดไหล 2 HP/วินาที, ฮีล 20% ของ Max HP เป้าหมาย และร่ายฟรี ลดดาเมจหลังกาฝากแตก 20% ผู้พัฒนาม็อดเวทย์ยังติดตั้ง provider เพื่อแทนค่าทั้งชุดและกำหนดราคาต่อได้

กด H เพื่อจัด HUD แยกจากหน้าสกิล (เปลี่ยนปุ่มได้ใน Controls): คลิกแผงเพื่อเลือก ลากเพื่อย้าย หมุนลูกกลิ้งหรือกด −/+ เพื่อปรับขนาด 60–140% กดคืนค่าแผงนี้หรือเสร็จสิ้นเพื่อบันทึก แผงสายสัมพันธ์ ไอคอนสกิล และแถบผู้รับพันธะปรับแยกกันได้
ถือหุ่นชนิดเดียวกด 1 จะเข้าคาถาทันที ถือหุ่นสองชนิดต่างกันสองมือจึงขึ้นหน้าเลือกหุ่น

## ขอบเขตชุดส่งมอบ

รวม Gradle wrapper, source/resources/tests, scripts สำหรับ build/package และเอกสารปัจจุบัน
ไม่รวม cache, build output, log, crash report, release เก่า หรือโลก/ข้อมูลผู้เล่นใน run directories
เก็บสิ่งเหล่านั้นไว้ต้นทางและสำรองก่อนแก้ ไม่ลบโลกเพื่อให้ชุดส่งมอบสะอาด
