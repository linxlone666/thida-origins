package dev.thida;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CombatRulesTest {
  @Test
  void domainLeavesThirtyPercent() {
    assertEquals(6, CombatRules.domainDamage(20, 1 - 0.7), 0.0001);
  }

  @Test
  void bleedNeverKillsOrHealsSubOneHp() {
    assertEquals(9, CombatRules.nonlethalDamage(10, 10000), 0.0001);
    assertEquals(0, CombatRules.nonlethalDamage(1, 10000));
    assertEquals(0, CombatRules.nonlethalDamage(0.5f, 10000));
    assertEquals(2, CombatRules.nonlethalDamage(10, 2));
  }

  @Test
  void domainMultiplierCannotCreateNegativeDamage() {
    assertEquals(0, CombatRules.domainDamage(5, -1));
  }
}
