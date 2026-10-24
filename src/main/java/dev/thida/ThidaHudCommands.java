package dev.thida;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Local HUD preferences only; works without operator privileges and sends no server packet. */
@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class ThidaHudCommands {
    @SubscribeEvent public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("thidahud")
                .executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("ตำแหน่งแถบสถานะ แนวนอน=" + ThidaHudConfig.X.get() + " แนวตั้ง="
                            + ThidaHudConfig.Y.get() + " | /thidahud position <x> <y> | /thidahud rows <1-12> | /thidahud reset"), false);
                    return 1;
                })
                .then(Commands.literal("position")
                        .then(Commands.argument("x", IntegerArgumentType.integer(0, 4096))
                                .then(Commands.argument("y", IntegerArgumentType.integer(0, 4096)).executes(c -> {
                                    ThidaHudConfig.X.set(IntegerArgumentType.getInteger(c, "x"));
                                    ThidaHudConfig.Y.set(IntegerArgumentType.getInteger(c, "y"));
                                    ThidaHudConfig.X.save();
                                    c.getSource().sendSuccess(() -> Component.literal("บันทึกตำแหน่งแถบสถานะแล้ว"), false);
                                    return 1;
                                }))))
                .then(Commands.literal("rows").then(Commands.argument("count", IntegerArgumentType.integer(1, 12)).executes(c -> {
                    ThidaHudConfig.ICONS_PER_ROW.set(IntegerArgumentType.getInteger(c, "count"));
                    ThidaHudConfig.ICONS_PER_ROW.save();
                    c.getSource().sendSuccess(() -> Component.literal("บันทึกจำนวนไอคอนต่อแถวแล้ว"), false);
                    return 1;
                })))
                .then(Commands.literal("preview").executes(c -> {
                    ThidaHud.preview();
                    c.getSource().sendSuccess(() -> Component.literal("แสดงไอคอนตัวอย่าง ท 10 วินาที — ปิดแชตเพื่อดู ไม่มีผลต่อสกิลจริง"), false);
                    return 1;
                }))
                .then(Commands.literal("reset").executes(c -> {
                    ThidaHudConfig.X.set(8); ThidaHudConfig.Y.set(8); ThidaHudConfig.ICONS_PER_ROW.set(6);
                    ThidaHudConfig.X.save();
                    c.getSource().sendSuccess(() -> Component.literal("คืนตำแหน่งแถบสถานะเริ่มต้นแล้ว"), false);
                    return 1;
                })));
    }
    private ThidaHudCommands() {}
}
