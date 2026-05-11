package com.frandm.justenoughbackups.client.ui.popup;

import com.frandm.justenoughbackups.backup.progress.BackupProgressPayload;
import com.frandm.justenoughbackups.backup.progress.BackupProgressState;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class BackupProgressHud {
    private static final long FINISHED_VISIBLE_MILLIS = 5_000L;
    private static final long RUNNING_TIMEOUT_MILLIS = 2_000L;
    private static volatile BackupProgressPayload current;
    private static volatile long lastUpdateMillis;

    private BackupProgressHud() {
    }

    public static void update(BackupProgressPayload payload) {
        current = payload;
        lastUpdateMillis = System.currentTimeMillis();
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        BackupProgressPayload progress = current;
        BackupConfig.Popup popup = BackupConfig.get().popup;
        if (progress == null || !popup.enabled) {
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
        PopupPositioning.Position position = PopupPositioning.resolve(
                font,
                popup,
                progress,
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight()
        );
        BackupPopupRenderer.render(graphics, font, popup, progress, position.x(), position.y());
    }
}
