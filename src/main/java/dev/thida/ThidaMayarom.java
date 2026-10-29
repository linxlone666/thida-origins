package dev.thida;

import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Body-form ultimate. Its clock lives in SavedData, independently of the player's entity. */
@EventBusSubscriber(modid = "thida")
public final class ThidaMayarom {
  public static final String ID = "mayarom";
  public static final int DURATION = 5 * 60 * 20, COOLDOWN = 20 * 60 * 20;
  private static final String UNTIL = "mayaromUntil", CD = "mayaromCooldown";
  private static final ResourceKey<DamageType> SHARED =
      ResourceKey.create(
          Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("thida", "shared"));

  private ThidaMayarom() {}

  public static boolean active(ServerPlayer p) {
    return ThidaEvents.isThida(p) && remaining(p) > 0;
  }

  public static long remaining(ServerPlayer p) {
    return Math.max(0, ThidaBonds.data(p).getLong(UNTIL) - ThidaBonds.now(p.server));
  }

  public static long cooldown(ServerPlayer p) {
    return Math.max(0, ThidaBonds.data(p).getLong(CD) - ThidaBonds.now(p.server));
  }

  public static void toggle(ServerPlayer p) {
    if (!ThidaEvents.isThida(p) || !p.isAlive() || p.isSpectator()) return;
    finishExpired(p.server);
    if (active(p)) {
      end(p.server, p.getUUID(), ThidaBonds.now(p.server), "PLAYER_CANCELLED");
      return;
    }
    if (!ThidaPermissions.unlocked(p, 4)) {
      ThidaEvents.tell(p, ThidaMessages.text("message.thida.mayarom.locked"));
      return;
    }
    if (cooldown(p) > 0) {
      ThidaEvents.tell(p, ThidaMessages.text("message.thida.mayarom.cooldown"));
      return;
    }
    ThidaBonds.data(p).putLong(UNTIL, ThidaBonds.now(p.server) + DURATION);
    ThidaBonds.data(p).remove("mayaromPresentation");
    ThidaBondData.get(p.server).setDirty();
    ThidaEvents.cleanse(p);
    ThidaSkillEffects.emit("ULTIMATE_STARTED", ID, p, null, "");
    ThidaEvents.tell(p, ThidaMessages.text("message.thida.mayarom.started"));
  }

  private static void end(MinecraftServer server, UUID ownerId, long endedAt, String reason) {
    var store = ThidaBondData.get(server);
    var n = store.player(ownerId);
    if (!n.contains(UNTIL)) return;
    n.remove(UNTIL);
    n.putLong(CD, endedAt + COOLDOWN);
    store.setDirty();
    ThidaPassiveEffects.end(server, ownerId, ID, "ultimate", "ULTIMATE_ENDED", reason);
  }

  static void endForDebug(ServerPlayer p) {
    end(p.server, p.getUUID(), ThidaBonds.now(p.server), "DEBUG_END");
  }

  private static void finishExpired(MinecraftServer server) {
    var store = ThidaBondData.get(server);
    long now = ThidaBonds.now(server);
    for (String id : store.ids()) {
      var n = store.player(UUID.fromString(id));
      if (n.contains(UNTIL) && n.getLong(UNTIL) <= now)
        end(server, UUID.fromString(id), n.getLong(UNTIL), "EXPIRED");
    }
  }

  /** Respects the existing doll item, dimension, death and bond availability rules. */
  private static ServerPlayer narotraOwner(ServerPlayer target) {
    for (var owner : target.server.getPlayerList().getPlayers())
      if (active(owner)
          && ThidaPassives.isBondAvailable(owner, "narotra_doll")
          && ThidaBonds.passiveTarget(owner, "narotra_doll") == target) return owner;
    return null;
  }

  public static void hud(ServerPlayer p, CompoundTag packet) {
    packet.putLong("domain", remaining(p));
    packet.putBoolean("inDomain", active(p));
    packet.putBoolean("unlock4", ThidaPermissions.unlocked(p, 4));
    packet.putInt("domainTotal", DURATION);
    packet.putInt("domainCooldownTotal", COOLDOWN);
    packet.getCompound("cooldowns").putLong("cd_domain_talisman", cooldown(p));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void damage(LivingIncomingDamageEvent event) {
    if (event.getSource().is(SHARED)) return;
    double amount = event.getAmount();
    if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
      boolean melee = event.getSource().getDirectEntity() == attacker;
      if (melee && active(attacker)) amount = ThidaMayaromScaling.meleeDamage(attacker, amount);
      if (melee && narotraOwner(attacker) != null) amount *= 1.20;
      if (!ThidaBonds.warded(attacker) && attacker.hasEffect(ThidaMod.MAYAROM_WEAKNESS))
        amount *= .80;
    }
    if (event.getEntity() instanceof ServerPlayer victim) {
      if (active(victim)) amount *= .30;
      if (narotraOwner(victim) != null) amount *= .80;
      if (!ThidaBonds.warded(victim) && victim.hasEffect(ThidaMod.MAYAROM_VULNERABILITY))
        amount *= 1.20;
    }
    event.setAmount((float) amount);
  }

  @SubscribeEvent
  public static void hit(LivingDamageEvent.Post event) {
    if (event.getSource().is(SHARED)) return;
    if (event.getNewDamage() <= 0
        || !(event.getEntity() instanceof ServerPlayer victim)
        || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
    if (active(attacker) && event.getSource().getDirectEntity() == attacker)
      curse(attacker, victim, false);
    var owner = narotraOwner(attacker);
    if (owner != null) curse(owner, victim, true);
  }

  /** Temporary spell results only: no link, history entry, spell cooldown or replacement. */
  private static void curse(ServerPlayer owner, ServerPlayer target, boolean allSpells) {
    if (ThidaBonds.warded(target)) return;
    long now = ThidaBonds.now(target.server);
    var n = ThidaBonds.data(target);
    target.addEffect(new MobEffectInstance(ThidaMod.MAYAROM_WEAKNESS, 200));
    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, allSpells ? 100 : 200));
    n.putLong("mayaromWeakUntil", now + 200);
    if (allSpells) {
      boolean wasBleeding =
          target.hasEffect(ThidaMod.MAYAROM_BLEED) && n.getLong("mayaromBleedUntil") > now;
      target.addEffect(new MobEffectInstance(ThidaMod.MAYAROM_SLOW, 200));
      target.addEffect(new MobEffectInstance(ThidaMod.MAYAROM_BLEED, 100));
      target.addEffect(new MobEffectInstance(ThidaMod.MAYAROM_VULNERABILITY, 200));
      n.putLong("mayaromBleedUntil", now + 100);
      // Repeated hits refresh the duration without postponing the next damage tick.
      if (!wasBleeding) n.putLong("mayaromNextBleed", now + 20);
      n.putLong("mayaromVulnerableUntil", now + 200);
    }
    ThidaBondData.get(target.server).setDirty();
    ThidaSkillEffects.emit(
        "ULTIMATE_HIT", ID, owner, target, allSpells ? "NILAKAN_ALL_SPELLS" : "MELEE_CURSE");
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    tick(event.getServer());
  }

  static void tick(MinecraftServer server) {
    finishExpired(server);
    long now = ThidaBonds.now(server);
    for (var p : server.getPlayerList().getPlayers()) {
      if (active(p)) ThidaEvents.cleanse(p);
      var n = ThidaBonds.data(p);
      if (n.getLong("mayaromBleedUntil") <= now) p.removeEffect(ThidaMod.MAYAROM_BLEED);
      if (n.getLong("mayaromWeakUntil") <= now) {
        p.removeEffect(ThidaMod.MAYAROM_WEAKNESS);
        p.removeEffect(ThidaMod.MAYAROM_SLOW);
      }
      if (n.getLong("mayaromVulnerableUntil") <= now)
        p.removeEffect(ThidaMod.MAYAROM_VULNERABILITY);
      if (p.isAlive()
          && !ThidaBonds.warded(p)
          && p.hasEffect(ThidaMod.MAYAROM_BLEED)
          && n.getLong("mayaromNextBleed") <= now) {
        p.setHealth(p.getHealth() - CombatRules.nonlethalDamage(p.getHealth(), 2));
        n.putLong("mayaromNextBleed", now + 20);
        ThidaBondData.get(server).setDirty();
      }
    }
  }
}
