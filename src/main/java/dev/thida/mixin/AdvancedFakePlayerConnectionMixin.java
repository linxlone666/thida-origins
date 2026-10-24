package dev.thida.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AFP 1.0.0 installs its no-op listener only AFTER placeNewPlayer fires mod sync events.
 * Drop packets to its synthetic connection during that gap, before NeoForge validates
 * client channels. Real connections and every other fake-player implementation are untouched. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class AdvancedFakePlayerConnectionMixin {
    @Shadow @Final protected Connection connection;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), cancellable = true)
    private void thida$skipSyntheticClient(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (connection.getClass().getName().equals("com.advancedfakeplayers.network.FakeConnection")) {
            ci.cancel();
        }
    }
}
