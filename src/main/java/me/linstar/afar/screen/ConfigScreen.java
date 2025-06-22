package me.linstar.afar.screen;

import me.linstar.afar.ChunkCacheManager;
import me.linstar.afar.Config;
import me.linstar.afar.network.WrappedSetRadiusPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.client.Options.genericValueLabel;

public class ConfigScreen extends Screen {
    final Screen parent;
    final Minecraft minecraft;

    OptionsList list;
    OptionInstance<Boolean> ENABLE = OptionInstance.createBoolean("afar.config.option.enable", value -> Tooltip.create(Component.translatable("afar.config.option.enable.dic")), Config.isEnable(), value -> {
        Config.setEnabled(value);
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && !value) connection.handleSetChunkCacheRadius(new WrappedSetRadiusPacket(ChunkCacheManager.INSTANCE.getRealChunkRadius()));
        Minecraft.getInstance().levelRenderer.allChanged();
    });
    OptionInstance<Integer> RENDER_DISTANCE = new OptionInstance<>("afar.config.option.render_distance", value -> Tooltip.create(Component.translatable("afar.config.option.render_distance.dic")), (p_231962_, p_268036_) -> genericValueLabel(p_231962_, Component.translatable("options.chunks", p_268036_)), new OptionInstance.IntRange(2, 32), Config.getRenderDistance(), (value) -> {
        Config.setRenderDistance(value);
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.handleSetChunkCacheRadius(new WrappedSetRadiusPacket(value));
        Minecraft.getInstance().levelRenderer.allChanged();
    });

    OptionInstance<Boolean> DEBUG = OptionInstance.createBoolean("afar.config.option.debug", Config.isDebug(), Config::setDebug);

    protected ConfigScreen(Screen parent) {
        super(Component.translatable("afar.config.title"));
        this.parent = parent;
        this.minecraft = Minecraft.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        this.list = new OptionsList(this.minecraft, this.width, this.height, 32, this.height - 32, 25);
        list.addBig(ENABLE);
        list.addBig(RENDER_DISTANCE);
        list.addSmall(new OptionInstance[]{DEBUG});
        this.addWidget(this.list);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int p_281550_, int p_282878_, float p_282465_) {
        this.renderBackground(graphics);
        list.render(graphics, p_281550_, p_282878_, p_282465_);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 16777215);
        super.render(graphics, p_281550_, p_282878_, p_282465_);
    }

    @Override
    public void onClose() {
        super.onClose();
        Config.save();
        Minecraft.getInstance().setScreen(parent);
    }

    public static ConfigScreen get(Screen parent) {
        return new ConfigScreen(parent);
    }
}
