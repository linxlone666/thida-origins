package dev.thida;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Optional AAA integration events; only reads gameplay state. */
final class ThidaRuenPresentation {
  static final double VIEW_RADIUS_SQUARED =
      (ThidaRuenSaneha.RADIUS + 96) * (ThidaRuenSaneha.RADIUS + 96);

  private ThidaRuenPresentation() {}

  static void project(
      Map<UUID, ThidaEffectEvent> result,
      MinecraftServer server,
      UUID epoch,
      UUID owner,
      ServerPlayer caster,
      CompoundTag domain,
      long now) {
    if (!ThidaRuenSaneha.active(domain, now)
        || !domain.hasUUID("activation")
        || caster != null && !ThidaPermissions.unlocked(caster, 4)) return;
    UUID activation = domain.getUUID("activation");
    var c = ThidaRuenSaneha.center(domain);
    var point = new ThidaEffectEvent.Point(domain.getString("dimension"), c.x, c.y, c.z);
    result.put(
        activation,
        new ThidaEffectEvent(
            epoch,
            0,
            activation,
            activation,
            "RUEN_DOMAIN_STARTED",
            ThidaRuenSaneha.ID,
            "",
            "domain",
            ThidaEffectEvent.Phase.START,
            owner,
            null,
            ThidaPassiveEffects.point(caster),
            null,
            point,
            ThidaEffectEvent.Anchor.WORLD,
            "",
            domain.getLong("started"),
            domain.getLong("ends"),
            now,
            ""));
    for (var recipient : server.getPlayerList().getPlayers()) {
      long start = ThidaRuenSaneha.wardStarted(domain, owner, recipient, now);
      if (start < 0) continue;
      UUID instance =
          UUID.nameUUIDFromBytes(
              (activation + ":ward:" + recipient.getUUID())
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      var target = ThidaPassiveEffects.point(recipient);
      result.put(
          instance,
          new ThidaEffectEvent(
              epoch,
              0,
              instance,
              activation,
              "RUEN_WARD_STARTED",
              ThidaRuenSaneha.ID,
              "",
              "ward",
              ThidaEffectEvent.Phase.START,
              owner,
              recipient.getUUID(),
              ThidaPassiveEffects.point(caster),
              target,
              target,
              ThidaEffectEvent.Anchor.TARGET,
              "",
              start,
              Math.min(start + ThidaRuenSaneha.WARD, domain.getLong("ends")),
              now,
              ""));
    }
  }
}
