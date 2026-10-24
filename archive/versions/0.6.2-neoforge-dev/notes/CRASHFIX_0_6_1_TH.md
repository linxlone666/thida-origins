# แก้ Singleplayer ล่ม — Thida Origins 0.6.1

วันที่ 17 ตุลาคม 2569

## สาเหตุและการแก้

โปรไฟล์ DRC 1.0.0 ล่มขณะเปิด Singleplayer และโหลดข้อมูลสำหรับสร้างโลก โดยรายงาน `Unbound values in registry ... origins:origin ... [#origins:origin]` จากไฟล์ layer ที่แอดออนธิดาเพิ่มไว้

รุ่น 0.6.0 ใช้ `"origins": ["#origins:origin"]` ซึ่งผ่านการตรวจในชุดม็อดย่อย แต่ในชุด DRC ทั้งหมดมีเส้นทางโหลดที่นำข้อความนี้ไปใช้เป็นรหัส Origin ที่ไม่ถูกประกาศ รุ่น 0.6.1 เปลี่ยน layer เป็นรายการรหัส Origin โดยตรง: Origin เดิมสิบตัวของ Origins 0.4-beta.2 และ `thida:thida`

ไฟล์กฎเกมของ Passive ไม่ได้เปลี่ยนในการแก้ครั้งนี้ เปลี่ยนเฉพาะข้อมูล layer หมายเลขรุ่น เอกสาร และเครื่องมือตรวจเพื่อครอบคลุมเส้นทาง Singleplayer

## การตรวจซ้ำ

- เพิ่ม `OriginLayerDataTest` เพื่อป้องกันการใช้ tag เป็นรหัส Origin ในรายการตัวเลือก และตรวจว่ามี Origin เดิมกับธิดาครบโดยไม่ซ้ำ
- เพิ่ม `SingleplayerSmoke` ใน source set สำหรับทดสอบเท่านั้น เปิด Singleplayer ในโฟลเดอร์รันแยก รอให้ถึงหน้า Create World แล้วปิด client อัตโนมัติ
- เพิ่มงาน Gradle `verifySingleplayerSmoke` ซึ่งต้องพบข้อความ `THIDA_SINGLEPLAYER_SMOKE_PASS` จึงผ่าน
- รองรับการอ่าน FFAPI, Connector และ JAR ของม็อดอื่นจากโปรไฟล์สำหรับตรวจความเข้ากันได้ โดยไม่บรรจุม็อดเหล่านั้นใน JAR ธิดา

## ผลตรวจรุ่น 0.6.1

- ทำซ้ำข้อผิดพลาดเดิมได้ใน client แยกที่ใช้รายการม็อด DRC ทั้งชุดก่อนแก้ข้อมูล
- หลังแก้ เปิด Singleplayer และถึงหน้า Create World ได้ด้วยชุดม็อดเดิม พบ `THIDA_SINGLEPLAYER_SMOKE_PASS` และ Gradle จบด้วย `BUILD SUCCESSFUL`
- ตัวทดสอบปิดหน้าคำแนะนำ resource pack ของ Yuushya ด้วย `onClose()` เพื่อกลับไปเมนูหลัก โดยไม่เปลี่ยนการตั้งค่าของม็อดนั้น
- Unit tests 15 รายการผ่าน และ GameTest 38 รายการผ่านทั้งกรณีมี Iron’s จริงและไม่มี Iron’s
- ตัวทดสอบเมนูอยู่ใน `src/gametest/` เท่านั้น ไม่รวมใน JAR สำหรับผู้เล่น

การตรวจ Singleplayer นี้ยืนยันการโหลด registry จนถึงหน้าสร้างโลก ยังไม่ได้ยืนยันการเล่นในโลกจริงหรือหลายผู้เล่นกับ DRC ทั้งชุด

ตัวอย่างคำสั่งจากโฟลเดอร์โปรเจกต์ เมื่อกำหนด Java 21 และ Gradle cache ตามสคริปต์ build แล้ว:

```powershell
.\gradlew.bat verifySingleplayerSmoke -PwithoutIron=true -PwithConnector -PallProfileMods "-PcompatProfile=C:/Users/linxs/AppData/Roaming/ModrinthApp/profiles/DRC 1.0.0"
```

การใช้ Connector ในสภาพแวดล้อมพัฒนาอ้างอิง [คู่มือของ Sinytra สำหรับ 1.21.x](https://github.com/Sinytra/Connector/blob/1.21.x/README.md#-developer-guide) โดยเพิ่ม adapter เฉพาะเมื่อส่ง `-PwithConnector` ส่วน `-PallProfileMods` อ่านรายการ JAR จากโปรไฟล์และใช้โฟลเดอร์รันของโปรเจกต์ จึงไม่ใช้โลกเดิมของผู้เล่น

หาก datapack อื่นทับ layer หลัก ให้รวม `thida:thida` ในรายการรหัส Origin ของ layer นั้นด้วย รุ่นนี้ยังมีแท็กเสริมของธิดาไว้ แต่ layer หลักไม่ได้อ้างอิงแท็กเพื่อสร้างรายการตัวเลือก
