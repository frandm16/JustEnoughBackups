package com.frandm.justenoughbackups.scheduler;

import com.frandm.justenoughbackups.backup.model.BackupType;

import java.time.ZonedDateTime;

public record NextBackupStatus(
        BackupType type,
        boolean enabled,
        boolean ready,
        long remainingMillis,
        ZonedDateTime scheduledAt
) {

    public NextBackupStatus(BackupType type, boolean enabled, boolean ready, long remainingMillis) {
        this(type, enabled, ready, remainingMillis, null);
    }

    static NextBackupStatus disabled(BackupType type) {
        return new NextBackupStatus(type, false, false, 0L, null);
    }

    static NextBackupStatus readyNow(BackupType type) {
        return new NextBackupStatus(type, true, true, 0L, null);
    }

    static NextBackupStatus readyNow(BackupType type, ZonedDateTime scheduledAt) {
        return new NextBackupStatus(type, true, true, 0L, scheduledAt);
    }

    static NextBackupStatus waiting(BackupType type, long remainingMillis) {
        return new NextBackupStatus(type, true, false, remainingMillis, null);
    }

    static NextBackupStatus waiting(BackupType type, long remainingMillis, ZonedDateTime scheduledAt) {
        return new NextBackupStatus(type, true, false, remainingMillis, scheduledAt);
    }

    public boolean isCronScheduled() {
        return scheduledAt != null;
    }
}
