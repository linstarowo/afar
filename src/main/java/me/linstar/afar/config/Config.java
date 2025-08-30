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
    private static final ForgeConfigSpec.ConfigValue<Integer> MAX_CHUNK_LOADING_PER_TICK = BUILDER.comment("The maximum count of fake chunks loading per tick").define("max_chunk_loading_per_tick", 5);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    static boolean enable = false;
    static int renderDistance = 8;
    static int maxChunkLoadingPerTick = 5;

    static boolean debug = false;  //never save


    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        enable = ENABLE.get();
        renderDistance = RENDER_DISTANCE.get();
        maxChunkLoadingPerTick = MAX_CHUNK_LOADING_PER_TICK.get();
    }

    public static void save(){
        ENABLE.set(enable);
        RENDER_DISTANCE.set(renderDistance);
        MAX_CHUNK_LOADING_PER_TICK.set(maxChunkLoadingPerTick);

        ENABLE.save();
        RENDER_DISTANCE.save();
        MAX_CHUNK_LOADING_PER_TICK.save();
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
}
