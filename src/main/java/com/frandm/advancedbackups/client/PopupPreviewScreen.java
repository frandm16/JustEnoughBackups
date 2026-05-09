package com.frandm.advancedbackups.client;

import com.frandm.advancedbackups.backup.progress.BackupProgressPayload;
import com.frandm.advancedbackups.config.BackupConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class PopupPreviewScreen extends Screen {
    private static final int SNAP_DISTANCE = 6;

    private final AdvancedBackupsConfigScreen parent;
    private final BackupConfig.Popup popup;
    private final BackupProgressPayload previewPayload;
    private boolean draggingPreview;
    private int dragOffsetX;
    private int dragOffsetY;

    PopupPreviewScreen(AdvancedBackupsConfigScreen parent, BackupConfig.Popup popup, BackupProgressPayload previewPayload) {
        super(Component.literal("Popup Preview"));
        this.parent = parent;
        this.popup = popup;
        this.previewPayload = previewPayload;
    }

    @Override
    protected void init() {
        clampPosition();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x00050505);

        BackupPopupRenderer.Dimensions dimensions = BackupPopupRenderer.measure(font, popup, previewPayload);
        boolean nearCenterX = Math.abs(centerX(dimensions) - width / 2) <= SNAP_DISTANCE;
        boolean nearCenterY = Math.abs(centerY(dimensions) - height / 2) <= SNAP_DISTANCE;
        int verticalColor = nearCenterX ? 0xAA55FFFF : 0x55707070;
        int horizontalColor = nearCenterY ? 0xAA55FFFF : 0x55707070;
        graphics.fill(width / 2, 0, width / 2 + 1, height, verticalColor);
        graphics.fill(0, height / 2, width, height / 2 + 1, horizontalColor);

        BackupPopupRenderer.render(graphics, font, popup, previewPayload, popup.x, popup.y);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsidePreview(event.x(), event.y())) {
            draggingPreview = true;
            dragOffsetX = (int) event.x() - popup.x;
            dragOffsetY = (int) event.y() - popup.y;
            setDragging(true);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingPreview) {
            popup.x = (int) event.x() - dragOffsetX;
            popup.y = (int) event.y() - dragOffsetY;
            snapAndClamp();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPreview = false;
        setDragging(false);
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        int amount = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> {
                popup.x -= amount;
                snapAndClamp();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                popup.x += amount;
                snapAndClamp();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                popup.y -= amount;
                snapAndClamp();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                popup.y += amount;
                snapAndClamp();
                return true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
        parent.rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        BackupPopupRenderer.Dimensions dimensions = BackupPopupRenderer.measure(font, popup, previewPayload);
        return mouseX >= popup.x - 4
                && mouseX <= popup.x + dimensions.width()
                && mouseY >= popup.y - 4
                && mouseY <= popup.y + dimensions.height();
    }

    private void snapAndClamp() {
        BackupPopupRenderer.Dimensions dimensions = BackupPopupRenderer.measure(font, popup, previewPayload);
        int targetX = width / 2 - dimensions.width() / 2;
        int targetY = height / 2 - dimensions.height() / 2;
        if (Math.abs(centerX(dimensions) - width / 2) <= SNAP_DISTANCE) {
            popup.x = targetX;
        }
        if (Math.abs(centerY(dimensions) - height / 2) <= SNAP_DISTANCE) {
            popup.y = targetY;
        }
        clampPosition(dimensions);
    }

    private void clampPosition() {
        clampPosition(BackupPopupRenderer.measure(font, popup, previewPayload));
    }

    private void clampPosition(BackupPopupRenderer.Dimensions dimensions) {
        popup.x = Math.clamp(popup.x, 4, Math.max(4, width - dimensions.width()));
        popup.y = Math.clamp(popup.y, 4, Math.max(4, height - dimensions.height()));
    }

    private int centerX(BackupPopupRenderer.Dimensions dimensions) {
        return popup.x + dimensions.width() / 2;
    }

    private int centerY(BackupPopupRenderer.Dimensions dimensions) {
        return popup.y + dimensions.height() / 2;
    }
}
