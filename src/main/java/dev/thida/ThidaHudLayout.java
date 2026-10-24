package dev.thida;

import net.minecraft.client.Minecraft;

/** Shared bounds keep drawing and mouse hit boxes in the same coordinate space. */
final class ThidaHudLayout {
  record Bounds(int x, int y, int width, int height, float scale) {
    boolean contains(double mx, double my) {
      return mx >= x && my >= y && mx < x + width && my < y + height;
    }
  }

  static Bounds fit(int x, int y, int width, int height, double requestedScale) {
    var window = Minecraft.getInstance().getWindow();
    int sw = window.getGuiScaledWidth(), sh = window.getGuiScaledHeight();
    float scale =
        (float) Math.min(requestedScale, Math.min(sw / (double) width, sh / (double) height));
    int w = Math.max(1, (int) Math.ceil(width * scale));
    int h = Math.max(1, (int) Math.ceil(height * scale));
    return new Bounds(
        Math.clamp(x, 0, Math.max(0, sw - w)), Math.clamp(y, 0, Math.max(0, sh - h)), w, h, scale);
  }

  private ThidaHudLayout() {}
}
