package me.linstar.afar;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import me.linstar.afar.config.Config;
import me.linstar.afar.network.WrappedChunkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import org.slf4j.Logger;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteOpenMode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SqliteChunkDataBase {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SQLiteConnection connection;
    private final ScheduledExecutorService scheduler;

    private final PreparedStatement insertStatement;
    private final PreparedStatement queryStatement;
    private final SQLiteDataSource source = new SQLiteDataSource();;
    private final ReentrantLock shutdownLock = new ReentrantLock();
    private final File dataFile;

    int chunkSavingCount = 0;


    SqliteChunkDataBase(File dataBaseFile) throws SQLException {
        var config = new SQLiteConfig();

        config.setOpenMode(SQLiteOpenMode.READWRITE);
        config.setCacheSize(-64000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.OFF);
        config.setPageSize(1024);

        source.setConfig(new SQLiteConfig());
        source.setUrl("jdbc:sqlite:" + dataBaseFile.toString().replace("\\", "/"));
        dataFile = dataBaseFile;

        LOGGER.debug("created database in {}", source.getUrl());

        connection = source.getConnection(null, null);
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
            stmt.execute("CREATE INDEX IF NOT EXISTS uc_pos_index ON data_table(x, z)");
            connection.commit();
        }

        //指令预加载
        insertStatement = connection.prepareStatement("INSERT INTO data_table (x, z, chunk_data) VALUES (?, ?, ?) ON CONFLICT DO UPDATE SET chunk_data=?;");
        queryStatement = connection.prepareStatement("SELECT chunk_data FROM data_table WHERE x=? AND z=?");

        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void tick() throws SQLException{
        if (!shutdownLock.isLocked()) shutdownLock.lock();

        var instance = ChunkCachingManager.get();

        if (instance.currentStatue.get() != ChunkCachingManager.Statue.RUNNING){
            if (connection.isClosed()) return;
            insertStatement.close();
            queryStatement.close();
            connection.commit();
            try(Statement stmt = connection.createStatement()){
                stmt.execute("PRAGMA wal_checkpoint(FULL);");
            }
            connection.close();
            shutdownLock.unlock();
            return;
        };

        var needToSave = instance.receivedChunkDataQueue;
        var needToLoad = instance.chunkLoadQueue;
        var loadedChunks = instance.loadedChunks;
        var minecraft = instance.minecraft;

        int count = 0;
        for (int i = 0; i < Config.getMaxChunkSavingPerTick(); i++) {
            var data = needToSave.poll();
            if (data == null) break;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try(GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(data.buf().array());
            } catch (IOException e) {
                LOGGER.error("Failed to write chunk data", e);
                continue;
            }
            var ba = out.toByteArray();
            insertStatement.setInt(1, data.x());
            insertStatement.setInt(2, data.z());
            insertStatement.setBytes(3, ba);
            insertStatement.setBytes(4, ba);
            insertStatement.addBatch();
            count ++;
        }
        insertStatement.executeBatch();

        chunkSavingCount += count;
        if (chunkSavingCount >= Config.getSavingChunkThreshold()){
            var time = System.currentTimeMillis();
            connection.commit();
            LOGGER.debug("saved {} chunks in {} ms", chunkSavingCount, System.currentTimeMillis() - time);
            chunkSavingCount = 0;
        }

        var time = System.currentTimeMillis();
        int loadCount = 0;
        for (int i = 0; i < Config.getMaxChunkLoadingPerTick(); i++) {
            var pos = needToLoad.poll();
            if (pos == null) break;

            queryStatement.setInt(1, pos.x);
            queryStatement.setInt(2, pos.z);
            try (ResultSet rs = queryStatement.executeQuery()) {
                if (!rs.next()) continue;

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ByteArrayInputStream in = new ByteArrayInputStream(rs.getBytes("chunk_data"));

                try(GZIPInputStream unzip = new GZIPInputStream(in)) {
                    byte[] buffer = new byte[256];
                    int n;
                    while ((n = unzip.read(buffer)) >= 0) {
                        out.write(buffer, 0, n);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to read chunk data", e);
                }

                WrappedChunkPacket packet = new WrappedChunkPacket(new FriendlyByteBuf(Unpooled.wrappedBuffer(out.toByteArray())));
                minecraft.executeIfPossible(() ->{
                    var connection = minecraft.getConnection();
                    var level = minecraft.level;
                    if (connection == null || level == null) return;

                    if(!(level.getChunk(pos.x, pos.z) instanceof EmptyLevelChunk)) return;
                    connection.handleLevelChunkWithLight(packet);
                    loadedChunks.add(new ChunkPos(packet.getX(), packet.getZ()));
                });
                loadCount++;
            }
        }
        if (loadCount != 0) LOGGER.debug("loaded {} chunks in {} ms", loadCount, System.currentTimeMillis() - time);
    }

    public void start(){
        scheduler.scheduleAtFixedRate(() -> {
            try{
                this.tick();
            }catch (Exception e){
                LOGGER.error("Error when executing task", e);
            }
        }, 0, 100, TimeUnit.MICROSECONDS);
    }

    public void close(){
        try{
            boolean result = shutdownLock.tryLock(1, TimeUnit.SECONDS);
            assert result;
            scheduler.shutdown();
        }catch(Exception e){
            LOGGER.error("Error when close database", e);
        }
    }

    public void runVacuum(){
        try(Statement stmt = connection.createStatement()){
            connection.setAutoCommit(true);
            stmt.executeUpdate("VACUUM");
            connection.setAutoCommit(false);
        }catch (Exception e){
            LOGGER.error("Error when vacuum database", e);
        }
    }

    public int getFileSize(){
        try {
            return (int) (dataFile.length() / 1024 / 1024);
        }catch (Exception e){
            LOGGER.error("Error when getting file size", e);
        }
        return 0;
    }

    public int getChunkCount(){
        try(Statement stmt = connection.createStatement()){
            ResultSet resultSet = stmt.executeQuery("SELECT COUNT(*) FROM data_table");
            if (resultSet.next()) return resultSet.getInt(1);
        }catch (Exception e){
            LOGGER.error("Error when getting chunk count", e);
        }

        return 0;
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
