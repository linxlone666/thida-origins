# Thida Origins

ม็อด Origin ธิดา สำหรับ **Minecraft 1.21.1 / NeoForge 21.1.233 / Java 21**
เลขเวอร์ชันม็อดกำหนดใน [gradle.properties](gradle.properties) ที่ค่า `mod_version`

รวม Passive, Skill 1–3 และอัลติ **เสน่ห์มายารมณ์** ส่วนเรือนเสน่หายังไม่รวมใน branch นี้
เล่นได้ด้วยค่าพื้นฐานโดยไม่ติดตั้งม็อดสเกลพลังเวทย์หรือ AAA ไม่มีการเชื่อม Iron's Spellbooks

## ส่งต่อให้ผู้พัฒนา

1. **[คู่มือม็อดพลังเวทย์และมานา](docs/MAGIC_INTEGRATION_GUIDE_TH.md)** — API, หน่วย, ค่าพื้นฐาน, การจ่ายมานา, สูตรอัลติ และตัวอย่างที่ตรวจคอมไพล์
2. **[คู่มือเอฟเฟกต์และ AAA backend](docs/EFFECTS_INTEGRATION_GUIDE_TH.md)** — event, mapping, จุดยึด, lifecycle, อัลติ และตัวอย่าง backend

อ่าน [โครงสร้างโค้ด](docs/ARCHITECTURE_TH.md), [กติกา Skill 1–3](docs/SKILLS_V7_TH.md)
และ [กติกาอัลติ](docs/ULTIMATE_MAYAROM_TH.md) ประกอบ ตัวอย่างใน `docs/examples/` ไม่รวมใน JAR

## Build และตรวจสอบ

ติดตั้ง JDK 21 และตั้ง `JAVA_HOME` จากนั้นรันที่โฟลเดอร์โปรเจกต์:

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
```

Linux/macOS ใช้ `./gradlew` แทน ผลลัพธ์อยู่ใน `build-neoforge/libs/`
`build` ตรวจ unit tests และคอมไพล์ตัวอย่างส่งต่อด้วย ส่วน GameTests ต้องรันแยก
การ build ครั้งแรกต้องดาวน์โหลด dependencies ตาม `build.gradle`

แพ็ก source และ JAR ที่ build แล้วไปยังโฟลเดอร์ใหม่:

```powershell
.\tools\Package-Handoff.ps1 -OutputDirectory C:\Thida-Handoff
```

ชุดส่งต่อไม่รวม build cache, JDK, โลกทดสอบ, log, `.git` หรือคลัง JAR รุ่นเก่า
ใช้เวอร์ชันม็อดเดียวกันทั้ง client/server ก่อนนำไปทดสอบกับ modpack จริง

## การใช้งาน

| ปุ่ม | หน้าที่ |
| --- | --- |
| `1` | เมนูคาถา: ลูกกลิ้งเลือก คลิกซ้ายยืนยัน คลิกขวา/Esc ย้อนกลับ |
| `2` | เรียกหุ่นกาฝาก |
| `3` | พันธะหทัย |
| `4` | เปิด/ยกเลิกเสน่ห์มายารมณ์ |
| `H` | แก้ตำแหน่งและขนาด HUD แยกจากเมนูคาถา |

เปลี่ยนปุ่มได้ใน Controls สกิล 2–4 ต้องให้ OP ปลดด้วย `/thida unlock <player> skill2`, `skill3`, `skill4` ตามลำดับ

## โครงสร้างและประวัติ

| ที่อยู่ | เนื้อหา |
| --- | --- |
| `src/main/` | โค้ดและ resource ที่ใช้ในเกม |
| `src/test/`, `src/gametest/` | unit tests และ GameTests |
| `docs/` | เอกสารปัจจุบันและคู่มือส่งต่อสองส่วน |
| `docs/examples/` | ตัวอย่าง integration ที่ตรวจคอมไพล์ได้ |
| `docs/history/`, `docs/migration-legacy/` | บันทึกรุ่นเก่าและข้อมูลการย้ายระบบ เพื่ออ้างอิง |
| `tools/` | สคริปต์ build และแพ็กส่งต่อ |
| `archive/` | JAR และหลักฐานรุ่นเดิม ไม่ใช่ dependencies ของ build ปัจจุบัน |

ไฟล์ใน `archive/` มีเฉพาะเมื่อ clone repository เต็มชุด ไม่รวมในแพ็ก source สำหรับส่งต่อ
คง tag, commit และ checksum ของรุ่นเก่าไว้ โดยไม่ใช้บันทึกรุ่นเก่าเป็นข้อกำหนดแทนโค้ดปัจจุบัน

## License

โค้ดใช้ [MIT](LICENSE) ฟอนต์ Itim ใช้ SIL OFL 1.1 พร้อมประกาศสิทธิ์ใน
[META-INF/licenses/Itim](src/main/resources/META-INF/licenses/Itim/)
ไม่มี font pack ที่จำกัดสิทธิ์ของ ThaiFontFix รวมอยู่ใน repository
