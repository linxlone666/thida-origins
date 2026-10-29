package dev.thida;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("thida")
@PrefixGameTestTemplate(false)
public final class ThidaMayaromGameTests {
  @GameTest(template = "empty")
  public static void permissionImmunityAndMagicBridge(GameTestHelper h) {
    try (var f = new ThidaGameTests.Fixture(h)) {
      var owner = f.player(true);
      var victim = f.player(false);
      ThidaMayarom.toggle(owner);
      h.assertTrue(!ThidaMayarom.active(owner), "Locked ultimate cannot start");
      ThidaBonds.data(owner).putBoolean("unlock4", true);
      owner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
      ThidaMayarom.toggle(owner);
      h.assertTrue(!owner.hasEffect(MobEffects.POISON), "Activation cleanses");
      owner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
      h.assertTrue(!owner.hasEffect(MobEffects.POISON), "Ultimate blocks new harmful effects");
      h.assertTrue(ThidaMayaromScaling.meleeDamage(owner, 4) == 4, "No fabricated magic stat");
      ThidaMayaromScaling.install(p -> 3);
      h.assertTrue(ThidaMayaromScaling.meleeDamage(owner, 4) == 10, "Base plus magic times two");
      h.assertTrue(victim.hurt(victim.damageSources().playerAttack(owner), 4), "Real melee hit");
      h.assertTrue(Math.abs(victim.getHealth() - 90) < .01, "Magic bridge participates in combat");
      h.assertTrue(
          victim.hasEffect(ThidaMod.MAYAROM_WEAKNESS) && victim.hasEffect(MobEffects.BLINDNESS),
          "Melee curse applied");
      h.assertTrue(
          !victim.hasEffect(ThidaMod.MAYAROM_BLEED), "Owner melee is not Narotra's full curse");
      ThidaMayaromScaling.install(p -> Double.NaN);
      h.assertTrue(ThidaMayaromScaling.meleeDamage(owner, 4) == 4, "Invalid provider is safe");
      ThidaMayaromScaling.install(
          p -> {
            throw new IllegalStateException("test");
          });
      h.assertTrue(ThidaMayaromScaling.meleeDamage(owner, 4) == 4, "Failed provider is safe");
      ThidaMayarom.toggle(owner);
      owner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
      h.assertTrue(owner.hasEffect(MobEffects.POISON), "Immunity ends with ultimate");
    } finally {
      ThidaMayaromScaling.install(null);
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void expiryOfflineAndBleedRemainNonlethal(GameTestHelper h) {
    try (var f = new ThidaGameTests.Fixture(h)) {
      var owner = f.player(true);
      var victim = f.player(false);
      var data = ThidaBonds.data(owner);
      long now = ThidaBonds.now(owner.server);
      // SavedData is authoritative even when no owner entity can currently be resolved.
      var offlineId = java.util.UUID.randomUUID();
      var offline = ThidaBondData.get(owner.server).player(offlineId);
      offline.putLong("mayaromUntil", now - 100);
      ThidaMayarom.tick(owner.server);
      h.assertTrue(
          offline.getLong("mayaromCooldown") == now - 100 + ThidaMayarom.COOLDOWN,
          "Offline expiry starts cooldown at actual expiry, not next login");
      data.putBoolean("unlock4", true);
      ThidaMayarom.toggle(owner);
      var effects = ThidaEffectProjection.read(owner.server, java.util.UUID.randomUUID(), now);
      h.assertTrue(
          effects.values().stream()
              .anyMatch(e -> e.power().equals("mayarom") && e.component().equals("ultimate")),
          "AAA projection includes active ultimate");
      data.putLong("mayaromUntil", now);
      ThidaMayarom.tick(owner.server);
      h.assertTrue(
          !ThidaMayarom.active(owner) && ThidaMayarom.cooldown(owner) == 24000,
          "Expiry begins full cooldown");
      victim.setHealth(2);
      victim.addEffect(new MobEffectInstance(ThidaMod.MAYAROM_BLEED, 100));
      ThidaBonds.data(victim).putLong("mayaromBleedUntil", now + 100);
      ThidaBonds.data(victim).putLong("mayaromNextBleed", now);
      ThidaMayarom.tick(owner.server);
      h.assertTrue(victim.getHealth() == 1, "Bleed never kills");
      ThidaEvents.cleanse(victim);
      ThidaBonds.data(victim).putLong("mayaromNextBleed", now);
      victim.setHealth(10);
      ThidaMayarom.tick(owner.server);
      h.assertTrue(victim.getHealth() == 10, "Cleansing stops subsequent bleed ticks");
    }
    h.succeed();
  }
}
