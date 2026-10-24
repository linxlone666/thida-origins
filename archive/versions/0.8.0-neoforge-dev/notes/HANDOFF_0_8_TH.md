# ส่งต่อ Thida Origins 0.8.0

## ขอบเขตและจุดย้อนกลับ

ตรวจต้นทางซอร์สจริงที่โฟลเดอร์ thida รุ่น 0.7.1 ก่อนแก้ ไม่ใช้โฟลเดอร์ JAR ส่งมอบแทนซอร์ส
ไม่พบ Git/AGENTS.md ในโปรเจกต์ จึงสำรอง src, docs, tools, Gradle wrapper และ config ก่อนแก้
สำรองอยู่นอกโปรเจกต์: thida-before-handoff-0.7.1-20261021.zip ใน workspace ของงานนี้
ไม่ได้แก้โฟลเดอร์ mods/โลกจริงของผู้เล่น และไม่ลบ run directories หรือข้อมูลผู้เล่น

## โครงสร้างและ API ที่เปลี่ยน

- ต่อ dispatcher เดิมให้เป็น event schema 2 พร้อม epoch/revision, activation/instance, spell แยกจาก reason, anchor, จุดพร้อมมิติ และเวลา
- แยก ThidaEffectProjection อ่านสถานะจริง ออกจาก ThidaPassiveEffects ที่ดูแลเส้นทางส่ง/ผู้มองเห็น/diff
- ThidaClientEffects ดูแล handle ต่อ instance, packet ซ้ำ/เก่า, ไม่มี entity, reload/detach และกู้ loop โดยไม่เล่น one-shot/เสียงเริ่มซ้ำ
- Skill 1 จ่ายจาก Quote ที่อ่านครั้งเดียวต่อ cast ห่อ Payment แบบ one-shot และกัน reentrant call; จ่ายไม่ผ่านไม่หยุดคาถาเก่า/ไม่เริ่ม CD
- ThidaSpell รวม ID และเลขคาถาที่ใช้ต่างกัน โดยคง storage index เดิม
- optional mana/skill provider reset เมื่อ server หยุด และจัดการ provider ที่ไม่พร้อม/ข้อมูลผิดโดยไม่เติมอีกระบบแทน
- ข้อความไทยใน Java ย้ายไป translation keys และมี ThidaMessages fallback สำหรับ dedicated server คงคำเดิมทั้งสอง locale
- จัดรูปแบบ Java ด้วย formatter แบบ dev-only และเอา import ที่ไม่มีการใช้ออก
- version มาจาก gradle.properties แห่งเดียวแล้ว expand ลง metadata

คู่มือหลัก: ARCHITECTURE_TH.md, PERSONAL_MOD_INTEGRATION_TH.md, SKILL_EFFECT_ADAPTER_TH.md

## สิ่งที่ลบ/ยุบและเหตุผล

- castDoll และ blessing ของระบบกดเลขเก่า: เส้นทางเดิมถูก guard ปิดอยู่แล้ว ระบบปัจจุบันร่ายผ่าน ThidaSpells จึงเอาเส้นทางซ้ำที่เข้าไม่ถึงออก
- config healAmount ที่เป็นผู้ใช้รายสุดท้ายของ blessing เก่า ไม่มีสูตรฮีลสมมติแทนในระบบใหม่
- Row และชุดสี private ที่ไม่มีผู้ใช้ใน ThidaHud
- packet LINK_CANCELLED ที่ส่งซ้ำกับ *_LINK_FAILED และการสร้าง packet อีกชุดใน ThidaSkillEffects
- รายการ event/schema เก่าซ้ำในคู่มือ Passive: เปลี่ยนเป็นอ้างคู่มือเดียว
- ไม่รวม build output, cache, log, crash report, release/สำรองเก่า, script migration ครั้งเดียว และเอกสารประวัติในชุด source ส่งมอบ

สิ่งเหล่านี้ไม่ได้แปลว่าลบโลกหรือข้อมูลย้อนหลังจากเครื่องผู้ใช้ ชุด source ใช้ allowlist ใน Package-Handoff.ps1
ไม่มีการเพิ่ม dependency AAA หรือ dependency runtime ใหม่ formatter ใช้เฉพาะตอนเรียก formatJava
repository/dependency ที่ยังใช้กับ Origins และ optional Iron’s/dev compatibility ยังคงไว้ ไม่ตัดตามการเดา

## สิ่งที่รักษาเพื่อ compatibility

registry ID, item เก่า, Origins layer/power, thida_state, thida_bonds_v7, UUID, ประวัติ, สิทธิ์, CD และ timer เดิม
เพิ่ม metadata ภาพแบบ lazy/idempotent โดยไม่ reset ค่าเกมหรือเปลี่ยนรูปแบบ storage index
คง heart_doll/retired registry และ compatibility โดเมน/NBT เดิมให้โลกโหลดได้ ไม่เปิดวิธีใช้สกิลเก่ากลับมา
คง mixin AFP, ฮีล และห้ามย้ายหุ่น เพราะยังจำเป็น ไม่แตะกฎ ULT ที่ยังไม่กำหนด
network protocol เปลี่ยนเป็น 10-neoforge ตาม schema ใหม่ ต้องใช้ client/server รุ่นเดียวกัน

## ผู้รับงานเริ่มตรงไหน

1. อ่าน ARCHITECTURE_TH.md เพื่อดูเจ้าของแต่ละระบบ
2. ม็อดมานา/เวทย์ลงทะเบียน API เดิมตาม PERSONAL_MOD_INTEGRATION_TH.md และตัวอย่างที่ docs/examples/PersonalProviderExample.java
3. ผู้ทำภาพใช้ SKILL_EFFECT_ADAPTER_TH.md, mapping ตัวอย่าง และ client/effects/VanillaPreviewAdapter
4. ยังไม่มี AAA backend จริง ให้ยืนยัน API/source ตรงรุ่นก่อนเชื่อม ไม่เดา method หรือ layout ไฟล์ AAA
5. ค่าร่าย สูตรสเกล/เพดาน ฐานฮีล และสูตรป้องกันหลังกาฝากแตกยังรอข้อมูล ไม่ได้เติมตัวเลขทดแทน

## ผลตรวจ

- Unit tests 30 กรณี รวม lifecycle 9 กรณี ผ่าน
- GameTests 60 กรณี ผ่านทั้งแบบไม่มี Iron’s และแบบมี Iron’s พร้อม AFP/Carry On จากโปรไฟล์
- ตรวจ Quote ของเมนูไม่จ่าย, cast อ่านครั้งเดียว/จ่ายครั้งเดียว, payment ซ้ำไม่จ่ายซ้ำ, failure ไม่แก้คาถาเก่า/CD
- ตรวจ instance หทัยกับ ward แยกกันและบัฟค้างหลัง bond จบ, identity อยู่ใน SavedData, จุดข้ามมิติถูกต้อง
- ตรวจ handle ไม่มี entity, duplicate/stale event, SYNC ไม่ replay burst/เสียง, reload, detach และหมดเวลา
- ตรวจเมนูจริงของเกมกันย้ายเข้ากล่อง/Shift/drop แต่ย้ายช่องผู้เล่นได้และกาฝากไม่แตกบนเคอร์เซอร์
- compileExamples ตรวจตัวอย่าง provider โดยไม่รวมคลาสตัวอย่างใน production JAR
- isolated client แบบไม่มี AAA/ไม่มี Iron’s เปิดถึง Create World ได้ (THIDA_SINGLEPLAYER_SMOKE_PASS)
- dedicated GameTest server เริ่มได้โดยไม่มี AAA/ไม่มีไฟล์เอฟเฟกต์จริง

คำสั่งที่ใช้ (แทน <profile> ด้วยโฟลเดอร์โปรไฟล์สำหรับ AFP):

```text
gradlew.bat formatJava test runGameTestServer build -PwithoutIron=true -PfakePlayerProfile=<profile> --console=plain
gradlew.bat test runGameTestServer build -PfakePlayerProfile=<profile> --console=plain
gradlew.bat verifySingleplayerSmoke -PwithoutIron=true --console=plain
```

หลักฐานในต้นทาง: handoff-regression.log, handoff-regression-iron.log, handoff-client.log และ build-neoforge/test-results/test/
ชุดส่งมอบไม่บรรจุ log/path ส่วนตัวจากเครื่องเดิม

## ข้อจำกัดที่ไม่ได้อ้างว่าตรวจแล้ว

ไม่ได้เล่น AAA จริง ไม่มีไฟล์อนุภาค AAA ของธิดา และยังไม่ยืนยัน signature ของ API 2.2.3 สำหรับการนำไปเรียก
preview vanilla คอมไพล์และตรวจ lifecycle แล้ว แต่ยังไม่ได้ตรวจภาพจริง/เสียง/ตำแหน่งกับงาน AAA หรือ Figura ทุกแบบ
ไม่ได้ทดสอบเครือข่ายหลายเครื่องหรือม็อดแพ็ก DRC ทั้งชุดกับ source 0.8.0 รอบนี้ client smoke ใช้ชุด runtime ขั้นต่ำ
ม็อดที่แก้ inventory หรือ HP ข้าม API มาตรฐานต้องทำ compatibility รายตัว ไม่อ้างว่าจับผู้ฮีลหรือการย้ายของจากทุกม็อดได้
compiler มีคำเตือน deprecated EventBusSubscriber API ของ NeoForge ที่ยังรองรับอยู่; build ผ่าน ไม่อัปเกรด loader เพื่อแก้คำเตือนในงานนี้
