package com.frandm.justenoughbackups.command;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.BackupMessages;
import com.frandm.justenoughbackups.backup.BackupPermissions;
import com.frandm.justenoughbackups.backup.BackupService;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.frandm.justenoughbackups.scheduler.BackupScheduler;
import com.frandm.justenoughbackups.scheduler.NextBackupStatus;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

public final class BackupCommand {
    private BackupCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("jeb")
                        .requires(BackupCommand::hasConfiguredPermission)
                        .then(Commands.literal("now")
                                .executes(context -> create(context.getSource(), BackupConfig.get().backupMode, ""))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> create(context.getSource(), BackupConfig.get().backupMode, StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("create")
                                .then(Commands.literal("full")
                                        .executes(context -> create(context.getSource(), BackupType.FULL, ""))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> create(context.getSource(), BackupType.FULL, StringArgumentType.getString(context, "name")))))
                                .then(Commands.literal("partial")
                                        .executes(context -> create(context.getSource(), BackupType.PARTIAL, ""))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> create(context.getSource(), BackupType.PARTIAL, StringArgumentType.getString(context, "name")))))
                                .then(Commands.literal("differential")
                                        .executes(context -> create(context.getSource(), BackupType.DIFFERENTIAL, ""))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> create(context.getSource(), BackupType.DIFFERENTIAL, StringArgumentType.getString(context, "name"))))))
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("next")
                                .executes(context -> next(context.getSource())))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("backupId", StringArgumentType.word())
                                        .executes(context -> restore(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "backupId")
                                        ))))
                        .then(Commands.literal("config")
                                .then(Commands.literal("reload")
                                        .executes(context -> reloadConfig(context.getSource()))))));
    }

    private static boolean hasConfiguredPermission(CommandSourceStack source) {
        return BackupPermissions.hasConfiguredPermission(source);
    }

    private static int create(CommandSourceStack source, BackupType type, String requestedName) {
        BackupService.createBackup(source.getServer(), type, "manual", requestedName)
                .thenAccept(manifest -> {
                })
                .exceptionally(exception -> {
                    WorldBackupMod.LOGGER.error("Manual backup failed.", exception);
                    return null;
                });

        return 1;
    }

    private static int list(CommandSourceStack source) {
        try {
            List<BackupManifest> backups = BackupService.listBackups(source.getServer());
            if (backups.isEmpty()) {
                source.sendSuccess(() -> BackupMessages.withTitle(Component.translatable("message.justenoughbackups.no_backups")), false);
                return 1;
            }

            source.sendSuccess(() -> BackupMessages.withTitle(Component.translatable("message.justenoughbackups.backup_count", backups.size())), false);
            backups.stream()
                    .forEach(manifest -> source.sendSuccess(
                            () -> formatBackup(manifest),
                            false
                    ));
            return backups.size();
        } catch (IOException exception) {
            WorldBackupMod.LOGGER.error("Failed to list backups.", exception);
            source.sendFailure(BackupMessages.withTitle(Component.translatable("message.justenoughbackups.list_failed")));
            return 0;
        }
    }

    private static int next(CommandSourceStack source) {
        NextBackupStatus status = BackupScheduler.nextBackupStatus();
        if (!status.enabled()) {
            source.sendSuccess(
                    () -> BackupMessages.withTitle(Component.translatable("message.justenoughbackups.next_disabled")),
                    false
            );
            return 1;
        }

        if (status.ready()) {
            source.sendSuccess(
                    () -> BackupMessages.withTitle(Component.translatable("message.justenoughbackups.next_ready")),
                    false
            );
            return 1;
        }

        source.sendSuccess(
                () -> BackupMessages.withTitle(Component.translatable("message.justenoughbackups.next_waiting", formatDuration(status.remainingMillis()))),
                false
        );
        return 1;
    }

    private static int restore(CommandSourceStack source, String backupId) {
        BackupService.restoreBackup(source.getServer(), backupId)
                .thenAccept(restore -> source.getServer().execute(() -> {
                    source.getServer().halt(false);
                }))
                .exceptionally(exception -> {
                    WorldBackupMod.LOGGER.error("Restore failed.", exception);
                    return null;
                });
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        BackupConfig config = BackupService.reloadConfig();
        source.sendSuccess(
                () -> BackupMessages.withTitle(Component.translatable("message.justenoughbackups.config_reloaded",
                        config.backupMode,
                        config.automaticBackupsEnabled,
                        config.automaticIntervalMinutes,
                        config.commandPermissionLevel,
                        config.messageChannel)),
                false
        );
        return 1;
    }

    private static Component formatBackup(BackupManifest manifest) {
        String base = manifest.baseBackupId == null ? "none" : manifest.baseBackupId;
        return BackupMessages.withTitle(Component.translatable("message.justenoughbackups.backup_entry",
                manifest.id,
                manifest.type.commandName(),
                manifest.includedFiles.size(),
                base,
                manifest.createdAt));
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0L) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

}
