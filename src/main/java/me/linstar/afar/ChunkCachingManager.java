package me.linstar.afar;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import me.linstar.afar.config.Config;
import me.linstar.afar.event.ClientLevelChangeEvent;
import me.linstar.afar.event.ClientReceivedChunkPacketEvent;
import me.linstar.afar.event.ClientReceivedForgetChunkEvent;
import me.linstar.afar.event.WorldIdEvent;
import me.linstar.afar.network.WrappedForgetChunkPacket;
import me.linstar.afar.until.Box;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber  //确保类加载
public class ChunkCachingManager {
    private static final AtomicReference<ChunkCachingManager> INSTANCE = new AtomicReference<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        INSTANCE.set(new ChunkCachingManager());
    }

    final AtomicReference<Statue> currentStatue = new AtomicReference<>();
    final AtomicReference<String> currentWorldId = new AtomicReference<>();
    final AtomicReference<ChunkPos> currentCentre = new AtomicReference<>();

    protected final ConcurrentLinkedQueue<ChunkData> receivedChunkDataQueue = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<ChunkPos> chunkLoadQueue = new ConcurrentLinkedQueue<>();

    final Set<ChunkPos> loadedChunks = ConcurrentHashMap.newKeySet();
    final Minecraft minecraft;

    SqliteChunkDataBase database;

    public enum Statue{
        STOP(),
        RUNNING(),
        PAUSED()
    }

    public record ChunkData(int x, int z, FriendlyByteBuf buf){
        @Override
        public int hashCode() {
            return ChunkPos.hash(x, z);
        }
    }

    private ChunkCachingManager() {
        currentStatue.set(Statue.STOP);
        minecraft = Minecraft.getInstance();
    }

    public void start(){
        if (currentStatue.get() == Statue.RUNNING || currentWorldId.get() == null) return;
        try {
            assert minecraft.level != null;
            var dimension = minecraft.level.dimension().location().toString().replace(":", "_");
            database = SqliteChunkDataBase.create(FMLPaths.MODSDIR.get().resolve( dimension + "_" + this.currentWorldId.get() + ".db").toFile());
            assert database != null;
            database.start();

            var lastStatue = currentStatue.getAndSet(Statue.RUNNING);
            if (lastStatue == Statue.STOP) MinecraftForge.EVENT_BUS.register(this);

        }catch (Exception e){
            LOGGER.error("Failed to start chunk caching", e);
        }
    }

    public void stop(){
        synchronized (this) {
            if (currentStatue.get() == Statue.STOP) return;
            try {
                MinecraftForge.EVENT_BUS.unregister(this);
                currentStatue.set(Statue.STOP);
                currentCentre.set(null);
                currentWorldId.set(null);

                database.close();
                receivedChunkDataQueue.clear();
                chunkLoadQueue.clear();
                loadedChunks.clear();
            } catch (Exception e) {
                LOGGER.error("Error when stopping chunk caching", e);
            }
        }
    }

    public static ChunkCachingManager get(){
        return INSTANCE.get();
    }

    @SubscribeEvent
    public void onDimensionChange(ClientLevelChangeEvent event){
        Afar.sendDebugMessage("Dimension changed. Pausing");
        this.currentStatue.set(Statue.PAUSED);
        currentWorldId.set(null);
        currentCentre.set(null);

        database.close();
        receivedChunkDataQueue.clear();
        chunkLoadQueue.clear();
        loadedChunks.clear();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isServer()) return;
        if (currentStatue.get() != Statue.RUNNING) return;
        var player = minecraft.player;
        var level = minecraft.level;
        var connection = minecraft.getConnection();
        if (player == null || level == null || connection == null) return;

        var centre = player.chunkPosition();
        var lateCentre = currentCentre.get();

        if (!centre.equals(lateCentre)) {
            currentCentre.set(centre);
            Box old = (lateCentre != null) ? Box.create(lateCentre.x, lateCentre.z, Config.getRenderDistance()) : Box.create(0, 0, 0);
            Box nev = Box.create(centre.x, centre.z , Config.getRenderDistance());

            //将新区块列入加载队列
            nev.forEach((x1, z1) -> {
                if (!old.contain(x1, z1) && level.getChunk(x1, z1) instanceof EmptyLevelChunk) {
                    chunkLoadQueue.offer(new ChunkPos(x1, z1));
                }
            });

            //将旧区块卸载
            loadedChunks.removeIf(pos -> {
                if (nev.contain(pos.x, pos.z)) return false;
                minecraft.executeIfPossible(() -> {
                    connection.handleForgetLevelChunk(new WrappedForgetChunkPacket(pos.x, pos.z));
                });
                return true;
            });
        }
    }

    @SubscribeEvent
    public void onChunkDataReceived(ClientReceivedChunkPacketEvent event){
        if (currentStatue.get() != Statue.RUNNING) return;
        var packet = event.getPacket();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buf);
        receivedChunkDataQueue.add(new ChunkData(packet.getX(), packet.getZ(), buf));
    }

    @SubscribeEvent
    public void onChunkForgetReceived(ClientReceivedForgetChunkEvent event){
        if (currentStatue.get() != Statue.RUNNING) return;
        var packet = event.getPacket();
        loadedChunks.add(new ChunkPos(packet.getX(), packet.getZ()));
    }

    @SubscribeEvent
    public void onDisconnected(ClientPlayerNetworkEvent.LoggingOut event){
        this.stop();
    }

    //作为启动入口点
    @SubscribeEvent
    public static void onWorldIdChange(WorldIdEvent event){
        if (!Config.isEnable()) return;
        Afar.sendDebugMessage("Received worldId: %s".formatted(event.getID()));
        var statue = INSTANCE.get().currentStatue.get();
        if (statue == Statue.RUNNING){
            LOGGER.debug("Received World Id Packet Twice.");
            return;
        }
        INSTANCE.get().currentWorldId.set(event.getID());
        Minecraft.getInstance().executeIfPossible(() -> INSTANCE.get().start()); //WorldIdEvent 将在Netty Thread fired. 此处意在确保start在客户端完成RespawnPacket的处理后再启动新数据库
    }

//    //Debug Entry
//    @SubscribeEvent
//    public static void onConnected(ClientPlayerNetworkEvent.LoggingIn event){
//        if (!Config.isEnable() || !Config.isDebug()) return;
//        var instance = INSTANCE.get();
//        if (instance.currentStatue.get() != Statue.RUNNING){
//            instance.currentWorldId.set("test");
//            instance.start();
//        }
//    }

    public Statue getStatue(){
        return currentStatue.get();
    }

    public SqliteChunkDataBase getDatabase(){
        return database;
    }
}
