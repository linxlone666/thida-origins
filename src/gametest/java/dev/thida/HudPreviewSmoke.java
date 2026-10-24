package dev.thida;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Isolated client check. Requires a disposable world copy named hud-preview. */
@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class HudPreviewSmoke {
  private static long started;
  private static boolean opening;
  private static int frames, stage;

  @SubscribeEvent
  public static void frame(RenderFrameEvent.Post event) {
    if (!Boolean.getBoolean("thida.hudPreview")) return;
    var mc = Minecraft.getInstance();
    if (started == 0) started = System.nanoTime();
    if (System.nanoTime() - started > 180_000_000_000L) {
      LogUtils.getLogger().error("THIDA_HUD_PREVIEW_TIMEOUT");
      mc.stop();
      return;
    }
    if (!opening && mc.getOverlay() == null && mc.screen instanceof TitleScreen && ++frames > 40) {
      opening = true;
      frames = 0;
      mc.createWorldOpenFlows().openWorld("hud-preview", () -> mc.setScreen(new TitleScreen()));
    }
    if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
    if (++frames < 35) return;
    frames = 0;
    try {
      switch (stage++) {
        case 0 -> {
          ThidaHudConfig.BOND_X.set(8);
          ThidaHudConfig.BOND_Y.set(8);
          ThidaHudConfig.BOND_SCALE.set(1.0);
          ThidaHudConfig.X.set(8);
          ThidaHudConfig.Y.set(8);
          ThidaHudConfig.EFFECT_SCALE.set(.9);
          ThidaHudConfig.RECIPIENT_X.set(0);
          ThidaHudConfig.RECIPIENT_Y.set(-80);
          ThidaHudConfig.RECIPIENT_SCALE.set(.9);
          mc.options.guiScale().set(2);
          mc.resizeDisplay();
          mc.setScreen(null);
          mc.player.setItemInHand(
              InteractionHand.MAIN_HAND,
              new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get()));
          mc.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
          ThidaClient.openMenu();
          require(
              mc.screen
                  .getTitle()
                  .getString()
                  .equals(ThidaMessages.text("ui.thida.client.spells.8")),
              "One doll opens spells directly");
        }
        case 1 -> {
          capture(mc, "single-doll-menu.png");
          mc.setScreen(null);
          mc.player.setItemInHand(
              InteractionHand.MAIN_HAND,
              new ItemStack(ThidaMod.ABILITIES.get("narotra_doll").get()));
          mc.player.setItemInHand(
              InteractionHand.OFF_HAND,
              new ItemStack(ThidaMod.ABILITIES.get("nilakan_doll").get()));
          ThidaClient.openMenu();
          require(
              mc.screen.getTitle().getString().equals(ThidaMessages.text("power.thida.arts.name")),
              "Two dolls open selection");
        }
        case 2 -> {
          capture(mc, "two-doll-menu.png");
          mc.setScreen(new ThidaHudEditor(null));
        }
        case 3 -> {
          capture(mc, "hud-editor-default.png");
          var screen = mc.screen;
          var bounds = ThidaBondHud.bondBounds(true);
          screen.mouseClicked(bounds.x() + 10, bounds.y() + 10, 0);
          screen.mouseDragged(bounds.x() + 42, bounds.y() + 48, 0, 32, 38);
          screen.mouseReleased(0, 0, 0);
          screen.mouseScrolled(0, 0, 0, -1);
          screen.mouseScrolled(0, 0, 0, -1);
          require(
              Math.abs(ThidaHudConfig.BOND_SCALE.get() - .9) < .001,
              "Editor wheel scales selected panel");
          require(
              ThidaHudConfig.BOND_X.get() == bounds.x() + 32, "Editor drag updates panel position");
        }
        case 4 -> {
          capture(mc, "hud-editor-resized.png");
          mc.screen.onClose();
          mc.setScreen(new ThidaHudEditor(null));
          require(
              Math.abs(ThidaHudConfig.BOND_SCALE.get() - .9) < .001,
              "Reopened editor retains size");
        }
        case 5 -> {
          if (new java.io.File(mc.gameDirectory, "screenshots/hud-editor-resized.png").isFile()) {
            LogUtils.getLogger()
                .info("THIDA_HUD_PREVIEW_PASS: menus, drag, scale, persistence and screenshots");
            mc.stop();
          } else stage = 5;
        }
      }
    } catch (RuntimeException | AssertionError failure) {
      LogUtils.getLogger().error("THIDA_HUD_PREVIEW_FAILED", failure);
      mc.stop();
    }
  }

  private static void capture(Minecraft mc, String name) {
    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private HudPreviewSmoke() {}
}
