package dev.thida;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PassiveRulesTest {
  @Test
  void narotraUsesFivePercentOfMaximumMana() {
    assertEquals(5.0f, ThidaPassives.manaRestoreAmount(100));
    assertEquals(12.5f, ThidaPassives.manaRestoreAmount(250));
    assertEquals(0.0f, ThidaPassives.manaRestoreAmount(0));
    assertEquals(0.0f, ThidaPassives.manaRestoreAmount(-20));
  }

  @Test
  void gazeAndBondRangesIncludeTheirBoundary() {
    assertTrue(ThidaPassives.withinRange(15 * 15, 15));
    assertFalse(ThidaPassives.withinRange(15.01 * 15.01, 15));
    assertTrue(ThidaPassives.withinRange(10 * 10, 10));
    assertFalse(ThidaPassives.withinRange(10.01 * 10.01, 10));
    assertTrue(ThidaPassives.withinRange(200 * 200, 200));
    assertFalse(ThidaPassives.withinRange(200.01 * 200.01, 200));
  }

  @Test
  void cleanseWaitsForBothItsDeadlineAndAnActualDebuff() {
    assertFalse(ThidaPassives.cleanseReady(100, 101, true));
    assertFalse(ThidaPassives.cleanseReady(101, 101, false));
    assertTrue(ThidaPassives.cleanseReady(101, 101, true));
    assertTrue(ThidaPassives.cleanseReady(1000, 101, true));
  }
}
