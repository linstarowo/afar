package me.linstar.afar.event;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientReceivedChunkPacketEvent extends AfarEvent {
    final ClientboundLevelChunkWithLightPacket packet;

    public ClientReceivedChunkPacketEvent(ClientboundLevelChunkWithLightPacket packet) {
        this.packet = packet;
    }

    public ClientboundLevelChunkWithLightPacket getPacket() {
        return packet;
    }
}
