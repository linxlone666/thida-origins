package dev.thida;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectLifecycleTest {
  @Test
  void startupSoundIsNotReplayedByLoopRestoreOrReload() {
    var sound =
        ThidaEffectBinding.read(
            "sound",
            JsonParser.parseString(
                    "{\"resource\":\"thida:placeholder\",\"loop\":true,\"sound\":\"minecraft:block.amethyst_block.chime\"}")
                .getAsJsonObject());
    ThidaClientEffects.reloadMappings(List.of(sound));
    ThidaClientEffects.accept(message(UUID.randomUUID(), 1, ThidaEffectEvent.Phase.START));
    ThidaClientEffects.tick();
    assertEquals(1, backend.sounds);
    ThidaClientEffects.reloadMappings(List.of(sound));
    ThidaClientEffects.tick();
    ThidaClientEffects.accept(message(UUID.randomUUID(), 2, ThidaEffectEvent.Phase.SYNC));
    ThidaClientEffects.tick();
    assertEquals(1, backend.sounds);
  }

  private final UUID epoch = UUID.randomUUID();
  private final UUID owner = UUID.randomUUID();
  private final UUID target = UUID.randomUUID();
  private final Backend backend = new Backend();
  private boolean loaded = true;

  @BeforeEach
  void setup() {
    ThidaClientEffects.clear();
    ThidaClientEffects.installAdapter(backend);
    ThidaClientEffects.installResolver(
        (event, binding, elapsed, remaining) ->
            loaded
                ? new ThidaPassiveEffectAdapter.Frame(event.point(), 0, 0, elapsed, remaining)
                : null);
    ThidaClientEffects.reloadMappings(List.of(binding("loop", true), binding("pulse", false)));
  }

  @AfterEach
  void cleanup() {
    ThidaClientEffects.clear();
    ThidaClientEffects.installAdapter(null);
  }

  private ThidaEffectBinding binding(String id, boolean loop) {
    return ThidaEffectBinding.read(
        id,
        JsonParser.parseString("{\"resource\":\"thida:placeholder\",\"loop\":" + loop + "}")
            .getAsJsonObject());
  }

  private ThidaEffectEvent message(UUID instance, long revision, ThidaEffectEvent.Phase phase) {
    var point = new ThidaEffectEvent.Point("minecraft:the_nether", 1, 2, 3);
    return new ThidaEffectEvent(
        epoch,
        revision,
        instance,
        instance,
        "SPELL_EFFECT_STARTED",
        "narotra_doll",
        "narotra_shield",
        "spell",
        phase,
        owner,
        target,
        new ThidaEffectEvent.Point("minecraft:overworld", 10, 20, 30),
        point,
        point,
        ThidaEffectEvent.Anchor.TARGET,
        "OFF_HAND",
        100,
        300,
        100,
        "");
  }

  @Test
  void contractRoundTripKeepsSpellReasonAndDimensionsSeparate() {
    var message = message(UUID.randomUUID(), 1, ThidaEffectEvent.Phase.START);
    assertEquals(message, ThidaEffectEvent.read(message.save()));
    assertEquals("narotra_shield", message.spell());
    assertEquals("", message.reason());
    assertNotEquals(message.ownerPoint().dimension(), message.point().dimension());
  }

  @Test
  void duplicateAndOutOfOrderPacketsDoNotReplayOrResurrect() {
    var message = message(UUID.randomUUID(), 1, ThidaEffectEvent.Phase.START);
    ThidaClientEffects.accept(message);
    ThidaClientEffects.tick();
    assertEquals(2, backend.started);
    ThidaClientEffects.accept(message);
    ThidaClientEffects.tick();
    assertEquals(2, backend.started);
    ThidaClientEffects.accept(
        message.delivery(2, ThidaEffectEvent.Phase.STOP, 120, "SPELL_EFFECT_ENDED", "EXPIRED"));
    assertEquals(2, backend.closed);
    ThidaClientEffects.accept(message);
    ThidaClientEffects.tick();
    assertEquals(3, backend.started); // one STOP pulse, never the stale START/loop
  }

  @Test
  void stopOnlyClosesItsOwnInstanceEvenWithoutEntity() {
    var first = message(UUID.randomUUID(), 1, ThidaEffectEvent.Phase.START);
    var second = message(UUID.randomUUID(), 2, ThidaEffectEvent.Phase.START);
    ThidaClientEffects.accept(first);
    ThidaClientEffects.accept(second);
    ThidaClientEffects.tick();
    loaded = false;
    ThidaClientEffects.accept(
        first.delivery(3, ThidaEffectEvent.Phase.STOP, 120, "SPELL_EFFECT_REPLACED", "REPLACED"));
    assertEquals(2, backend.closed);
    assertTrue(backend.open.contains(second.instance()));
  }

  @Test
  void restoredStateNeverPlaysOneShotAgain() {
    ThidaClientEffects.accept(message(UUID.randomUUID(), 1, ThidaEffectEvent.Phase.SYNC));
    ThidaClientEffects.tick();
    assertEquals(1, backend.started);
  }

  @Test
  void loadingEntityLaterStartsTheLoopSafely() {
    loaded = false;
    ThidaClientEffects.accept(message(UUID.randomUUID(), 1, ThidaEffectEvent.Phase.SYNC));
    ThidaClientEffects.tick();
    assertEquals(0, backend.started);
    loaded = true;
    ThidaClientEffects.tick();
    assertEquals(1, backend.started);
    loaded = false;
    ThidaClientEffects.tick();
    assertEquals(1, backend.closed);
    loaded = true;
    ThidaClientEffects.tick();
    assertEquals(2, backend.started);
  }

  @Test
  void reloadAndAdapterDetachReleaseHandlesWithoutReplayingPulses() {
    ThidaClientEffects.accept(message(UUID.randomUUID(), 1, ThidaEffectEvent.Phase.START));
    ThidaClientEffects.tick();
    ThidaClientEffects.reloadMappings(List.of(binding("loop", true), binding("pulse", false)));
    assertEquals(2, backend.closed);
    ThidaClientEffects.tick();
    assertEquals(3, backend.started);
    ThidaClientEffects.installAdapter(null);
    assertEquals(3, backend.closed);
    ThidaClientEffects.tick();
    assertEquals(3, backend.started);
    ThidaClientEffects.installAdapter(backend);
    ThidaClientEffects.tick();
    assertEquals(4, backend.started);
  }

  @Test
  void expiryAndLogoutReleaseResources() {
    ThidaClientEffects.accept(message(UUID.randomUUID(), 1, ThidaEffectEvent.Phase.SYNC));
    ThidaClientEffects.tick();
    for (int i = 0; i < 200; i++) ThidaClientEffects.tick();
    assertEquals(1, backend.closed);
    assertEquals(0, ThidaClientEffects.activeCount());
    ThidaClientEffects.accept(message(UUID.randomUUID(), 2, ThidaEffectEvent.Phase.SYNC));
    ThidaClientEffects.tick();
    ThidaClientEffects.clear();
    assertEquals(2, backend.closed);
    assertEquals(0, ThidaClientEffects.activeCount());
  }

  @Test
  void invalidMappingsAndBrokenBackendsCannotCrashReceiver() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ThidaEffectBinding.read(
                "bad",
                JsonParser.parseString("{\"resource\":\"bad resource\"}").getAsJsonObject()));
    ThidaClientEffects.installAdapter(
        new ThidaPassiveEffectAdapter() {
          public void onPassiveEvent(CompoundTag event) {
            throw new IllegalStateException("observer unavailable");
          }

          public Handle start(ThidaEffectEvent event, ThidaEffectBinding binding, Frame frame) {
            throw new IllegalStateException("missing resource");
          }
        });
    assertDoesNotThrow(
        () -> {
          ThidaClientEffects.accept(message(UUID.randomUUID(), 1, ThidaEffectEvent.Phase.START));
          ThidaClientEffects.tick();
        });
  }

  private static final class Backend implements ThidaPassiveEffectAdapter {
    int sounds;
    int started, closed;
    final Set<UUID> open = new HashSet<>();

    public void onPassiveEvent(CompoundTag event) {}

    public Handle start(ThidaEffectEvent event, ThidaEffectBinding binding, Frame frame) {
      if (!binding.sound().isEmpty()) sounds++;
      started++;
      open.add(event.instance());
      return () -> {
        closed++;
        open.remove(event.instance());
      };
    }
  }
}
