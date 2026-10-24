# Verification — 0.5.0-lite

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

Rendering in a real Minecraft client and real network latency have not been manually tested. This version changes the HUD protocol, so both client and server must use 0.5.0-lite. The full 0.4.1 release is preserved separately.

Final build passed: 31 Forge GameTests and 11 JUnit tests (42 total).
