package com.frandm.justenoughbackups.backup;

import java.time.format.DateTimeFormatter;

public final class BackupConstants {
    public static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    public static final String DATA_ENTRY = "justenoughbackups.data";
    public static final String MANIFEST_ENTRY = "manifest.json";
    public static final String STATUS_ENTRY = "justenoughbackups-status.json";
    public static final String DEFAULT_BACKUP_DIRECTORY = "backups/";

    private BackupConstants() {
    }
}
