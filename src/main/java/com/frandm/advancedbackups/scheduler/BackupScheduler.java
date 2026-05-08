package com.frandm.advancedbackups.scheduler;

import com.frandm.advancedbackups.WorldBackupMod;
import com.frandm.advancedbackups.backup.BackupService;
import com.frandm.advancedbackups.config.BackupConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class BackupScheduler {
    private static long ticksUntilNextCheck = 20L;
    private static long lastBackupMillis = 0L;

    private BackupScheduler() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (--ticksUntilNextCheck > 0) {
                return;
            }

            ticksUntilNextCheck = 20L;
            BackupConfig config = BackupConfig.get();
            if (!config.automaticBackupsEnabled) {
                return;
            }

            long now = System.currentTimeMillis();
            long intervalMillis = config.automaticIntervalMinutes * 60_000L;
            if (lastBackupMillis != 0L && now - lastBackupMillis < intervalMillis) {
                return;
            }

            if (BackupService.isBackupRunning()) {
                return;
            }

            lastBackupMillis = now;
            sendActionBar(server, "Advanced Backups: Initializing automatic backup...");
            BackupService.createBackup(server, config.backupMode, "automatic")
                    .whenComplete((manifest, exception) -> {
                        if (exception != null) {
                            WorldBackupMod.LOGGER.error("Automatic backup failed.", exception);
                            server.execute(() -> sendActionBar(server, "Advanced Backups: automatic backup failed."));
                            return;
                        }

                        server.execute(() -> sendActionBar(
                                server,
                                "Advanced Backups: automatic backup completed: " + manifest.id
                        ));
                    });
        });
    }

    private static void sendActionBar(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), true);
    }

    public static void resetTimer() {
        lastBackupMillis = 0L;
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
}
