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
        if (bytesWritten <= 0L) {
            return 0;
        }
        if (bytesWritten >= totalBytes) {
            return 100;
        }
        long quotient = totalBytes / 100L;
        long remainder = totalBytes % 100L;
        int low = 0;
        int high = 100;
        while (low < high) {
            int candidate = (low + high + 1) / 2;
            long threshold = quotient * candidate + (remainder * candidate + 99L) / 100L;
            if (bytesWritten >= threshold) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return low;
    }
}
