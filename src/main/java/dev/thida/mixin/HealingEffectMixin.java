package dev.thida.mixin;

import dev.thida.ThidaEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve the player source which vanilla LivingHealEvent does not expose. */
@Mixin(MobEffect.class)
public abstract class HealingEffectMixin {
    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"), cancellable = true)
    private void thida$instant(Entity direct, Entity indirect, LivingEntity target, int amplifier,
                               double potency, CallbackInfo ci) {
        if ((Object)this == MobEffects.HEAL && target instanceof ServerPlayer recipient
                && !recipient.isInvertedHealAndHarm() && indirect instanceof ServerPlayer caster
                && caster != recipient) {
            float amount = (int)(potency * (4 << amplifier) + 0.5D);
            if (amount > 0) ThidaEvents.addonHeal(caster, recipient, amount);
            ci.cancel();
        }
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void thida$regeneration(LivingEntity target, int amplifier, CallbackInfo ci) {
        if ((Object)this != MobEffects.REGENERATION || !(target instanceof ServerPlayer recipient)
                || recipient.getHealth() >= recipient.getMaxHealth()) return;
        var data = recipient.getPersistentData();
        if (!data.hasUUID("thida_regen_source")) return;
        ServerPlayer caster = recipient.server.getPlayerList().getPlayer(data.getUUID("thida_regen_source"));
        if (caster != null && caster != recipient) {
            ThidaEvents.addonHeal(caster, recipient, 1);
            ci.cancel();
        }
    }
}
