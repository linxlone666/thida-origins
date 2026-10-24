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
    dispatcher.register(
        Commands.literal("thida")
            .then(
                Commands.literal("resetcooldown")
                    .requires(s -> s.hasPermission(2))
                    .executes(c -> reset(c, c.getSource().getPlayerOrException()))
                    .then(
                        Commands.argument("player", EntityArgument.player())
                            .executes(c -> reset(c, EntityArgument.getPlayer(c, "player")))))
            .then(
                Commands.literal("debug")
                    .requires(s -> s.hasPermission(2))
                    .then(
                        Commands.literal("status")
                            .executes(c -> status(c, c.getSource().getPlayerOrException()))
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .executes(
                                        c -> status(c, EntityArgument.getPlayer(c, "player")))))
                    .then(
                        Commands.literal("end")
                            .executes(c -> end(c, c.getSource().getPlayerOrException()))
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .executes(
                                        c -> end(c, EntityArgument.getPlayer(c, "player")))))));
  }

  private static int reset(CommandContext<CommandSourceStack> c, ServerPlayer p) {
    int count = ThidaEvents.debugResetCooldowns(p);
    c.getSource()
        .sendSuccess(
            () ->
                Component.literal(
                    ThidaMessages.text("ui.thida.debug.reset.1")
                        + p.getScoreboardName()
                        + ThidaMessages.text("ui.thida.debug.reset.2")
                        + count
                        + ThidaMessages.text("ui.thida.debug.reset.3")),
            false);
    return 1;
  }

  private static int end(CommandContext<CommandSourceStack> c, ServerPlayer p) {
    ThidaEvents.debugEndPowers(p);
    c.getSource()
        .sendSuccess(
            () ->
                Component.literal(
                    ThidaMessages.text("ui.thida.debug.end.1")
                        + p.getScoreboardName()
                        + ThidaMessages.text("ui.thida.debug.end.2")),
            false);
    return 1;
  }

  private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
    var data = ThidaEvents.hudSnapshot(p);
    StringBuilder text =
        new StringBuilder(ThidaMessages.text("ui.thida.debug.status.1"))
            .append(p.getScoreboardName())
            .append(ThidaMessages.text("ui.thida.debug.status.2"))
            .append(p.getHealth())
            .append('/')
            .append(p.getMaxHealth())
            .append(ThidaMessages.text("ui.thida.debug.status.3"))
            .append(
                data.getBoolean("thida")
                    ? ThidaMessages.text("ui.thida.debug.status.4")
                    : ThidaMessages.text("ui.thida.debug.status.5"));
    for (String key : new String[] {"nilakan_doll", "narotra_doll", "parasite", "heart"}) {
      var link = data.getCompound(key);
      text.append('\n')
          .append(label(key))
          .append(": ")
          .append(link.getString("id").isEmpty() ? "—" : link.getString("name"))
          .append(
              link.getString("id").isEmpty() || link.getBoolean("available")
                  ? ""
                  : ThidaMessages.text("ui.thida.debug.status.6"));
    }
    text.append(ThidaMessages.text("ui.thida.debug.status.7"))
        .append(
            data.getBoolean("armed")
                ? ThidaMessages.text("ui.thida.debug.status.4")
                : ThidaMessages.text("ui.thida.debug.status.8"))
        .append(ThidaMessages.text("ui.thida.debug.status.9"))
        .append(
            data.getBoolean("dollsLocked")
                ? ThidaMessages.text("ui.thida.debug.status.4")
                : ThidaMessages.text("ui.thida.debug.status.8"))
        .append(ThidaMessages.text("ui.thida.debug.status.10"))
        .append(
            data.getBoolean("heartUsed")
                ? ThidaMessages.text("ui.thida.debug.status.4")
                : ThidaMessages.text("ui.thida.debug.status.11"))
        .append(ThidaMessages.text("ui.thida.debug.status.12"))
        .append(ThidaHudState.time(data.getLong("domain")));
    for (String section : new String[] {"activeEffects", "cooldowns"}) {
      var times = data.getCompound(section);
      text.append('\n')
          .append(
              section.equals("activeEffects")
                  ? ThidaMessages.text("ui.thida.debug.status.13")
                  : ThidaMessages.text("ui.thida.debug.status.14"))
          .append(':');
      for (String key : new java.util.TreeSet<>(times.getAllKeys()))
        if (times.getLong(key) > 0)
          text.append("\n  ")
              .append(label(key))
              .append(" = ")
              .append(ThidaHudState.time(times.getLong(key)));
    }
    var curses = p.getPersistentData().getCompound("thida_state");
    long now = p.level().getGameTime();
    text.append(ThidaMessages.text("ui.thida.debug.status.15"));
    String[] keys = {"weakUntil", "slow", "bleed", "vulnerableUntil"};
    String[] names = {
      ThidaMessages.text("ui.thida.debug.status.16"),
      ThidaMessages.text("ui.thida.debug.status.17"),
      ThidaMessages.text("ui.thida.debug.status.18"),
      ThidaMessages.text("ui.thida.debug.status.19")
    };
    for (int i = 0; i < keys.length; i++)
      text.append("\n  ")
          .append(names[i])
          .append(" = ")
          .append(ThidaHudState.time(Math.max(0, curses.getLong(keys[i]) - now)));
    var blind = p.getEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
    text.append(ThidaMessages.text("ui.thida.debug.status.20"))
        .append(ThidaHudState.time(blind == null ? 0 : blind.getDuration()));
    text.append(ThidaMessages.text("ui.thida.debug.status.21"))
        .append(
            p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED));
    c.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
    return 1;
  }

  private static String label(String key) {
    String name =
        key.contains("nilakan")
            ? ThidaMessages.text("ui.thida.client.choices.2")
            : key.contains("narotra")
                ? ThidaMessages.text("ui.thida.client.choices.1")
                : key.contains("parasite")
                    ? ThidaMessages.text("ui.thida.debug.label.1")
                    : key.contains("heart")
                        ? ThidaMessages.text("ui.thida.debug.label.2")
                        : key.contains("domain")
                            ? ThidaMessages.text("ui.thida.debug.label.3")
                            : ThidaMessages.text("ui.thida.debug.label.4");
    if (key.matches(".*_[0-2]$"))
      name +=
          ThidaMessages.text("ui.thida.debug.label.5") + (key.charAt(key.length() - 1) - '0' + 1);
    return name;
  }

  private ThidaDebug() {}
}
