package dev.thida;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Optional final-value provider; without one, spells use the approved unscaled values. */
public final class ThidaSkillIntegration {
  public enum Status {
    READY,
    NOT_READY,
    INVALID,
    UNAVAILABLE
  }

  public record Resolution(Status status, Quote quote) {}

  public static final double BASE_PERCENT = .20;
  public static final float BASE_BLEED_HP = 2;
  public static final double BASE_HEAL_FRACTION = .20;

  public interface Payment {
    boolean consume();
  }

  public record Quote(double percent, float healHp, float bleedHp, Payment payment) {
    public boolean valid() {
      return Double.isFinite(percent)
          && percent >= 0
          && Float.isFinite(healHp)
          && healHp >= 0
          && Float.isFinite(bleedHp)
          && bleedHp >= 0
          && payment != null;
    }
  }

  public interface Adapter {
    Optional<Quote> quote(ServerPlayer caster, String doll, int spell);

    default Optional<Quote> quote(ServerPlayer caster, ThidaSpell spell) {
      return quote(caster, spell.doll(), spell.storageIndex());
    }

    Optional<Double> parasiteBreakReduction(ServerPlayer caster, ServerPlayer target);
  }

  private static Adapter adapter;

  public static void install(Adapter next) {
    adapter = next;
    warned = false;
  }

  private static boolean warned;

  public static Resolution resolve(ServerPlayer player, ThidaSpell spell) {
    try {
      Adapter selected = adapter;
      var result = selected == null ? unscaledQuote(player, spell) : selected.quote(player, spell);
      if (result == null || result.isPresent() && !result.get().valid())
        return new Resolution(Status.INVALID, null);
      if (result.isEmpty()) return new Resolution(Status.NOT_READY, null);
      var value = result.get();
      var paid = new java.util.concurrent.atomic.AtomicBoolean();
      return new Resolution(
          Status.READY,
          new Quote(
              value.percent(),
              value.healHp(),
              value.bleedHp(),
              () -> paid.compareAndSet(false, true) && value.payment().consume()));
    } catch (RuntimeException error) {
      if (!warned) {
        com.mojang.logging.LogUtils.getLogger()
            .warn("Thida skill provider failed: {}", error.toString());
        warned = true;
      }
      return new Resolution(Status.INVALID, null);
    }
  }

  private static Optional<Quote> unscaledQuote(ServerPlayer caster, ThidaSpell spell) {
    float heal = 0;
    if (spell == ThidaSpell.NAROTRA_RENEWAL) {
      ServerPlayer target = ThidaBonds.passiveTarget(caster, spell.doll());
      if (target == null) return Optional.empty();
      heal = (float) (target.getMaxHealth() * BASE_HEAL_FRACTION);
    }
    return Optional.of(new Quote(BASE_PERCENT, heal, BASE_BLEED_HP, () -> true));
  }

  public static Optional<Quote> quote(ServerPlayer player, String doll, int spell) {
    return ThidaSpell.find(doll, spell)
        .map(value -> resolve(player, value))
        .filter(value -> value.status() == Status.READY)
        .map(Resolution::quote);
  }

  public static Optional<Double> breakReduction(ServerPlayer p, ServerPlayer target) {
    try {
      Adapter selected = adapter;
      if (selected == null) return Optional.of(BASE_PERCENT);
      return selected.parasiteBreakReduction(p, target).filter(n -> Double.isFinite(n) && n >= 0);
    } catch (RuntimeException error) {
      return Optional.empty();
    }
  }

  private ThidaSkillIntegration() {}
}
