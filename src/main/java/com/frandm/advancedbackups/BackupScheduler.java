package com.frandm.advancedbackups;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

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

            if (BackupManager.isBackupRunning()) {
                return;
            }

            lastBackupMillis = now;
            BackupManager.createBackup(server, config.backupMode, "automatic")
                    .exceptionally(exception -> {
                        lastBackupMillis = 0L;
                        WorldBackupMod.LOGGER.error("Automatic backup failed.", exception);
                        return null;
                    });
        });
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

    public record NextBackupStatus(boolean enabled, boolean ready, long remainingMillis) {
        private static NextBackupStatus disabled() {
            return new NextBackupStatus(false, false, 0L);
        }

        private static NextBackupStatus readyNow() {
            return new NextBackupStatus(true, true, 0L);
        }

        private static NextBackupStatus waiting(long remainingMillis) {
            return new NextBackupStatus(true, false, remainingMillis);
        }
    }
}
