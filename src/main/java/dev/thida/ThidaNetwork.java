package dev.thida;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ThidaNetwork {
    private static final String VERSION = "7-passive-effects";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("thida", "controls"), () -> VERSION, VERSION::equals, VERSION::equals);
    public record DollAction(int button) {}
    public record HudSnapshot(net.minecraft.nbt.CompoundTag data) {}
    public record DomainSnapshot(net.minecraft.nbt.CompoundTag data) {}
    public record PassiveEffectEvent(net.minecraft.nbt.CompoundTag data) {}
    public static void register() {
        CHANNEL.messageBuilder(DollAction.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> buffer.writeVarInt(message.button()))
                .decoder(buffer -> new DollAction(buffer.readVarInt()))
                .consumerMainThread((message, context) -> {
                    var player = context.get().getSender();
                    if (player != null) ThidaEvents.dollAction(player, message.button());
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(HudSnapshot.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeNbt(message.data()))
                .decoder(buffer -> new HudSnapshot(buffer.readNbt()))
                .consumerMainThread((message, context) -> {
                    ThidaHudState.accept(message.data());
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(DomainSnapshot.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeNbt(message.data()))
                .decoder(buffer -> new DomainSnapshot(buffer.readNbt()))
                .consumerMainThread((message, context) -> {
                    ThidaDomainState.accept(message.data());
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(PassiveEffectEvent.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeNbt(message.data()))
                .decoder(buffer -> new PassiveEffectEvent(buffer.readNbt()))
                .consumerMainThread((message, context) -> {
                    ThidaClientEffects.accept(message.data());
                    context.get().setPacketHandled(true);
                }).add();
    }
    private ThidaNetwork() {}
}


