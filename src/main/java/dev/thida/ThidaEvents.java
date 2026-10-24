package dev.thida;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-only mechanics. Origins integration must add/remove the thida_origin scoreboard tag.
 * Bonds and cooldowns survive item swaps; invalid, dead or disconnected partners terminate bonds.
 * Vanilla snowballs carry herbal orbs. Clouds are stationary, radius 4, lasting five seconds.
 * Bleed is one armor-bypassing HP/second; attack slowdown is a temporary attribute modifier.
 * Parasite uses thida:shared damage with resource-defined armor/effect bypass tags.
 * Vanilla healing events have no healer identity: ordinary healing is blocked for recipients;
 * only recognized external healing through addonHeal releases a covenant.
 * Domain hit debuffs focus on PvP. Weakening reduces all player-sourced damage by 30%;
 * vanilla Weakness additionally penalizes melee. Recipient absorption may absorb shared damage.
 */
@Mod.EventBusSubscriber(modid = "thida")
public final class ThidaEvents {
    private static final String ROOT = "thida_state";
    private static final UUID SLOW = UUID.fromString("d33de0e3-8da7-4466-b42c-190f32effb2a");
    private static boolean internalHeal;
    private static boolean transfer;
    private ThidaEvents() {}

    public static boolean isThida(ServerPlayer p) {
        return p.getTags().contains("thida_origin");
    }

    private static CompoundTag state(LivingEntity e) {
        CompoundTag root = e.getPersistentData();
        if (!root.contains(ROOT)) root.put(ROOT, new CompoundTag());
        return root.getCompound(ROOT);
    }

    private static long now(Entity e) { return e.level().getGameTime(); }
    private static int seconds() { return Math.max(1, ThidaConfig.EFFECT_SECONDS.get()) * 20; }
    private static String id(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && key.getNamespace().equals("thida") ? key.getPath() : "";
    }
    private static boolean holds(ServerPlayer p, String name) {
        return id(p.getMainHandItem()).equals(name) || id(p.getOffhandItem()).equals(name);
    }
    private static void effect(LivingEntity e, MobEffect effect, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(effect, ticks, amp));
    }
    private static void cleanse(LivingEntity e) {
        for (MobEffectInstance effect : new ArrayList<>(e.getActiveEffects()))
            if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL)
                e.removeEffect(effect.getEffect());
        state(e).remove("bleed");
        state(e).remove("slow");
        state(e).remove("weakUntil");
        if (e.getAttribute(Attributes.ATTACK_SPEED) != null)
            e.getAttribute(Attributes.ATTACK_SPEED).removeModifier(SLOW);
        e.clearFire();
    }
    private static ServerPlayer resolve(ServerPlayer owner, String key) {
        if (!isThida(owner) || !owner.isAlive() || owner.isSpectator()) return null;
        CompoundTag n = state(owner);
        if (!n.hasUUID(key)) return null;
        ServerPlayer target = owner.server.getPlayerList().getPlayer(n.getUUID(key));
        if (target == null || !target.isAlive() || target.isSpectator() || target == owner || target.level() != owner.level()
                || target.distanceToSqr(owner) > Math.pow(key.endsWith("_doll")
                        ? ThidaConfig.MARK_RANGE.get() : ThidaConfig.BOND_RANGE.get(), 2)) return null;
        return target;
    }
    private static ServerPlayer aimed(ServerPlayer owner) {
        ServerPlayer best = null;
        double distance = ThidaConfig.BOND_RANGE.get();
        Vec3 from = owner.getEyePosition();
        Vec3 end = from.add(owner.getLookAngle().scale(distance));
        for (ServerPlayer p : owner.server.getPlayerList().getPlayers()) {
            if (p == owner || p.level() != owner.level() || !p.isAlive() || p.isSpectator()
                    || !owner.hasLineOfSight(p) || owner.distanceToSqr(p) > distance * distance) continue;
            var hit = p.getBoundingBox().inflate(0.35).clip(from, end);
            if (hit.isPresent() && from.distanceTo(hit.get()) < distance) {
                distance = from.distanceTo(hit.get()); best = p;
            }
        }
        return best;
    }
    private static void tell(ServerPlayer p, String text) {
        p.displayClientMessage(net.minecraft.network.chat.Component.literal(text), true);
    }
    private static boolean covenant(ServerPlayer p) { return resolve(p, "heart") != null; }
    private static ServerPlayer heartOwner(ServerPlayer target) {
        for (ServerPlayer owner : target.server.getPlayerList().getPlayers())
            if (resolve(owner, "heart") == target) return owner;
        return null;
    }
    private static void heal(ServerPlayer target, float amount) {
        // Every addon heal from another caster deliberately dissolves the recipient's covenant.
        boolean previous = internalHeal;
        internalHeal = true;
        try { target.heal(amount); } finally { internalHeal = previous; }
    }
    /** Integration hook: external addon healing should use this to release covenants. */
    public static void addonHeal(ServerPlayer caster, ServerPlayer target, float amount) {
        if (caster == target && heartOwner(caster) != null) return;
        if (caster != target) clearHeartFor(target);
        heal(target, amount);
    }
    private static void clearHeartFor(ServerPlayer target) {
        state(target).remove("heart");
        state(target).remove("heartUsed");
        for (ServerPlayer p : target.server.getPlayerList().getPlayers())
            if (state(p).hasUUID("heart") && state(p).getUUID("heart").equals(target.getUUID()))
                { state(p).remove("heart"); state(p).remove("heartUsed"); }
    }

    public static InteractionResultHolder<ItemStack> use(ServerPlayer p, ItemStack stack, InteractionHand hand) {
        String item = id(stack);
        if (!isThida(p) || !p.isAlive() || p.isSpectator()) return InteractionResultHolder.pass(stack);
        CompoundTag n = state(p);
        if (covenant(p) && (item.equals("domain_talisman") || item.equals("parasite_doll") || item.equals("remedy")))
            return InteractionResultHolder.fail(stack);
        if ((item.equals("narotra_doll") || item.equals("nilakan_doll")) && resolve(p, "parasite") != null) {
            tell(p, "Release the parasite bond first."); return InteractionResultHolder.fail(stack);
        }
        if ((item.equals("narotra_doll") || item.equals("nilakan_doll")) && p.isShiftKeyDown()) {
            int mode = (n.getInt(item + "_mode") + 1) % (item.equals("nilakan_doll") ? 2 : 3);
            n.putInt(item + "_mode", mode);
            tell(p, item.equals("narotra_doll") ? new String[]{"Blessing: speed", "Blessing: resistance", "Blessing: cleanse"}[mode]
                    : new String[]{"Curse: bleed and blindness", "Curse: weakness and attack slowdown"}[mode]);
            return InteractionResultHolder.success(stack);
        }
        if (n.getLong("cd_" + item) > now(p)) {
            tell(p, "Ability is cooling down."); return InteractionResultHolder.fail(stack);
        }
        switch (item) {
            case "ward" -> {
                effect(p, MobEffects.ABSORPTION, seconds(), 0);
                effect(p, MobEffects.DAMAGE_RESISTANCE, seconds(), 0);
            }
            case "veil" -> effect(p, MobEffects.INVISIBILITY, seconds(), 0);
            case "grace" -> effect(p, MobEffects.HERO_OF_THE_VILLAGE, seconds(), 0);
            case "remedy" -> {
                if (covenant(p) || heartOwner(p) != null) return InteractionResultHolder.fail(stack);
                cleanse(p); addonHeal(p, p, ThidaConfig.HEAL_AMOUNT.get().floatValue());
            }
            case "herbal_orb" -> {
                Snowball ball = new Snowball(p.level(), p);
                ball.setItem(stack.copyWithCount(1));
                ball.getPersistentData().putUUID("thida_orb", p.getUUID());
                ball.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 1.2F, 0.5F);
                p.level().addFreshEntity(ball);
            }
            case "narotra_doll", "nilakan_doll" -> {
                ServerPlayer target = resolve(p, item);
                if (target == null) { tell(p, "Hit a nearby player with this doll to mark them."); return InteractionResultHolder.fail(stack); }
                if (item.equals("narotra_doll")) blessing(target, n.getInt(item + "_mode"));
                else curse(target, n.getInt(item + "_mode"));
            }
            case "parasite_doll" -> {
                if (resolve(p, "parasite") != null) {
                    n.remove("parasite"); tell(p, "Parasite bond released.");
                } else { tell(p, "Hit a player with the doll while below the parasite health threshold."); return InteractionResultHolder.fail(stack); }
            }
            case "heart_doll" -> {
                ServerPlayer target = aimed(p);
                if (target == null || covenant(p) || heartOwner(p) != null || covenant(target)
                        || heartOwner(target) != null) return InteractionResultHolder.fail(stack);
                // Only one incoming covenant can protect a player at a time.
                for (ServerPlayer other : p.server.getPlayerList().getPlayers())
                    if (resolve(other, "heart") == target) return InteractionResultHolder.fail(stack);
                n.putUUID("heart", target.getUUID());
                n.putBoolean("heartUsed", false);
                tell(p, "Covenant bound to " + target.getScoreboardName());
            }
            case "domain_talisman" -> n.putLong("domain", now(p) + Math.max(1, ThidaConfig.DOMAIN_DURATION.get()) * 20L);
            default -> { return InteractionResultHolder.pass(stack); }
        }
        n.putLong("cd_" + item, now(p) + Math.max(1, ThidaConfig.COOLDOWN_SECONDS.get()) * 20L);
        return InteractionResultHolder.success(stack);
    }
    private static void blessing(ServerPlayer p, int mode) {
        if (mode == 0) effect(p, MobEffects.MOVEMENT_SPEED, seconds(), 1);
        else if (mode == 1) effect(p, MobEffects.DAMAGE_RESISTANCE, seconds(), 0);
        else cleanse(p);
    }
    private static void curse(ServerPlayer p, int mode) {
        if (mode == 0) {
            state(p).putLong("bleed", now(p) + seconds());
            effect(p, MobEffects.BLINDNESS, seconds(), 0);
        } else {
            effect(p, MobEffects.WEAKNESS, seconds(), 1);
            state(p).putLong("weakUntil", now(p) + seconds());
            slow(p, seconds());
        }
    }
    private static void slow(ServerPlayer p, int duration) {
        state(p).putLong("slow", now(p) + duration);
        var attribute = p.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute != null && attribute.getModifier(SLOW) == null)
            attribute.addTransientModifier(new AttributeModifier(SLOW, "Thida curse", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    private static boolean inDomain(ServerPlayer owner, Entity target) {
        return isThida(owner) && state(owner).getLong("domain") > now(owner)
                && owner.level() == target.level()
                && owner.distanceToSqr(target) <= Math.pow(ThidaConfig.DOMAIN_RADIUS.get(), 2);
    }
    @SubscribeEvent
    public static void attack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (state(event.getEntity()).getLong("invulnerable") > now(event.getEntity())) {
            event.setCanceled(true); return;
        }
        if (!transfer && event.getSource().getEntity() instanceof ServerPlayer p && heartOwner(p) != null) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingHurtEvent event) {
        if (!transfer && event.getSource().getEntity() instanceof ServerPlayer attacker
                && state(attacker).getLong("weakUntil") > now(attacker))
            event.setAmount(event.getAmount() * 0.7F);
        if (!transfer && event.getEntity() instanceof ServerPlayer p && inDomain(p, p))
            event.setAmount(CombatRules.domainDamage(event.getAmount(), 1 - ThidaConfig.DOMAIN_REDUCTION.get()));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || event.getAmount() <= 0) return;
        if (state(victim).getLong("invulnerable") > now(victim)) { event.setAmount(0); return; }
        if (transfer) {
            ownerDamaged(victim);
            event.setAmount(protect(victim, event.getAmount()));
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            if (heartOwner(attacker) != null) { event.setAmount(0); return; }
            if (isThida(attacker)) {
                boolean melee = event.getSource().getDirectEntity() == attacker;
                if (melee && resolve(attacker, "parasite") == null) {
                    if (holds(attacker, "parasite_doll") && CombatRules.canStartParasite(
                            attacker.getHealth(), victim.getHealth(), ThidaConfig.PARASITE_THRESHOLD.get(),
                            ThidaConfig.PARASITE_BREAK_HEALTH.get())
                            && attacker.getHealth() + victim.getHealth() - event.getAmount() > ThidaConfig.PARASITE_BREAK_HEALTH.get()
                            && !covenant(attacker)
                            && state(attacker).getLong("cd_parasite_doll") <= now(attacker)
                            && attacker.distanceToSqr(victim) <= Math.pow(ThidaConfig.BOND_RANGE.get(), 2)
                            && !hasParasitePartner(attacker) && !hasParasitePartner(victim)) {
                        state(attacker).putUUID("parasite", victim.getUUID());
                        state(attacker).putLong("cd_parasite_doll", now(attacker) + Math.max(1, ThidaConfig.COOLDOWN_SECONDS.get()) * 20L);
                        tell(attacker, "Parasite bound to " + victim.getScoreboardName());
                    } else {
                        if (holds(attacker, "narotra_doll")) mark(attacker, victim, "narotra_doll");
                        if (holds(attacker, "nilakan_doll")) mark(attacker, victim, "nilakan_doll");
                    }
                }
                if (inDomain(attacker, victim)) {
                    effect(victim, MobEffects.WEAKNESS, seconds(), 0);
                    state(victim).putLong("weakUntil", now(victim) + seconds());
                    effect(victim, MobEffects.BLINDNESS, seconds(), 0);
                }
            }
            for (ServerPlayer owner : victim.server.getPlayerList().getPlayers()) {
                if (resolve(owner, "parasite") == null && resolve(owner, "narotra_doll") == attacker
                        && inDomain(owner, attacker) && inDomain(owner, victim)) {
                    state(owner).putUUID("nilakan_doll", victim.getUUID());
                    curse(victim, 0); curse(victim, 1);
                }
            }
        }
        ownerDamaged(victim);
        float amount = event.getAmount();
        for (ServerPlayer owner : victim.server.getPlayerList().getPlayers()) {
            ServerPlayer partner = resolve(owner, "parasite");
            if (partner == null || (owner != victim && partner != victim)) continue;
            ServerPlayer recipient = owner == victim ? partner : owner;
            float shared = CombatRules.sharedDamage(amount, ThidaConfig.SHARE_FRACTION.get());
            amount -= shared;
            finalDamage(recipient, shared, event.getSource());
            if (owner.isAlive() && partner.isAlive() && CombatRules.shouldBreak(
                    owner.getHealth() - (owner == victim ? amount : 0),
                    partner.getHealth() - (partner == victim ? amount : 0), ThidaConfig.PARASITE_BREAK_HEALTH.get())) {
                state(owner).remove("parasite");
                // Defer healing until the pending vanilla damage has actually been applied.
                state(owner).putBoolean("parasiteReward", true);
                effect(owner, MobEffects.MOVEMENT_SPEED, 40, 1);
                effect(partner, MobEffects.MOVEMENT_SPEED, 200, 1);
                effect(partner, MobEffects.DAMAGE_RESISTANCE, 200, 0);
            }
            break;
        }
        event.setAmount(protect(victim, amount));
    }
    private static boolean hasParasitePartner(ServerPlayer p) {
        if (resolve(p, "parasite") != null) return true;
        for (ServerPlayer owner : p.server.getPlayerList().getPlayers())
            if (resolve(owner, "parasite") == p) return true;
        return false;
    }
    private static void mark(ServerPlayer owner, ServerPlayer target, String key) {
        CompoundTag n = state(owner);
        if (n.getLong("cd_mark_" + key) > now(owner)) return;
        n.putUUID(key, target.getUUID());
        n.putLong("cd_mark_" + key, now(owner) + Math.max(1, ThidaConfig.COOLDOWN_SECONDS.get()) * 20L);
        if (key.equals("narotra_doll")) n.remove("gaze");
        tell(owner, (key.equals("narotra_doll") ? "Narotra" : "Nilakan") + " marked " + target.getScoreboardName());
    }
    private static void ownerDamaged(ServerPlayer p) {
        if (covenant(p)) {
            state(p).remove("heart");
            state(p).remove("heartUsed");
            state(p).putLong("bleed", now(p) + 200);
        }
    }
    private static float protect(ServerPlayer victim, float damage) {
        if (state(victim).getLong("invulnerable") > now(victim)) return 0;
        if (victim.getHealth() - damage > 1) return damage;
        for (ServerPlayer owner : victim.server.getPlayerList().getPlayers()) {
            if (resolve(owner, "heart") == victim && !state(owner).getBoolean("heartUsed")) {
                state(owner).putBoolean("heartUsed", true);
                state(victim).putLong("invulnerable", now(victim) + 100);
                return CombatRules.nonlethalDamage(victim.getHealth(), damage);
            }
        }
        return damage;
    }
    private static void finalDamage(ServerPlayer p, float amount, DamageSource source) {
        if (amount <= 0 || state(p).getLong("invulnerable") > now(p)) return;
        boolean previousTransfer = transfer;
        transfer = true;
        try {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    new ResourceLocation("thida", "shared"));
            var type = p.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(key);
            p.hurt(new DamageSource(type, source.getDirectEntity(), source.getEntity()), amount);
        } finally { transfer = previousTransfer; }
    }
    @SubscribeEvent
    public static void healing(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !internalHeal && heartOwner(p) != null) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void orbImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Snowball ball) || ball.level().isClientSide
                || !ball.getPersistentData().hasUUID("thida_orb") || event.getRayTraceResult().getType() == HitResult.Type.MISS) return;
        ServerPlayer owner = ball.getServer().getPlayerList().getPlayer(ball.getPersistentData().getUUID("thida_orb"));
        if (owner != null && isThida(owner) && owner.level() == ball.level()) {
            CompoundTag n = state(owner);
            Vec3 pos = event.getRayTraceResult().getLocation();
            n.putDouble("cloudX", pos.x); n.putDouble("cloudY", pos.y); n.putDouble("cloudZ", pos.z);
            n.putString("cloudDimension", owner.level().dimension().location().toString());
            n.putLong("cloud", now(owner) + 100);
        }
        event.setCanceled(true); ball.discard();
    }
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        CompoundTag n = state(p);
        long time = now(p);
        if (n.getLong("slow") <= time && p.getAttribute(Attributes.ATTACK_SPEED) != null)
            p.getAttribute(Attributes.ATTACK_SPEED).removeModifier(SLOW);
        if (n.getLong("bleed") > time && time % 20 == 0)
            p.hurt(p.damageSources().magic(), 1);
        if (!isThida(p)) {
            if (n.getBoolean("active")) cleanup(p);
            return;
        }
        n.putBoolean("active", true);
        for (String key : new String[]{"parasite", "heart", "narotra_doll", "nilakan_doll"})
            if (n.hasUUID(key) && resolve(p, key) == null) {
                n.remove(key);
                if (key.equals("heart")) n.remove("heartUsed");
            }
        ServerPlayer parasite = resolve(p, "parasite");
        if (parasite != null && CombatRules.shouldBreak(p.getHealth(), parasite.getHealth(), ThidaConfig.PARASITE_BREAK_HEALTH.get())) {
            n.remove("parasite"); n.putBoolean("parasiteReward", true);
            effect(p, MobEffects.MOVEMENT_SPEED, 40, 1);
            effect(parasite, MobEffects.MOVEMENT_SPEED, 200, 1);
            effect(parasite, MobEffects.DAMAGE_RESISTANCE, 200, 0);
        }
        if (n.getBoolean("parasiteReward")) {
            n.remove("parasiteReward");
            if (p.isAlive()) addonHeal(p, p, ThidaConfig.PARASITE_BREAK_HEAL.get().floatValue());
        }
        ServerPlayer marked = resolve(p, "narotra_doll");
        if (marked != null && resolve(p, "parasite") == null && looking(p, marked) && looking(marked, p)) {
            int gaze = n.getInt("gaze") + 1;
            if (gaze >= Math.max(1, ThidaConfig.GAZE_TICKS.get())) {
                blessing(marked, n.getInt("narotra_doll_mode")); gaze = 0;
            }
            n.putInt("gaze", gaze);
        } else n.putInt("gaze", 0);
        if (inDomain(p, p)) {
            cleanse(p);
            if (marked != null && resolve(p, "parasite") == null && inDomain(p, marked)) {
                effect(marked, MobEffects.DAMAGE_BOOST, 2, 1);
                effect(marked, MobEffects.DAMAGE_RESISTANCE, 2, 1);
            }
            if (time % 10 == 0) particles(p, p.position(), ThidaConfig.DOMAIN_RADIUS.get());
        }
        if (n.getLong("cloud") > time && n.getString("cloudDimension").equals(p.level().dimension().location().toString())) {
            Vec3 center = new Vec3(n.getDouble("cloudX"), n.getDouble("cloudY"), n.getDouble("cloudZ"));
            if (time % 10 == 0) particles(p, center, 4);
            if (time % 20 == 0) for (ServerPlayer target : p.server.getPlayerList().getPlayers()) {
                if (target.isAlive() && target.level() == p.level() && target.position().distanceToSqr(center) <= 16
                        && (target == p || (p.getTeam() != null && p.getTeam() == target.getTeam()))) {
                    addonHeal(p, target, ThidaConfig.HEAL_AMOUNT.get().floatValue() / 5);
                }
            }
        }
    }
    private static boolean looking(ServerPlayer from, ServerPlayer to) {
        return from.hasLineOfSight(to) && from.getLookAngle().dot(to.getEyePosition().subtract(from.getEyePosition()).normalize()) > 0.97;
    }
    private static void particles(ServerPlayer p, Vec3 center, double radius) {
        p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                center.x, center.y + 0.5, center.z, 12, radius / 2, 0.4, radius / 2, 0);
    }
    private static void cleanup(ServerPlayer p) {
        for (ServerPlayer other : p.server.getPlayerList().getPlayers()) {
            CompoundTag n = state(other);
            for (String key : new String[]{"heart", "parasite", "narotra_doll", "nilakan_doll"})
                if (n.hasUUID(key) && n.getUUID(key).equals(p.getUUID())) {
                    n.remove(key); n.remove("gaze");
                    if (key.equals("heart")) n.remove("heartUsed");
                }
        }
        CompoundTag n = state(p);
        // Retain cooldown deadlines so death/origin toggling cannot reset abilities.
        for (String key : new ArrayList<>(n.getAllKeys())) if (!key.startsWith("cd_")) n.remove(key);
        if (p.getAttribute(Attributes.ATTACK_SPEED) != null) p.getAttribute(Attributes.ATTACK_SPEED).removeModifier(SLOW);
    }
    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) cleanup(p);
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) cleanup(p);
    }
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData().getCompound(ROOT);
        CompoundTag copy = new CompoundTag();
        for (String key : old.getAllKeys()) if (key.startsWith("cd_")) copy.putLong(key, old.getLong(key));
        event.getEntity().getPersistentData().put(ROOT, copy);
    }
    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) cleanup(p);
    }
}
