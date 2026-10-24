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
    public static void ward(GameTestHelper h) {
        run(h, f -> {
            FakePlayer p = f.player(true);
            use(h, p, "ward");
            h.assertTrue(p.hasEffect(MobEffects.ABSORPTION), "Ward must grant absorption");
            h.assertTrue(p.getAbsorptionAmount() > 0, "Ward must supply absorption hearts");
            h.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Ward must grant resistance");
        });
    }

    @GameTest(template = "empty")
    public static void grace(GameTestHelper h) {
        run(h, f -> {
            FakePlayer p = f.player(true);
            use(h, p, "grace");
            h.assertTrue(p.hasEffect(MobEffects.HERO_OF_THE_VILLAGE), "Grace must grant Hero of the Village");
        });
    }

    @GameTest(template = "empty")
    public static void remedy(GameTestHelper h) {
        run(h, f -> {
            FakePlayer p = f.player(true);
            p.setHealth(40);
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200));
            use(h, p, "remedy");
            h.assertTrue(!p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.WEAKNESS), "Remedy must cleanse harmful effects");
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "Remedy must preserve beneficial effects");
            near(h, p.getHealth(), Math.min(100, 40 + ThidaConfig.HEAL_AMOUNT.get()), "Remedy healing");
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
            state(owner).putUUID("nilakan_doll", target.getUUID());
            use(h, owner, "nilakan_doll");
            h.assertTrue(target.hasEffect(MobEffects.BLINDNESS) && state(target).getLong("bleed") > target.level().getGameTime(), "First curse must apply blindness and bleed");
            state(owner).remove("cd_nilakan_doll");
            state(owner).putInt("nilakan_doll_mode", 1);
            double attackSpeed = target.getAttributeValue(Attributes.ATTACK_SPEED);
            use(h, owner, "nilakan_doll");
            h.assertTrue(target.hasEffect(MobEffects.WEAKNESS), "Second curse must apply weakness");
            near(h, target.getAttributeValue(Attributes.ATTACK_SPEED), attackSpeed * .5, "Second curse attack slowdown");
        });
    }

    @GameTest(template = "empty")
    public static void registryOriginResourceAndKitCommand(GameTestHelper h) {
        run(h, f -> {
            FakePlayer p = f.player(false);
            h.assertTrue(ThidaMod.ABILITIES.size() == 10, "All ten abilities must register");
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
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get()));
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
            target.hurt(target.damageSources().playerAttack(owner), 2);
            h.assertTrue(state(owner).hasUUID("narotra_doll") && state(owner).hasUUID("nilakan_doll"), "Both held dolls must mark on a hit");
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
            h.assertTrue(state(owner).hasUUID("narotra_doll") && state(owner).hasUUID("nilakan_doll"), "Hand changes must preserve both marks");
        });
    }

    @GameTest(template = "empty")
    public static void parasiteRejectsLowCombinedHealth(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            owner.setHealth(20); target.setHealth(20);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThidaMod.ABILITIES.get("parasite_doll").get()));
            target.hurt(target.damageSources().playerAttack(owner), 2);
            h.assertTrue(!state(owner).hasUUID("parasite") && !state(owner).getBoolean("parasiteReward"), "Low combined HP must not create or reward parasite");
            near(h, owner.getHealth(), 20, "Rejected parasite must not heal owner");
        });
    }

    @GameTest(template = "empty")
    public static void parasiteBreakRewardsOnlyOnce(GameTestHelper h) {
        run(h, f -> {
            FakePlayer owner = f.player(true), target = f.player(false);
            owner.setHealth(30); target.setHealth(30);
            state(owner).putUUID("parasite", target.getUUID());
            owner.hurt(owner.damageSources().generic(), 10);
            h.assertTrue(!state(owner).hasUUID("parasite"), "Bond must break at combined fifty HP");
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
            near(h, owner.getHealth(), 75, "Owner must gain fifty HP after pending damage");
            near(h, target.getHealth(), 25, "Partner keeps its damage share");
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
            near(h, owner.getHealth(), 75, "Reward cannot repeat next tick");
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

