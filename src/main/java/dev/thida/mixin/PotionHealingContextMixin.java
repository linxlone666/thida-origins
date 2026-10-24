package dev.thida.mixin;

import dev.thida.ThidaHealing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Drinking is a known non-skill heal; do not infer this for arbitrary mod calls to heal(). */
@Mixin(targets = "net.minecraft.world.item.PotionItem")
public abstract class PotionHealingContextMixin {
    @Redirect(method = "finishUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/alchemy/PotionContents;forEachEffect(Ljava/util/function/Consumer;)V"))
    private void thida$potion(net.minecraft.world.item.alchemy.PotionContents contents,
                              java.util.function.Consumer<net.minecraft.world.effect.MobEffectInstance> action,
                              ItemStack stack, Level level, LivingEntity target) {
        var caster = target instanceof net.minecraft.server.level.ServerPlayer p ? p : null;
        ThidaHealing.withSource(caster, ThidaHealing.Kind.NON_SKILL,
                () -> contents.forEachEffect(action));
    }
}
