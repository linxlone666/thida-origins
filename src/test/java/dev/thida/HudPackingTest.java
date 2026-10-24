package dev.thida;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class HudPackingTest {
  @Test
  void gridKeepsColumnsAlignedAndShrinksWithContent() {
    var grid = HudPacking.grid(List.of(80, 110, 100, 75), 248, 5, 4, 23, 2);
    assertEquals(grid.cells().get(0).x(), grid.cells().get(2).x());
    assertEquals(grid.cells().get(1).x(), grid.cells().get(3).x());
    assertEquals(grid.cells().get(0).width(), grid.cells().get(3).width());
    assertEquals(234, grid.width());
    assertEquals(60, grid.height());
    assertEquals(90, HudPacking.grid(List.of(80), 248, 5, 4, 23, 2).width());
    assertEquals(0, HudPacking.grid(List.of(), 248, 5, 4, 23, 2).height());
  }

  @Test
  void scaledWidthLeavesScreenMargin() {
    for (double scale : new double[] {.6, .9, 1, 1.4})
      for (int screen : new int[] {160, 320, 427, 854})
        assertTrue(HudPacking.available(screen, scale, 248) * scale <= screen - 12 + .001);
  }

  @Test
  void wrapsAndReleasesUnusedRows() {
    var many = HudPacking.flow(List.of(85, 85, 85), 190, 5, 4, 23, 6);
    assertEquals(60, many.height());
    assertEquals(184, many.width());
    assertEquals(32, many.cells().get(2).y());
    var one = HudPacking.flow(List.of(85), 190, 5, 4, 23, 6);
    assertEquals(95, one.width());
    assertEquals(33, one.height());
    assertEquals(0, HudPacking.flow(List.of(), 190, 5, 4, 23, 6).height());
  }

  @Test
  void oversizedChipAndColumnPreferenceStayInsidePanel() {
    var flow = HudPacking.flow(List.of(999, 30, 40), 150, 5, 4, 23, 1);
    assertEquals(150, flow.width());
    for (var c : flow.cells()) {
      assertTrue(c.x() >= 5);
      assertTrue(c.x() + c.width() <= flow.width() - 5);
      assertTrue(c.y() + 23 <= flow.height() - 5);
    }
    assertEquals(87, flow.height());
  }

  @Test
  void longContentIsCappedAndNarrowScreensWinOverMinimum() {
    assertEquals(224, HudPacking.limit(800, 158, 224));
    assertEquals(158, HudPacking.limit(80, 158, 224));
    assertEquals(100, HudPacking.limit(800, 158, 100));
  }
}
