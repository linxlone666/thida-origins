package dev.thida;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Dev-only test of a COPY of the affected world; never opens the user's profile world. */
@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class WorldJoinSmoke {
    private static long started, joined;
    private static int titleFrames;
    private static boolean opened, checked;
    private WorldJoinSmoke() {}

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("thida.worldJoinSmoke")) return;
        var client = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started > 300_000_000_000L) {
            LogUtils.getLogger().error("THIDA_WORLD_JOIN_SMOKE_TIMEOUT: {}",
                    client.screen == null ? "none" : client.screen.getClass().getName());
            client.stop(); return;
        }
        if (client.getOverlay() == null && client.screen != null
                && client.screen.getClass().getName().equals("com.yuushya.gui.CheckScreen")) client.screen.onClose();
        if (!opened && client.getOverlay() == null && client.screen instanceof TitleScreen && ++titleFrames > 40) {
            opened = true;
            LogUtils.getLogger().info("THIDA_WORLD_JOIN_SMOKE_OPEN: loading isolated affected-world copy");
            client.createWorldOpenFlows().openWorld("thida-crash-copy", () -> client.setScreen(new TitleScreen()));
        }
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
        if (joined == 0) joined = System.nanoTime();
        if (!checked && System.nanoTime() - joined > 10_000_000_000L) {
            checked = true;
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                long bots = server.getPlayerList().getPlayers().stream()
                        .filter(p -> p.getClass().getName().equals("com.advancedfakeplayers.entity.FakeServerPlayer")).count();
                if (bots > 0) LogUtils.getLogger().info("THIDA_WORLD_JOIN_SMOKE_PASS: joined copied world with {} restored bots", bots);
                else LogUtils.getLogger().error("THIDA_WORLD_JOIN_SMOKE_FAILED: no saved bots restored");
                client.execute(client::stop);
            });
        }
    }
}
