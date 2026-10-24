package dev.thida.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class RegenerationSourceMixin {
  @Inject(
      method =
          "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
      at = @At("RETURN"))
  private void thida$recordSource(
      MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
    if ((Object) this instanceof ServerPlayer player
        && cir.getReturnValue()
        && effect.getEffect() == MobEffects.REGENERATION) {
      if (source instanceof ServerPlayer caster)
        player.getPersistentData().putUUID("thida_regen_source", caster.getUUID());
      else player.getPersistentData().remove("thida_regen_source");
    }
  }
}
