# Thida Origins

This branch implements **เสน่ห์มายารมณ์ (Mayarom)**, version `0.9.0-mayarom-dev`,
on the recovered 0.8.9 source below. Press `4` to activate/cancel after an operator runs
`/thida unlock <player> skill4`. See [ultimate rules and integration handoff](docs/ULTIMATE_MAYAROM_TH.md).
The alternative ultimate **เรือนเสน่หา** is not included in this branch.

Minecraft Origins mod. Current recovered source: **0.8.9-neoforge-dev** for **Minecraft 1.21.1, NeoForge 21.1.233, Java 21**.

ม็อด Origin ธิดา — ซอร์สปัจจุบัน 0.8.9 พร้อมเอกสารภาษาไทยและประวัติรุ่นที่กู้คืน

## Downloads and history

- [Latest archived JAR: 0.8.9](archive/versions/0.8.9-neoforge-dev/thida-origins-1.21.1-neoforge-0.8.9-neoforge-dev.jar)
- [Reconstructed history and version table](HISTORY.md)
- [คำอธิบายประวัติภาษาไทย](HISTORY_TH.md)
- [Original release artifacts, notes and checksums](archive/versions/)

History covers 29 releases from 0.1.0 through 0.8.9, plus a saved Forge passive rewrite. Not every historical source snapshot survived. Tags beginning `archive/` identify binary-only milestones; they do not pretend that missing source code was recovered. Commit timestamps are import times. Imported commits are attributed to **linxlone666** with the GitHub noreply identity.

## Build the current source

Install a Java 21 JDK and set `JAVA_HOME`. Gradle downloads the dependencies declared in `build.gradle` on the first build.

Windows:

```powershell
.\gradlew.bat build
```

Linux/macOS:

```sh
./gradlew build
```

Output: `build-neoforge/libs/`. Historical Forge tags require their original Minecraft/Forge/Java versions, rather than the current profile. No local caches, JDK installations, worlds or dependency JARs are included.

## Current controls

- `1`: doll/spell menu; mouse wheel selects, left click confirms, right click/Esc returns.
- `2`: parasite doll action.
- `3`: heart bond action.
- `H`: separate HUD editor; drag panels and adjust their scale.

Keys can be changed in Minecraft Controls. Keep client and server mod versions compatible. Spell and mana integrations are described in the existing project documents; this history import does not change gameplay behavior.

## Documentation

- [Skill specifications (Thai)](docs/SKILLS_V7_TH.md)
- [Personal magic/stat integration](docs/PERSONAL_MOD_INTEGRATION_TH.md)
- [Effect adapter handoff](docs/SKILL_EFFECT_ADAPTER_TH.md)
- [Embedded Itim font and license details](docs/FONT_0_8_5_TH.md)
- [Font/HP and ThaiFontFix compatibility update](docs/FONT_HP_0_8_6_TH.md)
- [Compact single-line menu footer](docs/MENU_0_8_9_TH.md)

## Licensing

Project license: [MIT](LICENSE). The bundled **Itim** font retains its separate **SIL OFL 1.1** license and copyright notices in [META-INF/licenses/Itim](src/main/resources/META-INF/licenses/Itim/). The restricted ThaiFontFix font pack is not included in this repository.

## Continuing development

Use this Git checkout for future work. Update `gradle.properties` when releasing a new version, commit the changes, then add and push a new annotated version tag. Keep existing tags and historical JAR checksums immutable. See `RECOVERED_SOURCE_STATUS.md` when inspecting an older archive-only tag.
