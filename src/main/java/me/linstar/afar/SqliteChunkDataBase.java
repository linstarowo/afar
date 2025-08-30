package me.linstar.afar;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import me.linstar.afar.network.WrappedChunkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import org.slf4j.Logger;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteOpenMode;

import java.io.File;
import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SqliteChunkDataBase {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Connection connection;
    private final ScheduledExecutorService scheduler;

    private final PreparedStatement insertStatement;
    private final PreparedStatement queryStatement;

    volatile boolean shutdown = false;

    SqliteChunkDataBase(File dataBaseFile) throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");

        var source = new SQLiteDataSource();
        var config = new SQLiteConfig();

        config.setOpenMode(SQLiteOpenMode.READWRITE);
        config.setCacheSize(-64000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.OFF);

        source.setConfig(new SQLiteConfig());
        source.setUrl("jdbc:sqlite:" + dataBaseFile.toString().replace("\\", "/"));

        LOGGER.debug("created database in {}", source.getUrl());

        connection = source.getConnection();
        connection.setAutoCommit(false);

        //检测是否创建数据表
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS data_table (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "x INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, " +
                    "chunk_data BLOB NOT NULL, " +
                    "CONSTRAINT uc_pos UNIQUE (x, z))"
            );
            connection.commit();
        }

        //指令预加载
        insertStatement = connection.prepareStatement("INSERT INTO data_table (x, z, chunk_data) VALUES (?, ?, ?) ON CONFLICT DO UPDATE SET chunk_data=?;");
        queryStatement = connection.prepareStatement("SELECT chunk_data FROM data_table WHERE x=? AND z=?");

        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void tick() throws SQLException{
        if (shutdown) {
            insertStatement.close();
            queryStatement.close();
            connection.commit();
            connection.close();
            return;
        }

        var instance = ChunkCachingManager.get();
        var needToSave = instance.receivedChunkDataQueue;
        var needToLoad = instance.chunkLoadQueue;
        var loadedChunks = instance.loadedChunks;
        var minecraft = instance.minecraft;

        int count = 0;
        for (int i = 0; i < needToSave.size(); i++) {
            var data = needToSave.poll();
            if (data == null) break;

            var ba = data.buf().array();
            insertStatement.setInt(1, data.x());
            insertStatement.setInt(2, data.z());
            insertStatement.setBytes(3, ba);
            insertStatement.setBytes(4, ba);
            insertStatement.addBatch();
            count ++;
        }
        var time = System.currentTimeMillis();
        insertStatement.executeBatch();
        connection.commit();
        if (count != 0) LOGGER.debug("saved {} chunks in {} ms", count, System.currentTimeMillis() - time);

        for (int i = 0; i < needToLoad.size(); i++) {
            var pos = needToLoad.poll();
            queryStatement.setInt(1, pos.x);
            queryStatement.setInt(2, pos.z);
            try (ResultSet rs = queryStatement.executeQuery()) {
                if (!rs.next()) continue;
                WrappedChunkPacket packet = new WrappedChunkPacket(new FriendlyByteBuf(Unpooled.wrappedBuffer(rs.getBytes("chunk_data"))));
                minecraft.executeIfPossible(() ->{
                    var connection = minecraft.getConnection();
                    var level = minecraft.level;
                    if (connection == null || level == null) return;

                    if(!(level.getChunk(pos.x, pos.z) instanceof EmptyLevelChunk)) return;
                    connection.handleLevelChunkWithLight(packet);
                    loadedChunks.add(new ChunkPos(packet.getX(), packet.getZ()));
                });

            }
        }
    }

    public void start(){
        scheduler.scheduleAtFixedRate(() -> {
            try{
                this.tick();
            }catch (Exception e){
                LOGGER.error("Error when executing task");
            }
        }, 0, 100, TimeUnit.MICROSECONDS);
    }

    public void close(){
        try{
            this.shutdown = true;
            scheduler.shutdown();
        }catch(Exception e){
            LOGGER.error("Error when close database", e);
        }
    }

    public static SqliteChunkDataBase create(File dataFile) {
        if (!dataFile.exists()) dataFile.getParentFile().mkdirs();

        try{
            return new SqliteChunkDataBase(dataFile);
        }catch (Exception e){
            LOGGER.error("Error when init database", e);
        }
        return null;
    }

}
