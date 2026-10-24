package dev.thida;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Client display cache, isolated from client-only classes for dedicated servers. */
public final class ThidaDomainState {
  public record View(CompoundTag data, int received) {}

  private static final LinkedHashMap<UUID, View> DOMAINS = new LinkedHashMap<>();
  private static int ticks;

  public static void accept(CompoundTag data) {
    if (data == null || !data.hasUUID("owner")) return;
    UUID owner = data.getUUID("owner");
    if (!data.getBoolean("active")) {
      DOMAINS.remove(owner);
      return;
    }
    DOMAINS.put(owner, new View(data.copy(), ticks));
    while (DOMAINS.size() > 32) DOMAINS.remove(DOMAINS.keySet().iterator().next());
  }

  public static void tick() {
    ticks++;
    DOMAINS.values().removeIf(v -> ticks - v.received() > 30);
  }

  public static List<View> views() {
    return List.copyOf(DOMAINS.values());
  }

  public static float age(View view, float partial) {
    return view.data().getLong("age") + ticks - view.received() + partial;
  }

  public static void clear() {
    DOMAINS.clear();
    ticks = 0;
  }

  private ThidaDomainState() {}
}
