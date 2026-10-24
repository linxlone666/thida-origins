# Thida Origins 0.6.2-neoforge-dev — ทดสอบด้วย Advanced Fake Players

สำหรับ Minecraft 1.21.1 NeoForge 21.1.233, Origins ของ IAFEnvoy 0.4-beta.2 และ Java 21 ติดตั้งรุ่นเดียวกันทั้ง client/server และใช้ JAR ธิดาเพียงรุ่นเดียวใน mods

เพิ่มตัวแก้การส่ง packet ไปยัง connection จำลองระหว่างสร้าง bot ของ Advanced Fake Players 1.0.0 เมื่อมี Carry On 2.2.4.4 ไม่เปลี่ยนกฎ Passive และไม่ต้องแก้ JAR ของม็อดอื่น

- [วิธีทดสอบด้วย bot และผลตรวจ](FAKE_PLAYER_TEST_TH.md)
- [คู่มือ Passive ภาษาไทย](PASSIVES_TH.md)
- [Dependency และการย้าย](MIGRATION_1_21_1_TH.md)
- [จุดเชื่อมต่อเอฟเฟกต์](PASSIVE_EFFECTS.md)
- [การแก้ Singleplayer ใน 0.6.1 ที่รวมอยู่ด้วย](CRASHFIX_0_6_1_TH.md)

ผ่าน unit tests 15 และ GameTest 39 รายการพร้อม AFP/Carry On จริง ตรวจ Singleplayer ด้วยม็อด DRC ทั้งชุดถึงหน้า Create World ได้ ตรวจ regression เดิมพร้อม Iron’s และไม่มี AFP ผ่านด้วย

การเข้าสิง bot, HUD และการเล่นหลาย client จริงใน DRC ยังไม่ได้ยืนยัน DRC ที่ตรวจยังขาด Iron’s, Iron’s Lib และ GeckoLib สำหรับฟื้นมานา
