package dev.thida;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class ThidaHud {
  private static final int BADGE_HEIGHT = 35;
  private static final String[] LINKS = {"nilakan_doll", "narotra_doll", "parasite", "heart"};
  private static final String[] NAMES = {
    ThidaMessages.text("ui.thida.client.choices.2"),
    ThidaMessages.text("ui.thida.client.choices.1"),
    ThidaMessages.text("ui.thida.debug.label.1"),
    ThidaMessages.text("ui.thida.hud.labels.1")
  };
  private static CompoundTag previous;
  private static long revision = -1;
  private static String notice = "";
  private static int noticeTicks;

  public static void preview() {
    if (Minecraft.getInstance().level != null)
      Minecraft.getInstance().setScreen(new ThidaHudEditor(null));
  }

  public static String cooldown(String key) {
    long ticks =
        ThidaHudState.remaining(ThidaHudState.snapshot().getCompound("cooldowns").getLong(key));
    return ticks > 0
        ? ThidaHudState.time(ticks)
        : ThidaMessages.text("ui.thida.bondhud.drawrecipient.4");
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    ThidaHudState.accept(new CompoundTag());
    previous = null;
    revision = -1;
    noticeTicks = 0;
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null || mc.isPaused()) return;
    ThidaHudState.tick();
    if (noticeTicks > 0) noticeTicks--;
    if (revision == ThidaHudState.revision()) return;
    revision = ThidaHudState.revision();
    CompoundTag next = ThidaHudState.snapshot();
    if (previous != null && previous.getString("player").equals(next.getString("player")))
      detectChanges(previous, next);
    previous = next;
  }

  private static void detectChanges(CompoundTag old, CompoundTag next) {
    // Emit at most one cue per update, prioritizing protection and broken links.
    if (old.getLong("invulnerable") == 0 && next.getLong("invulnerable") > 0) {
      cue(ThidaMessages.text("ui.thida.hud.detectchanges.1"), SoundEvents.TOTEM_USE, 1);
      return;
    }
    for (int i = 0; i < LINKS.length; i++) {
      String before = old.getCompound(LINKS[i]).getString("id");
      String after = next.getCompound(LINKS[i]).getString("id");
      if (!before.isEmpty() && after.isEmpty()) {
        cue(
            NAMES[i] + ThidaMessages.text("ui.thida.hud.detectchanges.2"),
            SoundEvents.ITEM_BREAK,
            0.8F);
        return;
      }
      if (!after.isEmpty() && !before.equals(after)) {
        cue(
            NAMES[i] + " → " + next.getCompound(LINKS[i]).getString("name"),
            SoundEvents.EXPERIENCE_ORB_PICKUP,
            1.2F);
        return;
      }
    }
    if (old.getLong("domain") == 0 && next.getLong("domain") > 0) {
      cue(ThidaMessages.text("ui.thida.hud.detectchanges.3"), SoundEvents.BEACON_ACTIVATE, 1);
      return;
    }
    if (old.getLong("domain") > 0 && next.getLong("domain") == 0) {
      cue(ThidaMessages.text("ui.thida.hud.detectchanges.4"), SoundEvents.BEACON_DEACTIVATE, 1);
      return;
    }
    if (!old.getBoolean("armed") && next.getBoolean("armed")) {
      cue(
          ThidaMessages.text("ui.thida.hud.detectchanges.5"),
          SoundEvents.EXPERIENCE_ORB_PICKUP,
          0.8F);
      return;
    }
    if (!old.getString("guardian").equals(next.getString("guardian"))) {
      cue(
          next.getString("guardian").isEmpty()
              ? ThidaMessages.text("ui.thida.hud.detectchanges.6")
              : ThidaMessages.text("ui.thida.hud.detectchanges.7") + next.getString("guardian"),
          SoundEvents.EXPERIENCE_ORB_PICKUP,
          1);
      return;
    }
    CompoundTag cds = next.getCompound("cooldowns"), prior = old.getCompound("cooldowns");
    for (String key : prior.getAllKeys())
      if (next.getBoolean("thida") && prior.getLong(key) > 0 && cds.getLong(key) == 0) {
        cue(
            label(key) + ThidaMessages.text("ui.thida.hud.detectchanges.8"),
            SoundEvents.PLAYER_LEVELUP,
            1.4F);
        return;
      }
  }

  private static String label(String key) {
    for (int i = 0; i < LINKS.length; i++)
      if (key.contains(LINKS[i])) {
        if (key.startsWith("cd_effect_"))
          return NAMES[i]
              + ThidaMessages.text("ui.thida.debug.label.5")
              + (key.charAt(key.length() - 1) - '0' + 1);
        return NAMES[i];
      }
    return key.contains("domain")
        ? ThidaMessages.text("ui.thida.hud.label.1")
        : ThidaMessages.text("ui.thida.debug.label.4");
  }

  private static void cue(String text, SoundEvent sound, float pitch) {
    notice = text;
    noticeTicks = 80;
    if (ThidaHudConfig.SOUNDS.get() && Minecraft.getInstance().player != null)
      Minecraft.getInstance()
          .player
          .playSound(sound, ThidaHudConfig.VOLUME.get().floatValue(), pitch);
  }

  private record Badge(
      String icon, String key, HudTimeline.Phase phase, long ticks, int total, boolean sustained) {}

  private static void add(
      List<Badge> badges,
      String icon,
      String key,
      long active,
      long cd,
      int activeTotal,
      int cdTotal,
      boolean sustained) {
    HudTimeline.Phase phase = HudTimeline.phase(active, cd, sustained);
    if (phase != HudTimeline.Phase.HIDDEN)
      badges.add(
          new Badge(
              icon,
              key,
              phase,
              HudTimeline.shownTicks(active, cd),
              phase == HudTimeline.Phase.ACTIVE ? activeTotal : cdTotal,
              sustained));
  }

  @SubscribeEvent
  public static void render(RenderGuiEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null
        || mc.options.hideGui
        || mc.getDebugOverlay().showDebugScreen()
        || mc.screen != null
        || !ThidaHudConfig.VISIBLE.get()) return;
    draw(event.getGuiGraphics(), false);
  }

  static ThidaHudLayout.Bounds previewBounds() {
    return bounds(sampleBadges());
  }

  static boolean previewContains(double mouseX, double mouseY) {
    var area = previewBounds();
    double x = (mouseX - area.x()) / area.scale(), y = (mouseY - area.y()) / area.scale();
    for (var cell : flow(sampleBadges()).cells())
      if (x >= cell.x()
          && x < cell.x() + cell.width()
          && y >= cell.y()
          && y < cell.y() + BADGE_HEIGHT) return true;
    return false;
  }

  static void previewOutline(net.minecraft.client.gui.GuiGraphics g) {
    var area = previewBounds();
    g.pose().pushPose();
    g.pose().translate(area.x(), area.y(), 0);
    g.pose().scale(area.scale(), area.scale(), 1);
    for (var cell : flow(sampleBadges()).cells())
      g.renderOutline(cell.x(), cell.y(), cell.width(), BADGE_HEIGHT, ThidaGui.GOLD);
    g.pose().popPose();
  }

  private static List<Badge> sampleBadges() {
    var result = new ArrayList<Badge>();
    add(result, "speed", "", 500, 0, 400, 0, false);
    add(result, "domain", "", 0, 300, 0, 200, false);
    add(result, "heart", "", 0, 0, 0, 0, true);
    return result;
  }

  private static HudPacking.Flow flow(List<Badge> badges) {
    var font = Minecraft.getInstance().font;
    var widths = new ArrayList<Integer>();
    for (var badge : badges) widths.add(Math.max(34, ThidaGui.width("888:88") + 6));
    return HudPacking.flow(
        widths,
        ThidaHudLayout.available(ThidaHudConfig.EFFECT_SCALE.get(), 248),
        0,
        4,
        BADGE_HEIGHT,
        ThidaHudConfig.ICONS_PER_ROW.get());
  }

  private static ThidaHudLayout.Bounds bounds(List<Badge> badges) {
    var flow = flow(badges);
    var area =
        ThidaHudLayout.fit(
            ThidaHudConfig.X.get(),
            ThidaHudConfig.Y.get(),
            Math.max(1, flow.width()),
            Math.max(1, flow.height()),
            ThidaHudConfig.EFFECT_SCALE.get());
    int y = ThidaBondHud.avoidOverlap(area.x(), area.y(), area.width(), area.height());
    var recipient = ThidaBondHud.recipientBounds();
    boolean touchesRecipient =
        area.x() < recipient.x() + recipient.width()
            && area.x() + area.width() > recipient.x()
            && y < recipient.y() + recipient.height()
            && y + area.height() > recipient.y();
    if (touchesRecipient
        || y != area.y()
            && y + area.height() > Minecraft.getInstance().getWindow().getGuiScaledHeight() - 74) {
      var window = Minecraft.getInstance().getWindow();
      var bond = ThidaBondHud.bondBounds(Minecraft.getInstance().screen instanceof ThidaHudEditor);
      double sideScale =
          Math.min(
              ThidaHudConfig.EFFECT_SCALE.get(),
              Math.max(1, window.getGuiScaledWidth() - bond.x() - bond.width() - 12)
                  / (double) Math.max(1, flow.width()));
      return ThidaHudLayout.fit(
          window.getGuiScaledWidth() - (int) Math.ceil(flow.width() * sideScale) - 4,
          8,
          Math.max(1, flow.width()),
          Math.max(1, flow.height()),
          sideScale);
    }
    return ThidaHudLayout.fit(
        area.x(),
        y,
        Math.max(1, flow.width()),
        Math.max(1, flow.height()),
        ThidaHudConfig.EFFECT_SCALE.get());
  }

  static void draw(net.minecraft.client.gui.GuiGraphics g, boolean preview) {
    Minecraft mc = Minecraft.getInstance();
    CompoundTag data = ThidaHudState.snapshot();
    if (!preview && !data.getString("player").equals(mc.player.getUUID().toString())) return;
    List<Badge> badges = new ArrayList<>();
    if (data.getBoolean("thida")) {
      String[][] effects = {{"weak", "bleed", "vulnerable"}, {"shield", "cleanse", "speed"}};
      String[] dolls = {"nilakan_doll", "narotra_doll"};
      for (int i = 0; i < 2; i++) {
        for (int mode = 0; mode < 3; mode++) {
          String key = dolls[i] + "_" + mode;
          add(
              badges,
              effects[i][mode],
              Integer.toString(mode + 1),
              ThidaHudState.remaining(data.getCompound("activeEffects").getLong(key)),
              ThidaGui.remaining(data, "cd_effect_" + key),
              data.getInt("effectDuration"),
              data.getInt("effectTotal"),
              false);
        }
        // Doll suspension and each spell cooldown have separate timelines.
        add(
            badges,
            i == 0 ? "nilakan" : "narotra",
            "",
            0,
            ThidaGui.remaining(data, "cd_" + dolls[i]),
            0,
            data.getInt("releaseTotal"),
            false);
      }
      add(
          badges,
          "parasite",
          "",
          0,
          ThidaGui.remaining(data, "cd_parasite_doll"),
          0,
          data.getInt("releaseTotal"),
          data.getBoolean("armed") || !data.getCompound("parasite").getString("id").isEmpty());
      add(
          badges,
          "heart",
          "",
          0,
          ThidaGui.remaining(data, "cd_heart_doll"),
          0,
          data.getInt("releaseTotal"),
          !data.getCompound("heart").getString("id").isEmpty());
      add(
          badges,
          "domain",
          "",
          ThidaHudState.remaining(data.getLong("domain")),
          ThidaGui.remaining(data, "cd_domain_talisman"),
          data.getInt("domainTotal"),
          data.getInt("domainCooldownTotal"),
          false);
      if (data.getInt("gaze") > 0)
        add(badges, "narotra", "", data.getInt("gaze"), 0, data.getInt("gazeRequired"), 0, false);
    }
    if (!data.getString("guardian").isEmpty()) add(badges, "heart", "", 0, 0, 0, 0, true);
    add(
        badges,
        "shield",
        "!",
        ThidaHudState.remaining(data.getLong("invulnerable")),
        0,
        100,
        0,
        false);
    if (preview) {
      // Local placement sample only: no real effect, cooldown or network state is changed.
      badges.clear();
      badges.addAll(sampleBadges());
    }
    if (badges.isEmpty()) return;
    var flow = flow(badges);
    var area = bounds(badges);
    int x = 0, y = 0;
    g.pose().pushPose();
    g.pose().translate(area.x(), area.y(), 0);
    g.pose().scale(area.scale(), area.scale(), 1);
    for (int i = 0; i < badges.size(); i++) {
      Badge badge = badges.get(i);
      var cell = flow.cells().get(i);
      int bx = cell.x(), by = cell.y(), width = cell.width();
      boolean active = badge.phase() == HudTimeline.Phase.ACTIVE;
      int color = active ? 0xFF91DAC2 : ThidaGui.GOLD;
      ThidaGui.hudPanel(g, bx, by, width, BADGE_HEIGHT, color);
      int iconX = bx + (width - 16) / 2;
      ThidaGui.icon(g, badge.icon(), iconX, by + 3, 1);
      if (!active) g.fill(iconX, by + 3, iconX + 16, by + 19, 0x88211D2D);
      String timer = badge.sustained() ? "∞" : ThidaHudState.time(badge.ticks());
      ThidaGui.text(
          g,
          timer,
          bx + Math.max(2, (width - ThidaGui.width(timer)) / 2),
          by + 22,
          width - 4,
          color);
      float progress =
          badge.sustained()
              ? 1
              : active
                  ? badge.ticks() / (float) Math.max(1, badge.total())
                  : ThidaGui.progress(badge.ticks(), badge.total());
      ThidaGui.bar(g, bx + 3, by + 32, width - 6, progress, color);
    }
    g.pose().popPose();
  }

  private ThidaHud() {}
}
