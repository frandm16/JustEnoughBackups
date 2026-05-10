package com.frandm.justenoughbackups.scheduler;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.BackupMessages;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.BackupService;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class BackupScheduler {
    private static long ticksUntilNextCheck = 20L;
    private static long lastBackupMillis = 0L;
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
            if (!config.automaticBackupsEnabled) {
                return;
            }

            long now = System.currentTimeMillis();
            long intervalMillis = config.automaticIntervalMinutes * 60_000L;
            if (lastBackupMillis != 0L && now - lastBackupMillis < intervalMillis) {
                maybeBroadcastAutomaticBackupWarning(server, intervalMillis - (now - lastBackupMillis));
                return;
            }

            if (BackupService.isBackupRunning()) {
                return;
            }

            lastBackupMillis = now;
            resetWarnings();
            if (config.pauseAutomaticBackupsWithoutPlayers && !playersSeenSinceLastBackup) {
                return;
            }

            playersSeenSinceLastBackup = false;
            BackupService.createBackup(server, config.backupMode, "automatic")
                    .whenComplete((manifest, exception) -> {
                        if (exception != null) {
                            WorldBackupMod.LOGGER.error("Automatic backup failed.", exception);
                            return;
                        }
                    });
        });
    }

    private static void runLifecycleBackup(MinecraftServer server, BackupConfig config, String reason, boolean waitForCompletion) {
        lastBackupMillis = System.currentTimeMillis();

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
        lastBackupMillis = System.currentTimeMillis();
        playersSeenSinceLastBackup = false;
        resetWarnings();
    }

    public static NextBackupStatus nextBackupStatus() {
        BackupConfig config = BackupConfig.get();
        if (!config.automaticBackupsEnabled) {
            return NextBackupStatus.disabled();
        }

        if (lastBackupMillis == 0L) {
            return NextBackupStatus.readyNow();
        }

        long intervalMillis = config.automaticIntervalMinutes * 60_000L;
        long remainingMillis = Math.max(0L, intervalMillis - (System.currentTimeMillis() - lastBackupMillis));
        return remainingMillis == 0L
                ? NextBackupStatus.readyNow()
                : NextBackupStatus.waiting(remainingMillis);
    }

    private static void maybeBroadcastAutomaticBackupWarning(MinecraftServer server, long remainingMillis) {
        BackupConfig config = BackupConfig.get();
        if (!config.automaticBackupWarningEnabled || automaticWarningSent) {
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
