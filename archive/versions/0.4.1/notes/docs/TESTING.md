# Verification — 0.4.1

Minecraft 1.20.1 / Forge 47.4.0 / Origins Forge 1.20.1-1.10.0.9 / Caelus 3.2.0 / Java 17.

The implementation build passed with 29 Forge GameTests and 8 JUnit tests. The three obsolete power tests were replaced with a retirement regression test; two pixel-sprite tests were added. All retained bond/combat/HUD data tests remain active.

## What is covered

- Actual Forge events: combat sharing, strict caster-health break, one-time heal, covenant protection and sourced healing, stationary domain, all three curses, gaze linking, cancellation cooldowns and mark persistence.
- Origin selection and kit now expose five active abilities only.
- Every retired item is inert both through Item.use and the direct server ability hook, has no recipe and is excluded from the active ability map.
- HUD packet data, privacy, offline names, NBT round-trip, countdown arithmetic, gaze and domain state.
- All 13 sprite masks are 16×16, have transparent backgrounds, use fully opaque pixel colors and distinguish the two passive dolls. Generated PNGs use the same source masks as the HUD.
- Pixel contact sheet was visually inspected; five custom item textures replace vanilla textures.

## Manual client checks still required

The menu/HUD classes compile, but the actual GUI and sound have not been exercised in a Minecraft client. Server tests do not validate rendered text, mouse hitboxes, resource-pack overrides or perceived audio levels.

1. Install 0.4.1 on both client and server. Open an existing save with old items; verify “Retired” labels and no ability use. Kit/creative/crafting should expose only current powers.
2. Check the five relic textures in inventory, main hand and offhand.
3. Open the pixel menu from a doll outside hotbar slot 1; hover/click each tile and use number keys, Tab/Enter and Esc. Disabled tiles must not cast; cancellation must work even if the target is offline.
4. Compare GUI scales and long player names. Check HUD frames, key cooldown strips, focus outlines, locked state, gaze progress, domain bar and recipient protection. HUD must not overlap open screens and must hide with F1/F3.
5. Repeat link/unlink, domain expiry and cooldown readiness; verify a single sound cue per update and no replay on reconnect. Client config still independently controls HUD and sound.

## Art reproducibility

The source of truth is src/main/java/dev/thida/PixelSprites.java. Compile it together with tools/ExportPixelArt.java using JDK 17, then run ExportPixelArt from the project root. It writes the five item textures, 13 GUI PNGs and build/art/pixel-relics.png. Rebuild the mod after exporting.

## Integration limits

External healing without healer identity needs ThidaEvents.addonHeal. Direct setHealth can bypass Forge events. Skills using playerAttack damage may be classified as melee. Parasite visuals use the existing small Armor Stand; the Narotra combat actor remains the linked player. Balance defaults and the temporary domain curse interpretation are documented in POWERS.md.


## 0.4.1 control update

The menu opener is a registered Forge KeyMapping (default R, category Thida) with IN_GAME context. Minecraft persists rebindings in its own Controls settings. Keyboard and mouse bindings use normal KeyMapping click consumption; a competing hotbar/attack click is consumed only when actually opening a held doll's menu. Number keys 1–4 and mouse tiles map to the existing server actions 2–5; server action 1 remains the internal open-session request. HUD labels and readiness messages use effect keys 1–3. No combat rules or cooldown IDs were changed.

Manual acceptance still needed: change R to G, to hotbar key 1, and to a spare mouse button; verify no hotbar switch/attack on opening, no opener action while typing/chat/inventory, 1–3 activate the three matching effects, 4 cancels, click and Tab/Enter still work, and custom binding persists after restart.
