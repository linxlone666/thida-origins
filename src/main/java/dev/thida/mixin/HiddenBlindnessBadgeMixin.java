package dev.thida.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The inventory ignores showIcon; honor an explicitly hidden blindness instance there too. */
@Mixin(value = ClientHooks.class, remap = false)
abstract class HiddenBlindnessBadgeMixin {
  @Inject(method = "shouldRenderEffect", at = @At("RETURN"), cancellable = true)
  private static void thida$hideConcealedBlindness(
      MobEffectInstance effect, CallbackInfoReturnable<Boolean> result) {
    if (effect.getEffect().equals(MobEffects.BLINDNESS)
        && !effect.showIcon()
        && !effect.isVisible()) result.setReturnValue(false);
  }
}
