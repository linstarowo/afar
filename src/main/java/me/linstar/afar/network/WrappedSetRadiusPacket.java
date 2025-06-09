package me.linstar.afar.network;

import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;

public class WrappedSetRadiusPacket extends ClientboundSetChunkCacheRadiusPacket implements WrappedPacket {
    public WrappedSetRadiusPacket(int radius) {
        super(radius);
    }
}
