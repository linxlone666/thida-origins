package dev.thida;

/** Presentation does not restart or extend the server's actual cooldown. */
public final class HudTimeline {
  public enum Phase {
    ACTIVE,
    COOLDOWN,
    HIDDEN
  }

  public static Phase phase(long active, long cooldown, boolean sustained) {
    if (sustained || active > 0) return Phase.ACTIVE;
    return cooldown > 0 ? Phase.COOLDOWN : Phase.HIDDEN;
  }

  public static long shownTicks(long active, long cooldown) {
    return Math.max(0, active > 0 ? active : cooldown);
  }

  private HudTimeline() {}
}
