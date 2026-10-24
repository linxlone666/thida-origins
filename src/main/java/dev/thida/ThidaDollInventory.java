package dev.thida;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Dolls may move within their owner's inventory, never into a world/container item. */
@EventBusSubscriber(modid="thida")
public final class ThidaDollInventory {
    private static final String RETAINED="thida_retained_dolls";
    private ThidaDollInventory() {}
    public static boolean isDoll(Item item) {
        var id=BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals("thida") && switch(id.getPath()) {
            case "narotra_doll", "nilakan_doll", "parasite_doll", "heart_doll" -> true;
            default -> false;
        };
    }
    public static boolean isDoll(ItemStack stack) { return !stack.isEmpty() && isDoll(stack.getItem()); }
    public static boolean blocks(AbstractContainerMenu menu,int slotId,int button,ClickType type,Player player) {
        var carried=menu.getCarried();
        var slot=slotId>=0 && slotId<menu.slots.size() ? menu.slots.get(slotId) : null;
        boolean own=slot!=null && slot.container==player.getInventory();
        var clicked=slot==null ? ItemStack.EMPTY : slot.getItem();
        if(type==ClickType.THROW && isDoll(clicked)) return true;
        if(type==ClickType.CLONE && isDoll(clicked)) return true;
        if(type==ClickType.QUICK_MOVE && own && isDoll(clicked) && !(menu instanceof InventoryMenu)) return true;
        if(type==ClickType.SWAP && !own) {
            if((button>=0 && button<9 || button==40) && isDoll(player.getInventory().getItem(button))) return true;
        }
        if(isDoll(carried)) {
            if(type==ClickType.PICKUP && !own) return true;
            // Only own slots may enter the quick-craft destination set; allow its begin/end markers.
            if(type==ClickType.QUICK_CRAFT && AbstractContainerMenu.getQuickcraftHeader(button)==1 && !own) return true;
        }
        return false;
    }
    private static void retain(ServerPlayer player,ItemStack stack) {
        if(stack.isEmpty()) return;
        var list=player.getPersistentData().getList(RETAINED,10);
        list.add(stack.save(player.registryAccess())); player.getPersistentData().put(RETAINED,list);
    }
    public static boolean retainedToken(ServerPlayer player,String token,java.util.UUID expected) {
        return !retainedItem(player,token,expected).isEmpty();
    }
    public static ItemStack retainedItem(ServerPlayer player,String token,java.util.UUID expected) {
        for(var value:player.getPersistentData().getList(RETAINED,10)) {
            var stack=ItemStack.parseOptional(player.registryAccess(),(CompoundTag)value);
            var tag=ThidaItemData.read(stack);
            if(tag.hasUUID(token) && tag.getUUID(token).equals(expected)) return stack;
        }
        return ItemStack.EMPTY;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void toss(ItemTossEvent event) {
        if(!(event.getPlayer() instanceof ServerPlayer player) || !isDoll(event.getEntity().getItem())) return;
        event.setCanceled(true);
        var stack=event.getEntity().getItem(); player.getInventory().add(stack);
        if(!stack.isEmpty()) retain(player,stack);
        event.getEntity().discard();
        ThidaEvents.tell(player,"หุ่นไม่สามารถโยนหรือย้ายออกจากตัวได้");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void drops(LivingDropsEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        var iterator=event.getDrops().iterator();
        while(iterator.hasNext()) {
            var entity=iterator.next();
            if(isDoll(entity.getItem())) { retain(player,entity.getItem()); iterator.remove(); entity.discard(); }
        }
    }
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if(event.getEntity() instanceof ServerPlayer player && event.getOriginal().getPersistentData().contains(RETAINED)) {
            player.getPersistentData().put(RETAINED,event.getOriginal().getPersistentData().getList(RETAINED,10).copy());
            event.getOriginal().getPersistentData().remove(RETAINED);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void restore(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || !player.getPersistentData().contains(RETAINED)) return;
        ListTag pending=new ListTag();
        for(var value:player.getPersistentData().getList(RETAINED,10)) {
            var stack=ItemStack.parseOptional(player.registryAccess(),(CompoundTag)value);
            ThidaBonds.discardExpiredDoll(stack,player.level());
            if(!stack.isEmpty()) player.getInventory().add(stack);
            if(!stack.isEmpty()) pending.add(stack.save(player.registryAccess()));
        }
        if(pending.isEmpty()) player.getPersistentData().remove(RETAINED);
        else player.getPersistentData().put(RETAINED,pending);
    }
}
