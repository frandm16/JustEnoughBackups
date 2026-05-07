package com.frandm.advancedbackups.command;

import com.frandm.advancedbackups.BackupManager;
import com.frandm.advancedbackups.WorldBackupMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class BackupCommand {
    private BackupCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("advancedbackups")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("now")
                                .executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Advanced Backups: backup started."),
                                            false
                                    );

                                    BackupManager.createManualBackup(context.getSource().getServer())
                                            .exceptionally(exception -> {
                                                WorldBackupMod.LOGGER.error("Manual backup failed.", exception);
                                                return null;
                                            });

                                    return 1;
                                }))));
    }
}
