package me.linstar.afar.mixin;

import me.linstar.afar.Afar;
import me.linstar.afar.Config;
import me.linstar.afar.screen.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Component p_96550_) {
        super(p_96550_);
    }

    @Inject(at = @At("HEAD"), method = "init")
    private void lodconfig$init(CallbackInfo ci) {
        this.addRenderableWidget(
                new ImageButton(
                        this.width / 2 - 180,
                        this.height / 6 -36,
                        20, 20,
                        0, 0,
                        20, Afar.ICON_TEXTURE,
                        20, 40,
                        button -> Minecraft.getInstance().setScreen(ConfigScreen.get(this))
                )
        );
    }

}
