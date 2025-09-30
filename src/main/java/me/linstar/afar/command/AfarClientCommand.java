package me.linstar.afar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.linstar.afar.ChunkCachingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AfarClientCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("afar")
                .then(Commands.literal("dataInfo").executes(context -> {
                    showDatabaseInfo(context.getSource());
                    return 0;
                }))
                .then(Commands.literal("runVacuum").executes(context -> {
                    executeVacuum(context.getSource());
                    return 0;
                }))
                .then(Commands.literal("doDrop").executes(context -> {
                    executeDrop(context.getSource());
                    return 0;
                }))
                .then(Commands.literal("deleteOld").executes(context -> {
                    context.getSource().sendSystemMessage(Component.translatable("afar.command.delete_command_info"));
                    return 0;
                }).then(Commands.argument("value", FloatArgumentType.floatArg(0.01F)).executes(context -> {
                    executeDelete(context.getSource(), FloatArgumentType.getFloat(context, "value"));
                    return 0;
                })))
        );
    }

    public static void executeDelete(CommandSourceStack source, float time){
        var instance = ChunkCachingManager.get();
        if(!instance.getStatue().equals(ChunkCachingManager.Statue.RUNNING)){
            source.sendSystemMessage(Component.translatable("afar.command.check_running_statue").withStyle(ChatFormatting.RED));
            return;
        }

        var database = instance.getDatabase();
        double runningHours = database.getRunningHours();
        if (runningHours <= time){
            source.sendSystemMessage(Component.translatable("afar.command.delete_time_too_big").withStyle(ChatFormatting.RED));
            return;
        }

        int lines = database.runDelete((int) (time * 3600));
        if (lines < 0){
            source.sendSystemMessage(Component.translatable("afar.command.delete_failed").withStyle(ChatFormatting.RED));
        }else {
            source.sendSystemMessage(Component.translatable("afar.command.delete_finish", lines).withStyle(ChatFormatting.GREEN));
        }
    }

    public static void showDatabaseInfo(CommandSourceStack source){
        var instance = ChunkCachingManager.get();
        if(!instance.getStatue().equals(ChunkCachingManager.Statue.RUNNING)){
            source.sendSystemMessage(Component.translatable("afar.command.check_running_statue").withStyle(ChatFormatting.RED));
            return;
        }

        var database = instance.getDatabase();
        var recorder = database.getRecorder();
        int chunkCount = database.getChunkCount();
        int dataSize = database.getFileSize();
        double runningHour = database.getRunningHours();
        source.sendSystemMessage(Component.translatable("afar.command.database_statistic").withStyle(ChatFormatting.GOLD));
        source.sendSystemMessage(Component.translatable("afar.command.saved_chunks").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(chunkCount)).withStyle(ChatFormatting.GREEN)));
        source.sendSystemMessage(Component.translatable("afar.command.database_size").withStyle(ChatFormatting.GRAY).append(Component.literal(dataSize + "MB").withStyle(ChatFormatting.GREEN)));
        source.sendSystemMessage(Component.translatable("afar.command.average_tick_duration").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("%.2fms ".formatted(recorder.get_average_tick_duration())).withStyle(ChatFormatting.GREEN))
                .append(Component.translatable("afar.command.maximum").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("%.2f".formatted(recorder.get_max_tick_duration())).withStyle(ChatFormatting.GREEN)));
        source.sendSystemMessage(Component.translatable("afar.command.average_load_count").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("%.2f ".formatted(recorder.get_average_loads())).withStyle(ChatFormatting.GREEN))
                .append(Component.translatable("afar.command.maximum").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(recorder.get_max_load())).withStyle(ChatFormatting.GREEN)));
        source.sendSystemMessage(Component.translatable("afar.command.running_time").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("%.2fh".formatted(runningHour)).withStyle(ChatFormatting.GREEN)));
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

    public static void executeDrop(CommandSourceStack source){
        var instance = ChunkCachingManager.get();
        if(!instance.getStatue().equals(ChunkCachingManager.Statue.RUNNING)){
            source.sendSystemMessage(Component.translatable("afar.command.check_running_statue").withStyle(ChatFormatting.RED));
            return;
        }
        source.sendSystemMessage(Component.translatable("afar.command.prepare_drop").withStyle(ChatFormatting.GRAY));
        var database = instance.getDatabase();
        database.runDrop();
        source.sendSystemMessage(Component.translatable("afar.command.drop_finished").withStyle(ChatFormatting.GRAY));
    }
}
