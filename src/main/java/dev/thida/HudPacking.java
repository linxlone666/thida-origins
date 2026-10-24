package dev.thida;

import java.util.ArrayList;
import java.util.List;

/** GUI-unit geometry, independent of Minecraft and of gameplay state. */
final class HudPacking {
  record Cell(int x, int y, int width) {}

  record Flow(int width, int height, List<Cell> cells) {}

  static int limit(int wanted, int minimum, int maximum) {
    return Math.min(Math.max(1, maximum), Math.max(minimum, wanted));
  }

  static int available(int screenWidth, double scale, int cap) {
    return Math.max(1, Math.min(cap, (int) Math.floor((screenWidth - 12) / scale)));
  }

  static Flow flow(
      List<Integer> widths, int maximum, int padding, int gap, int rowHeight, int columns) {
    int inner = Math.max(1, maximum - padding * 2);
    var cells = new ArrayList<Cell>();
    int x = 0, y = 0, used = 0, count = 0;
    for (int wanted : widths) {
      int w = Math.min(inner, Math.max(1, wanted));
      if (count > 0 && (x + w > inner || count >= columns)) {
        x = 0;
        y += rowHeight + gap;
        count = 0;
      }
      cells.add(new Cell(padding + x, padding + y, w));
      used = Math.max(used, x + w);
      x += w + gap;
      count++;
    }
    return new Flow(
        widths.isEmpty() ? 0 : used + 2 * padding,
        widths.isEmpty() ? 0 : y + rowHeight + 2 * padding,
        List.copyOf(cells));
  }

  static Flow grid(
      List<Integer> widths, int maximum, int padding, int gap, int rowHeight, int columns) {
    if (widths.isEmpty()) return new Flow(0, 0, List.of());
    int cols = Math.max(1, Math.min(columns, widths.size()));
    int wanted = widths.stream().mapToInt(Integer::intValue).max().orElse(1);
    int cellWidth =
        Math.max(1, Math.min(wanted, (maximum - padding * 2 - gap * (cols - 1)) / cols));
    var cells = new ArrayList<Cell>();
    for (int i = 0; i < widths.size(); i++)
      cells.add(
          new Cell(
              padding + (i % cols) * (cellWidth + gap),
              padding + (i / cols) * (rowHeight + gap),
              cellWidth));
    int rows = (widths.size() + cols - 1) / cols;
    return new Flow(
        padding * 2 + cols * cellWidth + (cols - 1) * gap,
        padding * 2 + rows * rowHeight + (rows - 1) * gap,
        List.copyOf(cells));
  }

  private HudPacking() {}
}
