package dev.thida.mixin;

import dev.thida.ThidaEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve the player source which vanilla LivingHealEvent does not expose. */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class HealingEffectMixin {
    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"), cancellable = true)
    private void thida$instant(Entity direct, Entity indirect, LivingEntity target, int amplifier,
                               double potency, CallbackInfo ci) {
        if ((Object)this == MobEffects.HEAL.value() && target instanceof ServerPlayer recipient
                && !recipient.isInvertedHealAndHarm() && indirect instanceof ServerPlayer caster
                && caster != recipient) {
            float amount = (int)(potency * (4 << amplifier) + 0.5D);
            if (amount > 0) ThidaEvents.addonHeal(caster, recipient, amount);
            ci.cancel();
        }
    }

}
