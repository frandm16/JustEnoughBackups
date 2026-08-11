package com.frandm.justenoughbackups.command;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.BackupMessages;
import com.frandm.justenoughbackups.backup.BackupPermissions;
import com.frandm.justenoughbackups.backup.BackupService;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.frandm.justenoughbackups.scheduler.BackupScheduler;
import com.frandm.justenoughbackups.scheduler.NextBackupStatus;
import com.frandm.justenoughbackups.text.ServerTranslations;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
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
                                .then(Commands.argument("backup", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> suggestBackupNames(context.getSource(), builder))
                                        .executes(context -> restore(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "backup")
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
                sendLocal(source, BackupMessages.withTitle(ServerTranslations.component("message.justenoughbackups.no_backups")));
                return 1;
            }

            sendLocal(source, BackupMessages.withTitle(ServerTranslations.component("message.justenoughbackups.backup_count", String.valueOf(backups.size()))));
            backups.stream()
                    .forEach(manifest -> sendLocal(source, formatBackup(manifest)));
            return backups.size();
        } catch (IOException exception) {
            WorldBackupMod.LOGGER.error("Failed to list backups.", exception);
            sendLocal(source, BackupMessages.withTitle(ServerTranslations.component("message.justenoughbackups.list_failed")));
            return 0;
        }
    }

    private static int next(CommandSourceStack source) {
        List<NextBackupStatus> statuses = BackupScheduler.nextBackupStatus();

        if (statuses.isEmpty()) {
            sendLocal(source, BackupMessages.withTitle(ServerTranslations.component("message.justenoughbackups.next_disabled")));
            return 1;
        }
        for (NextBackupStatus status : statuses) {
            if (status.ready()) {
                sendLocal(source, BackupMessages.withTitle(ServerTranslations.component("message.justenoughbackups.next_ready")));
                return 1;
            } else {
                sendLocal(source, BackupMessages.withTitle(ServerTranslations.component("message.justenoughbackups.next_waiting",
                        status.type().commandName(), formatDuration(status.remainingMillis()))));
            }
        }

        return 1;
    }

    private static int restore(CommandSourceStack source, String backupId) {
        BackupService.restoreBackupByName(source.getServer(), backupId)
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
        BackupConfig.AutomaticSchedule schedule = config.automaticSchedule;
        String scheduleSummary = "full=" + describe(schedule.full)
                + ", differential=" + describe(schedule.differential)
                + ", partial=" + describe(schedule.partial);
        sendLocal(source, BackupMessages.withTitle(ServerTranslations.component("message.justenoughbackups.config_reloaded",
                String.valueOf(config.backupMode),
                String.valueOf(config.commandPermissionLevel),
                String.valueOf(config.messageChannel),
                scheduleSummary)));
        return 1;
    }

    private static String describe(BackupConfig.ScheduledBackup schedule) {
        return (schedule.enabled ? "on" : "off") + "/" + schedule.intervalMinutes + "m";
    }

    private static Component formatBackup(BackupManifest manifest) {
        String base = manifest.baseBackupId == null ? "none" : manifest.baseBackupId;
        return BackupMessages.withTitle(ServerTranslations.component("message.justenoughbackups.backup_entry",
                manifest.id,
                manifest.type.commandName(),
                String.valueOf(manifest.includedFiles.size()),
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

    private static void sendLocal(CommandSourceStack source, Component message) {
        if (source.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(message);
            return;
        }
        source.sendSuccess(() -> message, false);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBackupNames(
            CommandSourceStack source,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        try {
            List<String> suggestions = BackupService.listBackups(source.getServer()).stream()
                    .map(BackupStorage::displayName)
                    .filter(displayName -> !displayName.isBlank())
                    .toList();
            return SharedSuggestionProvider.suggest(suggestions, builder);
        } catch (IOException exception) {
            WorldBackupMod.LOGGER.warn("Failed to suggest backups for restore command.", exception);
            return builder.buildFuture();
        }
    }
}
