# ฟอนต์ของ Thida 0.8.4

## การใช้งาน

ใช้ JAR 0.8.4 คู่กับ `ThidaThaiFont-local.zip` ที่ทำจากไฟล์ ThaiFontFix-1.0.8.zip ของผู้ใช้ วาง ZIP นี้ใน `resourcepacks` ของโปรไฟล์ แล้วเปิดจาก Options → Resource Packs ให้แพ็กเสริมอยู่เหนือแพ็กอื่นที่แก้ `thida:ui`

แพ็กเสริมแก้เฉพาะ `assets/thida/font/ui.json` และมีไฟล์ฟอนต์ใน namespace `thida` ไม่มี `assets/minecraft` จึงไม่เปลี่ยนฟอนต์แชต vanilla เมนูเกม หรือม็อดอื่นด้วยตัวมันเอง หากต้องการเปลี่ยนเฉพาะ Thida ให้ใช้แพ็กเสริมนี้โดยไม่เปิด ThaiFontFix ฉบับที่เปลี่ยน `minecraft:default` ทั้งเกม

ขอบเขตที่ใช้ฟอนต์: HUD สายสัมพันธ์/ผู้รับ/เวลา เมนูเลือกหุ่นและคาถา หน้าจัด HUD พร้อมปุ่ม และชื่อไอเทม/tooltip ของไอเทม namespace Thida ข้อความระบบแชตและหน้าจอของ vanilla ยังใช้ฟอนต์เกมตาม resource pack ที่ผู้เล่นเลือก

## โครงสร้าง

- ม็อดมี font ID `thida:ui` โดยค่าเริ่มต้นอ้าง `minecraft:default` เป็น fallback จึงเปิดเกมได้โดยไม่มีแพ็กเสริมหรือไฟล์ TTF
- แพ็กเสริมใช้ `2005_iannnnnAMD` จากไฟล์ที่ผู้ใช้ให้มา พร้อมค่าเดียวกับ overlay 1.20.5+ ของแพ็กต้นฉบับ: size 16, shift [0,0], oversample 16 และ fallback ไป font ปกติสำหรับ glyph ที่ขาด
- `ThidaGui.styled` ใส่ style font เฉพาะ Component ของม็อด; `ThidaGui.width` และการตัดข้อความใช้ style เดียวกันเพื่อให้ layout ตรงกับภาพ
- Tooltip ถูกใส่ font ที่ client หลังสร้างรายการแล้ว โดยกรองเฉพาะไอเทม namespace `thida` ไม่แก้รายการไอเทมของม็อดอื่น
- การ reload resource ใช้ระบบ font ของ Minecraft ตามปกติ ไม่มีการเก็บ Font object หรือ glyph handle แยกที่ต้องล้างเอง
- ไม่เพิ่ม dependency และไม่เปลี่ยนกติกาสกิลหรือข้อมูลเซฟ

## ไฟล์ฟอนต์และที่มา

metadata ภายใน TTF ระบุ:

```
2005_iannnnnAMD
Copyright (c) iannnnn, 2005. All rights reserved.
For educations used only.
```

จึงแยกไฟล์ฟอนต์เป็นการปรับแต่งในเครื่องจากไฟล์ที่ผู้ใช้ให้มา ไม่รวม TTF ลงใน JAR หรือ source ZIP สำหรับแจกต่อ และเก็บข้อความที่มาไว้ใน `FONT-NOTICE.txt` ของแพ็กเสริม คู่มือไม่ได้ให้สิทธิ์เพิ่มเติมเหนือเงื่อนไขฟอนต์ต้นฉบับ

## การตรวจ

ใช้ `formatJava build runHudPreview -PwithoutIron=true` ในสำเนาโลกทดสอบ ตรวจทั้งไม่มีแพ็กเสริมและเปิดแพ็กเสริมเฉพาะ Thida วัด `THIDA_FONT_METRICS` เทียบ default กับ Thida เพื่อยืนยันว่าฟอนต์ใหม่ไม่ได้แทน default ของเกม ผลและภาพอยู่ในชุดส่งมอบ

ไม่ได้เปิดแพ็กหรือแก้ options ของโปรไฟล์ DRC จริงโดยอัตโนมัติ ต้องเลือกเปิดแพ็กเสริมในเกมเอง ยังไม่ได้ทดสอบร่วมกับ resource pack ทั้งหมดของโปรไฟล์จริง
