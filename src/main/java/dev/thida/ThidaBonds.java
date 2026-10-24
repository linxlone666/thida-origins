package dev.thida;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** v7 rules. Persistent records are authoritative; HUD and item text never grant a bond. */
@EventBusSubscriber(modid = "thida")
public final class ThidaBonds {
  public static final String PARASITE = "parasite", HEART = "heart";
  private static final String TOKEN = "thida_parasite_instance";
  private static boolean sharing;
  private static final ThreadLocal<java.util.Map<UUID, java.util.ArrayDeque<Float>>> HEALTH_BEFORE =
      ThreadLocal.withInitial(java.util.HashMap::new);
  private static final ResourceLocation HEART_DAMAGE =
      ResourceLocation.fromNamespaceAndPath("thida", "heart_melee");

  private ThidaBonds() {}

  public static long now(MinecraftServer server) {
    return server.overworld().getGameTime();
  }

  public static CompoundTag data(ServerPlayer p) {
    return ThidaBondData.get(p.server).player(p);
  }

  public static CompoundTag link(ServerPlayer owner, String kind) {
    return ThidaBondData.get(owner.server).link(owner.getUUID(), kind);
  }

  public static ServerPlayer target(ServerPlayer owner, String kind) {
    var n = link(owner, kind);
    return n.hasUUID("target") ? owner.server.getPlayerList().getPlayer(n.getUUID("target")) : null;
  }

  public static boolean heartActive(ServerPlayer p) {
    return link(p, HEART).hasUUID("target");
  }

  public static boolean dollsAllowed(ServerPlayer p) {
    return ThidaEvents.isThida(p)
        && !heartActive(p)
        && data(p).getLong("passiveBlockedUntil") <= now(p.server);
  }

  public static boolean conflictingPassives(ServerPlayer p) {
    var root = ThidaEvents.state(p);
    return root.hasUUID("narotra_doll")
        && root.hasUUID("nilakan_doll")
        && root.getUUID("narotra_doll").equals(root.getUUID("nilakan_doll"));
  }

  public static boolean canLink(ServerPlayer owner, ServerPlayer target, String kind) {
    return dollsAllowed(owner)
        && owner != target
        && target.isAlive()
        && !target.isSpectator()
        && !ThidaBondData.get(owner.server).occupied(target.getUUID(), owner.getUUID(), kind);
  }

  public static void recordPassive(ServerPlayer owner, ServerPlayer target, String kind) {
    record(owner, target, kind);
  }

  public static void adoptPassives(ServerPlayer owner) {
    var root = ThidaEvents.state(owner);
    var store = ThidaBondData.get(owner.server);
    for (String kind : new String[] {"narotra_doll", "nilakan_doll"}) {
      if (!root.hasUUID(kind) || store.link(owner.getUUID(), kind).hasUUID("target")) continue;
      var target = passiveTarget(owner, kind);
      if (target != null) recordPassive(owner, target, kind);
      else {
        var n = new CompoundTag();
        n.putUUID("target", root.getUUID(kind));
        n.putString("name", root.getString(kind + "Name"));
        store.links(owner.getUUID()).put(kind, n);
        store.history(root.getUUID(kind));
        store.setDirty();
      }
    }
  }

  private static CompoundTag record(ServerPlayer owner, ServerPlayer target, String kind) {
    var store = ThidaBondData.get(owner.server);
    var link = new CompoundTag();
    link.putUUID("target", target.getUUID());
    link.putString("name", target.getScoreboardName());
    link.putString("dimension", owner.level().dimension().location().toString());
    link.putLong("started", now(owner.server));
    store.links(owner.getUUID()).put(kind, link);
    store.remember(owner);
    store.remember(target);
    store.history(target.getUUID());
    return link;
  }

  public static void passiveEnded(ServerPlayer owner, String kind) {
    passiveEnded(owner, kind, "UNLINKED");
  }

  public static void passiveEnded(ServerPlayer owner, String kind, String reason) {
    var store = ThidaBondData.get(owner.server);
    store.links(owner.getUUID()).remove(kind);
    store.setDirty();
    ThidaSpells.stop(owner.server, owner.getUUID(), kind, reason);
  }

  public static void request(ServerPlayer p, CompoundTag request) {
    if (!ThidaEvents.isThida(p) || !p.isAlive() || p.isSpectator()) return;
    switch (request.getString("action")) {
      case "summon" -> summon(p);
      case "heart" -> requestHeart(p);
      case "confirm" -> confirm(p, request);
      case "cast" -> ThidaSpells.cast(p, request.getString("doll"), request.getInt("spell"));
      case "select" -> {
        String kind = request.getString("doll");
        if (ThidaTooltips.passive(kind) && ThidaEvents.holds(p, kind))
          ThidaSkillEffects.emit("DOLL_SELECTED", kind, p, passiveTarget(p, kind), "");
      }
      case "unlink" -> {
        String kind = request.getString("doll");
        if (ThidaTooltips.passive(kind) && ThidaPassives.holdsLinkedDoll(p, kind))
          ThidaPassives.unlink(p, kind, "PLAYER_UNLINKED");
      }
      default -> {}
    }
  }

  public static ServerPlayer passiveTarget(ServerPlayer owner, String kind) {
    var n = ThidaEvents.state(owner);
    return n.hasUUID(kind) ? owner.server.getPlayerList().getPlayer(n.getUUID(kind)) : null;
  }

  private static ItemStack parasiteItem(ServerPlayer p) {
    var n = data(p);
    if (!n.hasUUID("parasiteItem")) return ItemStack.EMPTY;
    var carried = p.containerMenu.getCarried();
    var carriedTag = ThidaItemData.read(carried);
    if (carried.is(ThidaMod.ABILITIES.get("parasite_doll").get())
        && carriedTag.hasUUID(TOKEN)
        && carriedTag.getUUID(TOKEN).equals(n.getUUID("parasiteItem"))) return carried;
    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
      var stack = p.getInventory().getItem(i);
      var tag = ThidaItemData.read(stack);
      if (stack.is(ThidaMod.ABILITIES.get("parasite_doll").get())
          && tag.hasUUID(TOKEN)
          && tag.getUUID(TOKEN).equals(n.getUUID("parasiteItem"))) return stack;
    }
    return ThidaDollInventory.retainedItem(p, TOKEN, n.getUUID("parasiteItem"));
  }

  public static void summon(ServerPlayer p) {
    var n = data(p);
    if (!ThidaPermissions.unlocked(p, 2)) {
      ThidaEvents.tell(p, ThidaMessages.text("ui.thida.bonds.summon.1"));
      return;
    }
    if (!dollsAllowed(p) || n.getLong("parasiteCooldown") > now(p.server)) {
      ThidaEvents.tell(p, ThidaMessages.text("ui.thida.bonds.summon.2"));
      return;
    }
    if (!parasiteItem(p).isEmpty() || link(p, PARASITE).hasUUID("target")) {
      ThidaEvents.tell(p, ThidaMessages.text("ui.thida.bonds.summon.3"));
      return;
    }
    int selected = p.getInventory().selected, slot = -1;
    if (p.getInventory().getItem(selected).isEmpty()) slot = selected;
    for (int distance = 1; slot < 0 && distance < 9; distance++) {
      for (int candidate : new int[] {selected + distance, selected - distance})
        if (candidate >= 0 && candidate < 9 && p.getInventory().getItem(candidate).isEmpty()) {
          slot = candidate;
          break;
        }
    }
    for (int i = 9; slot < 0 && i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) slot = i;
    if (slot < 0) {
      ThidaEvents.tell(p, ThidaMessages.text("ui.thida.bonds.summon.4"));
      return;
    }
    var stack = new ItemStack(ThidaMod.ABILITIES.get("parasite_doll").get());
    UUID token = UUID.randomUUID();
    ThidaItemData.putUUID(stack, TOKEN, token);
    ThidaItemData.putUUID(stack, "thida_parasite_owner", p.getUUID());
    p.getInventory().setItem(slot, stack);
    n.putUUID("parasiteItem", token);
    ThidaBondData.get(p.server).setDirty();
    ThidaEvents.tell(p, ThidaMessages.text("ui.thida.bonds.summon.5"));
    ThidaSkillEffects.emit("PARASITE_SUMMONED", PARASITE, p, null, "");
  }

  private static void requestHeart(ServerPlayer p) {
    if (!ThidaPermissions.unlocked(p, 3)) {
      ThidaEvents.tell(p, ThidaMessages.text("ui.thida.bonds.requestheart.1"));
      return;
    }
    if (heartActive(p)) {
      var target = target(p, HEART);
      if (target != null) ThidaSkillEffects.confirm(p, "heart_end", target);
      else {
        var packet = new CompoundTag();
        packet.putString("kind", "heart_end");
        packet.putUUID("target", link(p, HEART).getUUID("target"));
        packet.putString("name", link(p, HEART).getString("name"));
        if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer))
          net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
              p, new ThidaNetwork.Confirmation(packet));
      }
      return;
    }
    if (p.getHealth() <= 50 || data(p).getLong("heartCooldown") > now(p.server)) {
      ThidaEvents.tell(p, ThidaMessages.text("ui.thida.bonds.requestheart.2"));
      return;
    }
    ServerPlayer target = aimHeart(p);
    ThidaSkillEffects.emit("HEART_ATTEMPT_STARTED", HEART, p, target, "");
    if (target == null
        || !ThidaBondData.get(p.server).player(target).getBoolean("everLinked")
        || ThidaBondData.get(p.server).occupied(target.getUUID(), p.getUUID(), HEART)) {
      ThidaEvents.tell(p, ThidaMessages.text("ui.thida.bonds.requestheart.3"));
      return;
    }
    startHeart(p, target);
  }

  static ServerPlayer aimHeart(ServerPlayer p) {
    Vec3 from = p.getEyePosition(), end = from.add(p.getLookAngle().scale(16));
    ServerPlayer result = null;
    double nearest = Double.MAX_VALUE;
    for (var target : p.server.getPlayerList().getPlayers()) {
      if (target == p
          || !target.isAlive()
          || target.isSpectator()
          || target.isInvisible()
          || target.level() != p.level()
          || p.distanceToSqr(target) > 256
          || !p.hasLineOfSight(target)) continue;
      var hit = target.getBoundingBox().inflate(.2).clip(from, end);
      if (hit.isPresent() && from.distanceToSqr(hit.get()) < nearest) {
        result = target;
        nearest = from.distanceToSqr(hit.get());
      }
    }
    return result;
  }

  private static void startHeart(ServerPlayer p, ServerPlayer target) {
    ThidaPassives.unlink(p, "narotra_doll", "HEART_STARTED");
    ThidaPassives.unlink(p, "nilakan_doll", "HEART_STARTED");
    if (data(p).hasUUID("parasiteItem") || link(p, PARASITE).hasUUID("target"))
      breakParasite(p.server, p.getUUID(), "HEART_STARTED", false);
    var link = record(p, target, HEART);
    link.putBoolean("used", false);
    var n = ThidaBondData.get(p.server).player(target);
    long time = now(p.server);
    n.putLong("heartWard", time + 3600);
    n.putLong("heartSpeed", time + 1000);
    link.putLong("wardUntil", time + 3600);
    ThidaEvents.cleanse(target);
    ThidaEvents.tell(
        p, ThidaMessages.text("ui.thida.bonds.startheart.1") + target.getScoreboardName());
    ThidaSkillEffects.emit("HEART_LINKED", HEART, p, target, "");
    ThidaBondData.get(p.server).setDirty();
  }

  private static void confirm(ServerPlayer p, CompoundTag request) {
    if (!request.hasUUID("target")) return;
    var store = ThidaBondData.get(p.server);
    var n = data(p);
    if (request.getString("kind").equals("heart_end")) {
      if (heartActive(p)
          && link(p, HEART).getUUID("target").equals(request.getUUID("target"))
          && request.getBoolean("accept")) endHeart(p.server, p.getUUID(), "MANUAL");
      return;
    }
    var pending = n.getCompound("pendingParasite");
    n.remove("pendingParasite");
    store.setDirty();
    if (!request.getBoolean("accept")
        || !pending.hasUUID("target")
        || !pending.getUUID("target").equals(request.getUUID("target"))) return;
    var target = p.server.getPlayerList().getPlayer(request.getUUID("target"));
    if (!ThidaPermissions.unlocked(p, 2)
        || target == null
        || target.level() != p.level()
        || parasiteItem(p).isEmpty()
        || !canLink(p, target, PARASITE)
        || link(p, PARASITE).hasUUID("target")) return;
    record(p, target, PARASITE);
    long time = now(p.server);
    n.putLong("parasiteWard", time + 2400);
    store.player(target).putLong("parasiteWard", time + 2400);
    if (p.getHealth() < 25 || target.getHealth() < 25) {
      var health =
          ThidaDamageMath.initialHealth(
              p.getHealth(), p.getMaxHealth(), target.getHealth(), target.getMaxHealth());
      p.setHealth(health.owner());
      target.setHealth(health.target());
    }
    ThidaSkillEffects.emit("PARASITE_LINKED", PARASITE, p, target, "");
    ThidaEvents.tell(
        p, ThidaMessages.text("ui.thida.bonds.confirm.1") + target.getScoreboardName());
    if (p.getHealth() + target.getHealth() <= 50)
      breakParasite(p.server, p.getUUID(), "HEALTH_THRESHOLD", true);
  }

  private static UUID incoming(MinecraftServer server, UUID target, String kind) {
    var store = ThidaBondData.get(server);
    for (String id : store.ids()) {
      var link = store.link(UUID.fromString(id), kind);
      if (link.hasUUID("target") && link.getUUID("target").equals(target))
        return UUID.fromString(id);
    }
    return null;
  }

  public static UUID heartOwner(ServerPlayer p) {
    return incoming(p.server, p.getUUID(), HEART);
  }

  private static UUID parasiteOwner(ServerPlayer p) {
    return link(p, PARASITE).hasUUID("target")
        ? p.getUUID()
        : incoming(p.server, p.getUUID(), PARASITE);
  }

  public static boolean invulnerable(ServerPlayer p) {
    return data(p).getLong("heartInvulnerable") > now(p.server);
  }

  public static boolean warded(ServerPlayer p) {
    long time = now(p.server);
    var n = data(p);
    return n.getLong("heartWard") > time
        || n.getLong("parasiteWard") > time
        || ThidaSpells.warded(p);
  }

  public static void endHeart(MinecraftServer server, UUID ownerId, String reason) {
    var store = ThidaBondData.get(server);
    var link = store.link(ownerId, HEART);
    if (!link.hasUUID("target")) return;
    var owner = server.getPlayerList().getPlayer(ownerId);
    var target = server.getPlayerList().getPlayer(link.getUUID("target"));
    long start =
        reason.equals("MANUAL") ? Math.max(now(server), link.getLong("wardUntil")) : now(server);
    store.player(ownerId).putLong("heartCooldown", start + 3600);
    store.links(ownerId).remove(HEART);
    store.setDirty();
    if (owner != null) {
      removeModifier(owner, Attributes.ATTACK_DAMAGE, HEART_DAMAGE);
      ThidaEvents.tell(owner, ThidaMessages.text("ui.thida.bonds.endheart.1"));
      ThidaSkillEffects.emit("HEART_ENDED", HEART, owner, target, reason);
    } else if (target != null)
      ThidaSkillEffects.emitForRecipient("HEART_ENDED", HEART, ownerId, target, reason);
  }

  public static void breakParasite(
      MinecraftServer server, UUID ownerId, String reason, boolean reward) {
    var store = ThidaBondData.get(server);
    var n = store.player(ownerId);
    var link = store.link(ownerId, PARASITE);
    if (!n.hasUUID("parasiteItem") && !link.hasUUID("target")) return;
    var owner = server.getPlayerList().getPlayer(ownerId);
    var target =
        link.hasUUID("target") ? server.getPlayerList().getPlayer(link.getUUID("target")) : null;
    UUID targetId = link.hasUUID("target") ? link.getUUID("target") : null;
    store.links(ownerId).remove(PARASITE);
    n.putLong("parasiteCooldown", now(server) + 6000);
    n.putLong("passiveBlockedUntil", now(server) + 6000);
    n.remove("pendingParasite");
    if (owner != null) {
      var stack = parasiteItem(owner);
      if (!stack.isEmpty()) stack.setCount(0);
      if (n.hasUUID("parasiteItem"))
        for (var slot : owner.containerMenu.slots) {
          var tag = ThidaItemData.read(slot.getItem());
          if (tag.hasUUID(TOKEN) && tag.getUUID(TOKEN).equals(n.getUUID("parasiteItem")))
            slot.set(ItemStack.EMPTY);
        }
      if (reward && owner.isAlive()) ThidaHealing.apply(owner, owner, 50, ThidaHealing.Kind.OTHER);
      if (reward) n.putLong("parasiteSpeed", now(server) + 400);
      ThidaSkillEffects.emit("PARASITE_BROKEN", PARASITE, owner, target, reason);
      ThidaEvents.tell(owner, ThidaMessages.text("ui.thida.bonds.breakparasite.1"));
    }
    n.remove("parasiteItem");
    if (reward && targetId != null) {
      var recipient = store.player(targetId);
      recipient.putLong("parasiteSpeed", now(server) + 400);
      recipient.putLong("parasiteAfterDefenseUntil", now(server) + 200);
      if (owner != null && target != null)
        ThidaSkillIntegration.breakReduction(owner, target)
            .ifPresent(value -> recipient.putDouble("parasiteAfterDefense", value));
    }
    store.setDirty();
  }

  /** Spent dolls returning from offline inventory or a closed container are never reusable. */
  public static void discardExpiredDoll(ItemStack stack, net.minecraft.world.level.Level level) {
    if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
    var tag = ThidaItemData.read(stack);
    if (!tag.hasUUID(TOKEN) || !tag.hasUUID("thida_parasite_owner")) return;
    var owner =
        ThidaBondData.get(serverLevel.getServer()).player(tag.getUUID("thida_parasite_owner"));
    if (!owner.hasUUID("parasiteItem") || !owner.getUUID("parasiteItem").equals(tag.getUUID(TOKEN)))
      stack.setCount(0);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void incomingDamage(LivingIncomingDamageEvent event) {
    if (event.getEntity() instanceof ServerPlayer victim && invulnerable(victim)
        || event.getSource().getEntity() instanceof ServerPlayer attacker
            && invulnerable(attacker)) {
      event.setCanceled(true);
      return;
    }
    if (sharing || !(event.getEntity() instanceof ServerPlayer victim)) return;
    UUID owner = parasiteOwner(victim);
    if (owner != null) {
      var link = ThidaBondData.get(victim.server).link(owner, PARASITE);
      var a = victim.server.getPlayerList().getPlayer(owner);
      var b = victim.server.getPlayerList().getPlayer(link.getUUID("target"));
      if (a != null && b != null && a.isAlive() && b.isAlive() && a.level() == b.level())
        event.setAmount(event.getAmount() * .8f);
    }
    var n = data(victim);
    if (n.getLong("parasiteAfterDefenseUntil") > now(victim.server)
        && n.contains("parasiteAfterDefense"))
      event.setAmount(
          (float) (event.getAmount() * Math.max(0, 1 - n.getDouble("parasiteAfterDefense"))));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void beforeDamage(LivingDamageEvent.Pre event) {
    if (!(event.getEntity() instanceof ServerPlayer victim)) return;
    if (invulnerable(victim)) {
      event.setNewDamage(0);
      return;
    }
    float amount = event.getNewDamage();
    if (!sharing) {
      UUID ownerId = parasiteOwner(victim);
      if (ownerId != null) {
        var store = ThidaBondData.get(victim.server);
        var link = store.link(ownerId, PARASITE);
        var owner = victim.server.getPlayerList().getPlayer(ownerId);
        var target = victim.server.getPlayerList().getPlayer(link.getUUID("target"));
        if (owner != null
            && target != null
            && owner.isAlive()
            && target.isAlive()
            && owner.level() == target.level()) {
          float absorbed = Math.min(amount, victim.getAbsorptionAmount());
          var split =
              ThidaDamageMath.equalize(
                  owner.getHealth(), target.getHealth(), Math.max(0, amount - absorbed));
          ServerPlayer other = victim == owner ? target : owner;
          float otherDamage = victim == owner ? split.target() : split.owner();
          amount = absorbed + (victim == owner ? split.owner() : split.target());
          sharing = true;
          try {
            var key =
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("thida", "shared"));
            var type =
                victim
                    .registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(key);
            if (otherDamage > 0)
              other.hurt(
                  new net.minecraft.world.damagesource.DamageSource(
                      type, event.getSource().getDirectEntity(), event.getSource().getEntity()),
                  otherDamage);
          } finally {
            sharing = false;
          }
        }
      }
    }
    float absorbed = Math.min(amount, victim.getAbsorptionAmount());
    if (amount - absorbed >= victim.getHealth() && rescue(victim))
      amount = absorbed + Math.max(0, victim.getHealth() - 1);
    event.setNewDamage(amount);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void captureHp(LivingDamageEvent.Pre event) {
    if (event.getEntity() instanceof ServerPlayer p)
      HEALTH_BEFORE
          .get()
          .computeIfAbsent(p.getUUID(), id -> new java.util.ArrayDeque<>())
          .push(p.getHealth());
  }

  public static boolean rescue(ServerPlayer victim) {
    UUID id = heartOwner(victim);
    if (id == null) return false;
    var store = ThidaBondData.get(victim.server);
    var link = store.link(id, HEART);
    if (link.getBoolean("used")) return false;
    link.putBoolean("used", true);
    data(victim).putLong("heartInvulnerable", now(victim.server) + 100);
    store.setDirty();
    data(victim).putUUID("heartInvulnerabilityOwner", id);
    ThidaSkillEffects.emitForRecipient("HEART_DEATH_PREVENTED", HEART, id, victim, "LETHAL_DAMAGE");
    ThidaSkillEffects.emitForRecipient(
        "HEART_INVULNERABILITY_STARTED", HEART, id, victim, "LETHAL_DAMAGE");
    return true;
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void afterDamage(LivingDamageEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer victim)) return;
    var captures = HEALTH_BEFORE.get().get(victim.getUUID());
    float before = captures == null || captures.isEmpty() ? victim.getHealth() : captures.pop();
    if (captures != null && captures.isEmpty()) HEALTH_BEFORE.get().remove(victim.getUUID());
    if (sharing) return;
    if (heartActive(victim) && victim.getHealth() <= 50) {
      endHeart(victim.server, victim.getUUID(), "OWNER_HEALTH");
      if (event.getNewDamage() > 0) {
        data(victim).putLong("heartBleed", now(victim.server) + 200);
        data(victim).putLong("heartNextBleed", now(victim.server) + 20);
        victim.addEffect(new MobEffectInstance(ThidaMod.HEART_BLEED, 200));
        if (!victim.hasEffect(ThidaMod.HEART_BLEED)) {
          data(victim).remove("heartBleed");
          data(victim).remove("heartNextBleed");
        }
      }
    }
    UUID ownerId = parasiteOwner(victim);
    if (ownerId != null) {
      var store = ThidaBondData.get(victim.server);
      var link = store.link(ownerId, PARASITE);
      var owner = victim.server.getPlayerList().getPlayer(ownerId);
      var target = victim.server.getPlayerList().getPlayer(link.getUUID("target"));
      if (owner != null
          && target != null
          && (owner.getHealth() + target.getHealth() <= 50
              || !owner.isAlive()
              || !target.isAlive()))
        breakParasite(victim.server, ownerId, "HEALTH_THRESHOLD", true);
    }
    if (event.getNewDamage() <= 0
        || victim.getHealth() >= before
        || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
        || event.getSource().getDirectEntity() != attacker
        || !ThidaPermissions.unlocked(attacker, 2)
        || !attacker.getMainHandItem().is(ThidaMod.ABILITIES.get("parasite_doll").get())
        || parasiteItem(attacker) != attacker.getMainHandItem()
        || link(attacker, PARASITE).hasUUID("target")
        || !canLink(attacker, victim, PARASITE)) return;
    var pending = new CompoundTag();
    pending.putUUID("target", victim.getUUID());
    data(attacker).put("pendingParasite", pending);
    ThidaBondData.get(attacker.server).setDirty();
    ThidaSkillEffects.confirm(attacker, "parasite_link", victim);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void death(LivingDeathEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer p)) return;
    if (invulnerable(p) || rescue(p)) {
      p.setHealth(1);
      event.setCanceled(true);
      return;
    }
    if (!sharing) unavailable(p, "DEATH");
  }

  @SubscribeEvent
  public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer p) unavailable(p, "DIMENSION_CHANGED");
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer p) {
      var n = data(p);
      n.remove("pendingParasite");
      n.remove("offlineSpeed");
      var speed = p.getEffect(MobEffects.MOVEMENT_SPEED);
      long time = now(p.server);
      int amplifier =
          n.getLong("heartSpeed") > time ? 2 : n.getLong("parasiteSpeed") > time ? 1 : -1;
      long deadline = amplifier == 2 ? n.getLong("heartSpeed") : n.getLong("parasiteSpeed");
      if (speed != null
          && speed.getAmplifier() == amplifier
          && speed.getDuration() <= deadline - time + 2) {
        var saved = new CompoundTag();
        saved.putInt("amplifier", amplifier);
        saved.putInt("duration", speed.getDuration());
        saved.putLong("deadline", deadline);
        n.put("offlineSpeed", saved);
      }
      ThidaBondData.get(p.server).setDirty();
    }
  }

  @SubscribeEvent
  public static void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer p)) return;
    var saved = data(p).getCompound("offlineSpeed");
    var speed = p.getEffect(MobEffects.MOVEMENT_SPEED);
    if (saved.contains("deadline")
        && speed != null
        && speed.getAmplifier() == saved.getInt("amplifier")
        && speed.getDuration() <= saved.getInt("duration")) {
      p.removeEffect(MobEffects.MOVEMENT_SPEED);
      long left = saved.getLong("deadline") - now(p.server);
      if (left > 0)
        p.addEffect(
            new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, (int) left, saved.getInt("amplifier"), true, true));
    }
    data(p).remove("offlineSpeed");
    ThidaBondData.get(p.server).setDirty();
  }

  public static void unavailable(ServerPlayer p, String reason) {
    var store = ThidaBondData.get(p.server);
    for (String id : store.ids()) {
      UUID owner = UUID.fromString(id);
      for (String kind : new String[] {PARASITE, HEART}) {
        var link = store.link(owner, kind);
        if (owner.equals(p.getUUID())
            || link.hasUUID("target") && link.getUUID("target").equals(p.getUUID())) {
          if (kind.equals(HEART)) endHeart(p.server, owner, reason);
          else breakParasite(p.server, owner, reason, true);
        }
      }
    }
  }

  @SubscribeEvent
  public static void tossed(ItemTossEvent event) {
    if (event.getPlayer() instanceof ServerPlayer p
        && data(p).hasUUID("parasiteItem")
        && ThidaItemData.read(event.getEntity().getItem()).hasUUID(TOKEN)) {
      var tag = ThidaItemData.read(event.getEntity().getItem());
      if (data(p).hasUUID("parasiteItem")
          && tag.getUUID(TOKEN).equals(data(p).getUUID("parasiteItem"))) {
        breakParasite(p.server, p.getUUID(), "ITEM_REMOVED", true);
        event.getEntity().discard();
      }
    }
  }

  @SubscribeEvent
  public static void containerClosed(
      net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close event) {
    if (!(event.getEntity() instanceof ServerPlayer p)
        || !data(p).hasUUID("parasiteItem")
        || !parasiteItem(p).isEmpty()) return;
    UUID token = data(p).getUUID("parasiteItem");
    breakParasite(p.server, p.getUUID(), "ITEM_REMOVED", true);
    for (var slot : event.getContainer().slots) {
      var tag = ThidaItemData.read(slot.getItem());
      if (tag.hasUUID(TOKEN) && tag.getUUID(TOKEN).equals(token)) slot.set(ItemStack.EMPTY);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void tick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer p)) return;
    var store = ThidaBondData.get(p.server);
    store.remember(p);
    long time = now(p.server);
    var n = data(p);
    if (n.hasUUID("heartInvulnerabilityOwner") && n.getLong("heartInvulnerable") <= time) {
      ThidaSkillEffects.emitForRecipient(
          "HEART_INVULNERABILITY_ENDED",
          HEART,
          n.getUUID("heartInvulnerabilityOwner"),
          p,
          "EXPIRED");
      n.remove("heartInvulnerabilityOwner");
      store.setDirty();
    }
    if (!ThidaEvents.isThida(p)) {
      if (heartActive(p)) endHeart(p.server, p.getUUID(), "ORIGIN_LOST");
      if (n.hasUUID("parasiteItem")) breakParasite(p.server, p.getUUID(), "ORIGIN_LOST", false);
    }
    for (String kind : new String[] {PARASITE, HEART}) {
      UUID incomingOwner = incoming(p.server, p.getUUID(), kind);
      if (incomingOwner == null) continue;
      var incomingLink = store.link(incomingOwner, kind);
      if (!p.level()
          .dimension()
          .location()
          .toString()
          .equals(incomingLink.getString("dimension"))) {
        if (kind.equals(HEART)) endHeart(p.server, incomingOwner, "DIMENSION_CHANGED");
        else breakParasite(p.server, incomingOwner, "DIMENSION_CHANGED", true);
      }
    }
    if (n.hasUUID("parasiteItem") && parasiteItem(p).isEmpty())
      breakParasite(p.server, p.getUUID(), "ITEM_REMOVED", true);
    for (String kind : new String[] {PARASITE, HEART}) {
      var link = link(p, kind);
      var target = target(p, kind);
      if (target != null
          && (!target.isAlive()
              || !p.level().dimension().location().toString().equals(link.getString("dimension"))
              || target.level() != p.level())) {
        if (kind.equals(HEART)) endHeart(p.server, p.getUUID(), "TARGET_UNAVAILABLE");
        else breakParasite(p.server, p.getUUID(), "TARGET_UNAVAILABLE", true);
      }
    }
    if (heartActive(p) && p.getHealth() <= 50) endHeart(p.server, p.getUUID(), "OWNER_HEALTH");
    if (heartActive(p) && ThidaEvents.isThida(p))
      addModifier(p, Attributes.ATTACK_DAMAGE, HEART_DAMAGE, .20);
    else removeModifier(p, Attributes.ATTACK_DAMAGE, HEART_DAMAGE);
    if (p.isAlive()) {
      if (n.getLong("heartSpeed") > time)
        p.addEffect(
            new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, (int) (n.getLong("heartSpeed") - time), 2, true, true));
      if (n.getLong("parasiteSpeed") > time)
        p.addEffect(
            new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                (int) (n.getLong("parasiteSpeed") - time),
                1,
                true,
                true));
      if (n.contains("heartBleed")) {
        long deadline = n.getLong("heartBleed");
        if (time <= deadline
            && n.getLong("heartNextBleed") <= time
            && (p.hasEffect(ThidaMod.HEART_BLEED) || time == deadline)) {
          p.setHealth(p.getHealth() - CombatRules.nonlethalDamage(p.getHealth(), 2));
          n.putLong("heartNextBleed", time + 20);
          store.setDirty();
        }
        if (time >= deadline) {
          n.remove("heartBleed");
          n.remove("heartNextBleed");
          store.setDirty();
        }
      }
      if (warded(p)) ThidaEvents.cleanse(p);
    }
    ThidaSpells.tick(p.server);
  }

  public static void hud(ServerPlayer owner, CompoundTag packet) {
    var store = ThidaBondData.get(owner.server);
    long time = now(owner.server);
    var self = data(owner);
    if (ThidaEvents.isThida(owner)) adoptPassives(owner);
    for (String kind : new String[] {"narotra_doll", "nilakan_doll", PARASITE, HEART}) {
      var record = store.link(owner.getUUID(), kind);
      var row = new CompoundTag();
      if (ThidaEvents.isThida(owner) && record.hasUUID("target")) {
        UUID id = record.getUUID("target");
        var target = owner.server.getPlayerList().getPlayer(id);
        var cached = store.player(id);
        row.putString("id", id.toString());
        row.putString(
            "name", target == null ? cached.getString("name") : target.getScoreboardName());
        if (row.getString("name").isEmpty()) row.putString("name", record.getString("name"));
        row.putFloat("hp", target == null ? cached.getFloat("hp") : target.getHealth());
        row.putFloat("maxHp", target == null ? cached.getFloat("maxHp") : target.getMaxHealth());
        boolean active = target != null && target.isAlive() && target.level() == owner.level();
        if (ThidaTooltips.passive(kind)) active = ThidaPassives.isBondAvailable(owner, kind);
        row.putBoolean("available", active);
        row.putString(
            "status",
            target == null
                ? ThidaMessages.text("ui.thida.bonds.hud.1")
                : !target.isAlive()
                    ? ThidaMessages.text("ui.thida.bonds.hud.2")
                    : active
                        ? ThidaMessages.text("ui.thida.bonds.hud.3")
                        : ThidaMessages.text("ui.thida.bonds.hud.4"));
        if (ThidaTooltips.passive(kind)) {
          String tokenKey =
              "passive_" + (kind.equals("narotra_doll") ? "narotra" : "nilakan") + "_item";
          if (ThidaEvents.state(owner).hasUUID(tokenKey))
            row.putUUID("itemInstance", ThidaEvents.state(owner).getUUID(tokenKey));
        } else if (kind.equals(PARASITE) && self.hasUUID("parasiteItem"))
          row.putUUID("itemInstance", self.getUUID("parasiteItem"));
      }
      packet.put(kind, row);
    }
    packet.putBoolean("unlock2", ThidaPermissions.unlocked(owner, 2));
    packet.putBoolean("unlock3", ThidaPermissions.unlocked(owner, 3));
    packet.putBoolean("dollsLocked", !dollsAllowed(owner));
    packet.putBoolean(
        "armed", self.hasUUID("parasiteItem") && !link(owner, PARASITE).hasUUID("target"));
    packet
        .getCompound("cooldowns")
        .putLong("cd_heart_doll", Math.max(0, self.getLong("heartCooldown") - time));
    packet
        .getCompound("cooldowns")
        .putLong("cd_parasite_doll", Math.max(0, self.getLong("parasiteCooldown") - time));
    for (String kind : new String[] {"narotra_doll", "nilakan_doll"})
      packet
          .getCompound("cooldowns")
          .putLong("cd_" + kind, Math.max(0, self.getLong("passiveBlockedUntil") - time));
    for (String kind : new String[] {"narotra_doll", "nilakan_doll"})
      for (int spell = 0; spell < 3; spell++) {
        String key = kind + "_" + spell;
        packet.putString("spellReason_" + key, ThidaSpells.failure(owner, kind, spell));
        packet
            .getCompound("cooldowns")
            .putLong("cd_effect_" + key, Math.max(0, self.getLong("spellCd_" + key) - time));
        var active = ThidaBondData.child(self, "spells").getCompound(kind);
        packet
            .getCompound("activeEffects")
            .putLong(
                key,
                active.getInt("spell") == spell
                    ? Math.max(0, active.getLong("expires") - time)
                    : 0);
      }
    var recipient = new CompoundTag();
    UUID guardian = heartOwner(owner);
    recipient.putBoolean("active", guardian != null);
    if (guardian != null) {
      recipient.putBoolean("used", store.link(guardian, HEART).getBoolean("used"));
      recipient.putString("ownerName", store.player(guardian).getString("name"));
    }
    recipient.putLong("ward", Math.max(0, self.getLong("heartWard") - time));
    recipient.putLong("speed", Math.max(0, self.getLong("heartSpeed") - time));
    recipient.putLong("invulnerable", Math.max(0, self.getLong("heartInvulnerable") - time));
    packet.put("recipient", recipient);
    packet.putLong("invulnerable", Math.max(0, self.getLong("heartInvulnerable") - time));
    packet.putInt("effectTotal", 200);
    packet.putInt("effectDuration", 200);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void bleedRemoved(MobEffectEvent.Remove event) {
    if (!(event.getEntity() instanceof ServerPlayer p)) return;
    if (event.getEffect().equals(ThidaMod.HEART_BLEED)) {
      data(p).remove("heartBleed");
      data(p).remove("heartNextBleed");
      ThidaBondData.get(p.server).setDirty();
    } else if (event.getEffect().equals(ThidaMod.SPELL_BLEED)) ThidaSpells.cleanseBleed(p);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void effectApplicable(MobEffectEvent.Applicable event) {
    if (!(event.getEntity() instanceof ServerPlayer p)
        || event.getEffectInstance().getEffect().value().getCategory()
            != net.minecraft.world.effect.MobEffectCategory.HARMFUL) return;
    long time = now(p.server);
    var n = data(p);
    if (n.getLong("heartWard") > time || n.getLong("parasiteWard") > time || ThidaSpells.warded(p))
      event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
  }

  public static void addModifier(
      ServerPlayer p,
      net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
      ResourceLocation id,
      double amount) {
    var a = p.getAttribute(attribute);
    if (a != null && a.getModifier(id) == null)
      a.addTransientModifier(
          new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
  }

  public static void removeModifier(
      ServerPlayer p,
      net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
      ResourceLocation id) {
    var a = p.getAttribute(attribute);
    if (a != null) a.removeModifier(id);
  }
}
