package me.linstar.afar.mixin;

import me.linstar.afar.Config;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Options.class)
public abstract class OptionsMixin {

    @Shadow @Final private OptionInstance<Integer> renderDistance;

    @Inject(method = "getEffectiveRenderDistance", at = @At("HEAD"), cancellable = true)
    private void getEffectiveRenderDistance(CallbackInfoReturnable<Integer> info) {
        if (Config.isEnable()){
            info.setReturnValue(this.renderDistance.get());
        }
    }
}
