package dev.thida;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ThidaDamageMathTest {
  @Test
  void lowHealthAverageCapsEachMaximumIndependently() {
    assertEquals(
        new ThidaDamageMath.Health(60, 20), ThidaDamageMath.initialHealth(100, 100, 20, 20));
    assertEquals(
        new ThidaDamageMath.Health(20, 60), ThidaDamageMath.initialHealth(20, 20, 100, 100));
  }

  @Test
  void overflowIsNotTransferredOrConvertedToAbsorption() {
    assertEquals(
        new ThidaDamageMath.Health(55, 20), ThidaDamageMath.initialHealth(100, 100, 10, 20));
  }

  @Test
  void thresholdIsStrictlyBelow25() {
    assertEquals(
        new ThidaDamageMath.Health(100, 25), ThidaDamageMath.initialHealth(100, 100, 25, 100));
    assertEquals(
        new ThidaDamageMath.Health(62, 62), ThidaDamageMath.initialHealth(100, 100, 24, 100));
  }

  @Test
  void higherHealthAbsorbsDifferenceBeforeSplitting() {
    assertEquals(new ThidaDamageMath.Split(20, 0), ThidaDamageMath.equalize(100, 50, 20));
    assertEquals(new ThidaDamageMath.Split(45, 15), ThidaDamageMath.equalize(80, 50, 60));
  }

  @Test
  void overkillDamageIsNotClipped() {
    assertEquals(new ThidaDamageMath.Split(125, 75), ThidaDamageMath.equalize(100, 50, 200));
  }
}
