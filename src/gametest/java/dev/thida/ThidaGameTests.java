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
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

/** Synchronous tests: no fixture survives the test callback, even after an assertion fails. */
@GameTestHolder("thida")
@PrefixGameTestTemplate(false)
public final class ThidaGameTests {
    @GameTest(template = "empty")
    public static void retiredPowersAreInertAndUncraftable(GameTestHelper h) {
        run(h, f -> {
            FakePlayer p=f.player(true);
            p.setHealth(40);
            p.addEffect(new MobEffectInstance(MobEffects.POISON,200));
            for(var entry:ThidaMod.RETIRED.entrySet()) {
                ItemStack stack=new ItemStack(entry.getValue().get());
                p.setItemInHand(InteractionHand.MAIN_HAND,stack);
                h.assertTrue(stack.use(p.level(),p,InteractionHand.MAIN_HAND).getResult()==InteractionResult.PASS,"Retired item cannot activate: "+entry.getKey());
                h.assertTrue(ThidaEvents.use(p,stack,InteractionHand.MAIN_HAND).getResult()==InteractionResult.PASS,"Server rejects retired ability even through direct hook");
                h.assertTrue(p.server.getRecipeManager().byKey(new ResourceLocation("thida",entry.getKey())).isEmpty(),"Retired recipe removed");
                h.assertTrue(!ThidaMod.ABILITIES.containsKey(entry.getKey()),"Retired ability excluded from kit/creative");
            }
            near(h,p.getHealth(),40,"Retired remedy cannot heal");
            h.assertTrue(p.hasEffect(MobEffects.POISON)&&!p.hasEffect(MobEffects.ABSORPTION)
                    &&!p.hasEffect(MobEffects.INVISIBILITY)&&!p.hasEffect(MobEffects.HERO_OF_THE_VILLAGE),"No retired effects run");
        });
    }
    @GameTest(template = "empty")
    public static void nonOriginRejects(GameTestHelper h) {
        run(h, f -> {
            FakePlayer p = f.player(false);
            p.setHealth(40);
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            for (String id : ThidaMod.ABILITIES.keySet()) {
                ItemStack stack = new ItemStack(ThidaMod.ABILITIES.get(id).get());
                p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(stack.use(p.level(), p, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS,
                        "Non-origin must not activate " + id);
            }
            near(h, p.getHealth(), 40, "Non-origin must not heal");
            h.assertTrue(p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.ABSORPTION)
                    && !p.hasEffect(MobEffects.HERO_OF_THE_VILLAGE), "Non-origin effects must remain unchanged");
            h.assertTrue(!p.getPersistentData().contains("thida_state"), "Rejected use must not create ability state");
        });
    }

    @GameTest(template = "empty")
    public static void nilakanLinksOnAttackThenPausesAndResumes(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            ItemStack doll = new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, doll);
            target.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
            float targetHealth = target.getHealth();
            AttackEntityEvent attack = new AttackEntityEvent(owner, target);
            MinecraftForge.EVENT_BUS.post(attack);
            h.assertTrue(state(owner).hasUUID("nilakan_doll")
                    && state(owner).getUUID("nilakan_doll").equals(target.getUUID()), "A direct attack attempt links Nilakan");
            near(h, target.getHealth(), targetHealth, "Linking does not require damage to be dealt");

            tick(owner);
            near(h, owner.getAttributeValue(Attributes.MOVEMENT_SPEED), Attributes.MOVEMENT_SPEED.getDefaultValue() * 1.05,
                    "Active Nilakan grants 5% movement speed");
            near(h, owner.getAttributeValue(Attributes.ATTACK_DAMAGE), Attributes.ATTACK_DAMAGE.getDefaultValue() * 1.05,
                    "Active Nilakan grants 5% melee damage");

            target.setPos(owner.getX() + 201, owner.getY(), owner.getZ());
            tick(owner);
            h.assertTrue(state(owner).hasUUID("nilakan_doll"), "Out of range pauses without deleting the bond");
            near(h, owner.getAttributeValue(Attributes.MOVEMENT_SPEED), Attributes.MOVEMENT_SPEED.getDefaultValue(),
                    "Paused Nilakan removes its movement bonus");

            target.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
            tick(owner);
            near(h, owner.getAttributeValue(Attributes.MOVEMENT_SPEED), Attributes.MOVEMENT_SPEED.getDefaultValue() * 1.05,
                    "Returning to range resumes the bonus");

            f.uuidIndex.remove(target.getUUID());
            tick(owner);
            h.assertTrue(state(owner).hasUUID("nilakan_doll"), "Target disconnect pauses without deleting the bond");
            near(h, owner.getAttributeValue(Attributes.MOVEMENT_SPEED), Attributes.MOVEMENT_SPEED.getDefaultValue(),
                    "Disconnected target pauses Nilakan bonuses");
            f.uuidIndex.put(target.getUUID(), target);
            tick(owner);
            near(h, owner.getAttributeValue(Attributes.MOVEMENT_SPEED), Attributes.MOVEMENT_SPEED.getDefaultValue() * 1.05,
                    "Returning target resumes the bond");

            ThidaPassives.onOwnerUnavailable(owner);
            h.assertTrue(state(owner).hasUUID("nilakan_doll"), "Owner disconnect preserves its bond");
            tick(owner);
            near(h, owner.getAttributeValue(Attributes.MOVEMENT_SPEED), Attributes.MOVEMENT_SPEED.getDefaultValue() * 1.05,
                    "Owner reconnect resumes the bond");

            owner.removeTag("thida_origin");
            ThidaPassives.originLost(owner);
            h.assertTrue(!state(owner).hasUUID("nilakan_doll"), "Losing the Thida Origin ends the bond");
            near(h, owner.getAttributeValue(Attributes.MOVEMENT_SPEED), Attributes.MOVEMENT_SPEED.getDefaultValue(),
                    "Origin loss removes the passive bonus");

            owner.addTag("thida_origin");
            MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(owner, target));
            h.assertTrue(state(owner).hasUUID("nilakan_doll"), "Thida can establish another Nilakan bond");
            ThidaPassives.onToss(owner, owner.getMainHandItem());
            h.assertTrue(!state(owner).hasUUID("nilakan_doll"), "Tossing the linked doll ends its bond immediately");
        });
    }

    @GameTest(template = "empty")
    public static void passiveCleansesWaitForDebuffsAndTheirIntervals(GameTestHelper h) {
        run(h, f -> {
            FakePlayer narotraOwner = f.player(true), narotraTarget = f.player(false);
            ItemStack narotra = new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get());
            UUID narotraItemId = UUID.randomUUID();
            narotra.getOrCreateTag().putUUID("thida_passive_instance", narotraItemId);
            narotraOwner.setItemInHand(InteractionHand.MAIN_HAND, narotra);
            narotraTarget.setPos(narotraOwner.getX(), narotraOwner.getY(), narotraOwner.getZ() + 5);
            CompoundTag narotraState = state(narotraOwner);
            narotraState.putUUID("gazeTarget", narotraTarget.getUUID());
            narotraState.putUUID("passive_gaze_item", narotraItemId);
            narotraState.putInt("gaze", 199);
            ThidaPassives.tickAt(narotraOwner, narotraState, 1000);
            h.assertTrue(narotraState.getUUID("narotra_doll").equals(narotraTarget.getUUID()), "Narotra completes the 10-second gaze threshold");
            h.assertTrue(!narotraOwner.hasEffect(MobEffects.POISON), "No debuff means no cleanse is needed");

            narotraOwner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            ThidaPassives.tickAt(narotraOwner, narotraState, 1001);
            h.assertTrue(!narotraOwner.hasEffect(MobEffects.POISON), "Narotra waits for and immediately cleanses a debuff");
            narotraOwner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            ThidaPassives.tickAt(narotraOwner, narotraState, 1001 + 24000 - 1);
            h.assertTrue(narotraOwner.hasEffect(MobEffects.POISON), "Narotra respects its 20-minute cleanse interval");
            ThidaPassives.tickAt(narotraOwner, narotraState, 1001 + 24000);
            h.assertTrue(!narotraOwner.hasEffect(MobEffects.POISON), "Narotra cleanses when the interval expires and a debuff exists");

            FakePlayer nilakanOwner = f.player(true), nilakanTarget = f.player(false);
            ItemStack nilakan = new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get());
            UUID nilakanItemId = UUID.randomUUID();
            nilakan.getOrCreateTag().putUUID("thida_passive_instance", nilakanItemId);
            nilakanOwner.setItemInHand(InteractionHand.MAIN_HAND, nilakan);
            nilakanTarget.setPos(nilakanOwner.getX() + 5, nilakanOwner.getY(), nilakanOwner.getZ());
            CompoundTag nilakanState = state(nilakanOwner);
            nilakanState.putUUID("nilakan_doll", nilakanTarget.getUUID());
            nilakanState.putUUID("passive_nilakan_item", nilakanItemId);
            nilakanState.putBoolean("passive_nilakan_active", true);
            nilakanState.putLong("passive_nilakan_cleanse_at", 2000);
            nilakanOwner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            ThidaPassives.tickAt(nilakanOwner, nilakanState, 2000);
            h.assertTrue(!nilakanOwner.hasEffect(MobEffects.POISON), "Nilakan performs its first cleanse as soon as a debuff exists");
            nilakanOwner.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            ThidaPassives.tickAt(nilakanOwner, nilakanState, 2000 + 12000 - 1);
            h.assertTrue(nilakanOwner.hasEffect(MobEffects.POISON), "Nilakan respects its 10-minute cleanse interval");
            ThidaPassives.tickAt(nilakanOwner, nilakanState, 2000 + 12000);
            h.assertTrue(!nilakanOwner.hasEffect(MobEffects.POISON), "Nilakan cleanses when its interval expires");
        });
    }

    @GameTest(template = "empty")
    public static void covenantBlocksRecipientAttackAndHealing(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false), victim = f.player(false);
            state(owner).putUUID("heart", target.getUUID());
            target.setHealth(40);
            LivingAttackEvent attack = new LivingAttackEvent(victim, victim.damageSources().playerAttack(target), 4);
            MinecraftForge.EVENT_BUS.post(attack);
            h.assertTrue(attack.isCanceled(), "Covenant recipient's attack must be canceled on Forge bus");
            target.heal(5);
            near(h, target.getHealth(), 40, "Ordinary healing must be blocked");
            ThidaEvents.addonHeal(target, target, 5);
            near(h, target.getHealth(), 40, "Recipient's own addon healing must be blocked");
            h.assertTrue(state(owner).hasUUID("heart"), "Self healing must retain covenant");
        });
    }

    @GameTest(template = "empty")
    public static void ownerDamageBreaksCovenantAndBleeds(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            state(owner).putUUID("heart", target.getUUID());
            state(owner).putBoolean("heartUsed", true);
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(owner, owner.damageSources().generic(), 4));
            h.assertTrue(!state(owner).hasUUID("heart") && !state(owner).contains("heartUsed"), "Owner damage must clear covenant");
            h.assertTrue(state(owner).getLong("bleed") > owner.level().getGameTime(), "Owner damage must start bleed");
        });
    }

    @GameTest(template = "empty")
    public static void externalAddonHealingReleasesCovenant(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false), healer = f.player(true);
            state(owner).putUUID("heart", target.getUUID());
            state(owner).putBoolean("heartUsed", true);
            target.setHealth(40);
            ThidaEvents.addonHeal(healer, target, 6);
            near(h, target.getHealth(), 46, "External addon healing");
            h.assertTrue(!state(owner).contains("heart") && !state(owner).contains("heartUsed"), "External heal must release covenant");
            target.heal(2);
            near(h, target.getHealth(), 48, "Ordinary healing must resume");
        });
    }

    @GameTest(template = "empty")
    public static void vanillaExternalInstantHealthReleasesCovenant(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), recipient = f.player(false), caster = f.player(false);
            state(owner).putUUID("heart", recipient.getUUID());
            state(owner).putBoolean("heartUsed", true);
            recipient.setHealth(40);
            MobEffects.HEAL.applyInstantenousEffect(caster, caster, recipient, 0, 1.0);
            near(h, recipient.getHealth(), 44, "External vanilla instant health");
            h.assertTrue(!state(owner).contains("heart") && !state(owner).contains("heartUsed"), "External vanilla instant health must release covenant");
        });
    }

    @GameTest(template = "empty")
    public static void vanillaSelfInstantHealthKeepsCovenant(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), recipient = f.player(false);
            state(owner).putUUID("heart", recipient.getUUID());
            recipient.setHealth(40);
            MobEffects.HEAL.applyInstantenousEffect(recipient, recipient, recipient, 0, 1.0);
            near(h, recipient.getHealth(), 40, "Self vanilla instant health must be blocked");
            h.assertTrue(state(owner).hasUUID("heart"), "Self vanilla instant health must retain covenant");
        });
    }

    @GameTest(template = "empty")
    public static void vanillaExternalRegenerationReleasesCovenant(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), recipient = f.player(false), caster = f.player(false);
            state(owner).putUUID("heart", recipient.getUUID());
            state(owner).putBoolean("heartUsed", true);
            recipient.setHealth(40);
            recipient.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200), caster);
            MobEffects.REGENERATION.applyEffectTick(recipient, 0);
            near(h, recipient.getHealth(), 41, "External vanilla regeneration tick");
            h.assertTrue(!state(owner).contains("heart") && !state(owner).contains("heartUsed"), "External vanilla regeneration must release covenant");
        });
    }

    @GameTest(template = "empty")
    public static void vanillaSelfRegenerationKeepsCovenant(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), recipient = f.player(false);
            state(owner).putUUID("heart", recipient.getUUID());
            recipient.setHealth(40);
            recipient.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200), recipient);
            MobEffects.REGENERATION.applyEffectTick(recipient, 0);
            near(h, recipient.getHealth(), 40, "Self vanilla regeneration must be blocked");
            h.assertTrue(state(owner).hasUUID("heart"), "Self vanilla regeneration must retain covenant");
        });
    }

    @GameTest(template = "empty")
    public static void parasiteConservesActualDamage(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), partner = f.player(false);
            state(owner).putUUID("parasite", partner.getUUID());
            h.assertTrue(owner.hurt(owner.damageSources().generic(), 10), "Actual damage must be accepted");
            double share = ThidaConfig.SHARE_FRACTION.get();
            near(h, owner.getHealth(), 100 - 10 * (1 - share), "Owner damage share");
            near(h, partner.getHealth(), 100 - 10 * share, "Partner damage share");
            near(h, 200 - owner.getHealth() - partner.getHealth(), 10, "Total damage must be conserved");
            owner.invulnerableTime = partner.invulnerableTime = 0;
            h.assertTrue(partner.hurt(partner.damageSources().generic(), 6), "Reverse damage must be accepted");
            near(h, 200 - owner.getHealth() - partner.getHealth(), 16, "Reverse damage must also be conserved");
        });
    }

    @GameTest(template = "empty")
    public static void parasiteSharedDamageUsesTotem(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), partner = f.player(false);
            h.assertTrue(ThidaConfig.SHARE_FRACTION.get() > 0, "Totem test requires nonzero parasite sharing");
            partner.setHealth(1);
            partner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            state(owner).putUUID("parasite", partner.getUUID());
            owner.hurt(owner.damageSources().generic(), (float) (2 / ThidaConfig.SHARE_FRACTION.get()));
            h.assertTrue(partner.isAlive(), "Shared lethal damage must allow totem survival");
            h.assertTrue(partner.getOffhandItem().isEmpty(), "Shared damage must consume the totem");
            near(h, partner.getHealth(), 1, "Totem survival health");
            h.assertTrue(partner.hasEffect(MobEffects.REGENERATION), "Vanilla totem effects must run");
        });
    }

    @GameTest(template = "empty")
    public static void domainReducesDamageToThirtyPercent(GameTestHelper h) {
        run(h, f -> {
            FakePlayer p = f.player(true);
            near(h, ThidaConfig.DOMAIN_REDUCTION.get(), 0.7, "Test requires default domain reduction");
            use(h, p, "domain_talisman");
            LivingHurtEvent event = new LivingHurtEvent(p, p.damageSources().generic(), 10);
            MinecraftForge.EVENT_BUS.post(event);
            near(h, event.getAmount(), 3, "Domain must multiply incoming damage by .3");
        });
    }

    @GameTest(template = "empty")
    public static void nilakanBothModes(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
            target.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
            MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(owner, target));
            h.assertTrue(state(owner).hasUUID("nilakan_doll"), "Nilakan links on a direct attack attempt");
            tick(owner);
            near(h, owner.getAttributeValue(Attributes.MOVEMENT_SPEED), Attributes.MOVEMENT_SPEED.getDefaultValue() * 1.05,
                    "Nilakan grants 5% movement speed");
            near(h, owner.getAttributeValue(Attributes.ATTACK_DAMAGE), Attributes.ATTACK_DAMAGE.getDefaultValue() * 1.05,
                    "Nilakan grants 5% melee damage");
            h.assertTrue(!target.hasEffect(MobEffects.BLINDNESS) && !target.hasEffect(MobEffects.WEAKNESS), "Nilakan link does not cast active curses");
        });
    }

    @GameTest(template = "empty")
    public static void registryOriginResourceAndKitCommand(GameTestHelper h) {
        run(h, f -> {
            FakePlayer p = f.player(false);
            h.assertTrue(ThidaMod.ABILITIES.size() == 5, "Only the five current abilities register as active");
            ThidaMod.ABILITIES.forEach((id, item) -> h.assertTrue(item.isPresent()
                    && ForgeRegistries.ITEMS.getValue(new ResourceLocation("thida", id)) == item.get(), "Missing item " + id));
            var server = p.server;
            try (var reader = server.getResourceManager().getResource(new ResourceLocation("thida", "origins/thida.json"))
                    .orElseThrow().openAsReader()) {
                var origin = JsonParser.parseReader(reader).getAsJsonObject();
                h.assertTrue(origin.getAsJsonArray("powers").size() > 0, "Origin must declare powers");
                for (var power : origin.getAsJsonArray("powers")) {
                    ResourceLocation id = new ResourceLocation(power.getAsString());
                    h.assertTrue(server.getResourceManager().getResource(new ResourceLocation(id.getNamespace(), "powers/" + id.getPath() + ".json")).isPresent(), "Missing power " + id);
                }
            } catch (Exception e) { throw new AssertionError("Origin resource must load", e); }
            try {
                var source = server.createCommandSourceStack().withSuppressedOutput();
                server.getCommands().getDispatcher().execute("origin set " + p.getScoreboardName() + " origins:origin thida:thida", source);
                h.assertTrue(ThidaEvents.isThida(p), "Selecting actual Origin must run gained callback");
                server.getCommands().getDispatcher().execute("origin set " + p.getScoreboardName() + " origins:origin origins:human", source);
                h.assertTrue(!ThidaEvents.isThida(p), "Changing actual Origin must revoke callback tag");
                server.getCommands().getDispatcher().execute("origin set " + p.getScoreboardName() + " origins:origin thida:thida", source);
                int result = server.getCommands().getDispatcher().execute("thida kit", p.createCommandSourceStack().withSuppressedOutput());
                h.assertTrue(result == 1, "Kit command must succeed");
                ThidaMod.ABILITIES.forEach((id, item) -> h.assertTrue(p.getInventory().contains(new ItemStack(item.get())), "Kit missing " + id));
            } catch (Exception e) { throw new AssertionError("Kit command must execute", e); }
        });
    }

    @GameTest(template = "empty")
    public static void covenantSavesOnceAndExpires(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            state(owner).putUUID("heart", target.getUUID());
            target.setHealth(10);
            target.hurt(target.damageSources().generic(), 100);
            near(h, target.getHealth(), 1, "Lethal damage must leave one HP");
            h.assertTrue(state(owner).getBoolean("heartUsed"), "Covenant save must be consumed");
            h.assertTrue(state(owner).hasUUID("heart"), "Saving must retain recipient restrictions");
            near(h, state(target).getLong("invulnerable") - target.level().getGameTime(), 100, "Protection must last exactly five seconds");
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().generic(), 100);
            near(h, target.getHealth(), 1, "Protected damage must be canceled");
            state(target).putLong("invulnerable", 0);
            LivingDamageEvent after = new LivingDamageEvent(target, target.damageSources().generic(), 2);
            MinecraftForge.EVENT_BUS.post(after);
            near(h, after.getAmount(), 2, "Consumed covenant cannot save a second time");
        });
    }

    @GameTest(template = "empty")
    public static void dualDollsPersistAcrossHandSwap(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
            target.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
            MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(owner, target));
            h.assertTrue(!state(owner).hasUUID("narotra_doll") && state(owner).hasUUID("nilakan_doll"), "Nilakan links on attack while Narotra requires gaze");
            UUID nilakanItem = state(owner).getUUID("passive_nilakan_item");
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ItemStack nilakanInventory = new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get());
            nilakanInventory.getOrCreateTag().putUUID("thida_passive_instance", nilakanItem);
            owner.getInventory().add(nilakanInventory);
            ItemStack narotra = new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get());
            UUID narotraItem = UUID.randomUUID(); narotra.getOrCreateTag().putUUID("thida_passive_instance", narotraItem);
            owner.getInventory().add(narotra);
            state(owner).putUUID("narotra_doll", target.getUUID());
            state(owner).putUUID("passive_narotra_item", narotraItem);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
            h.assertTrue(state(owner).hasUUID("narotra_doll") && state(owner).hasUUID("nilakan_doll"), "Hand changes must preserve both marks");
            h.assertTrue(state(owner).getUUID("passive_nilakan_item").equals(nilakanItem), "Nilakan instance survives hand changes when stored in inventory");
        });
    }

    @GameTest(template = "empty")
    public static void parasiteRequiresSummonBeforeHit(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            owner.setHealth(20); target.setHealth(20);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get("parasite_doll").get()));
            target.hurt(target.damageSources().playerAttack(owner), 2);
            h.assertTrue(!state(owner).hasUUID("parasite") && !state(owner).getBoolean("parasiteReward"), "Low combined HP must not create or reward parasite");
            near(h, owner.getHealth(), 20, "Rejected parasite must not heal owner");
            use(h, owner, "parasite_doll");
            h.assertTrue(state(owner).getBoolean("parasiteArmed"), "Using skill summons and arms parasite");
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().playerAttack(owner), 2);
            h.assertTrue(state(owner).getBoolean("parasiteReward"), "Low caster health may break after shared damage");
            h.assertTrue(state(owner).getLong("cd_parasite_doll") > owner.level().getGameTime(), "Break starts cooldown");
        });
    }

    @GameTest(template = "empty")
    public static void parasiteBreakRewardsOnlyOnce(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            owner.setHealth(30); target.setHealth(30);
            state(owner).putUUID("parasite", target.getUUID());
            owner.hurt(owner.damageSources().generic(), 12);
            h.assertTrue(!state(owner).hasUUID("parasite"), "Bond breaks below 25 caster HP");
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
            near(h, owner.getHealth(), 74, "Owner must gain fifty HP after pending damage");
            near(h, target.getHealth(), 24, "Partner keeps its damage share");
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
            near(h, owner.getHealth(), 74, "Reward cannot repeat next tick");
            h.assertTrue(target.hasEffect(MobEffects.DAMAGE_RESISTANCE) && target.hasEffect(MobEffects.MOVEMENT_SPEED), "Partner receives escape buffs");
        });
    }

    @GameTest(template = "empty")
    public static void logoutClearsBondsWithoutReward(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            state(owner).putUUID("parasite", target.getUUID());
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(target));
            h.assertTrue(!state(owner).hasUUID("parasite") && !state(owner).getBoolean("parasiteReward"), "Disconnect clears bond without healing exploit");
        });
    }

    @GameTest(template = "empty")
    public static void narotraGazeLinksWithoutMutualGaze(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get()));
            target.setPos(owner.getX(), owner.getY(), owner.getZ() + 3);
            owner.setYRot(0); owner.setXRot(0); target.setYRot(0);
            tick(owner);
            h.assertTrue(state(owner).getInt("gaze") == 1, "Narotra gaze attempt records progress");
            owner.setYRot(90);
            tick(owner);
            h.assertTrue(!state(owner).hasUUID("gazeTarget") && state(owner).getInt("gaze") == 0,
                    "Looking away cancels and resets Narotra gaze progress");
            owner.setYRot(0);
            for (int i = 1; i < ThidaConfig.GAZE_TICKS.get(); i++) tick(owner);
            h.assertTrue(!state(owner).hasUUID("narotra_doll"), "Must wait full ten seconds");
            tick(owner);
            h.assertTrue(state(owner).hasUUID("narotra_doll"), "One-way gaze links Narotra");
            h.assertTrue(!target.hasEffect(MobEffects.MOVEMENT_SPEED), "Gaze links, it does not auto-cast buffs");
        });
    }

    @GameTest(template = "empty")
    public static void dollMenuCooldownsAndCancellation(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
            ItemStack stack = owner.getMainHandItem();
            h.assertTrue(ThidaEvents.use(owner, stack, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS,
                    "Passive doll has no active ability menu");
            h.assertTrue(!state(owner).hasUUID("nilakan_doll"), "Using the doll does not create a link");
        });
    }

    @GameTest(template = "empty")
    public static void narotraSpeedHealsAndCleanseRemovesThirdCurse(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            ItemStack doll = new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, doll);
            UUID itemId = UUID.randomUUID(); doll.getOrCreateTag().putUUID("thida_passive_instance", itemId);
            state(owner).putUUID("narotra_doll", target.getUUID());
            state(owner).putUUID("passive_narotra_item", itemId);
            state(owner).putLong("passive_narotra_cleanse_at", 0);
            target.setPos(owner.getX(), owner.getY(), owner.getZ() + 3);
            owner.setYRot(0); owner.setXRot(0);
            owner.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            tick(owner);
            h.assertTrue(!owner.hasEffect(MobEffects.POISON), "Narotra cleanses Thida, not its linked target");
            h.assertTrue(target.hasEffect(MobEffects.POISON), "Narotra does not cleanse the target");
            near(h, target.getHealth(), 100, "Narotra does not heal the target");
        });
    }

    @GameTest(template = "empty")
    public static void covenantUnlimitedRangeAndDomainAllowed(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            state(owner).putUUID("heart", target.getUUID());
            state(owner).putUUID("nilakan_doll", target.getUUID());
            target.setPos(owner.getX() + 1000, owner.getY(), owner.getZ());
            tick(owner);
            h.assertTrue(state(owner).hasUUID("heart"), "Covenant must survive beyond old range");
            use(h, owner, "domain_talisman");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
            ThidaEvents.dollAction(owner, 1); ThidaEvents.dollAction(owner, 3);
            h.assertTrue(!target.hasEffect(MobEffects.BLINDNESS), "Domain does not bypass covenant doll restriction");
            ThidaEvents.addonHeal(owner, target, 1);
            near(h, state(owner).getLong("cd_heart_doll") - owner.level().getGameTime(), 12000, "External heal starts covenant cooldown");
        });
    }

    @GameTest(template = "empty")
    public static void domainStationaryAndCooldownAfterExpiry(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true);
            use(h, owner, "domain_talisman");
            long time = owner.level().getGameTime();
            near(h, state(owner).getLong("domain") - time, 6000, "Domain lasts five minutes");
            h.assertTrue(state(owner).getBoolean("domainFx"), "Cast starts domain visual lifecycle");
            for (long age : new long[]{0, 20, 56, 60, 80}) {
                state(owner).putLong("domainStarted", time - age);
                tick(owner);
            }
            near(h, state(owner).getLong("cd_domain_talisman") - time, 30000, "Next cast is twenty-five minutes later");
            owner.setPos(owner.getX() + 40, owner.getY(), owner.getZ());
            LivingHurtEvent hit = new LivingHurtEvent(owner, owner.damageSources().generic(), 10);
            MinecraftForge.EVENT_BUS.post(hit);
            near(h, hit.getAmount(), 10, "Domain must not follow caster");
            state(owner).putLong("domain", time);
            tick(owner);
            h.assertTrue(!state(owner).getBoolean("domainFx"), "Expiry closes visual lifecycle");
            var stack = owner.getMainHandItem();
            h.assertTrue(stack.use(owner.level(), owner, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "Cooldown still applies after expiry");
        });
    }

    @GameTest(template = "empty")
    public static void domainCursePreservesSingleManualMark(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), ally = f.player(false), marked = f.player(false), victim = f.player(false);
            state(owner).putUUID("narotra_doll", ally.getUUID());
            state(owner).putUUID("nilakan_doll", marked.getUUID());
            use(h, owner, "domain_talisman"); tick(owner);
            h.assertTrue(ally.hasEffect(MobEffects.DAMAGE_BOOST), "Domain strengthens linked Narotra player");
            h.assertTrue(victim.hurt(victim.damageSources().playerAttack(ally), 2), "Real linked ally hit lands");
            h.assertTrue(state(owner).getUUID("nilakan_doll").equals(marked.getUUID()), "Domain must not replace manual mark");
            h.assertTrue(victim.hasEffect(MobEffects.BLINDNESS) && victim.hasEffect(MobEffects.WEAKNESS)
                    && state(victim).getLong("vulnerableUntil") > victim.level().getGameTime(), "Domain applies all three curses");
            long now = victim.level().getGameTime();
            for (String key : new String[]{"weakUntil", "slow", "bleed", "vulnerableUntil"})
                near(h, state(victim).getLong(key)-now, 400, "Domain duration: " + key);
            near(h, victim.getAttributeValue(Attributes.ATTACK_SPEED), 2, "Domain halves attack speed");
            var incoming = new LivingHurtEvent(victim, victim.damageSources().generic(), 10);
            MinecraftForge.EVENT_BUS.post(incoming);
            near(h, incoming.getAmount(), 10*(1+ThidaConfig.VULNERABILITY.get()), "Domain vulnerability increases incoming damage");
            var outgoing = new LivingHurtEvent(marked, marked.damageSources().playerAttack(victim), 10);
            MinecraftForge.EVENT_BUS.post(outgoing);
            near(h, outgoing.getAmount(), 7, "Domain weakness reduces outgoing damage");
        });
    }

    @GameTest(template = "empty")
    public static void persistentMarksSurviveLogoutAndRejectReplacement(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false), replacement = f.player(false);
            state(owner).putUUID("nilakan_doll", target.getUUID());
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
            replacement.hurt(replacement.damageSources().playerAttack(owner), 2);
            h.assertTrue(state(owner).getUUID("nilakan_doll").equals(target.getUUID()), "Cannot replace linked target without cancellation");
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(target));
            tick(owner);
            h.assertTrue(state(owner).hasUUID("nilakan_doll"), "Target logout does not erase permanent mark");
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(owner));
            h.assertTrue(state(owner).hasUUID("nilakan_doll"), "Owner logout preserves permanent mark");
        });
    }

    @GameTest(template = "empty")
    public static void hudSnapshotLinksCooldownsAndPrivacy(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false), other = f.player(true);
            state(owner).putUUID("nilakan_doll", target.getUUID());
            state(other).putUUID("heart", owner.getUUID());
            state(owner).putString("privateInternalValue", "must-not-sync");
            long time = owner.level().getGameTime();
            state(owner).putLong("cd_domain_talisman", time + 600);
            state(owner).putLong("cd_heart_doll", time - 20);
            var data = ThidaEvents.hudSnapshot(owner);
            h.assertTrue(data.getCompound("nilakan_doll").getString("name").equals(target.getScoreboardName()), "HUD resolves linked target name");
            near(h, data.getCompound("cooldowns").getLong("cd_domain_talisman"), 600, "HUD sends remaining active cooldown ticks");
            near(h, data.getCompound("cooldowns").getLong("cd_heart_doll"), 0, "Expired cooldown is clamped");
            h.assertTrue(data.getString("guardian").equals(other.getScoreboardName()), "Recipient sees their guardian");
            h.assertTrue(!data.contains("privateInternalValue") && !data.getCompound("heart").hasUUID("heart"), "Internal state is not copied wholesale");
            var recipient = ThidaEvents.hudSnapshot(target);
            h.assertTrue(!recipient.getBoolean("thida") && recipient.getCompound("nilakan_doll").isEmpty(), "Other player's private links are not exposed");
        });
    }

    @GameTest(template = "empty")
    public static void hudOfflineTargetAndWireRoundTrip(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
            target.hurt(target.damageSources().playerAttack(owner), 2);
            f.uuidIndex.remove(target.getUUID());
            var data = ThidaEvents.hudSnapshot(owner);
            var link = data.getCompound("nilakan_doll");
            h.assertTrue(link.isEmpty(), "Attack without holding the Nilakan doll does not create a link");
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                buffer.writeNbt(data);
                var decoded = buffer.readNbt();
                h.assertTrue(data.equals(decoded), "HUD NBT survives network encoding");
                ThidaHudState.accept(decoded);
                decoded.putString("player", "modified");
                h.assertTrue(ThidaHudState.snapshot().getString("player").equals(owner.getUUID().toString()), "Client snapshot is defensively copied");
                ThidaHudState.tick();
                near(h, ThidaHudState.remaining(20), 19, "Client countdown advances between packets");
                near(h, ThidaHudState.remaining(0), 0, "Client countdown never goes negative");
                h.assertTrue(ThidaHudState.time(1).equals("0:01") && ThidaHudState.time(12000).equals("10:00"), "Clock formatting rounds up and uses minutes");
            } finally { buffer.release(); ThidaHudState.accept(new CompoundTag()); }
        });
    }

    @GameTest(template = "empty")
    public static void hudDomainAndGazeProgress(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            state(owner).putInt("gaze", 120);
            state(owner).putUUID("gazeTarget", target.getUUID());
            use(h, owner, "domain_talisman");
            var data = ThidaEvents.hudSnapshot(owner);
            near(h, data.getLong("domain"), 6000, "HUD domain active countdown");
            near(h, data.getCompound("cooldowns").getLong("cd_domain_talisman"), 30000, "HUD domain readiness deadline");
            near(h, data.getInt("gaze"), 120, "HUD gaze progress");
            h.assertTrue(data.getString("gazeName").equals(target.getScoreboardName()) && data.getBoolean("inDomain"), "HUD target gaze and inside-domain status");
            owner.setPos(owner.getX() + 40, owner.getY(), owner.getZ());
            h.assertTrue(!ThidaEvents.hudSnapshot(owner).getBoolean("inDomain"), "HUD reflects leaving fixed domain");
        });
    }

    @GameTest(template = "empty")
    public static void liteHudTracksEffectBeforeRemainingCooldown(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner=f.player(true);
            var data=ThidaEvents.hudSnapshot(owner);
            h.assertTrue(data.getCompound("activeEffects").isEmpty(), "Passive links do not expose retired active-effect timers");
            h.assertTrue(data.getCompound("cooldowns").isEmpty(), "Passive links do not expose retired active-effect cooldowns");
        });
    }

    @GameTest(template = "empty")
    public static void liteHudInstantCleanseAndEarlyDispel(GameTestHelper h) {
        run(h, f -> {
            FakePlayer caster=f.player(true), healer=f.player(true);
            h.assertTrue(ThidaEvents.hudSnapshot(healer).getCompound("activeEffects").isEmpty(), "Passive cleanses are timer-driven rather than cast actions");
            h.assertTrue(ThidaEvents.hudSnapshot(caster).getCompound("cooldowns").isEmpty(), "Passive links do not expose retired dispel cooldowns");
        });
    }

    @GameTest(template = "empty")
    public static void debugResetRequiresOperatorAndPreservesPowers(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner=f.player(true), target=f.player(false);
            state(owner).putUUID("nilakan_doll",target.getUUID());
            state(owner).putUUID("heart",target.getUUID());
            state(owner).putBoolean("heartUsed",true);
            long now=owner.level().getGameTime();
            state(owner).putLong("domain",now+6000);
            state(owner).putLong("cd_domain_talisman",now+30000);
            state(owner).putLong("cd_effect_nilakan_doll_0",now+600);
            state(owner).putLong("hud_effect_nilakan_doll_0",now+400);
            String cmd="thida resetcooldown "+owner.getScoreboardName();
            var dispatcher=owner.server.getCommands().getDispatcher();
            boolean denied=false;
            try { dispatcher.execute(cmd,owner.createCommandSourceStack().withPermission(0).withSuppressedOutput()); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied=true; }
            h.assertTrue(denied&&state(owner).getLong("cd_domain_talisman")==now+30000,"Non-operator reset denied without mutation");
            try {
                dispatcher.execute(cmd,owner.createCommandSourceStack().withPermission(2).withSuppressedOutput());
                dispatcher.execute("thida debug status "+owner.getScoreboardName(),owner.createCommandSourceStack().withPermission(2).withSuppressedOutput());
            } catch(Exception e) { throw new AssertionError(e); }
            h.assertTrue(!state(owner).contains("cd_domain_talisman")&&!state(owner).contains("cd_effect_nilakan_doll_0"),"Reset removes both cooldown types");
            h.assertTrue(state(owner).hasUUID("nilakan_doll")&&state(owner).hasUUID("heart")&&state(owner).getBoolean("heartUsed"),"Reset preserves links and consumed protection");
            near(h,state(owner).getLong("domain"),now+6000,"Reset preserves active domain");
            near(h,state(owner).getLong("hud_effect_nilakan_doll_0"),now+400,"Reset preserves active effect timer");
        });
    }

    @GameTest(template = "empty")
    public static void debugEndStopsPowersWithoutReward(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner=f.player(true), target=f.player(false);
            use(h,owner,"parasite_doll");
            var dollId=state(owner).getUUID("parasiteEntity");
            state(owner).putUUID("nilakan_doll",target.getUUID());
            state(owner).putUUID("heart",target.getUUID());
            use(h,owner,"domain_talisman");
            owner.setHealth(40);
            try {
                owner.server.getCommands().getDispatcher().execute("thida debug end "+owner.getScoreboardName(),
                        owner.server.createCommandSourceStack().withPermission(2).withSuppressedOutput());
            } catch(Exception e) { throw new AssertionError(e); }
            h.assertTrue(!state(owner).hasUUID("nilakan_doll")&&!state(owner).hasUUID("heart")&&!state(owner).getBoolean("parasiteArmed"),"End clears owned links and summoned doll");
            h.assertTrue(!state(owner).contains("domain")&&!state(owner).getBoolean("parasiteReward"),"End clears domain without parasite reward");
            h.assertTrue(!state(owner).getBoolean("domainFx"),"Debug end closes domain visuals");
            var doll=owner.serverLevel().getEntity(dollId);
            h.assertTrue(doll==null||doll.isRemoved(),"End removes visual doll");
            near(h,owner.getHealth(),40,"Debug end does not heal or injure");
            near(h,state(owner).getLong("cd_domain_talisman")-owner.level().getGameTime(),24000,"End starts normal domain cooldown");
            near(h,state(owner).getLong("cd_heart_doll")-owner.level().getGameTime(),12000,"End starts normal heart cooldown");
        });
    }

    @GameTest(template = "empty")
    public static void parasiteAppearanceAndFacing(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true);
            use(h, owner, "parasite_doll");
            var doll = (net.minecraft.world.entity.decoration.ArmorStand)owner.serverLevel().getEntity(state(owner).getUUID("parasiteEntity"));
            h.assertTrue(doll != null && !doll.hasCustomName() && !doll.isCustomNameVisible(), "Summoned doll has no floating label");
            h.assertTrue(doll.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(ThidaMod.PARASITE_MASK.get()), "Doll wears custom mask");
            for (float yaw : new float[]{0, 90, 180, -90}) {
                owner.setYRot(yaw); owner.setXRot(20); tick(owner);
                near(h, doll.getYRot(), yaw, "Doll follows owner yaw");
                near(h, doll.getHeadPose().getX(), 20, "Doll follows head pitch");
                near(h, Math.hypot(doll.getX()-owner.getX(),doll.getZ()-owner.getZ()), Math.hypot(.85,.25), "Side offset rotates with owner");
            }
            doll.getPersistentData().remove("thidaParasiteStyle");
            doll.setCustomName(net.minecraft.network.chat.Component.literal("หุ่นกาฝาก")); doll.setCustomNameVisible(true);
            tick(owner);
            h.assertTrue(!doll.hasCustomName() && !doll.isCustomNameVisible(), "Old summoned dolls migrate without a name tag");
        });
    }

    @GameTest(template = "empty")
    public static void domainDisplaySnapshotsExpireAndClose(GameTestHelper h) {
        ThidaDomainState.clear();
        try {
            var first = new CompoundTag();
            first.putUUID("owner", UUID.randomUUID()); first.putBoolean("active", true); first.putLong("age", 100);
            first.putString("dimension", "minecraft:overworld"); first.putDouble("radius", 30);
            ThidaDomainState.accept(first);
            var second = first.copy(); second.putUUID("owner", UUID.randomUUID()); ThidaDomainState.accept(second);
            h.assertTrue(ThidaDomainState.views().size()==2, "Overlapping domains remain separate");
            ThidaDomainState.tick();
            near(h, ThidaDomainState.age(ThidaDomainState.views().get(0), .5f),101.5,"Animation interpolates between snapshots");
            first.putBoolean("active", false); ThidaDomainState.accept(first);
            h.assertTrue(ThidaDomainState.views().size()==1,"End packet removes only its owner's domain");
            for(int i=0;i<31;i++) ThidaDomainState.tick();
            h.assertTrue(ThidaDomainState.views().isEmpty(),"Out-of-range or disconnected domain expires without more packets");
            h.succeed();
        } finally { ThidaDomainState.clear(); }
    }

    private static void tick(ServerPlayer p) {
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }

    private static CompoundTag state(ServerPlayer p) {
        if (!p.getPersistentData().contains("thida_state")) p.getPersistentData().put("thida_state", new CompoundTag());
        return p.getPersistentData().getCompound("thida_state");
    }

    private static void use(GameTestHelper h, FakePlayer p, String id) {
        ItemStack stack = new ItemStack(ThidaMod.ABILITIES.get(id).get());
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        h.assertTrue(stack.use(p.level(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(), "Ability must activate: " + id);
    }

    private static void near(GameTestHelper h, double actual, double expected, String label) {
        h.assertTrue(Math.abs(actual - expected) < .001, label + ": expected " + expected + ", got " + actual);
    }

    private static void run(GameTestHelper h, Consumer<Fixture> test) {
        try (Fixture fixture = new Fixture(h)) { test.accept(fixture); }
        catch (RuntimeException | Error e) { e.printStackTrace(); throw e; }
        h.succeed();
    }

    /** Stock FakePlayer is unconditionally invulnerable. Keep its no-op packet handler,
     * but restore combat for actual LivingEntity hurt / Forge / vanilla totem integration. */
    private static final class CombatPlayer extends FakePlayer {
        CombatPlayer(ServerLevel level, GameProfile profile) { super(level, profile); }
        @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
        @Override public boolean canHarmPlayer(Player other) { return true; }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<FakePlayer> players = new ArrayList<>();
        private final Map<UUID, ServerPlayer> uuidIndex;
        private final List<ServerPlayer> serverPlayers;
        private final Map<Object, FakePlayer> factoryCache;

        @SuppressWarnings("unchecked")
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            uuidIndex = ObfuscationReflectionHelper.getPrivateValue(PlayerList.class,
                    helper.getLevel().getServer().getPlayerList(), "f_11197_");
            serverPlayers = ObfuscationReflectionHelper.getPrivateValue(PlayerList.class,
                    helper.getLevel().getServer().getPlayerList(), "f_11196_");
            try {
                Field field = FakePlayerFactory.class.getDeclaredField("fakePlayers");
                field.setAccessible(true);
                factoryCache = (Map<Object, FakePlayer>) field.get(null);
            } catch (ReflectiveOperationException e) { throw new AssertionError("Forge 47.4.0 fake-player fixture access", e); }
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
            ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, p, 0, "f_8921_");
            p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            p.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Attributes.MOVEMENT_SPEED.getDefaultValue());
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Attributes.ATTACK_DAMAGE.getDefaultValue());
            p.setHealth(100);
            BlockPos pos = helper.absolutePos(BlockPos.ZERO);
            p.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
            if (origin) p.addTag("thida_origin");
            serverPlayers.add(p);
            uuidIndex.put(uuid, p);
            helper.assertTrue(level.getServer().getPlayerList().getPlayer(uuid) == p, "UUID resolution must find loaded fixture");
            return p;
        }

        @Override public void close() {
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


