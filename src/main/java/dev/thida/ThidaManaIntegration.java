package dev.thida;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-only mana contract. A personal-mod adapter takes priority over optional Iron
 * compatibility.
 */
public final class ThidaManaIntegration {
  public interface Adapter {
    double maxMana(ServerPlayer player);

    /**
     * Restore the supplied amount, capped at maximum; return true when the operation is supported.
     */
    boolean restore(ServerPlayer player, float amount, float maximum);
  }

  private static Adapter personal;
  private static boolean warned;

  private ThidaManaIntegration() {}

  public static void install(Adapter adapter) {
    personal = adapter;
    warned = false;
  }

  public static boolean restoreFivePercent(ServerPlayer player) {
    try {
      return restore(player);
    } catch (RuntimeException error) {
      if (!warned) {
        com.mojang.logging.LogUtils.getLogger()
            .warn("Thida mana provider failed: {}", error.toString());
        warned = true;
      }
      return false;
    }
  }

  private static boolean restore(ServerPlayer player) {
    Adapter selected = personal;
    double maximum;
    if (selected != null) maximum = selected.maxMana(player);
    else {
      if (!net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) return false;
      maximum = ThidaIronMana.maximum(player);
    }
    if (!Double.isFinite(maximum) || maximum <= 0 || maximum > Float.MAX_VALUE) return false;
    float amount = ThidaPassives.manaRestoreAmount(maximum);
    return selected != null
        ? selected.restore(player, amount, (float) maximum)
        : ThidaIronMana.restore(player, amount, (float) maximum);
  }
}
