package dev.thida;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Single server presentation stream. Projection reads game state but never advances gameplay. */
@EventBusSubscriber(modid = "thida")
public final class ThidaPassiveEffects {
  public enum Audience {
    PARTICIPANTS,
    OWNER,
    TARGET,
    PARTICIPANTS_AND_NEARBY
  }

  public interface AudiencePolicy {
    Audience audience(ThidaEffectEvent event);
  }

  private static AudiencePolicy audiencePolicy = event -> Audience.PARTICIPANTS;
  private static final Map<MinecraftServer, Timeline> SERVERS = new WeakHashMap<>();
  private static final Set<String> PROJECTED =
      Set.of(
          "NAROTRA_ATTEMPT_STARTED",
          "NAROTRA_LINKED",
          "NILAKAN_LINKED",
          "LINK_RESUMED",
          "SPELL_EFFECT_STARTED",
          "ULTIMATE_STARTED",
          "PARASITE_LINKED",
          "HEART_LINKED",
          "HEART_INVULNERABILITY_STARTED",
          "HEART_INVULNERABILITY_ENDED");

  private ThidaPassiveEffects() {}

  public static void installAudiencePolicy(AudiencePolicy policy) {
    audiencePolicy = policy == null ? event -> Audience.PARTICIPANTS : policy;
  }

  static String power(String value) {
    return switch (value) {
      case "NAROTRA" -> "narotra_doll";
      case "NILAKAN" -> "nilakan_doll";
      default -> value;
    };
  }

  public static void emit(
      String event,
      String power,
      ServerPlayer owner,
      ServerPlayer target,
      Vec3 position,
      InteractionHand hand,
      String reason) {
    if (owner != null) {
      UUID targetId = target == null ? null : target.getUUID();
      if (targetId == null) {
        var root = ThidaEvents.state(owner);
        String key = event.equals("NAROTRA_LINK_FAILED") ? "gazeTarget" : power(power);
        if (root.hasUUID(key)) targetId = root.getUUID(key);
      }
      emit(
          owner.server,
          event,
          power,
          owner.getUUID(),
          targetId,
          "",
          reason,
          ThidaEffectEvent.Anchor.USER,
          hand == null ? "" : hand.name());
    }
  }

  public static void emitAtRecipient(
      String event, String power, ServerPlayer owner, ServerPlayer target, ServerPlayer recipient) {
    emit(
        owner.server,
        event,
        power,
        owner.getUUID(),
        target == null ? null : target.getUUID(),
        "",
        "",
        recipient == owner ? ThidaEffectEvent.Anchor.USER : ThidaEffectEvent.Anchor.TARGET,
        "");
  }

  public static void emit(
      MinecraftServer server,
      String event,
      String rawPower,
      UUID ownerId,
      UUID targetId,
      String spell,
      String reason,
      ThidaEffectEvent.Anchor anchor,
      String hand) {
    String power = power(rawPower);
    var timeline = SERVERS.computeIfAbsent(server, unused -> new Timeline());
    var owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
    var target = targetId == null ? null : server.getPlayerList().getPlayer(targetId);
    if (event.equals("NAROTRA_ATTEMPT_STARTED") && owner != null)
      ThidaEvents.state(owner).putUUID("thida_effect_attempt", UUID.randomUUID());
    if (event.equals("HEART_LINKED") || event.equals("PARASITE_LINKED")) {
      var store = ThidaBondData.get(server);
      UUID activation = identity(server, store.link(ownerId, power));
      for (UUID recipient : new UUID[] {ownerId, targetId})
        if (recipient != null) {
          store.player(recipient).putUUID("fx_" + power + "_owner", ownerId);
          store.player(recipient).putUUID("fx_" + power + "_activation", activation);
        }
    }
    if (PROJECTED.contains(event)) {
      reconcile(server);
      return;
    }
    if (event.equals("LINK_CANCELLED")) return;
    if (Set.of("LINK_PAUSED", "LINK_ENDED", "HEART_ENDED", "PARASITE_BROKEN").contains(event)) {
      if (end(server, ownerId, power, "bond", event, reason)) return;
    }
    if (event.equals("NAROTRA_LINK_FAILED"))
      end(server, ownerId, power, "attempt", "NAROTRA_ATTEMPT_ENDED", reason);
    var ownerPoint = point(owner);
    var targetPoint = point(target);
    var position = anchor == ThidaEffectEvent.Anchor.TARGET ? targetPoint : ownerPoint;
    if (position == null) position = targetPoint;
    if (position == null) return;
    long now = ThidaBonds.now(server);
    UUID instance = UUID.randomUUID();
    UUID activation = instance;
    if (event.equals("SPELL_CAST"))
      activation =
          identity(
              server,
              ThidaBondData.child(ThidaBondData.get(server).player(ownerId), "spells")
                  .getCompound(power));
    else if (ownerId != null && ThidaBondData.get(server).link(ownerId, power).hasUUID("target"))
      activation = identity(server, ThidaBondData.get(server).link(ownerId, power));
    var message =
        new ThidaEffectEvent(
            timeline.epoch,
            ++timeline.revision,
            instance,
            activation,
            event,
            power,
            spell,
            event.equals("SPELL_CAST") ? "spell" : "pulse",
            ThidaEffectEvent.Phase.ONE_SHOT,
            ownerId,
            targetId,
            ownerPoint,
            targetPoint,
            position,
            anchor,
            hand,
            now,
            now,
            now,
            reason);
    send(server, message, recipients(server, message));
  }

  public static boolean end(
      MinecraftServer server,
      UUID owner,
      String power,
      String component,
      String event,
      String reason) {
    var timeline = SERVERS.get(server);
    if (timeline == null) return false;
    boolean sent = false;
    for (var active : List.copyOf(timeline.active.values())) {
      var message = active.message;
      if (Objects.equals(owner, message.owner())
          && power.equals(message.power())
          && component.equals(message.component())) {
        send(
            server,
            message.delivery(
                ++timeline.revision,
                ThidaEffectEvent.Phase.STOP,
                ThidaBonds.now(server),
                event,
                reason),
            active.viewers);
        timeline.active.remove(message.instance());
        sent = true;
      }
    }
    return sent;
  }

  static ThidaEffectEvent.Point point(ServerPlayer player) {
    return player == null
        ? null
        : new ThidaEffectEvent.Point(
            player.level().dimension().location().toString(),
            player.getX(),
            player.getY(),
            player.getZ());
  }

  static UUID identity(MinecraftServer server, CompoundTag record) {
    if (!record.hasUUID("thida_effect_id")) {
      record.putUUID("thida_effect_id", UUID.randomUUID());
      ThidaBondData.get(server).setDirty();
    }
    return record.getUUID("thida_effect_id");
  }

  private static Set<UUID> recipients(MinecraftServer server, ThidaEffectEvent message) {
    Set<UUID> result = new HashSet<>();
    Audience audience;
    try {
      audience = audiencePolicy.audience(message);
    } catch (RuntimeException ignored) {
      audience = Audience.PARTICIPANTS;
    }
    if (audience != Audience.TARGET && message.owner() != null) result.add(message.owner());
    if (audience != Audience.OWNER && message.target() != null) result.add(message.target());
    if (audience == Audience.PARTICIPANTS_AND_NEARBY)
      for (var viewer : server.getPlayerList().getPlayers()) {
        var point = message.point();
        if (viewer.level().dimension().location().toString().equals(point.dimension())
            && viewer.distanceToSqr(point.x(), point.y(), point.z()) <= 4096)
          result.add(viewer.getUUID());
      }
    result.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    return result;
  }

  private static void send(
      MinecraftServer server, ThidaEffectEvent event, Collection<UUID> recipients) {
    var packet = new ThidaNetwork.PassiveEffectEvent(event.save());
    for (UUID id : recipients) {
      var player = server.getPlayerList().getPlayer(id);
      if (player != null && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer))
        PacketDistributor.sendToPlayer(player, packet);
    }
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    reconcile(event.getServer());
  }

  public static void reconcile(MinecraftServer server) {
    var timeline = SERVERS.computeIfAbsent(server, unused -> new Timeline());
    long now = ThidaBonds.now(server);
    var desired = ThidaEffectProjection.read(server, timeline.epoch, now);
    for (var previous : List.copyOf(timeline.active.values()))
      if (!desired.containsKey(previous.message.instance())) {
        var message = previous.message;
        String reason = endReason(server, message, now);
        send(
            server,
            message.delivery(
                ++timeline.revision, ThidaEffectEvent.Phase.STOP, now, stopEvent(message), reason),
            previous.viewers);
        timeline.active.remove(message.instance());
      }
    for (var message : desired.values()) {
      var viewers = recipients(server, message);
      var previous = timeline.active.get(message.instance());
      if (previous == null) {
        boolean fresh = now - message.startedTick() <= 1;
        boolean resumed =
            timeline.seen.contains(message.instance())
                && message.component().equals("bond")
                && ThidaTooltips.passive(message.power());
        var start =
            message.delivery(
                ++timeline.revision,
                fresh ? ThidaEffectEvent.Phase.START : ThidaEffectEvent.Phase.SYNC,
                now,
                resumed ? "LINK_RESUMED" : message.event(),
                fresh ? "" : "RESTORED");
        send(server, start, viewers);
        timeline.active.put(message.instance(), new Active(start, viewers));
        timeline.seen.add(message.instance());
        if (timeline.seen.size() > 8192) timeline.seen.remove(timeline.seen.iterator().next());
      } else {
        var removed = new HashSet<>(previous.viewers);
        removed.removeAll(viewers);
        if (!removed.isEmpty())
          send(
              server,
              previous.message.delivery(
                  ++timeline.revision,
                  ThidaEffectEvent.Phase.STOP,
                  now,
                  stopEvent(message),
                  "AUDIENCE_CHANGED"),
              removed);
        var added = new HashSet<>(viewers);
        added.removeAll(previous.viewers);
        if (!previous.message.point().dimension().equals(message.point().dimension()))
          added.addAll(viewers);
        if (!added.isEmpty())
          send(
              server,
              message.delivery(
                  ++timeline.revision,
                  ThidaEffectEvent.Phase.SYNC,
                  now,
                  message.event(),
                  "RESTORED"),
              added);
        timeline.active.put(message.instance(), new Active(message, viewers));
      }
    }
  }

  private static String endReason(MinecraftServer server, ThidaEffectEvent event, long now) {
    if (event.endsTick() > 0 && now >= event.endsTick()) return "EXPIRED";
    var owner = event.owner() == null ? null : server.getPlayerList().getPlayer(event.owner());
    var target = event.target() == null ? null : server.getPlayerList().getPlayer(event.target());
    if (event.owner() != null && owner == null) return "OWNER_OFFLINE";
    if (event.target() != null && target == null) return "TARGET_OFFLINE";
    if (owner != null && !owner.isAlive() || target != null && !target.isAlive()) return "DEATH";
    if (owner != null
        && event.component().equals("attempt")
        && ThidaEvents.state(owner).hasUUID("narotra_doll")
        && ThidaEvents.state(owner).getUUID("narotra_doll").equals(event.target()))
      return "COMPLETED";
    if (owner != null && target != null && owner.level() != target.level())
      return "DIMENSION_CHANGED";
    return "UNAVAILABLE";
  }

  public static java.util.List<ThidaEffectEvent> snapshot(MinecraftServer server) {
    var timeline = SERVERS.get(server);
    return timeline == null
        ? java.util.List.of()
        : timeline.active.values().stream().map(Active::message).toList();
  }

  private static String stopEvent(ThidaEffectEvent message) {
    return switch (message.component()) {
      case "attempt" -> "NAROTRA_ATTEMPT_ENDED";
      case "spell" -> "SPELL_EFFECT_ENDED";
      case "ultimate" -> "ULTIMATE_ENDED";
      case "invulnerability" -> "HEART_INVULNERABILITY_ENDED";
      case "bond" ->
          message.power().equals("heart")
              ? "HEART_ENDED"
              : message.power().equals("parasite") ? "PARASITE_BROKEN" : "LINK_PAUSED";
      default -> message.power().equals("heart") ? "HEART_BUFF_ENDED" : "PARASITE_BUFF_ENDED";
    };
  }

  private record Active(ThidaEffectEvent message, Set<UUID> viewers) {}

  private static final class Timeline {
    final UUID epoch = UUID.randomUUID();
    long revision;
    final Map<UUID, Active> active = new LinkedHashMap<>();
    final Set<UUID> seen = new LinkedHashSet<>();
  }

  @SubscribeEvent
  public static void stopped(ServerStoppedEvent event) {
    SERVERS.remove(event.getServer());
    installAudiencePolicy(null);
    ThidaSkillIntegration.install(null);
    ThidaManaIntegration.install(null);
    ThidaMayaromScaling.install(null);
  }
}
