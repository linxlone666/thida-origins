package dev.thida.mixin;

import dev.thida.ThidaFakePlayerStartup;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AFP's startup handler only calls respawnAll; defer that call until startup is complete. */
@Pseudo
@Mixin(targets = "com.advancedfakeplayers.AdvancedFakePlayers", remap = false)
public abstract class AdvancedFakePlayerStartupMixin {
  @Inject(method = "onServerStarted", at = @At("HEAD"), cancellable = true, remap = false)
  private void thida$restoreAfterStartup(ServerStartedEvent event, CallbackInfo ci) {
    ThidaFakePlayerStartup.defer(event.getServer());
    ci.cancel();
  }
}
