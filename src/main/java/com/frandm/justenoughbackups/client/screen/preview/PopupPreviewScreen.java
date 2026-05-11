package com.frandm.justenoughbackups.client.screen.preview;

import com.frandm.justenoughbackups.backup.progress.BackupProgressPayload;
import com.frandm.justenoughbackups.client.screen.config.JEBConfigScreen;
import com.frandm.justenoughbackups.client.ui.popup.BackupPopupRenderer;
import com.frandm.justenoughbackups.client.ui.popup.PopupPositioning;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class PopupPreviewScreen extends Screen {
    public static final int LINE_COLOR = 0x65BCBCBC;
    private static final int SNAP_DISTANCE = 6;
    private static final int GUIDE_SEGMENT = 1;
    private static final int GUIDE_GAP = 2;

    private final JEBConfigScreen parent;
    private final BackupConfig.Popup popup;
    private final BackupProgressPayload previewPayload;
    private boolean draggingPreview;
    private int dragOffsetX;
    private int dragOffsetY;

    public PopupPreviewScreen(JEBConfigScreen parent, BackupConfig.Popup popup, BackupProgressPayload previewPayload) {
        super(Component.translatable("screen.justenoughbackups.preview.title"));
        this.parent = parent;
        this.popup = popup;
        this.previewPayload = previewPayload;
    }

    @Override
    protected void init() {
        PopupPositioning.applyRatios(font, popup, previewPayload, width, height);
        clampPosition();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x00000000);

        drawVerticalGuide(graphics, width / 3, LINE_COLOR);
        drawVerticalGuide(graphics, 2 * width / 3, LINE_COLOR);
        drawHorizontalGuide(graphics, height / 3, LINE_COLOR);
        drawHorizontalGuide(graphics, 2 * height / 3, LINE_COLOR);

        BackupPopupRenderer.render(graphics, font, popup, previewPayload, popup.x, popup.y);
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
            PopupPositioning.rememberRatios(font, popup, previewPayload, width, height);
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
        parent.refreshAfterPreview();
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
        PopupPositioning.rememberRatios(font, popup, previewPayload, width, height);
    }

    private void clampPosition() {
        clampPosition(BackupPopupRenderer.measure(font, popup, previewPayload));
        PopupPositioning.rememberRatios(font, popup, previewPayload, width, height);
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

    private void drawVerticalGuide(GuiGraphics graphics, int x, int color) {
        for (int y = 0; y < height; y += GUIDE_SEGMENT + GUIDE_GAP) {
            graphics.fill(x, y, x + 1, Math.min(height, y + GUIDE_SEGMENT), color);
        }
    }

    private void drawHorizontalGuide(GuiGraphics graphics, int y, int color) {
        for (int x = 0; x < width; x += GUIDE_SEGMENT + GUIDE_GAP) {
            graphics.fill(x, y, Math.min(width, x + GUIDE_SEGMENT), y + 1, color);
        }
    }
}
