package dev.thida;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Two-sided energy surfaces; test terrain depth but never hide other transparent effects. */
final class ThidaDomainRenderType extends RenderType {
  static final RenderType ENERGY =
      create(
          "thida_domain_two_sided",
          DefaultVertexFormat.POSITION_COLOR,
          VertexFormat.Mode.QUADS,
          65536,
          false,
          true,
          CompositeState.builder()
              .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
              .setTransparencyState(LIGHTNING_TRANSPARENCY)
              .setCullState(NO_CULL)
              .setWriteMaskState(COLOR_WRITE)
              .setOutputState(WEATHER_TARGET)
              .createCompositeState(false));

  private ThidaDomainRenderType() {
    super(
        "thida_domain_unused",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        256,
        false,
        false,
        () -> {},
        () -> {});
  }
}
