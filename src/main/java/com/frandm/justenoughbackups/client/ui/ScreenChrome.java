package com.frandm.justenoughbackups.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class ScreenChrome {
    public static final int OUTER = 12;
    public static final int TITLE_Y = 8;
    public static final int VIEW_TOP = 64;
    public static final int FOOTER_H = 38;

    public static final int BG_COLOR = 0xCC050505;
    public static final int TITLE_COLOR = 0xFFFFFFFF;
    public static final int LINE_COLOR = 0x00606060;
    public static final int OUTLINE_COLOR = 0xFF333333;
    public static final int BUTTON_FILL = 0xFF3B3B3B;
    public static final int BUTTON_HOVER = 0xFF606060;
    public static final int BUTTON_DISABLED = 0x66333333;
    public static final int BUTTON_OUTLINE = 0xFF8A8A8A;
    public static final int BUTTON_OUTLINE_DISABLED = 0xFF555555;
    public static final int BUTTON_TEXT = 0xFFFFFFFF;
    public static final int BUTTON_TEXT_DISABLED = 0xFFAAAAAA;

    private ScreenChrome() {
    }

    public static int contentX() {
        return OUTER;
    }

    public static int contentWidth(int screenWidth) {
        return Math.max(1, screenWidth - OUTER * 2);
    }

    public static int viewportTop() {
        return VIEW_TOP;
    }

    public static int footerTop(int screenHeight) {
        return screenHeight - FOOTER_H;
    }

    public static int viewportBottom(int screenHeight) {
        return footerTop(screenHeight);
    }

    public static int viewportRight(int screenWidth) {
        return screenWidth - OUTER;
    }

    public static void drawSurfaceButton(GuiGraphics graphics, net.minecraft.client.gui.Font font, Rect rect, Component text, boolean active, boolean hovered) {
        int fill = !active ? BUTTON_DISABLED : hovered ? BUTTON_HOVER : BUTTON_FILL;

        graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), fill);

        int outline = active ? BUTTON_OUTLINE : BUTTON_OUTLINE_DISABLED;

        // borde arriba
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + 1, outline);

        // borde abajo
        graphics.fill(rect.x(), rect.y() + rect.h() - 1, rect.x() + rect.w(), rect.y() + rect.h(), outline);

        // borde izquierda
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.y() + rect.h(), outline);

        // borde derecha
        graphics.fill(rect.x() + rect.w() - 1, rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), outline);

        int textY = rect.y() + (rect.h() - font.lineHeight) / 2 + 1;

        graphics.drawCenteredString(
                font,
                text,
                rect.x() + rect.w() / 2,
                textY,
                active ? BUTTON_TEXT : BUTTON_TEXT_DISABLED
        );
    }

    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }
}
