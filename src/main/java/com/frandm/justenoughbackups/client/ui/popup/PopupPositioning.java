package com.frandm.justenoughbackups.client.ui.popup;

import com.frandm.justenoughbackups.backup.progress.BackupProgressPayload;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.client.gui.Font;

public final class PopupPositioning {
    private static final int MIN_POSITION = 4;

    private PopupPositioning() {
    }

    public static Position resolve(Font font, BackupConfig.Popup popup, BackupProgressPayload progress, int screenWidth, int screenHeight) {
        BackupPopupRenderer.Dimensions dimensions = BackupPopupRenderer.measure(font, popup, progress);
        if (hasRatios(popup)) {
            return new Position(
                    fromRatio(popup.xRatio, screenWidth, dimensions.width()),
                    fromRatio(popup.yRatio, screenHeight, dimensions.height())
            );
        }
        return new Position(
                clamp(popup.x, screenWidth, dimensions.width()),
                clamp(popup.y, screenHeight, dimensions.height())
        );
    }

    public static void applyRatios(Font font, BackupConfig.Popup popup, BackupProgressPayload progress, int screenWidth, int screenHeight) {
        Position position = resolve(font, popup, progress, screenWidth, screenHeight);
        popup.x = position.x();
        popup.y = position.y();
    }

    public static void clampAndRemember(Font font, BackupConfig.Popup popup, BackupProgressPayload progress, int screenWidth, int screenHeight) {
        BackupPopupRenderer.Dimensions dimensions = BackupPopupRenderer.measure(font, popup, progress);
        popup.x = clamp(popup.x, screenWidth, dimensions.width());
        popup.y = clamp(popup.y, screenHeight, dimensions.height());
        rememberRatios(popup, dimensions, screenWidth, screenHeight);
    }

    public static void rememberRatios(Font font, BackupConfig.Popup popup, BackupProgressPayload progress, int screenWidth, int screenHeight) {
        rememberRatios(popup, BackupPopupRenderer.measure(font, popup, progress), screenWidth, screenHeight);
    }

    private static boolean hasRatios(BackupConfig.Popup popup) {
        return popup.xRatio >= 0.0D && popup.yRatio >= 0.0D;
    }

    private static int fromRatio(double ratio, int screenSize, int popupSize) {
        int min = MIN_POSITION;
        int max = maxPosition(screenSize, popupSize);
        int span = Math.max(0, max - min);
        return Math.clamp((int) Math.round(min + Math.clamp(ratio, 0.0D, 1.0D) * span), min, max);
    }

    private static int clamp(int value, int screenSize, int popupSize) {
        return Math.clamp(value, MIN_POSITION, maxPosition(screenSize, popupSize));
    }

    private static int maxPosition(int screenSize, int popupSize) {
        return Math.max(MIN_POSITION, screenSize - popupSize);
    }

    private static void rememberRatios(BackupConfig.Popup popup, BackupPopupRenderer.Dimensions dimensions, int screenWidth, int screenHeight) {
        popup.xRatio = toRatio(popup.x, screenWidth, dimensions.width());
        popup.yRatio = toRatio(popup.y, screenHeight, dimensions.height());
    }

    private static double toRatio(int position, int screenSize, int popupSize) {
        int min = MIN_POSITION;
        int max = maxPosition(screenSize, popupSize);
        int span = max - min;
        if (span <= 0) {
            return 0.0D;
        }
        return Math.clamp((position - min) / (double) span, 0.0D, 1.0D);
    }

    public record Position(int x, int y) {
    }
}
