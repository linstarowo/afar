package me.linstar.afar;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Afar.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLE = BUILDER.comment("Enable mod").define("enable", true);
//    private static final ForgeConfigSpec.ConfigValue<Integer> CHUNK_RADIUS = BUILDER.comment("The maximum value of the locally cached render distance").define("chunk_radius", 32);
    private static final ForgeConfigSpec.ConfigValue<Integer> RENDER_DISTANCE = BUILDER.comment("The view distance of fake chunks").define("render_distance", 8);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    static boolean enable = false;
//    static int currentChunkRadius = 32;
    static int renderDistance = 8;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        enable = ENABLE.get();
//        chunkRadius = CHUNK_RADIUS.get();
        renderDistance = RENDER_DISTANCE.get();
    }

    public static void save(){
        ENABLE.set(enable);
//        CHUNK_RADIUS.set(chunkRadius);
        RENDER_DISTANCE.set(renderDistance);

        ENABLE.save();
//        CHUNK_RADIUS.save();
        RENDER_DISTANCE.save();
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
}
