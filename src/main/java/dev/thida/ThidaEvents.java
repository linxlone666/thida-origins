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

import net.minecraft.world.item.ItemStack;

import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

/** Server-only mechanics. Origin permissions come from the selected Origin via ThidaOrigins.
 * Narotra and Nilakan bonds survive item swaps, death and disconnect; unavailable targets pause their passive effects.
 * Other bonds and cooldowns use their own lifecycle rules.
 * Bleed is one armor-bypassing HP/second; attack slowdown is a temporary attribute modifier.
 * Parasite uses thida:shared damage with resource-defined armor/effect bypass tags.
 * v7 parasite/heart/healing rules live in ThidaBonds and ThidaHealing.
 * Legacy domain effects are retained here.
 * Domain hit debuffs focus on PvP. Weakening reduces all player-sourced damage by 30%;
 * vanilla Weakness additionally penalizes melee. Recipient absorption may absorb shared damage.
 */
@EventBusSubscriber(modid = "thida")
public final class ThidaEvents {
    private static final String ROOT = "thida_state";
    private static final ResourceLocation SLOW = ResourceLocation.fromNamespaceAndPath("thida", "curse_attack_speed");
    private static boolean internalHeal;
    private static boolean transfer;
    private ThidaEvents() {}

    public static boolean isThida(ServerPlayer p) {
        return ThidaOrigins.isThida(p);
    }

    /** Callers must enforce operator permission; these methods do not change existing effects. */
    public static int debugResetCooldowns(ServerPlayer p) {
        CompoundTag n = state(p);
        int count = 0;
        for (String key : new ArrayList<>(n.getAllKeys())) if (key.startsWith("cd_")) {
            n.remove(key); count++;
        }
        var data = ThidaBondData.get(p.server); var skill = data.player(p);
        for (String key : new ArrayList<>(skill.getAllKeys()))
            if (key.endsWith("Cooldown") || key.equals("passiveBlockedUntil") || key.startsWith("spellCd_")) { skill.remove(key); count++; }
        data.setDirty(); return count;
    }
    public static void debugEndPowers(ServerPlayer p) {
        ThidaBonds.endHeart(p.server, p.getUUID(), "DEBUG_END");
        ThidaBonds.breakParasite(p.server, p.getUUID(), "DEBUG_END", false);
        CompoundTag n = state(p);
        for (String key : new String[]{"nilakan_doll", "narotra_doll", "heart", "parasite"})
            if (n.hasUUID(key) || key.equals("parasite") && n.getBoolean("parasiteArmed")) {
                if (passive(key)) ThidaPassives.unlink(p, key, "DEBUG_END"); else release(p, key);
            }
        if (n.getLong("domain") > now(p)) {
            closeDomainFx(p);
            n.remove("domain");
            n.putLong("cd_domain_talisman", now(p) + ThidaConfig.DOMAIN_COOLDOWN.get() * 20L);
        }
        n.remove("parasiteReward"); n.remove("gaze"); n.remove("gazeTarget"); n.remove("menuDoll");
    }

    static CompoundTag state(LivingEntity e) {
        CompoundTag root = e.getPersistentData();
        if (!root.contains(ROOT)) root.put(ROOT, new CompoundTag());
        return root.getCompound(ROOT);
    }

    static long now(Entity e) { return e.level().getGameTime(); }
    private static int seconds() { return Math.max(1, ThidaConfig.EFFECT_SECONDS.get()) * 20; }
    static String id(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && key.getNamespace().equals("thida") ? key.getPath() : "";
    }
    static boolean holds(ServerPlayer p, String name) {
        return id(p.getMainHandItem()).equals(name) || id(p.getOffhandItem()).equals(name);
    }
    private static void effect(LivingEntity e, net.minecraft.core.Holder<MobEffect> effect, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(effect, ticks, amp));
    }
    static void cleanse(LivingEntity e) {
        for (MobEffectInstance effect : new ArrayList<>(e.getActiveEffects()))
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                e.removeEffect(effect.getEffect());
        state(e).remove("bleed");
        state(e).remove("slow");
        state(e).remove("weakUntil");
        state(e).remove("vulnerableUntil");
        if (e.getAttribute(Attributes.ATTACK_SPEED) != null)
            e.getAttribute(Attributes.ATTACK_SPEED).removeModifier(SLOW);
        e.clearFire();
    }
    private static ServerPlayer resolve(ServerPlayer owner, String key) {
        if (key.equals("heart") || key.equals("parasite")) return null; // v7 bonds live in world SavedData.
        if (!isThida(owner) || !owner.isAlive() || owner.isSpectator()) return null;
        CompoundTag n = state(owner);
        if (!n.hasUUID(key)) return null;
        ServerPlayer target = owner.server.getPlayerList().getPlayer(n.getUUID(key));
        if (target == null || !target.isAlive() || target.isSpectator() || target == owner) return null;
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
    static void tell(ServerPlayer p, String text) {
        p.displayClientMessage(net.minecraft.network.chat.Component.literal(text), true);
    }

    /** A player's own links and remaining times only; never broadcast private link state. */
    public static CompoundTag hudSnapshot(ServerPlayer p) {
        CompoundTag data = new CompoundTag(), n = state(p), cooldowns = new CompoundTag(), activeEffects = new CompoundTag();
        data.putString("player", p.getUUID().toString());
        data.putBoolean("thida", isThida(p));
        for (String key : new String[]{"nilakan_doll", "narotra_doll", "parasite", "heart"}) {
            CompoundTag link = new CompoundTag();
            if (isThida(p) && n.hasUUID(key)) {
                UUID uuid = n.getUUID(key);
                ServerPlayer target = p.server.getPlayerList().getPlayer(uuid);
                link.putString("id", uuid.toString());
                String name = target != null ? target.getScoreboardName() : n.getString(key + "Name");
                link.putString("name", name.isEmpty() ? uuid.toString().substring(0, 8) : name);
                link.putBoolean("available", passive(key) ? ThidaPassives.isBondAvailable(p, key)
                        : target != null && target.isAlive() && !target.isSpectator());
            }
            data.put(key, link);
        }
        if (isThida(p)) {
            data.putInt("effectDuration", seconds());
            for (String doll : new String[]{"nilakan_doll", "narotra_doll"}) if (!passive(doll))
                for (int mode = 0; mode < 3; mode++) activeEffects.putLong(doll + "_" + mode, remainingDollEffect(p, doll, mode));
            data.putInt("effectTotal", ThidaConfig.DOLL_EFFECT_COOLDOWN.get() * 20);
            data.putInt("releaseTotal", ThidaConfig.RELEASE_COOLDOWN.get() * 20);
            data.putInt("domainTotal", ThidaConfig.DOMAIN_DURATION.get() * 20);
            data.putInt("domainCooldownTotal", ThidaConfig.DOMAIN_COOLDOWN.get() * 20);
            for (String key : n.getAllKeys()) if (key.startsWith("cd_") && !key.startsWith("cd_effect_nilakan_doll_") && !key.startsWith("cd_effect_narotra_doll_") &&
                    (ThidaMod.ABILITIES.containsKey(key.substring(3)) || key.startsWith("cd_effect_nilakan_doll_") || key.startsWith("cd_effect_narotra_doll_")))
                cooldowns.putLong(key, Math.max(0, n.getLong(key) - now(p)));
            data.putBoolean("armed", n.getBoolean("parasiteArmed"));
            data.putBoolean("dollsLocked", !dollsAllowed(p));
            data.putBoolean("heartUsed", n.getBoolean("heartUsed"));
            data.putInt("gaze", n.getInt("gaze"));
            data.putInt("gazeRequired", 200);
            if (n.hasUUID("gazeTarget")) {
                ServerPlayer target = p.server.getPlayerList().getPlayer(n.getUUID("gazeTarget"));
                if (target != null) data.putString("gazeName", target.getScoreboardName());
            }
            data.putLong("domain", Math.max(0, n.getLong("domain") - now(p)));
            data.putBoolean("inDomain", inDomain(p, p));
        }
        ServerPlayer guardian = heartOwner(p);
        if (guardian != null) data.putString("guardian", guardian.getScoreboardName());
        data.putLong("invulnerable", Math.max(0, n.getLong("invulnerable") - now(p)));
        data.put("cooldowns", cooldowns);
        data.put("activeEffects", activeEffects);
        ThidaBonds.hud(p, data);
        return data;
    }
    private static long effectTicks(ServerPlayer p, net.minecraft.core.Holder<MobEffect> effect) {
        MobEffectInstance instance = p.getEffect(effect);
        return instance == null ? 0 : instance.getDuration();
    }
    private static long remainingDollEffect(ServerPlayer owner, String doll, int mode) {
        CompoundTag n = state(owner);
        String key = doll + "_" + mode;
        long remaining = n.getLong("hud_effect_" + key) - now(owner);
        if (remaining <= 0 || !n.hasUUID("hud_target_" + key)) return 0;
        ServerPlayer target = owner.server.getPlayerList().getPlayer(n.getUUID("hud_target_" + key));
        if (target == null || !target.isAlive()) return 0;
        long actual;
        if (doll.equals("narotra_doll")) {
            actual = mode == 0 ? effectTicks(target, MobEffects.DAMAGE_RESISTANCE)
                    : mode == 2 ? effectTicks(target, MobEffects.MOVEMENT_SPEED) : 0;
        } else {
            CompoundTag effects = state(target);
            actual = mode == 0 ? Math.max(effects.getLong("weakUntil"), effects.getLong("slow")) - now(target)
                    : mode == 1 ? Math.max(effects.getLong("bleed") - now(target), effectTicks(target, MobEffects.BLINDNESS))
                    : effects.getLong("vulnerableUntil") - now(target);
        }
        return Math.max(0, Math.min(remaining, actual));
    }
    private static void syncHud(ServerPlayer p) {
        if (p.tickCount % 5 != 0 || p instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                new ThidaNetwork.HudSnapshot(hudSnapshot(p)));
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
        ThidaHealing.apply(caster, target, amount, ThidaHealing.Kind.SKILL);
    }
    private static void clearHeartFor(ServerPlayer target) {
        for (ServerPlayer p : target.server.getPlayerList().getPlayers())
            if (state(p).hasUUID("heart") && state(p).getUUID("heart").equals(target.getUUID()))
                release(p, "heart");
    }

    private static boolean passive(String item) {
        return item.equals("nilakan_doll") || item.equals("narotra_doll");
    }
    private static boolean dollsAllowed(ServerPlayer p) {
        return !covenant(p) && !state(p).getBoolean("parasiteArmed") && resolve(p, "parasite") == null;
    }
    private static void release(ServerPlayer p, String key) {
        CompoundTag n = state(p);
        n.remove(key);
        String item = key.equals("heart") ? "heart_doll" : key.equals("parasite") ? "parasite_doll" : key;
        n.putLong("cd_" + item, now(p) + ThidaConfig.RELEASE_COOLDOWN.get() * 20L);
        if (key.equals("heart")) n.remove("heartUsed");
        if (key.equals("parasite")) {
            n.remove("parasiteArmed");
            if (n.hasUUID("parasiteEntity")) for (var level : p.server.getAllLevels()) {
                Entity doll = level.getEntity(n.getUUID("parasiteEntity"));
                if (doll != null) doll.discard();
            }
            n.remove("parasiteEntity");
        }
    }

    /** Number-key requests are validated on the server; clients cannot choose another player's mark. */
    public static void dollAction(ServerPlayer p, int button) {
        if (!isThida(p) || !p.isAlive() || p.isSpectator() || button < 1 || button > 5) return;
        CompoundTag n = state(p);
        if (button == 1) {
            String held = id(p.getMainHandItem());
            if (!passive(held)) held = id(p.getOffhandItem());
            if (passive(held)) n.putString("menuDoll", held);
            return;
        }
        String item = n.getString("menuDoll");
        if (!passive(item) || !holds(p, item)) return;
        n.remove("menuDoll");
        if (button == 5) {
            if (passive(item) && n.hasUUID(item)) ThidaPassives.unlink(p, item, "PLAYER_UNLINKED");
            return;
        }
        if (passive(item)) return;
        if (!dollsAllowed(p)) return;
        castDoll(p, item, button - 2);
    }
    private static boolean castDoll(ServerPlayer p, String item, int mode) {
        CompoundTag n = state(p);
        ServerPlayer target = resolve(p, item);
        String cd = "cd_effect_" + item + "_" + mode;
        if (!dollsAllowed(p) || target == null || n.getLong("cd_" + item) > now(p)
                || n.getLong(cd) > now(p)) {
            tell(p, "ยังไม่มีเป้าหมาย หรือสกิลติดคูลดาวน์"); return false;
        }
        if (item.equals("narotra_doll")) blessing(p, target, mode);
        else curse(target, mode);
        String effectKey = item + "_" + mode;
        // Cleanse is instantaneous; the speed/heal action tracks its speed component only.
        n.putLong("hud_effect_" + effectKey, now(p) + (item.equals("narotra_doll") && mode == 1 ? 0 : seconds()));
        n.putUUID("hud_target_" + effectKey, target.getUUID());
        n.putLong(cd, now(p) + ThidaConfig.DOLL_EFFECT_COOLDOWN.get() * 20L);
        return true;
    }

    public static InteractionResultHolder<ItemStack> use(ServerPlayer p, ItemStack stack, InteractionHand hand) {
        String item = id(stack);
        if (item.equals("parasite_doll") || item.equals("heart_doll")) {
            if (isThida(p)) tell(p, item.equals("parasite_doll") ? "ใช้ปุ่มเรียกหุ่นกาฝากในการตั้งค่าปุ่ม" : "ใช้ปุ่มพันธะหทัยในการตั้งค่าปุ่ม");
            return InteractionResultHolder.fail(stack);
        }
        if (!ThidaMod.ABILITIES.containsKey(item)) return InteractionResultHolder.pass(stack);
        if (!isThida(p) || !p.isAlive() || p.isSpectator()) return InteractionResultHolder.pass(stack);
        CompoundTag n = state(p);
        if (covenant(p) && (passive(item) || item.equals("parasite_doll")))
            return InteractionResultHolder.fail(stack);
        if (passive(item) && !dollsAllowed(p)) {
            tell(p, "ยกเลิกสายสัมพันธ์กาฝากก่อน"); return InteractionResultHolder.fail(stack);
        }
        if (passive(item)) return InteractionResultHolder.pass(stack);
        if (n.getLong("cd_" + item) > now(p)) {
            tell(p, "สกิลยังติดคูลดาวน์"); return InteractionResultHolder.fail(stack);
        }
        switch (item) {
            case "narotra_doll", "nilakan_doll" -> { return InteractionResultHolder.pass(stack); }
            case "parasite_doll" -> {
                if (resolve(p, "parasite") != null || n.getBoolean("parasiteArmed")) {
                    release(p, "parasite"); tell(p, "ยกเลิกสายสัมพันธ์กาฝากแล้ว");
                } else {
                    if (hasParasitePartner(p)) return InteractionResultHolder.fail(stack);
                    var doll = new net.minecraft.world.entity.decoration.ArmorStand(p.level(), p.getX(), p.getY(), p.getZ());
                    ThidaParasiteVisual.style(doll);
                    ThidaParasiteVisual.follow(doll, p);
                    p.level().addFreshEntity(doll);
                    n.putUUID("parasiteEntity", doll.getUUID());
                    n.putBoolean("parasiteArmed", true);
                    tell(p, "โจมตีผู้เล่นเป้าหมายเพื่อเชื่อมหุ่นกาฝาก");
                }
                return InteractionResultHolder.success(stack);
            }
            case "heart_doll" -> {
                if (covenant(p)) { release(p, "heart"); return InteractionResultHolder.success(stack); }
                ServerPlayer target = aimed(p);
                if (target == null || n.getBoolean("parasiteArmed") || hasParasitePartner(p) || heartOwner(p) != null || covenant(target)
                        || heartOwner(target) != null) return InteractionResultHolder.fail(stack);
                // Only one incoming covenant can protect a player at a time.
                for (ServerPlayer other : p.server.getPlayerList().getPlayers())
                    if (resolve(other, "heart") == target) return InteractionResultHolder.fail(stack);
                n.putUUID("heart", target.getUUID());
                n.putString("heartName", target.getScoreboardName());
                n.putBoolean("heartUsed", false);
                tell(p, "พันธสัญญาหัวใจเชื่อมกับ " + target.getScoreboardName());
                return InteractionResultHolder.success(stack);
            }
            case "domain_talisman" -> {
                if (n.getLong("domain") > now(p)) return InteractionResultHolder.fail(stack);
                n.putLong("domain", now(p) + ThidaConfig.DOMAIN_DURATION.get() * 20L);
                n.putDouble("domainX", p.getX()); n.putDouble("domainY", p.getY()); n.putDouble("domainZ", p.getZ());
                n.putString("domainDimension", p.level().dimension().location().toString());
                n.putLong("domainStarted", now(p));
                n.putBoolean("domainFx", true);
                ThidaDomainFx.open(p.serverLevel(), domainCenter(p));
                syncDomainVisual(p, true);
                n.putLong("cd_domain_talisman", n.getLong("domain") + ThidaConfig.DOMAIN_COOLDOWN.get() * 20L);
                return InteractionResultHolder.success(stack);
            }
            default -> { return InteractionResultHolder.pass(stack); }
        }

    }
    private static void blessing(ServerPlayer caster, ServerPlayer p, int mode) {
        if (mode == 0) effect(p, MobEffects.DAMAGE_RESISTANCE, seconds(), 0);
        else if (mode == 1) cleanse(p);
        else { effect(p, MobEffects.MOVEMENT_SPEED, seconds(), 1); addonHeal(caster, p, ThidaConfig.HEAL_AMOUNT.get().floatValue()); }
    }
    private static void curse(ServerPlayer p, int mode) {
        if (ThidaBonds.warded(p)) return;
        if (mode == 1) {
            state(p).putLong("bleed", now(p) + seconds());
            effect(p, MobEffects.BLINDNESS, seconds(), 0);
        } else if (mode == 0) {
            effect(p, MobEffects.WEAKNESS, seconds(), 1);
            state(p).putLong("weakUntil", now(p) + seconds());
            slow(p, seconds());
        } else state(p).putLong("vulnerableUntil", now(p) + seconds());
    }
    private static void slow(ServerPlayer p, int duration) {
        state(p).putLong("slow", now(p) + duration);
        var attribute = p.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute != null && attribute.getModifier(SLOW) == null)
            attribute.addTransientModifier(new AttributeModifier(SLOW, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static boolean inDomain(ServerPlayer owner, Entity target) {
        return isThida(owner) && state(owner).getLong("domain") > now(owner)
                && state(owner).getString("domainDimension").equals(target.level().dimension().location().toString())
                && target.position().distanceToSqr(domainCenter(owner)) <= Math.pow(ThidaConfig.DOMAIN_RADIUS.get(), 2);
    }
    private static Vec3 domainCenter(ServerPlayer p) {
        CompoundTag n = state(p);
        return new Vec3(n.getDouble("domainX"), n.getDouble("domainY"), n.getDouble("domainZ"));
    }
    @SubscribeEvent
    public static void attack(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (state(event.getEntity()).getLong("invulnerable") > now(event.getEntity())) {
            event.setCanceled(true); return;
        }
        if (!transfer && event.getSource().getEntity() instanceof ServerPlayer p
                && event.getSource().getDirectEntity() == p && heartOwner(p) != null) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingIncomingDamageEvent event) {
        if (!transfer && state(event.getEntity()).getLong("vulnerableUntil") > now(event.getEntity()))
            event.setAmount(event.getAmount() * (1 + ThidaConfig.VULNERABILITY.get().floatValue()));
        if (!transfer && event.getSource().getEntity() instanceof ServerPlayer attacker
                && state(attacker).getLong("weakUntil") > now(attacker))
            event.setAmount(event.getAmount() * 0.7F);
        if (!transfer && event.getEntity() instanceof ServerPlayer p && inDomain(p, p))
            event.setAmount(CombatRules.domainDamage(event.getAmount(), 1 - ThidaConfig.DOMAIN_REDUCTION.get()));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || event.getNewDamage() <= 0) return;
        if (state(victim).getLong("invulnerable") > now(victim)) { event.setNewDamage(0); return; }
        if (transfer) {
            ownerDamaged(victim);
            event.setNewDamage(protect(victim, event.getNewDamage()));
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            if (heartOwner(attacker) != null && event.getSource().getDirectEntity() == attacker) { event.setNewDamage(0); return; }
            if (isThida(attacker)) {
                boolean melee = event.getSource().getDirectEntity() == attacker;
                if (melee && resolve(attacker, "parasite") == null) {
                    if (state(attacker).getBoolean("parasiteArmed")
                            && !covenant(attacker)
                            && state(attacker).getLong("cd_parasite_doll") <= now(attacker)
                            && attacker.distanceToSqr(victim) <= Math.pow(ThidaConfig.BOND_RANGE.get(), 2)
                            && !hasParasitePartner(attacker) && !hasParasitePartner(victim)) {
                        state(attacker).putUUID("parasite", victim.getUUID());
                        state(attacker).putString("parasiteName", victim.getScoreboardName());
                        state(attacker).putBoolean("parasiteArmed", false);
                        effect(victim, MobEffects.MOVEMENT_SPEED, 200, 1);
                        effect(victim, MobEffects.DAMAGE_RESISTANCE, 200, 0);
                        tell(attacker, "กาฝากเชื่อมกับ " + victim.getScoreboardName());
                    }
                }
                if (inDomain(attacker, victim)) {
                    effect(victim, MobEffects.WEAKNESS, seconds(), 0);
                    state(victim).putLong("weakUntil", now(victim) + seconds());
                    effect(victim, MobEffects.BLINDNESS, seconds(), 0);
                }
            }
            for (ServerPlayer owner : victim.server.getPlayerList().getPlayers()) {
                if (dollsAllowed(owner) && resolve(owner, "narotra_doll") == attacker
                        && inDomain(owner, attacker) && inDomain(owner, victim)) {
                    // Domain curse is temporary; it does not overwrite the one manually linked target.
                    curse(victim, 0); curse(victim, 1); curse(victim, 2);
                }
            }
        }
        ownerDamaged(victim);
        float amount = event.getNewDamage();
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
                release(owner, "parasite");
                // Defer healing until the pending vanilla damage has actually been applied.
                state(owner).putBoolean("parasiteReward", true);
                effect(owner, MobEffects.MOVEMENT_SPEED, 40, 1);
                effect(partner, MobEffects.MOVEMENT_SPEED, 200, 1);
                effect(partner, MobEffects.DAMAGE_RESISTANCE, 200, 0);
            }
            break;
        }
        event.setNewDamage(protect(victim, amount));
    }
    private static boolean hasParasitePartner(ServerPlayer p) {
        if (resolve(p, "parasite") != null) return true;
        for (ServerPlayer owner : p.server.getPlayerList().getPlayers())
            if (resolve(owner, "parasite") == p) return true;
        return false;
    }
    private static void ownerDamaged(ServerPlayer p) {
        if (covenant(p)) {
            release(p, "heart");
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
                    ResourceLocation.fromNamespaceAndPath("thida", "shared"));
            var type = p.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(key);
            p.hurt(new DamageSource(type, source.getDirectEntity(), source.getEntity()), amount);
        } finally { transfer = previousTransfer; }
    }
    @SubscribeEvent
    public static void healing(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !internalHeal && heartOwner(p) != null) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (isThida(p)) ThidaBonds.adoptPassives(p);
        CompoundTag n = state(p);
        long time = now(p);
        if (n.getLong("slow") <= time && p.getAttribute(Attributes.ATTACK_SPEED) != null)
            p.getAttribute(Attributes.ATTACK_SPEED).removeModifier(SLOW);
        if (n.getLong("bleed") > time && time % 20 == 0)
            p.hurt(p.damageSources().magic(), 1);
        if (!isThida(p)) {
            if (n.getBoolean("active") || n.hasUUID("narotra_doll") || n.hasUUID("nilakan_doll")) cleanup(p);
            syncHud(p);
            return;
        }
        n.putBoolean("active", true);
        if (!n.getBoolean("passiveKit")) {
            for (String item : new String[]{"nilakan_doll", "narotra_doll"}) {
                ItemStack doll = new ItemStack(ThidaMod.ABILITIES.get(item).get());
                if (!p.getInventory().contains(doll) && !p.addItem(doll)) p.drop(doll, false);
            }
            n.putBoolean("passiveKit", true);
        }
        for (String key : new String[]{"parasite", "heart"})
            if (n.hasUUID(key) && resolve(p, key) == null) {
                release(p, key);
            }
        if (n.hasUUID("parasiteEntity")) {
            Entity doll = p.serverLevel().getEntity(n.getUUID("parasiteEntity"));
            if (doll instanceof net.minecraft.world.entity.decoration.ArmorStand stand) ThidaParasiteVisual.follow(stand, p);
        }
        if (n.getBoolean("parasiteReward")) {
            n.remove("parasiteReward");
            if (p.isAlive()) addonHeal(p, p, ThidaConfig.PARASITE_BREAK_HEAL.get().floatValue());
        }
        ThidaPassives.tick(p, n, time);
        ServerPlayer marked = resolve(p, "narotra_doll");
        if (n.getLong("domain") > time) {
            if (time % 10 == 0) syncDomainVisual(p, true);
            if (inDomain(p, p)) cleanse(p);
            if (marked != null && dollsAllowed(p) && inDomain(p, marked)) {
                effect(marked, MobEffects.DAMAGE_BOOST, 2, 1);
                effect(marked, MobEffects.DAMAGE_RESISTANCE, 2, 1);
            }
            var domainDimension = ResourceLocation.tryParse(n.getString("domainDimension"));
            var domainLevel = domainDimension == null ? null : p.server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    domainDimension));
            if (domainLevel != null) ThidaDomainFx.tick(domainLevel, domainCenter(p), ThidaConfig.DOMAIN_RADIUS.get(),
                    n.contains("domainStarted") ? time - n.getLong("domainStarted") : 80 + time % 20);
        } else if (n.getBoolean("domainFx")) closeDomainFx(p);
        syncHud(p);
    }
    private static boolean looking(ServerPlayer from, ServerPlayer to) {
        return from.hasLineOfSight(to) && from.getLookAngle().dot(to.getEyePosition().subtract(from.getEyePosition()).normalize()) > 0.97;
    }
    private static void closeDomainFx(ServerPlayer p) {
        CompoundTag n = state(p);
        syncDomainVisual(p, false);
        if (!n.getBoolean("domainFx")) return;
        var dimension = ResourceLocation.tryParse(n.getString("domainDimension"));
        var level = dimension == null ? null : p.server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimension));
        if (level != null) ThidaDomainFx.close(level, domainCenter(p), ThidaConfig.DOMAIN_RADIUS.get());
        n.remove("domainFx");
    }
    private static void syncDomainVisual(ServerPlayer p, boolean active) {
        CompoundTag state = state(p), packet = new CompoundTag();
        packet.putUUID("owner", p.getUUID()); packet.putBoolean("active", active);
        packet.putString("dimension", state.getString("domainDimension"));
        Vec3 c = domainCenter(p);
        double radius = ThidaConfig.DOMAIN_RADIUS.get();
        packet.putDouble("x", c.x); packet.putDouble("y", c.y); packet.putDouble("z", c.z);
        packet.putDouble("radius", radius);
        packet.putLong("age", state.contains("domainStarted") ? now(p)-state.getLong("domainStarted") : 80);
        for (ServerPlayer observer : p.server.getPlayerList().getPlayers()) {
            if (observer instanceof net.neoforged.neoforge.common.util.FakePlayer) continue;
            if (!active || observer.level().dimension().location().toString().equals(state.getString("domainDimension"))
                    && observer.position().distanceToSqr(c) <= Math.pow(radius + 96, 2)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(observer,
                        new ThidaNetwork.DomainSnapshot(packet));
            }
        }
    }
    private static void cleanup(ServerPlayer p) {
        ThidaPassives.onOwnerUnavailable(p);
        closeDomainFx(p);
        for (ServerPlayer other : p.server.getPlayerList().getPlayers()) {
            CompoundTag n = state(other);
            for (String key : new String[]{"heart", "parasite"})
                if (n.hasUUID(key) && n.getUUID(key).equals(p.getUUID())) {
                    release(other, key);
                }
        }
        CompoundTag n = state(p);
        if (n.hasUUID("heart")) release(p, "heart");
        if (n.hasUUID("parasite") || n.getBoolean("parasiteArmed")) release(p, "parasite");
        // Retain cooldown deadlines so death/origin toggling cannot reset abilities.
        if (!isThida(p)) ThidaPassives.originLost(p);
        for (String key : new ArrayList<>(n.getAllKeys()))
            if (!key.startsWith("cd_") && !(isThida(p) && (key.startsWith("hud_") || passive(key) || key.startsWith("passive_") || key.equals("nilakan_dollName") || key.equals("narotra_dollName") || key.equals("passiveKit")))) n.remove(key);
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
        for (String key : old.getAllKeys()) if (key.startsWith("cd_") || key.startsWith("hud_") || key.startsWith("passive_") || passive(key) || key.equals("nilakan_dollName") || key.equals("narotra_dollName")) copy.put(key, old.get(key).copy());
        event.getEntity().getPersistentData().put(ROOT, copy);
    }
    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && (state(p).hasUUID("parasite") || state(p).getBoolean("parasiteArmed")))
            release(p, "parasite");
    }
    @SubscribeEvent
    public static void tossed(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p) ThidaPassives.onToss(p, event.getEntity().getItem());
    }
}


