package me.linstar.afar.command;

import com.mojang.brigadier.CommandDispatcher;
import me.linstar.afar.ChunkCachingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AfarClientCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("afar")
                .then(Commands.literal("show_database_info").executes(context -> {
                    showDatabaseInfo(context.getSource());
                    return 0;
                }))
                .then(Commands.literal("run_database_vacuum").executes(context -> {
                    executeVacuum(context.getSource());
                    return 0;
                }))
        );
    }

    public static void showDatabaseInfo(CommandSourceStack source){
        var instance = ChunkCachingManager.get();
        if(!instance.getStatue().equals(ChunkCachingManager.Statue.RUNNING)){
            source.sendSystemMessage(Component.translatable("afar.command.check_running_statue").withStyle(ChatFormatting.RED));
            return;
        }

        var database = instance.getDatabase();
        int chunkCount = database.getChunkCount();
        int dataSize = database.getFileSize();
        source.sendSystemMessage(Component.translatable("afar.command.database_statistic").withStyle(ChatFormatting.GOLD));
        source.sendSystemMessage(Component.translatable("afar.command.saved_chunks").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(chunkCount)).withStyle(ChatFormatting.GREEN)));
        source.sendSystemMessage(Component.translatable("afar.command.database_size").withStyle(ChatFormatting.GRAY).append(Component.literal(dataSize + "MB").withStyle(ChatFormatting.GREEN)));
    }

    public static void executeVacuum(CommandSourceStack source){
        var instance = ChunkCachingManager.get();
        if(!instance.getStatue().equals(ChunkCachingManager.Statue.RUNNING)){
            source.sendSystemMessage(Component.translatable("afar.command.check_running_statue").withStyle(ChatFormatting.RED));
            return;
        }
        source.sendSystemMessage(Component.translatable("afar.command.prepare_vacuum").withStyle(ChatFormatting.GRAY));
        var database = instance.getDatabase();
        database.runVacuum();
        source.sendSystemMessage(Component.translatable("afar.command.vacuum_finished").withStyle(ChatFormatting.GRAY));
    }
}
