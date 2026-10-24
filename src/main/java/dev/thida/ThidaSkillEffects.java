package dev.thida;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** Named server events use the existing no-op client dispatcher; no particle dependency. */
public final class ThidaSkillEffects {
  /** Expiry still reaches the recipient when the caster is offline or the bond has ended. */
  public static void emitForRecipient(
      String event, String skill, java.util.UUID ownerId, ServerPlayer recipient, String reason) {
    ThidaPassiveEffects.emit(
        recipient.server,
        event,
        skill,
        ownerId,
        recipient.getUUID(),
        "",
        reason,
        ThidaEffectEvent.Anchor.TARGET,
        "");
  }

  public static void emit(
      String event, String skill, ServerPlayer owner, ServerPlayer target, String reason) {
    InteractionHand hand =
        ThidaEvents.id(owner.getOffhandItem()).equals(skill)
                && !ThidaEvents.id(owner.getMainHandItem()).equals(skill)
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
    ThidaPassiveEffects.emit(event, skill, owner, target, owner.position(), hand, reason);
  }

  public static void confirm(ServerPlayer owner, String kind, ServerPlayer target) {
    if (owner instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
    CompoundTag data = new CompoundTag();
    data.putString("kind", kind);
    data.putUUID("target", target.getUUID());
    data.putString("name", target.getScoreboardName());
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
        owner, new ThidaNetwork.Confirmation(data));
  }

  public static void spell(
      String event, ThidaSpell spell, ServerPlayer owner, ServerPlayer target) {
    ThidaPassiveEffects.emit(
        owner.server,
        event,
        spell.doll(),
        owner.getUUID(),
        target.getUUID(),
        spell.id(),
        "",
        ThidaEffectEvent.Anchor.TARGET,
        "");
  }

  private ThidaSkillEffects() {}
}
