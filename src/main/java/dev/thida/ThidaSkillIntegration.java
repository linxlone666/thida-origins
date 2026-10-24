package dev.thida;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Explicit integration contract. No invented mana price, heal basis or magic scaling. */
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

  private static final Adapter PENDING =
      new Adapter() {
        public Optional<Quote> quote(ServerPlayer p, String doll, int spell) {
          return Optional.empty();
        }

        public Optional<Double> parasiteBreakReduction(ServerPlayer p, ServerPlayer target) {
          return Optional.empty();
        }
      };
  private static Adapter adapter = PENDING;

  public static void install(Adapter next) {
    adapter = next == null ? PENDING : next;
    warned = false;
  }

  private static boolean warned;

  public static Resolution resolve(ServerPlayer player, ThidaSpell spell) {
    if (adapter == PENDING) return new Resolution(Status.UNAVAILABLE, null);
    try {
      var result = adapter.quote(player, spell);
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

  public static Optional<Quote> quote(ServerPlayer player, String doll, int spell) {
    return ThidaSpell.find(doll, spell)
        .map(value -> resolve(player, value))
        .filter(value -> value.status() == Status.READY)
        .map(Resolution::quote);
  }

  public static Optional<Double> breakReduction(ServerPlayer p, ServerPlayer target) {
    try {
      return adapter.parasiteBreakReduction(p, target).filter(n -> Double.isFinite(n) && n >= 0);
    } catch (RuntimeException error) {
      return Optional.empty();
    }
  }

  private ThidaSkillIntegration() {}
}
