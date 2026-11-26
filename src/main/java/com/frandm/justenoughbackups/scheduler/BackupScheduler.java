package com.frandm.justenoughbackups.scheduler;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.BackupMessages;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.BackupService;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.BackupPaths;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;
import com.frandm.justenoughbackups.backup.storage.remote.RemoteBackupCatalog;
import com.frandm.justenoughbackups.backup.model.BackupStatus;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class BackupScheduler {
    private static final long MISSED_CRON_GRACE_MILLIS = 60_000L;
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
    private static long ticksUntilNextCheck = 20L;
    private static final Map<BackupType, Long> lastBackupMillis =
            new ConcurrentHashMap<>();
    private static final Map<BackupType, ScheduledCronSlot> nextCronRuns =
            new ConcurrentHashMap<>();

    private static final List<BackupType> SCHEDULE_PRIORITY = List.of(
            BackupType.FULL,
            BackupType.DIFFERENTIAL,
            BackupType.PARTIAL
    );
    private static volatile boolean playersSeenSinceLastBackup = false;
    private static volatile boolean automaticWarningSent = false;

    private BackupScheduler() {
    }

    public static void register() {
        // started server
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BackupConfig config = BackupConfig.get();
            resetTimer();
            playersSeenSinceLastBackup = hasOnlinePlayers(server);
            if (config.backupOnServerStart) {
                CompletableFuture.supplyAsync(() -> shouldRunStartupBackup(server, config),
                                com.frandm.justenoughbackups.backup.parallel.BackupThreadPool.getExecutor())
                        .thenAccept(shouldRun -> {
                            if (shouldRun && server.isRunning()) {
                                server.execute(() -> {
                                    if (server.isRunning()) {
                                        runLifecycleBackup(server, config, "startup", false);
                                    }
                                });
                            }
                        });
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BackupConfig config = BackupConfig.get();
            if (config.backupOnServerStop && !hasPendingRestore(server) && !BackupService.isBackupRunning()) {
                runLifecycleBackup(server, config, "shutdown", true);
            }
        });

        // every tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (--ticksUntilNextCheck > 0) {
                return;
            }

            ticksUntilNextCheck = 20L;
            BackupConfig config = BackupConfig.get();
            long now = System.currentTimeMillis();
            boolean backupRunning = BackupService.isBackupRunning();
            BackupType dueType = pollDueBackup(config, now, backupRunning, hasOnlinePlayers(server));
            if (dueType == null) {
                if (!backupRunning) {
                    maybeBroadcastAutomaticBackupWarning(server, config, now);
                }
                return;
            }

            BackupService.createBackup(server, dueType, "automatic")
                    .whenComplete((manifest, exception) -> {
                        if (exception != null) {
                            WorldBackupMod.LOGGER.error("Automatic backup failed.", exception);
                        }
                    });
        });
    }

    static BackupType pollDueBackup(BackupConfig config, long now, boolean backupRunning, boolean hasOnlinePlayers) {
        if (hasOnlinePlayers) {
            playersSeenSinceLastBackup = true;
        }

        skipMissedCronRuns(config, now);

        BackupType dueType = dueBackupType(config, now);
        if (dueType == null) {
            return null;
        }

        if (backupRunning) {
            return null;
        }

        advanceSchedule(dueType, config.automaticSchedule.forType(dueType), now);
        for (BackupType other : SCHEDULE_PRIORITY) {
            if (other != dueType && StartupBackupPolicy.satisfiesRequestedType(other, dueType)) {
                BackupConfig.ScheduledBackup otherSchedule = config.automaticSchedule.forType(other);
                if (otherSchedule.enabled && remainingMillis(other, otherSchedule, now) == 0L) {
                    advanceSchedule(other, otherSchedule, now);
                }
            }
        }
        resetWarnings();
        if (config.pauseAutomaticBackupsWithoutPlayers && !playersSeenSinceLastBackup) {
            return null;
        }

        playersSeenSinceLastBackup = false;
        return dueType;
    }

    private static boolean shouldRunStartupBackup(MinecraftServer server, BackupConfig config) {
        int minimumMinutes = config.startupBackupThreshold.forType(config.backupMode);
        if (minimumMinutes <= 0) {
            return true;
        }

        try {
            List<BackupManifest> manifests;
            Map<String, BackupStatus> remoteStatuses = Map.of();
            if (config.remoteStorage != null && config.remoteStorage.enabled) {
                String worldDirectoryName = BackupPaths.worldDirectoryName(server, server.getWorldPath(LevelResource.ROOT));
                try (RemoteBackupCatalog catalog = new RemoteBackupCatalog(config.remoteStorage, worldDirectoryName)) {
                    List<RemoteBackupCatalog.CatalogEntry> entries = catalog.list();
                    manifests = entries.stream().map(RemoteBackupCatalog.CatalogEntry::manifest).toList();
                    Map<String, BackupStatus> statusMap = new java.util.HashMap<>();
                    for (RemoteBackupCatalog.CatalogEntry entry : entries) {
                        statusMap.put(entry.manifest().id, entry.status());
                    }
                    remoteStatuses = statusMap;
                }
            } else {
                manifests = BackupStorage.readManifests(BackupPaths.worldBackupDir(server));
            }
            Map<String, BackupStatus> statuses = remoteStatuses;
            BackupManifest newest = manifests.stream()
                    .filter(manifest -> StartupBackupPolicy.satisfiesRequestedType(config.backupMode, manifest.type))
                    .filter(manifest -> !manifest.isSafetyBackup)
                    .filter(manifest -> config.remoteStorage != null && config.remoteStorage.enabled
                            ? hasCompletedStatus(statuses.get(manifest.id)) : hasCompletedStatus(server, manifest))
                    .filter(manifest -> parseCreatedAt(manifest.createdAt) != null)
                    .max(Comparator.comparing(manifest -> parseCreatedAt(manifest.createdAt)))
                    .orElse(null);
            if (newest == null) {
                return true;
            }

            Instant createdAt = parseCreatedAt(newest.createdAt);
            Instant now = Instant.now();
            if (!StartupBackupPolicy.shouldRun(minimumMinutes, newest.createdAt, now)) {
                long remainingMinutes = StartupBackupPolicy.remainingMinutes(minimumMinutes, createdAt, now);
                WorldBackupMod.LOGGER.info("Skipping {} startup backup; latest completed backup {} is too recent ({} minute(s) remaining).",
                        config.backupMode, newest.id, remainingMinutes);
                return false;
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            WorldBackupMod.LOGGER.warn("Unable to check the latest {} backup; startup backup will run.", config.backupMode, exception);
            return true;
        }
    }

    private static boolean hasCompletedStatus(MinecraftServer server, BackupManifest manifest) {
        try {
            BackupStatus status = BackupStorage.readStatus(BackupPaths.worldBackupDir(server).resolve(manifest.zipFileName));
            return status != null && status.completed && (status.brokenFiles == null || status.brokenFiles.isEmpty());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean hasCompletedStatus(BackupStatus status) {
        return status != null && status.completed && (status.brokenFiles == null || status.brokenFiles.isEmpty());
    }

    private static Instant parseCreatedAt(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(createdAt);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static void runLifecycleBackup(MinecraftServer server, BackupConfig config, String reason, boolean waitForCompletion) {
        lastBackupMillis.put(config.backupMode, System.currentTimeMillis());

        CompletableFuture<BackupManifest> backup = BackupService.createBackup(server, config.backupMode, reason)
                .whenComplete((manifest, exception) -> {
                    if (exception != null) {
                        WorldBackupMod.LOGGER.error("{} backup failed.", reason, exception);
                        return;
                    }
                });

        if (waitForCompletion) {
            try {
                backup.join();
            } catch (CompletionException exception) {
                WorldBackupMod.LOGGER.error("{} backup did not complete during shutdown.", reason, exception);
            }
        }
    }

    private static boolean hasPendingRestore(MinecraftServer server) {
        return com.frandm.justenoughbackups.backup.restore.RestoreService.hasPendingRestore(
                server.getWorldPath(LevelResource.ROOT));
    }

    private static boolean hasOnlinePlayers(MinecraftServer server) {
        return server.getPlayerList().getPlayerCount() > 0;
    }

    public static void resetTimer() {
        resetTimer(BackupConfig.get(), System.currentTimeMillis());
    }

    static void resetTimer(BackupConfig config, long now) {
        for (BackupType type : BackupType.values()) {
            lastBackupMillis.put(type, now);
        }
        nextCronRuns.clear();
        if (config != null && config.automaticSchedule != null) {
            for (BackupType type : SCHEDULE_PRIORITY) {
                BackupConfig.ScheduledBackup schedule = config.automaticSchedule.forType(type);
                if (schedule != null && schedule.enabled && schedule.hasCron()) {
                    resolveCronSlot(type, schedule, now);
                }
            }
        }
        playersSeenSinceLastBackup = false;
        resetWarnings();
    }

    public static List<NextBackupStatus> nextBackupStatus() {
        return nextBackupStatus(BackupConfig.get(), System.currentTimeMillis());
    }

    static List<NextBackupStatus> nextBackupStatus(BackupConfig config, long now) {
        skipMissedCronRuns(config, now);
        List<NextBackupStatus> list = new ArrayList<>();

        for (BackupType type : SCHEDULE_PRIORITY) {
            BackupConfig.ScheduledBackup schedule = config.automaticSchedule.forType(type);
            if (!schedule.enabled) {
                continue;
            }

            if (schedule.hasCron()) {
                ScheduledCronSlot slot = resolveCronSlot(type, schedule, now);
                if (slot == null) {
                    continue;
                }
                long remaining = Math.max(0L, slot.nextRun().toInstant().toEpochMilli() - now);
                if (remaining <= 0L) {
                    list.add(NextBackupStatus.readyNow(type, slot.nextRun()));
                } else {
                    list.add(NextBackupStatus.waiting(type, remaining, slot.nextRun()));
                }
                continue;
            }

            long remaining = remainingMillis(type, schedule, now);

            if (remaining <= 0L) {
                list.add(NextBackupStatus.readyNow(type));
            } else {
                list.add(NextBackupStatus.waiting(type, remaining));
            }
        }

        list.sort(Comparator.comparingLong(NextBackupStatus::remainingMillis));
        return list;
    }

    private static BackupType dueBackupType(BackupConfig config, long now) {
        for (BackupType type : SCHEDULE_PRIORITY) {
            BackupConfig.ScheduledBackup schedule = config.automaticSchedule.forType(type);
            if (!schedule.enabled) {
                continue;
            }

            if (remainingMillis(type, schedule, now) == 0L) {
                return type;
            }
        }

        return null;
    }

    private static long nextRemainingMillis(BackupConfig config, long now) {
        long nextRemainingMillis = Long.MAX_VALUE;
        for (BackupType type : SCHEDULE_PRIORITY) {
            BackupConfig.ScheduledBackup schedule = config.automaticSchedule.forType(type);
            if (!schedule.enabled) {
                continue;
            }

            nextRemainingMillis = Math.min(nextRemainingMillis, remainingMillis(type, schedule, now));
        }

        return nextRemainingMillis;
    }

    private static long remainingMillis(BackupType type, BackupConfig.ScheduledBackup schedule, long now) {
        if (schedule.hasCron()) {
            ScheduledCronSlot slot = resolveCronSlot(type, schedule, now);
            if (slot == null) {
                return Long.MAX_VALUE;
            }
            return Math.max(0L, slot.nextRun().toInstant().toEpochMilli() - now);
        }

        long last = lastBackupMillis.getOrDefault(type, 0L);
        if (last == 0L) {
            return 0L;
        }

        long intervalMillis = schedule.intervalMinutes * 60_000L;
        return Math.max(0L, intervalMillis - (now - last));
    }

    private static void advanceSchedule(BackupType type, BackupConfig.ScheduledBackup schedule, long now) {
        lastBackupMillis.put(type, now);
        if (!schedule.hasCron()) {
            nextCronRuns.remove(type);
            return;
        }
        ScheduledCronSlot current = resolveCronSlot(type, schedule, now);
        if (current == null) {
            return;
        }
        long referenceMillis = Math.max(now, current.nextRun().toInstant().toEpochMilli());
        current.expression().nextAfter(Instant.ofEpochMilli(referenceMillis), current.zone())
                .ifPresentOrElse(
                        next -> nextCronRuns.put(type, new ScheduledCronSlot(
                                current.cron(), current.timezone(), current.expression(), current.zone(), next)),
                        () -> nextCronRuns.remove(type)
                );
    }

    private static void skipMissedCronRuns(BackupConfig config, long now) {
        if (config == null || config.automaticSchedule == null) {
            return;
        }
        for (BackupType type : SCHEDULE_PRIORITY) {
            BackupConfig.ScheduledBackup schedule = config.automaticSchedule.forType(type);
            if (schedule == null || !schedule.enabled || !schedule.hasCron()) {
                nextCronRuns.remove(type);
                continue;
            }
            ScheduledCronSlot slot = resolveCronSlot(type, schedule, now);
            if (slot == null) {
                continue;
            }
            long scheduledMillis = slot.nextRun().toInstant().toEpochMilli();
            if (now - scheduledMillis > MISSED_CRON_GRACE_MILLIS) {
                slot.expression().nextAfter(Instant.ofEpochMilli(now), slot.zone())
                        .ifPresentOrElse(
                                next -> {
                                    WorldBackupMod.LOGGER.info(
                                            "Skipping missed {} cron backup scheduled for {}; next run is {}.",
                                            type.commandName(),
                                            LOG_TIME_FORMAT.format(slot.nextRun()),
                                            LOG_TIME_FORMAT.format(next));
                                    nextCronRuns.put(type, new ScheduledCronSlot(
                                            slot.cron(), slot.timezone(), slot.expression(), slot.zone(), next));
                                },
                                () -> nextCronRuns.remove(type)
                        );
            }
        }
    }

    private static ScheduledCronSlot resolveCronSlot(BackupType type, BackupConfig.ScheduledBackup schedule, long now) {
        if (schedule == null || !schedule.hasCron()) {
            nextCronRuns.remove(type);
            return null;
        }
        ScheduledCronSlot existing = nextCronRuns.get(type);
        if (existing != null && existing.matches(schedule)) {
            return existing;
        }
        try {
            CronExpression expression = CronExpression.parse(schedule.cron);
            ZoneId zone = CronExpression.parseZoneId(schedule.timezone);
            ZonedDateTime next = expression.nextAfter(Instant.ofEpochMilli(now), zone).orElse(null);
            if (next == null) {
                nextCronRuns.remove(type);
                return null;
            }
            ScheduledCronSlot created = new ScheduledCronSlot(schedule.cron, schedule.timezone, expression, zone, next);
            nextCronRuns.put(type, created);
            return created;
        } catch (IllegalArgumentException exception) {
            nextCronRuns.remove(type);
            return null;
        }
    }

    private static void maybeBroadcastAutomaticBackupWarning(MinecraftServer server, BackupConfig config, long now) {
        if (!config.automaticBackupWarningEnabled || automaticWarningSent) {
            return;
        }

        long remainingMillis = nextRemainingMillis(config, now);
        if (remainingMillis <= 0L || remainingMillis == Long.MAX_VALUE) {
            return;
        }

        long remainingMinutes = Math.max(1L, (remainingMillis + 59_999L) / 60_000L);
        if (remainingMinutes <= config.automaticBackupWarningMinutes) {
            automaticWarningSent = true;
            BackupMessages.broadcastAutomaticBackupWarning(server, remainingMinutes);
        }
    }

    private static void resetWarnings() {
        automaticWarningSent = false;
    }

    private record ScheduledCronSlot(
            String cron,
            String timezone,
            CronExpression expression,
            ZoneId zone,
            ZonedDateTime nextRun
    ) {
        boolean matches(BackupConfig.ScheduledBackup schedule) {
            return Objects.equals(cron, schedule.cron) && Objects.equals(timezone, schedule.timezone);
        }
    }
}
