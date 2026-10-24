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
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Mod(ThidaMod.MOD_ID)
public final class ThidaMod {
    public static final String MOD_ID = "thida";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final Map<String, RegistryObject<Item>> ABILITIES = new LinkedHashMap<>();
    static {
        for (String id : new String[]{"ward", "veil", "grace", "remedy", "herbal_orb", "narotra_doll", "nilakan_doll", "parasite_doll", "heart_doll", "domain_talisman"}) {
            ABILITIES.put(id, ITEMS.register(id, () -> new AbilityItem(new Item.Properties().stacksTo(1))));
        }
    }
    public ThidaMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener(this::creativeItems);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ThidaConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
    }
    private void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
            ABILITIES.values().forEach(event::accept);
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("thida")
            .then(Commands.literal("kit").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (!ThidaEvents.isThida(player)) {
                    ctx.getSource().sendFailure(Component.literal("เลือก Origin ธิดาก่อน / Select the Thida Origin first."));
                    return 0;
                }
                for (RegistryObject<Item> item : ABILITIES.values()) {
                    if (!player.getInventory().contains(new ItemStack(item.get()))) {
                        ItemStack stack = new ItemStack(item.get());
                        if (!player.addItem(stack)) player.drop(stack, false);
                    }
                }
                ctx.getSource().sendSuccess(() -> Component.literal("ได้รับชุดไอเทมธิดา / Thida kit supplied."), false);
                return 1;
            }))
            .then(Commands.literal("help").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal("/thida kit — รับไอเทม | คลิกขวาใช้สกิล | Shift+คลิกขวาหุ่นเพื่อเปลี่ยนโหมด | ตีด้วยหุ่นเพื่อผูกเป้าหมาย"), false);
                return 1;
            })));
    }
    public static final class AbilityItem extends Item {
        public AbilityItem(Properties properties) { super(properties); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("item.thida.origin_required").withStyle(ChatFormatting.DARK_PURPLE));
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) return ThidaEvents.use(serverPlayer, player.getItemInHand(hand), hand);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
    }
}
