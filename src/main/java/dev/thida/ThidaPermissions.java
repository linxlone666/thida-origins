package dev.thida;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ThidaPermissions {
  public static boolean unlocked(ServerPlayer p, int skill) {
    return ThidaEvents.isThida(p)
        && ThidaBondData.get(p.server).player(p).getBoolean("unlock" + skill);
  }

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    var unlock = Commands.literal("unlock").requires(s -> s.hasPermission(2));
    var player = Commands.argument("player", EntityArgument.player());
    for (int skill : new int[] {2, 3}) {
      final int id = skill;
      player.then(
          Commands.literal("skill" + skill)
              .executes(
                  c -> {
                    ServerPlayer target = EntityArgument.getPlayer(c, "player");
                    if (!ThidaEvents.isThida(target)) {
                      c.getSource()
                          .sendFailure(
                              Component.literal(
                                  ThidaMessages.text("ui.thida.permissions.register.1")));
                      return 0;
                    }
                    var data = ThidaBondData.get(target.server);
                    data.player(target).putBoolean("unlock" + id, true);
                    data.setDirty();
                    c.getSource()
                        .sendSuccess(
                            () ->
                                Component.literal(
                                    ThidaMessages.text("ui.thida.permissions.register.2")
                                        + id
                                        + ThidaMessages.text("ui.thida.permissions.register.3")
                                        + target.getScoreboardName()
                                        + ThidaMessages.text("ui.thida.permissions.register.4")),
                            true);
                    ThidaEvents.tell(
                        target,
                        ThidaMessages.text("ui.thida.permissions.register.2")
                            + id
                            + ThidaMessages.text("ui.thida.permissions.register.4"));
                    return 1;
                  }));
    }
    dispatcher.register(Commands.literal("thida").then(unlock.then(player)));
  }

  private ThidaPermissions() {}
}
