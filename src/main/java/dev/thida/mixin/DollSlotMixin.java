package dev.thida.mixin;

import dev.thida.ThidaDollInventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class DollSlotMixin {
  @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
  private void thida$noExternalSlot(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
    if (ThidaDollInventory.isDoll(stack)
        && !(((Slot) (Object) this).container
            instanceof net.minecraft.world.entity.player.Inventory)) cir.setReturnValue(false);
  }
}
