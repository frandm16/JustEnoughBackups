package com.frandm.justenoughbackups.scheduler;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.BackupMessages;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.BackupService;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class BackupScheduler {
    private static long ticksUntilNextCheck = 20L;
    private static final EnumMap<BackupType, Long> lastBackupMillis =
            new EnumMap<>(BackupType.class);

    private static final List<BackupType> SCHEDULE_PRIORITY = List.of(
            BackupType.FULL,
            BackupType.DIFFERENTIAL,
            BackupType.PARTIAL
    );
    private static boolean playersSeenSinceLastBackup = false;
    private static boolean automaticWarningSent = false;

    private BackupScheduler() {
    }

    public static void register() {
        // started server
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BackupConfig config = BackupConfig.get();
            resetTimer();
            playersSeenSinceLastBackup = hasOnlinePlayers(server);
            if (config.backupOnServerStart) {
                runLifecycleBackup(server, config, "startup", false);
            }
        });

        // stopping server
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BackupConfig config = BackupConfig.get();
            if (config.backupOnServerStop) {
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
            if (hasOnlinePlayers(server)) {
                playersSeenSinceLastBackup = true;
            }

            long now = System.currentTimeMillis();
            BackupType dueType = dueBackupType(config, now);
            if (dueType == null) {
                maybeBroadcastAutomaticBackupWarning(server, config, now);
                return;
            }

            if (BackupService.isBackupRunning()) {
                return;
            }

            lastBackupMillis.put(dueType, now);
            resetWarnings();
            if (config.pauseAutomaticBackupsWithoutPlayers && !playersSeenSinceLastBackup) {
                return;
            }

            playersSeenSinceLastBackup = false;
            BackupService.createBackup(server, dueType, "automatic")
                    .whenComplete((manifest, exception) -> {
                        if (exception != null) {
                            WorldBackupMod.LOGGER.error("Automatic backup failed.", exception);
                            return;
                        }
                    });
        });
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

    private static boolean hasOnlinePlayers(MinecraftServer server) {
        return server.getPlayerList().getPlayerCount() > 0;
    }

    public static void resetTimer() {
        long now = System.currentTimeMillis();
        for (BackupType type : BackupType.values()) {
            lastBackupMillis.put(type, now);
        }
        playersSeenSinceLastBackup = false;
        resetWarnings();
    }

    public static List<NextBackupStatus> nextBackupStatus() {
        BackupConfig config = BackupConfig.get();
        long now = System.currentTimeMillis();
        List<NextBackupStatus> list = new ArrayList<>();

        for (BackupType type : SCHEDULE_PRIORITY) {
            BackupConfig.ScheduledBackup schedule = config.automaticSchedule.forType(type);
            if (!schedule.enabled) {
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
        long last = lastBackupMillis.getOrDefault(type, 0L);
        if (last == 0L) {
            return 0L;
        }

        long intervalMillis = schedule.intervalMinutes * 60_000L;
        return Math.max(0L, intervalMillis - (now - last));
    }

    private static void maybeBroadcastAutomaticBackupWarning(MinecraftServer server, BackupConfig config, long now) {
        if (!config.automaticBackupWarningEnabled || automaticWarningSent) {
            return;
        }

        long remainingMillis = nextRemainingMillis(config, now);
        if (remainingMillis == Long.MAX_VALUE) {
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
}
