package dev.thida;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** Server-to-client, privacy-minimal event dispatcher for future passive visuals. */
public final class ThidaPassiveEffects {
    private ThidaPassiveEffects() {}

    public static void emit(String event, String passive, ServerPlayer owner, ServerPlayer target,
                            Vec3 position, InteractionHand hand, String reason) {
        if (owner == null || owner.server == null) return;
        CompoundTag packet = new CompoundTag();
        packet.putString("event", event);
        packet.putString("passive", passive);
        packet.putUUID("owner", owner.getUUID());
        if (target != null) packet.putUUID("target", target.getUUID());
        else {
            String key = passive.equals("NAROTRA") ? "narotra_doll" : "nilakan_doll";
            if (ThidaEvents.state(owner).hasUUID(key)) packet.putUUID("target", ThidaEvents.state(owner).getUUID(key));
        }
        packet.putString("dimension", owner.level().dimension().location().toString());
        packet.putDouble("x", position.x); packet.putDouble("y", position.y); packet.putDouble("z", position.z);
        packet.putString("hand", hand == null ? "" : hand.name());
        packet.putString("reason", reason == null ? "" : reason);
        ThidaNetwork.PassiveEffectEvent message = new ThidaNetwork.PassiveEffectEvent(packet);
        if (!(owner instanceof net.minecraftforge.common.util.FakePlayer))
            ThidaNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner), message);
        if (target != null && target != owner && target.isAlive() && !(target instanceof net.minecraftforge.common.util.FakePlayer))
            ThidaNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), message);
    }
}
