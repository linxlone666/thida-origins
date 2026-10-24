# ส่งมอบ Thida Origins 0.7.0-neoforge-dev

ขอบเขตตาม text สองไฟล์ที่ส่งวันที่ 18 ตุลาคม 2569 และคำยืนยันก่อนหน้า: Passive 1/2, Skill 1/2/3, ปุ่ม/เมนู, HUD สายสัมพันธ์/ผู้รับหทัย, ข้อความไทย, ข้อมูลถาวรเซิร์ฟเวอร์ และจุดเชื่อมเอฟเฟกต์

## ไฟล์หลักที่ปรับ

- `ThidaBonds`, `ThidaBondData`, `ThidaPermissions`: สายสัมพันธ์ สิทธิ์ปลด ตัวจับเวลา HP การยืนยัน และการบันทึก UUID
- `ThidaSpells`, `ThidaSkillIntegration`: คาถาแยกคูลดาวน์ แทนผลเดิม และ adapter ค่าร่าย/สเกลที่ยังรอข้อมูล
- `ThidaManaIntegration`, `ThidaIronMana`, `ThidaPassives`: รับระบบมานาส่วนตัวและคำนวณ 5% Max Mana ต่อผู้รับ Iron’s เป็น optional
- `ThidaHealing` และ mixin ฮีล/อมตะ: แยกฮีลตนเองจากสกิล ยา ธรรมชาติ ผู้อื่น และแหล่งไม่ทราบ
- `ThidaClient`, `ThidaBondHud`, `ThidaHud`, `ThidaHudConfig`: เลข 1/2/3 เมนูลูกกลิ้ง tooltip ชื่อผู้เชื่อม แผงสกิน/HP/สถานะ และลากตำแหน่ง
- `ThidaSkillEffects`, `ThidaPassiveEffects`, `ThidaClientEffects`, `ThidaNetwork`: เหตุการณ์ server → client สำหรับ adapter ภาพและเสียง
- ภาษาไทยทั้ง locale, ยกเลิกสูตรคราฟต์หทัย/กาฝาก และใช้ภาพไอเทมเดิมเป็นตัวแทน
- Unit/GameTest, เอกสารไทย และชุดกติกาเก่าที่เก็บใน migration-legacy

รายการไฟล์เทียบกับสำรองก่อนเริ่มรุ่น 0.7 อยู่ใน `CHANGED_FILES_V7.txt`

## ผลตรวจและการใช้

Unit tests 21 กรณี และ GameTests 55 กรณีผ่านทั้งแบบมี/ไม่มี Iron’s
ดู `V7_VERIFICATION_TH.md` สำหรับหลักฐาน คำสั่ง และข้อจำกัดการตรวจหน้าจอ/เครือข่ายจริง
Minecraft 1.21.1, Java 21, NeoForge 21.1.233 ที่ตรวจ และ Origins IAFEnvoy 0.4-beta.2
ติดตั้งแอดออนรุ่นเดียวกันทั้ง client/server และนำ JAR ธิดารุ่นอื่นออกจากโฟลเดอร์ mods ก่อนใช้รุ่นนี้
Origin รวมอยู่ใน JAR แล้ว ไม่ต้องลง datapack ซ้ำ และไม่ต้องลง Iron’s เพื่อใช้สายสัมพันธ์หรือ Skill 2/3
ไฟล์รุ่นเสถียรในโปรไฟล์ DRC ยังไม่ได้ถูกแทนโดยอัตโนมัติ

## งานภายหลังตามที่ตกลง

รับม็อดมานา/สแตทส่วนตัว สูตรสเกล/เพดาน/ค่าฮีลและค่าร่ายจริง แล้วผูก adapter ตาม `PERSONAL_MOD_INTEGRATION_TH.md`
ก่อนมีข้อมูล คาถา Skill 1 แสดงว่าไม่เปิดใช้งาน และไม่มีการเดาโบนัสลดดาเมจหลังกาฝากแตก
รับเอฟเฟกต์จริงสำหรับ AAA ตาม `SKILL_EFFECT_ADAPTER_TH.md` ไม่มี dependency ใหม่ในรุ่นนี้
ULT และกฎซ้อนระหว่าง ULT ยังไม่ตัดสินใจ ชื่อเล่นเฉพาะเซิร์ฟเวอร์ยังพักไว้
