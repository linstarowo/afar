package me.linstar.afar.event;

import net.minecraft.network.protocol.game.ClientboundRespawnPacket;

public class ClientLevelChangeEvent extends AfarEvent {
    final ClientboundRespawnPacket packet;
    public ClientLevelChangeEvent(ClientboundRespawnPacket packet) {
        this.packet = packet;
    }

    public ClientboundRespawnPacket getPacket() {
        return packet;
    }
}
