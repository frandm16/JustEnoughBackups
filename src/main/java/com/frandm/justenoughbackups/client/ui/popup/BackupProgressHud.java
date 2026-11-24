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
    private static CachedLayout cachedLayout;

    private record HudEntry(BackupProgressPayload payload, long updatedAtNanos) {
    }

    private record CachedLayout(BackupProgressPayload progress, BackupConfig.Popup popup, Font font, BackupPopupRenderer.RenderLayout layout) {
    }

    private BackupProgressHud() {
    }

    public static void clear() {
        current = null;
        cachedLayout = null;
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
        CachedLayout cached = cachedLayout;
        BackupPopupRenderer.RenderLayout layout;
        if (cached != null && cached.progress() == progress && cached.popup() == popup && cached.font() == font) {
            layout = cached.layout();
        } else {
            layout = BackupPopupRenderer.layout(font, popup, progress);
            cachedLayout = new CachedLayout(progress, popup, font, layout);
        }
        PopupPositioning.Position position = PopupPositioning.resolve(
                popup,
                layout.dimensions(),
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight()
        );
        BackupPopupRenderer.render(graphics, font, layout, position.x(), position.y());
    }
}
