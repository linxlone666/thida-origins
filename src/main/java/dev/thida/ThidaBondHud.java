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
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Faces resolve through the actual client player/PlayerInfo skin (including FragmentSkin). */
@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class ThidaBondHud {
  private static final Map<UUID, PlayerSkin> SKINS = new HashMap<>();
  private static final int WIDTH = 236;
  private static int dragX = -1, dragY = -1;
  private static int recipientDragY = -1;

  private record Row(String kind, CompoundTag data) {}

  private ThidaBondHud() {}

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    SKINS.clear();
    dragX = dragY = -1;
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
    return dragX < 0 ? ThidaHudConfig.BOND_X.get() : dragX;
  }

  private static int y() {
    return dragY < 0 ? ThidaHudConfig.BOND_Y.get() : dragY;
  }

  public static int avoidOverlap(int x, int y, int width, int height) {
    int count = rows().size();
    if (count == 0) return y;
    int bx = x(), by = y(), bh = 22 + count * 31;
    return x < bx + WIDTH && x + width > bx && y < by + bh && y + height > by ? by + bh + 8 : y;
  }

  @SubscribeEvent
  public static void render(RenderGuiEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.player == null
        || mc.options.hideGui
        || mc.screen != null && !(mc.screen instanceof PositionScreen)) return;
    if (mc.screen instanceof PositionScreen) return;
    drawBonds(event.getGuiGraphics(), false);
    drawRecipient(event.getGuiGraphics(), false);
  }

  private static void drawBonds(GuiGraphics g, boolean preview) {
    var mc = Minecraft.getInstance();
    var rows = rows();
    if (rows.isEmpty() && preview) {
      var n = new CompoundTag();
      n.putString("id", mc.player.getUUID().toString());
      n.putString("name", ThidaMessages.text("ui.thida.bondhud.drawbonds.1"));
      n.putFloat("hp", 100);
      n.putFloat("maxHp", 100);
      n.putString("status", ThidaMessages.text("ui.thida.bondhud.drawbonds.2"));
      rows.add(new Row("narotra_doll", n));
    }
    if (rows.isEmpty()) return;
    int x = Math.min(x(), Math.max(0, mc.getWindow().getGuiScaledWidth() - WIDTH));
    int h = 22 + rows.size() * 31,
        y = Math.min(y(), Math.max(0, mc.getWindow().getGuiScaledHeight() - h));
    ThidaGui.frame(g, x, y, WIDTH, h, 0xFF91DAC2, true);
    ThidaGui.text(
        g,
        preview
            ? ThidaMessages.text("ui.thida.bondhud.drawbonds.3")
            : ThidaMessages.text("ui.thida.bondhud.drawbonds.4"),
        x + 7,
        y + 6,
        WIDTH - 14,
        ThidaGui.TEXT);
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      var n = row.data();
      int ry = y + 20 + i * 31;
      String icon =
          row.kind().startsWith("narotra")
              ? "narotra"
              : row.kind().startsWith("nilakan") ? "nilakan" : row.kind();
      ThidaGui.icon(g, icon, x + 5, ry + 5, 1);
      try {
        PlayerFaceRenderer.draw(g, skin(UUID.fromString(n.getString("id"))), x + 25, ry + 2, 22);
      } catch (IllegalArgumentException ignored) {
      }
      ThidaGui.text(g, n.getString("name"), x + 53, ry + 2, 174, ThidaGui.TEXT);
      String hp =
          String.format(
                  java.util.Locale.ROOT, "%.1f / %.1f HP", n.getFloat("hp"), n.getFloat("maxHp"))
              .replace(" HP", ThidaMessages.text("ui.thida.bondhud.drawbonds.5"));
      ThidaGui.text(
          g,
          hp + " • " + n.getString("status"),
          x + 53,
          ry + 15,
          174,
          n.getBoolean("available") ? 0xFF91DAC2 : ThidaGui.GOLD);
    }
  }

  private static int recipientTop() {
    int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
    int requested =
        recipientDragY >= 0 ? recipientDragY : height + ThidaHudConfig.RECIPIENT_Y.get();
    return Math.max(6, Math.min(height - 76, Math.max(height / 2 + 12, requested)));
  }

  private static void drawRecipient(GuiGraphics g, boolean preview) {
    var mc = Minecraft.getInstance();
    var data = ThidaHudState.snapshot().getCompound("recipient");
    if (preview) {
      data = new CompoundTag();
      data.putBoolean("active", true);
      data.putLong("ward", 3600);
      data.putLong("speed", 1000);
    }
    long ward = ThidaHudState.remaining(data.getLong("ward")),
        speed = ThidaHudState.remaining(data.getLong("speed"));
    long immune = ThidaHudState.remaining(data.getLong("invulnerable"));
    if (!data.getBoolean("active") && ward == 0 && speed == 0 && immune == 0) return;
    int w = Math.min(296, mc.getWindow().getGuiScaledWidth() - 12), h = 32;
    int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
    int y = recipientTop();
    ThidaGui.frame(g, x, y, w, h, 0xFFF0E9DA, true);
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
  }

  public static final class PositionScreen extends Screen {
    private final Screen parent;
    private boolean dragging, draggingRecipient;
    private double offsetX, offsetY;

    public PositionScreen(Screen parent) {
      super(Component.literal(ThidaMessages.text("ui.thida.bondhud.drawrecipient.10")));
      this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      renderBackground(g, mouseX, mouseY, partial);
      drawBonds(g, true);
      drawRecipient(g, true);
      ThidaGui.text(
          g,
          ThidaMessages.text("ui.thida.bondhud.render.1"),
          8,
          height - 18,
          width - 16,
          ThidaGui.TEXT);
      super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
      if (button == 1) {
        onClose();
        return true;
      }
      int recipientWidth = Math.min(296, width - 12), recipientX = (width - recipientWidth) / 2;
      if (button == 0
          && mx >= recipientX
          && mx <= recipientX + recipientWidth
          && my >= recipientTop()
          && my <= recipientTop() + 32) {
        draggingRecipient = true;
        offsetY = my - recipientTop();
        return true;
      }
      if (button == 0
          && mx >= x()
          && mx <= x() + WIDTH
          && my >= y()
          && my <= y() + 22 + Math.max(1, rows().size()) * 31) {
        dragging = true;
        offsetX = mx - x();
        offsetY = my - y();
        return true;
      }
      return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (draggingRecipient) {
        recipientDragY = (int) Math.max(6, Math.min(height - 76, my - offsetY));
        return true;
      }
      if (dragging) {
        dragX = (int) Math.max(0, Math.min(width - Math.min(WIDTH, width), mx - offsetX));
        dragY = (int) Math.max(0, Math.min(height - 53, my - offsetY));
      }
      return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
      dragging = draggingRecipient = false;
      return true;
    }

    @Override
    public void onClose() {
      if (dragX >= 0) {
        ThidaHudConfig.BOND_X.set(dragX);
        ThidaHudConfig.BOND_Y.set(dragY);
        ThidaHudConfig.BOND_X.save();
      }
      if (recipientDragY >= 0) {
        ThidaHudConfig.RECIPIENT_Y.set(recipientTop() - height);
        ThidaHudConfig.RECIPIENT_Y.save();
      }
      recipientDragY = -1;
      dragX = dragY = -1;
      minecraft.setScreen(parent);
    }
  }
}
