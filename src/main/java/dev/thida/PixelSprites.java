package dev.thida;

import java.util.LinkedHashMap;
import java.util.Map;

/** Hand-authored 16-pixel icons. The same symbol is used in the HUD and spell menu. */
public final class PixelSprites {
  public static final Map<String, int[]> ALL = new LinkedHashMap<>();

  static {
    // Bound straw dolls, followed by the parasite, covenant, and fixed domain.
    sprite(
        "narotra",
        ".....####.....",
        "....#gggb#....",
        "....#wbgw#....",
        "....#g##g#....",
        ".....#gg#.....",
        "..###b##b###..",
        ".#gb#jpj#bg#..",
        "..###jjj###...",
        ".....#p#......",
        "....#jjj#.....",
        "....#b#b#.....",
        "...#gg#gg#....",
        "...###.###....");
    sprite(
        "nilakan",
        ".....####.....",
        "....#gggb#....",
        "....#wbgw#....",
        "....#g##g#....",
        ".....#gg#.....",
        "..###b##b###..",
        ".#gb#vpv#bg#..",
        "..###vvv###...",
        ".....#p#......",
        "....#vvv#.....",
        "....#b#b#.....",
        "...#gg#gg#....",
        "...###.###....");
    sprite(
        "parasite",
        "......##......",
        ".....#gp#.....",
        ".....#rg#.....",
        "...###p####...",
        "..#bg###gb#...",
        "...##rpr##....",
        "....#prp#.....",
        "...#r###r#....",
        "..#gb#.#bg#...",
        "..###...###...",
        "....p...p.....",
        ".....rrr......",
        "......r.......");
    sprite(
        "heart",
        "..............",
        "...###..###...",
        "..#ppp##rrr#..",
        ".#pwwpprrrrr#.",
        ".#pppgrrpppr#.",
        "..#ppg##ppr#..",
        "...#pgwwpr#...",
        "....#gwwr#....",
        ".....#pr#.....",
        "......##......",
        "...ggg..ggg...",
        "...g......g...");
    sprite(
        "domain",
        "......g.......",
        ".....g#g......",
        "....g#p#g.....",
        "...g#prp#g....",
        "..g##rpr##g...",
        ".g##pwrwp##g..",
        ".#pprwwwrpp#..",
        ".#rprpwprpr#..",
        ".#rpr###rpr#..",
        ".#rp#g#g#pr#..",
        "..###g#g###...",
        "..gbgggggbg...",
        "...#######....");
    sprite(
        "shield",
        "...########...",
        "..#gggggggg#..",
        "..#gpprrppg#..",
        "..#gprwwrpg#..",
        "..#gprwwrpg#..",
        "..#gprwwrpg#..",
        "...#prwwrp#...",
        "...#prwwrp#...",
        "....#rwwr#....",
        ".....#rr#.....",
        "......##......");
    sprite(
        "cleanse",
        ".......w......",
        "......wpw.....",
        ".......w......",
        "...g......g...",
        "....#....#....",
        ".....#p.#.....",
        "...###pp###...",
        "..#ggpwwpgg#..",
        "...##pwwp##...",
        ".....pwwp.....",
        "......pp......",
        "...g......g...");
    sprite(
        "heal_speed",
        "...............",
        "......##..##...",
        ".....#pp##rr#..",
        ".....#pwpprr#..",
        "..gg#ppppprp#..",
        ".....#ppprp#...",
        ".gggg#pprrp#...",
        "......#prp#....",
        "...gg..#r#.....",
        "........#......",
        "...............");
    sprite(
        "weak",
        ".........##...",
        "........#wg#..",
        ".......#wg#...",
        "......#wg#....",
        ".....#wg#.....",
        "....#wg#......",
        "..##g##...#...",
        "...##b#..#p#..",
        "...#b##..#p#..",
        "..#b#...#ppp#.",
        "..###....#p#..",
        "..........#...");
    sprite(
        "bleed",
        "......####....",
        "...###pppp##..",
        "..#pp#ww#pp#..",
        ".#pp#ww#pppp#.",
        "..##ww#ppp##..",
        "...##ppp##....",
        ".....####.....",
        "........##....",
        ".......#pr#...",
        "......#prrr#..",
        "......#rrrp#..",
        ".......####...");
    sprite(
        "vulnerable",
        "................",
        ".........######.",
        ".######..#ggwg#.",
        ".#gwgp#...#ppg#.",
        ".#gpp#..c#pppg#.",
        ".#gpp#.c..#ppp#.",
        ".#ppp#..c.#pp#..",
        "..#ppp#c.#pp#...",
        "..#pp#.cc.#p#...",
        "...#p#..c..##...",
        "....##..c..#....",
        "....#...........",
        "........c.......",
        ".......cc.......",
        ".......c........");
    sprite(
        "speed",
        "........##....",
        "......##gg#...",
        ".....#gwg#....",
        "....#gwg#.....",
        "...#gwg#......",
        "....#gww##....",
        "..gg.##wg#....",
        "......#g#.....",
        "...gg#g#......",
        ".....##.......");
    sprite(
        "ward",
        "......##......",
        "...###gg###...",
        "..#gppppppg#..",
        "..#gpwppwpg#..",
        "..#gppwwppg#..",
        "..#gpwwwwpg#..",
        "...#pwwwwp#...",
        "...#ppwwpp#...",
        "....#pppp#....",
        ".....#pp#.....",
        "......##......");
    sprite(
        "unlink",
        "...####.......",
        "..#gbbg#......",
        ".#gw##g#..#p..",
        ".#g#..#..#p...",
        "..#g#...#p....",
        "...#..#pp#....",
        ".....#p#..#...",
        "....#p#..#g#..",
        "...#p#..#gwg#.",
        "..p#...#gbbg#.",
        ".......#####..");
    sprite(
        "lock",
        "....######....",
        "...#gggggg#...",
        "...#g####g#...",
        "...#g#..#g#...",
        "..##########..",
        "..#gbggbbgg#..",
        "..#gb##bbgg#..",
        "..#gb#pbbgg#..",
        "..#gbggbbgg#..",
        "..##########..");
    sprite(
        "ruen_inside",
        "......g.......",
        ".....g#g......",
        "....g#p#g.....",
        "...g##r##g....",
        "..g##rpr##g...",
        ".g#pprprpp#g..",
        "..##r###r##...",
        "..#p#g#g#p#...",
        "..#p#g#g#p#...",
        "..###g#g###...",
        "..ggggggggg...");
    sprite(
        "ruen_outside",
        "...##.........",
        "..#pp#........",
        "..#rp#.....g..",
        "...##.....g#g.",
        "..#pp#...g#r#g",
        ".#prrp#.######",
        ".#prrp#.#r##r#",
        "..#rr#..#r##r#",
        "..#rr#..######",
        "..####........");
    sprite(
        "roster",
        "..########....",
        ".#gggggggg#...",
        "..#wwwwww#....",
        "..#wpwwwp#....",
        "..#wwwwww#....",
        "..#wrwrrw#....",
        "..#wwwwww#....",
        "..#wpwppw#....",
        "..#wwwwww#....",
        ".#gggggggg#...",
        "..########....");
  }

  private static void sprite(String key, String... rows) {
    int[] pixels = new int[16 * 16];
    int width = java.util.Arrays.stream(rows).mapToInt(String::length).max().orElse(0);
    int left = (16 - width) / 2, top = (16 - rows.length) / 2;
    for (int y = 0; y < rows.length; y++) {
      for (int x = 0; x < rows[y].length(); x++) {
        pixels[(y + top) * 16 + x + left] =
            switch (rows[y].charAt(x)) {
              case '#' -> 0xFF2B1825;
              case 'r' -> 0xFFA22E58;
              case 'c' -> 0xFFA71934;
              case 'p' -> 0xFFF087AC;
              case 'g' -> 0xFFE7AE65;
              case 'b' -> 0xFF966240;
              case 'w' -> 0xFFFFEACB;
              case 'v' -> 0xFF8F629F;
              case 'j' -> 0xFF51A58F;
              default -> 0;
            };
      }
    }
    ALL.put(key, pixels);
  }

  private PixelSprites() {}
}
