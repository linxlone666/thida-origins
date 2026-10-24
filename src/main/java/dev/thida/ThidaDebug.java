package dev.thida;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Operator-only helpers included in the separate Debug build. */
public final class ThidaDebug {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("thida")
                .then(Commands.literal("resetcooldown").requires(s -> s.hasPermission(2))
                        .executes(c -> reset(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> reset(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("status")
                                .executes(c -> status(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> status(c, EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("end")
                                .executes(c -> end(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> end(c, EntityArgument.getPlayer(c, "player")))))));
    }
    private static int reset(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        int count = ThidaEvents.debugResetCooldowns(p);
        c.getSource().sendSuccess(() -> Component.literal("รีเซ็ตคูลดาวน์ " + p.getScoreboardName()
                + " แล้ว (" + count + ") — เป้าหมายและพลังที่กำลังทำงานยังอยู่"), false);
        return 1;
    }
    private static int end(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        ThidaEvents.debugEndPowers(p);
        c.getSource().sendSuccess(() -> Component.literal("ยุติหุ่น/พันธะ/โดเมนของ " + p.getScoreboardName()
                + " แล้ว เริ่มคูลดาวน์ปกติ; ใช้ /thida resetcooldown เพื่อทดลองใหม่"), false);
        return 1;
    }
    private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        var data = ThidaEvents.hudSnapshot(p);
        StringBuilder text = new StringBuilder("[Thida Debug] ").append(p.getScoreboardName())
                .append(" | HP ").append(p.getHealth()).append('/').append(p.getMaxHealth())
                .append(" | Origin ").append(data.getBoolean("thida"));
        for (String key : new String[]{"nilakan_doll", "narotra_doll", "parasite", "heart"}) {
            var link = data.getCompound(key);
            text.append('\n').append(key).append(": ").append(link.getString("id").isEmpty() ? "—" : link.getString("name"))
                    .append(link.getString("id").isEmpty() || link.getBoolean("available") ? "" : " (ไม่พร้อมรับผล)");
        }
        text.append("\nParasite armed: ").append(data.getBoolean("armed"))
                .append(" | Dolls locked: ").append(data.getBoolean("dollsLocked"))
                .append(" | Heart save used: ").append(data.getBoolean("heartUsed"))
                .append("\nDomain: ").append(ThidaHudState.time(data.getLong("domain")));
        for (String section : new String[]{"activeEffects", "cooldowns"}) {
            var times = data.getCompound(section);
            text.append('\n').append(section).append(':');
            for (String key : new java.util.TreeSet<>(times.getAllKeys())) if (times.getLong(key) > 0)
                text.append("\n  ").append(key).append(" = ").append(ThidaHudState.time(times.getLong(key)));
        }
        var curses = p.getPersistentData().getCompound("thida_state");
        long now = p.level().getGameTime();
        text.append("\nReceived curses / ดีบัฟที่ได้รับ:");
        String[] keys = {"weakUntil", "slow", "bleed", "vulnerableUntil"};
        String[] names = {"ดาเมจที่สร้างลดลง", "ตีช้า", "เลือดไหล", "รับดาเมจแรงขึ้น"};
        for (int i = 0; i < keys.length; i++) text.append("\n  ").append(names[i]).append(" = ")
                .append(ThidaHudState.time(Math.max(0, curses.getLong(keys[i])-now)));
        var blind = p.getEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
        text.append("\n  ตาบอด = ").append(ThidaHudState.time(blind == null ? 0 : blind.getDuration()));
        text.append("\n  Attack speed = ").append(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED));
        c.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }
    private ThidaDebug() {}
}
