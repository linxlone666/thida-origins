# ธิดา 0.6.4 — แก้เข้าโลกที่มี bot แล้วล่ม

## สาเหตุ

Crash report วันที่ 17 ตุลาคม 2569 เวลา 15:01 และ 15:03 ระบุ `lateinit property minecraftServer has not been initialized` จาก Plasmo Voice 2.1.13 ระหว่าง Advanced Fake Players 1.0.0 กู้คืน bot ที่บันทึกไว้

Advanced Fake Players เรียก `FakePlayerSavedData.respawnAll` ในตัวรับ `ServerStartedEvent` ก่อนตัวรับของ Plasmo Voice ทำงานเสร็จ การเพิ่ม bot เรียกสร้างรายการคำสั่งซึ่งตรวจสิทธิ์เสียง แล้วเข้าถึงเซิร์ฟเวอร์ของ Plasmo Voice ที่ยังไม่พร้อม จึงล่มก่อนผู้เล่นจริงเข้าโลก

## การแก้

- เพิ่ม optional `AdvancedFakePlayerStartupMixin` เลื่อนการเรียกคืน bot จากตัวรับเริ่มเซิร์ฟเวอร์ไปท้าย tick แรก หลังตัวรับเริ่มเซิร์ฟเวอร์ทั้งหมดจบแล้ว
- `ThidaFakePlayerStartup` เก็บงานตามเซิร์ฟเวอร์ เรียกครั้งเดียว แล้วล้างงานออก รวมถึงล้างเมื่อปิดเซิร์ฟเวอร์
- เรียกเมธอดกู้คืนเดิมของ Advanced Fake Players ผ่าน signature ที่ตรวจจาก JAR จริง ไม่ลบรายการ bot หรือแก้ข้อมูลโลก
- ยังใช้ตัวแก้ connection ของ 0.6.2 และภาษาไทยของ 0.6.3 ไม่มี dependency ใหม่ของ Plasmo Voice หรือ Advanced Fake Players ใน JAR ธิดา
- ไม่เปลี่ยนกฎ Passive ระบบมานา หรือพฤติกรรมชื่อผู้เล่น งานชื่อเล่นเซิร์ฟเวอร์ยังพักไว้ก่อน

## การตรวจโลกจริงแบบสำเนา

เพิ่ม source set สำหรับทดสอบ `WorldJoinSmoke` และงาน Gradle `verifyWorldJoinSmoke` ซึ่งเปิดเฉพาะโลกชื่อ `thida-crash-copy` ในโฟลเดอร์รัน `run-neoforge-world-join-smoke/` ตรวจว่า client เข้าโลกแล้ว และพบ bot ที่กู้คืนใน PlayerList จากนั้นปิดเกมทดสอบ

ใช้สำเนาโลก `New World` จาก DRC พร้อมข้อมูล `advanced_fake_players.dat` และรายการ JAR ของ DRC ทั้งชุด ไม่รันกับโฟลเดอร์โลกต้นฉบับ ตัวทดสอบโลกไม่รวมใน JAR ผู้เล่น

คำสั่งทดสอบ เมื่อกำหนด Java 21 และ Gradle cache ตามสคริปต์ build:

```powershell
.\gradlew.bat test jar verifyWorldJoinSmoke -PwithoutIron=true -PwithConnector -PallProfileMods "-PcompatProfile=C:/Users/linxs/AppData/Roaming/ModrinthApp/profiles/DRC 1.0.0"
```

เมื่อผ่านต้องพบทั้ง `THIDA_AFP_RESTORE_COMPLETE` และ `THIDA_WORLD_JOIN_SMOKE_PASS` ใน log ไม่ใช่เพียงการเปิดหน้า Create World การเล่นระยะยาว การเข้าสิง bot และผู้เล่นหลาย client ยังต้องตรวจเพิ่มตามกรณีใช้งาน

## ผลตรวจรุ่นนี้

- Unit tests 16 รายการผ่าน
- GameTest 39 รายการผ่านพร้อม Advanced Fake Players และ Carry On รวมการเชื่อม Passive กับ bot
- ตรวจเมื่อไม่มี Advanced Fake Players และมี Iron’s จริง: regression เดิม 38 รายการผ่าน การทดสอบ AFP เสริมข้ามตามเงื่อนไข ยืนยันว่า optional mixin ไม่บังคับติดตั้งม็อด bot
- เปิดสำเนาโลกที่ล่มด้วยม็อด DRC ทั้งชุด: กู้คืน bot ที่บันทึกไว้ 1 คนสำเร็จ และ client เข้าโลกได้ ผ่าน `THIDA_WORLD_JOIN_SMOKE_PASS` หลังเข้าโลกและรอ 10 วินาที
- ไม่พบข้อผิดพลาด `lateinit property minecraftServer` เดิมในรอบตรวจนี้
- โลกต้นฉบับไม่ได้ใช้เป็นโฟลเดอร์รันหรือถูกแก้ไขระหว่างการตรวจ
