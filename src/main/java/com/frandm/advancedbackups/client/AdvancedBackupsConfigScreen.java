package com.frandm.advancedbackups.client;

import com.frandm.advancedbackups.backup.model.BackupIntegrityMode;
import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.backup.progress.BackupProgressPayload;
import com.frandm.advancedbackups.backup.progress.BackupProgressState;
import com.frandm.advancedbackups.config.BackupConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
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

public final class AdvancedBackupsConfigScreen extends Screen {
    private static final int OUTER_MARGIN = 12;
    private static final int TAB_WIDTH = 112;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_GAP = 6;
    private static final int HEADER_HEIGHT = 34;
    private static final int CONTROL_HEIGHT = 20;
    private static final int DEFAULT_PREVIEW_BYTES = 128 * 1024 * 1024;
    private static final int DEFAULT_TOTAL_BYTES = 304 * 1024 * 1024;

    private final Screen parent;
    private BackupConfig working;
    private ConfigTab selectedTab = ConfigTab.BACKUPS;
    private PreviewState previewState = PreviewState.RUNNING;
    private ColorTarget selectedColor = ColorTarget.BACKGROUND;
    private final Map<ConfigTab, Integer> scrollByTab = new EnumMap<>(ConfigTab.class);
    private final List<RowInfo> rows = new ArrayList<>();
    private final List<ValidationError> validationErrors = new ArrayList<>();
    private final Map<String, String> rawInputs = new java.util.HashMap<>();
    private final Map<ColorChannel, ColorSlider> sliders = new EnumMap<>(ColorChannel.class);
    private Button saveButton;

    public AdvancedBackupsConfigScreen(Screen parent) {
        super(Component.literal("Advanced Backups"));
        this.parent = parent;
        this.working = BackupConfig.get().copy();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        rows.clear();
        sliders.clear();

        int tabX = OUTER_MARGIN;
        int tabY = OUTER_MARGIN + 24;
        for (ConfigTab tab : ConfigTab.values()) {
            ConfigTab captured = tab;
            Button button = Button.builder(Component.literal(tab.label), ignored -> {
                        selectedTab = captured;
                        rebuildWidgets();
                    })
                    .bounds(tabX, tabY, TAB_WIDTH, CONTROL_HEIGHT)
                    .build();
            button.active = selectedTab != tab;
            addRenderableWidget(button);
            tabY += 24;
        }

        int contentX = contentX();
        int contentY = contentTop();
        int contentW = contentWidth();
        int controlsW = Math.max(120, Math.min(220, contentW / 2));
        int y = contentY + HEADER_HEIGHT - currentScroll();

        switch (selectedTab) {
            case BACKUPS -> y = buildBackups(contentX, y, contentW, controlsW);
            case RETENTION -> y = buildRetention(contentX, y, contentW, controlsW);
            case PERMISSIONS -> y = buildPermissions(contentX, y, contentW, controlsW);
            case INTEGRITY -> y = buildIntegrity(contentX, y, contentW, controlsW);
            case HUD -> y = buildHud(contentX, y, contentW, controlsW);
        }

        int footerY = height - OUTER_MARGIN - CONTROL_HEIGHT;
        saveButton = addRenderableWidget(Button.builder(Component.literal("Save"), ignored -> saveAndClose())
                .bounds(width - OUTER_MARGIN - 282, footerY, 58, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(width - OUTER_MARGIN - 218, footerY, 58, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Reset Tab"), ignored -> resetCurrentTab())
                .bounds(width - OUTER_MARGIN - 154, footerY, 72, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Reset All"), ignored -> resetAll())
                .bounds(width - OUTER_MARGIN - 76, footerY, 76, CONTROL_HEIGHT)
                .build());

        clampPreviewPosition();
        validateAll();
    }

    private int buildBackups(int x, int y, int width, int controlsW) {
        y = addEnumRow(x, y, width, controlsW, "Backup mode", working.backupMode, BackupType.values(), value -> working.backupMode = value);
        y = addBooleanRow(x, y, width, controlsW, "Automatic backups", () -> working.automaticBackupsEnabled, value -> working.automaticBackupsEnabled = value);
        y = addBooleanRow(x, y, width, controlsW, "Pause when no players joined", () -> working.pauseAutomaticBackupsWithoutPlayers, value -> working.pauseAutomaticBackupsWithoutPlayers = value);
        y = addBooleanRow(x, y, width, controlsW, "Backup when world starts", () -> working.backupOnServerStart, value -> working.backupOnServerStart = value);
        y = addBooleanRow(x, y, width, controlsW, "Backup when world closes", () -> working.backupOnServerStop, value -> working.backupOnServerStop = value);
        y = addIntRow(x, y, width, controlsW, "Automatic interval minutes", () -> working.automaticIntervalMinutes, value -> working.automaticIntervalMinutes = value, 1, Integer.MAX_VALUE);
        return addTextRow(x, y, width, controlsW, "Backup directory", () -> working.backupDirectory, value -> working.backupDirectory = value, false);
    }

    private int buildRetention(int x, int y, int width, int controlsW) {
        y = addIntRow(x, y, width, controlsW, "Keep full backups", () -> working.retention.full, value -> working.retention.full = value, 1, Integer.MAX_VALUE);
        y = addIntRow(x, y, width, controlsW, "Keep partial backups", () -> working.retention.incremental, value -> working.retention.incremental = value, 0, Integer.MAX_VALUE);
        return addIntRow(x, y, width, controlsW, "Keep differential backups", () -> working.retention.differential, value -> working.retention.differential = value, 0, Integer.MAX_VALUE);
    }

    private int buildPermissions(int x, int y, int width, int controlsW) {
        return addIntRow(x, y, width, controlsW, "Command permission level", () -> working.commandPermissionLevel, value -> working.commandPermissionLevel = value, 0, 4);
    }

    private int buildIntegrity(int x, int y, int width, int controlsW) {
        return addEnumRow(x, y, width, controlsW, "Integrity mode", working.integrityMode, BackupIntegrityMode.values(), value -> working.integrityMode = value);
    }

    private int buildHud(int x, int y, int width, int controlsW) {
        y = addBooleanRow(x, y, width, controlsW, "Show backup popup", () -> working.popup.enabled, value -> working.popup.enabled = value);
        y = addBooleanRow(x, y, width, controlsW, "Show title", () -> working.popup.showTitle, value -> working.popup.showTitle = value);
        y = addBooleanRow(x, y, width, controlsW, "Center text", () -> working.popup.centerText, value -> working.popup.centerText = value);
        y = addBooleanRow(x, y, width, controlsW, "Show border", () -> working.popup.showBorder, value -> working.popup.showBorder = value);
        y = addPreviewStateRow(x, y, width, controlsW);
        y = addPreviewLayoutRow(x, y, width, controlsW);
        y = addIntRow(x, y, width, controlsW, "Popup X", () -> working.popup.x, value -> {
            working.popup.x = value;
            clampPreviewPosition();
        }, 0, Integer.MAX_VALUE);
        y = addIntRow(x, y, width, controlsW, "Popup Y", () -> working.popup.y, value -> {
            working.popup.y = value;
            clampPreviewPosition();
        }, 0, Integer.MAX_VALUE);

        y = addTextRow(x, y, width, controlsW, "Title", () -> working.popup.title, value -> working.popup.title = value, true);
        y = addTextRow(x, y, width, controlsW, "Running text", () -> working.popup.runningText, value -> working.popup.runningText = value, true);
        y = addTextRow(x, y, width, controlsW, "Completed text", () -> working.popup.completedText, value -> working.popup.completedText = value, true);
        y = addTextRow(x, y, width, controlsW, "Failed text", () -> working.popup.failedText, value -> working.popup.failedText = value, true);

        y += 6;
        for (ColorTarget target : ColorTarget.values()) {
            y = addColorRow(x, y, width, controlsW, target);
        }

        y += 6;
        int controlX = x + width - controlsW;
        for (ColorChannel channel : ColorChannel.values()) {
            rows.add(new RowInfo(y, ROW_HEIGHT, channel.label + " channel", tooltipFor(channel.label + " channel")));
            if (isRowInteractive(y)) {
                ColorSlider slider = new ColorSlider(controlX, y, controlsW, CONTROL_HEIGHT, channel, this::setSelectedChannel);
                sliders.put(channel, addRenderableWidget(slider));
            }
            y += ROW_HEIGHT + ROW_GAP;
        }
        syncSlidersFromSelectedColor();
        return y;
    }

    private int addBooleanRow(int x, int y, int width, int controlsW, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        addRow(label, y, false);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlX = x + width - controlsW;
        Button button = Button.builder(toggleMessage(getter.get()), ignored -> {
                    setter.accept(!getter.get());
                    rebuildWidgets();
                })
                .bounds(controlX, y + 6, controlsW, CONTROL_HEIGHT)
                .build();
        addRenderableWidget(button);
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private <T extends Enum<T>> int addEnumRow(int x, int y, int width, int controlsW, String label, T selected, T[] values, Consumer<T> setter) {
        addRow(label, y, false);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlX = x + width - controlsW;
        addRenderableWidget(CycleButton.builder((T value) -> Component.literal(value.toString()), selected)
                .withValues(values)
                .create(controlX, y + 6, controlsW, CONTROL_HEIGHT, Component.literal(label), (button, value) -> setter.accept(value)));
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private int addIntRow(int x, int y, int width, int controlsW, String label, IntSupplier getter, IntConsumer setter, int min, int max) {
        addRow(label, y, false);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlX = x + width - controlsW;
        int stepW = 22;
        addRenderableWidget(Button.builder(Component.literal("-"), ignored -> {
                    int next = Math.clamp(getter.getAsInt() - 1, min, max);
                    setter.accept(next);
                    rawInputs.put(label, String.valueOf(next));
                    rebuildWidgets();
                })
                .bounds(controlX, y + 6, stepW, CONTROL_HEIGHT)
                .build());
        EditBox field = new EditBox(font, controlX + stepW + 4, y + 6, controlsW - (stepW * 2) - 8, CONTROL_HEIGHT, Component.literal(label));
        field.setValue(rawInputs.getOrDefault(label, String.valueOf(getter.getAsInt())));
        field.setResponder(value -> {
            rawInputs.put(label, value);
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed >= min && parsed <= max) {
                    setter.accept(parsed);
                }
            } catch (NumberFormatException ignored) {
            }
            validateAll();
        });
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
                    int next = Math.clamp(getter.getAsInt() + 1, min, max);
                    setter.accept(next);
                    rawInputs.put(label, String.valueOf(next));
                    rebuildWidgets();
                })
                .bounds(controlX + controlsW - stepW, y + 6, stepW, CONTROL_HEIGHT)
                .build());
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private int addTextRow(int x, int y, int width, int controlsW, String label, Supplier<String> getter, Consumer<String> setter, boolean allowBlankAsDefault) {
        addRow(label, y, false);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlX = x + width - controlsW;
        EditBox field = new EditBox(font, controlX, y + 6, controlsW, CONTROL_HEIGHT, Component.literal(label));
        field.setValue(rawInputs.getOrDefault(label, value(getter.get())));
        field.setResponder(text -> {
            rawInputs.put(label, text);
            setter.accept(text);
            validateAll();
        });
        addRenderableWidget(field);
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private int addPreviewStateRow(int x, int y, int width, int controlsW) {
        addRow("Preview state", y, false);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlX = x + width - controlsW;
        int buttonW = Math.max(50, (controlsW - 8) / 3);
        addRenderableWidget(Button.builder(Component.literal("Running"), ignored -> previewState = PreviewState.RUNNING)
                .bounds(controlX, y + 6, buttonW, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> previewState = PreviewState.COMPLETED)
                .bounds(controlX + buttonW + 4, y + 6, buttonW, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Failed"), ignored -> previewState = PreviewState.FAILED)
                .bounds(controlX + (buttonW + 4) * 2, y + 6, buttonW, CONTROL_HEIGHT)
                .build());
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private int addPreviewLayoutRow(int x, int y, int width, int controlsW) {
        addRow("Preview Layout", y, false);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlX = x + width - controlsW;
        addRenderableWidget(Button.builder(Component.literal("Open"), ignored ->
                        minecraft.setScreen(new PopupPreviewScreen(this, working.popup, previewPayload())))
                .bounds(controlX, y + 6, controlsW, CONTROL_HEIGHT)
                .build());
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private int addColorRow(int x, int y, int width, int controlsW, ColorTarget target) {
        addRow(target.label + " color", y, false);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlX = x + width - controlsW;
        addRenderableWidget(Button.builder(Component.literal(target == selectedColor ? "Editing" : "Edit"), ignored -> {
                    selectedColor = target;
                    syncSlidersFromSelectedColor();
                    rebuildWidgets();
                })
                .bounds(controlX, y + 6, 52, CONTROL_HEIGHT)
                .build());
        EditBox field = new EditBox(font, controlX + 58, y + 6, controlsW - 58, CONTROL_HEIGHT, Component.literal(target.label + " color"));
        field.setMaxLength(11);
        field.setValue(rawInputs.getOrDefault(target.label + " color", target.get(working.popup)));
        field.setResponder(text -> {
            rawInputs.put(target.label + " color", text);
            parseColor(text).ifPresent(color -> {
                target.set(working.popup, formatColor(color));
                if (target == selectedColor) {
                    syncSlidersFromSelectedColor();
                }
            });
            validateAll();
        });
        addRenderableWidget(field);
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private void addRow(String label, int y, boolean invalid) {
        rows.add(new RowInfo(y, ROW_HEIGHT, label, tooltipFor(label)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC050505);
        graphics.text(font, title, OUTER_MARGIN, 8, 0xFFFFFFFF, true);

        graphics.fill(OUTER_MARGIN - 4, OUTER_MARGIN + 20, OUTER_MARGIN + TAB_WIDTH + 4, height - FOOTER_HEIGHT - 2, 0xDD111111);
        graphics.outline(OUTER_MARGIN - 4, OUTER_MARGIN + 20, TAB_WIDTH + 8, height - FOOTER_HEIGHT - OUTER_MARGIN - 18, 0xFF4A4A4A);

        int panelX = contentX() - 4;
        int panelY = contentTop();
        int panelW = contentWidth() + 8;
        int panelH = height - FOOTER_HEIGHT - panelY - 4;
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xBB101010);
        graphics.outline(panelX, panelY, panelW, panelH, 0xFF4A4A4A);
        graphics.text(font, Component.literal(selectedTab.label), contentX(), panelY + 8, 0xFFFFFFFF, true);

        RowInfo hoveredRow = null;
        for (RowInfo row : rows) {
            if (row.y + row.height < panelY + HEADER_HEIGHT || row.y > height - FOOTER_HEIGHT - 8) {
                continue;
            }
            int color = isInvalid(row.label) ? 0x66AA2222 : 0x66181818;
            graphics.fill(contentX(), row.y, contentX() + contentWidth(), row.y + row.height, color);
            graphics.outline(contentX(), row.y, contentWidth(), row.height, isInvalid(row.label) ? 0xFFFF5555 : 0xFF333333);
            graphics.text(font, Component.literal(trimToWidth(row.label, Math.max(50, contentWidth() / 2 - 16))), contentX() + 8, row.y + 11, 0xFFE0E0E0, true);
            drawColorSwatch(graphics, row);
            if (mouseX >= contentX() && mouseX <= contentX() + contentWidth() && mouseY >= row.y && mouseY <= row.y + row.height) {
                hoveredRow = row;
            }
        }

        if (!validationErrors.isEmpty()) {
            String message = validationErrors.getFirst().message;
            graphics.text(font, Component.literal(trimToWidth(message, Math.max(100, width - 230))), contentX(), height - 22, 0xFFFF7777, true);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (hoveredRow != null && !hoveredRow.tooltip.isBlank()) {
            drawTooltipBox(graphics, hoveredRow.tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll();
        int next = Math.clamp(currentScroll() - (int) (scrollY * 24), 0, max);
        scrollByTab.put(selectedTab, next);
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
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
        if (!validationErrors.isEmpty()) {
            return;
        }
        BackupConfig.saveAndApply(working);
        minecraft.setScreen(parent);
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
                working.backupDirectory = defaults.backupDirectory;
            }
            case RETENTION -> {
                working.retention.full = defaults.retention.full;
                working.retention.incremental = defaults.retention.incremental;
                working.retention.differential = defaults.retention.differential;
            }
            case PERMISSIONS -> working.commandPermissionLevel = defaults.commandPermissionLevel;
            case INTEGRITY -> working.integrityMode = defaults.integrityMode;
            case HUD -> {
                working.popup = defaults.popup.copy();
                selectedColor = ColorTarget.BACKGROUND;
                previewState = PreviewState.RUNNING;
            }
        }
        rawInputs.clear();
        rebuildWidgets();
    }

    private void resetAll() {
        working = BackupConfig.defaults();
        rawInputs.clear();
        selectedColor = ColorTarget.BACKGROUND;
        previewState = PreviewState.RUNNING;
        scrollByTab.clear();
        rebuildWidgets();
    }

    private void validateAll() {
        validationErrors.clear();
        validateIntInput("Automatic interval minutes", 1, Integer.MAX_VALUE);
        validateIntInput("Keep full backups", 1, Integer.MAX_VALUE);
        validateIntInput("Keep partial backups", 0, Integer.MAX_VALUE);
        validateIntInput("Keep differential backups", 0, Integer.MAX_VALUE);
        validateIntInput("Command permission level", 0, 4);

        if (working.automaticIntervalMinutes < 1) {
            validationErrors.add(new ValidationError("Automatic interval minutes", "Automatic interval minutes must be at least 1."));
        }
        if (working.commandPermissionLevel < 0 || working.commandPermissionLevel > 4) {
            validationErrors.add(new ValidationError("Command permission level", "Command permission level must be between 0 and 4."));
        }
        if (working.retention.full < 1) {
            validationErrors.add(new ValidationError("Keep full backups", "Keep full backups must be at least 1."));
        }
        if (working.retention.incremental < 0) {
            validationErrors.add(new ValidationError("Keep partial backups", "Keep partial backups cannot be negative."));
        }
        if (working.retention.differential < 0) {
            validationErrors.add(new ValidationError("Keep differential backups", "Keep differential backups cannot be negative."));
        }
        String backupDirectory = rawInputs.getOrDefault("Backup directory", value(working.backupDirectory));
        if (backupDirectory.isBlank()) {
            validationErrors.add(new ValidationError("Backup directory", "Backup directory cannot be empty."));
        }
        for (ColorTarget target : ColorTarget.values()) {
            String label = target.label + " color";
            if (parseColor(rawInputs.getOrDefault(label, target.get(working.popup))).isEmpty()) {
                validationErrors.add(new ValidationError(target.label + " color", target.label + " color must be #RRGGBB, #AARRGGBB, or 0xAARRGGBB."));
            }
        }
        saveButtonActive();
    }

    private void validateIntInput(String label, int min, int max) {
        String raw = rawInputs.get(label);
        if (raw == null) {
            return;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < min || parsed > max) {
                validationErrors.add(new ValidationError(label, label + " must be between " + min + " and " + max + "."));
            }
        } catch (NumberFormatException exception) {
            validationErrors.add(new ValidationError(label, label + " must be a whole number."));
        }
    }

    private void saveButtonActive() {
        if (saveButton != null) {
            saveButton.active = validationErrors.isEmpty();
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

    private void drawColorSwatch(GuiGraphicsExtractor graphics, RowInfo row) {
        ColorTarget target = colorTargetForRow(row.label);
        if (target == null) {
            return;
        }
        int x = contentX() + Math.max(54, contentWidth() / 2 - 42);
        int y = row.y + 8;
        graphics.fill(x, y, x + 18, y + 18, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, target.argb(working.popup));
        graphics.outline(x, y, 18, 18, target == selectedColor ? 0xFFFFFFFF : 0xFF555555);
    }

    private void drawTooltipBox(GuiGraphicsExtractor graphics, String tooltip, int mouseX, int mouseY) {
        String[] lines = tooltip.split("\\R");
        int tooltipWidth = 0;
        for (String line : lines) {
            tooltipWidth = Math.max(tooltipWidth, font.width(line));
        }
        tooltipWidth += 12;
        int tooltipHeight = lines.length * 11 + 8;
        int x = Math.min(mouseX + 12, width - tooltipWidth - 6);
        int y = Math.min(mouseY + 12, height - tooltipHeight - 6);
        graphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, 0xF0101010);
        graphics.outline(x, y, tooltipWidth, tooltipHeight, 0xFF707070);
        for (int i = 0; i < lines.length; i++) {
            graphics.text(font, Component.literal(lines[i]), x + 6, y + 5 + i * 11, 0xFFE0E0E0, true);
        }
    }

    private void setSelectedChannel(ColorChannel channel, int value) {
        int color = selectedColor.argb(working.popup);
        int updated = channel.apply(color, value);
        selectedColor.set(working.popup, formatColor(updated));
        rebuildWidgets();
    }

    private void syncSlidersFromSelectedColor() {
        int color = selectedColor.argb(working.popup);
        for (ColorChannel channel : ColorChannel.values()) {
            ColorSlider slider = sliders.get(channel);
            if (slider != null) {
                slider.setChannelValue(channel.extract(color));
            }
        }
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        BackupPopupRenderer.Dimensions dimensions = BackupPopupRenderer.measure(font, working.popup, previewPayload());
        return mouseX >= working.popup.x - 5
                && mouseX <= working.popup.x + dimensions.width()
                && mouseY >= working.popup.y - 5
                && mouseY <= working.popup.y + dimensions.height();
    }

    private void clampPreviewPosition() {
        BackupPopupRenderer.Dimensions dimensions = BackupPopupRenderer.measure(font, working.popup, previewPayload());
        working.popup.x = Math.clamp(working.popup.x, 4, Math.max(4, width - dimensions.width()));
        working.popup.y = Math.clamp(working.popup.y, 4, Math.max(4, height - dimensions.height()));
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

    private int contentX() {
        return OUTER_MARGIN + TAB_WIDTH + 16;
    }

    private int contentTop() {
        return OUTER_MARGIN + 24;
    }

    private int contentWidth() {
        return Math.max(180, width - contentX() - OUTER_MARGIN);
    }

    private int currentScroll() {
        return scrollByTab.getOrDefault(selectedTab, 0);
    }

    private int maxScroll() {
        int contentHeight = rows.isEmpty() ? 0 : rows.getLast().y + ROW_HEIGHT + currentScroll() - (contentTop() + HEADER_HEIGHT);
        int viewport = Math.max(1, height - FOOTER_HEIGHT - contentTop() - HEADER_HEIGHT - 10);
        return Math.max(0, contentHeight - viewport);
    }

    private boolean isRowInteractive(int y) {
        return y + ROW_HEIGHT >= contentTop() + HEADER_HEIGHT && y <= height - FOOTER_HEIGHT - 8;
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

    private static Component toggleMessage(boolean enabled) {
        return Component.literal(enabled ? "ON" : "OFF");
    }

    private static String value(String value) {
        return value == null ? "" : value;
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

    private static String tooltipFor(String label) {
        return switch (label) {
            case "Backup mode" -> """
                    FULL: copies the whole world every time. Safest and easiest to restore.
                    PARTIAL: copies only files changed since the latest backup. Smaller, but depends on the backup chain.
                    DIFFERENTIAL: copies files changed since the latest full backup. Larger than partial, simpler to restore.""";
            case "Automatic backups" -> "When enabled, the server creates backups automatically at the configured interval.";
            case "Pause when no players joined" -> "Skips scheduled automatic backups if no player has been online since the last backup.";
            case "Backup when world starts" -> "Creates one backup when the server finishes starting the world. Disabled by default.";
            case "Backup when world closes" -> "Creates one backup while the server is shutting down. Shutdown waits for it to finish. Disabled by default.";
            case "Automatic interval minutes" -> "Minutes between automatic backups. The timer resets when this value is saved.";
            case "Backup directory" -> "Directory where backups are stored. Relative paths are resolved from the game directory.";
            case "Command permission level" -> """
                    Minimum Minecraft permission level required to use /advancedbackups.
                    0: anyone can use it.
                    1: low-level permissions.
                    2: operators/gamemasters. Default.
                    3: admins.
                    4: server owners/highest permission level.""";
            case "Integrity mode" -> """
                    STRICT: failed backups are discarded and damaged restores are blocked.
                    PERMISSIVE: partial backups may remain, but damaged restores are blocked.
                    VERY_PERMISSIVE: damaged restores can continue with a warning. Use only for manual recovery.""";
            case "Keep full backups" -> "Maximum number of full backups to keep. At least one full backup is always retained.";
            case "Keep partial backups" -> "Maximum number of partial backups to keep. Required base backups are protected.";
            case "Keep differential backups" -> "Maximum number of differential backups to keep. Required full backups are protected.";
            case "Show backup popup" -> "Shows the backup progress HUD on clients with the mod installed.";
            case "Show title" -> "Shows or hides the title line at the top of the backup popup.";
            case "Center text" -> "Centers the popup title, status text, and progress line inside the popup.";
            case "Show border" -> "Draws a thin popup border using the text color.";
            case "Preview state" -> "Selects the sample state used by the popup preview editor.";
            case "Preview Layout" -> "Opens a clean positioning screen with center guide lines and snap.";
            case "Title" -> "Title shown at the top of the backup popup.";
            case "Running text", "Completed text", "Failed text" -> "Supports {reason}, {type}, {percent}, {bytesWritten}, and {totalBytes}.";
            case "Background color" -> "Popup background color. Accepts 0xAARRGGBB, #AARRGGBB, or #RRGGBB.";
            case "Running color" -> "Status text color used while a backup is running.";
            case "Completed color" -> "Status text color used when a backup completes.";
            case "Failed color" -> "Status text color used when a backup fails.";
            case "Text color" -> "Text color used for the title and progress line.";
            case "A channel" -> "Alpha channel for the selected color.";
            case "R channel" -> "Red channel for the selected color.";
            case "G channel" -> "Green channel for the selected color.";
            case "B channel" -> "Blue channel for the selected color.";
            default -> "";
        };
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
        BACKUPS("Backups"),
        RETENTION("Retention"),
        PERMISSIONS("Permissions"),
        INTEGRITY("Integrity"),
        HUD("HUD");

        private final String label;

        ConfigTab(String label) {
            this.label = label;
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
            int clamped = Math.clamp(value, 0, 255);
            int mask = 0xFF << shift;
            return (color & ~mask) | (clamped << shift);
        }
    }

    private static final class ColorSlider extends AbstractSliderButton {
        private final ColorChannel channel;
        private final ColorConsumer consumer;

        private ColorSlider(int x, int y, int width, int height, ColorChannel channel, ColorConsumer consumer) {
            super(x, y, width, height, Component.empty(), 1.0D);
            this.channel = channel;
            this.consumer = consumer;
            updateMessage();
        }

        private void setChannelValue(int value) {
            this.value = Math.clamp(value, 0, 255) / 255.0D;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(channel.label + ": " + channelValue()));
        }

        @Override
        protected void applyValue() {
            consumer.accept(channel, channelValue());
        }

        private int channelValue() {
            return (int) Math.round(value * 255.0D);
        }
    }

    @FunctionalInterface
    private interface ColorConsumer {
        void accept(ColorChannel channel, int value);
    }

    private record RowInfo(int y, int height, String label, String tooltip) {
    }

    private record ValidationError(String label, String message) {
    }
}
