# Verification — 0.5.1-debug

Target: Minecraft 1.20.1 / Forge 47.4.0 / Origins Forge 1.20.1-1.10.0.9 / Caelus 3.2.0 / Java 17.

## New automated coverage

- Three pure timeline tests: 20 seconds active then 10 seconds remaining cooldown then hidden, instantaneous and sustained skills, and effects outlasting their cooldown.
- Two Forge tests: server snapshot tracks actual cast duration and concurrent cooldown; instant cleanse has no fictitious active phase, and cleansing a curse ends its active icon without resetting cooldown.
- Retained tests cover combat, bindings, permanent marks, heart healing/protection, domain, retired powers, HUD serialization/privacy, sprites and configuration.

## Manual client acceptance still required

1. With no active spells or cooldowns, the HUD should draw nothing. Passive marks alone must not create permanent panels.
2. Cast each effect: show a bright icon with green edge and active countdown; then a dim CD icon with only the real remaining cooldown; then hide. Cleanse should go straight to CD. Repeat after reconnect and early cleanse/death of the target.
3. Cast multiple effects: one icon per effect, stable order, readable short timer and number 1–3, with wrapping according to iconsPerRow. Check each GUI scale.
4. Parasite/heart display infinity until termination, then their existing cooldown. Domain shows five-minute duration then twenty-minute cooldown. Check transient gaze and recipient immunity.
5. Controls remain R (rebindable) to open, 1–3 to cast and 4 to unlink; menu retains target names. Sounds remain enabled independently of HUD and do not replay on reconnect.

Rendering in a real Minecraft client and real network latency have not been manually tested. Install 0.5.1-debug on both client and server for this release. The full 0.4.1 and Lite 0.5.0-lite releases are preserved separately.

Final build passed: 33 Forge GameTests and 11 JUnit tests (44 total).

## Debug validation

Two new Forge GameTests execute the registered Brigadier commands: non-OP cooldown reset is rejected with no mutation; OP reset clears both per-effect and per-ability cooldowns while preserving links, active domain/effect timers and consumed heart save; debug end terminates owned bonds/domain and removes the summoned visual doll without healing/injury, then starts normal cooldowns.

Manual client checks: /thidahud position and rows must update layout and persist to client config, reset restores defaults, preview shows only sample icons marked P for ten seconds after closing chat, and preview must not change any server skill state. Verify OP and console command usage on a multiplayer server. Real client GUI and local config persistence have not been manually exercised.
