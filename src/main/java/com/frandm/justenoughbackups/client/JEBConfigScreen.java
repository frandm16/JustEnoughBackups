package com.frandm.justenoughbackups.client;

import com.frandm.justenoughbackups.backup.model.BackupIntegrityMode;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.progress.BackupProgressPayload;
import com.frandm.justenoughbackups.backup.progress.BackupProgressState;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class JEBConfigScreen extends Screen {
    private static final int OUTER = 12;
    private static final int TITLE_Y = 8;
    private static final int TAB_Y = 32;
    private static final int TAB_H = 20;
    private static final int VIEW_TOP = 64;
    private static final int FOOTER_H = 38;
    private static final int ROW_H = 32;
    private static final int ROW_GAP = 6;
    private static final int ROW_INSET = 8;
    private static final int CONTROL_H = 20;
    private static final int COLUMN_GAP = 8;
    private static final int SCROLL_GUTTER = 12;
    private static final int SCROLL_W = 4;
    private static final int MIN_THUMB_H = 18;
    private static final int DEFAULT_PREVIEW_BYTES = 128 * 1024 * 1024;
    private static final int DEFAULT_TOTAL_BYTES = 304 * 1024 * 1024;

    private static final int BG_COLOR = 0xCC050505;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LINE_COLOR = 0x00606060;
    private static final int ROW_COLOR = 0x66181818;
    private static final int ROW_BAD_COLOR = 0x66AA2222;
    private static final int OUTLINE_COLOR = 0xFF333333;
    private static final int OUTLINE_BAD_COLOR = 0xFFFF5555;

    private final Screen parent;
    private final Map<ConfigTab, Integer> scrollByTab = new EnumMap<>(ConfigTab.class);
    private final Map<String, String> rawInputs = new java.util.HashMap<>();
    private final List<ConfigRow> rows = new ArrayList<>();
    private final List<ValidationError> validationErrors = new ArrayList<>();
    private BackupConfig working;
    private ConfigTab selectedTab = ConfigTab.BACKUPS;
    private PreviewState previewState = PreviewState.RUNNING;
    private ColorTarget selectedColor = ColorTarget.BACKGROUND;
    private String focusedField;
    private int cursor;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private ColorChannel draggingChannel;

    public JEBConfigScreen(Screen parent) {
        super(Component.translatable("screen.justenoughbackups.config.title"));
        this.parent = parent;
        this.working = BackupConfig.get().copy();
    }

    @Override
    protected void init() {
        rebuildRows();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        PopupPositioning.applyRatios(font, working.popup, previewPayload(), width, height);
        buildCurrentRows();
        clampScroll();
        validateAll();
        clampPreviewPosition();
    }

    private void buildCurrentRows() {
        int y = 0;
        int x = contentX();
        int w = contentWidth();
        int controlsW = Math.min(w, Math.clamp(w / 2, 80, 240));
        switch (selectedTab) {
            case BACKUPS -> {
                y = enumRow(x, y, w, controlsW, "Backup mode", working.backupMode, BackupType.values(), value -> working.backupMode = value);
                y = booleanRow(x, y, w, controlsW, "Automatic backups", () -> working.automaticBackupsEnabled, value -> working.automaticBackupsEnabled = value);
                y = booleanRow(x, y, w, controlsW, "Pause when no players joined", () -> working.pauseAutomaticBackupsWithoutPlayers, value -> working.pauseAutomaticBackupsWithoutPlayers = value);
                y = booleanRow(x, y, w, controlsW, "Backup when world starts", () -> working.backupOnServerStart, value -> working.backupOnServerStart = value);
                y = booleanRow(x, y, w, controlsW, "Backup when world closes", () -> working.backupOnServerStop, value -> working.backupOnServerStop = value);
                y = intRow(x, y, w, controlsW, "Automatic interval minutes", () -> working.automaticIntervalMinutes, value -> working.automaticIntervalMinutes = value, 1, Integer.MAX_VALUE);
                y = intRow(x, y, w, controlsW, "Keep full backups", () -> working.retention.full, value -> working.retention.full = value, 1, Integer.MAX_VALUE);
                y = intRow(x, y, w, controlsW, "Keep partial backups", () -> working.retention.incremental, value -> working.retention.incremental = value, 0, Integer.MAX_VALUE);
                y = intRow(x, y, w, controlsW, "Keep differential backups", () -> working.retention.differential, value -> working.retention.differential = value, 0, Integer.MAX_VALUE);
                y = intRow(x, y, w, controlsW, "Command permission level", () -> working.commandPermissionLevel, value -> working.commandPermissionLevel = value, 0, 4);
                y = enumRow(x, y, w, controlsW, "Integrity mode", working.integrityMode, BackupIntegrityMode.values(), value -> working.integrityMode = value);
                textRow(x, y, w, controlsW, "Backup directory", () -> working.backupDirectory, value -> working.backupDirectory = value);
            }
            case HUD -> buildHudRows(x, y, w, controlsW);
            case PREVIEW -> {
            }
        }
    }

    private void buildHudRows(int x, int y, int w, int controlsW) {

        y = textRow(x, y, w, controlsW, "Title", () -> working.popup.title, value -> working.popup.title = value);
        y = textRow(x, y, w, controlsW, "Running text", () -> working.popup.runningText, value -> working.popup.runningText = value);
        y = textRow(x, y, w, controlsW, "Completed text", () -> working.popup.completedText, value -> working.popup.completedText = value);
        y = textRow(x, y, w, controlsW, "Failed text", () -> working.popup.failedText, value -> working.popup.failedText = value);
        y += 6;
        if (w < 430) {
            y = previewStateRow(x, y, w, controlsW);
            y = booleanRow(x, y, w, controlsW, "Show backup popup", () -> working.popup.enabled, value -> working.popup.enabled = value);
            y = booleanRow(x, y, w, controlsW, "Show title", () -> working.popup.showTitle, value -> working.popup.showTitle = value);
            y = booleanRow(x, y, w, controlsW, "Center text", () -> working.popup.centerText, value -> working.popup.centerText = value);
            y = booleanRow(x, y, w, controlsW, "Show border", () -> working.popup.showBorder, value -> working.popup.showBorder = value);
            y += 6;
            for (ColorTarget target : ColorTarget.values()) {
                y = colorRow(x, y, w, controlsW, target);
            }
            y += 6;
            for (ColorChannel channel : ColorChannel.values()) {
                y = channelRow(x, y, w, controlsW, channel);
            }
            return;
        }

        int leftW = (w - COLUMN_GAP) / 2;
        int rightW = w - leftW - COLUMN_GAP;
        int leftX = x;
        int rightX = x + leftW + COLUMN_GAP;
        int leftControls = Math.clamp(leftW / 2, 80, Math.min(controlsW, leftW - ROW_INSET * 2));
        int rightControls = Math.clamp(rightW / 2, 80, Math.min(controlsW, rightW - ROW_INSET * 2));
        int leftY = y;
        leftY = previewStateRow(leftX, leftY, leftW, leftControls);
        leftY = booleanRow(leftX, leftY, leftW, leftControls, "Show backup popup", () -> working.popup.enabled, value -> working.popup.enabled = value);
        leftY = booleanRow(leftX, leftY, leftW, leftControls, "Show title", () -> working.popup.showTitle, value -> working.popup.showTitle = value);
        leftY = booleanRow(leftX, leftY, leftW, leftControls, "Center text", () -> working.popup.centerText, value -> working.popup.centerText = value);
        leftY = booleanRow(leftX, leftY, leftW, leftControls, "Show border", () -> working.popup.showBorder, value -> working.popup.showBorder = value);
        leftY += 6;
        leftY = channelRow(leftX, leftY, leftW, leftControls, ColorChannel.ALPHA);
        channelRow(leftX, leftY, leftW, leftControls, ColorChannel.RED);

        int rightY = y;
        for (ColorTarget target : ColorTarget.values()) {
            rightY = colorRow(rightX, rightY, rightW, rightControls, target);
        }
        rightY += 6;
        rightY = channelRow(rightX, rightY, rightW, rightControls, ColorChannel.GREEN);
        channelRow(rightX, rightY, rightW, rightControls, ColorChannel.BLUE);
    }

    private int booleanRow(int x, int y, int w, int controlsW, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        addRow(x, y, w, label, (graphics, row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            drawButton(graphics, control, toggleMessage(getter.get()), true, control.contains(mouseX, mouseY));
        }, (row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            if (!control.contains(mouseX, mouseY)) {
                return false;
            }
            setter.accept(!getter.get());
            rebuildRows();
            return true;
        });
        return y + ROW_H + ROW_GAP;
    }

    private <T extends Enum<T>> int enumRow(int x, int y, int w, int controlsW, String label, T selected, T[] values, Consumer<T> setter) {
        addRow(x, y, w, label, (graphics, row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            drawButton(graphics, control, Component.literal(selected.toString()), true, control.contains(mouseX, mouseY));
        }, (row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            if (!control.contains(mouseX, mouseY)) {
                return false;
            }
            int next = (selected.ordinal() + 1) % values.length;
            setter.accept(values[next]);
            rebuildRows();
            return true;
        });
        return y + ROW_H + ROW_GAP;
    }

    private int intRow(int x, int y, int w, int controlsW, String label, IntSupplier getter, IntConsumer setter, int min, int max) {
        addRow(x, y, w, label, (graphics, row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            Rect minus = new Rect(control.x, control.y, 22, CONTROL_H);
            Rect plus = new Rect(control.x + control.w - 22, control.y, 22, CONTROL_H);
            Rect field = new Rect(control.x + 26, control.y, control.w - 52, CONTROL_H);
            drawButton(graphics, minus, Component.literal("-"), true, minus.contains(mouseX, mouseY));
            drawField(graphics, field, label, rawInputs.getOrDefault(label, String.valueOf(getter.getAsInt())), mouseX, mouseY);
            drawButton(graphics, plus, Component.literal("+"), true, plus.contains(mouseX, mouseY));
        }, (row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            Rect minus = new Rect(control.x, control.y, 22, CONTROL_H);
            Rect plus = new Rect(control.x + control.w - 22, control.y, 22, CONTROL_H);
            Rect field = new Rect(control.x + 26, control.y, control.w - 52, CONTROL_H);
            if (minus.contains(mouseX, mouseY)) {
                int next = Math.clamp(getter.getAsInt() - 1, min, max);
                setter.accept(next);
                rawInputs.put(label, String.valueOf(next));
                rebuildRows();
                return true;
            }
            if (plus.contains(mouseX, mouseY)) {
                int next = Math.clamp(getter.getAsInt() + 1, min, max);
                setter.accept(next);
                rawInputs.put(label, String.valueOf(next));
                rebuildRows();
                return true;
            }
            if (field.contains(mouseX, mouseY)) {
                focusField(label, rawInputs.getOrDefault(label, String.valueOf(getter.getAsInt())));
                return true;
            }
            return false;
        });
        return y + ROW_H + ROW_GAP;
    }

    private int textRow(int x, int y, int w, int controlsW, String label, Supplier<String> getter, Consumer<String> setter) {
        addRow(x, y, w, label, (graphics, row, screenY, mouseX, mouseY) -> {
            Rect field = controlRect(row, screenY, controlsW);
            drawField(graphics, field, label, rawInputs.getOrDefault(label, value(getter.get())), mouseX, mouseY);
        }, (row, screenY, mouseX, mouseY) -> {
            Rect field = controlRect(row, screenY, controlsW);
            if (!field.contains(mouseX, mouseY)) {
                return false;
            }
            focusField(label, rawInputs.getOrDefault(label, value(getter.get())));
            setter.accept(rawInputs.getOrDefault(label, value(getter.get())));
            return true;
        });
        return y + ROW_H + ROW_GAP;
    }

    private int previewStateRow(int x, int y, int w, int controlsW) {
        addRow(x, y, w, "Preview state", (graphics, row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            Rect running = segment(control, 0, 3);
            Rect done = segment(control, 1, 3);
            Rect failed = segment(control, 2, 3);
            drawButton(graphics, running, Component.translatable("screen.justenoughbackups.config.preview_state.running"), previewState != PreviewState.RUNNING, running.contains(mouseX, mouseY));
            drawButton(graphics, done, Component.translatable("screen.justenoughbackups.config.preview_state.done"), previewState != PreviewState.COMPLETED, done.contains(mouseX, mouseY));
            drawButton(graphics, failed, Component.translatable("screen.justenoughbackups.config.preview_state.failed"), previewState != PreviewState.FAILED, failed.contains(mouseX, mouseY));
        }, (row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            if (segment(control, 0, 3).contains(mouseX, mouseY)) {
                previewState = PreviewState.RUNNING;
                return true;
            }
            if (segment(control, 1, 3).contains(mouseX, mouseY)) {
                previewState = PreviewState.COMPLETED;
                return true;
            }
            if (segment(control, 2, 3).contains(mouseX, mouseY)) {
                previewState = PreviewState.FAILED;
                return true;
            }
            return false;
        });
        return y + ROW_H + ROW_GAP;
    }

    private int colorRow(int x, int y, int w, int controlsW, ColorTarget target) {
        String label = target.label + " color";
        addRow(x, y, w, label, (graphics, row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            Rect edit = new Rect(control.x, control.y, Math.clamp(control.w / 3, 34, 52), CONTROL_H);
            Rect field = new Rect(edit.x + edit.w + 6, control.y, control.w - edit.w - 6, CONTROL_H);
            drawButton(graphics, edit, Component.translatable(target == selectedColor ? "screen.justenoughbackups.config.editing" : "screen.justenoughbackups.config.edit"), true, edit.contains(mouseX, mouseY));
            drawField(graphics, field, label, rawInputs.getOrDefault(label, target.get(working.popup)), mouseX, mouseY);
            int swatchX = row.x + Math.max(54, row.w / 2 - 42);
            graphics.fill(swatchX, screenY + 8, swatchX + 18, screenY + 26, 0xFF000000);
            graphics.fill(swatchX + 1, screenY + 9, swatchX + 17, screenY + 25, target.argb(working.popup));
            graphics.outline(swatchX, screenY + 8, 18, 18, target == selectedColor ? 0xFFFFFFFF : 0xFF555555);
        }, (row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            Rect edit = new Rect(control.x, control.y, Math.clamp(control.w / 3, 34, 52), CONTROL_H);
            Rect field = new Rect(edit.x + edit.w + 6, control.y, control.w - edit.w - 6, CONTROL_H);
            if (edit.contains(mouseX, mouseY)) {
                selectedColor = target;
                return true;
            }
            if (field.contains(mouseX, mouseY)) {
                focusField(label, rawInputs.getOrDefault(label, target.get(working.popup)));
                selectedColor = target;
                return true;
            }
            return false;
        });
        return y + ROW_H + ROW_GAP;
    }

    private int channelRow(int x, int y, int w, int controlsW, ColorChannel channel) {
        addRow(x, y, w, channel.label + " channel", (graphics, row, screenY, mouseX, mouseY) -> {
            Rect slider = controlRect(row, screenY, controlsW);
            drawSlider(graphics, slider, channel);
        }, (row, screenY, mouseX, mouseY) -> {
            Rect slider = controlRect(row, screenY, controlsW);
            if (!slider.contains(mouseX, mouseY)) {
                return false;
            }
            draggingChannel = channel;
            setChannelFromMouse(slider, channel, mouseX);
            return true;
        });
        return y + ROW_H + ROW_GAP;
    }

    private void addRow(int x, int y, int w, String label, RowRenderer renderer, RowClick click) {
        rows.add(new ConfigRow(x, y, w, ROW_H, label, tooltipFor(label), renderer, click));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BG_COLOR); // Background
        graphics.centeredText(font, title,  width / 2, TITLE_Y, TITLE_COLOR); // Title: Just Enough Backups Config
        renderTabs(graphics, mouseX, mouseY);
        graphics.horizontalLine(0, width, viewportTop(), LINE_COLOR);
        graphics.horizontalLine(0, width, viewportBottom(), LINE_COLOR);

        graphics.enableScissor(viewportX(), viewportTop() + 1, viewportRight(), viewportBottom());
        ConfigRow hovered = null;
        for (ConfigRow row : rows) {
            int y = rowScreenY(row) + 1;
            if (y + row.h <= viewportTop() || y >= viewportBottom()) {
                continue;
            }
            int color = isInvalid(row.label) ? ROW_BAD_COLOR : ROW_COLOR;
            graphics.fill(row.x, y, row.x + row.w, y + row.h, color);
            graphics.outline(row.x, y, row.w, row.h, isInvalid(row.label) ? OUTLINE_BAD_COLOR : OUTLINE_COLOR);
            graphics.text(font, Component.literal(trimToWidth(labelText(row.label), Math.max(50, row.w / 2 - 16))), row.x + ROW_INSET, y + 11, 0xFFE0E0E0, true);
            row.renderer.render(graphics, row, y, mouseX, mouseY);
            if (isInsideViewport(mouseY) && mouseX >= row.x && mouseX <= row.x + row.w && mouseY >= y && mouseY <= y + row.h) {
                hovered = row;
            }
        }
        graphics.disableScissor();

        renderScrollbar(graphics);
        renderFooter(graphics, mouseX, mouseY);
        if (hovered != null && !hovered.tooltipKey.isBlank()) {
            drawTooltipBox(graphics, Component.translatable(hovered.tooltipKey).getString(), mouseX, mouseY);
        }
    }

    private void renderTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (ConfigTab tab : ConfigTab.values()) {
            Rect rect = tabRect(tab);
            drawButton(graphics, rect, Component.translatable(tab.key), selectedTab != tab, rect.contains(mouseX, mouseY));
        }
    }


    private void renderFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, footerTop(), width, height, 0x00000000);
        int footerY = height - OUTER - CONTROL_H;
        if (!validationErrors.isEmpty()) {
            String message = validationErrors.getFirst().message().getString();
            graphics.text(font, Component.literal(trimToWidth(message, Math.max(100, width - 280))), OUTER, footerY + 6, 0xFFFF7777, true);
        }
        Rect resetTab = resetTabRect();
        Rect resetAll = resetAllRect();
        Rect save = saveRect();
        drawButton(graphics, resetTab, Component.translatable("screen.justenoughbackups.config.reset_tab"), true, resetTab.contains(mouseX, mouseY));
        drawButton(graphics, resetAll, Component.translatable("screen.justenoughbackups.config.reset_all"), true, resetAll.contains(mouseX, mouseY));
        drawButton(graphics, save, Component.translatable("screen.justenoughbackups.common.save"), validationErrors.isEmpty(), save.contains(mouseX, mouseY));
    }

    private void drawButton(GuiGraphicsExtractor graphics, Rect rect, Component text, boolean active, boolean hovered) {
        int fill = !active ? 0x66333333 : hovered ? 0xFF606060 : 0xFF3B3B3B;
        graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, fill);
        graphics.outline(rect.x, rect.y, rect.w, rect.h, active ? 0xFF8A8A8A : 0xFF555555);
        graphics.centeredText(font, text, rect.x + rect.w / 2, rect.y + 6, active ? 0xFFFFFFFF : 0xFFAAAAAA);
    }

    private void drawField(GuiGraphicsExtractor graphics, Rect rect, String id, String text, int mouseX, int mouseY) {
        boolean focused = id.equals(focusedField);
        graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, focused ? 0xFF202020 : 0xFF151515);
        graphics.outline(rect.x, rect.y, rect.w, rect.h, focused ? 0xFFFFFFFF : rect.contains(mouseX, mouseY) ? 0xFF888888 : 0xFF555555);
        String visible = trimToWidth(text, Math.max(8, rect.w - 8));
        graphics.text(font, visible, rect.x + 4, rect.y + 6, 0xFFE0E0E0, true);
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caretX = rect.x + 4 + font.width(text.substring(0, Math.min(cursor, text.length())));
            graphics.fill(Math.min(caretX, rect.x + rect.w - 3), rect.y + 4, Math.min(caretX + 1, rect.x + rect.w - 2), rect.y + rect.h - 4, 0xFFFFFFFF);
        }
    }

    private void drawSlider(GuiGraphicsExtractor graphics, Rect rect, ColorChannel channel) {
        int value = channel.extract(selectedColor.argb(working.popup));
        graphics.fill(rect.x, rect.y + 8, rect.x + rect.w, rect.y + 12, 0xFF303030);
        int knob = rect.x + Math.round((rect.w - 6) * (value / 255.0F));
        graphics.fill(knob, rect.y + 3, knob + 6, rect.y + 17, 0xFFE0E0E0);
        graphics.outline(rect.x, rect.y, rect.w, rect.h, 0xFF555555);
        graphics.centeredText(font, Component.translatable("screen.justenoughbackups.config.channel_value", channel.label, value), rect.x + rect.w / 2, rect.y + 6, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mouseX = event.x();
        double mouseY = event.y();
        focusedField = null;
        if (handleTabClick(mouseX, mouseY) || handleFooterClick(mouseX, mouseY) || handleScrollbarClick(mouseX, mouseY)) {
            return true;
        }
        if (!isInsideViewport(mouseY)) {
            return true;
        }
        for (ConfigRow row : rows) {
            int y = rowScreenY(row);
            if (mouseX >= row.x && mouseX <= row.x + row.w && mouseY >= y && mouseY <= y + row.h) {
                return row.click.click(row, y, mouseX, mouseY);
            }
        }
        return true;
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        for (ConfigTab tab : ConfigTab.values()) {
            if (tabRect(tab).contains(mouseX, mouseY)) {
                if (tab == ConfigTab.PREVIEW) {
                    selectedTab = ConfigTab.HUD;
                    minecraft.setScreen(new PopupPreviewScreen(this, working.popup, previewPayload()));
                } else {
                    selectedTab = tab;
                    rebuildRows();
                }
                return true;
            }
        }
        return false;
    }

    private Rect tabRect(ConfigTab tab) {
        int areaX = width / 3;
        int areaW = width / 3;
        int gap = 4;
        int tabCount = ConfigTab.values().length;

        int tabW = Math.max(48, (areaW - gap * (tabCount - 1)) / tabCount);
        int totalW = tabW * tabCount + gap * (tabCount - 1);
        int x = areaX + (areaW - totalW) / 2;

        int index = tab.ordinal();
        int tabX = x + index * (tabW + gap);
        return new Rect(tabX, TAB_Y, tabW, TAB_H);
    }

    private boolean handleFooterClick(double mouseX, double mouseY) {
        if (resetTabRect().contains(mouseX, mouseY)) {
            resetCurrentTab();
            return true;
        }
        if (resetAllRect().contains(mouseX, mouseY)) {
            resetAll();
            return true;
        }
        if (saveRect().contains(mouseX, mouseY) && validationErrors.isEmpty()) {
            saveAndClose();
            return true;
        }
        return mouseY >= footerTop();
    }

    private boolean handleScrollbarClick(double mouseX, double mouseY) {
        if (!hasScrollbar() || mouseX < scrollbarX() - 4 || mouseX > scrollbarX() + SCROLL_W + 4 || mouseY < viewportTop() || mouseY > viewportBottom()) {
            return false;
        }
        int thumbY = scrollbarThumbY();
        int thumbH = scrollbarThumbHeight();
        draggingScrollbar = true;
        scrollbarDragOffset = mouseY >= thumbY && mouseY <= thumbY + thumbH ? (int) mouseY - thumbY : thumbH / 2;
        setScrollForThumb((int) mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            setScrollForThumb((int) event.y());
            return true;
        }
        if (draggingChannel != null) {
            ConfigRow row = rowForChannel(draggingChannel);
            if (row != null) {
                setChannelFromMouse(controlRect(row, rowScreenY(row), controlWidth(row.w)), draggingChannel, event.x());
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        draggingChannel = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int next = Math.clamp(currentScroll() - (int) Math.signum(scrollY) * (ROW_H + ROW_GAP), 0, maxScroll());
        scrollByTab.put(selectedTab, next);
        rebuildRows();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (focusedField == null) {
            return super.keyPressed(event);
        }
        String text = rawInputs.getOrDefault(focusedField, "");
        switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0 && !text.isEmpty()) {
                    rawInputs.put(focusedField, text.substring(0, cursor - 1) + text.substring(cursor));
                    cursor--;
                    applyFocusedField();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < text.length()) {
                    rawInputs.put(focusedField, text.substring(0, cursor) + text.substring(cursor + 1));
                    applyFocusedField();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(text.length(), cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = text.length();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER -> {
                focusedField = null;
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focusedField == null || !event.isAllowedChatCharacter()) {
            return false;
        }
        String text = rawInputs.getOrDefault(focusedField, "");
        String inserted = event.codepointAsString();
        int maxLength = colorTargetForRow(focusedField) == null ? 256 : 11;
        if (text.length() + inserted.length() > maxLength) {
            return true;
        }
        rawInputs.put(focusedField, text.substring(0, cursor) + inserted + text.substring(cursor));
        cursor += inserted.length();
        applyFocusedField();
        return true;
    }

    private void applyFocusedField() {
        String value = rawInputs.getOrDefault(focusedField, "");
        switch (focusedField) {
            case "Automatic interval minutes" -> parseInt(value).ifPresent(parsed -> working.automaticIntervalMinutes = parsed);
            case "Keep full backups" -> parseInt(value).ifPresent(parsed -> working.retention.full = parsed);
            case "Keep partial backups" -> parseInt(value).ifPresent(parsed -> working.retention.incremental = parsed);
            case "Keep differential backups" -> parseInt(value).ifPresent(parsed -> working.retention.differential = parsed);
            case "Command permission level" -> parseInt(value).ifPresent(parsed -> working.commandPermissionLevel = parsed);
            case "Backup directory" -> working.backupDirectory = value;
            case "Title" -> working.popup.title = value;
            case "Running text" -> working.popup.runningText = value;
            case "Completed text" -> working.popup.completedText = value;
            case "Failed text" -> working.popup.failedText = value;
            default -> {
                ColorTarget target = colorTargetForRow(focusedField);
                if (target != null) {
                    parseColor(value).ifPresent(color -> target.set(working.popup, formatColor(color)));
                }
            }
        }
        validateAll();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void saveAndClose() {
        validateAll();
        if (validationErrors.isEmpty()) {
            BackupConfig.saveAndApply(working);
            minecraft.setScreen(parent);
        }
    }

    private void resetCurrentTab() {
        BackupConfig defaults = BackupConfig.defaults();
        switch (selectedTab) {
            case BACKUPS -> {
                working.backupMode = defaults.backupMode;
                working.automaticBackupsEnabled = defaults.automaticBackupsEnabled;
                working.pauseAutomaticBackupsWithoutPlayers = defaults.pauseAutomaticBackupsWithoutPlayers;
                working.backupOnServerStart = defaults.backupOnServerStart;
                working.backupOnServerStop = defaults.backupOnServerStop;
                working.automaticIntervalMinutes = defaults.automaticIntervalMinutes;
                working.retention.full = defaults.retention.full;
                working.retention.incremental = defaults.retention.incremental;
                working.retention.differential = defaults.retention.differential;
                working.commandPermissionLevel = defaults.commandPermissionLevel;
                working.integrityMode = defaults.integrityMode;
                working.backupDirectory = defaults.backupDirectory;
            }
            case HUD -> working.popup = defaults.popup.copy();
            case PREVIEW -> {
            }
        }
        rawInputs.clear();
        focusedField = null;
        selectedColor = ColorTarget.BACKGROUND;
        rebuildRows();
    }

    private void resetAll() {
        working = BackupConfig.defaults();
        rawInputs.clear();
        focusedField = null;
        selectedColor = ColorTarget.BACKGROUND;
        previewState = PreviewState.RUNNING;
        scrollByTab.clear();
        rebuildRows();
    }

    private void validateAll() {
        validationErrors.clear();
        validateInt("Automatic interval minutes", working.automaticIntervalMinutes, 1, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.interval_min");
        validateInt("Command permission level", working.commandPermissionLevel, 0, 4, "screen.justenoughbackups.config.error.permission_range");
        validateInt("Keep full backups", working.retention.full, 1, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.full_min");
        validateInt("Keep partial backups", working.retention.incremental, 0, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.partial_min");
        validateInt("Keep differential backups", working.retention.differential, 0, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.differential_min");
        validateRawInt("Automatic interval minutes", 1, Integer.MAX_VALUE);
        validateRawInt("Command permission level", 0, 4);
        validateRawInt("Keep full backups", 1, Integer.MAX_VALUE);
        validateRawInt("Keep partial backups", 0, Integer.MAX_VALUE);
        validateRawInt("Keep differential backups", 0, Integer.MAX_VALUE);
        if (rawInputs.getOrDefault("Backup directory", value(working.backupDirectory)).isBlank()) {
            validationErrors.add(new ValidationError("Backup directory", Component.translatable("screen.justenoughbackups.config.error.backup_directory_empty")));
        }
        for (ColorTarget target : ColorTarget.values()) {
            String label = target.label + " color";
            if (parseColor(rawInputs.getOrDefault(label, target.get(working.popup))).isEmpty()) {
                validationErrors.add(new ValidationError(label, Component.translatable("screen.justenoughbackups.config.error.color", labelText(label))));
            }
        }
    }

    private void validateInt(String label, int value, int min, int max, String key) {
        if (value < min || value > max) {
            validationErrors.add(new ValidationError(label, Component.translatable(key)));
        }
    }

    private void validateRawInt(String label, int min, int max) {
        String raw = rawInputs.get(label);
        if (raw == null) {
            return;
        }
        Optional<Integer> parsed = parseInt(raw);
        if (parsed.isEmpty()) {
            validationErrors.add(new ValidationError(label, Component.translatable("screen.justenoughbackups.config.error.whole_number", labelText(label))));
        } else if (parsed.get() < min || parsed.get() > max) {
            validationErrors.add(new ValidationError(label, Component.translatable("screen.justenoughbackups.config.error.number_range", labelText(label), min, max)));
        }
    }

    private boolean isInvalid(String label) {
        for (ValidationError error : validationErrors) {
            if (error.label.equals(label)) {
                return true;
            }
        }
        return false;
    }

    private BackupProgressPayload previewPayload() {
        BackupProgressState state = switch (previewState) {
            case RUNNING -> BackupProgressState.RUNNING;
            case COMPLETED -> BackupProgressState.COMPLETED;
            case FAILED -> BackupProgressState.FAILED;
        };
        long written = state == BackupProgressState.COMPLETED ? DEFAULT_TOTAL_BYTES : DEFAULT_PREVIEW_BYTES;
        return new BackupProgressPayload("preview", BackupType.FULL, "automatic", written, DEFAULT_TOTAL_BYTES, 42, 100, state);
    }

    private void clampPreviewPosition() {
        PopupPositioning.clampAndRemember(font, working.popup, previewPayload(), width, height);
    }

    private int contentX() {
        return OUTER;
    }

    private int contentWidth() {
        return Math.max(1, width - OUTER * 2 - SCROLL_GUTTER);
    }

    private int controlWidth(int rowW) {
        return Math.clamp(rowW / 2, Math.min(72, rowW - ROW_INSET * 2), Math.max(72, rowW - ROW_INSET * 2));
    }

    private Rect controlRect(ConfigRow row, int screenY, int requestedW) {
        int available = Math.max(1, row.w - ROW_INSET * 2);
        int controlW = Math.clamp(requestedW, Math.min(available, 56), available);
        return new Rect(row.x + row.w - ROW_INSET - controlW, screenY + 6, controlW, CONTROL_H);
    }

    private Rect segment(Rect rect, int index, int count) {
        int gap = 4;
        int each = (rect.w - gap * (count - 1)) / count;
        int x = rect.x + index * (each + gap);
        int w = index == count - 1 ? rect.x + rect.w - x : each;
        return new Rect(x, rect.y, w, rect.h);
    }

    private int rowScreenY(ConfigRow row) {
        return viewportTop() + row.y - currentScroll();
    }

    private int currentScroll() {
        return scrollByTab.getOrDefault(selectedTab, 0);
    }

    private int maxScroll() {
        int contentH = 0;
        for (ConfigRow row : rows) {
            contentH = Math.max(contentH, row.y + row.h);
        }
        return Math.max(0, contentH - viewportHeight());
    }

    private void clampScroll() {
        scrollByTab.put(selectedTab, Math.clamp(currentScroll(), 0, maxScroll()));
    }

    private int viewportX() {
        return OUTER;
    }

    private int viewportRight() {
        return width - OUTER;
    }

    private int viewportTop() {
        return VIEW_TOP;
    }

    private int viewportBottom() {
        return footerTop();
    }

    private int viewportHeight() {
        return Math.max(1, viewportBottom() - viewportTop());
    }

    private int footerTop() {
        return height - FOOTER_H;
    }

    private boolean isInsideViewport(double mouseY) {
        return mouseY >= viewportTop() && mouseY <= viewportBottom();
    }

    private boolean hasScrollbar() {
        return maxScroll() > 0;
    }

    private int scrollbarX() {
        return width - OUTER - (SCROLL_GUTTER + SCROLL_W) / 2;
    }

    private int scrollbarThumbHeight() {
        if (!hasScrollbar()) {
            return viewportHeight();
        }
        return Math.clamp((viewportHeight() * viewportHeight()) / (viewportHeight() + maxScroll()), MIN_THUMB_H, viewportHeight());
    }

    private int scrollbarThumbY() {
        int max = maxScroll();
        int travel = Math.max(1, viewportHeight() - scrollbarThumbHeight());
        return max == 0 ? viewportTop() : viewportTop() + currentScroll() * travel / max;
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics) {
        if (!hasScrollbar()) {
            return;
        }
        int x = scrollbarX();
        graphics.fill(x, viewportTop() + 3, x + SCROLL_W, viewportBottom() - 3, 0x66181818);
        int thumbY = scrollbarThumbY();
        graphics.fill(x, thumbY, x + SCROLL_W, thumbY + scrollbarThumbHeight(), draggingScrollbar ? 0xFFFFFFFF : 0xFF8A8A8A);
    }

    private void setScrollForThumb(int mouseY) {
        int thumbH = scrollbarThumbHeight();
        int travel = Math.max(1, viewportHeight() - thumbH);
        int y = Math.clamp(mouseY - scrollbarDragOffset, viewportTop(), viewportBottom() - thumbH);
        scrollByTab.put(selectedTab, Math.clamp((y - viewportTop()) * maxScroll() / travel, 0, maxScroll()));
    }

    private void setChannelFromMouse(Rect rect, ColorChannel channel, double mouseX) {
        int value = Math.clamp((int) Math.round(((mouseX - rect.x) / Math.max(1.0D, rect.w - 1)) * 255.0D), 0, 255);
        int color = selectedColor.argb(working.popup);
        selectedColor.set(working.popup, formatColor(channel.apply(color, value)));
        rebuildRows();
    }

    private ConfigRow rowForChannel(ColorChannel channel) {
        String label = channel.label + " channel";
        for (ConfigRow row : rows) {
            if (row.label.equals(label)) {
                return row;
            }
        }
        return null;
    }

    private Rect resetTabRect() {
        int y = height - OUTER - CONTROL_H;
        int x = Math.max(OUTER, width - OUTER - 248);
        return new Rect(x, y, 72, CONTROL_H);
    }

    private Rect resetAllRect() {
        Rect resetTab = resetTabRect();
        return new Rect(resetTab.x + 78, resetTab.y, 76, CONTROL_H);
    }

    private Rect saveRect() {
        Rect resetTab = resetTabRect();
        return new Rect(resetTab.x + 190, resetTab.y, 58, CONTROL_H);
    }

    private void focusField(String id, String value) {
        focusedField = id;
        rawInputs.putIfAbsent(id, value);
        cursor = rawInputs.get(id).length();
    }

    private Component toggleMessage(boolean enabled) {
        return Component.translatable(enabled ? "screen.justenoughbackups.config.toggle.on" : "screen.justenoughbackups.config.toggle.off");
    }

    private String labelText(String label) {
        return Component.translatable(labelKey(label)).getString();
    }

    private void drawTooltipBox(GuiGraphicsExtractor graphics, String tooltip, int mouseX, int mouseY) {
        String[] lines = tooltip.split("\\R");
        int tooltipW = 0;
        for (String line : lines) {
            tooltipW = Math.max(tooltipW, font.width(line));
        }
        tooltipW += 12;
        int tooltipH = lines.length * 11 + 8;
        int x = Math.min(mouseX + 12, width - tooltipW - 6);
        int y = Math.min(mouseY + 12, height - tooltipH - 6);
        graphics.fill(x, y, x + tooltipW, y + tooltipH, 0xF0101010);
        graphics.outline(x, y, tooltipW, tooltipH, 0xFF707070);
        for (int i = 0; i < lines.length; i++) {
            graphics.text(font, lines[i], x + 6, y + 5 + i * 11, 0xFFE0E0E0, true);
        }
    }

    private String trimToWidth(String text, int maxWidth) {
        String value = value(text);
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        while (!value.isEmpty() && font.width(value + suffix) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static Optional<Integer> parseInt(String value) {
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parseColor(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() == 6) {
            normalized = "FF" + normalized;
        }
        if (normalized.length() != 8) {
            return Optional.empty();
        }
        try {
            return Optional.of((int) Long.parseLong(normalized, 16));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String formatColor(int color) {
        return String.format(Locale.ROOT, "0x%08X", color);
    }

    private static String labelKey(String label) {
        return switch (label) {
            case "Backup mode" -> "screen.justenoughbackups.config.backup_mode";
            case "Automatic backups" -> "screen.justenoughbackups.config.automatic_backups";
            case "Pause when no players joined" -> "screen.justenoughbackups.config.pause_without_players";
            case "Backup when world starts" -> "screen.justenoughbackups.config.backup_on_start";
            case "Backup when world closes" -> "screen.justenoughbackups.config.backup_on_stop";
            case "Automatic interval minutes" -> "screen.justenoughbackups.config.interval_minutes";
            case "Backup directory" -> "screen.justenoughbackups.config.backup_directory";
            case "Command permission level" -> "screen.justenoughbackups.config.permission_level";
            case "Integrity mode" -> "screen.justenoughbackups.config.integrity_mode";
            case "Keep full backups" -> "screen.justenoughbackups.config.keep_full";
            case "Keep partial backups" -> "screen.justenoughbackups.config.keep_partial";
            case "Keep differential backups" -> "screen.justenoughbackups.config.keep_differential";
            case "Show backup popup" -> "screen.justenoughbackups.config.show_popup";
            case "Show title" -> "screen.justenoughbackups.config.show_title";
            case "Center text" -> "screen.justenoughbackups.config.center_text";
            case "Show border" -> "screen.justenoughbackups.config.show_border";
            case "Preview state" -> "screen.justenoughbackups.config.preview_state";
            case "Title" -> "screen.justenoughbackups.config.popup_title";
            case "Running text" -> "screen.justenoughbackups.config.running_text";
            case "Completed text" -> "screen.justenoughbackups.config.completed_text";
            case "Failed text" -> "screen.justenoughbackups.config.failed_text";
            case "Background color" -> "screen.justenoughbackups.config.background_color";
            case "Running color" -> "screen.justenoughbackups.config.running_color";
            case "Completed color" -> "screen.justenoughbackups.config.completed_color";
            case "Failed color" -> "screen.justenoughbackups.config.failed_color";
            case "Text color" -> "screen.justenoughbackups.config.text_color";
            case "A channel" -> "screen.justenoughbackups.config.a_channel";
            case "R channel" -> "screen.justenoughbackups.config.r_channel";
            case "G channel" -> "screen.justenoughbackups.config.g_channel";
            case "B channel" -> "screen.justenoughbackups.config.b_channel";
            default -> "screen.justenoughbackups.config.unknown";
        };
    }

    private static String tooltipFor(String label) {
        return labelKey(label) + ".tooltip";
    }

    private static ColorTarget colorTargetForRow(String label) {
        for (ColorTarget target : ColorTarget.values()) {
            if (label.equals(target.label + " color")) {
                return target;
            }
        }
        return null;
    }

    private enum ConfigTab {
        BACKUPS("screen.justenoughbackups.config.tab.backups"),
        HUD("screen.justenoughbackups.config.tab.hud"),
        PREVIEW("screen.justenoughbackups.config.tab.preview");

        private final String key;

        ConfigTab(String key) {
            this.key = key;
        }
    }

    private enum PreviewState {
        RUNNING,
        COMPLETED,
        FAILED
    }

    private enum ColorTarget {
        BACKGROUND("Background") {
            @Override
            String get(BackupConfig.Popup popup) {
                return popup.backgroundColor;
            }

            @Override
            void set(BackupConfig.Popup popup, String value) {
                popup.backgroundColor = value;
            }

            @Override
            int argb(BackupConfig.Popup popup) {
                return popup.backgroundColorArgb();
            }
        },
        RUNNING("Running") {
            @Override
            String get(BackupConfig.Popup popup) {
                return popup.runningColor;
            }

            @Override
            void set(BackupConfig.Popup popup, String value) {
                popup.runningColor = value;
            }

            @Override
            int argb(BackupConfig.Popup popup) {
                return popup.runningColorArgb();
            }
        },
        COMPLETED("Completed") {
            @Override
            String get(BackupConfig.Popup popup) {
                return popup.completedColor;
            }

            @Override
            void set(BackupConfig.Popup popup, String value) {
                popup.completedColor = value;
            }

            @Override
            int argb(BackupConfig.Popup popup) {
                return popup.completedColorArgb();
            }
        },
        FAILED("Failed") {
            @Override
            String get(BackupConfig.Popup popup) {
                return popup.failedColor;
            }

            @Override
            void set(BackupConfig.Popup popup, String value) {
                popup.failedColor = value;
            }

            @Override
            int argb(BackupConfig.Popup popup) {
                return popup.failedColorArgb();
            }
        },
        TEXT("Text") {
            @Override
            String get(BackupConfig.Popup popup) {
                return popup.textColor;
            }

            @Override
            void set(BackupConfig.Popup popup, String value) {
                popup.textColor = value;
            }

            @Override
            int argb(BackupConfig.Popup popup) {
                return popup.textColorArgb();
            }
        };

        private final String label;

        ColorTarget(String label) {
            this.label = label;
        }

        abstract String get(BackupConfig.Popup popup);

        abstract void set(BackupConfig.Popup popup, String value);

        abstract int argb(BackupConfig.Popup popup);
    }

    private enum ColorChannel {
        ALPHA("A", 24),
        RED("R", 16),
        GREEN("G", 8),
        BLUE("B", 0);

        private final String label;
        private final int shift;

        ColorChannel(String label, int shift) {
            this.label = label;
            this.shift = shift;
        }

        int extract(int color) {
            return (color >>> shift) & 0xFF;
        }

        int apply(int color, int value) {
            int mask = 0xFF << shift;
            return (color & ~mask) | (Math.clamp(value, 0, 255) << shift);
        }
    }

    @FunctionalInterface
    private interface RowRenderer {
        void render(GuiGraphicsExtractor graphics, ConfigRow row, int screenY, int mouseX, int mouseY);
    }

    @FunctionalInterface
    private interface RowClick {
        boolean click(ConfigRow row, int screenY, double mouseX, double mouseY);
    }

    private record ConfigRow(int x, int y, int w, int h, String label, String tooltipKey, RowRenderer renderer, RowClick click) {
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    private record ValidationError(String label, Component message) {
    }
}
