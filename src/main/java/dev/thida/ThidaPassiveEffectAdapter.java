package dev.thida;

import net.minecraft.nbt.CompoundTag;

/** Client integration seam. AAA Particles can register a handler without touching game rules. */
public interface ThidaPassiveEffectAdapter {
    void onPassiveEvent(CompoundTag event);
}
