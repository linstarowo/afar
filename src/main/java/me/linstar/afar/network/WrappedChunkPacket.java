package me.linstar.afar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;

public class WrappedChunkPacket extends ClientboundLevelChunkWithLightPacket implements WrappedPacket{
    public WrappedChunkPacket(FriendlyByteBuf p_195710_){
        super(p_195710_);
    }
}
