package me.linstar.afar.config;

import me.linstar.afar.Afar;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Afar.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLE = BUILDER.comment("Enable mod").define("enable", true);
    private static final ForgeConfigSpec.ConfigValue<Integer> RENDER_DISTANCE = BUILDER.comment("The view distance of fake chunks").define("render_distance", 8);
    private static final ForgeConfigSpec.ConfigValue<Integer> MAX_CHUNK_LOADING_PER_TICK = BUILDER.comment("The maximum count of fake chunks loading per tick").define("max_chunk_loading_per_tick", 8);
    private static final ForgeConfigSpec.ConfigValue<Integer> MAX_CHUNK_SAVING_PER_TICK = BUILDER.comment("The maximum count of fake chunks saving per tick").define("max_chunk_saving_per_tick", 8);
    private static final ForgeConfigSpec.ConfigValue<Integer> SAVING_CHUNK_THRESHOLD = BUILDER.comment("The threshold that triggers database commit").define("saving_chunk_threshold", 8);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    volatile static boolean enable = false;
    volatile static int renderDistance = 8;
    volatile static int maxChunkLoadingPerTick = 8;
    volatile static int savingChunkThreshold = 8;
    volatile static int maxChunkSavingPerTick = 8;

    static boolean debug = false;  //never save


    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        enable = ENABLE.get();
        renderDistance = RENDER_DISTANCE.get();
        maxChunkLoadingPerTick = MAX_CHUNK_LOADING_PER_TICK.get();
        maxChunkSavingPerTick = MAX_CHUNK_SAVING_PER_TICK.get();
        savingChunkThreshold = SAVING_CHUNK_THRESHOLD.get();
    }

    public static void save(){
        ENABLE.set(enable);
        RENDER_DISTANCE.set(renderDistance);
        MAX_CHUNK_LOADING_PER_TICK.set(maxChunkLoadingPerTick);
        MAX_CHUNK_SAVING_PER_TICK.set(maxChunkSavingPerTick);
        SAVING_CHUNK_THRESHOLD.set(savingChunkThreshold);

        ENABLE.save();
        RENDER_DISTANCE.save();
        MAX_CHUNK_LOADING_PER_TICK.save();
        SAVING_CHUNK_THRESHOLD.save();
    }

    public static boolean isEnable(){
        return enable && !Minecraft.getInstance().isLocalServer();
    }

    public static int getRenderDistance(){
        return renderDistance;
    }

    public static void setEnabled(final boolean enabled){
        Config.enable = enabled;
    }

    public static void setRenderDistance(int renderDistance) {
        Config.renderDistance = renderDistance;
    }

    public static boolean isDebug(){
        return debug;
    }

    public static void setDebug(boolean debug){
        Config.debug = debug;
    }

    public static int getMaxChunkLoadingPerTick(){
        return maxChunkLoadingPerTick;
    }

    public static void setMaxChunkLoadingPerTick(int maxChunkLoadingPerTick){
        Config.maxChunkLoadingPerTick = maxChunkLoadingPerTick;
    }

    public static int getMaxChunkSavingPerTick() {
        return maxChunkSavingPerTick;
    }

    public static void setMaxChunkSavingPerTick(int maxChunkSavingPerTick) {
        Config.maxChunkSavingPerTick = maxChunkSavingPerTick;
    }

    public static int getSavingChunkThreshold() {
        return savingChunkThreshold;
    }

    public static void setSavingChunkThreshold(int savingChunkThreshold) {
        Config.savingChunkThreshold = savingChunkThreshold;
    }
}
