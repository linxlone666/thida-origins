package dev.thida;

import net.minecraft.nbt.CompoundTag;

/** Client integration seam. AAA Particles can register a handler without touching game rules. */
public interface ThidaPassiveEffectAdapter {
  /** Legacy observation callback. Managed adapters must not render a second copy here. */
  void onPassiveEvent(CompoundTag event);

  /**
   * Return null when the backend/resource/capability is unavailable. Called on the client thread
   * only.
   */
  default Handle start(ThidaEffectEvent event, ThidaEffectBinding binding, Frame frame) {
    return null;
  }

  default void onResourceReload() {}

  default void close() {}

  record Frame(
      ThidaEffectEvent.Point point,
      float yawDegrees,
      float pitchDegrees,
      double elapsedTicks,
      long remainingTicks) {}

  interface Handle extends AutoCloseable {
    default void update(Frame frame) {}

    @Override
    void close();
  }
}
