package dev.thida;

import net.minecraft.nbt.CompoundTag;

/** Clientbound display data only. No client classes are referenced by the dedicated server. */
public final class ThidaHudState {
  private static CompoundTag latest = new CompoundTag();
  private static int ageTicks;
  private static long revision;

  public static void accept(CompoundTag snapshot) {
    latest = snapshot == null ? new CompoundTag() : snapshot.copy();
    ageTicks = 0;
    revision++;
  }

  public static CompoundTag snapshot() {
    return latest.copy();
  }

  public static long revision() {
    return revision;
  }

  public static void tick() {
    ageTicks++;
  }

  public static long remaining(long ticks) {
    return Math.max(0, ticks - ageTicks);
  }

  public static String time(long ticks) {
    long seconds = (Math.max(0, ticks) + 19) / 20;
    return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
  }

  private ThidaHudState() {}
}
