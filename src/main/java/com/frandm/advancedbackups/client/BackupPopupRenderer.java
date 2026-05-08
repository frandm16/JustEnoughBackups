package com.frandm.advancedbackups.client;

import com.frandm.advancedbackups.backup.progress.BackupProgressPayload;
import com.frandm.advancedbackups.backup.progress.BackupProgressState;
import com.frandm.advancedbackups.config.BackupConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

final class BackupPopupRenderer {
    private BackupPopupRenderer() {
    }

    static Dimensions measure(Font font, BackupConfig.Popup popup, BackupProgressPayload progress) {
        String title = applyTemplate(popup.title, progress);
        String detail = detail(progress, popup);
        String progressLine = progressLine(progress);
        int width = Math.max(font.width(title), Math.max(font.width(detail), font.width(progressLine))) + 12;
        return new Dimensions(width, 34);
    }

    static void render(GuiGraphicsExtractor graphics, Font font, BackupConfig.Popup popup, BackupProgressPayload progress, int x, int y) {
        String title = applyTemplate(popup.title, progress);
        String detail = detail(progress, popup);
        String progressLine = progressLine(progress);
        Dimensions dimensions = measure(font, popup, progress);

        graphics.fill(x - 4, y - 4, x + dimensions.width(), y + dimensions.height(), popup.backgroundColorArgb());
        graphics.text(font, title, x, y, 0xFFFFFFFF, true);
        graphics.text(font, detail, x, y + 11, color(progress, popup), true);
        graphics.text(font, progressLine, x, y + 22, popup.textColorArgb(), true);
    }

    static String formatBytes(long bytes) {
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

    private static String detail(BackupProgressPayload progress, BackupConfig.Popup popup) {
        String template = switch (progress.state()) {
            case STARTED, RUNNING -> popup.runningText;
            case COMPLETED -> popup.completedText;
            case FAILED -> popup.failedText;
        };
        return applyTemplate(template, progress);
    }

    private static String progressLine(BackupProgressPayload progress) {
        if (progress.state() == BackupProgressState.FAILED) {
            return "Backup failed";
        }
        if (progress.state() == BackupProgressState.COMPLETED) {
            return "100% - " + formatBytes(progress.totalBytes()) + " copied";
        }

        return percent(progress) + "% - "
                + formatBytes(progress.bytesWritten())
                + " / "
                + formatBytes(progress.totalBytes());
    }

    private static int color(BackupProgressPayload progress, BackupConfig.Popup popup) {
        return switch (progress.state()) {
            case FAILED -> popup.failedColorArgb();
            case COMPLETED -> popup.completedColorArgb();
            default -> popup.runningColorArgb();
        };
    }

    private static String applyTemplate(String template, BackupProgressPayload progress) {
        return template
                .replace("{reason}", reason(progress))
                .replace("{type}", progress.backupType().toString())
                .replace("{percent}", String.valueOf(percent(progress)))
                .replace("{bytesWritten}", formatBytes(progress.bytesWritten()))
                .replace("{totalBytes}", formatBytes(progress.totalBytes()));
    }

    private static String reason(BackupProgressPayload progress) {
        return progress.reason() == null || progress.reason().isBlank()
                ? "backup"
                : progress.reason().replace('_', ' ');
    }

    private static int percent(BackupProgressPayload progress) {
        return progress.totalBytes() <= 0L
                ? 100
                : (int) Math.min(100L, (progress.bytesWritten() * 100L) / progress.totalBytes());
    }

    record Dimensions(int width, int height) {
    }
}
