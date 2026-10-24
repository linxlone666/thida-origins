package dev.thida.client.effects;

import dev.thida.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;

/** Runnable example, not AAA. Installed only by the explicit local preview command. */
public final class VanillaPreviewAdapter implements ThidaPassiveEffectAdapter {
  @Override
  public void onPassiveEvent(CompoundTag event) {}

  @Override
  public Handle start(ThidaEffectEvent event, ThidaEffectBinding binding, Frame initial) {
    if (!binding.resource().equals("thida:preview/end_rod")) return null;
    return new Handle() {
      private boolean closed;
      private int frames;

      @Override
      public void update(Frame frame) {
        if (closed || ++frames % 4 != 0) return;
        var level = Minecraft.getInstance().level;
        var point = frame.point();
        if (level != null && level.dimension().location().toString().equals(point.dimension()))
          level.addParticle(ParticleTypes.END_ROD, point.x(), point.y(), point.z(), 0, .02, 0);
      }

      @Override
      public void close() {
        closed = true;
      }
    };
  }
}
