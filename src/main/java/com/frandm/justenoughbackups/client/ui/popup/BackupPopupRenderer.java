package com.frandm.justenoughbackups.client.ui.popup;

import com.frandm.justenoughbackups.backup.progress.BackupProgressFormat;
import com.frandm.justenoughbackups.backup.progress.BackupProgressPayload;
import com.frandm.justenoughbackups.backup.progress.BackupProgressState;
import com.frandm.justenoughbackups.backup.progress.BackupProgressPhase;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class BackupPopupRenderer {
    private BackupPopupRenderer() {
    }

    public static Dimensions measure(Font font, BackupConfig.Popup popup, BackupProgressPayload progress) {
        String detail = detail(progress, popup);
        String progressLine = progressLine(progress);
        int contentWidth = Math.max(font.width(detail), font.width(progressLine));
        if (popup.showTitle) {
            contentWidth = Math.max(contentWidth, font.width(applyTemplate(popup.title, progress)));
        }
        int height = popup.showTitle ? 34 : 23;
        return new Dimensions(contentWidth + 12, height);
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, BackupConfig.Popup popup, BackupProgressPayload progress, int x, int y) {
        String detail = detail(progress, popup);
        String progressLine = progressLine(progress);
        Dimensions dimensions = measure(font, popup, progress);

        graphics.fill(x - 4, y - 4, x + dimensions.width(), y + dimensions.height(), popup.backgroundColorArgb());
        if (popup.showBorder) {
            graphics.outline(x - 4, y - 4, dimensions.width() + 4, dimensions.height() + 4, popup.textColorArgb());
        }

        int textY = y;
        if (popup.showTitle) {
            drawText(graphics, font, popup, applyTemplate(popup.title, progress), x, textY, dimensions.width(), popup.textColorArgb());
            textY += 11;
        }
        drawText(graphics, font, popup, detail, x, textY, dimensions.width(), color(progress, popup));
        drawText(graphics, font, popup, progressLine, x, textY + 11, dimensions.width(), popup.textColorArgb());
    }

    private static String detail(BackupProgressPayload progress, BackupConfig.Popup popup) {
        String template = switch (progress.state()) {
            case STARTED, RUNNING -> progress.phase() == BackupProgressPhase.SCANNING ? popup.scanningText : popup.runningText;
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
            return "100% - " + BackupProgressFormat.formatBytes(progress.bytesWritten()) + " copied";
        }
        if (progress.phase() == BackupProgressPhase.WRITING) {
            return BackupProgressFormat.formatBytes(progress.bytesWritten()) + " written";
        }

        String action = switch (progress.phase()) {
            case SCANNING -> " scanned";
            case COPYING -> " copied";
            case COMPRESSING -> " copied";
            case WRITING -> " written";
        };
        String written = BackupProgressFormat.formatBytes(progress.bytesWritten());
        if (progress.totalBytes() <= 0L) {
            return BackupProgressFormat.percent(progress.bytesWritten(), progress.totalBytes()) + "% - " + written + action;
        }
        return BackupProgressFormat.percent(progress.bytesWritten(), progress.totalBytes()) + "% - "
                + written
                + " / "
                + BackupProgressFormat.formatBytes(progress.totalBytes())
                + action;
    }

    private static int color(BackupProgressPayload progress, BackupConfig.Popup popup) {
        return switch (progress.state()) {
            case FAILED -> popup.failedColorArgb();
            case COMPLETED -> popup.completedColorArgb();
            default -> popup.runningColorArgb();
        };
    }

    private static void drawText(GuiGraphicsExtractor graphics, Font font, BackupConfig.Popup popup, String text, int x, int y, int popupWidth, int color) {
        int textX = popup.centerText ? x + Math.max(0, popupWidth - 8 - font.width(text)) / 2 : x;
        graphics.text(font, text, textX, y, color, true);
    }

    private static String applyTemplate(String template, BackupProgressPayload progress) {
        return template
                .replace("{reason}", reason(progress))
                .replace("{type}", progress.backupType().toString())
                .replace("{percent}", String.valueOf(BackupProgressFormat.percent(progress.bytesWritten(), progress.totalBytes())))
                .replace("{bytesWritten}", BackupProgressFormat.formatBytes(progress.bytesWritten()))
                .replace("{totalBytes}", BackupProgressFormat.formatBytes(progress.totalBytes()));
    }

    private static String reason(BackupProgressPayload progress) {
        return progress.reason() == null || progress.reason().isBlank()
                ? "backup"
                : progress.reason().replace('_', ' ');
    }

    public record Dimensions(int width, int height) {
    }
}
