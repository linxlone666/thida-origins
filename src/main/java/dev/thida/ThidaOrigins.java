package dev.thida;

import com.iafenvoy.origins.attachment.OriginDataHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Authoritative selection check for the Origins 0.4-beta.2 used by the DRC profile. */
public final class ThidaOrigins {
  public static final ResourceLocation THIDA =
      ResourceLocation.fromNamespaceAndPath("thida", "thida");

  private ThidaOrigins() {}

  public static boolean isThida(ServerPlayer player) {
    return OriginDataHolder.optional(player)
        .map(
            data ->
                data.getOrigins().values().stream()
                    .anyMatch(
                        origin ->
                            origin
                                .unwrapKey()
                                .map(key -> key.location().equals(THIDA))
                                .orElse(false)))
        .orElse(false);
  }
}
