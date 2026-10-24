package dev.thida;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** UUID based world state, including offline players. Never store permissions in an item. */
public final class ThidaBondData extends SavedData {
  private final CompoundTag players;
  private static final Factory<ThidaBondData> FACTORY =
      new Factory<>(() -> new ThidaBondData(new CompoundTag()), ThidaBondData::load);

  static ThidaBondData load(CompoundTag tag, HolderLookup.Provider lookup) {
    return new ThidaBondData(tag.getCompound("players").copy());
  }

  private ThidaBondData(CompoundTag players) {
    this.players = players;
  }

  public static ThidaBondData get(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "thida_bonds_v7");
  }

  public CompoundTag player(UUID id) {
    String key = id.toString();
    if (!players.contains(key)) {
      players.put(key, new CompoundTag());
      setDirty();
    }
    return players.getCompound(key);
  }

  public CompoundTag player(ServerPlayer p) {
    return player(p.getUUID());
  }

  public java.util.Set<String> ids() {
    return java.util.Set.copyOf(players.getAllKeys());
  }

  public static CompoundTag child(CompoundTag tag, String key) {
    if (!tag.contains(key)) tag.put(key, new CompoundTag());
    return tag.getCompound(key);
  }

  public CompoundTag links(UUID owner) {
    return child(player(owner), "links");
  }

  public CompoundTag link(UUID owner, String kind) {
    return links(owner).getCompound(kind);
  }

  public boolean occupied(UUID target, UUID exceptOwner, String exceptKind) {
    for (String id : ids()) {
      UUID owner = UUID.fromString(id);
      for (String kind : links(owner).getAllKeys()) {
        if (owner.equals(exceptOwner) && kind.equals(exceptKind)) continue;
        var link = link(owner, kind);
        if (link.hasUUID("target") && link.getUUID("target").equals(target)) return true;
      }
    }
    return false;
  }

  public void remember(ServerPlayer p) {
    var n = player(p);
    n.putString("name", p.getScoreboardName());
    n.putFloat("hp", p.getHealth());
    n.putFloat("maxHp", p.getMaxHealth());
    n.putString("dimension", p.level().dimension().location().toString());
    setDirty();
  }

  public void history(UUID target) {
    player(target).putBoolean("everLinked", true);
    setDirty();
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    tag.put("players", players.copy());
    return tag;
  }
}
