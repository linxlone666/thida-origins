package dev.thida;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = "thida")
public final class ThidaSpells {
  private static final Map<MinecraftServer, Long> LAST_TICK = new WeakHashMap<>();
  private static final java.util.Set<UUID> CASTING = new java.util.HashSet<>();

  private ThidaSpells() {}

  private static String cd(String kind, int spell) {
    return "spellCd_" + kind + "_" + spell;
  }

  public static String failure(ServerPlayer owner, String kind, int spell) {
    String validation = validate(owner, kind, spell);
    if (!validation.isEmpty()) return validation;
    return integrationFailure(
        ThidaSkillIntegration.resolve(owner, ThidaSpell.find(kind, spell).orElseThrow()));
  }

  private static String integrationFailure(ThidaSkillIntegration.Resolution result) {
    return switch (result.status()) {
      case READY -> "";
      case UNAVAILABLE -> ThidaMessages.text("message.thida.spell.unavailable");
      case NOT_READY -> ThidaMessages.text("message.thida.spell.not_ready");
      case INVALID -> ThidaMessages.text("message.thida.spell.invalid");
    };
  }

  private static String validate(ServerPlayer owner, String kind, int spell) {
    if (!ThidaTooltips.passive(kind) || spell < 0 || spell > 2)
      return ThidaMessages.text("ui.thida.spells.validate.1");
    if (!ThidaEvents.isThida(owner) || !ThidaBonds.dollsAllowed(owner))
      return ThidaMessages.text("ui.thida.spells.validate.2");
    if (!ThidaEvents.holds(owner, kind)) return ThidaMessages.text("ui.thida.spells.validate.3");
    if (ThidaBonds.conflictingPassives(owner))
      return ThidaMessages.text("ui.thida.spells.validate.4");
    if (!ThidaPassives.holdsLinkedDoll(owner, kind))
      return ThidaMessages.text("ui.thida.spells.validate.5");
    var target = ThidaBonds.passiveTarget(owner, kind);
    if (target == null || !target.isAlive())
      return ThidaMessages.text("ui.thida.spells.validate.6");
    if (ThidaBonds.data(owner).getLong(cd(kind, spell)) > ThidaBonds.now(owner.server))
      return ThidaMessages.text("ui.thida.spells.validate.7");
    return "";
  }

  public static void cast(ServerPlayer owner, String kind, int spell) {
    if (!CASTING.add(owner.getUUID())) return;
    try {
      castOnce(owner, kind, spell);
    } finally {
      CASTING.remove(owner.getUUID());
    }
  }

  private static void castOnce(ServerPlayer owner, String kind, int spell) {
    String reason = validate(owner, kind, spell);
    if (!reason.isEmpty()) {
      ThidaEvents.tell(owner, reason);
      return;
    }
    var target = ThidaBonds.passiveTarget(owner, kind);
    var descriptor = ThidaSpell.find(kind, spell).orElseThrow();
    var resolution = ThidaSkillIntegration.resolve(owner, descriptor);
    String integration = integrationFailure(resolution);
    if (!integration.isEmpty()) {
      ThidaEvents.tell(owner, integration);
      return;
    }
    var quote = resolution.quote();
    boolean paid;
    try {
      paid = quote.payment().consume();
    } catch (RuntimeException error) {
      paid = false;
    }
    if (target == null || !paid) {
      ThidaEvents.tell(owner, ThidaMessages.text("message.thida.spell.no_mana"));
      return;
    }
    stop(owner.server, owner.getUUID(), kind, "REPLACED");
    var store = ThidaBondData.get(owner.server);
    var n = new CompoundTag();
    long time = ThidaBonds.now(owner.server);
    n.putUUID("target", target.getUUID());
    n.putInt("spell", spell);
    n.putLong("expires", time + (spell == 1 ? 100 : 200));
    n.putLong("started", time);
    n.putDouble("percent", quote.percent());
    n.putFloat("bleedHp", quote.bleedHp());
    n.putLong("nextBleed", time + 20);
    ThidaBondData.child(store.player(owner), "spells").put(kind, n);
    store.player(owner).putLong(cd(kind, spell), time + 200);
    store.setDirty();
    if (kind.equals("narotra_doll") && spell == 1) ThidaEvents.cleanse(target);
    if (kind.equals("narotra_doll") && spell == 2)
      ThidaHealing.apply(owner, target, quote.healHp(), ThidaHealing.Kind.SKILL);
    if (kind.equals("nilakan_doll") && spell == 1 && !ThidaBonds.warded(target)) {
      var previous = target.getEffect(MobEffects.BLINDNESS);
      if (previous != null) {
        n.putLong("priorBlindUntil", time + previous.getDuration());
        n.putInt("priorBlindAmp", previous.getAmplifier());
      }
      target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
      if (target.hasEffect(MobEffects.BLINDNESS)
          && target.getEffect(MobEffects.BLINDNESS).getAmplifier() == 0
          && target.getEffect(MobEffects.BLINDNESS).getDuration() <= 100)
        target.getPersistentData().putUUID("thida_spell_blind_owner", owner.getUUID());
      target.addEffect(new MobEffectInstance(ThidaMod.SPELL_BLEED, 100));
      n.putBoolean("bleedStarted", target.hasEffect(ThidaMod.SPELL_BLEED));
    }
    apply(target, owner.getUUID(), kind, n);
    ThidaSkillEffects.spell("SPELL_CAST", descriptor, owner, target);
    ThidaPassiveEffects.reconcile(owner.server);
  }

  private static ResourceLocation modifier(UUID owner, String kind) {
    return ResourceLocation.fromNamespaceAndPath("thida", "spell_" + owner + "_" + kind);
  }

  private static void apply(ServerPlayer target, UUID owner, String kind, CompoundTag n) {
    if (kind.equals("narotra_doll") && n.getInt("spell") == 2)
      ThidaBonds.addModifier(
          target, Attributes.MOVEMENT_SPEED, modifier(owner, kind), n.getDouble("percent"));
    if (kind.equals("nilakan_doll") && n.getInt("spell") == 0) {
      if (ThidaBonds.warded(target))
        ThidaBonds.removeModifier(target, Attributes.ATTACK_SPEED, modifier(owner, kind));
      else
        ThidaBonds.addModifier(
            target, Attributes.ATTACK_SPEED, modifier(owner, kind), -n.getDouble("percent"));
    }
  }

  public static void stop(MinecraftServer server, UUID ownerId, String kind) {
    stop(server, ownerId, kind, "UNLINKED");
  }

  public static void stop(MinecraftServer server, UUID ownerId, String kind, String reason) {
    var store = ThidaBondData.get(server);
    var spells = ThidaBondData.child(store.player(ownerId), "spells");
    var n = spells.getCompound(kind);
    if (!n.hasUUID("target")) return;
    var target = server.getPlayerList().getPlayer(n.getUUID("target"));
    if (target != null) remove(target, ownerId, kind, n);
    else {
      var clean = ThidaBondData.child(store.player(n.getUUID("target")), "spellCleanup");
      var copy = n.copy();
      copy.putUUID("owner", ownerId);
      copy.putString("kind", kind);
      clean.put(ownerId + kind, copy);
    }
    ThidaPassiveEffects.end(
        server,
        ownerId,
        kind,
        "spell",
        reason.equals("REPLACED") ? "SPELL_EFFECT_REPLACED" : "SPELL_EFFECT_ENDED",
        reason);
    spells.remove(kind);
    store.setDirty();
  }

  private static void remove(ServerPlayer target, UUID owner, String kind, CompoundTag n) {
    ThidaBonds.removeModifier(target, Attributes.MOVEMENT_SPEED, modifier(owner, kind));
    ThidaBonds.removeModifier(target, Attributes.ATTACK_SPEED, modifier(owner, kind));
    if (kind.equals("nilakan_doll") && n.getInt("spell") == 1) {
      target.removeEffect(ThidaMod.SPELL_BLEED);
      var blind = target.getEffect(MobEffects.BLINDNESS);
      var persistent = target.getPersistentData();
      boolean owned =
          persistent.hasUUID("thida_spell_blind_owner")
              && persistent.getUUID("thida_spell_blind_owner").equals(owner);
      if (owned && blind != null && blind.getAmplifier() == 0 && blind.getDuration() <= 100)
        target.removeEffect(MobEffects.BLINDNESS);
      if (owned) persistent.remove("thida_spell_blind_owner");
      long prior = n.getLong("priorBlindUntil") - ThidaBonds.now(target.server);
      if (prior > 0)
        target.addEffect(
            new MobEffectInstance(MobEffects.BLINDNESS, (int) prior, n.getInt("priorBlindAmp")));
    }
  }

  public static void tick(MinecraftServer server) {
    long time = ThidaBonds.now(server);
    if (LAST_TICK.getOrDefault(server, Long.MIN_VALUE) == time) return;
    LAST_TICK.put(server, time);
    var store = ThidaBondData.get(server);
    for (var target : server.getPlayerList().getPlayers()) {
      var cleanup = ThidaBondData.child(store.player(target), "spellCleanup");
      for (String key : java.util.Set.copyOf(cleanup.getAllKeys())) {
        var n = cleanup.getCompound(key);
        remove(target, n.getUUID("owner"), n.getString("kind"), n);
        cleanup.remove(key);
        store.setDirty();
      }
    }
    for (String id : store.ids()) {
      UUID ownerId = UUID.fromString(id);
      var spells = ThidaBondData.child(store.player(ownerId), "spells");
      for (String kind : java.util.Set.copyOf(spells.getAllKeys())) {
        var n = spells.getCompound(kind);
        boolean bleed = kind.equals("nilakan_doll") && n.getInt("spell") == 1;
        if (n.getLong("expires") < time || n.getLong("expires") == time && !bleed) {
          stop(server, ownerId, kind, "EXPIRED");
          continue;
        }
        var target = server.getPlayerList().getPlayer(n.getUUID("target"));
        if (target == null || !target.isAlive()) continue;
        apply(target, ownerId, kind, n);
        if (bleed
            && n.getBoolean("bleedStarted")
            && !n.getBoolean("bleedCleansed")
            && (target.hasEffect(ThidaMod.SPELL_BLEED) || n.getLong("expires") == time)
            && !ThidaBonds.warded(target)
            && n.getLong("nextBleed") <= time) {
          target.setHealth(
              target.getHealth()
                  - CombatRules.nonlethalDamage(target.getHealth(), n.getFloat("bleedHp")));
          n.putLong("nextBleed", time + 20);
          store.setDirty();
        }
        if (n.getLong("expires") <= time) stop(server, ownerId, kind, "EXPIRED");
      }
    }
  }

  public static boolean warded(ServerPlayer target) {
    return percent(target, "narotra_doll", 1) > -1;
  }

  public static void cleanseBleed(ServerPlayer target) {
    var store = ThidaBondData.get(target.server);
    for (String id : store.ids()) {
      var n =
          ThidaBondData.child(store.player(UUID.fromString(id)), "spells")
              .getCompound("nilakan_doll");
      if (n.hasUUID("target")
          && n.getUUID("target").equals(target.getUUID())
          && n.getInt("spell") == 1) n.putBoolean("bleedCleansed", true);
    }
    store.setDirty();
  }

  /** A later blindness application belongs to that source and must survive our cleanup. */
  @SubscribeEvent
  public static void blindnessReplaced(
      net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) {
    if (event.getEffectInstance().getEffect().equals(MobEffects.BLINDNESS))
      event.getEntity().getPersistentData().remove("thida_spell_blind_owner");
  }

  private static double percent(ServerPlayer target, String kind, int spell) {
    var store = ThidaBondData.get(target.server);
    for (String id : store.ids()) {
      var n = ThidaBondData.child(store.player(UUID.fromString(id)), "spells").getCompound(kind);
      if (n.hasUUID("target")
          && n.getUUID("target").equals(target.getUUID())
          && n.getInt("spell") == spell
          && n.getLong("expires") > ThidaBonds.now(target.server)) return n.getDouble("percent");
    }
    return -1;
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void damage(LivingIncomingDamageEvent event) {
    double amount = event.getAmount();
    if (event.getEntity() instanceof ServerPlayer target) {
      double reduction = percent(target, "narotra_doll", 0),
          vulnerability = percent(target, "nilakan_doll", 2);
      if (reduction >= 0) amount *= Math.max(0, 1 - reduction);
      if (vulnerability >= 0 && !ThidaBonds.warded(target)) amount *= 1 + vulnerability;
    }
    if (event.getSource().getEntity() instanceof ServerPlayer caster) {
      double weakness = percent(caster, "nilakan_doll", 0);
      if (weakness >= 0 && !ThidaBonds.warded(caster)) amount *= Math.max(0, 1 - weakness);
    }
    event.setAmount((float) amount);
  }
}
