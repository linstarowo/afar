package me.linstar.afar;

import io.netty.buffer.ByteBuf;
import me.linstar.afar.command.AfarClientCommand;
import me.linstar.afar.config.Config;
import me.linstar.afar.event.ChunkCacheRadiusEvent;
import me.linstar.afar.screen.ConfigScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;

@Mod(Afar.MOD_ID)
public class Afar {
    public static final String MOD_ID = "afar";
    public static final ResourceLocation ICON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/icon.png");
    public static final ConfigScreenHandler.ConfigScreenFactory CONFIG_SCREEN_FACTORY = new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> ConfigScreen.get(screen));
    public static final ResourceLocation WORLD_ID_CHANNEL = new ResourceLocation("worldinfo", "world_id");
    private static int serverRadius = -1;

    public Afar() {
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        ModLoadingContext.get().registerExtensionPoint(CONFIG_SCREEN_FACTORY.getClass(), () -> CONFIG_SCREEN_FACTORY);
    }

    @Nullable
    public static String parseWorldId(ByteBuf buf){
        try {
            buf.readByte();
            int length;
            int b = buf.readUnsignedByte();
            if (b == 42) {
                length = buf.readUnsignedByte();
            } else if (b == 0) {
                return null;
            } else {
                length = b;
            }
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }catch (Exception e){
            return null;
        }
    }

    @SubscribeEvent
    public void onServerRadiusChanged(ChunkCacheRadiusEvent event){
        serverRadius = event.getRadius();
    }

    @SubscribeEvent
    public void onDisconnected(ClientPlayerNetworkEvent.LoggingOut event){
        serverRadius = -1;
    }

    public static int getServerRadius(){
        return serverRadius;
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ModEventListener{
        @SubscribeEvent
        public static void onCommandRegister(final RegisterClientCommandsEvent event) {
            AfarClientCommand.register(event.getDispatcher());
        }
    }
}
