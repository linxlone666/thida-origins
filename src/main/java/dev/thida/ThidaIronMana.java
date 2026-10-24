package dev.thida;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;

/** Loaded only when Iron's is present; uses its published 1.21.1 API and mana-change event. */
final class ThidaIronMana {
  private ThidaIronMana() {}

  static double maximum(ServerPlayer player) {
    var attribute =
        net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE
            .getHolder(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "irons_spellbooks", "max_mana"))
            .orElse(null);
    return attribute == null || player.getAttribute(attribute) == null
        ? 0
        : player.getAttributeValue(attribute);
  }

  static boolean restore(ServerPlayer player, float amount, float maximum) {
    MagicData magic = MagicData.getPlayerMagicData(player);
    magic.setMana(Math.min(maximum, magic.getMana() + amount));
    return true;
  }
}
