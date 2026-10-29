package dev.thida;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Synchronous tests: no fixture survives the test callback, even after an assertion fails. */
@GameTestHolder("thida")
@PrefixGameTestTemplate(false)
public final class ThidaGameTests {
  @GameTest(template = "empty")
  public static void advancedFakePlayerCanSpawnAndReceiveBonds(GameTestHelper h) {
    if (!net.neoforged.fml.ModList.get().isLoaded("advancedfakeplayers")) {
      com.mojang.logging.LogUtils.getLogger().info("AFP_COMPAT_TEST_SKIPPED: optional mod absent");
      h.succeed();
      return;
    }
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true);
          ServerPlayer target = null;
          try {
            Class<?> manager = Class.forName("com.advancedfakeplayers.manager.FakePlayerManager");
            target =
                (ServerPlayer)
                    manager
                        .getMethod(
                            "spawnFakePlayer",
                            net.minecraft.server.MinecraftServer.class,
                            ServerLevel.class,
                            GameProfile.class,
                            boolean.class)
                        .invoke(
                            null,
                            owner.server,
                            h.getLevel(),
                            new GameProfile(UUID.randomUUID(), "ThidaTestBot"),
                            false);
            h.assertTrue(
                owner.server.getPlayerList().getPlayer(target.getUUID()) == target,
                "AFP must finish login with Carry On installed");
            target.setPos(owner.getX(), owner.getY(), owner.getZ() + 5);
            target.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            selectOrigin(target, "origins:human");
            owner.setYRot(0);
            owner.setXRot(0);
            owner.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get()));
            for (int i = 0; i <= 200; i++) ThidaPassives.tickAt(owner, state(owner), i);
            h.assertTrue(
                state(owner).hasUUID("narotra_doll")
                    && state(owner).getUUID("narotra_doll").equals(target.getUUID()),
                "Narotra links AFP after gaze");
            ThidaPassives.unlink(owner, "narotra_doll", "TEST");
            owner.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
            NeoForge.EVENT_BUS.post(new AttackEntityEvent(owner, target));
            h.assertTrue(
                state(owner).hasUUID("nilakan_doll")
                    && state(owner).getUUID("nilakan_doll").equals(target.getUUID()),
                "Nilakan links AFP on attack");
            ThidaPassives.tickAt(owner, state(owner), 201);
            near(
                h,
                owner.getAttributeValue(Attributes.MOVEMENT_SPEED),
                Attributes.MOVEMENT_SPEED.value().getDefaultValue() * 1.05,
                "AFP activates Nilakan bonus");
            com.mojang.logging.LogUtils.getLogger()
                .info("AFP_COMPAT_TEST_PASS: login, Narotra, Nilakan with Carry On");
          } catch (ReflectiveOperationException e) {
            throw new AssertionError("AFP 1.0.0 integration failed", e);
          } finally {
            if (target != null) {
              owner.server.getPlayerList().remove(target);
              target.discard();
            }
          }
        });
  }

  @GameTest(template = "empty")
  public static void retiredPowersAreInertAndUncraftable(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer p = f.player(true);
          p.setHealth(40);
          p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
          for (var entry : ThidaMod.RETIRED.entrySet()) {
            ItemStack stack = new ItemStack(entry.getValue().get());
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            h.assertTrue(
                stack.use(p.level(), p, InteractionHand.MAIN_HAND).getResult()
                    == InteractionResult.PASS,
                "Retired item cannot activate: " + entry.getKey());
            h.assertTrue(
                ThidaEvents.use(p, stack, InteractionHand.MAIN_HAND).getResult()
                    == InteractionResult.PASS,
                "Server rejects retired ability even through direct hook");
            h.assertTrue(
                p.server
                    .getRecipeManager()
                    .byKey(ResourceLocation.fromNamespaceAndPath("thida", entry.getKey()))
                    .isEmpty(),
                "Retired recipe removed");
            h.assertTrue(
                !ThidaMod.ABILITIES.containsKey(entry.getKey()),
                "Retired ability excluded from kit/creative");
          }
          near(h, p.getHealth(), 40, "Retired remedy cannot heal");
          h.assertTrue(
              p.hasEffect(MobEffects.POISON)
                  && !p.hasEffect(MobEffects.ABSORPTION)
                  && !p.hasEffect(MobEffects.INVISIBILITY)
                  && !p.hasEffect(MobEffects.HERO_OF_THE_VILLAGE),
              "No retired effects run");
        });
  }

  @GameTest(template = "empty")
  public static void nonOriginRejects(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer p = f.player(false);
          p.addTag("thida_origin");
          h.assertTrue(
              !ThidaEvents.isThida(p),
              "A leftover scoreboard tag cannot grant the actual Thida Origin");
          p.setHealth(40);
          p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
          for (String id : ThidaMod.ABILITIES.keySet()) {
            ItemStack stack = new ItemStack(ThidaMod.ABILITIES.get(id).get());
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            h.assertTrue(
                !stack.use(p.level(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "Non-origin must not activate " + id);
          }
          near(h, p.getHealth(), 40, "Non-origin must not heal");
          h.assertTrue(
              p.hasEffect(MobEffects.POISON)
                  && !p.hasEffect(MobEffects.ABSORPTION)
                  && !p.hasEffect(MobEffects.HERO_OF_THE_VILLAGE),
              "Non-origin effects must remain unchanged");
          h.assertTrue(
              !p.getPersistentData().contains("thida_state"),
              "Rejected use must not create ability state");
        });
  }

  @GameTest(template = "empty")
  public static void nilakanLinksOnAttackThenPausesAndResumes(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true), target = f.player(false);
          ItemStack doll = new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get());
          owner.setItemInHand(InteractionHand.MAIN_HAND, doll);
          target.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
          float targetHealth = target.getHealth();
          AttackEntityEvent attack = new AttackEntityEvent(owner, target);
          NeoForge.EVENT_BUS.post(attack);
          h.assertTrue(
              state(owner).hasUUID("nilakan_doll")
                  && state(owner).getUUID("nilakan_doll").equals(target.getUUID()),
              "A direct attack attempt links Nilakan");
          near(h, target.getHealth(), targetHealth, "Linking does not require damage to be dealt");

          tick(owner);
          near(
              h,
              owner.getAttributeValue(Attributes.MOVEMENT_SPEED),
              Attributes.MOVEMENT_SPEED.value().getDefaultValue() * 1.05,
              "Active Nilakan grants 5% movement speed");
          near(
              h,
              owner.getAttributeValue(Attributes.ATTACK_DAMAGE),
              Attributes.ATTACK_DAMAGE.value().getDefaultValue() * 1.05,
              "Active Nilakan grants 5% melee damage");

          target.setPos(owner.getX() + 201, owner.getY(), owner.getZ());
          tick(owner);
          h.assertTrue(state(owner).hasUUID("nilakan_doll"), "Distance no longer deletes the bond");
          near(
              h,
              owner.getAttributeValue(Attributes.MOVEMENT_SPEED),
              Attributes.MOVEMENT_SPEED.value().getDefaultValue() * 1.05,
              "Nilakan remains active beyond 200 blocks");

          target.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
          tick(owner);
          near(
              h,
              owner.getAttributeValue(Attributes.MOVEMENT_SPEED),
              Attributes.MOVEMENT_SPEED.value().getDefaultValue() * 1.05,
              "Returning to range resumes the bonus");

          f.uuidIndex.remove(target.getUUID());
          tick(owner);
          h.assertTrue(
              state(owner).hasUUID("nilakan_doll"),
              "Target disconnect pauses without deleting the bond");
          near(
              h,
              owner.getAttributeValue(Attributes.MOVEMENT_SPEED),
              Attributes.MOVEMENT_SPEED.value().getDefaultValue(),
              "Disconnected target pauses Nilakan bonuses");
          f.uuidIndex.put(target.getUUID(), target);
          tick(owner);
          near(
              h,
              owner.getAttributeValue(Attributes.MOVEMENT_SPEED),
              Attributes.MOVEMENT_SPEED.value().getDefaultValue() * 1.05,
              "Returning target resumes the bond");

          ThidaPassives.onOwnerUnavailable(owner);
          h.assertTrue(state(owner).hasUUID("nilakan_doll"), "Owner disconnect preserves its bond");
          tick(owner);
          near(
              h,
              owner.getAttributeValue(Attributes.MOVEMENT_SPEED),
              Attributes.MOVEMENT_SPEED.value().getDefaultValue() * 1.05,
              "Owner reconnect resumes the bond");

          selectOrigin(owner, "origins:human");
          ThidaPassives.originLost(owner);
          h.assertTrue(
              !state(owner).hasUUID("nilakan_doll"), "Losing the Thida Origin ends the bond");
          near(
              h,
              owner.getAttributeValue(Attributes.MOVEMENT_SPEED),
              Attributes.MOVEMENT_SPEED.value().getDefaultValue(),
              "Origin loss removes the passive bonus");

          selectOrigin(owner, "thida:thida");
          NeoForge.EVENT_BUS.post(new AttackEntityEvent(owner, target));
          h.assertTrue(
              state(owner).hasUUID("nilakan_doll"), "Thida can establish another Nilakan bond");
          ThidaPassives.onToss(owner, owner.getMainHandItem());
          h.assertTrue(
              !state(owner).hasUUID("nilakan_doll"),
              "Tossing the linked doll ends its bond immediately");
        });
  }

  @GameTest(template = "empty")
  public static void passiveCleansesWaitForDebuffsAndTheirIntervals(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer narotraOwner = f.player(true), narotraTarget = f.player(false);
          ItemStack narotra = new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get());
          UUID narotraItemId = UUID.randomUUID();
          ThidaItemData.putUUID(narotra, "thida_passive_instance", narotraItemId);
          narotraOwner.setItemInHand(InteractionHand.MAIN_HAND, narotra);
          narotraTarget.setPos(narotraOwner.getX(), narotraOwner.getY(), narotraOwner.getZ() + 5);
          CompoundTag narotraState = state(narotraOwner);
          narotraState.putUUID("gazeTarget", narotraTarget.getUUID());
          narotraState.putUUID("passive_gaze_item", narotraItemId);
          narotraState.putInt("gaze", 199);
          ThidaPassives.tickAt(narotraOwner, narotraState, 1000);
          h.assertTrue(
              narotraState.getUUID("narotra_doll").equals(narotraTarget.getUUID()),
              "Narotra completes the 10-second gaze threshold");
          h.assertTrue(
              !narotraOwner.hasEffect(MobEffects.POISON), "No debuff means no cleanse is needed");

          narotraOwner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
          ThidaPassives.tickAt(narotraOwner, narotraState, 1001);
          h.assertTrue(
              !narotraOwner.hasEffect(MobEffects.POISON),
              "Narotra waits for and immediately cleanses a debuff");
          narotraOwner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
          ThidaPassives.tickAt(narotraOwner, narotraState, 1001 + 24000 - 1);
          h.assertTrue(
              narotraOwner.hasEffect(MobEffects.POISON),
              "Narotra respects its 20-minute cleanse interval");
          ThidaPassives.tickAt(narotraOwner, narotraState, 1001 + 24000);
          h.assertTrue(
              !narotraOwner.hasEffect(MobEffects.POISON),
              "Narotra cleanses when the interval expires and a debuff exists");

          FakePlayer nilakanOwner = f.player(true), nilakanTarget = f.player(false);
          ItemStack nilakan = new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get());
          UUID nilakanItemId = UUID.randomUUID();
          ThidaItemData.putUUID(nilakan, "thida_passive_instance", nilakanItemId);
          nilakanOwner.setItemInHand(InteractionHand.MAIN_HAND, nilakan);
          nilakanTarget.setPos(nilakanOwner.getX() + 5, nilakanOwner.getY(), nilakanOwner.getZ());
          CompoundTag nilakanState = state(nilakanOwner);
          nilakanState.putUUID("nilakan_doll", nilakanTarget.getUUID());
          nilakanState.putUUID("passive_nilakan_item", nilakanItemId);
          nilakanState.putBoolean("passive_nilakan_active", true);
          nilakanState.putLong("passive_nilakan_cleanse_at", 2000);
          nilakanOwner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
          ThidaPassives.tickAt(nilakanOwner, nilakanState, 2000);
          h.assertTrue(
              !nilakanOwner.hasEffect(MobEffects.POISON),
              "Nilakan performs its first cleanse as soon as a debuff exists");
          nilakanOwner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
          ThidaPassives.tickAt(nilakanOwner, nilakanState, 2000 + 12000 - 1);
          h.assertTrue(
              nilakanOwner.hasEffect(MobEffects.POISON),
              "Nilakan respects its 10-minute cleanse interval");
          ThidaPassives.tickAt(nilakanOwner, nilakanState, 2000 + 12000);
          h.assertTrue(
              !nilakanOwner.hasEffect(MobEffects.POISON),
              "Nilakan cleanses when its interval expires");
        });
  }

  @GameTest(template = "empty")
  public static void narotraLinksAndCleansesWithoutManaProvider(GameTestHelper h) {
    run(
        h,
        f -> {
          ThidaManaIntegration.install(null);
          FakePlayer owner = f.player(true), target = f.player(false);
          target.setPos(owner.getX(), owner.getY(), owner.getZ() + 5);
          completeNarotra(owner, target, 1000);
          h.assertTrue(
              state(owner).hasUUID("narotra_doll"), "Narotra links without a mana provider");
          h.assertTrue(
              !ThidaManaIntegration.restoreFivePercent(owner), "No mana account is invented");
          owner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
          ThidaPassives.tickAt(owner, state(owner), 1001);
          h.assertTrue(
              !owner.hasEffect(MobEffects.POISON),
              "Narotra still cleanses without a mana provider");
        });
  }

  private static void completeNarotra(FakePlayer owner, FakePlayer target, long time) {
    ItemStack doll = new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get());
    UUID token = UUID.randomUUID();
    ThidaItemData.putUUID(doll, "thida_passive_instance", token);
    owner.setItemInHand(InteractionHand.MAIN_HAND, doll);
    owner.setYRot(0);
    owner.setXRot(0);
    state(owner).putUUID("gazeTarget", target.getUUID());
    state(owner).putUUID("passive_gaze_item", token);
    state(owner).putInt("gaze", 199);
    ThidaPassives.tickAt(owner, state(owner), time);
  }

  @GameTest(template = "empty")
  public static void domainReducesDamageToThirtyPercent(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer p = f.player(true);
          near(
              h, ThidaConfig.DOMAIN_REDUCTION.get(), 0.7, "Test requires default domain reduction");
          ThidaBonds.data(p).putBoolean("unlock4", true);
          use(h, p, "domain_talisman");
          LivingIncomingDamageEvent event = incoming(p, p.damageSources().generic(), 10);
          NeoForge.EVENT_BUS.post(event);
          near(h, event.getAmount(), 3, "Domain must multiply incoming damage by .3");
        });
  }

  @GameTest(template = "empty")
  public static void nilakanBothModes(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true), target = f.player(false);
          owner.setItemInHand(
              InteractionHand.MAIN_HAND,
              new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
          target.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
          NeoForge.EVENT_BUS.post(new AttackEntityEvent(owner, target));
          h.assertTrue(
              state(owner).hasUUID("nilakan_doll"), "Nilakan links on a direct attack attempt");
          tick(owner);
          near(
              h,
              owner.getAttributeValue(Attributes.MOVEMENT_SPEED),
              Attributes.MOVEMENT_SPEED.value().getDefaultValue() * 1.05,
              "Nilakan grants 5% movement speed");
          near(
              h,
              owner.getAttributeValue(Attributes.ATTACK_DAMAGE),
              Attributes.ATTACK_DAMAGE.value().getDefaultValue() * 1.05,
              "Nilakan grants 5% melee damage");
          h.assertTrue(
              !target.hasEffect(MobEffects.BLINDNESS) && !target.hasEffect(MobEffects.WEAKNESS),
              "Nilakan link does not cast active curses");
        });
  }

  @GameTest(template = "empty")
  public static void registryOriginResourceAndKitCommand(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer p = f.player(false);
          h.assertTrue(
              ThidaMod.ABILITIES.size() == 5, "Only the five current abilities register as active");
          ThidaMod.ABILITIES.forEach(
              (id, item) ->
                  h.assertTrue(
                      item.get() != null
                          && BuiltInRegistries.ITEM.get(
                                  ResourceLocation.fromNamespaceAndPath("thida", id))
                              == item.get(),
                      "Missing item " + id));
          var server = p.server;
          var selectedLayer =
              com.iafenvoy.origins.attachment.OriginDataHolder.get(p)
                  .getOrigins()
                  .keySet()
                  .iterator()
                  .next();
          var options =
              selectedLayer
                  .value()
                  .collectOrigins(p)
                  .map(option -> option.unwrapKey().orElseThrow().location())
                  .toList();
          h.assertTrue(
              options.contains(ThidaOrigins.THIDA),
              "Thida is available in the actual Origin selection layer");
          h.assertTrue(
              options.contains(ResourceLocation.fromNamespaceAndPath("origins", "human")),
              "The selection layer preserves built-in Origins");
          try (var reader =
              server
                  .getResourceManager()
                  .getResource(
                      ResourceLocation.fromNamespaceAndPath("thida", "origins/origin/thida.json"))
                  .orElseThrow()
                  .openAsReader()) {
            var origin = JsonParser.parseReader(reader).getAsJsonObject();
            h.assertTrue(origin.getAsJsonArray("powers").size() > 0, "Origin must declare powers");
            for (var power : origin.getAsJsonArray("powers")) {
              ResourceLocation id = ResourceLocation.parse(power.getAsString());
              h.assertTrue(
                  server
                      .getResourceManager()
                      .getResource(
                          ResourceLocation.fromNamespaceAndPath(
                              id.getNamespace(), "origins/power/" + id.getPath() + ".json"))
                      .isPresent(),
                  "Missing power " + id);
            }
          } catch (Exception e) {
            throw new AssertionError("Origin resource must load", e);
          }
          try {
            var source = server.createCommandSourceStack().withSuppressedOutput();
            server
                .getCommands()
                .getDispatcher()
                .execute(
                    "origin set " + p.getScoreboardName() + " origins:origin thida:thida", source);
            h.assertTrue(
                ThidaEvents.isThida(p), "Selecting actual Origin must run gained callback");
            server
                .getCommands()
                .getDispatcher()
                .execute(
                    "origin set " + p.getScoreboardName() + " origins:origin origins:human",
                    source);
            h.assertTrue(
                !ThidaEvents.isThida(p), "Changing actual Origin must revoke callback tag");
            server
                .getCommands()
                .getDispatcher()
                .execute(
                    "origin set " + p.getScoreboardName() + " origins:origin thida:thida", source);
            int result =
                server
                    .getCommands()
                    .getDispatcher()
                    .execute("thida kit", p.createCommandSourceStack().withSuppressedOutput());
            h.assertTrue(result == 1, "Kit command must succeed");
            for (String id : new String[] {"narotra_doll", "nilakan_doll"})
              h.assertTrue(
                  p.getInventory().contains(new ItemStack(ThidaMod.ABILITIES.get(id).get())),
                  "Kit missing " + id);
          } catch (Exception e) {
            throw new AssertionError("Kit command must execute", e);
          }
        });
  }

  @GameTest(template = "empty")
  public static void dualDollsPersistAcrossHandSwap(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true), target = f.player(false);
          owner.setItemInHand(
              InteractionHand.OFF_HAND,
              new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
          target.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
          NeoForge.EVENT_BUS.post(new AttackEntityEvent(owner, target));
          h.assertTrue(
              !state(owner).hasUUID("narotra_doll") && state(owner).hasUUID("nilakan_doll"),
              "Nilakan links on attack while Narotra requires gaze");
          UUID nilakanItem = state(owner).getUUID("passive_nilakan_item");
          owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
          ItemStack nilakanInventory = new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get());
          ThidaItemData.putUUID(nilakanInventory, "thida_passive_instance", nilakanItem);
          owner.getInventory().add(nilakanInventory);
          ItemStack narotra = new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get());
          UUID narotraItem = UUID.randomUUID();
          ThidaItemData.putUUID(narotra, "thida_passive_instance", narotraItem);
          owner.getInventory().add(narotra);
          state(owner).putUUID("narotra_doll", f.player(false).getUUID());
          state(owner).putUUID("passive_narotra_item", narotraItem);
          NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(owner));
          h.assertTrue(
              state(owner).hasUUID("narotra_doll") && state(owner).hasUUID("nilakan_doll"),
              "Hand changes must preserve both marks");
          h.assertTrue(
              state(owner).getUUID("passive_nilakan_item").equals(nilakanItem),
              "Nilakan instance survives hand changes when stored in inventory");
        });
  }

  @GameTest(template = "empty")
  public static void logoutClearsBondsWithoutReward(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true), target = f.player(false);
          state(owner).putUUID("parasite", target.getUUID());
          NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(target));
          h.assertTrue(
              !state(owner).hasUUID("parasite") && !state(owner).getBoolean("parasiteReward"),
              "Disconnect clears bond without healing exploit");
        });
  }

  @GameTest(template = "empty")
  public static void narotraGazeLinksWithoutMutualGaze(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true), target = f.player(false);
          owner.setItemInHand(
              InteractionHand.MAIN_HAND,
              new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get()));
          target.setPos(owner.getX(), owner.getY(), owner.getZ() + 3);
          owner.setYRot(0);
          owner.setXRot(0);
          target.setYRot(0);
          tick(owner);
          h.assertTrue(state(owner).getInt("gaze") == 1, "Narotra gaze attempt records progress");
          owner.setYRot(90);
          tick(owner);
          h.assertTrue(
              !state(owner).hasUUID("gazeTarget") && state(owner).getInt("gaze") == 0,
              "Looking away cancels and resets Narotra gaze progress");
          owner.setYRot(0);
          for (int i = 1; i < ThidaConfig.GAZE_TICKS.get(); i++) tick(owner);
          h.assertTrue(!state(owner).hasUUID("narotra_doll"), "Must wait full ten seconds");
          tick(owner);
          h.assertTrue(state(owner).hasUUID("narotra_doll"), "One-way gaze links Narotra");
          h.assertTrue(
              !target.hasEffect(MobEffects.MOVEMENT_SPEED),
              "Gaze links, it does not auto-cast buffs");
        });
  }

  @GameTest(template = "empty")
  public static void dollMenuCooldownsAndCancellation(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true);
          owner.setItemInHand(
              InteractionHand.MAIN_HAND,
              new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
          ItemStack stack = owner.getMainHandItem();
          h.assertTrue(
              ThidaEvents.use(owner, stack, InteractionHand.MAIN_HAND).getResult()
                  == InteractionResult.PASS,
              "Passive doll has no active ability menu");
          h.assertTrue(
              !state(owner).hasUUID("nilakan_doll"), "Using the doll does not create a link");
        });
  }

  @GameTest(template = "empty")
  public static void narotraSpeedHealsAndCleanseRemovesThirdCurse(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true), target = f.player(false);
          ItemStack doll = new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get());
          owner.setItemInHand(InteractionHand.MAIN_HAND, doll);
          UUID itemId = UUID.randomUUID();
          ThidaItemData.putUUID(doll, "thida_passive_instance", itemId);
          state(owner).putUUID("narotra_doll", target.getUUID());
          state(owner).putUUID("passive_narotra_item", itemId);
          state(owner).putLong("passive_narotra_cleanse_at", 0);
          target.setPos(owner.getX(), owner.getY(), owner.getZ() + 3);
          owner.setYRot(0);
          owner.setXRot(0);
          owner.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
          target.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
          tick(owner);
          h.assertTrue(
              !owner.hasEffect(MobEffects.POISON), "Narotra cleanses Thida, not its linked target");
          h.assertTrue(target.hasEffect(MobEffects.POISON), "Narotra does not cleanse the target");
          near(h, target.getHealth(), 100, "Narotra does not heal the target");
        });
  }

  @GameTest(template = "empty")
  public static void mayaromFollowsCasterAndCooldownStartsOnEnd(GameTestHelper h) {
    run(
        h,
        f -> {
          var owner = f.player(true);
          ThidaBonds.data(owner).putBoolean("unlock4", true);
          use(h, owner, "domain_talisman");
          near(h, ThidaMayarom.remaining(owner), 6000, "Five minute duration");
          near(h, ThidaMayarom.cooldown(owner), 0, "No cooldown until the ultimate ends");
          owner.setPos(owner.getX() + 140, owner.getY(), owner.getZ());
          var hit = incoming(owner, owner.damageSources().generic(), 10);
          NeoForge.EVENT_BUS.post(hit);
          near(h, hit.getAmount(), 3, "Body form follows caster without a radius");
          NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(owner));
          h.assertTrue(ThidaMayarom.active(owner), "Logout retains ultimate");
          NeoForge.EVENT_BUS.post(
              new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(
                  owner, owner.damageSources().generic()));
          h.assertTrue(ThidaMayarom.active(owner), "Death retains ultimate state");
          NeoForge.EVENT_BUS.post(
              new PlayerEvent.PlayerChangedDimensionEvent(
                  owner,
                  net.minecraft.world.level.Level.OVERWORLD,
                  net.minecraft.world.level.Level.NETHER));
          h.assertTrue(ThidaMayarom.active(owner), "Dimension change retains ultimate");
          ThidaMayarom.toggle(owner);
          h.assertTrue(!ThidaMayarom.active(owner), "Second press cancels");
          near(h, ThidaMayarom.cooldown(owner), 24000, "Cancel starts twenty minute cooldown");
          ThidaMayarom.toggle(owner);
          h.assertTrue(!ThidaMayarom.active(owner), "Cooldown cannot be bypassed");
        });
  }

  @GameTest(template = "empty")
  public static void mayaromNarotraBuffAndCursesPreserveManualMark(GameTestHelper h) {
    run(
        h,
        f -> {
          var owner = f.player(true);
          var ally = f.player(false);
          var marked = f.player(false);
          var victim = f.player(false);
          ally.setPos(owner.getX(), owner.getY(), owner.getZ() + 4);
          completeNarotra(owner, ally, ThidaBonds.now(owner.server));
          state(owner).putUUID("nilakan_doll", marked.getUUID());
          ThidaBonds.data(owner).putBoolean("unlock4", true);
          ThidaMayarom.toggle(owner);
          h.assertTrue(
              ThidaPassives.isBondAvailable(owner, "narotra_doll"), "Narotra link available");
          var attack = incoming(victim, victim.damageSources().playerAttack(ally), 10);
          NeoForge.EVENT_BUS.post(attack);
          near(h, attack.getAmount(), 12, "Narotra melee increases twenty percent");
          var defense = incoming(ally, ally.damageSources().generic(), 10);
          NeoForge.EVENT_BUS.post(defense);
          near(h, defense.getAmount(), 8, "Narotra incoming decreases twenty percent");
          h.assertTrue(victim.hurt(victim.damageSources().playerAttack(ally), 2), "Ally hit lands");
          h.assertTrue(
              state(owner).getUUID("nilakan_doll").equals(marked.getUUID()),
              "Manual mark unchanged");
          h.assertTrue(
              victim.hasEffect(ThidaMod.MAYAROM_WEAKNESS)
                  && victim.hasEffect(ThidaMod.MAYAROM_BLEED)
                  && victim.hasEffect(ThidaMod.MAYAROM_VULNERABILITY)
                  && victim.hasEffect(MobEffects.BLINDNESS),
              "All Nilakan spell results applied");
          near(
              h,
              victim.getAttributeValue(Attributes.ATTACK_SPEED),
              3.2,
              "Attack speed decreases twenty percent");
          near(
              h,
              victim.getEffect(ThidaMod.MAYAROM_BLEED).getDuration(),
              100,
              "Bleed lasts five seconds");
          near(
              h,
              victim.getEffect(ThidaMod.MAYAROM_WEAKNESS).getDuration(),
              200,
              "Weakness lasts ten seconds");
          h.assertTrue(
              !ThidaBondData.get(owner.server).occupied(victim.getUUID(), owner.getUUID(), ""),
              "Curse creates no bond");
          var weakHit = incoming(marked, marked.damageSources().playerAttack(victim), 10);
          NeoForge.EVENT_BUS.post(weakHit);
          near(h, weakHit.getAmount(), 8, "Curse reduces outgoing damage twenty percent");
          var vulnerableHit = incoming(victim, victim.damageSources().generic(), 10);
          NeoForge.EVENT_BUS.post(vulnerableHit);
          near(h, vulnerableHit.getAmount(), 12, "Curse increases incoming damage twenty percent");
          long due = ThidaBonds.now(owner.server);
          ThidaBonds.data(victim).putLong("mayaromNextBleed", due);
          victim.invulnerableTime = 0;
          victim.hurt(victim.damageSources().playerAttack(ally), 2);
          near(
              h,
              ThidaBonds.data(victim).getLong("mayaromNextBleed"),
              due,
              "Refreshing curse does not postpone an already due bleed tick");
          ThidaEvents.cleanse(victim);
          h.assertTrue(!victim.hasEffect(ThidaMod.MAYAROM_BLEED), "Curse can be cleansed");
        });
  }

  @GameTest(template = "empty")
  public static void persistentMarksSurviveLogoutAndRejectReplacement(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true),
              target = f.player(false),
              replacement = f.player(false);
          state(owner).putUUID("nilakan_doll", target.getUUID());
          owner.setItemInHand(
              InteractionHand.MAIN_HAND,
              new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
          replacement.hurt(replacement.damageSources().playerAttack(owner), 2);
          h.assertTrue(
              state(owner).getUUID("nilakan_doll").equals(target.getUUID()),
              "Cannot replace linked target without cancellation");
          NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(target));
          tick(owner);
          h.assertTrue(
              state(owner).hasUUID("nilakan_doll"), "Target logout does not erase permanent mark");
          NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(owner));
          h.assertTrue(
              state(owner).hasUUID("nilakan_doll"), "Owner logout preserves permanent mark");
        });
  }

  @GameTest(template = "empty")
  public static void hudOfflineTargetAndWireRoundTrip(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true), target = f.player(false);
          owner.setItemInHand(
              InteractionHand.MAIN_HAND,
              new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
          target.hurt(target.damageSources().playerAttack(owner), 2);
          f.uuidIndex.remove(target.getUUID());
          var data = ThidaEvents.hudSnapshot(owner);
          var link = data.getCompound("nilakan_doll");
          h.assertTrue(
              link.isEmpty(), "Attack without holding the Nilakan doll does not create a link");
          var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
          try {
            buffer.writeNbt(data);
            var decoded = buffer.readNbt();
            h.assertTrue(data.equals(decoded), "HUD NBT survives network encoding");
            ThidaHudState.accept(decoded);
            decoded.putString("player", "modified");
            h.assertTrue(
                ThidaHudState.snapshot().getString("player").equals(owner.getUUID().toString()),
                "Client snapshot is defensively copied");
            ThidaHudState.tick();
            near(h, ThidaHudState.remaining(20), 19, "Client countdown advances between packets");
            near(h, ThidaHudState.remaining(0), 0, "Client countdown never goes negative");
            h.assertTrue(
                ThidaHudState.time(1).equals("0:01") && ThidaHudState.time(12000).equals("10:00"),
                "Clock formatting rounds up and uses minutes");
          } finally {
            buffer.release();
            ThidaHudState.accept(new CompoundTag());
          }
        });
  }

  @GameTest(template = "empty")
  public static void hudDomainAndGazeProgress(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true), target = f.player(false);
          state(owner).putInt("gaze", 120);
          state(owner).putUUID("gazeTarget", target.getUUID());
          ThidaBonds.data(owner).putBoolean("unlock4", true);
          use(h, owner, "domain_talisman");
          var data = ThidaEvents.hudSnapshot(owner);
          near(h, data.getLong("domain"), 6000, "HUD domain active countdown");
          near(
              h,
              data.getCompound("cooldowns").getLong("cd_domain_talisman"),
              0,
              "HUD domain readiness deadline");
          near(h, data.getInt("gaze"), 120, "HUD gaze progress");
          h.assertTrue(
              data.getString("gazeName").equals(target.getScoreboardName())
                  && data.getBoolean("inDomain"),
              "HUD target gaze and inside-domain status");
          owner.setPos(owner.getX() + 40, owner.getY(), owner.getZ());
          h.assertTrue(
              ThidaEvents.hudSnapshot(owner).getBoolean("inDomain"),
              "HUD body form remains active after moving");
        });
  }

  @GameTest(template = "empty")
  public static void debugResetRequiresOperatorAndPreservesPowers(GameTestHelper h) {
    run(
        h,
        f -> {
          FakePlayer owner = f.player(true), target = f.player(false);
          state(owner).putUUID("nilakan_doll", target.getUUID());
          state(owner).putUUID("heart", target.getUUID());
          state(owner).putBoolean("heartUsed", true);
          long now = owner.level().getGameTime();
          state(owner).putLong("domain", now + 6000);
          state(owner).putLong("cd_domain_talisman", now + 30000);
          state(owner).putLong("cd_effect_nilakan_doll_0", now + 600);
          state(owner).putLong("hud_effect_nilakan_doll_0", now + 400);
          String cmd = "thida resetcooldown " + owner.getScoreboardName();
          var dispatcher = owner.server.getCommands().getDispatcher();
          boolean denied = false;
          try {
            dispatcher.execute(
                cmd, owner.createCommandSourceStack().withPermission(0).withSuppressedOutput());
          } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            denied = true;
          }
          h.assertTrue(
              denied && state(owner).getLong("cd_domain_talisman") == now + 30000,
              "Non-operator reset denied without mutation");
          try {
            dispatcher.execute(
                cmd, owner.createCommandSourceStack().withPermission(2).withSuppressedOutput());
            dispatcher.execute(
                "thida debug status " + owner.getScoreboardName(),
                owner.createCommandSourceStack().withPermission(2).withSuppressedOutput());
          } catch (Exception e) {
            throw new AssertionError(e);
          }
          h.assertTrue(
              !state(owner).contains("cd_domain_talisman")
                  && !state(owner).contains("cd_effect_nilakan_doll_0"),
              "Reset removes both cooldown types");
          h.assertTrue(
              state(owner).hasUUID("nilakan_doll")
                  && state(owner).hasUUID("heart")
                  && state(owner).getBoolean("heartUsed"),
              "Reset preserves links and consumed protection");
          near(h, state(owner).getLong("domain"), now + 6000, "Reset preserves active domain");
          near(
              h,
              state(owner).getLong("hud_effect_nilakan_doll_0"),
              now + 400,
              "Reset preserves active effect timer");
        });
  }

  @GameTest(template = "empty")
  public static void domainDisplaySnapshotsExpireAndClose(GameTestHelper h) {
    ThidaDomainState.clear();
    try {
      var first = new CompoundTag();
      first.putUUID("owner", UUID.randomUUID());
      first.putBoolean("active", true);
      first.putLong("age", 100);
      first.putString("dimension", "minecraft:overworld");
      first.putDouble("radius", 30);
      ThidaDomainState.accept(first);
      var second = first.copy();
      second.putUUID("owner", UUID.randomUUID());
      ThidaDomainState.accept(second);
      h.assertTrue(ThidaDomainState.views().size() == 2, "Overlapping domains remain separate");
      ThidaDomainState.tick();
      near(
          h,
          ThidaDomainState.age(ThidaDomainState.views().get(0), .5f),
          101.5,
          "Animation interpolates between snapshots");
      first.putBoolean("active", false);
      ThidaDomainState.accept(first);
      h.assertTrue(
          ThidaDomainState.views().size() == 1, "End packet removes only its owner's domain");
      for (int i = 0; i < 31; i++) ThidaDomainState.tick();
      h.assertTrue(
          ThidaDomainState.views().isEmpty(),
          "Out-of-range or disconnected domain expires without more packets");
      h.succeed();
    } finally {
      ThidaDomainState.clear();
    }
  }

  private static LivingIncomingDamageEvent incoming(
      net.minecraft.world.entity.LivingEntity entity, DamageSource source, float amount) {
    return new LivingIncomingDamageEvent(entity, new DamageContainer(source, amount));
  }

  private static LivingDamageEvent.Pre damageEvent(
      net.minecraft.world.entity.LivingEntity entity, DamageSource source, float amount) {
    return new LivingDamageEvent.Pre(entity, new DamageContainer(source, amount));
  }

  private static void selectOrigin(ServerPlayer player, String id) {
    var layerKey =
        net.minecraft.resources.ResourceKey
            .<com.iafenvoy.origins.data.layer.Layer>createRegistryKey(
                ResourceLocation.fromNamespaceAndPath("origins", "layer"));
    var originKey =
        net.minecraft.resources.ResourceKey
            .<com.iafenvoy.origins.data.origin.Origin>createRegistryKey(
                ResourceLocation.fromNamespaceAndPath("origins", "origin"));
    var layer =
        player
            .registryAccess()
            .registryOrThrow(layerKey)
            .getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(
                    layerKey, ResourceLocation.fromNamespaceAndPath("origins", "origin")));
    var origin =
        player
            .registryAccess()
            .registryOrThrow(originKey)
            .getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(originKey, ResourceLocation.parse(id)));
    com.iafenvoy.origins.attachment.OriginDataHolder.get(player).setOrigin(layer, origin);
  }

  private static void tick(ServerPlayer p) {
    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
  }

  private static CompoundTag state(ServerPlayer p) {
    if (!p.getPersistentData().contains("thida_state"))
      p.getPersistentData().put("thida_state", new CompoundTag());
    return p.getPersistentData().getCompound("thida_state");
  }

  private static void use(GameTestHelper h, FakePlayer p, String id) {
    ItemStack stack = new ItemStack(ThidaMod.ABILITIES.get(id).get());
    p.setItemInHand(InteractionHand.MAIN_HAND, stack);
    h.assertTrue(
        stack.use(p.level(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(),
        "Ability must activate: " + id);
  }

  private static void near(GameTestHelper h, double actual, double expected, String label) {
    h.assertTrue(
        Math.abs(actual - expected) < .001, label + ": expected " + expected + ", got " + actual);
  }

  private static void run(GameTestHelper h, Consumer<Fixture> test) {
    try (Fixture fixture = new Fixture(h)) {
      test.accept(fixture);
    } catch (RuntimeException | Error e) {
      e.printStackTrace();
      throw e;
    }
    h.succeed();
  }

  /**
   * Stock FakePlayer is unconditionally invulnerable. Keep its no-op packet handler, but restore
   * combat for actual LivingEntity hurt / Forge / vanilla totem integration.
   */
  private static final class CombatPlayer extends FakePlayer {
    CombatPlayer(ServerLevel level, GameProfile profile) {
      super(level, profile);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
      return false;
    }

    @Override
    public boolean canHarmPlayer(Player other) {
      return true;
    }
  }

  static final class Fixture implements AutoCloseable {
    private final GameTestHelper helper;
    private final List<FakePlayer> players = new ArrayList<>();
    private final Map<UUID, ServerPlayer> uuidIndex;
    private final List<ServerPlayer> serverPlayers;
    private final Map<Object, FakePlayer> factoryCache;

    @SuppressWarnings("unchecked")
    Fixture(GameTestHelper helper) {
      this.helper = helper;
      uuidIndex =
          ObfuscationReflectionHelper.getPrivateValue(
              PlayerList.class, helper.getLevel().getServer().getPlayerList(), "playersByUUID");
      serverPlayers =
          ObfuscationReflectionHelper.getPrivateValue(
              PlayerList.class, helper.getLevel().getServer().getPlayerList(), "players");
      try {
        Field field = FakePlayerFactory.class.getDeclaredField("fakePlayers");
        field.setAccessible(true);
        factoryCache = (Map<Object, FakePlayer>) field.get(null);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("NeoForge 1.21.1 fake-player fixture access", e);
      }
    }

    FakePlayer player(boolean origin) {
      ServerLevel level = helper.getLevel();
      UUID uuid = UUID.randomUUID();
      GameProfile profile = new GameProfile(uuid, "thida_" + uuid.toString().substring(0, 8));
      FakePlayer original = FakePlayerFactory.get(level, profile);
      // Replace only this unique factory entry; never unload another test's level/cache.
      FakePlayer combat = new CombatPlayer(level, profile);
      factoryCache.replaceAll((key, value) -> value == original ? combat : value);
      FakePlayer p = FakePlayerFactory.get(level, profile);
      players.add(p);
      helper.assertTrue(p == combat, "Factory must resolve combat fixture");
      ObfuscationReflectionHelper.setPrivateValue(
          ServerPlayer.class, p, 0, "spawnInvulnerableTime");
      p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
      p.getAttribute(Attributes.MOVEMENT_SPEED)
          .setBaseValue(Attributes.MOVEMENT_SPEED.value().getDefaultValue());
      p.getAttribute(Attributes.ATTACK_DAMAGE)
          .setBaseValue(Attributes.ATTACK_DAMAGE.value().getDefaultValue());
      p.setHealth(100);
      BlockPos pos = helper.absolutePos(BlockPos.ZERO);
      // 1.21.1 places test templates near the world's floor. Keep gaze rays above surrounding
      // terrain.
      p.setPos(pos.getX() + .5, pos.getY() + 12, pos.getZ() + .5);
      selectOrigin(p, origin ? "thida:thida" : "origins:human");
      serverPlayers.add(p);
      uuidIndex.put(uuid, p);
      helper.assertTrue(
          level.getServer().getPlayerList().getPlayer(uuid) == p,
          "UUID resolution must find loaded fixture");
      return p;
    }

    @Override
    public void close() {
      for (FakePlayer p : players) {
        serverPlayers.remove(p);
        uuidIndex.remove(p.getUUID(), p);
        factoryCache.entrySet().removeIf(entry -> entry.getValue() == p);
        p.getPersistentData().remove("thida_state");
        p.removeTag("thida_origin");
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.discard();
      }
    }
  }
}
