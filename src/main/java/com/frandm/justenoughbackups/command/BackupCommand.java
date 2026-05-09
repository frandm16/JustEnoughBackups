package com.frandm.justenoughbackups.command;

import com.frandm.justenoughbackups.WorldBackupMod;
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
                                .executes(context -> create(context.getSource(), BackupConfig.get().backupMode)))
                        .then(Commands.literal("create")
                                .then(Commands.literal("full")
                                        .executes(context -> create(context.getSource(), BackupType.FULL)))
                                .then(Commands.literal("partial")
                                        .executes(context -> create(context.getSource(), BackupType.PARTIAL)))
                                .then(Commands.literal("differential")
                                        .executes(context -> create(context.getSource(), BackupType.DIFFERENTIAL))))
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

    private static int create(CommandSourceStack source, BackupType type) {
        source.sendSuccess(
                () -> Component.literal("Just Enough Backups: " + type.commandName() + " backup started."),
                false
        );

        BackupService.createBackup(source.getServer(), type, "manual")
                .thenAccept(manifest -> source.getServer().execute(() -> source.sendSuccess(
                        () -> Component.literal("Just Enough Backups: backup created: " + manifest.id),
                        false
                )))
                .exceptionally(exception -> {
                    WorldBackupMod.LOGGER.error("Manual backup failed.", exception);
                    source.getServer().execute(() ->
                            source.sendFailure(Component.literal("Just Enough Backups: backup failed. Check server logs."))
                    );
                    return null;
                });

        return 1;
    }

    private static int list(CommandSourceStack source) {
        try {
            List<BackupManifest> backups = BackupService.listBackups(source.getServer());
            if (backups.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Just Enough Backups: no backups found."), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal("Just Enough Backups: " + backups.size() + " backup(s):"), false);
            backups.stream()
                    .forEach(manifest -> source.sendSuccess(
                            () -> Component.literal(formatBackup(manifest)),
                            false
                    ));
            return backups.size();
        } catch (IOException exception) {
            WorldBackupMod.LOGGER.error("Failed to list backups.", exception);
            source.sendFailure(Component.literal("Just Enough Backups: failed to list backups."));
            return 0;
        }
    }

    private static int next(CommandSourceStack source) {
        NextBackupStatus status = BackupScheduler.nextBackupStatus();
        if (!status.enabled()) {
            source.sendSuccess(
                    () -> Component.literal("Just Enough Backups: automatic backups are disabled."),
                    false
            );
            return 1;
        }

        if (status.ready()) {
            source.sendSuccess(
                    () -> Component.literal("Just Enough Backups: the next automatic backup is ready and will start on the next scheduler check."),
                    false
            );
            return 1;
        }

        source.sendSuccess(
                () -> Component.literal("Just Enough Backups: next automatic backup in " + formatDuration(status.remainingMillis()) + "."),
                false
        );
        return 1;
    }

    private static int restore(CommandSourceStack source, String backupId) {
        source.sendSuccess(
                () -> Component.literal("Just Enough Backups: restore started for " + backupId + "."),
                true
        );

        BackupService.restoreBackup(source.getServer(), backupId)
                .thenAccept(restore -> source.getServer().execute(() -> {
                    source.sendSuccess(
                            () -> Component.literal("Just Enough Backups: restore prepared for " + restore.backupId()
                                    + ". Stopping server now; restart it to load the restored world."),
                            true
                    );
                    source.getServer().halt(false);
                }))
                .exceptionally(exception -> {
                    WorldBackupMod.LOGGER.error("Restore failed.", exception);
                    source.getServer().execute(() ->
                            source.sendFailure(Component.literal("Just Enough Backups: restore failed: " + rootMessage(exception)))
                    );
                    return null;
                });
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        BackupConfig config = BackupService.reloadConfig();
        source.sendSuccess(
                () -> Component.literal("Just Enough Backups: config reloaded. Mode=" + config.backupMode
                        + ", automatic=" + config.automaticBackupsEnabled
                        + ", interval=" + config.automaticIntervalMinutes + "m"
                        + ", permissionLevel=" + config.commandPermissionLevel),
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

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
