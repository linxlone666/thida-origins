# โครงสร้างโปรเจกต์ 0.8.0

| ส่วน | หน้าที่ |
|---|---|
| ThidaOrigins / ThidaPermissions | Origin จริงและสิทธิ์ OP ที่คงอยู่ |
| ThidaBondData | SavedData UUID ประวัติ พันธะ คูลดาวน์ และเวลาบัฟ |
| ThidaPassives | จ้อง/ตีเชื่อม นรตรา/นิลกาล มานาและการชำระตามเวลา |
| ThidaBonds | กาฝาก/หทัย การยืนยัน เงื่อนไข HP วงจรชีวิต |
| ThidaSpells / ThidaSpell | คาถา Skill 1 และ ID/เลขคาถาที่เก็บได้แบบเดิม |
| ThidaSkillIntegration / ThidaManaIntegration / ThidaHealing | API ค่าร่าย/สเกล ฟื้นมานา และการระบุผู้ฮีล |
| ThidaIronMana | ส่วนเดียวที่รู้จัก API Iron’s โหลดเฉพาะเมื่อ mod มีอยู่ |
| ThidaDollInventory / mixin หุ่น | กฎห้ามโยน/ย้ายออก รักษาหุ่นขณะย้ายช่องและหลังเกิดใหม่ |
| ThidaClient / ThidaHud / ThidaBondHud | ปุ่ม เมนู รายชื่อ สกิน และตำแหน่งในเครื่อง |
| ThidaNetwork | ช่องข้อมูล server-authoritative; ภาพไม่มี clientbound-to-server action |
| ThidaPassiveEffects / ThidaSkillEffects | dispatcher ภาพเส้นทางเดียวและนโยบายผู้มองเห็น |
| ThidaEffectProjection | อ่านสถานะจริงเป็นภาพต่อเนื่อง ไม่เดิน timer เกมซ้ำ |
| ThidaEffectEvent / ThidaEffectBinding | สัญญาข้อมูลและ mapping ของ Thida |
| ThidaClientEffects | dedup, resume, อายุภาพ, handle และ cleanup |
| client/effects | resource reload, entity anchor และตัวอย่าง preview (ไม่มี AAA จริง) |
| ThidaEvents / ThidaDomain* | ทางเข้าเดิมและ compatibility รวมโดเมนเดิม ไม่ออกแบบ ULT ใหม่ |

## การอ่านและจ่ายค่าร่าย

ThidaSpells.validate → ThidaSkillIntegration.resolve หนึ่งครั้งต่อการร่าย → Payment.consume หนึ่งครั้ง → แทนคาถาเก่า/บันทึกผลและ CD → ส่งภาพ
เมนูเรียก resolve เพื่ออ่านอย่างเดียว ไม่ consume
Payment ของ resolution เป็น one-shot และกัน reentrant cast ต่อผู้เล่น ถ้าจ่ายไม่ผ่านผลเก่าและ CD เดิมไม่ถูกแตะ

## ข้อมูลเก่า

ยังใช้ thida_bonds_v7, thida_state, UUID ผู้เล่น, item/Origin registry และ spell index 0/1/2 เดิม
เพิ่ม thida_effect_id และ fx_* เพื่อจำชุดภาพแบบ lazy/idempotent ไม่มีการเปลี่ยน HP คูลดาวน์ หรือ reset เซฟ
คง heart_doll และ registry เก่าเพื่อให้โลกโหลดได้ ไม่กลับไปใช้ไอเทมเก่าเป็นวิธีร่ายหทัย
คง mixin แก้ AFP, การฮีล, การย้ายหุ่น เพราะยังมีหน้าที่และการทดสอบรองรับ
คงพฤติกรรมโดเมนเก่า ตามข้อกำหนดไม่ทำ ULT ใหม่

## Static state

server timeline ใช้ weak key ตาม MinecraftServer และล้างเมื่อ ServerStopped; provider มานา/สเกลและ audience policy reset ตอนนั้น
client ไม่เก็บ player/world ใน manager; Frame เก็บเฉพาะข้อมูลค่า handle ปิดเมื่อสิ้นสุด/เปลี่ยนโลก/reload/ถอด backend
ThidaEffectClient เก็บ client level เพื่อตรวจเปลี่ยนมิติและล้างตอน LoggingOut ส่วน skin cache เดิมมี logout cleanup
