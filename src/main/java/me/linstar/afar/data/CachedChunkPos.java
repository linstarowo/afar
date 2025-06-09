package me.linstar.afar.data;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public record CachedChunkPos(int x, int z) {
    public static CachedChunkPos load(byte[] data){
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        return new CachedChunkPos(buf.readInt(), buf.readInt());
    }

    public static CachedChunkPos create(int x, int z){
        return new CachedChunkPos(x, z);
    }

    public byte[] toBytes(){
        FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
        byteBuf.writeInt(x);
        byteBuf.writeInt(z);

        return byteBuf.array();
    }
}
