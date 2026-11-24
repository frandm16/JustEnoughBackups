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
        return layout(font, popup, progress).dimensions();
    }

    public static RenderLayout layout(Font font, BackupConfig.Popup popup, BackupProgressPayload progress) {
        String title = popup.showTitle ? applyTemplate(popup.title, progress) : "";
        String detail = detail(progress, popup);
        String progressLine = progressLine(progress);
        int titleWidth = popup.showTitle ? font.width(title) : 0;
        int detailWidth = font.width(detail);
        int progressWidth = font.width(progressLine);
        int contentWidth = Math.max(detailWidth, progressWidth);
        if (popup.showTitle) {
            contentWidth = Math.max(contentWidth, titleWidth);
        }
        int height = popup.showTitle ? 34 : 23;
        return new RenderLayout(
                title,
                detail,
                progressLine,
                titleWidth,
                detailWidth,
                progressWidth,
                new Dimensions(contentWidth + 12, height),
                popup.backgroundColorArgb(),
                popup.textColorArgb(),
                color(progress, popup),
                popup.showTitle,
                popup.showBorder,
                popup.centerText
        );
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, BackupConfig.Popup popup, BackupProgressPayload progress, int x, int y) {
        render(graphics, font, layout(font, popup, progress), x, y);
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, RenderLayout layout, int x, int y) {
        Dimensions dimensions = layout.dimensions();
        graphics.fill(x - 4, y - 4, x + dimensions.width(), y + dimensions.height(), layout.backgroundColor());
        if (layout.showBorder()) {
            graphics.outline(x - 4, y - 4, dimensions.width() + 4, dimensions.height() + 4, layout.textColor());
        }

        int textY = y;
        if (layout.showTitle()) {
            drawText(graphics, font, layout.centerText(), layout.title(), layout.titleWidth(), x, textY, dimensions.width(), layout.textColor());
            textY += 11;
        }
        drawText(graphics, font, layout.centerText(), layout.detail(), layout.detailWidth(), x, textY, dimensions.width(), layout.detailColor());
        drawText(graphics, font, layout.centerText(), layout.progressLine(), layout.progressWidth(), x, textY + 11, dimensions.width(), layout.textColor());
    }

    static String detail(BackupProgressPayload progress, BackupConfig.Popup popup) {
        String template = switch (progress.state()) {
            case STARTED, RUNNING -> switch (progress.phase()) {
                case SCANNING -> popup.scanningText;
                case COMPRESSING -> "Compressing backup";
                default -> popup.runningText;
            };
            case COMPLETED -> popup.completedText;
            case FAILED -> popup.failedText;
        };
        return applyTemplate(template, progress);
    }

    static String progressLine(BackupProgressPayload progress) {
        if (progress.state() == BackupProgressState.FAILED) {
            return "Backup failed";
        }
        if (progress.state() == BackupProgressState.COMPLETED) {
            return "100% - " + BackupProgressFormat.formatBytes(progress.bytesWritten()) + " copied";
        }
        if (progress.phase() == BackupProgressPhase.WRITING) {
            return BackupProgressFormat.formatBytes(progress.bytesWritten()) + " written";
        }
        if (progress.phase() == BackupProgressPhase.COMPRESSING) {
            return "Compressing entries";
        }

        String action = switch (progress.phase()) {
            case SCANNING -> " scanned";
            case COPYING -> " copied";
            case COMPRESSING -> " compressing";
            case UPLOADING -> " uploaded";
            case DOWNLOADING -> " downloaded";
            case VERIFYING -> " verified";
            default -> " written";
        };
        String written = BackupProgressFormat.formatBytes(progress.bytesWritten());
        if (progress.totalBytes() <= 0L) {
            return written + action;
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

    private static void drawText(GuiGraphicsExtractor graphics, Font font, boolean centerText, String text, int textWidth, int x, int y, int popupWidth, int color) {
        int textX = centerText ? x + Math.max(0, popupWidth - 8 - textWidth) / 2 : x;
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

    public record RenderLayout(
            String title,
            String detail,
            String progressLine,
            int titleWidth,
            int detailWidth,
            int progressWidth,
            Dimensions dimensions,
            int backgroundColor,
            int textColor,
            int detailColor,
            boolean showTitle,
            boolean showBorder,
            boolean centerText
    ) {
    }
}
