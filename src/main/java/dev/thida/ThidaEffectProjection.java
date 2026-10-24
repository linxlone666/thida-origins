package dev.thida;

import static dev.thida.ThidaPassiveEffects.identity;
import static dev.thida.ThidaPassiveEffects.point;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Read model of existing game state; only additive presentation identity metadata is initialized.
 */
final class ThidaEffectProjection {
  private ThidaEffectProjection() {}

  static Map<UUID, ThidaEffectEvent> read(MinecraftServer server, UUID epoch, long now) {
    Map<UUID, ThidaEffectEvent> result = new LinkedHashMap<>();
    var store = ThidaBondData.get(server);
    for (String id : store.ids()) {
      UUID ownerId = UUID.fromString(id);
      var owner = server.getPlayerList().getPlayer(ownerId);
      if (owner != null) {
        var root = ThidaEvents.state(owner);
        if (root.hasUUID("gazeTarget")) {
          var target = server.getPlayerList().getPlayer(root.getUUID("gazeTarget"));
          if (target != null) {
            if (!root.hasUUID("thida_effect_attempt"))
              root.putUUID("thida_effect_attempt", UUID.randomUUID());
            add(
                result,
                epoch,
                root.getUUID("thida_effect_attempt"),
                "NAROTRA_ATTEMPT_STARTED",
                "narotra_doll",
                "",
                "attempt",
                owner,
                target,
                ownerId,
                now - root.getInt("gaze"),
                0,
                now,
                ThidaEffectEvent.Anchor.HAND);
          }
        }
        for (String power : new String[] {"narotra_doll", "nilakan_doll", "parasite", "heart"}) {
          var bond = store.link(ownerId, power);
          var target =
              bond.hasUUID("target")
                  ? server.getPlayerList().getPlayer(bond.getUUID("target"))
                  : null;
          boolean available =
              target != null
                  && target.isAlive()
                  && owner.isAlive()
                  && target.level() == owner.level();
          if (ThidaTooltips.passive(power)) available = ThidaPassives.isBondAvailable(owner, power);
          if (available) {
            String start =
                power.equals("narotra_doll")
                    ? "NAROTRA_LINKED"
                    : power.equals("nilakan_doll")
                        ? "NILAKAN_LINKED"
                        : power.equals("heart") ? "HEART_LINKED" : "PARASITE_LINKED";
            add(
                result,
                epoch,
                identity(server, bond),
                start,
                power,
                "",
                "bond",
                owner,
                target,
                ownerId,
                bond.getLong("started"),
                0,
                now,
                ThidaEffectEvent.Anchor.TARGET);
          }
        }
      }
      var spells = ThidaBondData.child(store.player(ownerId), "spells");
      for (String power : spells.getAllKeys()) {
        var spell = spells.getCompound(power);
        var target =
            spell.hasUUID("target")
                ? server.getPlayerList().getPlayer(spell.getUUID("target"))
                : null;
        if (target != null && target.isAlive() && spell.getLong("expires") > now) {
          String spellId =
              ThidaSpell.find(power, spell.getInt("spell")).map(ThidaSpell::id).orElse("");
          long started =
              spell.contains("started")
                  ? spell.getLong("started")
                  : spell.getLong("expires") - (spell.getInt("spell") == 1 ? 100 : 200);
          add(
              result,
              epoch,
              identity(server, spell),
              "SPELL_EFFECT_STARTED",
              power,
              spellId,
              "spell",
              owner,
              target,
              ownerId,
              started,
              spell.getLong("expires"),
              now,
              ThidaEffectEvent.Anchor.TARGET);
        }
      }
      var recipient = server.getPlayerList().getPlayer(ownerId);
      if (recipient == null || !recipient.isAlive()) continue;
      var data = store.player(ownerId);
      for (var timer : TIMERS) {
        long deadline = data.getLong(timer.key);
        if (deadline <= now
            || timer.key.equals("parasiteAfterDefenseUntil")
                && !data.contains("parasiteAfterDefense")) continue;
        UUID caster =
            data.hasUUID("fx_" + timer.power + "_owner")
                ? data.getUUID("fx_" + timer.power + "_owner")
                : timer.component.equals("invulnerability")
                        && data.hasUUID("heartInvulnerabilityOwner")
                    ? data.getUUID("heartInvulnerabilityOwner")
                    : null;
        UUID activation =
            data.hasUUID("fx_" + timer.power + "_activation")
                ? data.getUUID("fx_" + timer.power + "_activation")
                : UUID.nameUUIDFromBytes(
                    (ownerId + timer.power + deadline).getBytes(StandardCharsets.UTF_8));
        String start =
            timer.component.equals("invulnerability")
                ? "HEART_INVULNERABILITY_STARTED"
                : timer.power.equals("heart") ? "HEART_BUFF_STARTED" : "PARASITE_BUFF_STARTED";
        add(
            result,
            epoch,
            activation,
            start,
            timer.power,
            "",
            timer.component,
            caster == null ? null : server.getPlayerList().getPlayer(caster),
            recipient,
            caster,
            deadline - timer.duration,
            deadline,
            now,
            ThidaEffectEvent.Anchor.TARGET);
      }
    }
    return result;
  }

  private static void add(
      Map<UUID, ThidaEffectEvent> result,
      UUID epoch,
      UUID activation,
      String event,
      String power,
      String spell,
      String component,
      ServerPlayer owner,
      ServerPlayer target,
      UUID ownerId,
      long started,
      long ends,
      long now,
      ThidaEffectEvent.Anchor anchor) {
    var ownerPoint = point(owner);
    var targetPoint = point(target);
    var position = anchor == ThidaEffectEvent.Anchor.TARGET ? targetPoint : ownerPoint;
    if (position == null) return;
    UUID instance =
        UUID.nameUUIDFromBytes(
            (activation
                    + ":"
                    + component
                    + ":"
                    + (target == null ? "" : target.getUUID())
                    + ":"
                    + ends)
                .getBytes(StandardCharsets.UTF_8));
    String hand =
        owner != null
                && ThidaEvents.id(owner.getOffhandItem()).equals(power)
                && !ThidaEvents.id(owner.getMainHandItem()).equals(power)
            ? "OFF_HAND"
            : "MAIN_HAND";
    result.put(
        instance,
        new ThidaEffectEvent(
            epoch,
            0,
            instance,
            activation,
            event,
            power,
            spell,
            component,
            ThidaEffectEvent.Phase.START,
            ownerId,
            target == null ? null : target.getUUID(),
            ownerPoint,
            targetPoint,
            position,
            anchor,
            hand,
            started,
            ends,
            now,
            ""));
  }

  private record Timer(String key, String power, String component, int duration) {}

  private static final List<Timer> TIMERS =
      List.of(
          new Timer("heartWard", "heart", "ward", 3600),
          new Timer("heartSpeed", "heart", "speed", 1000),
          new Timer("heartInvulnerable", "heart", "invulnerability", 100),
          new Timer("parasiteWard", "parasite", "ward", 2400),
          new Timer("parasiteSpeed", "parasite", "speed", 400),
          new Timer("parasiteAfterDefenseUntil", "parasite", "after_defense", 200));
}
