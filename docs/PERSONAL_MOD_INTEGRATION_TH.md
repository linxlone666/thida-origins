# เชื่อมม็อดมานา พลังเวทย์ และการรักษา

## API ที่ใช้จริง

| API | หน้าที่ | ไม่มี provider |
|---|---|---|
| ThidaManaIntegration.Adapter | ฟื้นมานา Passive จาก Max Mana ของผู้รับ | ข้ามเฉพาะการฟื้นมานา |
| ThidaSkillIntegration.Adapter | ผลหลังสเกลและราคาคาถา Skill 1 / ลดดาเมจหลังกาฝากแตก | ใช้ค่าพื้นฐานที่ยืนยันในรุ่น 0.8.1 และร่ายฟรี |
| ThidaHealing | ระบุผู้ฮีล/ผู้รับ/ชนิดฮีลของ Skill 3 | แหล่งไม่ทราบให้ฮีลก่อนแล้วจบพันธะ |

ลงทะเบียนบน server lifecycle ด้วย install(adapter), ถอดด้วย install(null); ServerStopped reset provider ให้ไม่ค้างข้ามเซิร์ฟเวอร์
อย่าลงทะเบียน provider ที่เก็บ ServerPlayer หรือ MinecraftServer เก่าไว้ใน static field ใช้ UUID และอ่านข้อมูลปัจจุบันเมื่อถูกเรียก
API ทั้งหมดนี้ทำงานบน server thread; ภาพ/เสียงใช้คนละ adapter ฝั่ง client ตาม SKILL_EFFECT_ADAPTER_TH.md

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

## การฮีลหทัย

ใช้ ThidaHealing.apply(caster, target, hp, Kind.SKILL) หรือครอบ call เดิมด้วย withSource(caster, kind, action)
Kind.NON_SKILL สำหรับยา/ธรรมชาติที่ระบุแหล่งถูกต้อง; OTHER/UNKNOWN เป็น fallback ที่ตกลงไว้
self skill ที่รู้แหล่งถูกบล็อก ยา/ธรรมชาติ/Regeneration ยังทำงาน ผู้อื่นหรือแหล่งไม่ทราบให้ฮีลก่อนแล้วตัดพันธะ
มี mixin สำหรับ vanilla heal, ดื่มยา, instant health, food regeneration และ Regeneration ของเกม
ม็อดที่เขียน setHealth/เปลี่ยน HP โดยตรงหรือข้าม heal ที่รองรับ อาจไม่ผ่านระบบระบุผู้รักษา ต้องทำ compatibility กับม็อดนั้น
ไม่อ้างว่ารู้ผู้รักษาจากทุกม็อดอัตโนมัติ แหล่งไม่ทราบอาจฮีลตนเองสำเร็จหนึ่งครั้งก่อนพันธะหลุดตามที่เจ้าของงานเลือก
