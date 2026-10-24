# จุดเชื่อมเอฟเฟกต์ Skill 1–3

กฎเกมเรียก `ThidaSkillEffects.emit` ฝั่งเซิร์ฟเวอร์ ซึ่งส่งผ่าน `ThidaPassiveEffects`
payload `ThidaNetwork.PassiveEffectEvent` ไปยัง `ThidaClientEffects.accept`
ติดตั้งตัวแสดงผลฝั่ง client ด้วย `ThidaClientEffects.installAdapter(ThidaPassiveEffectAdapter)`
ค่าเริ่มต้นเป็น no-op การไม่ติดตั้ง adapter/ไม่มีไฟล์เอฟเฟกต์ต้องไม่หยุดการเชื่อม ดาเมจ ฮีล หรือเวลา
ไม่เรียก API ของ AAA Particles ในคลาสกฎเกม

## ชื่อเหตุการณ์

| event | ความหมาย |
|---|---|
| NAROTRA_ATTEMPT_STARTED | เริ่มจ้องเชื่อมนรตรา |
| NAROTRA_LINKED | นรตราเชื่อมสำเร็จ |
| NAROTRA_LINK_FAILED / LINK_CANCELLED | การจ้องถูกยกเลิก |
| NAROTRA_MANA_RESTORED | ฟื้นมานาตาม Passive |
| NAROTRA_DEBUFF_CLEANSED | ล้างดีบัฟธิดาตาม Passive |
| NILAKAN_ATTEMPT_STARTED | เริ่มเชื่อมนิลกาล |
| NILAKAN_LINKED | นิลกาลเชื่อมสำเร็จ |
| NILAKAN_LINK_FAILED | เชื่อมนิลกาลไม่สำเร็จ |
| NILAKAN_DEBUFF_CLEANSED | ล้างดีบัฟธิดาตาม Passive |
| LINK_PAUSED / LINK_RESUMED / LINK_ENDED | พัก/กลับมาทำงาน/สิ้นสุด Passive |
| DOLL_SELECTED | เลือกชนิดหุ่นในเมนู |
| SPELL_CAST | ร่ายคาถาสำเร็จ |
| SPELL_EFFECT_STARTED / SPELL_EFFECT_ENDED | เริ่ม/หยุดผลคาถา |
| PARASITE_SUMMONED / PARASITE_LINKED / PARASITE_BROKEN | เรียก/เชื่อม/แตกกาฝาก |
| HEART_LINKED / HEART_DEATH_PREVENTED / HEART_ENDED | เริ่มหทัย/ช่วยตาย/สิ้นสุด |
| HEART_ATTEMPT_STARTED | เริ่มพยายามเชื่อมหทัยหลังตรวจสิทธิ์และคูลดาวน์ |
| HEART_INVULNERABILITY_STARTED / HEART_INVULNERABILITY_ENDED | เริ่ม/ครบเวลาอมตะ 5 วินาที ส่งให้ผู้รับได้แม้ธิดาออฟไลน์ |

ให้ใช้ชื่อที่ปรากฏในจุดเรียกซอร์สเป็นสัญญาจริง หากเพิ่มหรือเปลี่ยนชื่อให้อัปเดตตารางนี้พร้อมกัน
สำหรับ SPELL_* ค่า reason เป็นเลขคาถาตามเอกสาร 2/3/4 หรือเหตุผลสิ้นสุด

## ข้อมูลและตำแหน่ง

event: ชื่อเหตุการณ์, passive: ชนิดพลัง, owner/target: UUID เท่าที่มีข้อมูล
dimension: รหัสมิติของพิกัด, x/y/z: พิกัดโลก, hand: มือหรือค่าว่าง, reason: รหัสเหตุผล
ไม่ส่ง IP บัญชีภายนอก หรือข้อมูลส่วนตัวอื่น

ตัวแสดงผลต้องตรวจมิติ client ให้ตรงก่อนใช้พิกัด หากต้องตามตัวให้หา entity จาก UUID ใน client level
เป้าหมายออฟไลน์/อยู่คนละมิติ/ยังไม่โหลด entity ต้องข้ามภาพติดตัวได้ ไม่แปลงเป็น entity ของผู้เล่นอื่น
เหตุการณ์ไม่มี target ที่โหลดอยู่ให้ใช้ตำแหน่งโลกเฉพาะมิติที่ตรง หรือข้ามการแสดงผลอย่างปลอดภัย
ใช้ model/item icon เดิมจนกว่าเจ้าของงานส่งชิ้นงานใหม่

## เพิ่ม AAA Particles ภายหลัง

1. ตรวจเอกสารทางการของ AAA Particles รุ่นที่รองรับ Minecraft 1.21.1 NeoForge จริงก่อนกำหนด dependency และ API
2. สร้าง adapter ฝั่ง client แยก เช่น `dev.thida.client.aaa` แล้วลงทะเบียนผ่าน installAdapter ใน client setup
3. วางทรัพยากรใน `src/main/resources/assets/thida/` โดยสร้างโฟลเดอร์ย่อยตามรูปแบบที่เอกสารรุ่นนั้นกำหนด
4. ผูกชื่อ event + ชนิดพลัง + เลขคาถาเข้ากับ resource ID ของเอฟเฟกต์ ไม่แก้กฎเกมเพื่อเลือกไฟล์ภาพ
5. เพิ่มการลงทะเบียน resource reload/resource configuration ตามเอกสาร API รุ่นที่ตรวจแล้ว
6. จัดเก็บ handle เอฟเฟกต์ต่อ UUID/ชนิดพลัง ล้างเมื่อรับ SPELL_EFFECT_ENDED/LINK_ENDED และเมื่อออกโลก
7. ตรวจว่าถอด adapter/ถอดทรัพยากรแล้ว Passive และ Skill ที่พร้อมใช้ยังทำงาน

รอบนี้ยังไม่ตรวจ signature ของ AAA เพราะไม่ได้เรียก API นั้น และยังไม่เพิ่ม dependency
