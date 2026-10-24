# ธิดา — คู่มือความสามารถ

สำหรับ Minecraft Forge 1.20.1 และ Origins Forge 1.10.0.9 ใช้ Origin `thida:thida` ในเลเยอร์ `origins:origin` ผลการตรวจ build และการทดสอบอยู่ใน [TESTING](TESTING.md)

## เริ่มเล่นและปุ่มควบคุม

เลือก Origin ธิดา แล้วพิมพ์ `/thida kit` เพื่อรับไอเทมทั้งสิบชนิด ไม่ต้องเป็น OP แต่ต้องมีแท็ก `thida_origin` คำสั่งเติมเฉพาะชนิดที่ไม่มีใน inventory ถ้าเก็บไม่ลงจะวางลงพื้น ใช้ `/thida help` ดูวิธีควบคุม หรือคราฟต์ไอเทมเองตามสูตรด้านล่าง

คลิกขวาเพื่อใช้ไอเทม ถือในมือหลักสำหรับการตีเพื่อผูกเป้าหมาย นรตราและนิลกาลเปลี่ยนโหมดด้วย Shift+คลิกขวา โหมดและคูลดาวน์อยู่ที่ผู้เล่น การสลับไอเทมจึงไม่รีเซ็ตคูลดาวน์ ไอเทมใช้ซ้ำได้และไม่ถูกใช้หมดเมื่อกดสกิล ทุกความสามารถทำงานผ่านแอดออน ไม่มีปุ่ม active power ของ Origins ซ้ำอีกชุด

เป้าหมายหุ่นเป็นผู้เล่นอื่นในมิติเดียวกัน ระยะพันธะหทัย/กาฝากเริ่มต้น 16 บล็อก ส่วนมาร์คนรตรา/นิลกาลคงอยู่ได้ในระยะ 64 บล็อก เป้าหมายตาย ออกจากเกม เปลี่ยนมิติ หรือเป็น spectator ทำให้ลิงก์ใช้ไม่ได้และถูกล้าง ผู้ถือ Origin ที่เป็น spectator ใช้สกิลหรือคงลิงก์ไม่ได้

## ไอเทมและผลเริ่มต้น

ทุก ID ในตารางมี namespace `thida:` คูลดาวน์ใช้ความสามารถเริ่มต้น 20 วินาทีต่อชนิดไอเทม ผลทั่วไปนาน 10 วินาที

| ID / ชื่อ | วิธีใช้และผล |
| --- | --- |
| `ward` อาคมคุ้มครอง | คลิกขวาให้ Absorption I และ Resistance I แก่ตนเอง |
| `veil` เครื่องรางม่านพราง | คลิกขวาให้ Invisibility แก่ตนเอง |
| `grace` เครื่องรางพรเกื้อหนุน | คลิกขวาให้ Hero of the Village I แก่ตนเอง |
| `remedy` ยาชำระล้างและรักษา | คลิกขวาล้าง harmful effects, เลือดไหล, การลดความเร็วโจมตี, สถานะลดดาเมจ และดับไฟ พร้อมรักษาตนเอง 8 HP ใช้ไม่ได้ระหว่างเป็นผู้ให้หรือผู้รับพันธสัญญาหัวใจ |
| `herbal_orb` ลูกสมุนไพรรักษา | คลิกขวาขว้าง เมื่อตกกระทบสร้างเมฆอยู่กับที่รัศมี 4 บล็อก นาน 5 วินาที รักษาผู้ใช้และผู้เล่นทีม scoreboard เดียวกัน 1.6 HP ต่อจังหวะหนึ่งวินาที |
| `narotra_doll` ตุ๊กตาพรนรตรา | ตีผู้เล่นเพื่อทำเครื่องหมาย คลิกขวามอบพรตามโหมด: Speed II, Resistance I หรือชำระล้าง อีกวิธีคือทั้งคู่จ้องกันต่อเนื่อง 10 วินาทีเพื่อมอบพรโหมดที่เลือก |
| `nilakan_doll` ตุ๊กตาคำสาปนิลกาล | ตีผู้เล่นเพื่อทำเครื่องหมาย คลิกขวาสาปตามหนึ่งในสองโหมดที่อธิบายด้านล่าง |
| `parasite_doll` ตุ๊กตากาฝากแบ่งปันความเสียหาย | ตีผู้เล่นเพื่อสร้างลิงก์เมื่อผ่านเกณฑ์ HP และคูลดาวน์ คลิกขวายุติลิงก์ได้เมื่อคูลดาวน์หมด |
| `heart_doll` ตุ๊กตาพันธสัญญาหัวใจ | เล็งผู้เล่นที่มองเห็นแล้วคลิกขวาเพื่อมอบการคุ้มครอง ทั้งสองต้องไม่มีพันธสัญญาอื่น |
| `domain_talisman` ยันต์อาณาเขตธิดา | คลิกขวาเปิดอาณาเขตรัศมี 30 บล็อก นาน 15 วินาที มีศูนย์กลางตามตัวผู้ใช้ |

การตีเพื่อทำเครื่องหมายนรตรา/นิลกาลมีคูลดาวน์แยก 20 วินาที และต้องเป็นการโจมตีที่เกิดความเสียหายจริง ระหว่างลิงก์กาฝากของตนทำงาน จะใช้หรือเปลี่ยนโหมดตุ๊กตาพร/คำสาปไม่ได้

### นิลกาล: สองโหมด

1. เลือดไหลและตาบอด: เลือดไหลเป็น magic damage 1 HP ต่อวินาที พร้อม Blindness
2. อ่อนแอและลดความเร็วโจมตี: Weakness II, ลดความเสียหายที่มีผู้เล่นนั้นเป็นแหล่งกำเนิด 30% และลด Attack Speed 50% ไม่ใช่ลดความเร็วเดิน Weakness ของ vanilla ยังลดดาเมจระยะประชิดเพิ่มเติมตามกฎเกม

### กาฝากและเกณฑ์สุขภาพ

ผู้ใช้ต้องมี HP ปัจจุบันมากกว่า 0 และต่ำกว่า 150 ทั้งคู่ต้องมี HP รวมมากกว่า 50 และหลังหักดาเมจของการตีเริ่มลิงก์แล้วยังต้องรวมมากกว่า 50 ทั้งคู่ต้องไม่มีคู่กาฝากอยู่แล้ว ผู้ให้พันธสัญญาหัวใจเริ่มกาฝากไม่ได้

**150 เป็นเกณฑ์เริ่มกาฝาก ไม่ใช่ Max Health** ผู้เล่น vanilla เต็มเลือดคนละ 20 HP รวม 40 จึงเริ่มกาฝากไม่ได้ด้วยค่าเริ่มต้น เซิร์ฟเวอร์ต้องใช้ระบบเพิ่มสุขภาพ หรือปรับเกณฑ์ใน config ให้เหมาะกับระบบสุขภาพของตน Resources ไม่เพิ่ม Max Health อัตโนมัติ หน่วยทั้งหมดเป็น HP โดย 2 HP เท่ากับ 1 หัวใจ

ลิงก์แบ่งดาเมจ 50% ไปยังอีกฝ่าย ไม่บังคับให้ทั้งสองมี HP เท่ากัน เมื่อ HP รวมเหลือไม่เกิน 50 ลิงก์สิ้นสุด ผู้ใช้ได้รับการรักษา 50 HP หลังประมวลผลดาเมจ และ Speed II 2 วินาที อีกฝ่ายได้ Speed II และ Resistance I 10 วินาที การรักษาไม่เกิน Max Health จริง การยุติเองหรือหลุดระยะไม่ใช่เงื่อนไขรางวัลนี้

ความเสียหายส่งต่อใช้ `thida:shared` ผ่าน `hurt` เพื่อผ่านระบบ totem และ death events พร้อมตัวกันการส่งต่อวนซ้ำ โดยข้ามเกราะ effects/resistance enchantments คูลดาวน์รับดาเมจ และโล่ ตาม tags ของ resources; Absorption ยังอาจดูดซับดาเมจส่งต่อได้

### พันธสัญญาหัวใจ

ผู้รับพันธสัญญาโจมตีไม่ได้และถูกบล็อกการรักษาปกติ เมื่อดาเมจจะทำให้เหลือไม่เกิน 1 HP จะคงอย่างน้อย 1 HP และคุ้มกันดาเมจ 5 วินาที ใช้การช่วยชีวิตได้หนึ่งครั้งต่อพันธสัญญา

ผู้ให้พันธสัญญาใช้ remedy, parasite และ domain ไม่ได้ เมื่อผู้ให้ได้รับดาเมจ พันธสัญญาขาดและผู้ให้เลือดไหล 10 วินาที ลิงก์ยังสิ้นสุดได้จากการหลุดระยะ การตาย ออกจากเกม หรือเปลี่ยนมิติ

การรักษาจากผู้เล่นอื่นผ่าน `ThidaEvents.addonHeal(caster, target, amount)` ยุติพันธสัญญาที่เกี่ยวกับผู้รับการรักษาแล้วจึงรักษา เมฆสมุนไพรใช้ API นี้ด้วย Healing mixins รองรับ Instant Health ของ vanilla ที่มีแหล่งจากผู้เล่นอื่น และ Regeneration ที่บันทึกแหล่งผู้เล่นไว้ โดย Regeneration ปลดพันธสัญญาเมื่อมีจังหวะรักษาจริงและ HP ยังไม่เต็ม ม็อดที่ไม่ส่งข้อมูลผู้รักษาต้องเชื่อมผ่าน `addonHeal`; การเรียก heal ที่ไม่ทราบผู้รักษาไม่ถือเป็นการรักษาจากบุคคลอื่นโดยอัตโนมัติ

### อาณาเขตและข้อจำกัดระยะ

ระหว่างอาณาเขต ผู้ใช้ล้างสถานะเสียต่อเนื่องและลดดาเมจที่ได้รับ 70% การโจมตีผู้เล่นในอาณาเขตให้ Weakness I, สถานะลดดาเมจจากผู้เล่นนั้น 30% และ Blindness

เป้าหมายนรตราที่อยู่ในอาณาเขตได้รับ Strength II และ Resistance II อย่างต่อเนื่อง หากเป้าหมายนี้โจมตีผู้เล่นในอาณาเขต ศัตรูจะถูกทำเครื่องหมายนิลกาลและรับคำสาปทั้งสองโหมด กลไกสนับสนุนนี้หยุดระหว่างกาฝากของเจ้าของทำงาน

มาร์คทั่วไปใช้ `markRange=64` แยกจาก `bondRange=16` ทำให้เป้าหมายนรตราได้รับบัฟได้ทั่วโดเมนรัศมี 30 บล็อก กลไกมาร์คและคำสาปออกแบบสำหรับการต่อสู้ระหว่างผู้เล่น

## ค่าปรับแต่ง

กำหนดใน `src/main/java/dev/thida/ThidaConfig.java` ลงทะเบียนเป็น SERVER config

| Key | ค่าเริ่มต้น | ความหมาย |
| --- | --- | --- |
| `bondRange` | 16 | ระยะพันธะหทัยและกาฝาก หน่วยบล็อก |
| `markRange` | 64 | ระยะคงมาร์คนรตรา/นิลกาล หน่วยบล็อก |
| `domainRadius` | 30 | รัศมีอาณาเขต หน่วยบล็อก |
| `domainDuration` | 15 | ระยะเวลาอาณาเขต วินาที |
| `gazeTicks` | 200 | ระยะจ้องต่อเนื่อง 20 ticks = 1 วินาที |
| `effectSeconds` | 10 | ระยะเวลาผลทั่วไป วินาที |
| `cooldownSeconds` | 20 | คูลดาวน์ความสามารถ/การทำเครื่องหมาย วินาที |
| `parasiteThreshold` | 150 | HP ผู้ใช้ต้องต่ำกว่าค่านี้ |
| `parasiteBreakHealth` | 50 | เกณฑ์ HP รวมสำหรับห้ามเริ่มและตัดลิงก์ |
| `parasiteBreakHeal` | 50 | HP รักษาผู้ใช้เมื่อตัดลิงก์ตามเกณฑ์ |
| `healAmount` | 8 | HP ของ remedy; เมฆใช้หนึ่งในห้าต่อจังหวะ |
| `shareFraction` | 0.5 | สัดส่วนดาเมจส่งต่อ |
| `domainReduction` | 0.7 | สัดส่วนลดดาเมจเจ้าของอาณาเขต |

รัศมีเมฆ 4 บล็อก อายุเมฆ 5 วินาที เลือดไหล 1 HP/วินาที ความเร็วโจมตี -50% และระยะคุ้มกันหัวใจ 5 วินาทีเป็นค่าปัจจุบันใน event code ไม่ใช่ config keys ข้างต้น

สำหรับโลกทดสอบ แอดมินตั้ง Max Health 100 ให้ผู้เล่นสองคนและเติมเลือดได้ดังนี้ เปลี่ยน `Caster`/`Target` เป็นชื่อจริง:

```mcfunction
/attribute Caster minecraft:generic.max_health base set 100
/attribute Target minecraft:generic.max_health base set 100
/effect give Caster minecraft:instant_health 1 6 true
/effect give Target minecraft:instant_health 1 6 true
```

เมื่อไม่มีพันธสัญญาบล็อกการรักษา ทั้งคู่เต็ม 100 HP จะผ่านเกณฑ์สุขภาพกาฝาก ต้องผ่านระยะและคูลดาวน์ด้วย หลังทดสอบคืน base เดิม เช่น vanilla ใช้ `/attribute Caster minecraft:generic.max_health base set 20` และทำเช่นเดียวกันกับ Target ไม่มีคำสั่งเหล่านี้รันอัตโนมัติ รายละเอียดการทดสอบขอบเขต 150/50 และผลทดสอบอยู่ใน [TESTING](TESTING.md)

## สูตร Survival และรูปลักษณ์

แต่ละสูตรเป็น shapeless ใช้วัตถุดิบอย่างละหนึ่ง ผลลัพธ์หนึ่งชิ้น วัตถุดิบและ texture มี namespace `minecraft:` ผลลัพธ์มี namespace `thida:`

| ID | Texture item | วัตถุดิบ |
| --- | --- | --- |
| `ward` | `gold_ingot` | paper + gold_ingot + amethyst_shard |
| `veil` | `phantom_membrane` | paper + ink_sac + phantom_membrane |
| `grace` | `feather` | paper + feather + glowstone_dust |
| `remedy` | `glistering_melon_slice` | glass_bottle + glistering_melon_slice + sugar |
| `herbal_orb` | `slime_ball` | slime_ball + wheat_seeds + ghast_tear |
| `narotra_doll` | `wheat` | wheat + string + gold_nugget |
| `nilakan_doll` | `fermented_spider_eye` | wheat + string + fermented_spider_eye |
| `parasite_doll` | `spider_eye` | wheat + string + spider_eye + redstone |
| `heart_doll` | `heart_of_the_sea` | wheat + string + heart_of_the_sea |
| `domain_talisman` | `nautilus_shell` | paper + nautilus_shell + ender_pearl + amethyst_shard |

คราฟต์ได้ตามปกติเมื่อ `doLimitedCrafting=false` ไม่มี advancement ปลดล็อกสูตรอัตโนมัติ เซิร์ฟเวอร์ที่เปิด limited crafting ต้องจัดการปลดล็อกสูตรเอง การคราฟต์ไม่ให้สิทธิ์ Origin โมเดลทั้งสิบใช้ `minecraft:item/generated` กับ texture vanilla ที่ต่างกัน พร้อมชื่อไทยและอังกฤษ

## ทรัพยากรและการเชื่อม Origins

ไฟล์อยู่ใต้ `src/main/resources` และใช้ metadata/การบรรจุของแอดออน ไม่ใช่ datapack ZIP เดี่ยว

- `data/thida/origins/thida.json`: Origin ธิดา
- `data/thida/powers/arts.json`: `origins:simple` แสดงคำอธิบาย ไม่มี active ability ซ้ำ
- `data/thida/powers/origin_callbacks.json`: gained/chosen/added/respawned เรียก `tag @s add thida_origin`; lost เรียก `tag @s remove thida_origin`
- `data/origins/origin_layers/origin.json`: ผสาน Origin ด้วย `replace: false`
- `assets/thida/models/item/`, `assets/thida/lang/`, `data/thida/recipes/`: โมเดล คำแปล และสูตรไอเทม
- `data/thida/damage_type/shared.json`: `message_id=thida.shared`, `scaling=never`, `exhaustion=0`
- `data/minecraft/tags/damage_type/`: ผสาน `thida:shared` ใน `bypasses_armor`, `bypasses_effects`, `bypasses_resistance`, `bypasses_enchantments`, `bypasses_cooldown`, `bypasses_shield` ด้วย `replace: false`

การเข้าโลกใช้ `entity_action_added` ตาม schema ไม่มี `entity_action_join` ไม่ลบแท็กผ่าน `entity_action_removed` เพราะเกี่ยวกับ logout/respawn ด้วย เมื่อเสีย Origin callback lost ลบแท็กทันที ทำให้ kit และความสามารถตรวจสิทธิ์ไม่ผ่าน ส่วน Java ล้างสถานะลิงก์เมื่อ tick ตรวจพบและเมื่อเกิด death/logout/dimension events โดยรักษาคูลดาวน์ ไม่มี function แจกแท็กหรือสิทธิ์แก่ผู้เล่นทั่วไป

ข้อความตายมี `death.attack.thida.shared` และ `.player` ทั้งสองภาษา โดย `%1$s` เป็นผู้เสียชีวิต และ `%2$s` เป็นผู้เล่นที่เกี่ยวข้องในแบบ player

อ้างอิง schema ทางการ: [Callback](https://origins.readthedocs.io/en/1.10.0/types/power_types/action_on_callback/), [Simple](https://origins.readthedocs.io/en/1.10.0/types/power_types/simple/), [Origin](https://origins.readthedocs.io/en/1.10.0/json/origin/), [Layer](https://origins.readthedocs.io/en/1.10.0/json/origin_layer/) และ [Origins Forge releases](https://github.com/EdwinMindcraft/origins-architectury/releases)


