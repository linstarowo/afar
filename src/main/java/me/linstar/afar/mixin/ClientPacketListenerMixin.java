package me.linstar.afar.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.linstar.afar.ChunkCacheManager;
import me.linstar.afar.Config;
import me.linstar.afar.network.WrappedPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 800)
public class ClientPacketListenerMixin {

    @WrapOperation(method = "handleSetChunkCacheRadius", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundSetChunkCacheRadiusPacket;getRadius()I"))
    public int modifyRadius(ClientboundSetChunkCacheRadiusPacket instance, Operation<Integer> original){
        int radius = instance.getRadius();
        if (Config.isEnable()) {
            if (!(instance instanceof WrappedPacket)) {
                ChunkCacheManager.INSTANCE.onServerRadiusChange(radius);
            }
            return Config.getRenderDistance();
        }
        return radius;
    }

    @WrapOperation(method = "handleLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;chunkRadius()I"))
    public int modifyRadius(ClientboundLoginPacket instance, Operation<Integer> original){
        int radius = instance.chunkRadius();

        if (Config.isEnable()){
            ChunkCacheManager.INSTANCE.onServerRadiusChange(radius);
            return Config.getRenderDistance();
        }
        return radius;
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("HEAD"), cancellable = true)
    public void onChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo info){
        if (!Config.isEnable()) return;
        if (packet instanceof WrappedPacket) return;
//        ChunkCacheManager.INSTANCE.onServerForgetChunk(packet.getX(), packet.getZ());
        info.cancel();
    }

    @Inject(method = "handleSetChunkCacheCenter", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;updateViewCenter(II)V"))
    public void onSetCenter(ClientboundSetChunkCacheCenterPacket packet, CallbackInfo ci){
        ChunkCacheManager.INSTANCE.onViewCenterPacket(packet.getX(), packet.getZ());
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;updateLevelChunk(IILnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;)V"))
    public void onLevelChunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci){
        if (packet instanceof WrappedPacket) return;
        ChunkCacheManager.INSTANCE.onChunkReceived(packet);
    }

    @Inject(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V"))
    public void onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci){
        ChunkCacheManager.INSTANCE.onDimensionChange(packet.getDimension().location().toString().replace(":", "_"));
    }
}
