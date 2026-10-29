# Guide 1 — เชื่อมม็อดพลังเวทย์ มานา และการรักษา

คู่มือส่งต่อสำหรับ `0.9.0-mayarom-dev` / Minecraft 1.21.1 / NeoForge 21.1.233 / Java 21
ใช้คู่กับ [ตัวอย่างที่ตรวจคอมไพล์](examples/PersonalProviderExample.java)
ภาพและเสียงมี [คู่มือแยก](EFFECTS_INTEGRATION_GUIDE_TH.md) ไม่ลงทะเบียนผ่าน API ชุดนี้

## เริ่มงานตามลำดับนี้

1. Build โปรเจกต์ด้วย JDK 21 ให้ผ่านก่อน จากนั้นให้ม็อด compatibility ของคุณอ้างอิง Thida รุ่นนี้
   ใช้ JAR ที่ build เองหรือ dependency ภายในทีม ไม่ต้องเปลี่ยน registry, NBT หรือ Origin ID
2. เชื่อมการอ่านสเตตัสจาก API จริงของม็อดคุณ ห้ามอ่านค่าพลังเวทย์/ราคาจาก packet ของ client
3. ลงทะเบียนเฉพาะ provider ที่พร้อมใช้งานบน logical server ระหว่าง server startup
   ลงทะเบียนใหม่ทุกครั้งที่เปิดเซิร์ฟเวอร์หรือโลก integrated server
4. ทดสอบค่าพื้นฐานก่อน ตามด้วยค่าหลังสเกล มานาไม่พอ ออฟไลน์ และการเข้าโลกใหม่

API นี้ไม่ค้นหาม็อดสเตตัสให้เอง และ `install` แต่ละตัวรองรับ provider ปัจจุบันเพียงตัวเดียว
หากมีหลายม็อดต้องทำตัวรวมให้เหลือ provider เดียวก่อนลงทะเบียน
ไม่ต้องสร้าง tick loop สำหรับคาถาหรือฟื้นมานาซ้ำกับ Thida

## API ที่ใช้จริง

| API | หน้าที่ | ไม่มี provider |
|---|---|---|
| ThidaManaIntegration.Adapter | ฟื้นมานา Passive จาก Max Mana ของผู้รับ | ข้ามเฉพาะการฟื้นมานา |
| ThidaSkillIntegration.Adapter | ผลหลังสเกลและราคาคาถา Skill 1 / ลดดาเมจหลังกาฝากแตก | ใช้ค่าพื้นฐานที่ยืนยันในรุ่น 0.8.1 และร่ายฟรี |
| ThidaMayaromScaling | อ่านพลังเวทย์สำหรับสูตรดาเมจประชิดอัลติ | ใช้ดาเมจประชิดเดิม ไม่มีค่าพลังเวทย์สมมติ |
| ThidaHealing | ระบุผู้ฮีล/ผู้รับ/ชนิดฮีลของ Skill 3 | แหล่งไม่ทราบให้ฮีลก่อนแล้วจบพันธะ |

ลงทะเบียนบน server lifecycle ด้วย install(adapter), ถอดด้วย install(null); ServerStopped reset provider ให้ไม่ค้างข้ามเซิร์ฟเวอร์
อย่าลงทะเบียน provider ที่เก็บ ServerPlayer หรือ MinecraftServer เก่าไว้ใน static field ใช้ UUID และอ่านข้อมูลปัจจุบันเมื่อถูกเรียก
API ทั้งหมดนี้ทำงานบน server thread; ภาพ/เสียงใช้คนละ adapter ฝั่ง client ตาม EFFECTS_INTEGRATION_GUIDE_TH.md

## ฟื้นมานา

maxMana(player) อ่าน Max Mana ของ **ผู้รับแต่ละคน** รวมสแตทที่ม็อดส่วนตัวกำหนดแล้ว
restore(player, amount, maximum) เพิ่มมานาปัจจุบันและ cap ไม่เกิน maximum; คืน true เมื่อรองรับการดำเนินการ
ระบบธิดาคำนวณ 5% เองและดูแลรอบ 20 วินาที/ไม่เร่งตามหลายสายสัมพันธ์ ผู้ทำ adapter ไม่ต้องมี timer อีกชุด
provider ต้อง cap ยอดมานาภายใน restore โดยใช้ current mana ของระบบนั้น สัญญาเดิมไม่เปิดช่องให้ Thida อ่าน/เขียนบัญชีมานาโดยตรง
ข้อมูลไม่พร้อม/NaN/Infinity/ติดลบจะไม่เติมมานา และไม่สร้างบัญชีมานาหรือเปลี่ยนไปใช้ระบบอื่น
ข้อผิดพลาด provider ถูกจับและบันทึกครั้งเดียว ไม่ทำให้ระบบพันธะหรือสกิลอื่นล่ม
ไม่มี adapter ของ Iron’s Spellbooks แล้ว และไม่มี dependency มานาส่วนตัวหรือ AAA บังคับ

## ค่าและหน่วย Skill 1

Quote.percent ใช้ fraction: 0.20 หมายถึง 20%; healHp/bleedHp หน่วย HP (2 HP = หนึ่งหัวใจ)
ตัวตั้งต้นที่ยืนยันคือ 20% และเลือดไหล 2 HP/วินาที แต่ Quote ต้องคืน **ผลสุดท้ายหลังสเกล**; Thida ไม่คูณพลังเวทย์ซ้ำ
เมื่อไม่มี adapter: percent=0.20, bleedHp=2, ฮีลนรตรา 4 เท่ากับ 20% ของ Max HP เป้าหมาย และ payment ไม่มีค่าใช้จ่าย ลดดาเมจหลังกาฝากแตก 0.20 เป็นค่าที่เจ้าของงานยืนยันสำหรับเล่นโดยไม่ใช้ระบบสเกล
เมื่อมี adapter จะใช้ผลลัพธ์และ payment ของ provider เท่านั้น หาก provider ไม่พร้อมหรือข้อมูลผิดจะปฏิเสธการร่าย ไม่เปลี่ยนไปร่ายฟรี สูตรแปลงพลังเวทย์และราคาในระบบภายนอกให้ผู้พัฒนาม็อดนั้นกำหนด
ThidaSpell ให้ ID ที่อ่านง่าย และ map ไป storage index 0/1/2 กับเลขเอกสาร 2/3/4 โดยไม่เปลี่ยน NBT เก่า
method quote(ServerPlayer, ThidaSpell) bridge เข้าสัญญา String/int เดิมเพื่อไม่บังคับให้ provider เก่าพัง; ผู้เขียนใหม่ใช้ ThidaSpell.find หรือ enum โดยตรง

## อ่าน quote และจ่ายจริง

1. เปิดเมนู/tooltip/ตรวจ readiness อ่าน quote ได้หลายครั้ง **ต้องไม่มีผลข้างเคียง**
2. เมื่อร่าย server ตรวจ Origin หุ่นชิ้นที่ถือ เป้าหมาย สถานะพันธะ และ CD ก่อน
3. resolve อ่าน Quote ของการร่ายครั้งนี้หนึ่งครั้ง แล้วตรวจ NaN/Infinity/ค่าติดลบ/Payment ที่หาย
4. consume เรียกบน server เพียงครั้งเดียว เป็นการตรวจและจ่ายแบบ atomic ของระบบมานา คืน false เมื่อจ่ายไม่ได้
5. จ่ายสำเร็จแล้วจึงหยุดคาถาเก่า สร้างผลใหม่ และเริ่ม CD; จ่ายไม่ผ่านห้ามเปลี่ยนสิ่งเหล่านั้น

Payment ของ Resolution ถูกห่อ one-shot และมีตัวกัน reentrant cast ต่อผู้เล่น คำขอร่ายซ้ำที่ติด CD ไม่หักซ้ำ
อย่าเรียก quote เองแล้ว consume ใน UI และห้ามใช้ยอดมานา ราคา หรือพลังเวทย์จาก packet client
Resolution ประกาศ READY, UNAVAILABLE, NOT_READY, INVALID แต่โหมดไม่มี provider ในรุ่นนี้คืน READY ด้วยค่าพื้นฐาน ไม่คืน UNAVAILABLE
กรณี consume คืน false แจ้งมานาไม่พอ ค่าร่ายและราคาไม่ต้องเผยในข้อความผู้เล่น

parasiteBreakReduction(caster, target) คืน fraction ผลสุดท้ายสำหรับบัฟเป้าหมาย 10 วินาที; หากไม่มี adapter ใช้ 0.20 หากติดตั้ง adapter แล้วคืน Optional.empty จะไม่เพิ่มผลลดดาเมจส่วนนี้
install(null) คืนสู่โหมดค่าพื้นฐาน ไม่ปิด Skill 1; การฟื้นมานา Passive ยังคงต้องมี ThidaManaIntegration.Adapter แยกต่างหาก
ตัวอย่างใน docs/examples/PersonalProviderExample.java ใช้ facade ที่ประกาศในไฟล์เอง ไม่ใช่การเดาชื่อ API ของม็อดส่วนตัว
ตัวอย่างไม่ลงทะเบียนใน JAR และไม่กำหนดสูตรสมดุล

## ตารางคาถาที่ต้องส่งผลหลังสเกล

| enum / ID | doll / storage index | ค่าที่ใช้ |
| --- | --- | --- |
| `NAROTRA_SHIELD` / `narotra_shield` | `narotra_doll` / 0 | percent ลดดาเมจ 10 วินาที |
| `NAROTRA_CLEANSE` / `narotra_cleanse` | `narotra_doll` / 1 | ล้าง/กันดีบัฟ 5 วินาที ค่า percent ไม่ได้เพิ่มระยะเวลา |
| `NAROTRA_RENEWAL` / `narotra_renewal` | `narotra_doll` / 2 | percent ความเร็ว 10 วินาที; healHp ฮีลครั้งเดียว |
| `NILAKAN_WEAKNESS` / `nilakan_weakness` | `nilakan_doll` / 0 | percent ลดดาเมจ/ความเร็วโจมตี 10 วินาที |
| `NILAKAN_BLEED` / `nilakan_bleed` | `nilakan_doll` / 1 | bleedHp ต่อวินาที ไม่ตาย; ตาบอด/เลือดไหล 5 วินาที |
| `NILAKAN_VULNERABILITY` / `nilakan_vulnerability` | `nilakan_doll` / 2 | percent เพิ่มดาเมจขาเข้า 10 วินาที |

เลขคาถาในเอกสาร 2/3/4 ตรงกับ storage index 0/1/2 ห้ามเปลี่ยนเลขในเซฟ
ค่าทุกช่องของ Quote ต้อง finite และ >= 0 แม้คาถานั้นไม่ใช้ช่องนั้น ให้คืน 0 แทน null
กรณีฮีลจาก Max HP เป้าหมาย ใช้ `ThidaBonds.passiveTarget(caster, spell.doll())` อ่านเป้าหมายจาก server
หากข้อมูลผู้เล่นหรือเป้าหมายยังไม่พร้อมให้คืน `Optional.empty()` ไม่ใช้ค่าที่ client ส่งมาแทน
Payment ต้องไม่เป็น null สูตรสมดุลและเพดานเปอร์เซ็นต์ให้เจ้าของระบบสเตตัสกำหนด
อย่าคืน 20 แทน .20 หรือคูณสเกลซ้ำทั้งใน Quote และ event ดาเมจของม็อดคุณ

## อัลติเสน่ห์มายารมณ์: สูตรแยกจาก Skill 1

```java
ThidaMayaromScaling.install(player -> myStats.magicPower(player.getUUID()));
```

บรรทัดนี้แสดงจุดเรียก; `myStats` เป็นระบบของผู้รับงาน ไม่ใช่ API ที่มีอยู่ใน Thida
ตัวอย่าง `PersonalProviderExample.registerMayarom` ประกาศ facade จริงให้แทนที่ได้และตรวจคอมไพล์แล้ว

สูตรที่กำหนดคือ **ดาเมจประชิดเดิม + (พลังเวทย์ × 2)**
provider คืนพลังเวทย์ดิบเท่านั้น ไม่คืนดาเมจสุดท้ายและไม่คูณสองมาก่อน
Thida เรียกสูตรเมื่ออัลติทำงานใน LivingIncomingDamageEvent ก่อนคำนวณเกราะ Minecraft
ไม่สร้างดาเมจเพิ่มอีก hit และไม่คำนวณซ้ำกับดาเมจแบ่งปัน `thida:shared`
ไม่มี provider, ค่า NaN/Infinity/ติดลบ, ผลลัพธ์เกินขอบเขต หรือ exception จะใช้ดาเมจประชิดเดิม
อัลติยังทำงานและไม่มีการหักมานาผ่าน callback นี้

ค่าลดดาเมจ 70%, โบนัสนรตรา 20% และดีบัฟจากอัลติ 20%/เลือดไหล 2 HP ยังเป็นค่าคงที่
`ThidaSkillIntegration.Adapter` ไม่สเกลค่าเหล่านั้นอัตโนมัติ การเพิ่มสเกลส่วนนี้เป็นงานแก้ API เพิ่มเติมที่ต้องตกลงแยก
ยังไม่รวมอัลติเรือนเสน่หาใน branch นี้

## ลำดับการติดตั้งและถอด

- `ThidaSkillIntegration.install(adapter)` สำหรับ Quote และผลหลังกาฝากแตก
- `ThidaManaIntegration.install(adapter)` สำหรับการฟื้นมานา Passive ซึ่งเป็นคนละระบบกับการจ่ายค่าร่าย
- `ThidaMayaromScaling.install(provider)` สำหรับพลังเวทย์ดิบของอัลติ
- ถอดแต่ละตัวด้วย `install(null)` ทั้งสามตัวถูก reset เมื่อ ServerStopped

อย่าลงทะเบียน callback เหล่านี้เฉพาะ client setup: dedicated server ต้องเรียกได้โดยไม่โหลดคลาส Minecraft client
ใช้ UUID อ่านข้อมูลปัจจุบัน ไม่เก็บ ServerPlayer ของโลกเดิมไว้ใน provider

## เกณฑ์ตรวจรับงานเชื่อมต่อ

- ไม่ลงม็อดสเตตัส: เกมเปิดได้ คาถาใช้ค่าพื้นฐาน ไม่มีมานาสมมติ
- ลง provider แล้ว: UI อ่าน quote หลายครั้งไม่เสียมานา; ร่ายสำเร็จเสียครั้งเดียว
- มานาไม่พอ/Quote ไม่พร้อม/ค่าผิด: ไม่ร่าย ไม่เปลี่ยนคาถาเก่า และไม่เริ่ม CD ใหม่
- Max Mana เปลี่ยนตามสเตตัส: ฟื้น 5% ของ Max ปัจจุบันของผู้รับแต่ละคน ไม่เกินเพดาน
- อัลติ: ดาเมจเดิม 4 และพลังเวทย์ 3 ได้ 10 ก่อนเกราะ ไม่ใช่ 8 หรือ 14
- ยกเลิกอัลติ: ไม่เรียกโบนัสสูตรอีก; เปลี่ยนโลก/เซิร์ฟเวอร์: ไม่มี provider เก่าค้าง
- ทดสอบ client และ dedicated server พร้อมม็อดสเตตัสจริง และรัน `build` / `runGameTestServer`
- ส่งมอบชื่อ/รุ่นม็อด สูตรที่ใช้ เพดาน ค่าร่าย จุดลงทะเบียน และผลทดสอบให้ผู้ดูแลต่อ

## การฮีลหทัย

ใช้ ThidaHealing.apply(caster, target, hp, Kind.SKILL) หรือครอบ call เดิมด้วย withSource(caster, kind, action)
Kind.NON_SKILL สำหรับยา/ธรรมชาติที่ระบุแหล่งถูกต้อง; OTHER/UNKNOWN เป็น fallback ที่ตกลงไว้
self skill ที่รู้แหล่งถูกบล็อก ยา/ธรรมชาติ/Regeneration ยังทำงาน ผู้อื่นหรือแหล่งไม่ทราบให้ฮีลก่อนแล้วตัดพันธะ
มี mixin สำหรับ vanilla heal, ดื่มยา, instant health, food regeneration และ Regeneration ของเกม
ม็อดที่เขียน setHealth/เปลี่ยน HP โดยตรงหรือข้าม heal ที่รองรับ อาจไม่ผ่านระบบระบุผู้รักษา ต้องทำ compatibility กับม็อดนั้น
ไม่อ้างว่ารู้ผู้รักษาจากทุกม็อดอัตโนมัติ แหล่งไม่ทราบอาจฮีลตนเองสำเร็จหนึ่งครั้งก่อนพันธะหลุดตามที่เจ้าของงานเลือก
