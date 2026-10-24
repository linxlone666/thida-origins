package dev.thida;

import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real hurt/heal calls exercise mixins and the NeoForge damage pipeline, not just arithmetic. */
@GameTestHolder("thida")
@PrefixGameTestTemplate(false)
public final class ThidaV7GameTests {
  private static void run(GameTestHelper h, BiConsumer<ServerPlayer, ServerPlayer> body) {
    try (var f = new ThidaGameTests.Fixture(h)) {
      var owner = f.player(true);
      var target = f.player(false);
      target.setPos(owner.getX(), owner.getY(), owner.getZ() + 4);
      body.accept(owner, target);
      ThidaBonds.endHeart(owner.server, owner.getUUID(), "TEST_CLEANUP");
      ThidaBonds.breakParasite(owner.server, owner.getUUID(), "TEST_CLEANUP", false);
      for (String kind : new String[] {"narotra_doll", "nilakan_doll"})
        ThidaPassives.unlink(owner, kind, "TEST_CLEANUP");
    } finally {
      ThidaSkillIntegration.install(null);
      ThidaManaIntegration.install(null);
    }
    h.succeed();
  }

  private static void near(GameTestHelper h, float actual, float expected) {
    h.assertTrue(Math.abs(actual - expected) < .01, "Expected " + expected + ", got " + actual);
  }

  private static void parasite(ServerPlayer owner, ServerPlayer target) {
    ThidaBonds.data(owner).putBoolean("unlock2", true);
    ThidaBonds.summon(owner);
    var pending = new CompoundTag();
    pending.putUUID("target", target.getUUID());
    ThidaBonds.data(owner).put("pendingParasite", pending);
    var request = new CompoundTag();
    request.putString("action", "confirm");
    request.putString("kind", "parasite");
    request.putUUID("target", target.getUUID());
    request.putBoolean("accept", true);
    ThidaBonds.request(owner, request);
  }

  private static void heart(ServerPlayer owner, ServerPlayer target) {
    ThidaBonds.data(owner).putBoolean("unlock3", true);
    ThidaBondData.get(owner.server).history(target.getUUID());
    owner.setYRot(0);
    owner.setXRot(0);
    var request = new CompoundTag();
    request.putString("action", "heart");
    ThidaBonds.request(owner, request);
  }

  private static void testAdapter() {
    ThidaSkillIntegration.install(
        new ThidaSkillIntegration.Adapter() {
          public Optional<ThidaSkillIntegration.Quote> quote(
              ServerPlayer p, String kind, int spell) {
            return Optional.of(new ThidaSkillIntegration.Quote(.2, 20, 2, () -> true));
          }

          public Optional<Double> parasiteBreakReduction(ServerPlayer p, ServerPlayer target) {
            return Optional.of(.2);
          }
        });
  }

  private static void passive(ServerPlayer owner, ServerPlayer target, String kind) {
    owner.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get(kind).get()));
    if (kind.equals("nilakan_doll"))
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
          new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(owner, target));
    else {
      owner.setYRot(0);
      owner.setXRot(0);
      for (int i = 0; i <= 200; i++) ThidaPassives.tickAt(owner, ThidaEvents.state(owner), i);
    }
  }

  @GameTest(template = "empty")
  public static void lockedSkillCannotSummon(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          ThidaBonds.summon(a);
          h.assertTrue(!ThidaBonds.data(a).hasUUID("parasiteItem"), "Locked by default");
        });
  }

  @GameTest(template = "empty")
  public static void summonUsesSelectedThenNearestEmptySlot(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          a.getInventory().selected = 4;
          a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
          ThidaBonds.data(a).putBoolean("unlock2", true);
          ThidaBonds.summon(a);
          h.assertTrue(
              a.getInventory().getItem(5).is(ThidaMod.ABILITIES.get("parasite_doll").get()),
              "Nearest empty hotbar slot");
        });
  }

  @GameTest(template = "empty")
  public static void fullInventoryDoesNotCreateToken(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          for (int i = 0; i < 36; i++) a.getInventory().setItem(i, new ItemStack(Items.STONE));
          ThidaBonds.data(a).putBoolean("unlock2", true);
          ThidaBonds.summon(a);
          h.assertTrue(
              !ThidaBonds.data(a).hasUUID("parasiteItem"), "Full inventory rejects summon");
        });
  }

  @GameTest(template = "empty")
  public static void parasiteLowHealthCapsOwnMaximum(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          b.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
          b.setHealth(20);
          parasite(a, b);
          near(h, a.getHealth(), 60);
          near(h, b.getHealth(), 20);
          h.assertTrue(
              ThidaBonds.link(a, "parasite").hasUUID("target"), "Bond active after capped average");
        });
  }

  @GameTest(template = "empty")
  public static void parasiteDamageEqualizesAfterReduction(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          b.setHealth(50);
          parasite(a, b);
          a.hurt(a.damageSources().generic(), 25);
          near(h, a.getHealth(), 80);
          near(h, b.getHealth(), 50);
          a.invulnerableTime = 0;
          b.invulnerableTime = 0;
          b.hurt(b.damageSources().generic(), 75);
          near(h, a.getHealth(), 35);
          near(h, b.getHealth(), 35);
        });
  }

  @GameTest(template = "empty")
  public static void parasiteOverkillCanKillBoth(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          parasite(a, b);
          a.hurt(a.damageSources().generic(), 1000);
          h.assertTrue(
              !a.isAlive() && !b.isAlive(), "Overkill must not be clipped or revive either player");
        });
  }

  @GameTest(template = "empty")
  public static void parasiteBreakPausesExistingPassives(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          passive(a, b, "nilakan_doll");
          ThidaBonds.data(a).putBoolean("unlock2", true);
          ThidaBonds.summon(a);
          ThidaBonds.breakParasite(a.server, a.getUUID(), "TEST", true);
          ThidaPassives.tickAt(a, ThidaEvents.state(a), 201);
          h.assertTrue(
              !ThidaPassives.isBondAvailable(a, "nilakan_doll"),
              "Five minute cooldown pauses existing bond");
          h.assertTrue(ThidaEvents.state(a).hasUUID("nilakan_doll"), "Pause retains target");
        });
  }

  @GameTest(template = "empty")
  public static void parasiteNeedsActualMeleeHpLoss(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          ThidaBonds.data(a).putBoolean("unlock2", true);
          ThidaBonds.summon(a);
          b.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);
          b.setAbsorptionAmount(20);
          near(h, b.getAbsorptionAmount(), 20);
          b.hurt(b.damageSources().playerAttack(a), 2);
          h.assertTrue(
              !ThidaBonds.data(a).contains("pendingParasite"),
              "Absorption-only damage is not HP loss");
          b.setAbsorptionAmount(0);
          b.invulnerableTime = 0;
          b.hurt(b.damageSources().playerAttack(a), 2);
          h.assertTrue(
              ThidaBonds.data(a).getCompound("pendingParasite").hasUUID("target"),
              "HP loss offers confirmation");
          h.assertTrue(!ThidaBonds.link(a, "parasite").hasUUID("target"), "No bond before yes");
        });
  }

  @GameTest(template = "empty")
  public static void parasiteRejectKeepsDamage(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          ThidaBonds.data(a).putBoolean("unlock2", true);
          ThidaBonds.summon(a);
          b.hurt(b.damageSources().playerAttack(a), 5);
          float hp = b.getHealth();
          var n = new CompoundTag();
          n.putString("action", "confirm");
          n.putUUID("target", b.getUUID());
          n.putBoolean("accept", false);
          ThidaBonds.request(a, n);
          near(h, b.getHealth(), hp);
          h.assertTrue(!ThidaBonds.link(a, "parasite").hasUUID("target"), "No bond on rejection");
        });
  }

  @GameTest(template = "empty")
  public static void heartRequiresHistoryAndStrictHpThreshold(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          a.setHealth(50);
          heart(a, b);
          h.assertTrue(!ThidaBonds.heartActive(a), "Exactly 50 rejects");
          a.setHealth(51);
          heart(a, b);
          h.assertTrue(ThidaBonds.heartActive(a), "Above 50 with history accepts");
        });
  }

  @GameTest(template = "empty")
  public static void heartRescuesOnceAndSuppressesOutputOnlyDuringImmunity(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          heart(a, b);
          b.hurt(b.damageSources().generic(), 1000);
          near(h, b.getHealth(), 1);
          b.invulnerableTime = 0;
          b.hurt(b.damageSources().generic(), 1000);
          near(h, b.getHealth(), 1);
          a.hurt(a.damageSources().playerAttack(b), 10);
          near(h, a.getHealth(), 100);
          ThidaBonds.data(b).putLong("heartInvulnerable", 0);
          b.invulnerableTime = 0;
          b.hurt(b.damageSources().generic(), 1000);
          h.assertTrue(!b.isAlive(), "Second lethal hit is not saved");
        });
  }

  @GameTest(template = "empty")
  public static void heartSelfSkillBlockedButNaturalHealAllowed(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          heart(a, b);
          b.setHealth(40);
          ThidaHealing.apply(b, b, 10, ThidaHealing.Kind.SKILL);
          near(h, b.getHealth(), 40);
          ThidaHealing.apply(b, b, 10, ThidaHealing.Kind.NON_SKILL);
          near(h, b.getHealth(), 50);
          h.assertTrue(ThidaBonds.heartActive(a), "Natural or potion healing keeps bond");
        });
  }

  @GameTest(template = "empty")
  public static void heartExternalAndUnknownHealHappenBeforeEnd(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          heart(a, b);
          b.setHealth(40);
          ThidaHealing.apply(a, b, 10, ThidaHealing.Kind.SKILL);
          near(h, b.getHealth(), 50);
          h.assertTrue(!ThidaBonds.heartActive(a), "External heal ends after healing");
          ThidaBonds.data(a).putLong("heartCooldown", 0);
          heart(a, b);
          b.heal(10);
          near(h, b.getHealth(), 60);
          h.assertTrue(!ThidaBonds.heartActive(a), "Unknown heal follows agreed fallback");
        });
  }

  @GameTest(template = "empty")
  public static void heartManualCancelWaitsForOriginalWardDeadline(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          heart(a, b);
          long deadline = ThidaBonds.link(a, "heart").getLong("wardUntil");
          ThidaBonds.endHeart(a.server, a.getUUID(), "MANUAL");
          h.assertTrue(
              ThidaBonds.data(a).getLong("heartCooldown") == deadline + 3600,
              "Early manual cancellation adds cooldown after original ward");
          h.assertTrue(ThidaBonds.warded(b), "Started ward survives bond ending");
        });
  }

  @GameTest(template = "empty")
  public static void noManaAdapterRejectsSpellWithoutCooldown(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          passive(a, b, "narotra_doll");
          ThidaSpells.cast(a, "narotra_doll", 0);
          h.assertTrue(
              !ThidaBonds.data(a).contains("spellCd_narotra_doll_0"),
              "No guessed mana, no cooldown");
        });
  }

  @GameTest(template = "empty")
  public static void spellsReplaceSameDollAndHaveIndependentCooldowns(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          passive(a, b, "narotra_doll");
          float base = (float) b.getAttributeValue(Attributes.MOVEMENT_SPEED);
          testAdapter();
          ThidaSpells.cast(a, "narotra_doll", 0);
          h.assertTrue(
              ThidaSpells.failure(a, "narotra_doll", 1).isEmpty(), "Other spell remains ready");
          ThidaSpells.cast(a, "narotra_doll", 2);
          near(h, (float) b.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.2f);
          ThidaPassives.unlink(a, "narotra_doll", "TEST");
          near(h, (float) b.getAttributeValue(Attributes.MOVEMENT_SPEED), base);
          h.assertTrue(
              ThidaBondData.child(ThidaBonds.data(a), "spells").isEmpty(),
              "Unlink removes spell immediately");
        });
  }

  @GameTest(template = "empty")
  public static void heartWardBlocksHarmfulEffects(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          heart(a, b);
          b.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
          h.assertTrue(
              !b.hasEffect(MobEffects.POISON), "Ward prevents harmful effects at application");
        });
  }

  @GameTest(template = "empty")
  public static void persistentOfflineSnapshotRetainsLinkAndHealth(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          parasite(a, b);
          ThidaBondData.get(a.server).remember(b);
          net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
              new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(b));
          h.assertTrue(ThidaBonds.link(a, "parasite").hasUUID("target"), "Logout preserves bond");
          var saved = ThidaBondData.get(a.server).save(new CompoundTag(), a.registryAccess());
          h.assertTrue(
              saved
                  .getCompound("players")
                  .getCompound(a.getUUID().toString())
                  .getBoolean("unlock2"),
              "Unlock stored by UUID");
          h.assertTrue(
              saved
                  .getCompound("players")
                  .getCompound(b.getUUID().toString())
                  .getBoolean("everLinked"),
              "History persisted");
          var loaded = ThidaBondData.load(saved, a.registryAccess());
          h.assertTrue(
              loaded.link(a.getUUID(), "parasite").getUUID("target").equals(b.getUUID()),
              "SavedData reload preserves target");
          h.assertTrue(loaded.player(a).getBoolean("unlock2"), "SavedData reload preserves unlock");
        });
  }

  @GameTest(template = "empty")
  public static void spellWorksAcrossDimensionWhilePassivePauses(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          passive(a, b, "narotra_doll");
          testAdapter();
          var original = b.serverLevel();
          b.setServerLevel(b.server.getLevel(net.minecraft.world.level.Level.NETHER));
          try {
            h.assertTrue(
                !ThidaPassives.isBondAvailable(a, "narotra_doll"),
                "Passive pauses across dimension");
            h.assertTrue(
                ThidaSpells.failure(a, "narotra_doll", 0).isEmpty(),
                "Skill 1 accepts cross-dimension target");
            ThidaSpells.cast(a, "narotra_doll", 0);
            h.assertTrue(ThidaEvents.state(a).hasUUID("narotra_doll"), "Casting does not unlink");
          } finally {
            b.setServerLevel(original);
          }
        });
  }

  @GameTest(template = "empty")
  public static void spellWorksBeyondOldRange(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          passive(a, b, "narotra_doll");
          testAdapter();
          b.setPos(a.getX() + 10000, a.getY(), a.getZ());
          ThidaSpells.cast(a, "narotra_doll", 0);
          h.assertTrue(
              ThidaBonds.data(a).getLong("spellCd_narotra_doll_0")
                  == ThidaBonds.now(a.server) + 200,
              "No range cap and exact ten second cooldown");
        });
  }

  @GameTest(template = "empty")
  public static void nilakanBleedStartsAfterOneSecondAndCannotKill(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          passive(a, b, "nilakan_doll");
          testAdapter();
          ThidaSpells.cast(a, "nilakan_doll", 1);
          near(h, b.getHealth(), 100);
          h.assertTrue(b.hasEffect(MobEffects.BLINDNESS), "Uses vanilla blindness");
          long time = ThidaBonds.now(a.server);
          a.server.getWorldData().overworldData().setGameTime(time + 20);
          ThidaSpells.tick(a.server);
          near(h, b.getHealth(), 98);
          b.setHealth(.5f);
          a.server.getWorldData().overworldData().setGameTime(time + 40);
          ThidaSpells.tick(a.server);
          near(h, b.getHealth(), .5f);
          ThidaPassives.unlink(a, "nilakan_doll", "TEST");
          h.assertTrue(!b.hasEffect(MobEffects.BLINDNESS), "Replacement removes owned blindness");
        });
  }

  @GameTest(template = "empty")
  public static void heartLateManualCancelStartsCooldownNow(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          heart(a, b);
          ThidaBonds.link(a, "heart").putLong("wardUntil", ThidaBonds.now(a.server) - 1);
          ThidaBonds.endHeart(a.server, a.getUUID(), "MANUAL");
          h.assertTrue(
              ThidaBonds.data(a).getLong("heartCooldown") == ThidaBonds.now(a.server) + 3600,
              "Late manual end starts three minutes now");
        });
  }

  @GameTest(template = "empty")
  public static void heartThresholdDamageBleedIsCleanseable(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          heart(a, b);
          a.setHealth(55);
          a.hurt(a.damageSources().generic(), 6);
          h.assertTrue(!ThidaBonds.heartActive(a), "Damage below threshold ends heart");
          h.assertTrue(a.hasEffect(ThidaMod.HEART_BLEED), "Damage reason adds bleed");
          ThidaEvents.cleanse(a);
          h.assertTrue(!a.hasEffect(ThidaMod.HEART_BLEED), "Cleanse removes bleed marker");
        });
  }

  @GameTest(template = "empty")
  public static void offlineSpeedExpiresOnOriginalDeadline(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          heart(a, b);
          ThidaBonds.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(b));
          h.assertTrue(b.hasEffect(MobEffects.MOVEMENT_SPEED), "Speed initially applied");
          ThidaBonds.logout(
              new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(b));
          ThidaBonds.data(b)
              .getCompound("offlineSpeed")
              .putLong("deadline", ThidaBonds.now(a.server) - 1);
          ThidaBonds.data(b).putLong("heartSpeed", ThidaBonds.now(a.server) - 1);
          ThidaBonds.login(
              new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(b));
          h.assertTrue(
              !b.hasEffect(MobEffects.MOVEMENT_SPEED), "Offline time cannot extend owned Speed");
        });
  }

  @GameTest(template = "empty")
  public static void spentParasiteItemCannotReturnFromOfflineStorage(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          ThidaBonds.data(a).putBoolean("unlock2", true);
          ThidaBonds.summon(a);
          var copy = a.getMainHandItem().copy();
          ThidaBonds.breakParasite(a.server, a.getUUID(), "TEST", false);
          ThidaBonds.discardExpiredDoll(copy, a.level());
          h.assertTrue(copy.isEmpty(), "Spent token is discarded when it returns");
        });
  }

  @GameTest(template = "empty")
  public static void lostOriginCannotUseSavedUnlock(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          ThidaBonds.data(b).putBoolean("unlock2", true);
          var request = new CompoundTag();
          request.putString("action", "summon");
          ThidaBonds.request(b, request);
          h.assertTrue(
              !ThidaBonds.data(b).hasUUID("parasiteItem"), "Stored unlock does not grant Origin");
        });
  }

  @GameTest(template = "empty")
  public static void spareUnlinkedDollCannotCastUsingInventoryDoll(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          passive(a, b, "narotra_doll");
          testAdapter();
          a.getInventory().setItem(10, a.getMainHandItem());
          a.setItemInHand(
              InteractionHand.MAIN_HAND,
              new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get()));
          h.assertTrue(
              !ThidaSpells.failure(a, "narotra_doll", 0).isEmpty(),
              "Only held linked instance can cast");
        });
  }

  @GameTest(template = "empty")
  public static void conflictingLegacyPassivesPauseUntilPlayerUnlinks(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          passive(a, b, "nilakan_doll");
          ThidaEvents.state(a).putUUID("narotra_doll", b.getUUID());
          h.assertTrue(ThidaBonds.conflictingPassives(a), "Detect legacy duplicate target");
          h.assertTrue(
              !ThidaPassives.isBondAvailable(a, "nilakan_doll"), "Do not stack legacy conflict");
          ThidaPassives.unlink(a, "narotra_doll", "TEST");
          h.assertTrue(
              ThidaPassives.isBondAvailable(a, "nilakan_doll"),
              "Player chooses which link to keep");
        });
  }

  @GameTest(template = "empty")
  public static void personalManaOverridesOptionalIronAndUsesEachRecipientsMaximum(
      GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          var mana = new java.util.HashMap<java.util.UUID, Float>();
          ThidaManaIntegration.install(
              new ThidaManaIntegration.Adapter() {
                public double maxMana(ServerPlayer p) {
                  return p == a ? 200 : 600;
                }

                public boolean restore(ServerPlayer p, float amount, float maximum) {
                  mana.put(
                      p.getUUID(), Math.min(maximum, mana.getOrDefault(p.getUUID(), 0f) + amount));
                  return true;
                }
              });
          passive(a, b, "narotra_doll");
          near(h, mana.get(a.getUUID()), 10);
          near(h, mana.get(b.getUUID()), 30);
          ThidaPassives.tickAt(a, ThidaEvents.state(a), 201);
          near(h, mana.get(a.getUUID()), 10);
          ThidaPassives.tickAt(a, ThidaEvents.state(a), 600);
          near(h, mana.get(a.getUUID()), 20);
          near(h, mana.get(b.getUUID()), 60);
        });
  }

  @GameTest(template = "empty")
  public static void invalidPersonalMaximumDoesNotFallThroughToIron(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          ThidaManaIntegration.install(
              new ThidaManaIntegration.Adapter() {
                public double maxMana(ServerPlayer p) {
                  return Double.NaN;
                }

                public boolean restore(ServerPlayer p, float amount, float maximum) {
                  throw new AssertionError("Invalid maximum must not restore");
                }
              });
          h.assertTrue(
              !ThidaManaIntegration.restoreFivePercent(a),
              "Reject invalid maximum without invoking Iron");
        });
  }

  @GameTest(template = "empty")
  public static void immunityExpiryHookSurvivesBondEnding(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          heart(a, b);
          b.hurt(b.damageSources().generic(), 1000);
          ThidaBonds.endHeart(a.server, a.getUUID(), "TEST");
          h.assertTrue(ThidaBonds.invulnerable(b), "Ending bond preserves started immunity");
          ThidaBonds.data(b).putLong("heartInvulnerable", ThidaBonds.now(a.server));
          ThidaBonds.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(b));
          h.assertTrue(
              !ThidaBonds.data(b).hasUUID("heartInvulnerabilityOwner"),
              "Expiry dispatched once after bond ending");
        });
  }

  @GameTest(template = "empty")
  public static void nilakanBleedAppliesFivePulsesIncludingExpiry(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          passive(a, b, "nilakan_doll");
          testAdapter();
          ThidaSpells.cast(a, "nilakan_doll", 1);
          long started = ThidaBonds.now(a.server);
          for (int pulse = 1; pulse <= 5; pulse++) {
            a.server.getWorldData().overworldData().setGameTime(started + pulse * 20);
            ThidaSpells.tick(a.server);
            near(h, b.getHealth(), 100 - pulse * 2);
          }
          h.assertTrue(
              ThidaBondData.child(ThidaBonds.data(a), "spells").isEmpty(),
              "Final pulse also expires spell");
        });
  }

  @GameTest(template = "empty")
  public static void cleansingBleedPreventsItsFinalPulse(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          passive(a, b, "nilakan_doll");
          testAdapter();
          ThidaSpells.cast(a, "nilakan_doll", 1);
          long started = ThidaBonds.now(a.server);
          ThidaEvents.cleanse(b);
          a.server.getWorldData().overworldData().setGameTime(started + 100);
          ThidaSpells.tick(a.server);
          near(h, b.getHealth(), 100);
        });
  }

  @GameTest(template = "empty")
  public static void heartBleedWaitsOneSecondAndAppliesTenPulses(GameTestHelper h) {
    run(
        h,
        (a, b) -> {
          heart(a, b);
          a.setHealth(55);
          a.hurt(a.damageSources().generic(), 6);
          long started = ThidaBonds.now(a.server);
          near(h, a.getHealth(), 49);
          ThidaBonds.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(a));
          near(h, a.getHealth(), 49);
          for (int pulse = 1; pulse <= 10; pulse++) {
            a.server.getWorldData().overworldData().setGameTime(started + pulse * 20);
            ThidaBonds.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(a));
            near(h, a.getHealth(), 49 - pulse * 2);
          }
        });
  }
}
