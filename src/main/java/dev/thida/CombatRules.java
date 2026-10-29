package dev.thida;

/** Pure, independently testable combat arithmetic. All health values are HP, not hearts. */
public final class CombatRules {
  private CombatRules() {}

  public static float domainDamage(float damage, double multiplier) {
    return (float) (Math.max(0, damage) * Math.max(0, Math.min(1, multiplier)));
  }

  public static float nonlethalDamage(float health, float damage) {
    return Math.min(Math.max(0, damage), Math.max(0, health - 1));
  }
}
