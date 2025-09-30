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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SqliteChunkDataBase {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SQLiteConnection connection;
    private final ScheduledExecutorService scheduler;

    private final PreparedStatement insertStatement;
    private final PreparedStatement queryStatement;
    private final PreparedStatement updateTickStatement;
    private final SQLiteDataSource source = new SQLiteDataSource();
    private final ReentrantLock shutdownLock = new ReentrantLock();
    private final TickRecorder recorder = new TickRecorder();
    private final File dataFile;

    // 执行线程专用变量, 不考虑可见性
    int chunkSavingCount = 0;
    long tick_count = 0L;
    AtomicInteger tick_to_sec = new AtomicInteger(0);

    // Shit code
    public static class TickRecorder{
        public static final int MAX_SIZE = 128;

        final ConcurrentLinkedQueue<Integer> chunk_load_counts = new ConcurrentLinkedQueue<>();
        int load_count;

        final ConcurrentLinkedQueue<Double> tick_load_counts = new ConcurrentLinkedQueue<>();
        int tick_count;

        int max_load_counts = 0;
        double max_tick_duration = 0;

        void update(int loads, double duration){
            if (loads == 0) return;
            if (load_count >= MAX_SIZE) {
                chunk_load_counts.poll();
            }else {
                load_count += 1;
            }
            chunk_load_counts.add(loads);

            if (tick_count > MAX_SIZE) {
                tick_load_counts.poll();
            }else {
                tick_count += 1;
            }
            tick_load_counts.add(duration);

            if (loads > max_load_counts) max_load_counts = loads;
            if (duration > max_tick_duration) max_tick_duration = duration;
        }

        public double get_average_tick_duration(){
            double total = 0;
            for (Double i : tick_load_counts) total += i;
            return total / tick_count;
        }

        public double get_average_loads(){
            double total = 0;
            for (Integer i : chunk_load_counts) total += i;
            return total / load_count;
        }

        public int get_max_load(){
            return this.max_load_counts;
        }

        public double get_max_tick_duration(){
            return this.max_tick_duration;
        }

    }

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
                    "tick_count INTEGER NOT NULL, " +
                    "chunk_data BLOB NOT NULL, " +
                    "CONSTRAINT uc_pos UNIQUE (x, z))"
            );
            stmt.execute("CREATE TABLE IF NOT EXISTS tick_count (" +
                    "id INTEGER PRIMARY KEY, " +
                    "tick_count INTEGER NOT NULL)"
            );

            stmt.execute("CREATE INDEX IF NOT EXISTS uc_pos_index ON data_table(x, z)");
            connection.commit();
        }

        //指令预加载
        insertStatement = connection.prepareStatement("INSERT INTO data_table (x, z, tick_count, chunk_data) VALUES (?, ?, ?, ?) ON CONFLICT DO UPDATE SET chunk_data=?;");
        queryStatement = connection.prepareStatement("SELECT chunk_data FROM data_table WHERE x=? AND z=?");
        updateTickStatement = connection.prepareStatement("INSERT INTO tick_count (id, tick_count) VALUES (1, ?) ON CONFLICT DO UPDATE SET tick_count=?;");

        try(Statement stmt = connection.createStatement(); ResultSet result = stmt.executeQuery("SELECT tick_count FROM tick_count WHERE id=1")) {
            if (result.next()) {
                tick_count = result.getLong("tick_count");
                LOGGER.info("Getting tick count from database: %s".formatted(tick_count));
            }
        }

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
                stmt.execute("PRAGMA wal_checkpoint(FULL);");   //确保日志文件正常保存, 避免重新开启数据库时是锁定
            }
            updateTickStatement.setLong(1, tick_count);
            updateTickStatement.setLong(2, tick_count);
            updateTickStatement.execute();
            updateTickStatement.close();

            connection.commit();
            connection.close();
            shutdownLock.unlock();
            return;
        }

        var startTime = System.currentTimeMillis();
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
            insertStatement.setLong(3, tick_count);
            insertStatement.setBytes(4, ba);
            insertStatement.setBytes(5, ba);
            insertStatement.addBatch();
            count ++;
        }
        insertStatement.executeBatch();

        chunkSavingCount += count;
        if (chunkSavingCount >= Config.getSavingChunkThreshold()){
            connection.commit();
            chunkSavingCount = 0;
        }

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
        this.recorder.update(loadCount, System.currentTimeMillis() - startTime);
    }

    public void start(){
        scheduler.scheduleAtFixedRate(() -> {
            try{
                this.tick();
                if (this.tick_to_sec.incrementAndGet() >= 10) {
                    this.tick_to_sec.set(0);

                    this.tick_count += 1;
                }
            }catch (Exception e){
                LOGGER.error("Error when executing task", e);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
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

    public void runDrop(){
        try(Statement stmt = connection.createStatement()){
            stmt.execute("DELETE FROM data_table");
            connection.commit();
        }catch (Exception e){
            LOGGER.error("Error when dropping database", e);
        }
    }

    public int runDelete(int tc){
        try(Statement stmt = connection.createStatement()){
            int lines = stmt.executeUpdate("DELETE FROM data_table WHERE tick_count <= " + tc);
            connection.commit();
            return lines;
        }catch (Exception e){
            LOGGER.error("Error when deleting data", e);
        }

        return -1;
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

    public TickRecorder getRecorder(){
        return this.recorder;
    }

    public double getRunningHours(){
        return (double) this.tick_count / 3600;
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
