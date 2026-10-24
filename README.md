# ธิดา — Origins สำหรับ Minecraft 1.21.1 NeoForge

ซอร์สรุ่นพัฒนา **0.7.0-neoforge-dev** ใช้ข้อความไทย คำอธิบายไอเทมแบบรายการ และระบบ Skill 1–3 ตามคำยืนยันล่าสุด ใช้ Java 21, NeoForge 21.1.233 และ Origins ของ IAFEnvoy 0.4-beta.2 รวมตัวแก้ Singleplayer/Advanced Fake Players รุ่นก่อน งานชื่อเล่นเซิร์ฟเวอร์พักไว้ รุ่น 0.6.5 ที่ติดตั้งใน DRC ยังไม่ถูกแทนระหว่างพัฒนา

**ยังรอข้อมูลก่อนเปิดคาถา Skill 1:** ม็อดมานาส่วนตัว สูตรสเกล/เพดาน และฐาน/จำนวนฮีล รวมถึงสูตรลดดาเมจ 10 วินาทีหลังกาฝากแตก จึงเตรียม adapter และไม่เดาตัวเลข

งานตาม text รอบนี้แยกจากการเชื่อมม็อดส่วนตัวในภายหลัง Iron’s เป็น optional เท่านั้น การฟื้นมานารับ adapter ส่วนตัวได้ รายละเอียดอยู่ใน [คู่มือเชื่อมม็อดส่วนตัวภาษาไทย](docs/PERSONAL_MOD_INTEGRATION_TH.md)

## เอกสาร

- [ข้อกำหนด Passive และ Skill 1–3 รุ่น 0.7 ภาษาไทย](docs/SKILLS_V7_TH.md)
- [ผลตรวจและข้อจำกัดรุ่น 0.7](docs/V7_VERIFICATION_TH.md)
- [จุดเชื่อมเอฟเฟกต์ Skill 1–3 ภาษาไทย](docs/SKILL_EFFECT_ADAPTER_TH.md)

- [คำอธิบายไอเทมแบบรายการ รุ่น 0.6.5](docs/TOOLTIPS_0_6_5_TH.md)

- [แก้เข้าโลกที่มี bot แล้วล่ม รุ่น 0.6.4](docs/WORLD_JOIN_FIX_0_6_4_TH.md)

- [ข้อความภาษาไทยและขอบเขตรุ่น 0.6.3](docs/THAI_ONLY_0_6_3_TH.md)

- [คู่มือ Passive นรตราและนิลกาลภาษาไทย](docs/PASSIVES_TH.md)
- [รายละเอียดการย้าย เวอร์ชัน dependency และวิธีติดตั้ง](docs/MIGRATION_1_21_1_TH.md)
- [จุดเชื่อมต่อเอฟเฟกต์สำหรับ AAA Particles](docs/PASSIVE_EFFECTS.md)
- [แก้ Singleplayer ล่มใน DRC รุ่น 0.6.1](docs/CRASHFIX_0_6_1_TH.md)
- [ใช้ Advanced Fake Players ทดสอบ Passive รุ่น 0.6.2](docs/FAKE_PLAYER_TEST_TH.md)
- [พฤติกรรมพลังเดิมและข้อมูลอ้างอิง](docs/POWERS.md)
- [README ของรุ่น Forge 1.20.1 ก่อนย้าย](docs/README_FORGE_1_20_1.md)

## สร้างและตรวจ

```powershell
.\tools\Build-NeoForge.ps1 -GameTests
```

ใช้ JDK 21 ใน `.tooling/java/` หรือกำหนด `JAVA_HOME` ไปที่ JDK 21 สคริปต์สร้าง JAR, ZIP datapack และรัน unit tests กับ GameTest เพิ่ม `-WithoutIron` เพื่อตรวจการทำงานโดยไม่มี Iron’s

JAR อยู่ใน `build-neoforge/libs/` และชุดรุ่น 0.7 อยู่ใน `release/0.7.0-neoforge-dev/` ข้อมูล Origin รวมอยู่ใน JAR แล้ว ไม่ต้องติดตั้ง ZIP datapack ซ้ำ

## ใช้งาน

ติดตั้งแอดออนเวอร์ชันเดียวกันทั้ง client/server พร้อม Origins ของ IAFEnvoy 0.4-beta.2 และ Jupiter เลือก Origin ธิดา ถือหุ่นนรตราจ้องผู้เล่นระยะ 15 บล็อก 10 วินาที หรือถือหุ่นนิลกาลโจมตีภายใน 10 บล็อก หลังเชื่อมเก็บหุ่นในกระเป๋าได้ เปิดเมนูด้วยเลข 1 แถวบน ใช้ลูกกลิ้งเลื่อน คลิกซ้ายเลือก คลิกขวาหรือ Esc ย้อนกลับ เปลี่ยนปุ่มได้ใน Controls

OP ปลด Skill 2/3 แยกด้วย `/thida unlock <ผู้เล่น> skill2` หรือ `skill3` เลข 2 เรียกกาฝาก เลข 3 ใช้พันธะหทัย รายละเอียดและรายการที่ยังรอข้อมูลอยู่ในคู่มือรุ่น 0.7

การฟื้นมานาใช้ adapter ของม็อดส่วนตัว หรือ Iron’s Spells ’n Spellbooks ที่เข้ากันได้เป็นทางเลือกสำรอง หากยังไม่มีระบบมานา กลไกเชื่อม ล้างดีบัฟ โบนัสนิลกาล และ Skill 2/3 ยังทำงาน

ใช้โมเดลและ texture เดิม ULT ยังไม่ตัดสินใจ ไม่มีการเพิ่ม dependency AAA Particles ต้องทดลองหลายผู้เล่นจริงและตรวจม็อดฮีลภายนอกก่อนใช้ในเซิร์ฟเวอร์เล่นจริง
