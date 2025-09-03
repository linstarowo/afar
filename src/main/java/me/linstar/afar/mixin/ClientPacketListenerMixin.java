package me.linstar.afar.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.linstar.afar.Afar;
import me.linstar.afar.config.Config;
import me.linstar.afar.event.*;
import me.linstar.afar.network.WrappedPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 800)
public class ClientPacketListenerMixin {

    @Inject(method = "handleCustomPayload", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/NetworkHooks;onCustomPayload(Lnet/minecraftforge/network/ICustomPacket;Lnet/minecraft/network/Connection;)Z", remap = false))
    public void onWorldIdPacket(ClientboundCustomPayloadPacket packet, CallbackInfo info){
        if(packet.getIdentifier().equals(Afar.WORLD_ID_CHANNEL)){
            var id = Afar.parseWorldId(packet.getData().duplicate());
            if (id != null) MinecraftForge.EVENT_BUS.post(new WorldIdEvent(id));
        }
    }

    @WrapOperation(method = "handleSetChunkCacheRadius", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundSetChunkCacheRadiusPacket;getRadius()I"))
    public int modifyRadius(ClientboundSetChunkCacheRadiusPacket instance, Operation<Integer> original){
        int radius = instance.getRadius();
        if (Config.isEnable()) {
            if (!(instance instanceof WrappedPacket)) {
                MinecraftForge.EVENT_BUS.post(new ChunkCacheRadiusEvent(radius));
            }
            return Config.getRenderDistance();
        }
        return radius;
    }

    @WrapOperation(method = "handleLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;chunkRadius()I"))
    public int modifyRadius(ClientboundLoginPacket instance, Operation<Integer> original){
        int radius = instance.chunkRadius();

        MinecraftForge.EVENT_BUS.post(new ChunkCacheRadiusEvent(radius));
        if (Config.isEnable()){
            return Config.getRenderDistance();
        }
        return radius;
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("HEAD"), cancellable = true)
    public void onChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo info){
        if (!Config.isEnable()) return;
        if (packet instanceof WrappedPacket) return;
        info.cancel();
        MinecraftForge.EVENT_BUS.post(new ClientReceivedForgetChunkEvent(packet));
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;updateLevelChunk(IILnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;)V"))
    public void onLevelChunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci){
        if (packet instanceof WrappedPacket) return;
        MinecraftForge.EVENT_BUS.post(new ClientReceivedChunkPacketEvent(packet));
    }

    @Inject(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V"))
    public void onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci){
        MinecraftForge.EVENT_BUS.post(new ClientLevelChangeEvent(packet));
    }
}
