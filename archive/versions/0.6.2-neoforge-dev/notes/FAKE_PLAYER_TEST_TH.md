# ใช้ Advanced Fake Players ทดสอบธิดา — 0.6.2

## ปัญหาและการแก้

DRC ใช้ `advancedfakeplayers-neoforge-1.21.1-1.0.0.jar` กับ `carryon-neoforge-1.21.1-2.2.4.4.jar` เมื่อสร้าง bot พบ `Payload carryon:sync_scripts may not be sent to the client!`

Stack trace และ bytecode ยืนยันว่า Advanced Fake Players ติดตั้ง listener ที่ไม่ส่ง packet หลัง `PlayerList.placeNewPlayer` จบ แต่ Carry On ส่งข้อมูลระหว่างเมธอดนี้ ทำให้ listener ปกติตรวจช่องทางเครือข่ายของ client จำลองที่ไม่ได้เชื่อมต่อจริง

รุ่น 0.6.2 เพิ่ม `AdvancedFakePlayerConnectionMixin` ที่จุดเริ่ม `ServerCommonPacketListenerImpl.send(Packet, PacketSendListener)` ข้ามการส่งเฉพาะ connection ที่ชื่อคลาสตรงกับ `com.advancedfakeplayers.network.FakeConnection` ก่อนถึงการตรวจ packet ของ NeoForge สอดคล้องกับ listener ของม็อดที่ละทิ้ง packet อยู่แล้วหลังสร้างสำเร็จ ไม่ปิดการตรวจของ connection อื่น ไม่แก้ Carry On JAR และไม่เพิ่ม dependency ใน JAR ธิดา

## วิธีทดสอบ

1. ปิด Minecraft แล้วเปิด DRC ใหม่ให้โหลด Thida 0.6.2 เลือก Origin ธิดาในโลกทดสอบที่เปิด cheats
2. รับหุ่นด้วย `/thida kit` หากยังไม่มี ระบบยังตรวจ Origin ตามปกติ
3. สร้างเป้าหมาย:

   ```mcfunction
   /fakeplayer spawn ThidaTestBot
   /gamemode survival ThidaTestBot
   /tp ThidaTestBot ~ ~ ~3
   ```

4. นรตรา: ถือหุ่นแล้วเล็ง bot ต่อเนื่อง 10 วินาที ในระยะไม่เกิน 15 บล็อกและไม่มีสิ่งกีดขวาง
5. นิลกาล: ถือหุ่นแล้วโจมตี bot ใช้ระยะโจมตีปกติของเกมเพื่อทดสอบ ไม่จำเป็นต้องทำดาเมจสำเร็จ หลังเชื่อมตรวจโบนัสความเร็วและดาเมจประชิด 5%
6. ยกเลิกด้วยเมนูหุ่น R แล้วกด 1 ก่อนเปลี่ยนเป้าหมาย การโยนหุ่นออกจากตัวยังคงยกเลิกสายสัมพันธ์
7. ใช้ `/fakeplayer remove ThidaTestBot` เพื่อลบ bot การนำ bot ออกไม่เท่ากับทดสอบการตัดอินเทอร์เน็ตของผู้เล่นจริง

หากสร้าง bot ก่อนแก้แล้วล้มเหลว ให้เริ่มเซสชันเกมใหม่ก่อน เพื่อไม่ใช้สถานะเข้าเกมที่ค้างจากความล้มเหลวเดิม

ม็อดมี `/fakeplayer control ThidaTestBot` เพื่อเข้าสิง และ `/fakeplayer unpossess` หรือปุ่ม O เพื่อกลับ ตาม [คู่มือผู้พัฒนา](https://www.curseforge.com/minecraft/mc-mods/advanced-fake-player) การทดสอบหลักให้ผู้เล่นจริงเป็นธิดาและ bot เป็นเป้าหมาย การเข้าสิงและ HUD ของทุกม็อดยังไม่ได้ยืนยัน

## ผลตรวจและข้อจำกัด

- Unit tests 15 รายการผ่าน และ GameTest 39 รายการผ่านพร้อม Advanced Fake Players และ Carry On เวอร์ชันเดียวกับ DRC
- ตรวจเปิด Singleplayer ด้วยรายการม็อด DRC ทั้งชุดในโฟลเดอร์รันแยกแล้ว ผ่านถึงหน้า Create World
- ตรวจซ้ำเมื่อไม่มี Advanced Fake Players แต่มี Iron’s จริง: regression เดิม 38 รายการผ่าน และการทดสอบเสริมข้ามพร้อมบันทึก `AFP_COMPAT_TEST_SKIPPED`
- การทดสอบใหม่เรียกขั้นตอนสร้าง bot จริง ตรวจ UUID ใน PlayerList เชื่อมนรตราด้วยการจ้อง 200 ticks เชื่อมนิลกาลด้วย AttackEntityEvent และตรวจโบนัสความเร็ว 5%
- Reflection ใช้เฉพาะใน `src/gametest/` หากไม่ติดตั้งม็อดจะบันทึก `AFP_COMPAT_TEST_SKIPPED` เมื่อทดสอบพร้อมม็อดต้องพบ `AFP_COMPAT_TEST_PASS`
- DRC ที่ตรวจยังไม่มี Iron’s Spells ’n Spellbooks, Iron’s Lib และ GeckoLib ต้องติดตั้ง dependency ที่เข้ากันได้เพื่อทดสอบมานา
- Bot ไม่ทดแทนหลาย client จริง โดยเฉพาะ HUD เครือข่าย disconnect และการเข้าสิง ตัวแก้ไม่เพิ่มการส่งสถานะของม็อดอื่นไปยังผู้เข้าสิง

คำสั่งผู้พัฒนา เมื่อกำหนด Java 21 และ Gradle cache ตามสคริปต์ build:

```powershell
.\gradlew.bat test jar runGameTestServer -PwithoutIron=true "-PfakePlayerProfile=C:/Users/linxs/AppData/Roaming/ModrinthApp/profiles/DRC 1.0.0"
```

แฟล็กนี้อ่าน JAR สองตัวเพื่อทดสอบในโฟลเดอร์รันแยก ไม่แก้โลกหรือ config ของผู้เล่น
