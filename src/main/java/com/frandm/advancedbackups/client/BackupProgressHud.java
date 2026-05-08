package com.frandm.advancedbackups.client;

import com.frandm.advancedbackups.backup.progress.BackupProgressPayload;
import com.frandm.advancedbackups.backup.progress.BackupProgressState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

final class BackupProgressHud {
    private static final long FINISHED_VISIBLE_MILLIS = 5_000L;
    private static final long RUNNING_TIMEOUT_MILLIS = 2_000L;
    private static volatile BackupProgressPayload current;
    private static volatile long lastUpdateMillis;

    private BackupProgressHud() {
    }

    static void update(BackupProgressPayload payload) {
        current = payload;
        lastUpdateMillis = System.currentTimeMillis();
    }

    static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        BackupProgressPayload progress = current;
        if (progress == null) {
            return;
        }

        long age = System.currentTimeMillis() - lastUpdateMillis;
        if (progress.state() == BackupProgressState.RUNNING || progress.state() == BackupProgressState.STARTED) {
            if (age > RUNNING_TIMEOUT_MILLIS) {
                return;
            }
        } else if (age > FINISHED_VISIBLE_MILLIS) {
            current = null;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String detail = detail(progress);
        String progressLine = progressLine(progress);

        int x = 8;
        int y = 8;
        int width = Math.max(font.width(detail), font.width(progressLine)) + 12;
        int height = 34;
        graphics.fill(x - 4, y - 4, x + width, y + height, 0xAA101010);
        graphics.text(font, detail, x, y + 11, color(progress), true);
        graphics.text(font, progressLine, x, y + 22, 0xFFE0E0E0, true);
    }

    private static String detail(BackupProgressPayload progress) {
        String reason = progress.reason() == null || progress.reason().isBlank()
                ? "backup"
                : progress.reason().replace('_', ' ');
        String state = switch (progress.state()) {
            case STARTED -> "Starting";
            case RUNNING -> "Running";
            case COMPLETED -> "Completed";
            case FAILED -> "Failed";
        };
        return state + " " + reason + " " + progress.backupType();
    }

    private static String progressLine(BackupProgressPayload progress) {
        if (progress.state() == BackupProgressState.FAILED) {
            return "Backup failed";
        }
        if (progress.state() == BackupProgressState.COMPLETED) {
            return "100% - " + formatBytes(progress.totalBytes()) + " copied";
        }

        int percent = progress.totalBytes() <= 0L
                ? 100
                : (int) Math.min(100L, (progress.bytesWritten() * 100L) / progress.totalBytes());
        return percent + "% - "
                + formatBytes(progress.bytesWritten())
                + " / "
                + formatBytes(progress.totalBytes());
    }

    private static int color(BackupProgressPayload progress) {
        return switch (progress.state()) {
            case FAILED -> 0xFFFF5555;
            case COMPLETED -> 0xFF55FF55;
            default -> 0xFF55FFFF;
        };
    }

    private static String formatBytes(long bytes) {
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
}
