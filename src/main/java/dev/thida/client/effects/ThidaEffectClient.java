package dev.thida.client.effects;

import com.google.gson.JsonParser;
import dev.thida.*;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Client-only resource and entity plumbing; no skill mutation or server request. */
@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class ThidaEffectClient {
  private static Object level;

  private ThidaEffectClient() {}

  @EventBusSubscriber(modid = "thida", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
  public static final class Registration {
    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener(
          new SimplePreparableReloadListener<java.util.List<ThidaEffectBinding>>() {
            @Override
            protected java.util.List<ThidaEffectBinding> prepare(
                ResourceManager resources, ProfilerFiller profiler) {
              var bindings = new ArrayList<ThidaEffectBinding>();
              resources
                  .listResources("thida_effects", path -> path.getPath().endsWith(".json"))
                  .forEach(
                      (location, resource) -> {
                        try (var reader = resource.openAsReader()) {
                          var file = JsonParser.parseReader(reader).getAsJsonObject();
                          if (file.get("schema").getAsInt() != 1)
                            throw new IllegalArgumentException("Unsupported mapping schema");
                          int index = 0;
                          for (var value : file.getAsJsonArray("bindings")) {
                            var binding = value.getAsJsonObject();
                            if (!binding.has("enabled") || binding.get("enabled").getAsBoolean())
                              bindings.add(
                                  ThidaEffectBinding.read(location + ":" + index, binding));
                            index++;
                          }
                        } catch (Exception error) {
                          ThidaClientEffects.warnOnce(
                              location.toString(), new IllegalArgumentException(error));
                        }
                      });
              return java.util.List.copyOf(bindings);
            }

            @Override
            protected void apply(
                java.util.List<ThidaEffectBinding> bindings,
                ResourceManager resources,
                ProfilerFiller profiler) {
              ThidaClientEffects.reloadMappings(bindings);
            }
          });
    }
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var client = Minecraft.getInstance();
    if (client.level == null || client.player == null || client.isPaused()) return;
    if (level != client.level) {
      level = client.level;
      ThidaClientEffects.worldChanged();
      ThidaClientEffects.installResolver(ThidaEffectClient::frame);
    }
    ThidaClientEffects.tick();
  }

  private static ThidaPassiveEffectAdapter.Frame frame(
      ThidaEffectEvent event, ThidaEffectBinding binding, double elapsed, long remaining) {
    var client = Minecraft.getInstance();
    if (client.level == null) return null;
    var anchor = binding.anchor() == null ? event.anchor() : binding.anchor();
    var position = event.point();
    float yaw = 0, pitch = 0;
    if (anchor != ThidaEffectEvent.Anchor.WORLD) {
      var id = anchor == ThidaEffectEvent.Anchor.TARGET ? event.target() : event.owner();
      if (id == null) return null;
      var player = client.level.getPlayerByUUID(id);
      if (player == null) return null;
      yaw = player.getYRot();
      pitch = player.getXRot();
      var point = player.position();
      if (anchor == ThidaEffectEvent.Anchor.HAND) {
        double side =
            player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -.35 : .35;
        if (event.hand().equals("OFF_HAND")) side = -side;
        double radians = Math.toRadians(yaw);
        point =
            player.getEyePosition().add(Math.cos(radians) * side, -.35, Math.sin(radians) * side);
      }
      position =
          new ThidaEffectEvent.Point(
              client.level.dimension().location().toString(), point.x, point.y, point.z);
    }
    if (!client.level.dimension().location().toString().equals(position.dimension())) return null;
    var offset = binding.offset();
    position =
        new ThidaEffectEvent.Point(
            position.dimension(),
            position.x() + offset.x(),
            position.y() + offset.y(),
            position.z() + offset.z());
    return new ThidaPassiveEffectAdapter.Frame(position, yaw, pitch, elapsed, remaining);
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    ThidaClientEffects.clear();
    level = null;
  }

  @SubscribeEvent
  public static void commands(RegisterClientCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("thidafx")
                .then(
                    Commands.literal("preview")
                        .executes(
                            context -> {
                              var client = Minecraft.getInstance();
                              if (client.player == null) return 0;
                              ThidaClientEffects.installAdapter(new VanillaPreviewAdapter());
                              ThidaClientEffects.preview(
                                  new ThidaEffectEvent.Point(
                                      client.level.dimension().location().toString(),
                                      client.player.getX(),
                                      client.player.getY(),
                                      client.player.getZ()),
                                  client.player.getUUID(),
                                  "thida:preview/end_rod");
                              context
                                  .getSource()
                                  .sendSuccess(
                                      () -> Component.translatable("message.thida.fx.preview"),
                                      false);
                              return 1;
                            }))
                .then(
                    Commands.literal("off")
                        .executes(
                            context -> {
                              ThidaClientEffects.installAdapter(null);
                              context
                                  .getSource()
                                  .sendSuccess(
                                      () -> Component.translatable("message.thida.fx.off"), false);
                              return 1;
                            })));
  }
}
