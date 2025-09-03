package me.linstar.afar.event;

import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;

public class ClientReceivedForgetChunkEvent extends AfarEvent {
    final ClientboundForgetLevelChunkPacket packet;
    public ClientReceivedForgetChunkEvent(ClientboundForgetLevelChunkPacket packet) {
        this.packet = packet;
    }

    public ClientboundForgetLevelChunkPacket getPacket() {
        return packet;
    }
}
