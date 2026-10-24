package dev.thida;

import net.minecraft.nbt.CompoundTag;

/** Client-side event relay; defaults to a no-op so the passive mechanics need no particle mod. */
public final class ThidaClientEffects {
    private static ThidaPassiveEffectAdapter adapter = event -> {};
    private ThidaClientEffects() {}

    public static void installAdapter(ThidaPassiveEffectAdapter next) {
        adapter = next == null ? event -> {} : next;
    }

    public static void accept(CompoundTag event) {
        adapter.onPassiveEvent(event.copy());
    }
}
