package dev.thida.mixin;

import dev.thida.ThidaHealing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Regeneration moved from MobEffect to a dedicated implementation in Minecraft 1.21.1. */
@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RegenerationHealingMixin {
    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void thida$regeneration(LivingEntity target, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (!(target instanceof ServerPlayer recipient) || recipient.getHealth() >= recipient.getMaxHealth()) return;
        var data = recipient.getPersistentData();
        ServerPlayer caster = data.hasUUID("thida_regen_source")
                ? recipient.server.getPlayerList().getPlayer(data.getUUID("thida_regen_source")) : null;
        boolean external = data.hasUUID("thida_regen_source") && !data.getUUID("thida_regen_source").equals(recipient.getUUID());
        ThidaHealing.apply(caster, recipient, 1, external ? ThidaHealing.Kind.OTHER : ThidaHealing.Kind.NON_SKILL);
        cir.setReturnValue(true);
    }
}
