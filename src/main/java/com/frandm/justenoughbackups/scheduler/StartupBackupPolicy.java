package com.frandm.justenoughbackups.scheduler;

import com.frandm.justenoughbackups.backup.model.BackupType;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

final class StartupBackupPolicy {
    private StartupBackupPolicy() {
    }

    static boolean shouldRun(int minimumAgeMinutes, String latestCreatedAt, Instant now) {
        if (minimumAgeMinutes <= 0 || latestCreatedAt == null || latestCreatedAt.isBlank()) {
            return true;
        }
        final Instant createdAt;
        try {
            createdAt = Instant.parse(latestCreatedAt);
        } catch (DateTimeParseException exception) {
            return true;
        }
        if (createdAt.isAfter(now)) {
            return true;
        }
        return Duration.between(createdAt, now).compareTo(Duration.ofMinutes(minimumAgeMinutes)) >= 0;
    }

    static boolean satisfiesRequestedType(BackupType requestedType, BackupType completedType) {
        return completedType == requestedType || requestedType != BackupType.FULL && completedType == BackupType.FULL;
    }

    static long remainingMinutes(int minimumAgeMinutes, Instant createdAt, Instant now) {
        long minimumMillis = minimumAgeMinutes * 60_000L;
        long elapsedMillis = Math.max(0L, now.toEpochMilli() - createdAt.toEpochMilli());
        return Math.max(1L, (minimumMillis - elapsedMillis + 59_999L) / 60_000L);
    }
}