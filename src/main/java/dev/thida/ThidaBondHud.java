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
  private static final int WIDTH = 208;

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

  static ThidaHudLayout.Bounds bondBounds(boolean preview) {
    int count = preview ? Math.max(1, rows().size()) : rows().size();
    return ThidaHudLayout.fit(x(), y(), WIDTH, 21 + count * 34, ThidaHudConfig.BOND_SCALE.get());
  }

  static void drawBonds(GuiGraphics g, boolean preview) {
    var mc = Minecraft.getInstance();
    var rows = rows();
    if (rows.isEmpty() && preview) {
      var n = new CompoundTag();
      n.putString("id", mc.player.getUUID().toString());
      n.putString("name", ThidaMessages.text("ui.thida.bondhud.drawbonds.1"));
      n.putFloat("hp", 100);
      n.putFloat("maxHp", 100);
      n.putBoolean("available", true);
      n.putString("status", ThidaMessages.text("ui.thida.bondhud.drawbonds.2"));
      rows.add(new Row("narotra_doll", n));
    }
    if (rows.isEmpty()) return;
    var area = bondBounds(preview);
    g.pose().pushPose();
    g.pose().translate(area.x(), area.y(), 0);
    g.pose().scale(area.scale(), area.scale(), 1);
    int x = 0, y = 0, h = 21 + rows.size() * 34;
    ThidaGui.hudPanel(g, x, y, WIDTH, h, 0xFF91DAC2);
    ThidaGui.text(
        g,
        ThidaMessages.text("ui.thida.bondhud.drawbonds.4"),
        x + 7,
        y + 6,
        WIDTH - 14,
        ThidaGui.TEXT);
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      var n = row.data();
      int ry = y + 19 + i * 34;
      String icon =
          row.kind().startsWith("narotra")
              ? "narotra"
              : row.kind().startsWith("nilakan") ? "nilakan" : row.kind();
      ThidaGui.icon(g, icon, x + 5, ry + 5, 1);
      try {
        PlayerFaceRenderer.draw(g, skin(UUID.fromString(n.getString("id"))), x + 25, ry + 2, 22);
      } catch (IllegalArgumentException ignored) {
      }
      ThidaGui.text(g, n.getString("name"), x + 53, ry + 2, WIDTH - 61, ThidaGui.TEXT);
      String hp =
          String.format(
              java.util.Locale.ROOT, "%.0f / %.0f", n.getFloat("hp"), n.getFloat("maxHp"));
      String kind = ThidaMessages.text("hud.thida.kind." + icon);
      ThidaGui.text(
          g,
          kind + " · " + (n.getBoolean("available") ? hp : n.getString("status")),
          x + 53,
          ry + 15,
          WIDTH - 61,
          n.getBoolean("available") ? 0xFF91DAC2 : ThidaGui.GOLD);
      float health = n.getFloat("maxHp") > 0 ? n.getFloat("hp") / n.getFloat("maxHp") : 0;
      ThidaGui.bar(
          g,
          x + 53,
          ry + 28,
          WIDTH - 61,
          health,
          n.getBoolean("available") ? 0xFF91DAC2 : ThidaGui.GOLD);
    }
    g.pose().popPose();
  }

  static ThidaHudLayout.Bounds recipientBounds() {
    var window = Minecraft.getInstance().getWindow();
    int width = Math.min(286, window.getGuiScaledWidth() - 12);
    double scale = ThidaHudConfig.RECIPIENT_SCALE.get();
    int x =
        (window.getGuiScaledWidth() - (int) Math.ceil(width * scale)) / 2
            + ThidaHudConfig.RECIPIENT_X.get();
    return ThidaHudLayout.fit(
        x, window.getGuiScaledHeight() + ThidaHudConfig.RECIPIENT_Y.get(), width, 34, scale);
  }

  static void drawRecipient(GuiGraphics g, boolean preview) {
    var mc = Minecraft.getInstance();
    var data = ThidaHudState.snapshot().getCompound("recipient");
    if (preview) {
      data = new CompoundTag();
      data.putBoolean("active", true);
      data.putLong("ward", 3600);
      data.putLong("speed", 1000);
    }
    long ward = preview ? 3600 : ThidaHudState.remaining(data.getLong("ward")),
        speed = preview ? 1000 : ThidaHudState.remaining(data.getLong("speed"));
    long immune = ThidaHudState.remaining(data.getLong("invulnerable"));
    if (!data.getBoolean("active") && ward == 0 && speed == 0 && immune == 0) return;
    var area = recipientBounds();
    g.pose().pushPose();
    g.pose().translate(area.x(), area.y(), 0);
    g.pose().scale(area.scale(), area.scale(), 1);
    int w = Math.min(286, mc.getWindow().getGuiScaledWidth() - 12), h = 34;
    int x = 0, y = 0;
    ThidaGui.hudPanel(g, x, y, w, h, ThidaGui.GOLD);
    String status =
        preview
            ? ThidaMessages.text("ui.thida.bondhud.drawrecipient.1")
            : data.getBoolean("active")
                ? ThidaMessages.text("ui.thida.bondhud.drawrecipient.2")
                    + (data.getBoolean("used")
                        ? ThidaMessages.text("ui.thida.bondhud.drawrecipient.3")
                        : ThidaMessages.text("ui.thida.bondhud.drawrecipient.4"))
                : ThidaMessages.text("ui.thida.bondhud.drawrecipient.5");
    ThidaGui.text(g, status, x + 7, y + 5, w - 14, ThidaGui.TEXT);
    var parts = new ArrayList<String>();
    if (ward > 0)
      parts.add(ThidaMessages.text("ui.thida.bondhud.drawrecipient.6") + ThidaHudState.time(ward));
    if (speed > 0)
      parts.add(ThidaMessages.text("ui.thida.bondhud.drawrecipient.7") + ThidaHudState.time(speed));
    if (immune > 0)
      parts.add(
          ThidaMessages.text("ui.thida.bondhud.drawrecipient.8") + ThidaHudState.time(immune));
    String timers =
        parts.isEmpty()
            ? ThidaMessages.text("ui.thida.bondhud.drawrecipient.9")
            : String.join(" • ", parts);
    ThidaGui.text(g, timers, x + 7, y + 19, w - 14, ThidaGui.GOLD);
    g.pose().popPose();
  }

  /** Compatibility entry point for existing HUD commands. */
  public static final class PositionScreen extends ThidaHudEditor {
    public PositionScreen(Screen parent) {
      super(parent);
    }
  }
}
