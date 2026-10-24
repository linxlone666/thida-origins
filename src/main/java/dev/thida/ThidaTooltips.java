package dev.thida;

import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Short, styled rows instead of a single paragraph; safe on both physical sides. */
public final class ThidaTooltips {
  public static final String MORE = "tooltip.thida.more";
  private static final Map<String, Integer> USE_ROWS =
      Map.of(
          "narotra_doll",
          2,
          "nilakan_doll",
          2,
          "parasite_doll",
          2,
          "heart_doll",
          2,
          "domain_talisman",
          1);
  private static final Map<String, Integer> EFFECT_ROWS =
      Map.of("narotra_doll", 3, "nilakan_doll", 2, "domain_talisman", 3);

  private ThidaTooltips() {}

  public static void append(String item, List<Component> lines) {
    lines.add(
        Component.translatable("item.thida.origin_required").withStyle(ChatFormatting.DARK_PURPLE));
    section(lines, "use", ChatFormatting.AQUA);
    bullets(lines, item, "use", USE_ROWS.getOrDefault(item, 0));
    if (EFFECT_ROWS.containsKey(item)) {
      section(lines, "effect", ChatFormatting.GREEN);
      bullets(lines, item, "effect", EFFECT_ROWS.get(item));
    }
    lines.add(Component.empty());
    if (passive(item)) {
      lines.add(
          Component.translatable(
                  "tooltip.thida.unlink", Component.keybind("key.thida.open_doll_menu"))
              .withStyle(ChatFormatting.GOLD));
      lines.add(
          Component.translatable(MORE).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    } else {
      lines.add(
          Component.translatable("tooltip.thida." + item + ".control")
              .withStyle(ChatFormatting.GOLD));
    }
  }

  public static boolean passive(String item) {
    return item.equals("narotra_doll") || item.equals("nilakan_doll");
  }

  public static void appendDetails(String item, List<Component> lines) {
    if (!passive(item)) return;
    section(lines, "rules", ChatFormatting.YELLOW);
    bullets(lines, "passive", "rule", 3);
    bullets(lines, item, "rule", 1);
    section(lines, "obtain", ChatFormatting.AQUA);
    bullets(lines, "passive", "obtain", 2);
  }

  private static void section(List<Component> lines, String key, ChatFormatting color) {
    lines.add(Component.empty());
    lines.add(
        Component.translatable("tooltip.thida.section." + key)
            .withStyle(color, ChatFormatting.BOLD));
  }

  private static void bullets(List<Component> lines, String item, String part, int count) {
    for (int i = 1; i <= count; i++)
      lines.add(
          Component.literal(" • ")
              .append(Component.translatable("tooltip.thida." + item + "." + part + "." + i))
              .withStyle(ChatFormatting.GRAY));
  }
}
