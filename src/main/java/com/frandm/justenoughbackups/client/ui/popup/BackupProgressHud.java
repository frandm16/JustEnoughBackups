package com.frandm.justenoughbackups.client.ui.popup;

import com.frandm.justenoughbackups.backup.progress.BackupProgressPayload;
import com.frandm.justenoughbackups.backup.progress.BackupProgressState;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Objects;

public final class BackupProgressHud {
    private static final long FINISHED_VISIBLE_NANOS = 5_000_000_000L;
    private static final long RUNNING_TIMEOUT_NANOS = 3_000_000_000L;
    private static volatile HudEntry current;

    private record HudEntry(BackupProgressPayload payload, long updatedAtNanos) {
    }

    private BackupProgressHud() {
    }

    public static void clear() {
        current = null;
    }

    public static void update(BackupProgressPayload payload) {
        HudEntry previousEntry = current;
        BackupProgressPayload previous = previousEntry == null ? null : previousEntry.payload();
        if (previous != null
                && (previous.state() == BackupProgressState.COMPLETED || previous.state() == BackupProgressState.FAILED)
                && (payload.state() == BackupProgressState.STARTED || payload.state() == BackupProgressState.RUNNING)
                && !previous.backupId().isEmpty()
                && Objects.equals(previous.backupId(), payload.backupId())) {
            return;
        }
        current = new HudEntry(payload, System.nanoTime());
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        HudEntry entry = current;
        BackupConfig.Popup popup = BackupConfig.get().popup;
        if (entry == null || !popup.enabled) {
            return;
        }

        BackupProgressPayload progress = entry.payload();
        long ageNanos = System.nanoTime() - entry.updatedAtNanos();
        if (progress.state() == BackupProgressState.RUNNING || progress.state() == BackupProgressState.STARTED) {
            if (ageNanos > RUNNING_TIMEOUT_NANOS) {
                current = null;
                return;
            }
        } else if (ageNanos > FINISHED_VISIBLE_NANOS) {
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
