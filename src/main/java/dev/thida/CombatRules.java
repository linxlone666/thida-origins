package dev.thida;

/** Pure, independently testable combat arithmetic. All health values are HP, not hearts. */
public final class CombatRules {
  private CombatRules() {}

  public static float sharedDamage(float damage, double fraction) {
    return (float) (Math.max(0, damage) * Math.max(0, Math.min(1, fraction)));
  }

  public static float domainDamage(float damage, double multiplier) {
    return (float) (Math.max(0, damage) * Math.max(0, Math.min(1, multiplier)));
  }

  public static float nonlethalDamage(float health, float damage) {
    return Math.min(Math.max(0, damage), Math.max(0, health - 1));
  }

  public static boolean canParasite(float health, double threshold) {
    return health > 0 && health < threshold;
  }

  public static boolean canStartParasite(
      float ownerHealth, float targetHealth, double threshold, double breakHealth) {
    return ownerHealth > 0 && targetHealth > 0;
  }

  public static boolean shouldBreak(float ownerHealth, float targetHealth, double threshold) {
    return ownerHealth < threshold;
  }
}
