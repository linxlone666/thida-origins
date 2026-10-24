package dev.thida;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in client-only samples in a disposable world. No gameplay requests are sent. */
@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class HudPreviewSmoke {
  private static long started, next;
  private static boolean opening;
  private static int frames, stage;
  private static int mode;

  @SubscribeEvent
  public static void frame(RenderFrameEvent.Post event) {
    if (!Boolean.getBoolean("thida.hudPreview")) return;
    var mc = Minecraft.getInstance();
    long now = System.nanoTime();
    if (started == 0) started = now;
    if (now - started > 240_000_000_000L) {
      mc.stop();
      return;
    }
    if (!opening && mc.getOverlay() == null && mc.screen instanceof TitleScreen && ++frames > 40) {
      opening = true;
      mc.createWorldOpenFlows().openWorld("hud-preview", () -> mc.setScreen(new TitleScreen()));
    }
    if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
    ThidaHudState.accept(sample(mc));
    if (now < next) return;
    next = now + 2_000_000_000L;
    try {
      switch (stage++) {
        case 0 -> {
          persistence(mc, false);
          ThidaHudConfig.BOND_X.set(8);
          ThidaHudConfig.BOND_Y.set(8);
          ThidaHudConfig.X.set(8);
          ThidaHudConfig.Y.set(8);
          ThidaHudConfig.RECIPIENT_X.set(0);
          ThidaHudConfig.RECIPIENT_Y.set(-80);
          scales(1);
          mc.getWindow().setWindowed(1280, 720);
          mc.options.guiScale().set(2);
          mc.resizeDisplay();
          mc.setScreen(null);
          var server = mc.getSingleplayerServer();
          if (server != null)
            server.execute(
                () -> {
                  var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                  if (player != null)
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                });
        }
        case 1 -> capture(mc, "after-many.png");
        case 2 -> mode = 1;
        case 3 -> capture(mc, "after-single.png");
        case 4 -> mode = 2;
        case 5 -> capture(mc, "after-leftovers.png");
        case 6 -> mode = 3;
        case 7 -> capture(mc, "after-save-used.png");
        case 8 -> {
          mode = 0;
          mc.setScreen(new ThidaHudEditor(null));
        }
        case 9 -> {
          capture(mc, "after-editor.png");
          scales(1.4);
          mc.options.guiScale().set(3);
          mc.resizeDisplay();
        }
        case 10 -> {
          capture(mc, "after-140-gui3.png");
          scales(.6);
          mc.options.guiScale().set(2);
          mc.resizeDisplay();
        }
        case 11 -> {
          capture(mc, "after-60-gui2.png");
          var b = ThidaBondHud.bondBounds(true);
          mc.screen.mouseClicked(b.x() + 8, b.y() + 8, 0);
          mc.screen.mouseDragged(b.x() + 38, b.y() + 28, 0, 30, 20);
          mc.screen.mouseReleased(0, 0, 0);
          mc.screen.mouseScrolled(0, 0, 0, 1);
          require(Math.abs(ThidaHudConfig.BOND_SCALE.get() - .65) < .001, "Selected scale");
          require(ThidaHudConfig.BOND_X.get() == b.x() + 30, "Drag position");
          require(!ThidaBondHud.bondBounds(true).contains(-1, -1), "Bounds exclude outside");
          var effect = ThidaHud.previewBounds();
          require(ThidaHud.previewContains(effect.x() + 8, effect.y() + 8), "Effect cell hit area");
          mc.screen.mouseClicked(effect.x() + 8, effect.y() + 8, 0);
          mc.screen.mouseScrolled(0, 0, 0, 1);
          mc.screen.mouseReleased(0, 0, 0);
          require(
              Math.abs(ThidaHudConfig.EFFECT_SCALE.get() - .65) < .001, "Independent effect scale");
          var recipient = ThidaBondHud.recipientBounds();
          mc.screen.mouseClicked(recipient.x() + 8, recipient.y() + 8, 0);
          mc.screen.mouseScrolled(0, 0, 0, 1);
          mc.screen.mouseReleased(0, 0, 0);
          require(
              Math.abs(ThidaHudConfig.RECIPIENT_SCALE.get() - .65) < .001,
              "Independent recipient scale");
          require(
              Math.abs(ThidaHudConfig.BOND_SCALE.get() - .65) < .001,
              "Other selections preserve bond scale");
          mc.screen.onClose();
          mc.setScreen(new ThidaHudEditor(null));
        }
        case 12 -> {
          capture(mc, "after-dragged.png");
          mc.getWindow().setWindowed(854, 480);
          mc.options.guiScale().set(2);
          mc.resizeDisplay();
          scales(1.4);
        }
        case 13 -> {
          capture(mc, "after-small-140.png");
          scales(1);
        }
        case 14 -> {
          capture(mc, "after-small-default.png");
          mc.screen.onClose();
          ThidaHudConfig.BOND_X.save();
          persistence(mc, true);
          LogUtils.getLogger()
              .info("THIDA_HUD_PREVIEW_PASS: samples, GUI2/3, HUD60/100/140, resize, drag, reopen");
          next = now + 2_000_000_000L;
        }
        case 15 -> {
          capture(mc, "after-manual.png");
          if (mc.screen instanceof ThidaHudEditor) mc.screen.onClose();
          ThidaHudConfig.BOND_X.save();
          persistence(mc, true);
          mc.stop();
        }
      }
      if (mc.screen instanceof ThidaHudEditor) {
        checkBounds(ThidaBondHud.bondBounds(true), mc);
        checkBounds(ThidaBondHud.recipientBounds(), mc);
        checkBounds(ThidaHud.previewBounds(), mc);
        var bond = ThidaBondHud.bondBounds(true);
        var effect = ThidaHud.previewBounds();
        var recipient = ThidaBondHud.recipientBounds();
        require(
            !(bond.x() < recipient.x() + recipient.width()
                && bond.x() + bond.width() > recipient.x()
                && bond.y() < recipient.y() + recipient.height()
                && bond.y() + bond.height() > recipient.y()),
            "Recipient separate from bonds");
        require(
            !(bond.x() < effect.x() + effect.width()
                && bond.x() + bond.width() > effect.x()
                && bond.y() < effect.y() + effect.height()
                && bond.y() + bond.height() > effect.y()),
            "Auto placed panels separate");
      }
    } catch (RuntimeException | AssertionError failure) {
      LogUtils.getLogger().error("THIDA_HUD_PREVIEW_FAILED", failure);
      mc.stop();
    }
  }

  private static CompoundTag sample(Minecraft mc) {
    var data = new CompoundTag();
    data.putString("player", mc.player.getUUID().toString());
    data.putBoolean("thida", true);
    int i = 0;
    for (String kind : new String[] {"narotra_doll", "nilakan_doll", "parasite", "heart"}) {
      if (mode == 1 && i > 0) break;
      var n = new CompoundTag();
      n.putString("id", mc.player.getUUID().toString());
      n.putString("name", i == 0 ? "Mai" : "LongTargetName_ผู้เล่นชื่อยาวมาก");
      n.putFloat("hp", i == 0 ? 18 : 1234);
      n.putFloat("maxHp", i == 0 ? 20 : 9999);
      n.putBoolean("available", i != 2);
      n.putString("status", i == 2 ? "ออฟไลน์" : "ทำงาน");
      data.put(kind, n);
      i++;
    }
    var recipient = new CompoundTag();
    recipient.putBoolean("active", mode != 2);
    recipient.putBoolean("used", mode == 3);
    if (mode != 3) recipient.putLong("ward", 3600);
    if (mode == 0 || mode == 2) {
      recipient.putLong("speed", 1000);
      recipient.putLong("invulnerable", 100);
    }
    data.put("recipient", recipient);
    var active = new CompoundTag();
    active.putLong("narotra_doll_0", 180);
    active.putLong("nilakan_doll_1", 80);
    data.put("activeEffects", active);
    var cds = new CompoundTag();
    cds.putLong("cd_heart_doll", 5800);
    data.put("cooldowns", cds);
    return data;
  }

  private static void scales(double value) {
    ThidaHudConfig.BOND_SCALE.set(value);
    ThidaHudConfig.EFFECT_SCALE.set(value);
    ThidaHudConfig.RECIPIENT_SCALE.set(value);
  }

  private static void persistence(Minecraft mc, boolean save) {
    var path = mc.gameDirectory.toPath().resolve("hud-persistence-v3.properties");
    String actual =
        ThidaHudConfig.BOND_X.get()
            + ","
            + ThidaHudConfig.BOND_Y.get()
            + ","
            + ThidaHudConfig.BOND_SCALE.get()
            + ","
            + ThidaHudConfig.RECIPIENT_SCALE.get()
            + ","
            + ThidaHudConfig.EFFECT_SCALE.get();
    try {
      if (save) java.nio.file.Files.writeString(path, actual);
      else if (java.nio.file.Files.exists(path)) {
        require(
            java.nio.file.Files.readString(path).equals(actual),
            "Full restart restores HUD config");
        LogUtils.getLogger().info("THIDA_HUD_RESTART_PASS: {}", actual);
      }
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
  }

  private static void checkBounds(ThidaHudLayout.Bounds b, Minecraft mc) {
    require(
        b.x() >= 0
            && b.y() >= 0
            && b.x() + b.width() <= mc.getWindow().getGuiScaledWidth()
            && b.y() + b.height() <= mc.getWindow().getGuiScaledHeight(),
        "Inside screen");
    int cx = mc.getWindow().getGuiScaledWidth() / 2, cy = mc.getWindow().getGuiScaledHeight() / 2;
    require(!b.contains(cx, cy), "Crosshair visible");
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  private static void capture(Minecraft mc, String name) {
    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
  }

  private HudPreviewSmoke() {}
}
