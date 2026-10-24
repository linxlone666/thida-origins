# Verification — 0.3.0

Target: Minecraft Java 1.20.1, Forge 47.4.0, Origins Forge 1.20.1-1.10.0.9, Caelus 3.2.0+1.20.1 and Temurin JDK 17.

2026-09-14: Build and server integration suite passed: **31 Forge GameTests and 6 JUnit tests**. Final build and GameTest rerun also passed after client input and resource updates; all 34 main JSON resources parsed successfully.

## Automated checks

- `./build.ps1`: Java compilation, JUnit arithmetic tests, reobfuscated player JAR and companion resource ZIP.
- `./build.ps1 -Tasks runGameTestServer`: headless Forge server using actual Origins and Caelus mods, combat-enabled fake players and real event bus/vanilla healing paths.
- Coverage: Origin callbacks, kit command, permission rejection, all three Nilakan effects, separate effect cooldowns, explicit cancellation and relink lockout, one-way ten-second Narotra gaze, speed plus healing, custom-debuff cleanse, persistent marks, parasite summon prerequisite and damage split, strict caster-health break, one-time +50 HP reward, Totem survival, unlimited heart range, one-use immortality, external healing provenance, and stationary domain geometry/end-based cooldown.
- Domain regression: all three temporary curses apply without replacing the existing manual mark; heart restrictions remain active while domain is running.
- GameTest code is in a separate source set and excluded from the player JAR.

## Manual client/multiplayer checks still required

These are server integration tests, not a real-player graphical playthrough. Check on two modded clients:

1. Select Thida: receive both Passive dolls. Hold Nilakan and hit player A. Hold Narotra and gaze at player B for ten uninterrupted seconds. Confirm target switching resets gaze.
2. With a doll in a hotbar slot other than slot 1, press 1. Confirm menu opens without switching slots; 2/3/4 casts the matching effect, 5 cancels, Esc closes. Test offhand selection and latency.
3. Verify separate 30-second effect cooldowns and the 10-minute cancellation lockout; marks survive swaps/reconnects and changing distance.
4. Summon parasite by right-click. Confirm visible small pumpkin doll, successful hit linking, Passive lockout and doll removal on cancellation/break. At 25 caster HP exactly do not break; below 25 after shared damage do break and heal +50 once, capped at max health.
5. Heart: recipient cannot melee or self-heal, can use other skills, receives one five-second save. External sourced healing releases; owner damage releases and bleeds ten seconds. Domain remains usable; other dolls remain locked.
6. Walk out of a cast domain: center remains fixed. Verify protection inside, strengthened linked Narotra player, all three curses on their hits, five-minute duration, then twenty-minute cooldown.
7. Test death, logout, dimension changes, Origin removal, server restart and interactions with the server's health/skill mods.

## Integration limits / provisional behavior

- External healing without source identity cannot be distinguished from self-healing. Other mods should call `ThidaEvents.addonHeal(caster, target, amount)`.
- Direct `setHealth()` can bypass Forge combat/healing events. Skills using playerAttack damage sources may be treated as melee by the covenant restriction.
- Rendering, number-key UI behavior, packet latency, custom mod compatibility and balance require client testing.
- Parasite is a small Armor Stand with a pumpkin head. Narotra combat behavior applies to its linked player; no independent combat NPC is included.
- Share ratio 50/50, Narotra heal 8 HP, curse vulnerability +30%, escape speed duration and domain effect levels are provisional defaults, documented in POWERS.md.
- New config keys replace the obsolete 0.1.0 duration/combined-health keys. Existing customized servers should review the new configuration.


## HUD checks added in 0.3.0

Three server GameTests cover snapshot target names, per-effect cooldown remaining ticks, expired cooldown clamping, incoming guardian display, omission of private internal state/other users' links, offline cached names, NBT wire round-trip, defensive copies, client countdown arithmetic and minute formatting, gaze progress, stationary domain remaining time and inside/outside state. All 31 GameTests and 6 JUnit tests passed in the final 0.3.0 build.

Dedicated-server startup also verifies client rendering classes do not load on the server. Packet transport across two real clients, actual overlay drawing, Thai text clipping at each GUI scale and audible cue volume have not been manually verified.

Client acceptance: open the number-key menu and verify per-action cooldown text; link/unlink each doll and hear one cue; wait for a cooldown to expire and hear readiness once; check gaze updates, domain boundary, F1/F3 hiding, reconnect without replayed sounds, offline labels, and the recipient guardian/immortality panel. Set showHud=false and cueSounds=false independently in config/thida-client.toml and verify the corresponding feature is disabled. Compare multiple GUI scales and long player names.
