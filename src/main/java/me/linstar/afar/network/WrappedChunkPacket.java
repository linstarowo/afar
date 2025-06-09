package me.linstar.afar.network;

import io.netty.buffer.Unpooled;
import me.linstar.afar.mixin.accessor.LevelChunkPacketDataAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

public class WrappedChunkPacket extends ClientboundLevelChunkWithLightPacket implements WrappedPacket{
    public WrappedChunkPacket(LevelChunk p_285290_, LevelLightEngine p_285254_, @Nullable BitSet p_285350_, @Nullable BitSet p_285304_) {
        super(p_285290_, p_285254_, p_285350_, p_285304_);
        ((LevelChunkPacketDataAccessor) getChunkData()).getBlockEntitiesData().clear();
    }

    public WrappedChunkPacket(FriendlyByteBuf p_195710_){
        super(p_195710_);
        ((LevelChunkPacketDataAccessor) getChunkData()).getBlockEntitiesData().clear();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf p_195712_) {
        ((LevelChunkPacketDataAccessor) getChunkData()).getBlockEntitiesData().clear();
        super.write(p_195712_);
    }

    public static WrappedChunkPacket wrap(ClientboundLevelChunkWithLightPacket packet){
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buf);
        return new WrappedChunkPacket(buf);
    }
}
