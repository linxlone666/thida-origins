package dev.thida;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Development-only check of the menu path which loads new-world dynamic registries. */
@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class SingleplayerSmoke {
  private static int titleTicks;
  private static int createWorldTicks;
  private static boolean opened;
  private static long started;
  private static int frames;

  @SubscribeEvent
  public static void frame(RenderFrameEvent.Post event) {
    if (!Boolean.getBoolean("thida.singleplayerSmoke")) return;
    Minecraft client = Minecraft.getInstance();
    // Yuushya's optional resource recommendations precede the title screen in DRC.
    // Its onClose returns to the previous screen without changing its config.
    if (client.getOverlay() == null
        && client.screen != null
        && client.screen.getClass().getName().equals("com.yuushya.gui.CheckScreen")) {
      LogUtils.getLogger()
          .info("THIDA_SINGLEPLAYER_SMOKE_DISMISS: Yuushya resource recommendations");
      client.screen.onClose();
    }
    if (++frames == 300)
      LogUtils.getLogger()
          .info(
              "THIDA_SINGLEPLAYER_SMOKE_SCREEN: {}",
              client.screen == null ? "none" : client.screen.getClass().getName());
    if (started == 0) started = System.nanoTime();
    if (System.nanoTime() - started > 180_000_000_000L) {
      LogUtils.getLogger()
          .error("THIDA_SINGLEPLAYER_SMOKE_TIMEOUT: menu did not reach Create World");
      client.stop();
      return;
    }
    if (!opened
        && client.getOverlay() == null
        && client.screen instanceof TitleScreen
        && ++titleTicks >= 40) {
      opened = true;
      LogUtils.getLogger()
          .info("THIDA_SINGLEPLAYER_SMOKE_OPEN: opening Singleplayer on a fresh test directory");
      client.setScreen(new SelectWorldScreen(client.screen));
    }
    if (opened && client.screen instanceof CreateWorldScreen && ++createWorldTicks >= 20) {
      LogUtils.getLogger()
          .info(
              "THIDA_SINGLEPLAYER_SMOKE_PASS: Create World screen opened and dynamic registries"
                  + " loaded");
      client.stop();
    }
  }

  private SingleplayerSmoke() {}
}
