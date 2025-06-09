package me.linstar.afar.network;

import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;

public class WrappedForgetChunkPacket extends ClientboundForgetLevelChunkPacket implements WrappedPacket{
    public WrappedForgetChunkPacket(int x, int z) {
        super(x, z);
    }
}
