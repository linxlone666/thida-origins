# Guide 2 — เอฟเฟกต์ Passive, Skill 1–3 และอัลติ / AAA backend

คู่มือส่งต่อสำหรับ `0.9.0-mayarom-dev` / Minecraft 1.21.1 / NeoForge 21.1.233 / Java 21
สัญญา event schema **2** และไฟล์ mapping schema **1** เป็นคนละเวอร์ชันกัน
ตัวอย่าง backend ที่ตรวจคอมไพล์อยู่ใน [EffectBackendExample.java](examples/EffectBackendExample.java)
เรื่องพลังเวทย์/มานาอ่าน [คู่มือส่วนที่ 1](MAGIC_INTEGRATION_GUIDE_TH.md)

## เริ่มงานตามลำดับนี้

1. Build ม็อดนี้ให้ผ่าน และอ่านตัวอย่าง `VanillaPreviewAdapter` เพื่อเข้าใจ start/update/close
2. ยืนยันรุ่น AAA และ AAA World ที่ทีมจะใช้ ตรวจ API ตรงกับ JAR จริงก่อนเขียน backend
3. ทำม็อด compatibility ฝั่ง client หรือ package client ที่ไม่ถูกโหลดใน dedicated server
4. แทนที่ facade ในตัวอย่างด้วย API เล่น/อัปเดต/หยุดของ AAA รุ่นจริง
5. ส่ง resource pack ที่มีไฟล์เอฟเฟกต์จริงและ mapping ของ Thida เปิด enabled เฉพาะรายการที่ทำแล้ว
6. ทดสอบการคืนภาพและ cleanup ตามรายการท้ายคู่มือ ไม่แก้กติกาสกิลเพื่อเล่นภาพ

## สถานะที่ส่งมอบ

ระบบจริงในรุ่นนี้คือ event schema 2, mapping, lifecycle/handle, การกู้ภาพสถานะ และ backend ตัวอย่างที่เปิดเองได้
**ยังไม่มี AAA backend จริง ไม่มี dependency AAA ใหม่ และไม่ได้อ้างว่าเล่นไฟล์ AAA ได้แล้ว**
AAA Particles และ AAA Particles: World ไม่ถือว่ามี API เดียวกัน
ชื่อ method ของ AAA ไม่ได้ถูกกำหนดในคู่มือนี้ และตัวอย่างไม่ได้สมมติ API ภายนอกขึ้นมา
ก่อนเพิ่มตัวเล่นจริง ให้ผู้รับงานยืนยัน source/javadoc ตรงรุ่นที่ตกลงใช้ รวมการ stop/dispose, resource reload และ thread ที่ API รองรับ

## เส้นทางเดียวและความรับผิดชอบ

กฎเกม → ThidaSkillEffects / ThidaPassiveEffects → PassiveEffectEvent → ThidaClientEffects → ThidaPassiveEffectAdapter
ThidaEffectProjection อ่านสถานะเกมเพื่อคงภาพต่อเนื่อง และเพิ่มเพียง metadata UUID สำหรับภาพ ไม่เปลี่ยน HP มานา บัฟ คูลดาวน์ สิทธิ์ หรือพันธะ
ไม่มี packet ภาพแบบ client → server และ backend ไม่มีทางส่งคำขอสกิลผ่านสัญญานี้
เซิร์ฟเวอร์ส่งเมื่อเริ่ม/หยุด/เปลี่ยนผู้มองเห็น/มิติ ไม่ส่งภาพทุก tick; การตามตัวใช้ entity ฝั่ง client
ฝั่ง server ตรวจ projection ทุก tick แต่ส่งเฉพาะ diff และเมื่อมีผู้รับใหม่

## สัญญา event

ThidaEffectEvent เป็น record, save/read ใช้ NBT บน PassiveEffectEvent เดิม; protocol network เป็น 10-neoforge ทั้ง client/server ต้องตรงรุ่น

| ช่อง | ความหมาย |
|---|---|
| schema | 2 |
| epoch / sequence | รอบเซิร์ฟเวอร์และ revision เพิ่มขึ้น ป้องกัน packet เก่าหรือซ้ำ |
| instance | UUID ของภาพหนึ่งส่วน ใช้เป็น key ของ handle |
| activation | UUID ของการใช้งานหนึ่งรอบ ส่วนย่อยของหทัยใช้ activation เดียวกัน แต่ instance ต่างกัน |
| event / power / spell / component | ชื่อเหตุการณ์, ชนิดพลัง, ID คาถา, ส่วนภาพ เช่น bond/ward/speed |
| phase | ONE_SHOT, START, STOP, SYNC |
| owner / target | UUID เท่าที่มี ไม่ต้องมี entity อยู่ใน client |
| point | dimension + x/y/z ของจุดเล่นภาพ ห้ามแยกมิติออกจากพิกัด |
| ownerPoint / targetPoint | snapshot จุดผู้ใช้/เป้าหมายพร้อมมิติ อาจไม่มีเมื่อออฟไลน์ |
| anchor / hand | USER, TARGET, HAND, WORLD; MAIN_HAND/OFF_HAND หรือค่าว่าง |
| startedTick / endsTick / sentTick | gameTime ฝั่งเซิร์ฟเวอร์ หน่วย ticks; endsTick=0 คือไม่กำหนดหมดเวลา |
| reason | เหตุผล เช่น EXPIRED, REPLACED, PLAYER_UNLINKED, ORIGIN_LOST, DEATH, DIMENSION_CHANGED, OWNER_OFFLINE, TARGET_OFFLINE |

หนึ่งวินาที = 20 ticks เวลาประมาณบน client ใช้ deadline/sentTick กับจำนวน tick หลังรับ packet
spell เป็น narotra_shield, narotra_cleanse, narotra_renewal, nilakan_weakness, nilakan_bleed, nilakan_vulnerability หรือค่าว่าง
ThidaSpell รวม storage index 0/1/2 กับเลขเอกสาร 2/3/4 ไว้ที่เดียว **ไม่ใส่เลขคาถาใน reason อีกแล้ว**
power ใช้ narotra_doll, nilakan_doll, parasite, heart, mayarom ชื่อ NAROTRA/NILAKAN เก่าแปลงที่ dispatcher

## รายชื่อ event ที่ส่งจริง

| event | phase / เวลาเกิด |
|---|---|
| NAROTRA_ATTEMPT_STARTED | START/SYNC ระหว่างพยายามจ้อง |
| NAROTRA_ATTEMPT_ENDED | STOP เมื่อพยายามสิ้นสุด |
| NAROTRA_LINK_FAILED | ONE_SHOT เมื่อจ้องไม่สำเร็จ พร้อมเหตุผล |
| NILAKAN_ATTEMPT_STARTED / NILAKAN_LINK_FAILED | ONE_SHOT การลองเชื่อมด้วยการตี / ล้มเหลว |
| NAROTRA_LINKED / NILAKAN_LINKED | START เมื่อเชื่อม; SYNC เมื่อกู้สถานะที่มีอยู่ |
| LINK_PAUSED / LINK_RESUMED / LINK_ENDED | STOP เมื่อพัก, SYNC เมื่อกลับมาทำงาน, STOP เมื่อปลด |
| NAROTRA_MANA_RESTORED | ONE_SHOT ที่ผู้รับมานาจริง อาจเป็นผู้ใช้หรือเป้าหมาย |
| NAROTRA_DEBUFF_CLEANSED / NILAKAN_DEBUFF_CLEANSED | ONE_SHOT ที่ธิดา |
| DOLL_SELECTED | ONE_SHOT เมื่อ server ยอมรับการเลือกหุ่น |
| SPELL_CAST | ONE_SHOT เมื่อจ่ายมานาและร่ายสำเร็จ มี spell แยกชัดเจน |
| SPELL_EFFECT_STARTED | START/SYNC ภาพคาถาที่กำลังทำงาน |
| SPELL_EFFECT_REPLACED / SPELL_EFFECT_ENDED | STOP เหตุผล REPLACED / EXPIRED / เหตุผลปลดสายสัมพันธ์ |
| PARASITE_SUMMONED | ONE_SHOT เมื่อสร้างหุ่นสำเร็จ |
| PARASITE_LINKED / PARASITE_BROKEN | START/SYNC / STOP หรือ ONE_SHOT หากยังไม่มีภาพต่อเนื่องให้หยุด |
| PARASITE_BUFF_STARTED / PARASITE_BUFF_ENDED | START/SYNC / STOP; component ward, speed, after_defense |
| HEART_ATTEMPT_STARTED | ONE_SHOT หลังผ่านสิทธิ์/คูลดาวน์ ก่อนประเมินเป้าหมาย |
| HEART_LINKED / HEART_ENDED | START/SYNC / STOP; component bond |
| HEART_DEATH_PREVENTED | ONE_SHOT ช่วยชีวิตจริง |
| HEART_INVULNERABILITY_STARTED / HEART_INVULNERABILITY_ENDED | START/SYNC / STOP; component invulnerability |
| HEART_BUFF_STARTED / HEART_BUFF_ENDED | START/SYNC / STOP; component ward หรือ speed |
| ULTIMATE_STARTED / ULTIMATE_ENDED | START/SYNC / STOP; power mayarom, component ultimate, anchor USER |
| ULTIMATE_HIT | ONE_SHOT; power mayarom, component pulse; reason MELEE_CURSE หรือ NILAKAN_ALL_SPELLS |

LINK_CANCELLED ที่เคยส่งซ้ำกับ *_LINK_FAILED ถูกยุบ ไม่ส่ง packet ซ้ำ
LINK_RESUMED เป็น SYNC เพื่อคืน loop โดยไม่เล่น burst ครั้งเดียวซ้ำ; ผู้รับใหม่ใช้ SYNC ของสถานะปัจจุบันเช่นกัน
STOP ปิด handle ก่อนหา entity จึงยังเก็บกวาดได้แม้ผู้เล่นไม่อยู่แล้ว
หทัยจบจะหยุดเฉพาะ bond ส่วน ward/speed/invulnerability ใช้ instance ของตนจน deadline
after_defense ไม่สร้างภาพผลที่ยังไม่มีสูตร/provider

## ภาพอัลติเสน่ห์มายารมณ์

ไฟล์ตั้งต้นปิดไว้ที่ `assets/thida/thida_effects/mayarom.json`
มีสอง binding: loop รอบตัวธิดา และภาพเมื่อโจมตีที่เป้าหมาย
เปลี่ยน resource ID ให้ตรงกับ backend ของคุณและเปิด enabled เมื่อมีไฟล์จริง

ภาพอัลติใช้ `power=mayarom`, `component=ultimate`, `anchor=USER`
เป็นกายแท้ตามตัว ไม่ใช่พื้นอาณาเขตรัศมี 30/100 บล็อก ห้ามใช้ renderer โดเมนเก่าแทนกติกาใหม่
อัลติคงสถานะหลังตาย ออกจากเกม และย้ายมิติ แต่ภาพบน entity ที่ไม่อยู่จะพัก
เมื่อเกิดใหม่/เข้าเกมให้คืนเฉพาะเวลาที่เหลือด้วย SYNC โดยไม่เล่น burst เปิดอัลติซ้ำ
เมื่อหมดเวลา/กดยกเลิกจะส่ง STOP พร้อม ULTIMATE_ENDED ให้ปิด handle

ULTIMATE_HIT ใช้ reason แยกการโจมตีของธิดากับผลนิลกาลจากผู้เชื่อมนรตรา
backend อ่าน `event.reason()` ได้ แต่ mapping parser ปัจจุบันกรองได้เฉพาะ event/power/spell/component
ไม่มีตัวกรอง reason ใน JSON หากต้องการภาพต่างกันให้แยกใน backend หรือเพิ่ม API แบบเข้ากันย้อนหลัง
mapping loop=false ไม่เล่นภาพซ้ำตอนรับ SYNC เช่นเดียวกับสกิลอื่น

## ลงทะเบียน backend และ mapping

ใน client setup ของม็อด compatibility เรียก `ThidaClientEffects.installAdapter(new YourAaaAdapter())`
ให้ API ที่อ้างคลาส AAA อยู่เฉพาะ package client/compat ของผู้รับงาน ลงทะเบียนเมื่อ ModList ยืนยันรุ่นที่รองรับเท่านั้น
ค่าเริ่มต้นเป็น no-op; ถอดด้วย installAdapter(null) จะ close handle ก่อนเปลี่ยน backend
onPassiveEvent(NBT) เป็น callback สังเกตข้อมูลเพื่อรองรับ API เดิม **ห้ามเล่นภาพซ้ำใน callback นี้ถ้าใช้ start/Handle แล้ว**
implement start(event, binding, frame) ให้คืน Handle หรือ null เมื่อไม่รองรับ/ทรัพยากรหาย; Handle.update ตามตำแหน่งและ Handle.close หยุด/คืนทรัพยากรจริง
ใช้ onResourceReload ล้าง resource cache ของ backend และ close เมื่อตัว adapter ถูกถอด

วาง mapping ของ Thida ที่ `assets/<namespace>/thida_effects/*.json`
ไฟล์ตัวอย่างปิดไว้ที่ `assets/thida/thida_effects/example.json` ไม่สร้างภาพในการเล่นปกติ

```json
{
  "schema": 1,
  "bindings": [{
    "enabled": true,
    "event": "*",
    "power": "narotra_doll",
    "spell": "narotra_shield",
    "component": "spell",
    "resource": "yourpack:shield_loop",
    "anchor": "TARGET",
    "offset": [0, 1, 0],
    "rotationDegrees": [0, 0, 0],
    "scale": 1,
    "loop": true,
    "lifetimeTicks": 0,
    "sound": "yourpack:shield_start",
    "volume": 0.6,
    "pitch": 1
  }]
}
```

event/power/spell/component รับ `*` เป็น wildcard; แนะนำ event=* + component สำหรับ loop เพื่อกลับมาจากพักผลได้
resource เป็น logical resource ID ที่ backend แปลเอง **ไม่ใช่การยืนยันโฟลเดอร์หรือรูปแบบไฟล์ AAA**
offset หน่วยบล็อกตามแกนโลก; rotationDegrees เป็นมุม x/y/z; scale ตัวคูณ; loop true หมายถึง handle ต่อเนื่อง
lifetimeTicks=0 ใช้ deadline ของ event (หรือ 20 ticks สำหรับ one-shot); ค่าบวกจำกัดเฉพาะอายุภาพ ไม่ต่อบัฟ
sound/volume/pitch และการหมุน/ขนาดต้องนำไปใช้เฉพาะเมื่อ backend รุ่นจริงรองรับ ตัวอย่าง vanilla แสดงเฉพาะอนุภาคเพื่อดู anchor/lifecycle
loop จะไม่เริ่มจาก event STOP/ONE_SHOT; mapping ที่ loop=false จะไม่เล่นใน SYNC จึงไม่เล่นเสียงหรือ burst ซ้ำเมื่อเข้าเกมใหม่
ไฟล์ mapping ผิด/ไฟล์เอฟเฟกต์หายต้อง skip ไม่ throw ออกสู่เกม มี log ครั้งเดียวต่อ resource/key ต่อรอบ reload สูงสุด 128 key
ไม่มีไฟล์เอฟเฟกต์จริงในชุดนี้ และไม่จำเป็นต้องสร้างไฟล์ปลอมให้ผ่านตัวตรวจ

## มิติ จุดยึด และผู้มองเห็น

USER/TARGET หา entity ตาม UUID ใน client level ปัจจุบัน หากไม่พบให้หยุด handle ติดตัวและรอ entity กลับมา
HAND เป็นจุดโดยประมาณใกล้มือ รองรับมือหลักซ้าย/ขวาและมือรอง ไม่อ้างว่าเป็นตำแหน่ง bone ของ Figura
WORLD ใช้ point.dimension ตรงกับ client เท่านั้น; อย่าใช้ตำแหน่งผู้ใช้แทนเป้าหมายข้ามมิติ
Frame มีจุดที่รวม offset แล้ว, yaw/pitch, elapsedTicks และ remainingTicks (Long.MAX_VALUE คือไม่กำหนด)
backend ไม่ควรเก็บ entity/player/world ใน static state; เก็บ handle ต่อ instance และอ่าน Frame ใหม่แทน

กำหนดผู้รับจากจุดเดียวด้วย `ThidaPassiveEffects.installAudiencePolicy` ค่าเริ่มต้น PARTICIPANTS
มี OWNER, TARGET, PARTICIPANTS_AND_NEARBY (มิติของจุดภาพและรัศมี 64 บล็อก) ไม่ส่งทั้งเซิร์ฟเวอร์โดยอัตโนมัติ
ผู้รับใหม่/กลับเข้าเกมได้ SYNC เฉพาะสถานะที่ยังทำงาน ไม่มีการ replay ONE_SHOT เก่า

## Resource reload / เปลี่ยนโลก

F3+T โหลด mapping ใหม่ผ่าน RegisterClientReloadListenersEvent และ SimplePreparableReloadListener ของ Minecraft
ลำดับคือ close handle → แจ้ง backend onResourceReload → เปลี่ยน mapping → กู้เฉพาะ loop
backend AAA ต้องตรวจเอกสารรุ่นจริงว่าต้อง unregister/dispose/reload ไฟล์อย่างไร ไม่ใช้ชื่อ method ที่เดาขึ้น
ออกโลก/เปลี่ยนเซิร์ฟเวอร์ clear instance, revision และ handle; เปลี่ยนมิติหยุด handle เดิมแล้ว resolve loop ใหม่อย่างปลอดภัย
การถอด adapter หยุดทุก handle โดยไม่ล้างพันธะหรือ timer เซิร์ฟเวอร์

## ตัวอย่างที่รันได้ครบหนึ่งเส้นทาง

`/thidafx preview` เป็น client command: ติดตั้ง VanillaPreviewAdapter แบบสมัครใจ → สร้าง PREVIEW ในเครื่อง → เลือก binding resource `thida:preview/end_rod` → start คืน Handle → update สร้างอนุภาค END_ROD ตาม Frame → ครบ 100 ticks เรียก close
ไม่มี packet ขอร่าย ไม่มีดาเมจ/ฮีล/ค่าใช้จ่าย และไม่สร้าง/ปลดพันธะ
`/thidafx off` ถอดตัวอย่าง; หากมี backend จริงที่ลงทะเบียนแล้ว คำสั่ง preview จะเปลี่ยนไปใช้ตัวอย่างจนกว่าจะลงทะเบียน backend จริงใหม่ จึงใช้เฉพาะตอนพัฒนา
ตัวอย่างนี้อยู่ใน src/main/java/dev/thida/client/effects/VanillaPreviewAdapter.java และใช้ API vanilla ที่คอมไพล์จริง ไม่ใช่ pseudo AAA
ประกอบกับตัวอย่าง JSON ด้านบน ผู้รับงานเปลี่ยนเพียง backend และ resource/mapping โดยไม่แก้คลาสสกิล
การทดสอบ EffectLifecycleTest ใช้ handle จำลองนับ start/close ตรวจ packet ซ้ำ, STOP ไม่มี entity, reload, detach และ SYNC ไม่ replay one-shot

## ตัวอย่าง backend สำหรับนำไปต่อ

`docs/examples/EffectBackendExample.java` แสดงการเรียก `installAdapter`, การส่ง event/binding/Frame
และ Handle ที่ปิดซ้ำได้อย่างปลอดภัย `Backend` กับ `Playback` เป็น facade ของตัวอย่าง
ให้ผู้รับงาน implement ด้วย AAA รุ่นที่ตกลงใช้ ไม่ใช่ชื่อคลาสจากม็อด AAA จริง
ตัวอย่างนี้ตรวจคอมไพล์ด้วย `compileExamples` แต่ไม่ลงทะเบียนอัตโนมัติและไม่รวมใน JAR

Backend.start ต้องอ่าน resource ของ binding และคืน Playback ที่ถือ instance ของเอฟเฟกต์นั้น
update ใช้ Frame ใหม่ ไม่เก็บ world/player เดิมข้ามการเปลี่ยนโลก
close ของ Playback ต้องหยุดภาพและเสียงที่ผูกอยู่ รวมถึงคืนทรัพยากรตาม API จริง
Backend.reload ล้าง cache ของไฟล์หลัง handle เดิมถูกปิดแล้ว ไม่สร้างบัฟหรือส่งคำขอร่าย

## เกณฑ์ตรวจรับงานเอฟเฟกต์

- ไม่มี AAA: เปิดเกม/เซิร์ฟเวอร์ได้ สกิลทำงานตามเดิม ไม่มีภาพปลอมที่ทำให้เข้าใจว่ารองรับแล้ว
- มี AAA/backend/resource: ภาพแต่ละ event เล่นที่ USER/TARGET/HAND/WORLD ถูกคนและถูกมิติ
- เริ่ม/ยกเลิก/หมดเวลา: loop ไม่ซ้ำและไม่ค้างหลัง STOP แม้หา entity ไม่พบ
- เข้าเกมระหว่างบัฟทำงาน: คืน loop ด้วย SYNC แต่ไม่เล่น burst/เสียงเปิดสกิลซ้ำ
- เป้าหมายออฟไลน์/ตาย/กลับมา: เคารพ event lifecycle ไม่ปลดพันธะจากโค้ดภาพ
- ย้ายมิติ/ออกโลก/เปลี่ยนเซิร์ฟเวอร์: ไม่มี handle ของโลกเดิมค้าง
- F3+T/ถอด backend: ปิดของเก่าและคืนเฉพาะ loop ปัจจุบัน ไม่คืน one-shot ที่จบแล้ว
- ไฟล์หาย/JSON ผิด/AAA ไม่รองรับ resource: ข้ามภาพอย่างปลอดภัย ไม่มี log ซ้ำทุกเฟรม
- ทดสอบ dedicated server โดยไม่มีคลาส AAA/client ถูกโหลดจาก common setup
- ส่งมอบรุ่น AAA ที่รองรับ dependency จริง backend ไฟล์เอฟเฟกต์ mapping สิทธิ์ไฟล์ และผลทดสอบ
