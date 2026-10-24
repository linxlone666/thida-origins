package dev.thida;

import net.minecraft.client.Minecraft;

/** Shared bounds keep drawing and mouse hit boxes in the same coordinate space. */
final class ThidaHudLayout {
  static int available(double scale, int cap) {
    return HudPacking.available(
        Minecraft.getInstance().getWindow().getGuiScaledWidth(), scale, cap);
  }

  record Bounds(int x, int y, int width, int height, float scale) {
    boolean contains(double mx, double my) {
      return mx >= x && my >= y && mx < x + width && my < y + height;
    }
  }

  static Bounds fit(int x, int y, int width, int height, double requestedScale) {
    var window = Minecraft.getInstance().getWindow();
    int sw = window.getGuiScaledWidth(), sh = window.getGuiScaledHeight();
    float scale =
        (float)
            Math.min(
                requestedScale,
                Math.min(
                    (sw - 8) / (double) Math.max(1, width),
                    Math.max(1, sh - 82) / (double) Math.max(1, height)));
    int w = Math.max(1, (int) Math.ceil(width * scale));
    int h = Math.max(1, (int) Math.ceil(height * scale));
    x = Math.clamp(x, 4, Math.max(4, sw - w - 4));
    y = Math.clamp(y, 4, Math.max(4, sh - h - 4));
    // Vanilla hotbar, hearts and food occupy the central bottom strip.
    if (x < sw / 2 + 110 && x + w > sw / 2 - 110 && y + h > sh - 74) y = Math.max(4, sh - 74 - h);
    // Prefer a side lane; only reduce scale further when neither side can hold the panel.
    if (x < sw / 2 + 12 && x + w > sw / 2 - 12 && y < sh / 2 + 12 && y + h > sh / 2 - 12) {
      if (w > sw / 2 - 18) {
        scale = (float) Math.min(scale, Math.max(1, sw / 2 - 18) / (double) Math.max(1, width));
        w = Math.max(1, (int) Math.ceil(width * scale));
        h = Math.max(1, (int) Math.ceil(height * scale));
      }
      x = x < sw / 2 ? 4 : sw - w - 4;
    }
    return new Bounds(x, y, w, h, scale);
  }

  private ThidaHudLayout() {}
}
