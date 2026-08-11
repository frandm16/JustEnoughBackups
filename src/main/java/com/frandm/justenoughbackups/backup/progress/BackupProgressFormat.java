package com.frandm.justenoughbackups.backup.progress;

import java.util.Locale;

public final class BackupProgressFormat {
    private BackupProgressFormat() {
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }

        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (value >= 1024.0D && unit < units.length - 1) {
            value /= 1024.0D;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    public static int percent(long bytesWritten, long totalBytes) {
        if (totalBytes <= 0L) {
            return 0;
        }
        return (int) Math.min(100L, (bytesWritten * 100L) / totalBytes);
    }
}
