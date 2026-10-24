# Thida 0.8.5 — ฝังฟอนต์ไทยใน JAR

ใช้ **Itim Regular (ไอติม)** จาก Google Fonts รองรับไทย/ละติน เป็นฟอนต์ลักษณะลายมือ ไม่ต้องติดตั้ง resource pack เพิ่ม ใช้เฉพาะ HUD เมนู ปุ่ม editor และ tooltip ของไอเทม Thida ตามขอบเขตระบบฟอนต์ของ 0.8.4 ไม่เปลี่ยนฟอนต์ vanilla หรือกติกาสกิล

หากเคยเปิด `ThidaThaiFont-local.zip` ให้ปิดแพ็กนั้นเพื่อให้ฟอนต์ Itim ใน JAR ทำงาน เพราะแพ็กภายนอกยังมีสิทธิ์ override `thida:ui` ตามระบบ resource pack ปกติ ไม่ใช้หรือบรรจุฟอนต์ `2005_iannnnnAMD` จาก ThaiFontFix เดิมใน JAR นี้

## ใบอนุญาตและที่มา

Itim ใช้ **SIL Open Font License 1.1** อนุญาตให้ bundle/embed และแจกพร้อมซอฟต์แวร์ได้ รวมถึงซอฟต์แวร์เชิงพาณิชย์ โดยเก็บประกาศลิขสิทธิ์และใบอนุญาตไปด้วย ไม่ขายฟอนต์เดี่ยว และไม่เปลี่ยนใบอนุญาตของฟอนต์

- แหล่งทางการ: https://github.com/google/fonts/tree/0b58fb370093f9a9f4ff785d94405710b79de67c/ofl/itim
- ใบอนุญาต: https://github.com/google/fonts/blob/0b58fb370093f9a9f4ff785d94405710b79de67c/ofl/itim/OFL.txt
- คำอธิบายการแจกพร้อมซอฟต์แวร์ของ OFL: https://openfontlicense.org/ofl-faq/
- ผู้สร้างตามประกาศต้นฉบับ: Cadson Demak และ Pablo Impallari
- SHA-256 ของไฟล์ฟอนต์: `9164d7eb92ca717b53b07193e3445c96782222d24215151b8d2851b576b17645`

ใช้ข้อมูล TTF ต้นฉบับโดยไม่แก้ไข เปลี่ยนเฉพาะชื่อไฟล์ resource เป็นตัวพิมพ์เล็กตามข้อกำหนด Minecraft ไม่แก้ชื่อภายในฟอนต์และไม่อ้างว่าเป็นผู้สร้างฟอนต์

## สิ่งที่บรรจุใน JAR และซอร์ส

- `assets/thida/font/itim-regular.ttf`: ไฟล์ฟอนต์ต้นฉบับ
- `assets/thida/font/ui.json`: TTF provider ขนาด 11, shift [0,0], oversample 4 และ fallback ไป `minecraft:default` สำหรับอักขระที่ขาด
- `META-INF/licenses/Itim/OFL.txt`: ใบอนุญาตและประกาศลิขสิทธิ์ฉบับเต็มจากต้นฉบับ
- `META-INF/licenses/Itim/SOURCE.txt`: commit ที่มา ชื่อไฟล์ต้นฉบับ และ checksum

ใบอนุญาตของฟอนต์ยังเป็น OFL 1.1 แยกจากใบอนุญาตของม็อด ต้องเก็บไฟล์ประกาศและใบอนุญาตเหล่านี้ไว้เมื่อแจก JAR/ซอร์สต่อ

## การตรวจ

- เพิ่ม unit tests ตรวจว่าฟอนต์/ใบอนุญาต/ที่มาครบ ค่า hash ตรง และ font ID ถูกจำกัดใน namespace Thida
- รัน `formatJava build runHudPreview -PwithoutIron=true` โดยเปิดเพียง vanilla กับ mod resources ไม่มีแพ็กฟอนต์เสริม
- ตรวจภาพ HUD หลายขนาด รวม tooltip จริงของไอเทมและเมนูคาถา พร้อมทดสอบ geometry เดิม
- รอบแรกพบ path ตัวพิมพ์ใหญ่ทำให้ font ไม่โหลด จึงเปลี่ยนเป็นตัวพิมพ์เล็กและตรวจซ้ำ ผลสุดท้ายอยู่ในเอกสารส่งมอบ

ไม่ได้ติดตั้ง JAR ลงโปรไฟล์ DRC จริงหรือเปิด/ปิดแพ็กของผู้ใช้อัตโนมัติ
