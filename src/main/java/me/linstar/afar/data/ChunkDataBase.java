package me.linstar.afar.data;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import me.linstar.afar.network.WrappedChunkPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLPaths;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ChunkDataBase {
    static {
        RocksDB.loadLibrary();
    }

    protected static final Path DATA_PATH = FMLPaths.MODSDIR.get().toAbsolutePath();
    protected static final Options DATA_OPTIONS = new Options().setCreateIfMissing(true);

    private final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(4, 8, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());  //先进先出

    private final String SERVER_NAME;
    RocksDB rocksDB;

    private String currentDimension;

    public ChunkDataBase(String serverName) {
        SERVER_NAME = serverName;
        var serverDir = DATA_PATH.resolve(SERVER_NAME).toFile();
        if (!serverDir.exists()) {
            serverDir.mkdirs();
        }
    }

    public boolean isSameDimension(String dimension) {
        return currentDimension.equals(dimension);
    }

    public void switchDimension(String dimensionId) throws RocksDBException {
        EXECUTOR.getQueue().clear();
        if (this.rocksDB != null) {
            this.rocksDB.close();
        }
        this.rocksDB = RocksDB.open(DATA_OPTIONS, DATA_PATH.resolve(SERVER_NAME).resolve(dimensionId).toString());
        this.currentDimension = dimensionId;
    }

    public void switchDimension() throws RocksDBException {
        switchDimension(Minecraft.getInstance().level.dimension().location().toString().replace(":", "_"));
    }


    public CompletableFuture<Void> put(final WrappedChunkPacket packet) {
        var key = CachedChunkPos.create(packet.getX(), packet.getZ());
        return CompletableFuture.runAsync(() -> {
            try{
                var buff = new FriendlyByteBuf(Unpooled.buffer());
                packet.write(buff);
                this.rocksDB.put(key.toBytes(), buff.array());
            }catch (RocksDBException e){
                LogUtils.getLogger().error("error", e);
            }
        }, EXECUTOR);
    }

    public CompletableFuture<WrappedChunkPacket> get(int x,  int z){
        var key = CachedChunkPos.create(x, z);

        return CompletableFuture.supplyAsync(() -> {
            try{
                byte[] data = this.rocksDB.get(key.toBytes());
                var buff = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                return new WrappedChunkPacket(buff);

            }catch (RocksDBException e) {
                throw new RuntimeException(e);
            }

        }, EXECUTOR);
    }

    public void stop(){
        rocksDB.close();
    }
}
