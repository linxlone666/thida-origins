package dev.thida;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ThidaHudConfig {
  private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
  public static final ModConfigSpec.BooleanValue VISIBLE = B.define("showHud", true);
  public static final ModConfigSpec.BooleanValue SOUNDS = B.define("cueSounds", true);
  public static final ModConfigSpec.IntValue ICONS_PER_ROW =
      B.defineInRange("iconsPerRow", 6, 1, 12);
  public static final ModConfigSpec.IntValue X = B.defineInRange("hudX", 8, 0, 4096);
  public static final ModConfigSpec.IntValue Y = B.defineInRange("hudY", 8, 0, 4096);
  public static final ModConfigSpec.DoubleValue VOLUME = B.defineInRange("cueVolume", 0.45, 0, 1);
  public static final ModConfigSpec.IntValue BOND_X = B.defineInRange("bondHudX", 8, 0, 4096);
  public static final ModConfigSpec.IntValue BOND_Y = B.defineInRange("bondHudY", 8, 0, 4096);
  public static final ModConfigSpec.IntValue RECIPIENT_Y =
      B.defineInRange("recipientHudOffsetY", -80, -4096, 0);
  public static final ModConfigSpec.IntValue RECIPIENT_X =
      B.defineInRange("recipientHudOffsetX", 0, -4096, 4096);
  public static final ModConfigSpec.DoubleValue BOND_SCALE =
      B.defineInRange("bondHudScale", 1.0, .6, 1.4);
  public static final ModConfigSpec.DoubleValue EFFECT_SCALE =
      B.defineInRange("effectHudScale", .9, .6, 1.4);
  public static final ModConfigSpec.DoubleValue RECIPIENT_SCALE =
      B.defineInRange("recipientHudScale", .9, .6, 1.4);
  public static final ModConfigSpec.BooleanValue INPUT_V7 =
      B.define("inputDefaultsV7Migrated", false);
  public static final ModConfigSpec SPEC = B.build();

  private ThidaHudConfig() {}
}
