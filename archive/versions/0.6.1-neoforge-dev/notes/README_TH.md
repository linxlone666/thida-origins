# Thida Origins 0.6.1-neoforge-dev — แก้ Singleplayer ล่ม

สำหรับ Minecraft 1.21.1 NeoForge 21.1.233, Origins ของ IAFEnvoy 0.4-beta.2 และ Java 21 ติดตั้งรุ่นเดียวกันทั้ง client/server และใช้ JAR ธิดาเพียงรุ่นเดียวในโฟลเดอร์ mods

รุ่นนี้แก้รายการ Origin ที่ทำให้ DRC ล่มเมื่อกด Singleplayer โดยไม่เปลี่ยนกฎเกม Passive

- [รายละเอียดการแก้และผลตรวจ](CRASHFIX_0_6_1_TH.md)
- [คู่มือ Passive ภาษาไทย](PASSIVES_TH.md)
- [Dependency และการย้าย](MIGRATION_1_21_1_TH.md)
- [จุดเชื่อมต่อเอฟเฟกต์](PASSIVE_EFFECTS.md)

ผ่าน unit tests 15 รายการและ GameTest 38 รายการทั้งกรณีมี Iron’s และไม่มี Iron’s ตรวจเปิด Singleplayer ด้วยม็อด DRC ทั้งชุดในโฟลเดอร์แยกแล้วถึงหน้า Create World สำเร็จ ยังต้องตรวจการเล่นในโลกจริงและหลายผู้เล่น

DRC ที่ตรวจยังไม่มี Iron’s Spells ’n Spellbooks, Iron’s Lib และ GeckoLib ต้องเพิ่ม dependency ที่เข้ากันได้เพื่อให้ฟื้นมานา การเชื่อม โบนัสนิลกาล และการล้างดีบัฟทำงานได้เมื่อไม่มี Iron’s

ข้อมูล Origin รวมใน JAR แล้ว ไม่ต้องติดตั้ง datapack ซ้ำ
