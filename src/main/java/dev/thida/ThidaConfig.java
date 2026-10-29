package dev.thida;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ThidaConfig {
  private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
  public static final ModConfigSpec.IntValue BOND_RANGE =
      B.comment("Legacy compatibility key. Current passive targeting uses its own fixed ranges.")
          .defineInRange("bondRange", 16, 2, 128);
  public static final ModConfigSpec.IntValue DOMAIN_RADIUS =
      B.defineInRange("domainRadius", 30, 1, 128);
  public static final ModConfigSpec.IntValue DOMAIN_DURATION =
      B.comment("Seconds. Legacy config key retained for existing installations.")
          .defineInRange("domainActiveSeconds", 300, 1, 3600);
  public static final ModConfigSpec.IntValue GAZE_TICKS =
      B.comment("Legacy compatibility key. Current Narotra gaze requires 200 ticks.")
          .defineInRange("gazeTicks", 200, 20, 1200);
  public static final ModConfigSpec.IntValue EFFECT_SECONDS =
      B.defineInRange("dollEffectSeconds", 20, 1, 300);
  public static final ModConfigSpec.IntValue DOLL_EFFECT_COOLDOWN =
      B.comment("Legacy compatibility key. Current spell cooldowns are handled by ThidaSpells.")
          .defineInRange("dollEffectCooldown", 30, 1, 3600);
  public static final ModConfigSpec.IntValue RELEASE_COOLDOWN =
      B.defineInRange("releaseCooldown", 600, 1, 7200);
  public static final ModConfigSpec.IntValue DOMAIN_COOLDOWN =
      B.defineInRange("domainCooldown", 1200, 1, 7200);
  public static final ModConfigSpec.DoubleValue VULNERABILITY =
      B.comment("Provisional extra incoming damage fraction for Nilakan action 4.")
          .defineInRange("vulnerability", 0.3, 0, 5);
  public static final ModConfigSpec.DoubleValue PARASITE_BREAK_HEALTH =
      B.comment("Legacy compatibility key. Current parasite break rules use combined health.")
          .defineInRange("parasiteCasterBreakHealth", 25.0, 1, 2048);
  public static final ModConfigSpec.DoubleValue PARASITE_BREAK_HEAL =
      B.defineInRange("parasiteBreakHeal", 50.0, 0, 2048);
  public static final ModConfigSpec.DoubleValue SHARE_FRACTION =
      B.comment("Legacy compatibility key. Current sharing uses health equalization.")
          .defineInRange("shareFraction", 0.5, 0.0, 1.0);
  public static final ModConfigSpec.DoubleValue DOMAIN_REDUCTION =
      B.defineInRange("domainReduction", 0.7, 0.0, 1.0);
  public static final ModConfigSpec SPEC = B.build();

  private ThidaConfig() {}
}
