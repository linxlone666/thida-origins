package dev.thida;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Other mods should wrap attributed skill heals with apply/withSource. Unknown heals follow the owner's explicit fallback. */
public final class ThidaHealing {
    public enum Kind { SKILL, NON_SKILL, OTHER, UNKNOWN }
    public record Source(ServerPlayer caster, Kind kind) {}
    private static final ThreadLocal<Source> SOURCE = ThreadLocal.withInitial(() -> new Source(null, Kind.UNKNOWN));
    private ThidaHealing() {}
    public static Source current() { return SOURCE.get(); }
    public static void withSource(ServerPlayer caster, Kind kind, Runnable action) {
        Source previous = SOURCE.get(); SOURCE.set(new Source(caster, kind));
        try { action.run(); } finally { SOURCE.set(previous); }
    }
    public static void apply(ServerPlayer caster, LivingEntity target, float amount, Kind kind) {
        withSource(caster, kind, () -> target.heal(amount));
    }
    public static boolean blocked(LivingEntity entity) {
        return entity instanceof ServerPlayer p && ThidaBonds.heartOwner(p) != null
                && current().kind() == Kind.SKILL && current().caster() == p;
    }
    public static void after(LivingEntity entity, float before) {
        if (!(entity instanceof ServerPlayer p) || p.getHealth() <= before) return;
        var source = current(); var owner = ThidaBonds.heartOwner(p);
        if (owner == null) return;
        boolean external = source.caster() != null && source.caster() != p;
        if (external || source.kind() == Kind.UNKNOWN || source.kind() == Kind.OTHER)
            ThidaBonds.endHeart(p.server, owner, external ? "EXTERNAL_HEAL" : "UNATTRIBUTED_HEAL");
    }
}
