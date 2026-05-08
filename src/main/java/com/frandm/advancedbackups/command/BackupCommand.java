package com.frandm.advancedbackups.command;

import com.frandm.advancedbackups.BackupConfig;
import com.frandm.advancedbackups.BackupManager;
import com.frandm.advancedbackups.BackupManifest;
import com.frandm.advancedbackups.BackupType;
import com.frandm.advancedbackups.WorldBackupMod;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.io.IOException;
import java.util.List;

public final class BackupCommand {
    private BackupCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("advancedbackups")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("now")
                                .executes(context -> create(context.getSource(), BackupConfig.get().backupMode)))
                        .then(Commands.literal("create")
                                .then(Commands.literal("full")
                                        .executes(context -> create(context.getSource(), BackupType.FULL_BACKUPS)))
                                .then(Commands.literal("incremental")
                                        .executes(context -> create(context.getSource(), BackupType.INCREMENTAL)))
                                .then(Commands.literal("differential")
                                        .executes(context -> create(context.getSource(), BackupType.DIFFERENTIAL))))
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("backupId", StringArgumentType.word())
                                        .then(Commands.literal("confirm")
                                                .executes(context -> restore(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "backupId")
                                                )))))
                        .then(Commands.literal("config")
                                .then(Commands.literal("reload")
                                        .executes(context -> reloadConfig(context.getSource()))))));
    }

    private static int create(CommandSourceStack source, BackupType type) {
        source.sendSuccess(
                () -> Component.literal("Advanced Backups: " + type.commandName() + " backup started."),
                false
        );

        BackupManager.createBackup(source.getServer(), type, "manual")
                .thenAccept(manifest -> source.getServer().execute(() -> source.sendSuccess(
                        () -> Component.literal("Advanced Backups: backup created: " + manifest.id),
                        false
                )))
                .exceptionally(exception -> {
                    WorldBackupMod.LOGGER.error("Manual backup failed.", exception);
                    source.getServer().execute(() ->
                            source.sendFailure(Component.literal("Advanced Backups: backup failed. Check server logs."))
                    );
                    return null;
                });

        return 1;
    }

    private static int list(CommandSourceStack source) {
        try {
            List<BackupManifest> backups = BackupManager.listBackups(source.getServer());
            if (backups.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Advanced Backups: no backups found."), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal("Advanced Backups: " + backups.size() + " backup(s):"), false);
            backups.stream()
                    .forEach(manifest -> source.sendSuccess(
                            () -> Component.literal(formatBackup(manifest)),
                            false
                    ));
            return backups.size();
        } catch (IOException exception) {
            WorldBackupMod.LOGGER.error("Failed to list backups.", exception);
            source.sendFailure(Component.literal("Advanced Backups: failed to list backups."));
            return 0;
        }
    }

    private static int restore(CommandSourceStack source, String backupId) {
        source.sendSuccess(
                () -> Component.literal("Advanced Backups: restore started for " + backupId + "."),
                true
        );

        BackupManager.restoreBackup(source.getServer(), backupId)
                .thenAccept(restore -> source.getServer().execute(() -> {
                    source.sendSuccess(
                            () -> Component.literal("Advanced Backups: restore prepared for " + restore.backupId()
                                    + ". Stopping server now; restart it to load the restored world."),
                            true
                    );
                    source.getServer().halt(false);
                }))
                .exceptionally(exception -> {
                    WorldBackupMod.LOGGER.error("Restore failed.", exception);
                    source.getServer().execute(() ->
                            source.sendFailure(Component.literal("Advanced Backups: restore failed: " + rootMessage(exception)))
                    );
                    return null;
                });
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        BackupConfig config = BackupManager.reloadConfig();
        source.sendSuccess(
                () -> Component.literal("Advanced Backups: config reloaded. Mode=" + config.backupMode
                        + ", automatic=" + config.automaticBackupsEnabled
                        + ", interval=" + config.automaticIntervalMinutes + "m"),
                false
        );
        return 1;
    }

    private static String formatBackup(BackupManifest manifest) {
        String base = manifest.baseBackupId == null ? "none" : manifest.baseBackupId;
        return "- " + manifest.id
                + " [" + manifest.type.commandName() + "]"
                + " files=" + manifest.includedFiles.size()
                + " base=" + base
                + " at=" + manifest.createdAt;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
