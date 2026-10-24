package dev.thida;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ThidaConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue BOND_RANGE = B.comment("Maximum active bond and targeting distance in blocks.").defineInRange("bondRange", 16, 2, 128);
    public static final ForgeConfigSpec.IntValue MARK_RANGE = B.comment("Maximum range for ordinary straw-doll marks; separate from heart/parasite bonds.").defineInRange("markRange", 64, 2, 256);
    public static final ForgeConfigSpec.IntValue DOMAIN_RADIUS = B.defineInRange("domainRadius", 30, 1, 128);
    public static final ForgeConfigSpec.IntValue DOMAIN_DURATION = B.comment("Seconds.").defineInRange("domainDuration", 15, 1, 300);
    public static final ForgeConfigSpec.IntValue GAZE_TICKS = B.comment("Continuous mutual gaze required; 20 ticks = 1 second.").defineInRange("gazeTicks", 200, 20, 1200);
    public static final ForgeConfigSpec.IntValue EFFECT_SECONDS = B.defineInRange("effectSeconds", 10, 1, 300);
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS = B.defineInRange("cooldownSeconds", 20, 1, 600);
    public static final ForgeConfigSpec.DoubleValue PARASITE_THRESHOLD = B.comment("Caster must have less than this many HP to form parasite bond.").defineInRange("parasiteThreshold", 150.0, 1, 2048);
    public static final ForgeConfigSpec.DoubleValue PARASITE_BREAK_HEALTH = B.comment("Combined remaining HP threshold. Does not force equal health.").defineInRange("parasiteBreakHealth", 50.0, 1, 2048);
    public static final ForgeConfigSpec.DoubleValue PARASITE_BREAK_HEAL = B.defineInRange("parasiteBreakHeal", 50.0, 0, 2048);
    public static final ForgeConfigSpec.DoubleValue HEAL_AMOUNT = B.defineInRange("healAmount", 8.0, 0, 2048);
    public static final ForgeConfigSpec.DoubleValue SHARE_FRACTION = B.defineInRange("shareFraction", 0.5, 0.0, 1.0);
    public static final ForgeConfigSpec.DoubleValue DOMAIN_REDUCTION = B.defineInRange("domainReduction", 0.7, 0.0, 1.0);
    public static final ForgeConfigSpec SPEC = B.build();
    private ThidaConfig() { }
}
