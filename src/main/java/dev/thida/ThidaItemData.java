package dev.thida;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Item custom data must be copied and written back through 1.21 data components. */
public final class ThidaItemData {
  private ThidaItemData() {}

  public static CompoundTag read(ItemStack stack) {
    return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
  }

  public static void putUUID(ItemStack stack, String key, UUID value) {
    CompoundTag data = read(stack);
    data.putUUID(key, value);
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
  }
}
