package dev.thida;

import java.util.function.ToDoubleFunction;
import net.minecraft.server.level.ServerPlayer;

/** Optional stat bridge. No magic mod or invented magic stat is required by this branch. */
public final class ThidaMayaromScaling {
  private static ToDoubleFunction<ServerPlayer> magicPower;

  private ThidaMayaromScaling() {}

  /** Install a read-only provider; null restores the standalone base melee damage. */
  public static void install(ToDoubleFunction<ServerPlayer> provider) {
    magicPower = provider;
  }

  /** Output = existing melee damage + (magic power * 2). */
  public static double meleeDamage(ServerPlayer caster, double baseDamage) {
    var provider = magicPower;
    if (provider == null) return baseDamage;
    try {
      double magic = provider.applyAsDouble(caster);
      double result = baseDamage + magic * 2;
      return Double.isFinite(magic)
              && magic >= 0
              && Double.isFinite(result)
              && result <= Float.MAX_VALUE
          ? result
          : baseDamage;
    } catch (RuntimeException ignored) {
      return baseDamage;
    }
  }
}
