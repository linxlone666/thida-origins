package dev.thida;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CombatRulesTest {
  @Test
  void sharingConservesFinalDamageAcrossHealthScales() {
    for (float damage : new float[] {0, 0.5f, 1, 9, 50, 150, 1000}) {
      float shared = CombatRules.sharedDamage(damage, 0.5);
      assertEquals(damage, (damage - shared) + shared, 0.0001);
      assertEquals(damage / 2, shared, 0.0001);
    }
  }

  @Test
  void domainLeavesThirtyPercent() {
    assertEquals(6, CombatRules.domainDamage(20, 1 - 0.7), 0.0001);
  }

  @Test
  void covenantSurvivesOverkillAndDoesNotHealSubOneHp() {
    assertEquals(9, CombatRules.nonlethalDamage(10, 10000), 0.0001);
    assertEquals(0, CombatRules.nonlethalDamage(1, 10000));
    assertEquals(0, CombatRules.nonlethalDamage(0.5f, 10000));
    assertEquals(2, CombatRules.nonlethalDamage(10, 2));
  }

  @Test
  void parasiteBreakUsesOnlyCasterHealthStrictlyBelow25() {
    assertFalse(CombatRules.shouldBreak(25, 1, 25));
    assertTrue(CombatRules.shouldBreak(24.5f, 100, 25));
    assertFalse(CombatRules.shouldBreak(26, 1, 25));
  }

  @Test
  void malformedFractionsCannotCreateNegativeDamage() {
    assertEquals(0, CombatRules.sharedDamage(-5, .5));
    assertEquals(5, CombatRules.sharedDamage(5, 2));
    assertEquals(0, CombatRules.domainDamage(5, -1));
  }

  @Test
  void parasiteHasNoOld150HpOrCombinedHealthGate() {
    assertTrue(CombatRules.canStartParasite(20, 20, 150, 25));
    assertTrue(CombatRules.canStartParasite(25, 25, 150, 25));
    assertTrue(CombatRules.canStartParasite(100, 100, 150, 50));
    assertTrue(CombatRules.canStartParasite(150, 100, 150, 25));
    assertFalse(CombatRules.canStartParasite(0, 100, 150, 25));
  }
}
