package dev.thida;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The domain belongs to the world clock, not the caster's player entity. */
@EventBusSubscriber(modid = "thida")
public final class ThidaRuenSaneha {
  static final String ID = "ruen_saneha", RECORD = "ruenSaneha", CD = "ruenSanehaCooldown";
  static final int DURATION = 6000, COOLDOWN = 24000, WARD = 2400, CAPACITY = 5;
  static final double RADIUS = 100;
  private static final ResourceLocation SPEED =
      ResourceLocation.fromNamespaceAndPath("thida", "ruen_speed");
  private static final ResourceLocation SHARED =
      ResourceLocation.fromNamespaceAndPath("thida", "shared");

  enum Side {
    INSIDE("inside"),
    OUTSIDE("outside");
    final String key;

    Side(String key) {
      this.key = key;
    }
  }

  private ThidaRuenSaneha() {}

  private static boolean allowed(ServerPlayer p) {
    return ThidaPermissions.unlocked(p, 4) && p.isAlive() && !p.isSpectator();
  }

  static CompoundTag domain(ServerPlayer p) {
    return ThidaBonds.data(p).getCompound(RECORD);
  }

  static Vec3 center(CompoundTag domain) {
    return new Vec3(domain.getDouble("x"), domain.getDouble("y"), domain.getDouble("z"));
  }

  static boolean contains(CompoundTag domain, ServerPlayer player) {
    return domain.getString("dimension").equals(player.level().dimension().location().toString())
        && player.position().distanceToSqr(center(domain)) <= RADIUS * RADIUS;
  }

  static boolean active(CompoundTag domain, long now) {
    return domain.getLong("ends") > now;
  }

  static boolean start(ServerPlayer owner) {
    if (!allowed(owner)) {
      tell(owner, "locked");
      return false;
    }
    var data = ThidaBonds.data(owner);
    var current = data.getCompound(RECORD);
    long now = ThidaBonds.now(owner.server);
    if (active(current, now)) return false;
    if (current.hasUUID("activation"))
      end(owner.server, owner.getUUID(), current.getLong("ends"), "EXPIRED");
    if (data.getLong(CD) > now) {
      tell(owner, "cooldown");
      return false;
    }
    var feet = owner.position().add(0, .01, 0);
    // A long vanilla ray can report its initial inside-shape offset when standing on the floor.
    var ground =
        owner
            .level()
            .clip(
                new ClipContext(
                    feet,
                    feet.add(0, -.5, 0),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    owner));
    if (ground.getType() == HitResult.Type.MISS)
      ground =
          owner
              .level()
              .clip(
                  new ClipContext(
                      feet,
                      new Vec3(
                          feet.x,
                          Math.max(owner.level().getMinBuildHeight(), owner.getY() - RADIUS),
                          feet.z),
                      ClipContext.Block.COLLIDER,
                      ClipContext.Fluid.NONE,
                      owner));
    if (ground.getType() == HitResult.Type.MISS
        || owner.position().distanceToSqr(ground.getLocation()) > RADIUS * RADIUS) {
      tell(owner, "no_ground");
      return false;
    }
    var point = ground.getLocation();
    var domain = new CompoundTag();
    domain.putUUID("activation", UUID.randomUUID());
    domain.putLong("started", now);
    domain.putLong("ends", now + DURATION);
    domain.putString("dimension", owner.level().dimension().location().toString());
    domain.putDouble("x", point.x);
    domain.putDouble("y", point.y);
    domain.putDouble("z", point.z);
    ThidaBondData.get(owner.server).putRuen(owner.getUUID(), domain);
    cleanse(owner);
    refreshSpeed(owner);
    ThidaPassiveEffects.reconcile(owner.server);
    tell(owner, "started");
    return true;
  }

  static boolean useTalisman(ServerPlayer owner) {
    if (!allowed(owner)) {
      tell(owner, "locked");
      return false;
    }
    if (!active(domain(owner), ThidaBonds.now(owner.server))) return start(owner);
    cancel(owner);
    return true;
  }

  static void request(ServerPlayer owner, CompoundTag request) {
    String action = request.getString("action");
    if (action.equals("ruen_start")) {
      start(owner);
      return;
    }
    var domain = domain(owner);
    if (!allowed(owner)
        || !active(domain, ThidaBonds.now(owner.server))
        || !domain.hasUUID("activation")
        || !request.hasUUID("activation")
        || !domain.getUUID("activation").equals(request.getUUID("activation"))) return;
    switch (action) {
      case "ruen_cancel" -> cancel(owner);
      case "ruen_roster" -> {
        long now = ThidaBonds.now(owner.server);
        var state = ThidaEvents.state(owner);
        if (state.getLong("ruenRosterAt") <= now) {
          state.putLong("ruenRosterAt", now + 10);
          sendRoster(owner, domain, "");
        }
      }
      case "ruen_list_pick" -> {
        Side side =
            switch (request.getString("side")) {
              case "inside" -> Side.INSIDE;
              case "outside" -> Side.OUTSIDE;
              default -> null;
            };
        if (side == null || !request.hasUUID("target")) return;
        long now = ThidaBonds.now(owner.server);
        var state = ThidaEvents.state(owner);
        if (state.getLong("ruenSelectAt") > now) return;
        state.putLong("ruenSelectAt", now + 5);
        var target = owner.server.getPlayerList().getPlayer(request.getUUID("target"));
        if (target != null
            && (!eligible(owner, domain, target)
                || !ThidaRuenTargeting.visible(owner, target.getEyePosition()))) target = null;
        String result = select(owner, domain, side, target);
        sendRoster(owner, domain, result);
      }
      case "ruen_pick_inside" -> selectAimed(owner, domain, Side.INSIDE, request);
      case "ruen_pick_outside" -> selectAimed(owner, domain, Side.OUTSIDE, request);
      default -> {}
    }
  }

  private static void cancel(ServerPlayer owner) {
    end(owner.server, owner.getUUID(), ThidaBonds.now(owner.server), "PLAYER_CANCELLED");
    tell(owner, "ended");
  }

  static void debugEnd(ServerPlayer owner) {
    var domain = domain(owner);
    if (domain.hasUUID("activation")) {
      long now = ThidaBonds.now(owner.server);
      end(owner.server, owner.getUUID(), Math.min(now, domain.getLong("ends")), "DEBUG_END");
    }
  }

  private static void end(MinecraftServer server, UUID owner, long at, String reason) {
    var store = ThidaBondData.get(server);
    var data = store.player(owner);
    var domain = data.getCompound(RECORD);
    if (!domain.hasUUID("activation")) return;
    store.putRuen(owner, null);
    data.putLong(CD, at + COOLDOWN);
    for (var player : server.getPlayerList().getPlayers())
      if (player.getUUID().equals(owner)
          || domain.getCompound(Side.INSIDE.key).contains(player.getStringUUID()))
        refreshSpeed(player);
    ThidaPassiveEffects.end(server, owner, ID, "domain", "RUEN_DOMAIN_ENDED", reason);
    ThidaPassiveEffects.end(server, owner, ID, "ward", "RUEN_WARD_ENDED", reason);
  }

  private static void selectAimed(
      ServerPlayer owner, CompoundTag domain, Side side, CompoundTag request) {
    var target = aimedTarget(owner, domain);
    if (target != null
        && request.hasUUID("target")
        && !request.getUUID("target").equals(target.getUUID())) target = null;
    select(owner, domain, side, target);
  }

  private static String select(
      ServerPlayer owner, CompoundTag domain, Side side, ServerPlayer target) {
    if (target == null) {
      tell(owner, "no_target");
      return "no_target";
    }
    String id = target.getStringUUID();
    if (assigned(domain, id)) {
      tell(owner, "already_selected");
      return "already_selected";
    }
    var members = ThidaBondData.child(domain, side.key);
    if (members.size() >= CAPACITY) {
      tell(owner, "full");
      return "full";
    }
    long now = ThidaBonds.now(owner.server);
    members.putLong(id, now);
    ThidaBondData.child(domain, "names").putString(id, target.getScoreboardName());
    ThidaBondData.get(owner.server).setDirty();
    if (side == Side.INSIDE) {
      cleanse(target);
      refreshSpeed(target);
      ThidaPassiveEffects.reconcile(owner.server);
    }
    ThidaPassiveEffects.emit(
        owner.server,
        "RUEN_MEMBER_SELECTED",
        ID,
        owner.getUUID(),
        target.getUUID(),
        side.key,
        "",
        ThidaEffectEvent.Anchor.TARGET,
        "");
    ThidaEvents.tell(
        owner,
        ThidaMessages.text("message.thida.ruen." + side.key) + ": " + target.getScoreboardName());
    return "added";
  }

  private static boolean assigned(CompoundTag domain, String id) {
    return domain.getCompound(Side.INSIDE.key).contains(id)
        || domain.getCompound(Side.OUTSIDE.key).contains(id);
  }

  private static double reach(ServerPlayer owner) {
    return (owner.server.getPlayerList().getViewDistance() + 1) * 16.0;
  }

  private static boolean eligible(ServerPlayer owner, CompoundTag domain, ServerPlayer target) {
    return contains(domain, target)
        && ThidaRuenTargeting.eligible(owner, target, center(domain), reach(owner));
  }

  private static void sendRoster(ServerPlayer owner, CompoundTag domain, String feedback) {
    if (owner instanceof FakePlayer) return;
    var packet = new CompoundTag();
    packet.putUUID("activation", domain.getUUID("activation"));
    packet.putString("feedback", feedback);
    var candidates = new ListTag();
    owner.server.getPlayerList().getPlayers().stream()
        .filter(
            target -> !assigned(domain, target.getStringUUID()) && eligible(owner, domain, target))
        .filter(target -> ThidaRuenTargeting.visible(owner, target.getEyePosition()))
        .sorted(
            Comparator.comparingDouble((ServerPlayer target) -> owner.distanceToSqr(target))
                .thenComparing(ServerPlayer::getScoreboardName))
        .forEach(
            target ->
                candidates.add(
                    rosterRow(
                        target.getUUID(),
                        target.getScoreboardName(),
                        (int) Math.round(owner.distanceTo(target)))));
    packet.put("candidates", candidates);
    for (Side side : Side.values()) {
      var members = new ListTag();
      for (String id : domain.getCompound(side.key).getAllKeys().stream().sorted().toList()) {
        UUID uuid = UUID.fromString(id);
        var member = owner.server.getPlayerList().getPlayer(uuid);
        String name =
            member != null ? member.getScoreboardName() : domain.getCompound("names").getString(id);
        if (name.isEmpty() && owner.server.getProfileCache() != null)
          name =
              owner.server.getProfileCache().get(uuid).map(profile -> profile.getName()).orElse("");
        members.add(rosterRow(uuid, name.isEmpty() ? id.substring(0, 8) : name, -1));
      }
      packet.put(side.key, members);
    }
    PacketDistributor.sendToPlayer(owner, new ThidaNetwork.RuenRoster(packet));
  }

  private static CompoundTag rosterRow(UUID target, String name, int distance) {
    var row = new CompoundTag();
    row.putUUID("id", target);
    row.putString("name", name);
    row.putInt("distance", distance);
    return row;
  }

  /** Resolve the crosshair independently, then compare with the client's optional preview. */
  private static ServerPlayer aimedTarget(ServerPlayer owner, CompoundTag domain) {
    if (!domain.getString("dimension").equals(owner.level().dimension().location().toString()))
      return null;
    return ThidaRuenTargeting.aimed(
        owner,
        owner.server.getPlayerList().getPlayers(),
        reach(owner),
        target -> eligible(owner, domain, target));
  }

  private static long selectedAt(CompoundTag domain, UUID owner, ServerPlayer p) {
    if (owner.equals(p.getUUID())) return domain.getLong("started");
    var members = domain.getCompound(Side.INSIDE.key);
    return members.contains(p.getStringUUID()) ? members.getLong(p.getStringUUID()) : -1;
  }

  /** Returns -1 for no buff, 0 for an active buff without ward, or the ward deadline. */
  private static long benefits(ServerPlayer p) {
    if (!p.isAlive() || p.isSpectator()) return -1;
    var store = ThidaBondData.get(p.server);
    long now = ThidaBonds.now(p.server), result = -1;
    for (UUID owner : store.ruenOwners()) {
      var domain = store.player(owner).getCompound(RECORD);
      if (!active(domain, now) || !contains(domain, p)) continue;
      var caster = p.server.getPlayerList().getPlayer(owner);
      if (caster != null && !ThidaPermissions.unlocked(caster, 4)) continue;
      long start = selectedAt(domain, owner, p);
      if (start >= 0) result = Math.max(result, start + WARD > now ? start + WARD : 0);
    }
    return result;
  }

  static boolean warded(ServerPlayer p) {
    return benefits(p) > 0;
  }

  static long wardStarted(CompoundTag domain, UUID owner, ServerPlayer recipient, long now) {
    if (!recipient.isAlive() || recipient.isSpectator() || !contains(domain, recipient)) return -1;
    long started = selectedAt(domain, owner, recipient);
    return started >= 0 && started + WARD > now ? started : -1;
  }

  static void refreshSpeed(ServerPlayer p) {
    var attribute = p.getAttribute(Attributes.MOVEMENT_SPEED);
    if (attribute == null) return;
    var speed = p.getEffect(MobEffects.MOVEMENT_SPEED);
    // Match vanilla Speed III without replacing or resetting any existing Speed potion.
    double vanilla = speed == null ? 1 : 1 + .20 * (speed.getAmplifier() + 1);
    double amount = vanilla < 1.6 && benefits(p) >= 0 ? 1.6 / vanilla - 1 : 0;
    var previous = attribute.getModifier(SPEED);
    if (previous != null && previous.amount() == amount) return;
    if (previous != null) attribute.removeModifier(SPEED);
    if (amount > 0) ThidaBonds.addModifier(p, Attributes.MOVEMENT_SPEED, SPEED, amount);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void damage(LivingIncomingDamageEvent event) {
    // Parasite sharing already splits the adjusted damage; applying multipliers again breaks it.
    if (event.getSource().typeHolder().is(SHARED)) return;
    float amount = event.getAmount();
    if (event.getSource().getEntity() instanceof ServerPlayer attacker && benefits(attacker) >= 0)
      amount *= 1.2f;
    if (event.getEntity() instanceof ServerPlayer victim && benefits(victim) >= 0) amount *= .5f;
    event.setAmount(amount);
  }

  @SubscribeEvent
  public static void playerTick(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer p) refreshSpeed(p);
  }

  @SubscribeEvent
  public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer p) refreshSpeed(p);
  }

  @SubscribeEvent
  public static void serverTick(ServerTickEvent.Post event) {
    tick(event.getServer());
  }

  static void tick(MinecraftServer server) {
    var store = ThidaBondData.get(server);
    long now = ThidaBonds.now(server);
    for (UUID owner : List.copyOf(store.ruenOwners())) {
      var domain = store.player(owner).getCompound(RECORD);
      if (!domain.hasUUID("activation")) continue;
      var caster = server.getPlayerList().getPlayer(owner);
      if (!active(domain, now)) end(server, owner, domain.getLong("ends"), "EXPIRED");
      else if (caster != null && !ThidaPermissions.unlocked(caster, 4))
        end(server, owner, now, "ORIGIN_LOST");
    }
  }

  static void hud(ServerPlayer owner, CompoundTag data) {
    var domain = domain(owner);
    long now = ThidaBonds.now(owner.server);
    boolean unlocked = ThidaPermissions.unlocked(owner, 4);
    data.putBoolean("unlock4", unlocked);
    data.putInt("domainTotal", DURATION);
    data.putInt("domainCooldownTotal", COOLDOWN);
    data.putLong("domain", Math.max(0, domain.getLong("ends") - now));
    data.putBoolean("inDomain", active(domain, now) && contains(domain, owner));
    data.getCompound("cooldowns")
        .putLong("cd_domain_talisman", Math.max(0, ThidaBonds.data(owner).getLong(CD) - now));
    if (unlocked && active(domain, now) && domain.hasUUID("activation")) {
      data.putUUID("ruenActivation", domain.getUUID("activation"));
      data.putInt("ruenInside", domain.getCompound(Side.INSIDE.key).size());
      data.putInt("ruenOutside", domain.getCompound(Side.OUTSIDE.key).size());
      var anchor = new CompoundTag();
      for (String axis : List.of("x", "y", "z")) anchor.putDouble(axis, domain.getDouble(axis));
      anchor.putString("dimension", domain.getString("dimension"));
      anchor.putDouble("reach", reach(owner));
      data.put("ruenAnchor", anchor);
    }
  }

  private static void cleanse(ServerPlayer recipient) {
    ThidaSpells.cleanseNilakan(recipient);
    ThidaEvents.cleanse(recipient);
  }

  private static void tell(ServerPlayer owner, String key) {
    ThidaEvents.tell(owner, ThidaMessages.text("message.thida.ruen." + key));
  }
}
