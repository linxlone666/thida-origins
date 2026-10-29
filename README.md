# Thida Origins

Minecraft Origins mod for **Minecraft 1.21.1, NeoForge 21.1.233, Java 21**.

ม็อด Origin ธิดา พร้อมเอกสารสำหรับพัฒนาต่อ เลขเวอร์ชันม็อดกำหนดใน [gradle.properties](gradle.properties) ที่ค่า `mod_version`

## Downloads

- [Original release artifacts, notes and checksums](archive/versions/)

See [archive notes](archive/README.md) when inspecting an older release.

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

Keys can be changed in Minecraft Controls. Keep client and server mod versions compatible.

## Documentation

- [Skill specifications (Thai)](docs/SKILLS_V7_TH.md)
- [Personal magic/stat integration](docs/PERSONAL_MOD_INTEGRATION_TH.md)
- [Effect adapter handoff](docs/SKILL_EFFECT_ADAPTER_TH.md)
- [Architecture](docs/ARCHITECTURE_TH.md)
- [Previous implementation notes](docs/history/)

## Licensing

Project license: [MIT](LICENSE). The bundled **Itim** font retains its separate **SIL OFL 1.1** license and copyright notices in [META-INF/licenses/Itim](src/main/resources/META-INF/licenses/Itim/). The restricted ThaiFontFix font pack is not included in this repository.

## Continuing development

Use this Git checkout for future work. Update `gradle.properties` when releasing a new version, commit the changes, then add and push a new annotated version tag. Keep existing tags and historical JAR checksums immutable.
