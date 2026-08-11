package com.frandm.justenoughbackups.backup;

import java.time.format.DateTimeFormatter;

public final class BackupConstants {
    public static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    public static final String DATA_ENTRY = "justenoughbackups.data";
    public static final String MANIFEST_ENTRY = "manifest.json";
    public static final String STATUS_ENTRY = "justenoughbackups-status.json";
    public static final String SUMMARY_ENTRY = "summary.txt";
    public static final String DEFAULT_BACKUP_DIRECTORY = "backups/";
    public static final String RESTORE_CONTAINER = ".justenoughbackups-restore";
    public static final String RESTORE_STAGING_PREFIX = ".justenoughbackups-staging-";
    public static final String RESTORE_OLD_PREFIX = ".justenoughbackups-old-";
    public static final String RESTORE_INTENT_PREFIX = ".justenoughbackups-intent-";

    private BackupConstants() {
    }
}
