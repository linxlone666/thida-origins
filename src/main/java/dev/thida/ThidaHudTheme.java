package dev.thida;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Parchment, dark wood and rose ink shared by the HUD and the skill menus. */
final class ThidaHudTheme {
  static final int INK = 0xFF2D1B21;
  static final int SECONDARY = 0xFF583B36;
  static final int ACTIVE = 0xFF891B4F;
  static final int RESTING = 0xFF6D492A;
  static final int SELECTION = 0xFFF4A7BF;
  static final int ICON_VEIL = 0x6662493A;
  static final int ON_DARK = 0xFFFFE8C3;
  private static final int WOOD = 0xFF362020;
  private static final int WOOD_LIGHT = 0xFF74503A;
  private static final int PAPER = 0xFFF1D8AD;
  private static final int PAPER_LIGHT = 0xFFFFE8C3;
  private static final int PAPER_EDGE = 0xFFE5C390;

  private ThidaHudTheme() {}

  static void panel(GuiGraphics g, int x, int y, int width, int height, int accent) {
    if (width < 6 || height < 6) return;
    int right = x + width, bottom = y + height;
    g.fill(x, y, right, bottom, WOOD);
    g.fill(x + 1, y + 1, right - 1, bottom - 1, WOOD_LIGHT);
    g.fill(x + 2, y + 2, right - 2, bottom - 2, PAPER);
    g.fill(x + 2, y + 2, right - 2, y + 3, PAPER_LIGHT);
    g.fill(x + 2, bottom - 3, right - 2, bottom - 2, PAPER_EDGE);
    // Small ink marks keep the corners legible without adding texture behind the text.
    g.fill(x + 2, y + 2, x + 5, y + 3, accent);
    g.fill(x + 2, y + 3, x + 3, y + 5, accent);
    g.fill(right - 5, bottom - 3, right - 2, bottom - 2, accent);
    g.fill(right - 3, bottom - 5, right - 2, bottom - 3, accent);
  }

  static void rule(GuiGraphics g, int x, int y, int width) {
    if (width < 5) return;
    g.fill(x, y, x + width, y + 1, PAPER_EDGE);
    int center = x + width / 2;
    g.fill(center - 1, y, center + 2, y + 1, ACTIVE);
  }

  static void popup(GuiGraphics g, int x, int y, int width, int height, int header) {
    g.fill(x + 3, y + 3, x + width + 3, y + height + 3, 0x66000000);
    panel(g, x, y, width, height, ACTIVE);
    g.fill(x + 3, y + 3, x + width - 3, y + header, WOOD);
    g.fill(x + 3, y + header, x + width - 3, y + header + 1, 0xFFAD7753);
    g.fill(x + 5, y + 5, x + 7, y + 7, SELECTION);
    g.fill(x + width - 7, y + 5, x + width - 5, y + 7, SELECTION);
  }

  static void menuTile(
      GuiGraphics g,
      int x,
      int y,
      int width,
      int height,
      boolean marked,
      boolean hover,
      boolean muted) {
    if (width < 4 || height < 4) return;
    int edge = marked ? ACTIVE : hover ? WOOD_LIGHT : PAPER_EDGE;
    int fill = marked ? 0xFFF3C2BE : muted ? 0xFFEBD3AF : hover ? PAPER_LIGHT : 0xFFF5DFB9;
    g.fill(x, y, x + width, y + height, edge);
    g.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    if (marked) g.fill(x + 1, y + 2, x + 3, y + height - 2, ACTIVE);
  }

  static void bar(GuiGraphics g, int x, int y, int width, float progress, int ink) {
    if (width <= 0) return;
    g.fill(x, y, x + width, y + 2, 0xFFBD9774);
    int fill = Math.round(width * Math.clamp(progress, 0f, 1f));
    if (fill > 0) g.fill(x, y, x + fill, y + 2, ink);
  }

  /** Floating statuses keep their transparent background above the hotbar. */
  static void floatingText(GuiGraphics g, String text, int x, int y, int width, boolean timer) {
    if (text == null || text.isBlank() || width < ThidaGui.width("…")) return;
    var font = Minecraft.getInstance().font;
    if (ThidaGui.width(text) > width)
      text =
          font.getSplitter()
                  .plainHeadByWidth(
                      text,
                      width - ThidaGui.width("…"),
                      ThidaGui.styled(Component.empty()).getStyle())
              + "…";
    g.drawString(font, ThidaGui.styled(Component.literal(text)), x, y + 1, WOOD, false);
    g.drawString(font, ThidaGui.styled(Component.literal(text)), x + 1, y, WOOD, false);
    g.drawString(
        font,
        ThidaGui.styled(Component.literal(text)),
        x,
        y,
        timer ? 0xFFFFC3D7 : 0xFFFFF1DB,
        false);
  }
}
