package dev.thida;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("thida")
@PrefixGameTestTemplate(false)
public final class HandoffGameTests {
  @GameTest(template = "empty")
  public static void movingDollsInsidePlayerNeverTransfersToChestOrBreaksOnCursor(
      GameTestHelper helper) {
    try (var fixture = new ThidaGameTests.Fixture(helper)) {
      var owner = fixture.player(true);
      ThidaBonds.data(owner).putBoolean("unlock2", true);
      ThidaBonds.summon(owner);
      var originalMenu = owner.containerMenu;
      var chest = new net.minecraft.world.SimpleContainer(27);
      var menu = net.minecraft.world.inventory.ChestMenu.threeRows(1, owner.getInventory(), chest);
      owner.containerMenu = menu;
      try {
        menu.clicked(54, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, owner);
        helper.assertTrue(
            !owner.getInventory().getItem(0).isEmpty() && chest.isEmpty(),
            "Shift transfer stays on player");
        menu.clicked(54, 0, net.minecraft.world.inventory.ClickType.PICKUP, owner);
        ThidaBonds.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(owner));
        helper.assertTrue(
            ThidaBonds.data(owner).hasUUID("parasiteItem") && !menu.getCarried().isEmpty(),
            "Cursor is still owned inventory");
        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, owner);
        helper.assertTrue(
            chest.isEmpty() && !menu.getCarried().isEmpty(), "External placement rejected");
        menu.clicked(55, 0, net.minecraft.world.inventory.ClickType.PICKUP, owner);
        helper.assertTrue(
            !owner.getInventory().getItem(1).isEmpty() && menu.getCarried().isEmpty(),
            "Move to another own slot accepted");
        owner.getInventory().selected = 1;
        helper.assertTrue(
            !owner.drop(false) && !owner.getMainHandItem().isEmpty(), "Drop does not remove doll");
      } finally {
        owner.containerMenu = originalMenu;
        ThidaBonds.breakParasite(owner.server, owner.getUUID(), "TEST", false);
      }
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void endingHeartOnlyEndsBondPresentationWhileBuffInstancesSurvive(
      GameTestHelper helper) {
    try (var fixture = new ThidaGameTests.Fixture(helper)) {
      var owner = fixture.player(true);
      var target = fixture.player(false);
      target.setPos(owner.getX(), owner.getY(), owner.getZ() + 4);
      owner.setYRot(0);
      owner.setXRot(0);
      ThidaBonds.data(owner).putBoolean("unlock3", true);
      ThidaBondData.get(owner.server).history(target.getUUID());
      var request = new net.minecraft.nbt.CompoundTag();
      request.putString("action", "heart");
      ThidaBonds.request(owner, request);
      ThidaPassiveEffects.reconcile(owner.server);
      var before =
          ThidaPassiveEffects.snapshot(owner.server).stream()
              .filter(
                  value -> owner.getUUID().equals(value.owner()) && value.power().equals("heart"))
              .toList();
      helper.assertTrue(
          before.stream().anyMatch(value -> value.component().equals("bond")),
          "Heart bond has a visual instance");
      var ward =
          before.stream()
              .filter(value -> value.component().equals("ward"))
              .findFirst()
              .orElseThrow();
      var bond =
          before.stream()
              .filter(value -> value.component().equals("bond"))
              .findFirst()
              .orElseThrow();
      helper.assertTrue(
          ward.activation().equals(bond.activation()) && !ward.instance().equals(bond.instance()),
          "Shared activation, independently stoppable components");
      ThidaBonds.endHeart(owner.server, owner.getUUID(), "MANUAL");
      ThidaPassiveEffects.reconcile(owner.server);
      var after = ThidaPassiveEffects.snapshot(owner.server);
      helper.assertTrue(
          after.stream().noneMatch(value -> value.instance().equals(bond.instance())),
          "Only bond stops");
      helper.assertTrue(
          after.stream().anyMatch(value -> value.instance().equals(ward.instance())),
          "Ward keeps exact instance after bond ends");
      var saved =
          ThidaBondData.get(owner.server)
              .save(new net.minecraft.nbt.CompoundTag(), owner.registryAccess());
      var restored = ThidaBondData.load(saved, owner.registryAccess());
      helper.assertTrue(
          restored.player(target).getUUID("fx_heart_activation").equals(ward.activation()),
          "Presentation identity survives SavedData reload without resetting buffs");
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void crossDimensionPresentationUsesTargetsDimension(GameTestHelper helper) {
    try (var fixture = new ThidaGameTests.Fixture(helper)) {
      var owner = fixture.player(true);
      var target = fixture.player(false);
      var spells = ThidaBondData.child(ThidaBonds.data(owner), "spells");
      var spell = new net.minecraft.nbt.CompoundTag();
      spell.putUUID("target", target.getUUID());
      spell.putInt("spell", 0);
      spell.putLong("started", ThidaBonds.now(owner.server));
      spell.putLong("expires", ThidaBonds.now(owner.server) + 200);
      spells.put("narotra_doll", spell);
      var original = target.serverLevel();
      target.setServerLevel(target.server.getLevel(net.minecraft.world.level.Level.NETHER));
      try {
        ThidaPassiveEffects.reconcile(owner.server);
        var visual =
            ThidaPassiveEffects.snapshot(owner.server).stream()
                .filter(
                    value ->
                        value.component().equals("spell") && owner.getUUID().equals(value.owner()))
                .findFirst()
                .orElseThrow();
        helper.assertTrue(
            visual.point().dimension().equals("minecraft:the_nether")
                && visual.ownerPoint().dimension().equals("minecraft:overworld"),
            "Coordinates retain their own dimensions");
        helper.assertTrue(
            visual.anchor() == ThidaEffectEvent.Anchor.TARGET
                && visual.target().equals(target.getUUID()),
            "Target anchor never substitutes the caster");
      } finally {
        target.setServerLevel(original);
        ThidaSpells.stop(owner.server, owner.getUUID(), "narotra_doll");
      }
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void quoteIsReadOnlyAndCastPaysOnceWithoutBreakingOldSpellOnFailure(
      GameTestHelper helper) {
    try (var fixture = new ThidaGameTests.Fixture(helper)) {
      var owner = fixture.player(true);
      var target = fixture.player(false);
      target.setPos(owner.getX(), owner.getY(), owner.getZ() + 4);
      owner.setYRot(0);
      owner.setXRot(0);
      owner.setItemInHand(
          InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get()));
      for (int tick = 0; tick <= 200; tick++)
        ThidaPassives.tickAt(owner, ThidaEvents.state(owner), tick);
      var quotes = new AtomicInteger();
      var payments = new AtomicInteger();
      ThidaSkillIntegration.install(
          new ThidaSkillIntegration.Adapter() {
            public Optional<ThidaSkillIntegration.Quote> quote(
                net.minecraft.server.level.ServerPlayer caster, String doll, int spell) {
              quotes.incrementAndGet();
              return Optional.of(
                  new ThidaSkillIntegration.Quote(
                      .2,
                      20,
                      2,
                      () -> {
                        payments.incrementAndGet();
                        return spell == 0;
                      }));
            }

            public Optional<Double> parasiteBreakReduction(
                net.minecraft.server.level.ServerPlayer caster,
                net.minecraft.server.level.ServerPlayer recipient) {
              return Optional.empty();
            }
          });
      for (int tick = 0; tick < 5; tick++) ThidaSpells.failure(owner, "narotra_doll", 0);
      helper.assertTrue(payments.get() == 0, "Menu quotes never pay");
      quotes.set(0);
      ThidaSpells.cast(owner, "narotra_doll", 0);
      helper.assertTrue(
          quotes.get() == 1 && payments.get() == 1, "Cast evaluates once and pays once");
      ThidaPassiveEffects.reconcile(owner.server);
      var before =
          ThidaBondData.child(ThidaBonds.data(owner), "spells").getCompound("narotra_doll").copy();
      ThidaSpells.cast(owner, "narotra_doll", 2);
      helper.assertTrue(
          before.equals(
              ThidaBondData.child(ThidaBonds.data(owner), "spells").getCompound("narotra_doll")),
          "Failed payment preserves old spell and its metadata");
      helper.assertTrue(
          !ThidaBonds.data(owner).contains("spellCd_narotra_doll_2"),
          "Failed payment never starts cooldown");
      var snapshot = ThidaPassiveEffects.snapshot(owner.server);
      helper.assertTrue(
          snapshot.stream()
              .anyMatch(
                  event ->
                      event.component().equals("spell") && event.spell().equals("narotra_shield")),
          "Typed spell has its own effect instance");
      ThidaSpells.stop(owner.server, owner.getUUID(), "narotra_doll");
      ThidaPassives.unlink(owner, "narotra_doll", "TEST");
    } finally {
      ThidaSkillIntegration.install(null);
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void resolvedPaymentCannotBeConsumedTwice(GameTestHelper helper) {
    try (var fixture = new ThidaGameTests.Fixture(helper)) {
      var owner = fixture.player(true);
      var payments = new AtomicInteger();
      ThidaSkillIntegration.install(
          new ThidaSkillIntegration.Adapter() {
            public Optional<ThidaSkillIntegration.Quote> quote(
                net.minecraft.server.level.ServerPlayer caster, String doll, int spell) {
              return Optional.of(
                  new ThidaSkillIntegration.Quote(
                      .2,
                      20,
                      2,
                      () -> {
                        payments.incrementAndGet();
                        return true;
                      }));
            }

            public Optional<Double> parasiteBreakReduction(
                net.minecraft.server.level.ServerPlayer caster,
                net.minecraft.server.level.ServerPlayer target) {
              return Optional.empty();
            }
          });
      var result = ThidaSkillIntegration.resolve(owner, ThidaSpell.NAROTRA_SHIELD);
      helper.assertTrue(
          result.quote().payment().consume()
              && !result.quote().payment().consume()
              && payments.get() == 1,
          "Each resolved payment is one shot");
    } finally {
      ThidaSkillIntegration.install(null);
    }
    helper.succeed();
  }
}
