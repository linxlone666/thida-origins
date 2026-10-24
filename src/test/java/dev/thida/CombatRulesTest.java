package dev.thida;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatRulesTest {
    @Test void sharingConservesFinalDamageAcrossHealthScales() {
        for (float damage : new float[]{0, 0.5f, 1, 9, 50, 150, 1000}) {
            float shared = CombatRules.sharedDamage(damage, 0.5);
            assertEquals(damage, (damage - shared) + shared, 0.0001);
            assertEquals(damage / 2, shared, 0.0001);
        }
    }
    @Test void domainLeavesThirtyPercent() {
        assertEquals(6, CombatRules.domainDamage(20, 1 - 0.7), 0.0001);
    }
    @Test void covenantSurvivesOverkillAndDoesNotHealSubOneHp() {
        assertEquals(9, CombatRules.nonlethalDamage(10, 10000), 0.0001);
        assertEquals(0, CombatRules.nonlethalDamage(1, 10000));
        assertEquals(0, CombatRules.nonlethalDamage(0.5f, 10000));
        assertEquals(2, CombatRules.nonlethalDamage(10, 2));
    }
    @Test void parasiteBoundaryUsesStrict150HpAndInclusive50Hp() {
        assertFalse(CombatRules.canParasite(150, 150));
        assertTrue(CombatRules.canParasite(149.5f, 150));
        assertFalse(CombatRules.canParasite(0, 150));
        assertTrue(CombatRules.shouldBreak(25, 25, 50));
        assertFalse(CombatRules.shouldBreak(25.5f, 25, 50));
    }
    @Test void malformedFractionsCannotCreateNegativeDamage() {
        assertEquals(0, CombatRules.sharedDamage(-5, .5));
        assertEquals(5, CombatRules.sharedDamage(5, 2));
        assertEquals(0, CombatRules.domainDamage(5, -1));
    }
    @Test void cannotFarmBreakHealingWithVanillaHealthPair() {
        assertFalse(CombatRules.canStartParasite(20, 20, 150, 50));
        assertFalse(CombatRules.canStartParasite(25, 25, 150, 50));
        assertTrue(CombatRules.canStartParasite(100, 100, 150, 50));
        assertFalse(CombatRules.canStartParasite(150, 100, 150, 50));
    }
}
