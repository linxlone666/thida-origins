# ธิดา 0.6.4 — แก้เข้าโลกที่มี bot แล้วล่ม

ใช้กับ Minecraft 1.21.1 NeoForge 21.1.233, Origins ของ IAFEnvoy 0.4-beta.2 และ Java 21 ติดตั้งรุ่นเดียวกันทั้ง client/server และใช้ JAR ธิดาเพียงรุ่นเดียวใน mods

เลื่อนการกู้คืน bot ที่บันทึกไว้ของ Advanced Fake Players จนตัวรับเริ่มเซิร์ฟเวอร์ทั้งหมดทำงานเสร็จ แก้ Plasmo Voice เข้าถึงเซิร์ฟเวอร์ก่อนพร้อม ไม่มี dependency ใหม่ ไม่ลบ bot หรือแก้ข้อมูลโลก

- [รายละเอียดแก้เข้าโลกล่ม](WORLD_JOIN_FIX_0_6_4_TH.md)
- [คู่มือ Passive](PASSIVES_TH.md)
- [ทดสอบด้วย bot](FAKE_PLAYER_TEST_TH.md)
- [ภาษาไทยในเกม](THAI_ONLY_0_6_3_TH.md)
- [Dependency และการย้าย](MIGRATION_1_21_1_TH.md)
- [จุดเชื่อมต่อเอฟเฟกต์](PASSIVE_EFFECTS.md)

ตรวจสำเนาโลกที่ล่มด้วยม็อด DRC ทั้งชุดแล้ว client เข้าโลกได้ พร้อม bot เดิม 1 คน ผ่าน unit tests 16 และ GameTest 39 รายการพร้อม AFP/Carry On ตรวจเมื่อไม่มี AFP และมี Iron’s จริงแล้วด้วย

ยังใช้ข้อความไทย งานชื่อเล่นเซิร์ฟเวอร์พักไว้ก่อน การเล่นระยะยาวและหลาย client จริงยังต้องตรวจเพิ่ม
