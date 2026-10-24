package dev.thida.mixin;

import dev.thida.ThidaHealing;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.food.FoodData")
public abstract class NaturalHealingMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void thida$natural(Player player, float amount) {
        if (player instanceof ServerPlayer p) ThidaHealing.apply(p, p, amount, ThidaHealing.Kind.NON_SKILL);
        else player.heal(amount);
    }
}
