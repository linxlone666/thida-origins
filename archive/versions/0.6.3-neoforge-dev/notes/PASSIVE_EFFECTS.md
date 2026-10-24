# Passive effect adapter contract

Current build: Minecraft 1.21.1, NeoForge 21.1.233, Java 21, and IAFEnvoy Origins 0.4-beta.2. The optional mana adapter compiles against Iron's 1.21.1-3.16.3 API.

Narotra and Nilakan rules run on the server in `ThidaPassives`. That class sends visual cues through `ThidaPassiveEffects`; it does not call AAA Particles or any client-only class. `ThidaNetwork.PassiveEffectEvent` transports the event to the linked players, and `ThidaClientEffects` forwards a copy to the installed `ThidaPassiveEffectAdapter`. The default adapter is a no-op, so both passives work without AAA Particles or effect resources.

## Event payload

The packet is an NBT compound with only these fields:

| Field | Meaning |
| --- | --- |
| `event` | Event name listed below |
| `passive` | `NAROTRA` or `NILAKAN` |
| `owner` | Thida player's UUID |
| `target` | Linked/attempted target UUID, when known (also kept for offline targets) |
| `dimension` | Dimension resource location for the event position |
| `x`, `y`, `z` | Effect anchor. For mana restoration this is the recipient's position; otherwise it is the Thida player's position |
| `hand` | `MAIN_HAND`, `OFF_HAND`, or empty |
| `reason` | Stable reason code for a cancellation, pause, or end; otherwise empty |

Events currently emitted:

- Narotra: `NAROTRA_ATTEMPT_STARTED`, `NAROTRA_LINKED`, `NAROTRA_LINK_FAILED`, `NAROTRA_MANA_RESTORED`, `NAROTRA_DEBUFF_CLEANSED`.
- Nilakan: `NILAKAN_ATTEMPT_STARTED`, `NILAKAN_LINKED`, `NILAKAN_LINK_FAILED`, `NILAKAN_DEBUFF_CLEANSED`.
- Shared lifecycle: `LINK_CANCELLED`, `LINK_PAUSED`, `LINK_RESUMED`, and `LINK_ENDED`. `LINK_ENDED` uses `DOLL_TOSSED`, `ORIGIN_LOST`, or `PLAYER_UNLINKED` as its reason.

`LINK_PAUSED` reasons include `TARGET_OFFLINE`, `TARGET_UNAVAILABLE`, `DIMENSION_CHANGED`, and `OUT_OF_RANGE`. Gaze failures include `ITEM_SWITCHED`, `LOOK_LOST`, `OBSTRUCTED`, `OUT_OF_RANGE`, and `NO_TARGET_IN_RANGE_OR_SIGHT`. Nilakan failures include `ORIGIN_REQUIRED`, `OUT_OF_RANGE`, and `UNLINK_BEFORE_RETARGETING`.

## Adding a client adapter

Register it from the client setup path with `ThidaClientEffects.installAdapter(event -> ...)`. Keep all AAA Particles imports and calls inside that adapter. Use `event`, `passive`, and `reason` to select an effect; anchor it at `dimension` and `x/y/z`, and use `target` only to resolve/render the linked entity when it is present in that dimension. Do not add player names, inventory data, or other personal state to the packet.

Put Thida-authored particle resources under `src/main/resources/assets/thida/particles/` and their resource definitions/settings under `src/main/resources/assets/thida/`. The adapter should map these resources to the exact registration format required by the chosen AAA Particles release. That mapping is intentionally not guessed here: no AAA Particles version or API is a dependency of this build. Before adding it, verify its documented Minecraft 1.21.1 NeoForge compatibility and client API, then add its dependency and resource schema in the integration change.

The current code adds no new model, animation, or particle effect. Existing doll item models remain the visual placeholders.
