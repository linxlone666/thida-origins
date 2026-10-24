package dev.thida;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ThidaHudConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.BooleanValue VISIBLE = B.define("showHud", true);
    public static final ForgeConfigSpec.BooleanValue SOUNDS = B.define("cueSounds", true);
    public static final ForgeConfigSpec.IntValue ICONS_PER_ROW = B.defineInRange("iconsPerRow", 6, 1, 12);
    public static final ForgeConfigSpec.IntValue X = B.defineInRange("hudX", 8, 0, 4096);
    public static final ForgeConfigSpec.IntValue Y = B.defineInRange("hudY", 8, 0, 4096);
    public static final ForgeConfigSpec.DoubleValue VOLUME = B.defineInRange("cueVolume", 0.45, 0, 1);
    public static final ForgeConfigSpec SPEC = B.build();
    private ThidaHudConfig() {}
}
