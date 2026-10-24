package dev.thida;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ThidaNetwork {
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("thida", name));
    }
    public record DollAction(int button) implements CustomPacketPayload {
        public static final Type<DollAction> TYPE = ThidaNetwork.type("doll_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, DollAction> CODEC = StreamCodec.of(
                (buffer, message) -> buffer.writeVarInt(message.button()), buffer -> new DollAction(buffer.readVarInt()));
        @Override public Type<DollAction> type() { return TYPE; }
    }
    public record HudSnapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<HudSnapshot> TYPE = ThidaNetwork.type("hud");
        public static final StreamCodec<RegistryFriendlyByteBuf, HudSnapshot> CODEC = StreamCodec.of(
                (buffer, message) -> buffer.writeNbt(message.data()), buffer -> new HudSnapshot(buffer.readNbt()));
        @Override public Type<HudSnapshot> type() { return TYPE; }
    }
    public record DomainSnapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<DomainSnapshot> TYPE = ThidaNetwork.type("domain");
        public static final StreamCodec<RegistryFriendlyByteBuf, DomainSnapshot> CODEC = StreamCodec.of(
                (buffer, message) -> buffer.writeNbt(message.data()), buffer -> new DomainSnapshot(buffer.readNbt()));
        @Override public Type<DomainSnapshot> type() { return TYPE; }
    }
    public record PassiveEffectEvent(CompoundTag data) implements CustomPacketPayload {
        public static final Type<PassiveEffectEvent> TYPE = ThidaNetwork.type("passive_effect");
        public static final StreamCodec<RegistryFriendlyByteBuf, PassiveEffectEvent> CODEC = StreamCodec.of(
                (buffer, message) -> buffer.writeNbt(message.data()), buffer -> new PassiveEffectEvent(buffer.readNbt()));
        @Override public Type<PassiveEffectEvent> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("8-neoforge");
        registrar.playToServer(DollAction.TYPE, DollAction.CODEC, (message, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) ThidaEvents.dollAction(player, message.button());
        }));
        registrar.playToClient(HudSnapshot.TYPE, HudSnapshot.CODEC,
                (message, context) -> context.enqueueWork(() -> ThidaHudState.accept(message.data())));
        registrar.playToClient(DomainSnapshot.TYPE, DomainSnapshot.CODEC,
                (message, context) -> context.enqueueWork(() -> ThidaDomainState.accept(message.data())));
        registrar.playToClient(PassiveEffectEvent.TYPE, PassiveEffectEvent.CODEC,
                (message, context) -> context.enqueueWork(() -> ThidaClientEffects.accept(message.data())));
    }
    private ThidaNetwork() {}
}