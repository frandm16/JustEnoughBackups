package com.frandm.justenoughbackups.scheduler;

import com.frandm.justenoughbackups.backup.model.BackupType;

public record NextBackupStatus(BackupType type, boolean enabled, boolean ready, long remainingMillis) {

    static NextBackupStatus disabled(BackupType type) {
        return new NextBackupStatus(type, false, false, 0L);
    }

    static NextBackupStatus readyNow(BackupType type) {
        return new NextBackupStatus(type, true, true, 0L);
    }

    static NextBackupStatus waiting(BackupType type, long remainingMillis) {
        return new NextBackupStatus(type, true, false, remainingMillis);
    }
}
