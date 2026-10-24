# ผลตรวจฟอนต์ฝัง JAR 0.8.5

- เลือก Itim Regular จาก Google Fonts ใบอนุญาต SIL OFL 1.1 อนุญาตให้รวมและแจกพร้อมซอฟต์แวร์ตามเงื่อนไข มีเครดิตและใบอนุญาตฉบับเต็มใน JAR แล้ว ดูรายละเอียดและแหล่งทางการใน `FONT_0_8_5_TH.md`
- build และ unit tests 37 รายการผ่าน รวม 2 tests ใหม่สำหรับฟอนต์ ใบอนุญาต provenance และ checksum
- ตรวจ client จริงโดยเปิดเฉพาะ vanilla/mod resources ไม่มีแพ็กฟอนต์เสริม ผ่านชุด HUD GUI Scale 2/3, HUD scale 60/100/140%, เปลี่ยนขนาดหน้าต่าง และ editor เดิม
- ตรวจภาพ HUD เมนูคาถา และ tooltip จริงของไอเทมนรตรา ชื่อไอเทมมี font style Thida ตาม assertion ของ visual test
- ค่า font metrics ของข้อความเดียวกัน: default=49, Thida=71 ยืนยันการใช้ฟอนต์แยกจาก default
- แก้ปัญหา resource path ตัวพิมพ์ใหญ่จากรอบแรกและลด provider size เป็น 11 เพื่อให้เข้ากับระยะบรรทัด tooltip รอบสุดท้ายไม่พบ font load error
- ตรวจใน JAR แล้วมี TTF เพียงหนึ่งไฟล์ที่ hash ตรงกับต้นฉบับ `9164d7eb92ca717b53b07193e3445c96782222d24215151b8d2851b576b17645` มี `OFL.txt` และ `SOURCE.txt` ครบ ไม่มี visual test หรือ GameTests ใน JAR
- ไม่รวมฟอนต์จาก ThaiFontFix เดิม ไม่เปลี่ยนกติกาสกิลหรือข้อมูลเซฟ และไม่ได้ติดตั้งลงโปรไฟล์ DRC จริงอัตโนมัติ
- ยังไม่ได้ทดสอบ resource packs ทั้งชุดของผู้ใช้หรือการเล่นหลายเครื่องในรอบนี้ ไม่ได้รัน GameTests ฝั่งเซิร์ฟเวอร์ซ้ำสำหรับการเปลี่ยน asset ฟอนต์ครั้งนี้

ใช้ JAR 0.8.5 แทนรุ่นเดิมได้เลย ไม่ต้องติดตั้งแพ็กฟอนต์เพิ่ม หากเคยเปิด `ThidaThaiFont-local.zip` ให้ปิดแพ็กนั้นเพื่อให้เห็นฟอนต์ใหม่ใน JAR ส่วน ThaiFontFix ที่เปลี่ยนฟอนต์ทั้งเกมจะเปิดหรือปิดตามความต้องการของผู้ใช้ได้ โดย font Thida มี namespace แยก

ภาพอยู่ใน `screenshots/`: `after-many.png`, `after-editor.png`, `font-tooltip.png`, `font-menu.png`
