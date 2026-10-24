package dev.thida;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import net.neoforged.neoforge.registries.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Mod(ThidaMod.MOD_ID)
public final class ThidaMod {
    public static final String MOD_ID = "thida";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<Item> PARASITE_MASK = ITEMS.register("parasite_mask", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final Map<String, DeferredItem<? extends Item>> ABILITIES = new LinkedHashMap<>();
    // Old IDs remain inert for existing saves; excluded from kits, creative tabs and recipes.
    public static final Map<String, DeferredItem<? extends Item>> RETIRED = new LinkedHashMap<>();
    static {
        for (String id : new String[]{"narotra_doll", "nilakan_doll", "parasite_doll", "heart_doll", "domain_talisman"}) {
            ABILITIES.put(id, ITEMS.register(id, () -> new AbilityItem(new Item.Properties().stacksTo(1))));
        }
        for (String id : new String[]{"ward", "veil", "grace", "remedy", "herbal_orb"})
            RETIRED.put(id, ITEMS.register(id, () -> new Item(new Item.Properties().stacksTo(1)) {
                @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
                    lines.add(Component.translatable("item.thida.retired").withStyle(ChatFormatting.DARK_GRAY));
                }
            }));
    }
    public ThidaMod(IEventBus bus, ModContainer container) {

        ITEMS.register(bus);
        bus.addListener(ThidaNetwork::register);
        bus.addListener(this::creativeItems);
        container.registerConfig(ModConfig.Type.SERVER, ThidaConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ThidaHudConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(this::commands);
    }
    private void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
            ABILITIES.values().forEach(event::accept);
    }
    private void commands(RegisterCommandsEvent event) {
        ThidaDebug.register(event.getDispatcher());
        event.getDispatcher().register(Commands.literal("thida")
            .then(Commands.literal("kit").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (!ThidaEvents.isThida(player)) {
                    ctx.getSource().sendFailure(Component.literal("เลือกเผ่ากำเนิดธิดาก่อน"));
                    return 0;
                }
                for (DeferredItem<? extends Item> item : ABILITIES.values()) {
                    if (!player.getInventory().contains(new ItemStack(item.get()))) {
                        ItemStack stack = new ItemStack(item.get());
                        if (!player.addItem(stack)) player.drop(stack, false);
                    }
                }
                ctx.getSource().sendSuccess(() -> Component.literal("ได้รับชุดไอเทมธิดาแล้ว"), false);
                return 1;
            }))
            .then(Commands.literal("help").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal("/thida kit | ถือหุ่นกดปุ่มเปิดเมนู (เริ่มต้น R; เปลี่ยนได้ในการตั้งค่าปุ่ม) แล้วกด 1 เพื่อยกเลิกสายสัมพันธ์ | นิลกาล: โจมตีผู้เล่นใน 10 บล็อกเพื่อเชื่อม | นรตรา: จ้องผู้เล่นใน 15 บล็อก 10 วินาทีเพื่อเชื่อม | กาฝาก: คลิกขวาเสกแล้วโจมตีเพื่อเชื่อม | พันธะ/โดเมน: คลิกขวา"), false);
                return 1;
            })));
    }
    public static final class AbilityItem extends Item {
        public AbilityItem(Properties properties) { super(properties); }
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
            ThidaTooltips.append(ThidaEvents.id(stack), lines);
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) return ThidaEvents.use(serverPlayer, player.getItemInHand(hand), hand);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
    }
}

