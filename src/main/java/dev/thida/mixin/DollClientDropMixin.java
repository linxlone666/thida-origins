package dev.thida.mixin;

import dev.thida.ThidaDollInventory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class DollClientDropMixin {
  @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
  private void thida$noDropPrediction(boolean all, CallbackInfoReturnable<Boolean> cir) {
    var player = (LocalPlayer) (Object) this;
    if (ThidaDollInventory.isDoll(player.getMainHandItem())) {
      player.displayClientMessage(
          Component.literal(dev.thida.ThidaMessages.text("message.thida.passive.tossed")), true);
      cir.setReturnValue(false);
    }
  }
}
