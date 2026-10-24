package dev.thida;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Restore AFP bots after ALL ServerStartedEvent listeners (including Plasmo Voice) finish. */
@EventBusSubscriber(modid = "thida")
public final class ThidaFakePlayerStartup {
  private static final Set<MinecraftServer> PENDING = ConcurrentHashMap.newKeySet();

  private ThidaFakePlayerStartup() {}

  public static void defer(MinecraftServer server) {
    PENDING.add(server);
    LogUtils.getLogger()
        .info("THIDA_AFP_RESTORE_DEFERRED: waiting for first completed server tick");
  }

  @SubscribeEvent
  public static void afterTick(ServerTickEvent.Post event) {
    MinecraftServer server = event.getServer();
    if (!PENDING.remove(server)) return;
    try {
      // Optional integration: exact AFP 1.0.0 method verified from the installed JAR.
      Class.forName("com.advancedfakeplayers.data.FakePlayerSavedData")
          .getMethod("respawnAll", MinecraftServer.class)
          .invoke(null, server);
      LogUtils.getLogger()
          .info("THIDA_AFP_RESTORE_COMPLETE: saved fake players restored after startup");
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Advanced Fake Players saved-player restoration failed", e);
    }
  }

  @SubscribeEvent
  public static void stopping(ServerStoppingEvent event) {
    PENDING.remove(event.getServer());
  }
}
