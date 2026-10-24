package dev.thida;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Faces resolve through the actual client player/PlayerInfo skin (including FragmentSkin). */
@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class ThidaBondHud {
  private static final Map<UUID, PlayerSkin> SKINS = new HashMap<>();

  private record Row(String kind, CompoundTag data) {}

  private ThidaBondHud() {}

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    SKINS.clear();
  }

  private static PlayerSkin skin(UUID id) {
    var mc = Minecraft.getInstance();
    if (mc.level != null
        && mc.level.getPlayerByUUID(id)
            instanceof net.minecraft.client.player.AbstractClientPlayer player)
      SKINS.put(id, player.getSkin());
    else if (mc.getConnection() != null && mc.getConnection().getPlayerInfo(id) != null)
      SKINS.put(id, mc.getConnection().getPlayerInfo(id).getSkin());
    return SKINS.getOrDefault(id, DefaultPlayerSkin.get(id));
  }

  private static ArrayList<Row> rows() {
    var result = new ArrayList<Row>();
    var state = ThidaHudState.snapshot();
    if (!state.getBoolean("thida")) return result;
    for (String kind : new String[] {"narotra_doll", "nilakan_doll", "parasite", "heart"}) {
      var n = state.getCompound(kind);
      if (!n.getString("id").isEmpty()) result.add(new Row(kind, n));
    }
    return result;
  }

  private static int x() {
    return ThidaHudConfig.BOND_X.get();
  }

  private static int y() {
    return ThidaHudConfig.BOND_Y.get();
  }

  public static int avoidOverlap(int x, int y, int width, int height) {
    int count = rows().size();
    boolean editor = Minecraft.getInstance().screen instanceof ThidaHudEditor;
    if (count == 0 && !editor) return y;
    var area = bondBounds(editor);
    return x < area.x() + area.width()
            && x + width > area.x()
            && y < area.y() + area.height()
            && y + height > area.y()
        ? area.y() + area.height() + 6
        : y;
  }

  @SubscribeEvent
  public static void render(RenderGuiEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.player == null
        || mc.options.hideGui
        || mc.screen != null
        || !ThidaHudConfig.VISIBLE.get()) return;
    drawBonds(event.getGuiGraphics(), false);
    drawRecipient(event.getGuiGraphics(), false);
  }

  private static ArrayList<Row> displayedRows(boolean preview) {
    if (!preview) return rows();
    var result = new ArrayList<Row>();
    String[] kinds = {"narotra_doll", "nilakan_doll", "parasite", "heart"};
    for (int i = 0; i < kinds.length; i++) {
      var n = new CompoundTag();
      n.putString("id", Minecraft.getInstance().player.getUUID().toString());
      n.putString("name", i == 0 ? "Mai" : "LongTargetName_ผู้เล่นชื่อยาวมาก");
      n.putFloat("hp", i == 0 ? 18 : 1234);
      n.putFloat("maxHp", i == 0 ? 20 : 9999);
      n.putBoolean("available", i != 2);
      n.putString("status", ThidaMessages.text("hud.thida.offline"));
      result.add(new Row(kinds[i], n));
    }
    return result;
  }

  private static String icon(Row row) {
    return row.kind().replace("_doll", "");
  }

  static int numberWidth(CompoundTag n) {
    // Reserve by maximum HP magnitude; changing current HP cannot resize the card.
    int digits =
        Math.max(
            3,
            Math.min(
                7, Long.toString(Math.max(1, (long) Math.ceil(n.getFloat("maxHp")))).length()));
    int digitWidth = 0;
    for (int digit = 0; digit <= 9; digit++)
      digitWidth = Math.max(digitWidth, ThidaGui.width(Integer.toString(digit)));
    return digitWidth * digits * 2 + ThidaGui.width(" / ");
  }

  private static int bondWidth(boolean preview) {
    var font = Minecraft.getInstance().font;
    int wanted = ThidaGui.width(ThidaMessages.text("ui.thida.bondhud.drawbonds.4")) + 14;
    for (var row : displayedRows(preview)) {
      int name = ThidaGui.width(row.data().getString("name"));
      int detail =
          ThidaGui.width(ThidaMessages.text("hud.thida.kind." + icon(row)))
              + 8
              + (row.data().getBoolean("available")
                  ? numberWidth(row.data())
                  : ThidaGui.width(row.data().getString("status")));
      wanted = Math.max(wanted, 54 + Math.max(name, detail));
    }
    return HudPacking.limit(
        wanted, 158, ThidaHudLayout.available(ThidaHudConfig.BOND_SCALE.get(), 224));
  }

  static ThidaHudLayout.Bounds bondBounds(boolean preview) {
    int height = 21 + displayedRows(preview).size() * 34;
    var area =
        ThidaHudLayout.fit(x(), y(), bondWidth(preview), height, ThidaHudConfig.BOND_SCALE.get());
    if (!recipientStatuses(preview).isEmpty()) {
      var recipient = recipientBounds(preview);
      if (area.x() < recipient.x() + recipient.width()
          && area.x() + area.width() > recipient.x()
          && area.y() < recipient.y() + recipient.height()
          && area.y() + area.height() > recipient.y()
          && recipient.y() - area.y() > 60)
        area =
            ThidaHudLayout.fit(
                area.x(),
                area.y(),
                bondWidth(preview),
                height,
                Math.min(area.scale(), (recipient.y() - area.y() - 6) / (double) height));
    }
    return area;
  }

  static void drawBonds(GuiGraphics g, boolean preview) {
    var mc = Minecraft.getInstance();
    var rows = displayedRows(preview);
    if (rows.isEmpty()) return;
    int width = bondWidth(preview);
    var area = bondBounds(preview);
    g.pose().pushPose();
    g.pose().translate(area.x(), area.y(), 0);
    g.pose().scale(area.scale(), area.scale(), 1);
    ThidaGui.hudPanel(g, 0, 0, width, 21 + rows.size() * 34, 0xFF91DAC2);
    ThidaGui.text(
        g, ThidaMessages.text("ui.thida.bondhud.drawbonds.4"), 7, 6, width - 14, ThidaGui.TEXT);
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      var n = row.data();
      int ry = 19 + i * 34;
      ThidaGui.icon(g, icon(row), 5, ry + 7, 1);
      try {
        PlayerFaceRenderer.draw(g, skin(UUID.fromString(n.getString("id"))), 25, ry + 4, 18);
      } catch (IllegalArgumentException ignored) {
      }
      ThidaGui.text(g, n.getString("name"), 49, ry + 2, width - 56, ThidaGui.TEXT);
      String kind = ThidaMessages.text("hud.thida.kind." + icon(row));
      boolean available = n.getBoolean("available");
      String value =
          available
              ? String.format(
                  java.util.Locale.ROOT, "%.0f / %.0f", n.getFloat("hp"), n.getFloat("maxHp"))
              : n.getString("status");
      if (!available && value.isBlank()) value = ThidaMessages.text("hud.thida.paused");
      int valueWidth = Math.min(width - 56, available ? numberWidth(n) : ThidaGui.width(value));
      ThidaGui.text(g, kind, 49, ry + 15, Math.max(0, width - 62 - valueWidth), ThidaGui.MUTED);
      ThidaGui.fittedText(
          g,
          value,
          width - 7 - valueWidth,
          ry + 15,
          valueWidth,
          available ? 0xFF91DAC2 : ThidaGui.GOLD);
      if (available) {
        float health = n.getFloat("maxHp") > 0 ? n.getFloat("hp") / n.getFloat("maxHp") : 0;
        ThidaGui.bar(g, 49, ry + 28, Math.min(108, width - 56), health, 0xFF91DAC2);
      } else {
        // Never present cached offline HP as a current measurement.
        ThidaGui.text(
            g, ThidaMessages.text("hud.thida.hp_unknown"), 49, ry + 26, width - 56, ThidaGui.MUTED);
      }
    }
    g.pose().popPose();
  }

  private record Status(String icon, String label, long ticks) {}

  private static ArrayList<Status> recipientStatuses(boolean preview) {
    var data = ThidaHudState.snapshot().getCompound("recipient");
    var result = new ArrayList<Status>();
    if (preview || data.getBoolean("active"))
      result.add(
          new Status(
              "heart",
              ThidaMessages.text(
                  data.getBoolean("used") ? "hud.thida.save_used" : "hud.thida.save_ready"),
              0));
    String[] keys = {"ward", "speed", "invulnerable"};
    String[] icons = {"cleanse", "speed", "shield"};
    for (int i = 0; i < keys.length; i++) {
      long ticks =
          preview
              ? new long[] {3600, 1000, 100}[i]
              : ThidaHudState.remaining(data.getLong(keys[i]));
      if (ticks > 0)
        result.add(new Status(icons[i], ThidaMessages.text("hud.thida.status." + keys[i]), ticks));
    }
    return result;
  }

  private static HudPacking.Flow recipientFlow(boolean preview) {
    var widths = new ArrayList<Integer>();
    var font = Minecraft.getInstance().font;
    for (var status : recipientStatuses(preview))
      widths.add(
          26
              + ThidaGui.width(status.label())
              + (status.ticks() > 0 ? ThidaGui.width(" 888:88") : 0));
    return HudPacking.grid(
        widths, ThidaHudLayout.available(ThidaHudConfig.RECIPIENT_SCALE.get(), 248), 5, 4, 23, 2);
  }

  static ThidaHudLayout.Bounds recipientBounds() {
    return recipientBounds(Minecraft.getInstance().screen instanceof ThidaHudEditor);
  }

  private static ThidaHudLayout.Bounds recipientBounds(boolean preview) {
    var window = Minecraft.getInstance().getWindow();
    var flow = recipientFlow(preview);
    double scale = ThidaHudConfig.RECIPIENT_SCALE.get();
    int bottom =
        Math.min(
            window.getGuiScaledHeight() - 74,
            window.getGuiScaledHeight() + ThidaHudConfig.RECIPIENT_Y.get());
    int topLimit = window.getGuiScaledHeight() / 2 + 14;
    // At very small GUI sizes, fit between crosshair and the vanilla bars.
    // Higher custom positions use the lane above the crosshair instead.
    if (bottom > topLimit)
      scale = Math.min(scale, (bottom - topLimit) / (double) Math.max(1, flow.height()));
    else scale = Math.min(scale, Math.max(1, bottom - 4) / (double) Math.max(1, flow.height()));
    scale = Math.min(scale, (window.getGuiScaledWidth() - 12) / (double) Math.max(1, flow.width()));
    int width = (int) Math.ceil(flow.width() * scale);
    int height = (int) Math.ceil(flow.height() * scale);
    return ThidaHudLayout.fit(
        (window.getGuiScaledWidth() - width) / 2 + ThidaHudConfig.RECIPIENT_X.get(),
        bottom - height,
        Math.max(1, flow.width()),
        Math.max(1, flow.height()),
        scale);
  }

  static void drawRecipient(GuiGraphics g, boolean preview) {
    var statuses = recipientStatuses(preview);
    if (statuses.isEmpty()) return;
    var flow = recipientFlow(preview);
    var area = recipientBounds(preview);
    g.pose().pushPose();
    g.pose().translate(area.x(), area.y(), 0);
    g.pose().scale(area.scale(), area.scale(), 1);
    for (int i = 0; i < statuses.size(); i++) {
      var s = statuses.get(i);
      var c = flow.cells().get(i);
      ThidaGui.icon(g, s.icon(), c.x() + 2, c.y() + 3, 1);
      int timerWidth = s.ticks() > 0 ? ThidaGui.width("888:88") : 0;
      ThidaGui.text(
          g,
          s.label(),
          c.x() + 22,
          c.y() + 7,
          c.width() - 24 - (timerWidth > 0 ? timerWidth + 4 : 0),
          ThidaGui.TEXT);
      if (timerWidth > 0)
        ThidaGui.text(
            g,
            ThidaHudState.time(s.ticks()),
            c.x() + c.width() - timerWidth - 2,
            c.y() + 7,
            timerWidth,
            ThidaGui.TEXT);
    }
    g.pose().popPose();
  }

  /** Compatibility entry point for existing HUD commands. */
  public static final class PositionScreen extends ThidaHudEditor {
    public PositionScreen(Screen parent) {
      super(parent);
    }
  }
}
