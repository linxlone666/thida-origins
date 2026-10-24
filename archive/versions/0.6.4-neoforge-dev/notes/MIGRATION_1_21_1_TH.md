# การย้าย Thida Origins ไป Minecraft 1.21.1 NeoForge

อัปเดตวันที่ 17 ตุลาคม 2569 รุ่นแอดออน `0.6.1-neoforge-dev`

## เวอร์ชันเป้าหมายและโปรไฟล์ที่ตรวจ

ตรวจรายการไฟล์และ metadata ของ JAR ในโปรไฟล์ `C:\Users\linxs\AppData\Roaming\ModrinthApp\profiles\DRC 1.0.0` และเวอร์ชัน loader จาก log ของโปรไฟล์ แล้วใช้เวอร์ชันต่อไปนี้ในการพอร์ต:

| ส่วนประกอบ | เวอร์ชัน |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.233 ตรงกับโปรไฟล์ DRC |
| Java สำหรับพัฒนาและรัน | 21 |
| Gradle | 9.2.1 |
| ModDevGradle | 2.0.148 |
| Origins ของ IAFEnvoy | 0.4-beta.2 ตรงกับ JAR ในโปรไฟล์ |
| Jupiter | 2.3.7 ตรงกับโปรไฟล์ |
| Iron’s Spells ’n Spellbooks | 1.21.1-3.16.3 ใช้ในสภาพแวดล้อมพัฒนา |
| Iron’s Lib | 1.21.1-2.1.0 |
| GeckoLib | 4.7.5.1 สำหรับ NeoForge 1.21.1 |
| Curios | 9.5.1+1.21.1 ตรงกับโปรไฟล์ |
| Player Animator | 2.0.4+1.21.1 ตรงกับโปรไฟล์ |

ในโปรไฟล์ที่ตรวจมี Origins, Jupiter, Curios, Player Animator และ AAA Particles อยู่แล้ว แต่ยังไม่พบ Iron’s Spells ’n Spellbooks, Iron’s Lib และ GeckoLib ระบบฟื้นมานาต้องมี Iron’s และ dependency ของมัน ส่วนการเชื่อมและล้างดีบัฟสามารถทำงานเมื่อไม่มี Iron’s ได้

แอดออนธิดายังไม่มี dependency หรือเอฟเฟกต์ AAA Particles แม้ในโปรไฟล์จะติดตั้ง AAA Particles อยู่แล้ว

## สิ่งที่ย้าย

- เปลี่ยน ForgeGradle เป็น ModDevGradle และเปลี่ยน manifest เป็น `META-INF/neoforge.mods.toml` ใช้ Java 21 และชื่อ JAR สำหรับ 1.21.1 NeoForge
- ย้าย package, event bus, การลงทะเบียนไอเทม และระบบตั้งค่าจาก Forge เป็น NeoForge
- เปลี่ยนเหตุการณ์ tick และเหตุการณ์รับดาเมจให้ใช้ API ของ NeoForge รวมถึง `LivingIncomingDamageEvent` และ `LivingDamageEvent.Pre`
- เปลี่ยน attribute modifier จาก UUID เป็น ResourceLocation และใช้ Holder ของแอตทริบิวต์/สถานะตาม API 1.21.1
- เก็บ UUID ประจำหุ่นผ่าน `DataComponents.CUSTOM_DATA` โดยคัดลอกข้อมูลก่อนแก้และเขียนกลับเข้า ItemStack
- เปลี่ยน packet เป็น `CustomPacketPayload` พร้อม StreamCodec และการลงทะเบียน payload ของ NeoForge โปรโตคอลแอดออนเป็น `8-neoforge`
- ปรับ client tick, การวาด vertex, การตรวจหน้าจอ debug, tooltip และข้อมูลสีย้อมให้ใช้ API ใหม่
- ย้ายจุดดักการรักษาไปยังคลาส Instant Health และ Regeneration ที่ Minecraft 1.21.1 แยกออกมา เพื่อคงพฤติกรรมเดิมของพันธะหทัย
- เปลี่ยนการตรวจสิทธิ์เป็นการอ่าน Origin ที่เลือกจริงผ่าน `OriginDataHolder` ของ Origins 0.4-beta.2 แท็ก scoreboard ที่ค้างอยู่เพียงอย่างเดียวไม่ให้สิทธิ์ธิดา
- เปลี่ยน integration มานาเป็น adapter ที่คอมไพล์กับ API ของ Iron’s โดยตรง และโหลดเมื่อมี Iron’s เท่านั้น

งานนี้พอร์ตกลไกสกิลอื่นเท่าที่ API ใหม่ต้องใช้เพื่อให้โปรเจกต์โหลดและชุดตรวจเดิมทำงานได้ การออกแบบสกิลใหม่ยังอยู่นอกงานนี้ ULT ยังคงไม่ตัดสินใจ

## การย้ายข้อมูล Origin และสูตรคราฟต์

| ข้อมูล | ตำแหน่งใหม่ |
| --- | --- |
| Origin ธิดา | `data/thida/origins/origin/thida.json` |
| Power คำอธิบายและ callback | `data/thida/origins/power/` |
| Layer หลัก | `data/origins/origins/layer/origin.json` |
| แท็กเพิ่มธิดาในรายการ Origin | `data/origins/tags/origins/origin/origin.json` |
| สูตรคราฟต์ | `data/thida/recipe/` |
| โครงสร้าง GameTest | `src/gametest/resources/data/thida/structure/empty.nbt` |

Layer หลักใช้รายการรหัส Origin โดยตรง: Origin เดิมสิบตัวและ `thida:thida` รุ่น 0.6.1 เปลี่ยนจาก `#origins:origin` เพราะพบการโหลด tag เป็นชื่อ Origin ในเส้นทาง Singleplayer ของโปรไฟล์ DRC จนเกิด `Unbound values in registry ... [#origins:origin]` หาก datapack อื่นทับ layer หลัก ต้องรวม `thida:thida` ในรายการของ layer นั้นด้วย แท็กเสริมของธิดายังคงมีไว้ แต่ layer หลักไม่ได้ใช้แท็กเป็นรายการตัวเลือก

ปรับไอคอน Origin เป็นรูปแบบ `{"id":"minecraft:totem_of_undying"}` เปลี่ยนผลลัพธ์สูตรคราฟต์จาก `item` เป็น `id` และใช้ datapack format 48 ไฟล์ข้อมูลเดิมเก็บเป็นข้อมูลอ้างอิงใน `docs/migration-legacy/` และ manifest Forge เดิมอยู่ที่ `docs/forge-1.20.1-mods.toml`

ไอคอน โมเดล และ texture ไอเทมยังใช้ไฟล์เดิม

## การสร้างและตรวจสอบ

ใช้สคริปต์จากโฟลเดอร์โปรเจกต์:

```powershell
# คอมไพล์ รัน unit tests และสร้าง JAR/ZIP
.\tools\Build-NeoForge.ps1

# เพิ่ม GameTest พร้อม Iron’s และ dependency จริง
.\tools\Build-NeoForge.ps1 -GameTests

# ตรวจการทำงานเมื่อไม่มี Iron’s
.\tools\Build-NeoForge.ps1 -GameTests -WithoutIron
```

สคริปต์เลือก JDK 21 ใน `.tooling/java/` หากมี มิฉะนั้นใช้ JDK จาก `JAVA_HOME` และใช้ cache ของ Gradle ใน `.gradle-cache/`

ผลลัพธ์ JAR อยู่ใน `build-neoforge/libs/` และ ZIP datapack อยู่ใน `build-neoforge/distributions/` โฟลเดอร์รันแยกเป็น `run-neoforge/` และ `run-neoforge-gametest/` เพื่อให้การพัฒนารุ่นใหม่ไม่ใช้โลกทดสอบ Forge เดิม

## ผลตรวจสอบ

คอมไพล์และ unit tests 15 รายการผ่าน ตรวจ GameTest ทั้งหมด 38 รายการบน NeoForge 21.1.233 ผ่านทั้งกรณีมี Iron’s และไม่มี Iron’s การตรวจมานาจริงยืนยันการฟื้น 5% ของ Max Mana แยกตามผู้รับ รอบ 20 วินาที การจำกัดอัตราจากหลายแหล่ง และการจำกัดไม่เกิน Max Mana

ชุดตรวจยังครอบคลุมสิทธิ์ Origin จริง การมีธิดาใน layer ที่เลือกได้โดยคง Origin เดิมไว้ การเชื่อม การยกเลิก การพัก/กลับมาทำงาน รอบล้างดีบัฟ และ regression ของกลไกเดิม การเริ่ม client แบบแยกโหลดม็อด ทรัพยากรและระบบเสียงได้ การตรวจ client ขั้นนี้ยังไม่ใช่การเล่นทดสอบหลายผู้เล่น

GameTest ใช้ผู้เล่นจำลอง ยังต้องตรวจด้วยผู้เล่นจริงใน DRC ทั้งชุด โดยเฉพาะเข้าเกมใหม่ เกิดใหม่ ข้ามมิติ การแสดงมานา และปฏิสัมพันธ์กับม็อดอื่น

รุ่น 0.6.1 ตรวจเปิด Singleplayer ด้วยรายการม็อดจาก DRC ทั้งชุดในโฟลเดอร์แยกแล้ว ผ่านถึงหน้า Create World และโหลด dynamic registries สำเร็จ รายละเอียดอยู่ใน [เอกสารแก้ Singleplayer ล่ม](CRASHFIX_0_6_1_TH.md)

## การนำ JAR ไปทดสอบ

ใช้ `thida-origins-1.21.1-neoforge-0.6.4-neoforge-dev.jar` กับ Minecraft 1.21.1 NeoForge และ Origins ของ IAFEnvoy 0.4-beta.2 ติดตั้งแอดออนเวอร์ชันเดียวกันทั้ง client/server รุ่นนี้ใช้ข้อความภาษาไทยและรวมตัวแก้ Advanced Fake Players เมื่อใช้ Carry On และ Plasmo Voice ดู [คู่มือทดสอบด้วย bot](FAKE_PLAYER_TEST_TH.md), [รายละเอียดภาษาไทย](THAI_ONLY_0_6_3_TH.md) และ [การแก้เข้าโลกล่ม](WORLD_JOIN_FIX_0_6_4_TH.md)

หากต้องการฟื้นมานา ให้ติดตั้ง Iron’s Spells ’n Spellbooks สำหรับ NeoForge 1.21.1 พร้อม Iron’s Lib, GeckoLib, Curios และ Player Animator ที่เข้ากันได้ รายการในตารางเป็นชุดที่ใช้ตรวจระบบธิดา ไฟล์เหล่านี้ไม่ได้ถูกรวมไว้ใน JAR ธิดา

JAR ธิดามีข้อมูล Origin อยู่แล้ว จึงไม่ต้องติดตั้ง ZIP datapack ซ้ำ ZIP ที่สร้างแยกใช้สำหรับจัดการ datapack ภายนอกและยังต้องมีแอดออนธิดาเพื่อให้กลไกเกมทำงาน

การพอร์ตนี้ไม่ได้แปลงโลก 1.20.1 หรือข้อมูล Origin จากพอร์ต Forge เดิมโดยอัตโนมัติ ใช้โลกทดสอบสำเนาหรือโลกใหม่ก่อนนำไปใช้กับข้อมูลที่ต้องเก็บรักษา

## ซอร์สสำรองและเอกสารประกอบ

สำรองซอร์สก่อนพอร์ตไว้ที่ `C:\Users\linxs\Documents\ChatGPT\Thida Powers\thida-forge-1.20.1-before-neoforge-20261016.zip` รวม src, docs, Gradle wrapper และไฟล์ตั้งค่า build

- [คู่มือ Passive ภาษาไทย](PASSIVES_TH.md)
- [สัญญาเอฟเฟกต์สำหรับ adapter](PASSIVE_EFFECTS.md)
- [MDK ทางการ NeoForge 1.21.1](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle)
- [คู่มือย้ายข้อมูล Origins ของ IAFEnvoy](https://docs.iafenvoy.com/docs/mod/origins/guides/porting/)
- [Origins 0.4-beta.2](https://www.curseforge.com/minecraft/mc-mods/origins-neoforge/files/8842849)
- [การตั้งค่า dependency ของ Iron’s](https://iron.wiki/developers/)
