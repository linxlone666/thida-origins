package dev.thida;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Explicit integration contract. No invented mana price, heal basis or magic scaling. */
public final class ThidaSkillIntegration {
    public static final double BASE_PERCENT = .20;
    public static final float BASE_BLEED_HP = 2;
    public interface Payment { boolean consume(); }
    public record Quote(double percent, float healHp, float bleedHp, Payment payment) {
        public boolean valid() {
            return Double.isFinite(percent) && percent >= 0 && Float.isFinite(healHp) && healHp >= 0
                    && Float.isFinite(bleedHp) && bleedHp >= 0 && payment != null;
        }
    }
    public interface Adapter {
        Optional<Quote> quote(ServerPlayer caster, String doll, int spell);
        Optional<Double> parasiteBreakReduction(ServerPlayer caster, ServerPlayer target);
    }
    private static final Adapter PENDING = new Adapter() {
        public Optional<Quote> quote(ServerPlayer p, String doll, int spell) { return Optional.empty(); }
        public Optional<Double> parasiteBreakReduction(ServerPlayer p, ServerPlayer target) { return Optional.empty(); }
    };
    private static Adapter adapter = PENDING;
    public static void install(Adapter next) { adapter = next == null ? PENDING : next; }
    public static Optional<Quote> quote(ServerPlayer p, String doll, int spell) { return adapter.quote(p, doll, spell).filter(Quote::valid); }
    public static Optional<Double> breakReduction(ServerPlayer p, ServerPlayer target) {
        return adapter.parasiteBreakReduction(p, target).filter(n -> Double.isFinite(n) && n >= 0);
    }
    private ThidaSkillIntegration() {}
}
