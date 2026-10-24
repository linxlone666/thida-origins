package dev.thida;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.*;

@Mod(ThidaMod.MOD_ID)
public final class ThidaMod {
  public static final String MOD_ID = "thida";
  public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
  public static final DeferredRegister<net.minecraft.world.effect.MobEffect> EFFECTS =
      DeferredRegister.create(net.minecraft.core.registries.Registries.MOB_EFFECT, MOD_ID);
  public static final DeferredHolder<
          net.minecraft.world.effect.MobEffect, net.minecraft.world.effect.MobEffect>
      HEART_BLEED =
          EFFECTS.register(
              "heart_bleed",
              () ->
                  new net.minecraft.world.effect.MobEffect(
                      net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0xA82E45) {});
  public static final DeferredHolder<
          net.minecraft.world.effect.MobEffect, net.minecraft.world.effect.MobEffect>
      SPELL_BLEED =
          EFFECTS.register(
              "spell_bleed",
              () ->
                  new net.minecraft.world.effect.MobEffect(
                      net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x7F2B5C) {});
  public static final DeferredItem<Item> PARASITE_MASK =
      ITEMS.register("parasite_mask", () -> new Item(new Item.Properties().stacksTo(1)));
  public static final Map<String, DeferredItem<? extends Item>> ABILITIES = new LinkedHashMap<>();
  // Old IDs remain inert for existing saves; excluded from kits, creative tabs and recipes.
  public static final Map<String, DeferredItem<? extends Item>> RETIRED = new LinkedHashMap<>();

  static {
    for (String id :
        new String[] {
          "narotra_doll", "nilakan_doll", "parasite_doll", "heart_doll", "domain_talisman"
        }) {
      ABILITIES.put(
          id, ITEMS.register(id, () -> new AbilityItem(new Item.Properties().stacksTo(1))));
    }
    for (String id : new String[] {"ward", "veil", "grace", "remedy", "herbal_orb"})
      RETIRED.put(
          id,
          ITEMS.register(
              id,
              () ->
                  new Item(new Item.Properties().stacksTo(1)) {
                    @Override
                    public void appendHoverText(
                        ItemStack stack,
                        Item.TooltipContext context,
                        List<Component> lines,
                        TooltipFlag flag) {
                      lines.add(
                          Component.translatable("item.thida.retired")
                              .withStyle(ChatFormatting.DARK_GRAY));
                    }
                  }));
  }

  public ThidaMod(IEventBus bus, ModContainer container) {

    ITEMS.register(bus);
    EFFECTS.register(bus);
    bus.addListener(ThidaNetwork::register);
    bus.addListener(this::creativeItems);
    container.registerConfig(ModConfig.Type.SERVER, ThidaConfig.SPEC);
    container.registerConfig(ModConfig.Type.CLIENT, ThidaHudConfig.SPEC);
    NeoForge.EVENT_BUS.addListener(this::commands);
  }

  private void creativeItems(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
      ABILITIES.entrySet().stream()
          .filter(e -> !e.getKey().equals("parasite_doll") && !e.getKey().equals("heart_doll"))
          .map(java.util.Map.Entry::getValue)
          .forEach(event::accept);
  }

  private void commands(RegisterCommandsEvent event) {
    ThidaPermissions.register(event.getDispatcher());
    ThidaDebug.register(event.getDispatcher());
    event
        .getDispatcher()
        .register(
            Commands.literal("thida")
                .then(
                    Commands.literal("kit")
                        .executes(
                            ctx -> {
                              ServerPlayer player = ctx.getSource().getPlayerOrException();
                              if (!ThidaEvents.isThida(player)) {
                                ctx.getSource()
                                    .sendFailure(
                                        Component.literal(
                                            ThidaMessages.text("ui.thida.mod.commands.1")));
                                return 0;
                              }
                              for (DeferredItem<? extends Item> item :
                                  java.util.List.of(
                                      ABILITIES.get("narotra_doll"),
                                      ABILITIES.get("nilakan_doll"))) {
                                if (!player.getInventory().contains(new ItemStack(item.get()))) {
                                  ItemStack stack = new ItemStack(item.get());
                                  if (!player.addItem(stack)) player.drop(stack, false);
                                }
                              }
                              ctx.getSource()
                                  .sendSuccess(
                                      () ->
                                          Component.literal(
                                              ThidaMessages.text("ui.thida.mod.commands.2")),
                                      false);
                              return 1;
                            }))
                .then(
                    Commands.literal("help")
                        .executes(
                            ctx -> {
                              ctx.getSource()
                                  .sendSuccess(
                                      () ->
                                          Component.literal(
                                              ThidaMessages.text("ui.thida.mod.commands.3")
                                                  + ThidaMessages.text("ui.thida.mod.commands.4")
                                                  + ThidaMessages.text("ui.thida.mod.commands.5")
                                                  + ThidaMessages.text("ui.thida.mod.commands.6")
                                                  + ThidaMessages.text("ui.thida.mod.commands.7")
                                                  + ThidaMessages.text("ui.thida.mod.commands.8")),
                                      false);
                              return 1;
                            })));
  }

  public static final class AbilityItem extends Item {
    public AbilityItem(Properties properties) {
      super(properties);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
      if (ThidaDollInventory.isDoll(stack)) {
        if (player instanceof ServerPlayer p) {
          ThidaEvents.tell(p, ThidaMessages.text("message.thida.passive.tossed"));
          p.containerMenu.broadcastFullState();
        }
        return false;
      }
      return super.onDroppedByPlayer(stack, player);
    }

    @Override
    public boolean canFitInsideContainerItems() {
      return !ThidaDollInventory.isDoll(this) && super.canFitInsideContainerItems();
    }

    @Override
    public void inventoryTick(
        ItemStack stack,
        Level level,
        net.minecraft.world.entity.Entity entity,
        int slot,
        boolean selected) {
      ThidaBonds.discardExpiredDoll(stack, level);
      super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public void appendHoverText(
        ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
      ThidaTooltips.append(ThidaEvents.id(stack), lines);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
        Level level, Player player, InteractionHand hand) {
      if (player instanceof ServerPlayer serverPlayer)
        return ThidaEvents.use(serverPlayer, player.getItemInHand(hand), hand);
      return InteractionResultHolder.success(player.getItemInHand(hand));
    }
  }
}
