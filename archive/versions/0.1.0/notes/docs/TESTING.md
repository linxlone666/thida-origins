# Verification

Target: Minecraft Java 1.20.1, Forge 47.4.0, Origins Forge 1.20.1-1.10.0.9, Caelus 3.2.0+1.20.1 and Temurin JDK 17.0.20.1.

**2026-09-09 result: BUILD SUCCESSFUL; all 21 Forge GameTests and all 6 JUnit tests passed.** All 34 main JSON resources parse. The delivered JAR contains both mixin configuration and generated production refmap, and excludes GameTest classes.

## Automated checks

- `./build.ps1`: compiles Java, runs JUnit arithmetic tests and produces the reobfuscated mod JAR plus the companion resource ZIP.
- `./build.ps1 -Tasks runGameTestServer`: starts a headless Forge GameTest server with the actual Origins and Caelus mods. Tests are isolated in the `gametest` source set and are not shipped in the player JAR.
- The GameTests exercise real Forge events and vanilla healing/totem paths with combat-enabled fake players. Fixtures register both the player list and UUID index and remove them after each test.
- Tests include Origin selection/revocation through the real `/origin` command, item registration, kit command, ward, grace, cleanse, permission rejection, both curse modes, damage sharing, Totem survival, covenant recipient restrictions, external/self healing provenance, immunity, dual-hand marks, low-health rejection, one-time break rewards, and logout cleanup.

## Scope of verification

These are server integration tests, not a visual client playthrough or a stress test with real networked players. Manual checks remain for item rendering/tooltips, projectile appearance, continuous gaze feel, domain particles, latency, balance and interactions with a server's other mods.

Healing supplied by arbitrary mods without a healer identity requires the public `ThidaEvents.addonHeal` integration hook. Mods that directly set health can bypass Forge events. The source tracking for regeneration follows the latest accepted regeneration application; unusual stacking of hidden regeneration effects from multiple healers needs compatibility testing.

## Manual multiplayer acceptance

1. Install the documented versions on a test server and two clients; select Thida on one player. Confirm switching to Human removes access to `/thida kit` and active abilities.
2. Obtain the kit, mark a second player with the two ordinary dolls, swap hands, and verify both marks remain. Test each mode and uninterrupted mutual gaze for 10 seconds.
3. Assign both players the same scoreboard team, throw the herbal orb, and verify the cloud heals allies but not an unteamed third player.
4. Test parasite with both players at 100 maximum/current HP: damage either player, verify the configured split and break reward at combined HP <=50. Verify manual release, leaving range and logout give no reward.
5. Form a heart covenant: confirm the recipient can use non-healing skills but cannot damage others or heal themselves. Lethal damage should leave 1 HP and five seconds of immunity, once per contract. A sourced external heal releases the covenant; damaging its owner causes ten seconds of bleeding.
6. Activate domain and compare the same damage source before/during its duration. Verify the marked ally's buff and automatic curse transfer on hit inside the 30-block radius.
7. Repeat after death, respawn, dimension changes, Origin changes and reconnects. Check for stale marks and cooldown resets.
