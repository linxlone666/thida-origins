package dev.thida;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ThidaConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue BOND_RANGE = B.comment("Initial targeting distance only. Existing links have no distance limit.").defineInRange("bondRange", 16, 2, 128);
    public static final ForgeConfigSpec.IntValue DOMAIN_RADIUS = B.defineInRange("domainRadius", 30, 1, 128);
    public static final ForgeConfigSpec.IntValue DOMAIN_DURATION = B.comment("Seconds. v0.2 key replaces domainDuration.").defineInRange("domainActiveSeconds", 300, 1, 3600);
    public static final ForgeConfigSpec.IntValue GAZE_TICKS = B.comment("Caster gaze required; target need not look back. 20 ticks = 1 second.").defineInRange("gazeTicks", 200, 20, 1200);
    public static final ForgeConfigSpec.IntValue EFFECT_SECONDS = B.defineInRange("dollEffectSeconds", 20, 1, 300);
    public static final ForgeConfigSpec.IntValue DOLL_EFFECT_COOLDOWN = B.defineInRange("dollEffectCooldown", 30, 1, 3600);
    public static final ForgeConfigSpec.IntValue RELEASE_COOLDOWN = B.defineInRange("releaseCooldown", 600, 1, 7200);
    public static final ForgeConfigSpec.IntValue DOMAIN_COOLDOWN = B.defineInRange("domainCooldown", 1200, 1, 7200);
    public static final ForgeConfigSpec.DoubleValue VULNERABILITY = B.comment("Provisional extra incoming damage fraction for Nilakan action 4.").defineInRange("vulnerability", 0.3, 0, 5);
    public static final ForgeConfigSpec.DoubleValue PARASITE_BREAK_HEALTH = B.comment("Break when caster HP after shared damage is strictly below this value.").defineInRange("parasiteCasterBreakHealth", 25.0, 1, 2048);
    public static final ForgeConfigSpec.DoubleValue PARASITE_BREAK_HEAL = B.defineInRange("parasiteBreakHeal", 50.0, 0, 2048);
    public static final ForgeConfigSpec.DoubleValue HEAL_AMOUNT = B.defineInRange("healAmount", 8.0, 0, 2048);
    public static final ForgeConfigSpec.DoubleValue SHARE_FRACTION = B.defineInRange("shareFraction", 0.5, 0.0, 1.0);
    public static final ForgeConfigSpec.DoubleValue DOMAIN_REDUCTION = B.defineInRange("domainReduction", 0.7, 0.0, 1.0);
    public static final ForgeConfigSpec SPEC = B.build();
    private ThidaConfig() { }
}
