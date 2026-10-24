package dev.thida;

import com.google.gson.JsonObject;

/** Thida-owned mapping format; resource IDs are opaque to the game rules. */
public record ThidaEffectBinding(
    String id,
    String event,
    String power,
    String spell,
    String component,
    String resource,
    ThidaEffectEvent.Anchor anchor,
    Vector offset,
    Vector rotationDegrees,
    double scale,
    boolean loop,
    int lifetimeTicks,
    String sound,
    float volume,
    float pitch) {
  public ThidaEffectBinding withoutStartupSound() {
    return new ThidaEffectBinding(
        id,
        event,
        power,
        spell,
        component,
        resource,
        anchor,
        offset,
        rotationDegrees,
        scale,
        loop,
        lifetimeTicks,
        "",
        volume,
        pitch);
  }

  public record Vector(double x, double y, double z) {
    public Vector {
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
        throw new IllegalArgumentException("Invalid vector");
    }
  }

  public boolean matches(ThidaEffectEvent message) {
    return matches(event, message.event())
        && matches(power, message.power())
        && matches(spell, message.spell())
        && matches(component, message.component());
  }

  private static boolean matches(String filter, String value) {
    return filter.equals("*") || filter.equals(value);
  }

  public static ThidaEffectBinding read(String id, JsonObject json) {
    double scale = json.has("scale") ? json.get("scale").getAsDouble() : 1;
    int duration = json.has("lifetimeTicks") ? json.get("lifetimeTicks").getAsInt() : 0;
    float volume = json.has("volume") ? json.get("volume").getAsFloat() : 1;
    float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 1;
    if (!Double.isFinite(scale)
        || scale <= 0
        || duration < 0
        || !Float.isFinite(volume)
        || volume < 0
        || !Float.isFinite(pitch)
        || pitch <= 0) throw new IllegalArgumentException("Invalid presentation values");
    String resource = text(json, "resource", "");
    if (net.minecraft.resources.ResourceLocation.tryParse(resource) == null)
      throw new IllegalArgumentException("Invalid effect resource ID");
    String sound = text(json, "sound", "");
    if (!sound.isEmpty() && net.minecraft.resources.ResourceLocation.tryParse(sound) == null)
      throw new IllegalArgumentException("Invalid sound ID");
    return new ThidaEffectBinding(
        id,
        text(json, "event", "*"),
        text(json, "power", "*"),
        text(json, "spell", "*"),
        text(json, "component", "*"),
        resource,
        json.has("anchor")
            ? ThidaEffectEvent.Anchor.valueOf(json.get("anchor").getAsString())
            : null,
        vector(json, "offset"),
        vector(json, "rotationDegrees"),
        scale,
        json.has("loop") && json.get("loop").getAsBoolean(),
        duration,
        sound,
        volume,
        pitch);
  }

  private static String text(JsonObject json, String key, String fallback) {
    return json.has(key) ? json.get(key).getAsString() : fallback;
  }

  private static Vector vector(JsonObject json, String key) {
    if (!json.has(key)) return new Vector(0, 0, 0);
    var array = json.getAsJsonArray(key);
    if (array.size() != 3) throw new IllegalArgumentException("Expected three coordinates");
    return new Vector(
        array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
  }
}
