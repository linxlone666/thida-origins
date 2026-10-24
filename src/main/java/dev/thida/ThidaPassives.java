package dev.thida;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.minecraft.core.registries.BuiltInRegistries;

/** Server-authoritative lifecycle and effects for Narotra and Nilakan only. */
@EventBusSubscriber(modid = "thida")
public final class ThidaPassives {
    private static final String INSTANCE_TAG = "thida_passive_instance";
    private static final int MAX_RANGE = 200;
    private static final int NAROTRA_START_RANGE = 15;
    private static final int NILAKAN_START_RANGE = 10;
    private static final int GAZE_TICKS = 200;
    private static final int MANA_INTERVAL = 20 * 20;
    private static final int NAROTRA_CLEANSE_INTERVAL = 20 * 60 * 20;
    private static final int NILAKAN_CLEANSE_INTERVAL = 10 * 60 * 20;
    private static final ResourceLocation NILAKAN_DAMAGE = ResourceLocation.fromNamespaceAndPath("thida", "nilakan_damage");
    private static final ResourceLocation MOVEMENT_SPEED = ResourceLocation.fromNamespaceAndPath("thida", "nilakan_speed");
    private ThidaPassives() {}

    private static CompoundTag data(ServerPlayer p) { return ThidaEvents.state(p); }
    private static ItemStack heldStack(ServerPlayer p, String item) {
        if (ThidaEvents.id(p.getMainHandItem()).equals(item)) return p.getMainHandItem();
        if (ThidaEvents.id(p.getOffhandItem()).equals(item)) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }
    private static InteractionHand heldHand(ServerPlayer p, String item) {
        return ThidaEvents.id(p.getOffhandItem()).equals(item) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
    private static UUID instance(ItemStack stack) {
        CompoundTag tag = ThidaItemData.read(stack);
        if (!tag.hasUUID(INSTANCE_TAG)) { tag.putUUID(INSTANCE_TAG, UUID.randomUUID()); ThidaItemData.putUUID(stack, INSTANCE_TAG, tag.getUUID(INSTANCE_TAG)); }
        return tag.getUUID(INSTANCE_TAG);
    }
    private static boolean hasInstance(ServerPlayer p, String item, UUID wanted) {
        if (wanted == null) return false;
        for (ItemStack stack : p.getInventory().items)
            if (ThidaEvents.id(stack).equals(item) && !ThidaItemData.read(stack).isEmpty() && ThidaItemData.read(stack).hasUUID(INSTANCE_TAG)
                    && ThidaItemData.read(stack).getUUID(INSTANCE_TAG).equals(wanted)) return true;
        for (ItemStack stack : p.getInventory().offhand)
            if (ThidaEvents.id(stack).equals(item) && !ThidaItemData.read(stack).isEmpty() && ThidaItemData.read(stack).hasUUID(INSTANCE_TAG)
                    && ThidaItemData.read(stack).getUUID(INSTANCE_TAG).equals(wanted)) return true;
        for (ItemStack stack : p.getInventory().armor)
            if (ThidaEvents.id(stack).equals(item) && !ThidaItemData.read(stack).isEmpty() && ThidaItemData.read(stack).hasUUID(INSTANCE_TAG)
                    && ThidaItemData.read(stack).getUUID(INSTANCE_TAG).equals(wanted)) return true;
        return false;
    }
    private static ServerPlayer target(ServerPlayer owner, String key) {
        if (!data(owner).hasUUID(key)) return null;
        return owner.server.getPlayerList().getPlayer(data(owner).getUUID(key));
    }
    private static boolean available(ServerPlayer owner, ServerPlayer target) {
        return ThidaEvents.isThida(owner) && owner.isAlive() && !owner.isSpectator()
                && target != null && target.isAlive() && !target.isSpectator()
                && owner.level() == target.level() && withinRange(owner.distanceToSqr(target), MAX_RANGE);
    }
    private static void message(ServerPlayer p, String thai) {
        ThidaEvents.tell(p, thai);
    }
    private static Vec3 position(ServerPlayer p) { return p.position(); }

    private static boolean isLookingAt(ServerPlayer owner, ServerPlayer target) {
        Vec3 look = target.getEyePosition().subtract(owner.getEyePosition()).normalize();
        return owner.hasLineOfSight(target) && owner.getLookAngle().dot(look) > 0.97;
    }
    private static ServerPlayer aimedNarotra(ServerPlayer owner) {
        ServerPlayer best = null;
        double bestDistance = NAROTRA_START_RANGE;
        Vec3 from = owner.getEyePosition();
        Vec3 end = from.add(owner.getLookAngle().scale(NAROTRA_START_RANGE));
        for (ServerPlayer p : owner.server.getPlayerList().getPlayers()) {
            if (p == owner || p.level() != owner.level() || !p.isAlive() || p.isSpectator()
                    || !withinRange(owner.distanceToSqr(p), NAROTRA_START_RANGE)
                    || !owner.hasLineOfSight(p)) continue;
            var hit = p.getBoundingBox().inflate(0.35).clip(from, end);
            if (hit.isPresent() && from.distanceTo(hit.get()) <= bestDistance) {
                bestDistance = from.distanceTo(hit.get()); best = p;
            }
        }
        return best;
    }

    public static void tick(ServerPlayer p, CompoundTag n, long time) {
        if (!ThidaEvents.isThida(p)) return;
        tickAt(p, n, passiveTime(p));
    }

    static void tickAt(ServerPlayer p, CompoundTag n, long time) {
        if (!ThidaEvents.isThida(p)) return;
        updateNarotraGaze(p, n, time);
        updateBond(p, n, "narotra_doll", time);
        updateBond(p, n, "nilakan_doll", time);
    }

    private static void updateNarotraGaze(ServerPlayer p, CompoundTag n, long time) {
        if (n.hasUUID("narotra_doll") || !p.isAlive() || p.isSpectator()) {
            cancelGaze(p, n, "ALREADY_LINKED");
            if (n.hasUUID("narotra_doll") && !heldStack(p, "narotra_doll").isEmpty()
                    && !n.getBoolean("passive_narotra_retarget_notice")) {
                message(p, "ต้องยกเลิกสายสัมพันธ์ก่อนเปลี่ยนเป้าหมาย");
                n.putBoolean("passive_narotra_retarget_notice", true);
            }
            return;
        }
        ItemStack held = heldStack(p, "narotra_doll");
        if (held.isEmpty()) { cancelGaze(p, n, "ITEM_SWITCHED"); return; }
        ServerPlayer current = null;
        if (n.hasUUID("gazeTarget")) {
            if (ThidaItemData.read(held).isEmpty() || !ThidaItemData.read(held).hasUUID(INSTANCE_TAG)
                    || !ThidaItemData.read(held).getUUID(INSTANCE_TAG).equals(n.getUUID("passive_gaze_item"))) {
                cancelGaze(p, n, "ITEM_SWITCHED");
                return;
            }
            current = p.server.getPlayerList().getPlayer(n.getUUID("gazeTarget"));
        }
        if (current != null) {
            String reason = null;
            if (!current.isAlive() || current.isSpectator()) reason = "TARGET_UNAVAILABLE";
            else if (current.level() != p.level() || !withinRange(p.distanceToSqr(current), NAROTRA_START_RANGE)) reason = "OUT_OF_RANGE";
            else if (!p.hasLineOfSight(current)) reason = "OBSTRUCTED";
            else if (!isLookingAt(p, current)) reason = "LOOK_LOST";
            if (reason != null) { cancelGaze(p, n, reason); return; }
        } else current = aimedNarotra(p);
        if (current == null) { cancelGaze(p, n, "NO_TARGET_IN_RANGE_OR_SIGHT"); return; }
        if (!n.hasUUID("gazeTarget")) {
            UUID itemId = instance(held);
            n.putUUID("gazeTarget", current.getUUID());
            n.putUUID("passive_gaze_item", itemId);
            n.putInt("gaze", 0);
            ThidaPassiveEffects.emit("NAROTRA_ATTEMPT_STARTED", "NAROTRA", p, current, position(p), heldHand(p, "narotra_doll"), "");
        }
        int gaze = n.getInt("gaze") + 1;
        n.putInt("gaze", gaze);
        if (gaze >= GAZE_TICKS) {
            UUID itemId = n.getUUID("passive_gaze_item");
            n.putUUID("narotra_doll", current.getUUID());
            n.putString("narotra_dollName", current.getScoreboardName());
            n.putUUID("passive_narotra_item", itemId);
            n.putLong("passive_narotra_cleanse_at", time);
            n.putBoolean("passive_narotra_active", true);
            n.remove("gaze"); n.remove("gazeTarget"); n.remove("passive_gaze_item");
            message(p, "นรตราเชื่อมกับ " + current.getScoreboardName());
            ThidaPassiveEffects.emit("NAROTRA_LINKED", "NAROTRA", p, current, position(p), heldHand(p, "narotra_doll"), "");
            grantManaIfReady(p, time, p, current);
            grantManaIfReady(current, time, p, current);
        }
    }

    private static void cancelGaze(ServerPlayer p, CompoundTag n, String reason) {
        if (!n.hasUUID("gazeTarget")) { n.remove("gaze"); n.remove("passive_gaze_item"); return; }
        ServerPlayer old = p.server.getPlayerList().getPlayer(n.getUUID("gazeTarget"));
        ThidaPassiveEffects.emit("NAROTRA_LINK_FAILED", "NAROTRA", p, old, position(p), heldHand(p, "narotra_doll"), reason);
        ThidaPassiveEffects.emit("LINK_CANCELLED", "NAROTRA", p, old, position(p), heldHand(p, "narotra_doll"), reason);
        String thai = switch (reason) {
            case "ITEM_SWITCHED" -> "เปลี่ยนหรือเก็บหุ่นระหว่างเชื่อม";
            case "LOOK_LOST" -> "หันสายตาออกจากเป้าหมาย";
            case "OBSTRUCTED" -> "มีสิ่งกีดขวางระหว่างกัน";
            case "OUT_OF_RANGE" -> "เป้าหมายอยู่นอกระยะ 15 บล็อก";
            case "DOLL_TOSSED" -> "หุ่นหลุดจากตัว";
            case "ORIGIN_LOST" -> "ผู้ใช้ไม่ได้เป็นธิดาแล้ว";
            default -> "ไม่เข้าเงื่อนไขการเชื่อม";
        };
        message(p, "การเชื่อมนรตราถูกยกเลิก: " + thai);
        n.remove("gaze"); n.remove("gazeTarget"); n.remove("passive_gaze_item");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner) || owner.level().isClientSide
                || !(event.getTarget() instanceof ServerPlayer target)) return;
        ItemStack held = heldStack(owner, "nilakan_doll");
        if (held.isEmpty()) return;
        ThidaPassiveEffects.emit("NILAKAN_ATTEMPT_STARTED", "NILAKAN", owner, target, position(owner), heldHand(owner, "nilakan_doll"), "");
        CompoundTag n = data(owner);
        if (!ThidaEvents.isThida(owner) || !owner.isAlive() || owner.isSpectator()) {
            failNilakan(owner, target, "ORIGIN_REQUIRED"); return;
        }
        if (owner == target || !withinRange(owner.distanceToSqr(target), NILAKAN_START_RANGE)) {
            failNilakan(owner, target, "OUT_OF_RANGE"); return;
        }
        if (n.hasUUID("nilakan_doll")) {
            failNilakan(owner, target, "UNLINK_BEFORE_RETARGETING"); return;
        }
        UUID itemId = instance(held);
        n.putUUID("nilakan_doll", target.getUUID());
        n.putString("nilakan_dollName", target.getScoreboardName());
        n.putUUID("passive_nilakan_item", itemId);
        n.putLong("passive_nilakan_cleanse_at", passiveTime(owner));
        n.putBoolean("passive_nilakan_active", available(owner, target));
        message(owner, "นิลกาลเชื่อมกับ " + target.getScoreboardName());
        ThidaPassiveEffects.emit("NILAKAN_LINKED", "NILAKAN", owner, target, position(owner), heldHand(owner, "nilakan_doll"), "");
    }

    private static void failNilakan(ServerPlayer owner, ServerPlayer target, String reason) {
        String thai = switch (reason) {
            case "ORIGIN_REQUIRED" -> "ต้องเลือกเผ่ากำเนิดธิดา";
            case "OUT_OF_RANGE" -> "เป้าหมายต้องอยู่ในระยะ 10 บล็อก";
            case "UNLINK_BEFORE_RETARGETING" -> "ยกเลิกสายสัมพันธ์เดิมก่อนเลือกเป้าหมายใหม่";
            default -> "ไม่เข้าเงื่อนไขการเชื่อม";
        };
        message(owner, "เชื่อมนิลกาลไม่ได้: " + thai);
        ThidaPassiveEffects.emit("NILAKAN_LINK_FAILED", "NILAKAN", owner, target, position(owner), InteractionHand.MAIN_HAND, reason);
        ThidaPassiveEffects.emit("LINK_CANCELLED", "NILAKAN", owner, target, position(owner), InteractionHand.MAIN_HAND, reason);
    }

    private static void updateBond(ServerPlayer owner, CompoundTag n, String key, long time) {
        if (!n.hasUUID(key)) { removeNilakanBonus(owner, key); return; }
        ServerPlayer target = target(owner, key);
        boolean active = available(owner, target)
                && hasInstance(owner, key, n.hasUUID("passive_" + (key.equals("narotra_doll") ? "narotra" : "nilakan") + "_item")
                        ? n.getUUID("passive_" + (key.equals("narotra_doll") ? "narotra" : "nilakan") + "_item") : null);
        String stateKey = "passive_" + (key.equals("narotra_doll") ? "narotra" : "nilakan") + "_active";
        boolean wasActive = n.getBoolean(stateKey);
        if (active != wasActive) {
            n.putBoolean(stateKey, active);
            ThidaPassiveEffects.emit(active ? "LINK_RESUMED" : "LINK_PAUSED", key.equals("narotra_doll") ? "NAROTRA" : "NILAKAN",
                    owner, target, position(owner), InteractionHand.MAIN_HAND, active ? "TARGET_AVAILABLE" : pauseReason(owner, target));
        }
        if (!active) { removeNilakanBonus(owner, key); return; }
        if (key.equals("narotra_doll")) {
            long next = data(owner).getLong("passive_narotra_cleanse_at");
            if (cleanseReady(time, next, hasThidaDebuff(owner))) {
                ThidaEvents.cleanse(owner);
                data(owner).putLong("passive_narotra_cleanse_at", time + NAROTRA_CLEANSE_INTERVAL);
                ThidaPassiveEffects.emit("NAROTRA_DEBUFF_CLEANSED", "NAROTRA", owner, target, position(owner), InteractionHand.MAIN_HAND, "");
            }
            if (data(owner).getLong("passive_next_mana") <= time) grantManaIfReady(owner, time, owner, target);
            if (data(target).getLong("passive_next_mana") <= time) grantManaIfReady(target, time, owner, target);
        } else {
            applyNilakanBonus(owner);
            long next = data(owner).getLong("passive_nilakan_cleanse_at");
            if (cleanseReady(time, next, hasThidaDebuff(owner))) {
                ThidaEvents.cleanse(owner);
                data(owner).putLong("passive_nilakan_cleanse_at", time + NILAKAN_CLEANSE_INTERVAL);
                ThidaPassiveEffects.emit("NILAKAN_DEBUFF_CLEANSED", "NILAKAN", owner, target, position(owner), InteractionHand.MAIN_HAND, "");
            }
        }
    }

    private static String pauseReason(ServerPlayer owner, ServerPlayer target) {
        if (target == null) return "TARGET_OFFLINE";
        if (!target.isAlive() || target.isSpectator()) return "TARGET_UNAVAILABLE";
        if (owner.level() != target.level()) return "DIMENSION_CHANGED";
        return "OUT_OF_RANGE";
    }
    private static boolean hasThidaDebuff(ServerPlayer p) {
        for (var effect : p.getActiveEffects()) if (effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) return true;
        CompoundTag n = data(p);
        return n.getLong("bleed") > ThidaEvents.now(p) || n.getLong("slow") > ThidaEvents.now(p)
                || n.getLong("weakUntil") > ThidaEvents.now(p) || n.getLong("vulnerableUntil") > ThidaEvents.now(p)
                || p.isOnFire();
    }

    private static void grantManaIfReady(ServerPlayer p, long time, ServerPlayer linkOwner, ServerPlayer linkTarget) {
        CompoundTag n = data(p);
        if (n.getLong("passive_next_mana") > time) return;
        var maxMana = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "max_mana")).orElse(null);
        if (maxMana == null || p.getAttribute(maxMana) == null) return;
        float amount = manaRestoreAmount(p.getAttributeValue(maxMana));
        if (amount <= 0 || !changeIronMana(p, amount, (float) p.getAttributeValue(maxMana))) return;
        n.putLong("passive_next_mana", time + MANA_INTERVAL);
        ThidaPassiveEffects.emit("NAROTRA_MANA_RESTORED", "NAROTRA", linkOwner, linkTarget, position(p), heldHand(linkOwner, "narotra_doll"), "");
    }

    private static boolean changeIronMana(ServerPlayer player, float amount, float maximum) {
        if (!net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) return false;
        return ThidaIronMana.restore(player, amount, maximum);
    }

    private static void applyNilakanBonus(ServerPlayer p) {
        addModifier(p, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED, "Thida Nilakan speed", 0.05D);
        addModifier(p, Attributes.ATTACK_DAMAGE, NILAKAN_DAMAGE, "Thida Nilakan melee damage", 0.05D);
    }
    private static void addModifier(ServerPlayer p, net.minecraft.core.Holder<Attribute> attribute,
                                    ResourceLocation id, String name, double amount) {
        var instance = p.getAttribute(attribute);
        if (instance != null && instance.getModifier(id) == null)
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static void removeNilakanBonus(ServerPlayer p, String key) {
        if (!key.equals("nilakan_doll")) return;
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        var damage = p.getAttribute(Attributes.ATTACK_DAMAGE);
        if (speed != null) speed.removeModifier(MOVEMENT_SPEED);
        if (damage != null) damage.removeModifier(NILAKAN_DAMAGE);
    }

    public static void unlink(ServerPlayer owner, String key, String reason) {
        CompoundTag n = data(owner);
        if (!n.hasUUID(key)) return;
        ServerPlayer target = target(owner, key);
        String passive = key.equals("narotra_doll") ? "NAROTRA" : "NILAKAN";
        ItemStack held = heldStack(owner, key);
        InteractionHand hand = held == owner.getOffhandItem() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ThidaPassiveEffects.emit("LINK_ENDED", passive, owner, target, position(owner), hand, reason);
        n.remove(key); n.remove(key + "Name"); n.remove("passive_" + (key.equals("narotra_doll") ? "narotra" : "nilakan") + "_item");
        n.remove("passive_" + (key.equals("narotra_doll") ? "narotra" : "nilakan") + "_active");
        removeNilakanBonus(owner, key);
        if (reason.equals("DOLL_TOSSED"))
            message(owner, "หุ่นหลุดจากตัว สายสัมพันธ์" + (key.equals("narotra_doll") ? "นรตรา" : "นิลกาล") + " ถูกยกเลิก");
        else if (reason.equals("ORIGIN_LOST"))
            message(owner, "เสียเผ่ากำเนิดธิดาแล้ว สายสัมพันธ์" + (key.equals("narotra_doll") ? "นรตรา" : "นิลกาล") + " สิ้นสุด");
        else message(owner, (key.equals("narotra_doll") ? "นรตรา" : "นิลกาล") + " ยกเลิกสายสัมพันธ์แล้ว");
    }

    public static void originLost(ServerPlayer p) {
        CompoundTag n = data(p);
        cancelGaze(p, n, "ORIGIN_LOST");
        unlink(p, "narotra_doll", "ORIGIN_LOST");
        unlink(p, "nilakan_doll", "ORIGIN_LOST");
        removeNilakanBonus(p, "nilakan_doll");
    }

    public static void onToss(ServerPlayer p, ItemStack stack) {
        String key = ThidaEvents.id(stack);
        if (!key.equals("narotra_doll") && !key.equals("nilakan_doll")) return;
        CompoundTag n = data(p);
        String token = "passive_" + (key.equals("narotra_doll") ? "narotra" : "nilakan") + "_item";
        if (!ThidaItemData.read(stack).isEmpty() && ThidaItemData.read(stack).hasUUID(INSTANCE_TAG) && n.hasUUID(token)
                && ThidaItemData.read(stack).getUUID(INSTANCE_TAG).equals(n.getUUID(token))) {
            ServerPlayer target = target(p, key);
            unlink(p, key, "DOLL_TOSSED");
        }
        if (key.equals("narotra_doll") && n.hasUUID("passive_gaze_item") && !ThidaItemData.read(stack).isEmpty()
                && ThidaItemData.read(stack).hasUUID(INSTANCE_TAG) && ThidaItemData.read(stack).getUUID(INSTANCE_TAG).equals(n.getUUID("passive_gaze_item")))
            cancelGaze(p, n, "DOLL_TOSSED");
    }

    public static boolean isBondAvailable(ServerPlayer owner, String key) {
        if (!ThidaEvents.isThida(owner)) return false;
        CompoundTag n = data(owner);
        String token = "passive_" + (key.equals("narotra_doll") ? "narotra" : "nilakan") + "_item";
        UUID itemId = n.hasUUID(token) ? n.getUUID(token) : null;
        return available(owner, target(owner, key)) && hasInstance(owner, key, itemId);
    }

    static float manaRestoreAmount(double maxMana) { return (float) (Math.max(0, maxMana) * 0.05D); }
    static boolean withinRange(double distanceSqr, double range) { return distanceSqr <= range * range; }
    static boolean cleanseReady(long now, long readyAt, boolean hasDebuff) { return readyAt <= now && hasDebuff; }

    public static void onOwnerUnavailable(ServerPlayer owner) {
        CompoundTag n = data(owner);
        cancelGaze(owner, n, "OWNER_UNAVAILABLE");
        for (String key : new String[]{"narotra_doll", "nilakan_doll"}) {
            if (!n.hasUUID(key)) continue;
            String activeKey = "passive_" + (key.equals("narotra_doll") ? "narotra" : "nilakan") + "_active";
            if (n.getBoolean(activeKey)) {
                ThidaPassiveEffects.emit("LINK_PAUSED", key.equals("narotra_doll") ? "NAROTRA" : "NILAKAN",
                        owner, target(owner, key), position(owner), heldHand(owner, key), "OWNER_UNAVAILABLE");
                n.putBoolean(activeKey, false);
            }
        }
        removeNilakanBonus(owner, "nilakan_doll");
    }

    private static long passiveTime(ServerPlayer p) {
        return p.getServer().overworld().getGameTime();
    }
}
