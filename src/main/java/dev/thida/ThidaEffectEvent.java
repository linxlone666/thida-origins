package dev.thida;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Versioned, clientbound presentation data. No gameplay commands or player objects. */
public record ThidaEffectEvent(
    UUID epoch,
    long sequence,
    UUID instance,
    UUID activation,
    String event,
    String power,
    String spell,
    String component,
    Phase phase,
    UUID owner,
    UUID target,
    Point ownerPoint,
    Point targetPoint,
    Point point,
    Anchor anchor,
    String hand,
    long startedTick,
    long endsTick,
    long sentTick,
    String reason) {
  public enum Phase {
    ONE_SHOT,
    START,
    STOP,
    SYNC
  }

  public enum Anchor {
    USER,
    TARGET,
    HAND,
    WORLD
  }

  public record Point(String dimension, double x, double y, double z) {
    public Point {
      if (dimension == null
          || dimension.isBlank()
          || !Double.isFinite(x)
          || !Double.isFinite(y)
          || !Double.isFinite(z)) throw new IllegalArgumentException("Invalid effect point");
    }

    CompoundTag save() {
      var tag = new CompoundTag();
      tag.putString("dimension", dimension);
      tag.putDouble("x", x);
      tag.putDouble("y", y);
      tag.putDouble("z", z);
      return tag;
    }

    static Point read(CompoundTag tag) {
      return new Point(
          tag.getString("dimension"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }
  }

  public ThidaEffectEvent {
    if (epoch == null
        || instance == null
        || activation == null
        || point == null
        || event == null
        || event.isBlank()
        || power == null
        || power.isBlank()
        || sequence < 0
        || phase == null
        || anchor == null) throw new IllegalArgumentException("Invalid effect event");
  }

  public CompoundTag save() {
    var tag = new CompoundTag();
    tag.putInt("schema", 2);
    tag.putUUID("epoch", epoch);
    tag.putLong("sequence", sequence);
    tag.putUUID("instance", instance);
    tag.putUUID("activation", activation);
    tag.putString("event", event);
    tag.putString("power", power);
    tag.putString("spell", spell);
    tag.putString("component", component);
    tag.putString("phase", phase.name());
    tag.putString("anchor", anchor.name());
    if (owner != null) tag.putUUID("owner", owner);
    if (target != null) tag.putUUID("target", target);
    if (ownerPoint != null) tag.put("ownerPoint", ownerPoint.save());
    if (targetPoint != null) tag.put("targetPoint", targetPoint.save());
    tag.put("point", point.save());
    tag.putString("hand", hand);
    tag.putLong("startedTick", startedTick);
    tag.putLong("endsTick", endsTick);
    tag.putLong("sentTick", sentTick);
    tag.putString("reason", reason);
    return tag;
  }

  public static ThidaEffectEvent read(CompoundTag tag) {
    if (tag.getInt("schema") != 2) throw new IllegalArgumentException("Unsupported effect schema");
    return new ThidaEffectEvent(
        tag.getUUID("epoch"),
        tag.getLong("sequence"),
        tag.getUUID("instance"),
        tag.getUUID("activation"),
        tag.getString("event"),
        tag.getString("power"),
        tag.getString("spell"),
        tag.getString("component"),
        Phase.valueOf(tag.getString("phase")),
        tag.hasUUID("owner") ? tag.getUUID("owner") : null,
        tag.hasUUID("target") ? tag.getUUID("target") : null,
        tag.contains("ownerPoint") ? Point.read(tag.getCompound("ownerPoint")) : null,
        tag.contains("targetPoint") ? Point.read(tag.getCompound("targetPoint")) : null,
        Point.read(tag.getCompound("point")),
        Anchor.valueOf(tag.getString("anchor")),
        tag.getString("hand"),
        tag.getLong("startedTick"),
        tag.getLong("endsTick"),
        tag.getLong("sentTick"),
        tag.getString("reason"));
  }

  public ThidaEffectEvent delivery(
      long revision, Phase deliveryPhase, long now, String type, String endReason) {
    return new ThidaEffectEvent(
        epoch,
        revision,
        instance,
        activation,
        type,
        power,
        spell,
        component,
        deliveryPhase,
        owner,
        target,
        ownerPoint,
        targetPoint,
        point,
        anchor,
        hand,
        startedTick,
        endsTick,
        now,
        endReason);
  }
}
