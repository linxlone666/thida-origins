package handoff.example;

import dev.thida.ThidaManaIntegration;
import dev.thida.ThidaMayaromScaling;
import dev.thida.ThidaSkillIntegration;
import dev.thida.ThidaSpell;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Documentation-only facades: substitute calls to the personal mod's confirmed API here. */
public final class PersonalProviderExample {
    public interface MagicStats {
        double magicPower(UUID player);
    }
    public static void registerMayarom(MagicStats stats) {
        ThidaMayaromScaling.install(player -> stats.magicPower(player.getUUID()));
    }
    public interface PersonalMana {
        double maximum(UUID player);
        double current(UUID player);
        void set(UUID player, double value);
    }
    public interface Evaluator {
        // Pure read. Return fully scaled values and a payment tied to this one request.
        Optional<ThidaSkillIntegration.Quote> evaluate(UUID caster, ThidaSpell spell);
        Optional<Double> parasiteDefense(UUID caster, UUID target);
    }
    public static void register(PersonalMana mana, Evaluator evaluator) {
        ThidaManaIntegration.install(new ThidaManaIntegration.Adapter() {
            public double maxMana(ServerPlayer recipient) { return mana.maximum(recipient.getUUID()); }
            public boolean restore(ServerPlayer recipient, float amount, float maximum) {
                double current = mana.current(recipient.getUUID());
                if (!Double.isFinite(current) || current < 0) return false;
                mana.set(recipient.getUUID(), Math.min(maximum, current + amount));
                return true;
            }
        });
        ThidaSkillIntegration.install(new ThidaSkillIntegration.Adapter() {
            public Optional<ThidaSkillIntegration.Quote> quote(ServerPlayer caster, String doll, int index) {
                return ThidaSpell.find(doll, index).flatMap(spell -> evaluator.evaluate(caster.getUUID(), spell));
            }
            public Optional<Double> parasiteBreakReduction(ServerPlayer caster, ServerPlayer target) {
                return evaluator.parasiteDefense(caster.getUUID(), target.getUUID());
            }
        });
    }
    public static void unregister() {
        ThidaMayaromScaling.install(null);
        ThidaManaIntegration.install(null);
        ThidaSkillIntegration.install(null);
    }
}
