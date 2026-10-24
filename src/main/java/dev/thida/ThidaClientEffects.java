package dev.thida;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/** Client-thread lifecycle manager. No Minecraft client classes or gameplay mutators. */
public final class ThidaClientEffects {
  public interface FrameResolver {
    ThidaPassiveEffectAdapter.Frame resolve(
        ThidaEffectEvent event, ThidaEffectBinding binding, double elapsed, long remaining);
  }

  private static Consumer<CompoundTag> confirmation = data -> {};
  private static final ThidaPassiveEffectAdapter NOOP = event -> {};
  private static ThidaPassiveEffectAdapter adapter = NOOP;
  private static FrameResolver resolver = (event, binding, elapsed, remaining) -> null;
  private static List<ThidaEffectBinding> bindings = List.of();
  private static final Map<UUID, Playing> active = new LinkedHashMap<>();
  private static final LinkedHashMap<UUID, Long> revisions = new LinkedHashMap<>();
  private static final Set<String> warnings = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private static UUID epoch;
  private static long ticks;

  private ThidaClientEffects() {}

  public static void installConfirmation(Consumer<CompoundTag> handler) {
    confirmation = handler;
  }

  public static void confirmation(CompoundTag data) {
    if (data != null) confirmation.accept(data.copy());
  }

  public static void installResolver(FrameResolver next) {
    resolver = next == null ? (event, binding, elapsed, remaining) -> null : next;
  }

  public static void installAdapter(ThidaPassiveEffectAdapter next) {
    releaseHandles();
    if (adapter != next)
      try {
        adapter.close();
      } catch (RuntimeException error) {
        warnOnce("adapter-close", error);
      }
    adapter = next == null ? NOOP : next;
    resumeLoops();
  }

  public static void reloadMappings(List<ThidaEffectBinding> next) {
    releaseHandles();
    try {
      adapter.onResourceReload();
    } catch (RuntimeException error) {
      warnOnce("reload", error);
    }
    bindings = List.copyOf(next);
    warnings.clear();
    resumeLoops();
  }

  private static void resumeLoops() {
    active.values().removeIf(state -> state.event.phase() == ThidaEffectEvent.Phase.ONE_SHOT);
    for (var state : active.values()) {
      state.event =
          state.event.delivery(
              state.event.sequence(),
              ThidaEffectEvent.Phase.SYNC,
              state.event.sentTick(),
              state.event.event(),
              "RESTORED");
      state.attempted.clear();
    }
  }

  public static void accept(CompoundTag data) {
    if (data == null) return;
    try {
      accept(ThidaEffectEvent.read(data));
    } catch (RuntimeException error) {
      warnOnce("packet", error);
    }
  }

  public static void accept(ThidaEffectEvent event) {
    if (!event.epoch().equals(epoch)) {
      clear();
      epoch = event.epoch();
    }
    long previous = revisions.getOrDefault(event.instance(), -1L);
    if (event.sequence() <= previous) return;
    revisions.put(event.instance(), event.sequence());
    while (revisions.size() > 8192) revisions.remove(revisions.keySet().iterator().next());
    try {
      adapter.onPassiveEvent(event.save());
    } catch (RuntimeException error) {
      warnOnce("observer", error);
    }
    if (event.phase() == ThidaEffectEvent.Phase.STOP) {
      var old = active.remove(event.instance());
      if (old != null) close(old);
      var pulse = new Playing(event, ticks);
      pulse.stopPulse = true;
      active.put(event.instance(), pulse);
    } else {
      var old = active.get(event.instance());
      if (old != null) close(old);
      active.put(event.instance(), new Playing(event, ticks));
    }
  }

  public static void tick() {
    ticks++;
    var iterator = active.values().iterator();
    while (iterator.hasNext()) {
      var state = iterator.next();
      long age = ticks - state.received;
      long remaining =
          state.event.endsTick() == 0
              ? Long.MAX_VALUE
              : Math.max(0, state.event.endsTick() - state.event.sentTick() - age);
      boolean pulse = state.stopPulse || state.event.phase() == ThidaEffectEvent.Phase.ONE_SHOT;
      if (!pulse && remaining == 0 || pulse && age > 200) {
        close(state);
        iterator.remove();
        continue;
      }
      for (var binding : state.preview == null ? bindings : state.preview) {
        if (!binding.matches(state.event)) continue;
        if (state.event.phase() == ThidaEffectEvent.Phase.SYNC && !binding.loop()) continue;
        if (pulse && binding.loop()) continue;
        long lifetime =
            binding.lifetimeTicks() > 0 ? binding.lifetimeTicks() : pulse ? 20 : Long.MAX_VALUE;
        if (age >= lifetime) {
          closeHandle(state.handles.remove(binding.id()));
          state.attempted.add(binding.id());
          continue;
        }
        ThidaPassiveEffectAdapter.Frame frame;
        try {
          frame =
              resolver.resolve(
                  state.event,
                  binding,
                  Math.max(0, state.event.sentTick() - state.event.startedTick()) + age,
                  pulse
                      ? Math.max(0, lifetime - age)
                      : Math.min(
                          remaining, lifetime == Long.MAX_VALUE ? remaining : lifetime - age));
        } catch (RuntimeException error) {
          warnOnce("anchor:" + binding.id(), error);
          frame = null;
        }
        if (frame == null) {
          closeHandle(state.handles.remove(binding.id()));
          if (binding.loop()) state.attempted.remove(binding.id());
          continue;
        }
        if (!state.attempted.contains(binding.id())) {
          state.attempted.add(binding.id());
          try {
            var effectiveBinding =
                state.event.phase() == ThidaEffectEvent.Phase.SYNC
                    ? binding.withoutStartupSound()
                    : binding;
            var handle = adapter.start(state.event, effectiveBinding, frame);
            if (handle != null) state.handles.put(binding.id(), handle);
            else if (adapter != NOOP)
              warnOnce(
                  binding.resource(), new IllegalArgumentException("Backend/resource unavailable"));
          } catch (RuntimeException error) {
            warnOnce(binding.resource(), error);
          }
        }
        var handle = state.handles.get(binding.id());
        if (handle != null) {
          try {
            handle.update(frame);
          } catch (RuntimeException error) {
            warnOnce(binding.resource(), error);
            closeHandle(state.handles.remove(binding.id()));
          }
        }
      }
    }
  }

  public static void preview(ThidaEffectEvent.Point point, UUID player, String resource) {
    UUID instance = UUID.randomUUID();
    if (epoch == null) epoch = UUID.randomUUID();
    var event =
        new ThidaEffectEvent(
            epoch,
            0,
            instance,
            instance,
            "PREVIEW",
            "preview",
            "",
            "preview",
            ThidaEffectEvent.Phase.START,
            player,
            player,
            point,
            point,
            point,
            ThidaEffectEvent.Anchor.WORLD,
            "",
            0,
            100,
            0,
            "LOCAL_PREVIEW");
    accept(event);
    active.get(instance).preview =
        List.of(
            new ThidaEffectBinding(
                "local-preview",
                "PREVIEW",
                "preview",
                "*",
                "*",
                resource,
                ThidaEffectEvent.Anchor.WORLD,
                new ThidaEffectBinding.Vector(0, 1, 0),
                new ThidaEffectBinding.Vector(0, 0, 0),
                1,
                true,
                100,
                "",
                1,
                1));
  }

  public static void worldChanged() {
    releaseHandles();
    resumeLoops();
  }

  public static void clear() {
    releaseHandles();
    active.clear();
    revisions.clear();
    warnings.clear();
    epoch = null;
    ticks = 0;
  }

  public static int activeCount() {
    return active.size();
  }

  private static void releaseHandles() {
    for (var state : active.values()) close(state);
  }

  private static void close(Playing state) {
    for (var handle : state.handles.values()) closeHandle(handle);
    state.handles.clear();
  }

  private static void closeHandle(ThidaPassiveEffectAdapter.Handle handle) {
    if (handle != null)
      try {
        handle.close();
      } catch (RuntimeException error) {
        warnOnce("close", error);
      }
  }

  public static void warnOnce(String key, RuntimeException error) {
    if (warnings.size() < 128 && warnings.add(key))
      com.mojang.logging.LogUtils.getLogger()
          .warn("Thida effect skipped [{}]: {}", key, error.toString());
  }

  private static final class Playing {
    ThidaEffectEvent event;
    final long received;
    boolean stopPulse;
    List<ThidaEffectBinding> preview;
    final Map<String, ThidaPassiveEffectAdapter.Handle> handles = new HashMap<>();
    final Set<String> attempted = new HashSet<>();

    Playing(ThidaEffectEvent event, long received) {
      this.event = event;
      this.received = received;
    }
  }
}
