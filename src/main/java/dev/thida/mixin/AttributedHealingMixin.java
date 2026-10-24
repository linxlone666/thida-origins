package dev.thida.mixin;

import dev.thida.ThidaHealing;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class AttributedHealingMixin {
  @Unique
  private final java.util.ArrayDeque<Float> thida$healthBeforeHeal = new java.util.ArrayDeque<>();

  @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
  private void thida$before(float amount, CallbackInfo ci) {
    var entity = (LivingEntity) (Object) this;
    if (entity.level().isClientSide) return;
    if (ThidaHealing.blocked(entity)) {
      ci.cancel();
      return;
    }
    thida$healthBeforeHeal.push(entity.getHealth());
  }

  @Inject(method = "heal", at = @At("RETURN"))
  private void thida$after(float amount, CallbackInfo ci) {
    if (!thida$healthBeforeHeal.isEmpty())
      ThidaHealing.after((LivingEntity) (Object) this, thida$healthBeforeHeal.pop());
  }
}
