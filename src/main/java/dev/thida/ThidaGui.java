package dev.thida;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Pixel frames and shared icon language for both the skill menu and the compact HUD. */
public final class ThidaGui {
  public static final int TEXT = 0xFFF5E8D2,
      MUTED = 0xFFC0AEC5,
      GOLD = 0xFFE2BC79,
      BASE = 0xEE211D2D;

  public static void frame(GuiGraphics g, int x, int y, int w, int h, int accent, boolean focus) {
    g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x80000000);
    g.fill(x + 1, y, x + w - 1, y + h, BASE);
    g.fill(x, y + 1, x + w, y + h - 1, BASE);
    g.fill(x + 2, y, x + w - 2, y + 1, focus ? accent : 0xFF67516F);
    g.fill(x, y + 2, x + 1, y + h - 2, focus ? accent : 0xFF67516F);
    g.fill(x + 2, y + h - 1, x + w - 2, y + h, 0xFF382C44);
    g.fill(x + w - 1, y + 2, x + w, y + h - 2, 0xFF382C44);
    g.fill(x + 1, y + 1, x + 3, y + 3, accent);
    g.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, accent);
  }

  public static void icon(GuiGraphics g, String key, int x, int y, int scale) {
    int[] pixels = PixelSprites.ALL.get(key);
    if (pixels == null) return;
    for (int j = 0; j < 16; j++)
      for (int i = 0; i < 16; i++) {
        int color = pixels[j * 16 + i];
        if (color != 0)
          g.fill(x + i * scale, y + j * scale, x + (i + 1) * scale, y + (j + 1) * scale, color);
      }
  }

  public static void bar(GuiGraphics g, int x, int y, int w, float progress, int color) {
    g.fill(x, y, x + w, y + 2, 0xFF110F1A);
    int fill = Math.round(w * Math.max(0, Math.min(1, progress)));
    if (fill > 0) g.fill(x, y, x + fill, y + 2, color);
  }

  public static void text(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
    var font = Minecraft.getInstance().font;
    if (font.width(text) > maxWidth)
      text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    g.drawString(font, text, x, y, color, false);
  }

  public static long remaining(net.minecraft.nbt.CompoundTag data, String key) {
    return ThidaHudState.remaining(data.getCompound("cooldowns").getLong(key));
  }

  public static float progress(long remaining, int total) {
    return 1 - Math.min(1, remaining / (float) Math.max(1, total));
  }

  private ThidaGui() {}
}
