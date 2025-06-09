package me.linstar.afar.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ClientboundLevelChunkPacketData.class)
public interface LevelChunkPacketDataAccessor {
    @Accessor("blockEntitiesData")
    List<?> getBlockEntitiesData();
}
