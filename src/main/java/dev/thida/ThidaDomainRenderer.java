package dev.thida;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Additive, depth-tested world geometry: visible in daylight, no solid screen overlay. */
@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class ThidaDomainRenderer {
  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
    ThidaDomainState.clear();
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    if (!Minecraft.getInstance().isPaused()) ThidaDomainState.tick();
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null) return;
    var poses = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    var buffers = mc.renderBuffers().bufferSource();
    boolean drawn = false;
    for (var view : ThidaDomainState.views()) {
      var n = view.data();
      if (!n.getString("dimension").equals(mc.level.dimension().location().toString())) continue;
      double radius = Math.max(1, Math.min(128, n.getDouble("radius")));
      float age =
          ThidaDomainState.age(view, event.getPartialTick().getGameTimeDeltaPartialTick(false));
      float progress = Math.min(1, age / 60f);
      double r = radius * (1 - Math.pow(1 - progress, 3));
      poses.pushPose();
      poses.translate(
          n.getDouble("x") - camera.x, n.getDouble("y") - camera.y, n.getDouble("z") - camera.z);
      Matrix4f matrix = poses.last().pose();
      VertexConsumer out = buffers.getBuffer(ThidaDomainRenderType.ENERGY);
      mirrors(out, matrix, radius, r, age, progress);
      float pulse = .65f + .15f * (float) Math.sin(age * .06);
      ring(out, matrix, r, .16, .28, 255, 25, 95, pulse);
      ring(out, matrix, Math.max(0, r - .7), .18, .09, 255, 190, 60, .9f);
      ring(out, matrix, Math.max(0, r - 1.1), .18, .06, 255, 55, 160, .6f);
      ring(out, matrix, 4 * progress, .2, .13, 255, 190, 60, .8f);
      ring(out, matrix, 5.3 * progress, .2, .08, 255, 65, 135, .7f);
      // Upper and lower meridians outline the actual spherical ability range.
      for (int meridian = 0; meridian < 12; meridian++) {
        double angle = meridian * Math.PI / 12 + age * .0015;
        for (int step = 0; step < 64; step++) {
          double a = step * Math.PI * 2 / 64, b = (step + 1) * Math.PI * 2 / 64;
          double y1 = r * Math.sin(a), y2 = r * Math.sin(b);
          double x1 = r * Math.cos(a), x2 = r * Math.cos(b);
          double dx = Math.sin(angle) * .065, dz = -Math.cos(angle) * .065;
          quad(
              out,
              matrix,
              x1 * Math.cos(angle) - dx,
              y1,
              x1 * Math.sin(angle) - dz,
              x2 * Math.cos(angle) - dx,
              y2,
              x2 * Math.sin(angle) - dz,
              x2 * Math.cos(angle) + dx,
              y2,
              x2 * Math.sin(angle) + dz,
              x1 * Math.cos(angle) + dx,
              y1,
              x1 * Math.sin(angle) + dz,
              210,
              45,
              150,
              .48f);
        }
      }
      for (int latitude = 1; latitude <= 3; latitude++) {
        double y = r * latitude / 4;
        ring(out, matrix, Math.sqrt(r * r - y * y), y, .12, 255, 80, 170, .6f);
      }
      // Eight crossed energy pillars and their crowns make the domain readable from outside.
      for (int i = 0; i < 8; i++) {
        double angle = i * Math.PI / 4;
        double x = r * Math.cos(angle), z = r * Math.sin(angle);
        double height = Math.min(8, radius * .27) * progress;
        quad(
            out, matrix, x - .16, 0, z, x + .16, 0, z, x + .06, height, z, x - .06, height, z, 255,
            165, 45, .85f);
        quad(
            out, matrix, x, 0, z - .16, x, 0, z + .16, x, height, z + .06, x, height, z - .06, 255,
            45, 120, .85f);
      }
      // Rotating six-point ritual seal at the fixed center.
      for (int i = 0; i < 6; i++) {
        double a = i * Math.PI / 3 + age * .008, b = a + Math.PI * 2 / 3;
        double x1 = 5 * progress * Math.cos(a), z1 = 5 * progress * Math.sin(a);
        double x2 = 5 * progress * Math.cos(b), z2 = 5 * progress * Math.sin(b);
        double length = Math.max(.001, Math.hypot(x2 - x1, z2 - z1));
        double dx = -(z2 - z1) / length * .055, dz = (x2 - x1) / length * .055;
        quad(
            out, matrix, x1 - dx, .21, z1 - dz, x2 - dx, .21, z2 - dz, x2 + dx, .21, z2 + dz,
            x1 + dx, .21, z1 + dz, 255, 190, 60, .85f);
      }
      poses.popPose();
      drawn = true;
    }
    if (drawn) buffers.endBatch(ThidaDomainRenderType.ENERGY);
  }

  private static void mirrors(
      VertexConsumer out, Matrix4f base, double radius, double r, float age, float progress) {
    double scale = Math.min(1.5, radius / 30) * progress;
    if (scale < .01) return;
    for (int belt = 0; belt < 2; belt++) {
      int count = belt == 0 ? 12 : 8;
      double height = belt == 0 ? radius * .13 : radius * .56;
      double orbit = Math.sqrt(Math.max(0, r * r - Math.pow(height * progress, 2)));
      for (int i = 0; i < count; i++) {
        double angle = i * Math.PI * 2 / count + age * (belt == 0 ? .008 : -.006) + belt * .3;
        double bob = Math.sin(age * .035 + i) * .35 * scale;
        Matrix4f m =
            new Matrix4f(base)
                .translate(
                    (float) (Math.cos(angle) * orbit),
                    (float) (height * progress + bob),
                    (float) (Math.sin(angle) * orbit))
                .rotateY((float) (Math.PI / 2 - angle + Math.sin(age * .018 + i) * .3))
                .rotateZ((float) (Math.sin(age * .014 + i) * .09))
                .scale((float) scale);
        // Faint silver-blue glass with a violet inset and bright gold bevel.
        panel(out, m, -1.5, -2.5, 1.5, 2.5, 150, 190, 255, .11f);
        panel(out, m, -1.32, -2.32, 1.32, 2.32, 150, 70, 230, .08f);
        panel(out, m, -1.58, -2.58, -1.46, 2.58, 255, 190, 80, .85f);
        panel(out, m, 1.46, -2.58, 1.58, 2.58, 255, 190, 80, .85f);
        panel(out, m, -1.58, 2.46, 1.58, 2.58, 255, 205, 120, .9f);
        panel(out, m, -1.58, -2.58, 1.58, -2.46, 255, 160, 65, .8f);
        // A traveling highlight reads as glass without rendering a second world scene.
        double sweep = ((age * .025 + i * .31) % 4) - 2;
        double low = Math.max(-2.3, sweep - .35), high = Math.min(2.3, sweep + .35);
        if (high > low) panel(out, m, -1.3, low, 1.3, high, 205, 235, 255, .2f);
        quad(out, m, -1.15, -1.9, 0, -.98, -1.9, 0, .95, 1.9, 0, .78, 1.9, 0, 210, 230, 255, .18f);
        // Diamond sigil floats in the pane's center, readable from either side.
        quad(out, m, 0, .55, 0, .35, 0, 0, 0, -.55, 0, -.35, 0, 0, 255, 120, 195, .55f);
        panel(out, m, -.04, -.85, .04, .85, 255, 210, 125, .75f);
      }
    }
  }

  private static void panel(
      VertexConsumer out,
      Matrix4f m,
      double left,
      double bottom,
      double right,
      double top,
      int red,
      int green,
      int blue,
      float alpha) {
    quad(
        out, m, left, bottom, 0, right, bottom, 0, right, top, 0, left, top, 0, red, green, blue,
        alpha);
  }

  private static void ring(
      VertexConsumer v,
      Matrix4f m,
      double r,
      double y,
      double width,
      int red,
      int green,
      int blue,
      float alpha) {
    for (int i = 0; i < 128; i++) {
      double a = i * Math.PI / 64,
          b = (i + 1) * Math.PI / 64,
          inner = Math.max(0, r - width),
          outer = r + width;
      quad(
          v,
          m,
          inner * Math.cos(a),
          y,
          inner * Math.sin(a),
          outer * Math.cos(a),
          y,
          outer * Math.sin(a),
          outer * Math.cos(b),
          y,
          outer * Math.sin(b),
          inner * Math.cos(b),
          y,
          inner * Math.sin(b),
          red,
          green,
          blue,
          alpha);
    }
  }

  private static void quad(
      VertexConsumer v,
      Matrix4f m,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      double x3,
      double y3,
      double z3,
      double x4,
      double y4,
      double z4,
      int r,
      int g,
      int b,
      float a) {
    vertex(v, m, x1, y1, z1, r, g, b, a);
    vertex(v, m, x2, y2, z2, r, g, b, a);
    vertex(v, m, x3, y3, z3, r, g, b, a);
    vertex(v, m, x4, y4, z4, r, g, b, a);
  }

  private static void vertex(
      VertexConsumer v, Matrix4f m, double x, double y, double z, int r, int g, int b, float a) {
    v.addVertex(m, (float) x, (float) y, (float) z).setColor(r, g, b, (int) (255 * a));
  }

  private ThidaDomainRenderer() {}
}
