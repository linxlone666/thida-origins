package dev.thida.mixin;

import dev.thida.ThidaDollInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class DollContainerMixin {
  @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
  private void thida$keepDolls(
      int slot, int button, ClickType type, Player player, CallbackInfo ci) {
    if (ThidaDollInventory.blocks(
        (AbstractContainerMenu) (Object) this, slot, button, type, player)) ci.cancel();
  }
}
