package me.linstar.afar;

import com.mojang.logging.LogUtils;
import me.linstar.afar.data.ChunkDataBase;
import me.linstar.afar.network.WrappedChunkPacket;
import me.linstar.afar.network.WrappedForgetChunkPacket;
import me.linstar.afar.until.Box;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(Dist.CLIENT)
public class ChunkCacheManager {  //史诗级单例 长生命周期

    public static final ChunkCacheManager INSTANCE = new ChunkCacheManager();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Set<int[]> SUBMITTED_FAKE_CHUNKS = ConcurrentHashMap.newKeySet();

    private ChunkDataBase dataBase;
    private Minecraft minecraft;
    volatile int centralX;
    volatile int centralZ;
    int realChunkRadius = -1;
    boolean isStarted = false;

    public ChunkCacheManager() {}

    public void start(){
        try {
            this.minecraft = Minecraft.getInstance();
            var serverName = Objects.requireNonNull(minecraft.getCurrentServer()).ip.replace(":", "_");
            var chunkPos = Objects.requireNonNull(minecraft.player).chunkPosition();
            dataBase = new ChunkDataBase(serverName);
            dataBase.switchDimension();

            centralX = chunkPos.x;
            centralZ = chunkPos.z;
            isStarted = true;
        }catch (Exception e){
            LOGGER.error("Failed to start chunk caching", e);
        }
    }

    public void stop(){
        if(!isStarted) return;
        if(dataBase != null) dataBase.stop();
        isStarted = false;
    }

    public void onChunkReceived(ClientboundLevelChunkWithLightPacket packet){
        if (!isStarted) return;
        dataBase.put(WrappedChunkPacket.wrap(packet));
    }

    public void onViewCenterPacket(int x, int z){
        if (!isStarted) return;
        if (centralX == x && centralZ == z) return;

        Box old = Box.create(centralX, centralZ, Config.getRenderDistance());
        Box nev = Box.create(x, z , Config.getRenderDistance());
        Box server_new = Box.create(x, z, realChunkRadius);

        centralX = x;
        centralZ = z;

        nev.forEach((x1, z1) -> {
            if (!old.contain(x1, z1) && !server_new.contain(x1, z1)) loadChunk(x1, z1);
        });

        SUBMITTED_FAKE_CHUNKS.removeIf(pos -> {
            if (nev.contain(pos[0], pos[1])) return false;;
            this.forgetChunk(pos[0], pos[1]);
            return true;
        });
    }

    private void loadChunk(int x, int z){
        var connection = minecraft.getConnection();
        if (connection == null) return;
        dataBase.get(x, z).thenAccept(packet -> minecraft.execute(() -> {
            if (!isInClientView(x, z)) return;
            connection.handleLevelChunkWithLight(packet);
            SUBMITTED_FAKE_CHUNKS.add(new int[]{x, z});
        }));
    }

    private void forgetChunk(int x, int z){
        var connection = minecraft.getConnection();
        if (connection != null) {
            connection.handleForgetLevelChunk(new WrappedForgetChunkPacket(x, z));
        }
    }

    private boolean isInClientView(int x, int z){
        return Math.abs(x- centralZ) <= Config.getRenderDistance() && Math.abs(z- centralX) <= Config.getRenderDistance();
    }

    public int getRealChunkRadius(){
        if (realChunkRadius == -1) throw new RuntimeException("Getting real chunk radius when not started");
        return realChunkRadius;
    }

    public void onServerRadiusChange(int radius){
        this.realChunkRadius = radius;
    }

    public void onDimensionChange(String dimension){
        if (!isStarted) return;
        try {
//            SUBMITTED_CHUNKS.clear();
            SUBMITTED_FAKE_CHUNKS.clear();
            dataBase.switchDimension(dimension);
        }catch (Exception e){
            LOGGER.error("Failed to switch dimension", e);
        }
    }

    @SubscribeEvent
    public static void onConnected(ClientPlayerNetworkEvent.LoggingIn event){
        if (!Config.isEnable()) return;
        INSTANCE.start();
    }

    @SubscribeEvent
    public static void onDisconnected(ClientPlayerNetworkEvent.LoggingOut event){
        if (!Config.isEnable()) return;
        INSTANCE.stop();
        INSTANCE.SUBMITTED_FAKE_CHUNKS.clear();
        INSTANCE.realChunkRadius = -1;
    }
}
