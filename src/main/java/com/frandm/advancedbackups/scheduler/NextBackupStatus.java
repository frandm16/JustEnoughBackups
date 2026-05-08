package com.frandm.advancedbackups.scheduler;

public record NextBackupStatus(boolean enabled, boolean ready, long remainingMillis) {
    static NextBackupStatus disabled() {
        return new NextBackupStatus(false, false, 0L);
    }

    static NextBackupStatus readyNow() {
        return new NextBackupStatus(true, true, 0L);
    }

    static NextBackupStatus waiting(long remainingMillis) {
        return new NextBackupStatus(true, false, remainingMillis);
    }
}
