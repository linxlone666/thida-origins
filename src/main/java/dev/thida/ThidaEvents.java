package dev.thida;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server entry points and legacy save/domain compatibility. Current passive, spell, bond and
 * attributed healing rules live in ThidaPassives, ThidaSpells, ThidaBonds and ThidaHealing.
 */
@EventBusSubscriber(modid = "thida")
public final class ThidaEvents {
  private static final String ROOT = "thida_state";
  private static final ResourceLocation SLOW =
      ResourceLocation.fromNamespaceAndPath("thida", "curse_attack_speed");

  private ThidaEvents() {}

  public static boolean isThida(ServerPlayer p) {
    return ThidaOrigins.isThida(p);
  }

  /** Callers must enforce operator permission; these methods do not change existing effects. */
  public static int debugResetCooldowns(ServerPlayer p) {
    CompoundTag n = state(p);
    int count = 0;
    for (String key : new ArrayList<>(n.getAllKeys()))
      if (key.startsWith("cd_")) {
        n.remove(key);
        count++;
      }
    var data = ThidaBondData.get(p.server);
    var skill = data.player(p);
    for (String key : new ArrayList<>(skill.getAllKeys()))
      if (key.endsWith("Cooldown")
          || key.equals("passiveBlockedUntil")
          || key.startsWith("spellCd_")) {
        skill.remove(key);
        count++;
      }
    data.setDirty();
    return count;
  }

  public static void debugEndPowers(ServerPlayer p) {
    ThidaMayarom.endForDebug(p);
    ThidaBonds.endHeart(p.server, p.getUUID(), "DEBUG_END");
    ThidaBonds.breakParasite(p.server, p.getUUID(), "DEBUG_END", false);
    CompoundTag n = state(p);
    for (String key : new String[] {"nilakan_doll", "narotra_doll", "heart", "parasite"})
      if (n.hasUUID(key) || key.equals("parasite") && n.getBoolean("parasiteArmed")) {
        if (passive(key)) ThidaPassives.unlink(p, key, "DEBUG_END");
        else release(p, key);
      }
    if (n.getLong("domain") > now(p)) {
      closeDomainFx(p);
      n.remove("domain");
      n.putLong("cd_domain_talisman", now(p) + ThidaConfig.DOMAIN_COOLDOWN.get() * 20L);
    }
    n.remove("parasiteReward");
    n.remove("gaze");
    n.remove("gazeTarget");
    n.remove("menuDoll");
  }

  static CompoundTag state(LivingEntity e) {
    CompoundTag root = e.getPersistentData();
    if (!root.contains(ROOT)) root.put(ROOT, new CompoundTag());
    return root.getCompound(ROOT);
  }

  static long now(Entity e) {
    return e.level().getGameTime();
  }

  static String id(ItemStack stack) {
    ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
    return key != null && key.getNamespace().equals("thida") ? key.getPath() : "";
  }

  static boolean holds(ServerPlayer p, String name) {
    return id(p.getMainHandItem()).equals(name) || id(p.getOffhandItem()).equals(name);
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

  static void tell(ServerPlayer p, String text) {
    p.displayClientMessage(net.minecraft.network.chat.Component.literal(text), true);
  }

  /** A player's own links and remaining times only; never broadcast private link state. */
  public static CompoundTag hudSnapshot(ServerPlayer p) {
    CompoundTag data = new CompoundTag(),
        n = state(p),
        cooldowns = new CompoundTag(),
        activeEffects = new CompoundTag();
    data.putString("player", p.getUUID().toString());
    data.putBoolean("thida", isThida(p));
    for (String key : new String[] {"nilakan_doll", "narotra_doll", "parasite", "heart"}) {
      CompoundTag link = new CompoundTag();
      if (isThida(p) && n.hasUUID(key)) {
        UUID uuid = n.getUUID(key);
        ServerPlayer target = p.server.getPlayerList().getPlayer(uuid);
        link.putString("id", uuid.toString());
        String name = target != null ? target.getScoreboardName() : n.getString(key + "Name");
        link.putString("name", name.isEmpty() ? uuid.toString().substring(0, 8) : name);
        link.putBoolean(
            "available",
            passive(key)
                ? ThidaPassives.isBondAvailable(p, key)
                : target != null && target.isAlive() && !target.isSpectator());
      }
      data.put(key, link);
    }
    if (isThida(p)) {
      data.putInt("releaseTotal", ThidaConfig.RELEASE_COOLDOWN.get() * 20);
      data.putInt("domainTotal", ThidaConfig.DOMAIN_DURATION.get() * 20);
      data.putInt("domainCooldownTotal", ThidaConfig.DOMAIN_COOLDOWN.get() * 20);
      for (String key : n.getAllKeys())
        if (key.startsWith("cd_")
            && !key.startsWith("cd_effect_nilakan_doll_")
            && !key.startsWith("cd_effect_narotra_doll_")
            && (ThidaMod.ABILITIES.containsKey(key.substring(3))
                || key.startsWith("cd_effect_nilakan_doll_")
                || key.startsWith("cd_effect_narotra_doll_")))
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
    }
    data.putLong("invulnerable", Math.max(0, n.getLong("invulnerable") - now(p)));
    data.put("cooldowns", cooldowns);
    data.put("activeEffects", activeEffects);
    ThidaBonds.hud(p, data);
    if (isThida(p)) ThidaMayarom.hud(p, data);
    return data;
  }

  private static void syncHud(ServerPlayer p) {
    if (p.tickCount % 5 != 0 || p instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
        p, new ThidaNetwork.HudSnapshot(hudSnapshot(p)));
  }

  /** Integration hook: external addon healing should use this to release covenants. */
  public static void addonHeal(ServerPlayer caster, ServerPlayer target, float amount) {
    ThidaHealing.apply(caster, target, amount, ThidaHealing.Kind.SKILL);
  }

  private static boolean passive(String item) {
    return item.equals("nilakan_doll") || item.equals("narotra_doll");
  }

  private static boolean dollsAllowed(ServerPlayer p) {
    return !state(p).getBoolean("parasiteArmed");
  }

  private static void release(ServerPlayer p, String key) {
    CompoundTag n = state(p);
    n.remove(key);
    String item =
        key.equals("heart") ? "heart_doll" : key.equals("parasite") ? "parasite_doll" : key;
    n.putLong("cd_" + item, now(p) + ThidaConfig.RELEASE_COOLDOWN.get() * 20L);
    if (key.equals("heart")) n.remove("heartUsed");
    if (key.equals("parasite")) {
      n.remove("parasiteArmed");
      if (n.hasUUID("parasiteEntity"))
        for (var level : p.server.getAllLevels()) {
          Entity doll = level.getEntity(n.getUUID("parasiteEntity"));
          if (doll != null) doll.discard();
        }
      n.remove("parasiteEntity");
    }
  }

  /**
   * Number-key requests are validated on the server; clients cannot choose another player's mark.
   */
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
    // Legacy numbered requests only retain opening/unlinking compatibility.
    // Skill 1 casting is handled exclusively by ThidaSpells.
  }

  public static InteractionResultHolder<ItemStack> use(
      ServerPlayer p, ItemStack stack, InteractionHand hand) {
    String item = id(stack);
    if (item.equals("parasite_doll") || item.equals("heart_doll")) {
      if (isThida(p))
        tell(
            p,
            item.equals("parasite_doll")
                ? ThidaMessages.text("ui.thida.events.use.1")
                : ThidaMessages.text("ui.thida.events.use.2"));
      return InteractionResultHolder.fail(stack);
    }
    if (!ThidaMod.ABILITIES.containsKey(item)) return InteractionResultHolder.pass(stack);
    if (!isThida(p) || !p.isAlive() || p.isSpectator()) return InteractionResultHolder.pass(stack);
    CompoundTag n = state(p);
    if (passive(item) && !dollsAllowed(p)) {
      tell(p, ThidaMessages.text("ui.thida.events.use.3"));
      return InteractionResultHolder.fail(stack);
    }
    if (passive(item)) return InteractionResultHolder.pass(stack);
    if (n.getLong("cd_" + item) > now(p)) {
      tell(p, ThidaMessages.text("ui.thida.events.use.4"));
      return InteractionResultHolder.fail(stack);
    }
    switch (item) {
      case "domain_talisman" -> {
        boolean wasActive = ThidaMayarom.active(p);
        ThidaMayarom.toggle(p);
        return wasActive != ThidaMayarom.active(p)
            ? InteractionResultHolder.success(stack)
            : InteractionResultHolder.fail(stack);
      }
      default -> {
        return InteractionResultHolder.pass(stack);
      }
    }
  }

  private static Vec3 domainCenter(ServerPlayer p) {
    CompoundTag n = state(p);
    return new Vec3(n.getDouble("domainX"), n.getDouble("domainY"), n.getDouble("domainZ"));
  }

  @SubscribeEvent
  public static void attack(LivingIncomingDamageEvent event) {
    if (event.getEntity().level().isClientSide) return;
    if (state(event.getEntity()).getLong("invulnerable") > now(event.getEntity())) {
      event.setCanceled(true);
      return;
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void hurt(LivingIncomingDamageEvent event) {
    if (state(event.getEntity()).getLong("vulnerableUntil") > now(event.getEntity()))
      event.setAmount(event.getAmount() * (1 + ThidaConfig.VULNERABILITY.get().floatValue()));
    if (event.getSource().getEntity() instanceof ServerPlayer attacker
        && state(attacker).getLong("weakUntil") > now(attacker))
      event.setAmount(event.getAmount() * 0.7F);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void damage(LivingDamageEvent.Pre event) {
    if (!(event.getEntity() instanceof ServerPlayer victim) || event.getNewDamage() <= 0) return;
    if (state(victim).getLong("invulnerable") > now(victim)) {
      event.setNewDamage(0);
      return;
    }
  }

  @SubscribeEvent
  public static void tick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer p)) return;
    if (isThida(p)) ThidaBonds.adoptPassives(p);
    CompoundTag n = state(p);
    long time = now(p);
    if (n.getLong("slow") <= time && p.getAttribute(Attributes.ATTACK_SPEED) != null)
      p.getAttribute(Attributes.ATTACK_SPEED).removeModifier(SLOW);
    if (n.getLong("bleed") > time && time % 20 == 0) p.hurt(p.damageSources().magic(), 1);
    if (!isThida(p)) {
      if (n.getBoolean("active") || n.hasUUID("narotra_doll") || n.hasUUID("nilakan_doll"))
        cleanup(p);
      syncHud(p);
      return;
    }
    n.putBoolean("active", true);
    if (!n.getBoolean("passiveKit")) {
      for (String item : new String[] {"nilakan_doll", "narotra_doll"}) {
        ItemStack doll = new ItemStack(ThidaMod.ABILITIES.get(item).get());
        if (!p.getInventory().contains(doll) && !p.addItem(doll)) p.drop(doll, false);
      }
      n.putBoolean("passiveKit", true);
    }
    for (String key : new String[] {"parasite", "heart"})
      if (n.hasUUID(key)) {
        release(p, key);
      }
    if (n.hasUUID("parasiteEntity")) {
      Entity doll = p.serverLevel().getEntity(n.getUUID("parasiteEntity"));
      if (doll instanceof net.minecraft.world.entity.decoration.ArmorStand stand)
        ThidaParasiteVisual.follow(stand, p);
    }
    if (n.getBoolean("parasiteReward")) {
      n.remove("parasiteReward");
      if (p.isAlive()) addonHeal(p, p, ThidaConfig.PARASITE_BREAK_HEAL.get().floatValue());
    }
    ThidaPassives.tick(p, n, time);
    syncHud(p);
  }

  private static void closeDomainFx(ServerPlayer p) {
    CompoundTag n = state(p);
    syncDomainVisual(p, false);
    if (!n.getBoolean("domainFx")) return;
    var dimension = ResourceLocation.tryParse(n.getString("domainDimension"));
    var level =
        dimension == null
            ? null
            : p.server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimension));
    if (level != null) ThidaDomainFx.close(level, domainCenter(p), ThidaConfig.DOMAIN_RADIUS.get());
    n.remove("domainFx");
  }

  private static void syncDomainVisual(ServerPlayer p, boolean active) {
    CompoundTag state = state(p), packet = new CompoundTag();
    packet.putUUID("owner", p.getUUID());
    packet.putBoolean("active", active);
    packet.putString("dimension", state.getString("domainDimension"));
    Vec3 c = domainCenter(p);
    double radius = ThidaConfig.DOMAIN_RADIUS.get();
    packet.putDouble("x", c.x);
    packet.putDouble("y", c.y);
    packet.putDouble("z", c.z);
    packet.putDouble("radius", radius);
    packet.putLong(
        "age", state.contains("domainStarted") ? now(p) - state.getLong("domainStarted") : 80);
    for (ServerPlayer observer : p.server.getPlayerList().getPlayers()) {
      if (observer instanceof net.neoforged.neoforge.common.util.FakePlayer) continue;
      if (!active
          || observer
                  .level()
                  .dimension()
                  .location()
                  .toString()
                  .equals(state.getString("domainDimension"))
              && observer.position().distanceToSqr(c) <= Math.pow(radius + 96, 2)) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            observer, new ThidaNetwork.DomainSnapshot(packet));
      }
    }
  }

  private static void cleanup(ServerPlayer p) {
    ThidaPassives.onOwnerUnavailable(p);
    closeDomainFx(p);
    for (ServerPlayer other : p.server.getPlayerList().getPlayers()) {
      CompoundTag n = state(other);
      for (String key : new String[] {"heart", "parasite"})
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
      if (!key.startsWith("cd_")
          && !(isThida(p)
              && (key.startsWith("hud_")
                  || passive(key)
                  || key.startsWith("passive_")
                  || key.equals("nilakan_dollName")
                  || key.equals("narotra_dollName")
                  || key.equals("passiveKit")))) n.remove(key);
    if (p.getAttribute(Attributes.ATTACK_SPEED) != null)
      p.getAttribute(Attributes.ATTACK_SPEED).removeModifier(SLOW);
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
    for (String key : old.getAllKeys())
      if (key.startsWith("cd_")
          || key.startsWith("hud_")
          || key.startsWith("passive_")
          || passive(key)
          || key.equals("nilakan_dollName")
          || key.equals("narotra_dollName")) copy.put(key, old.get(key).copy());
    event.getEntity().getPersistentData().put(ROOT, copy);
  }

  @SubscribeEvent
  public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer p
        && (state(p).hasUUID("parasite") || state(p).getBoolean("parasiteArmed")))
      release(p, "parasite");
  }

  @SubscribeEvent
  public static void tossed(ItemTossEvent event) {
    if (event.getPlayer() instanceof ServerPlayer p)
      ThidaPassives.onToss(p, event.getEntity().getItem());
  }
}
