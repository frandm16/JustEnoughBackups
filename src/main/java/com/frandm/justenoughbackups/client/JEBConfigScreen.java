package com.frandm.justenoughbackups.client;

import com.frandm.justenoughbackups.backup.model.BackupIntegrityMode;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.progress.BackupProgressPayload;
import com.frandm.justenoughbackups.backup.progress.BackupProgressState;
import com.frandm.justenoughbackups.config.BackupConfig;
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

public final class JEBConfigScreen extends Screen {
    private static final int OUTER_MARGIN = 12;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_GAP = 6;
    private static final int HEADER_HEIGHT = 34;
    private static final int CONTROL_HEIGHT = 20;
    private static final int TAB_GAP = 4;
    private static final int ROW_INSET = 8;
    private static final int COLUMN_GAP = 8;
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

    public JEBConfigScreen(Screen parent) {
        super(Component.translatable("screen.justenoughbackups.config.title"));
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
        PopupPositioning.applyRatios(font, working.popup, previewPayload(), width, height);

        int tabY = OUTER_MARGIN + 22;
        int tabWidth = Math.max(48, (width - OUTER_MARGIN * 2 - TAB_GAP * (ConfigTab.values().length - 1)) / ConfigTab.values().length);
        int tabX = OUTER_MARGIN;
        for (ConfigTab tab : ConfigTab.values()) {
            Button button = Button.builder(Component.translatable(tab.key), ignored -> {
                        if (tab == ConfigTab.PREVIEW) {
                            selectedTab = ConfigTab.HUD;
                            minecraft.setScreen(new PopupPreviewScreen(this, working.popup, previewPayload()));
                            return;
                        }
                        selectedTab = tab;
                        rebuildWidgets();
                    })
                    .bounds(tabX, tabY, tabWidth, CONTROL_HEIGHT)
                    .build();
            button.active = selectedTab != tab;
            addRenderableWidget(button);
            tabX += tabWidth + TAB_GAP;
        }

        int contentX = contentX();
        int contentY = contentTop();
        int contentW = contentWidth();
        int controlsW = Math.min(contentW, Math.clamp(contentW / 2, 80, 240));
        int y = contentY + HEADER_HEIGHT - currentScroll();

        switch (selectedTab) {
            case BACKUPS -> buildBackups(contentX, y, contentW, controlsW);
            case RETENTION -> buildRetention(contentX, y, contentW, controlsW);
            case PERMISSIONS -> buildPermissions(contentX, y, contentW, controlsW);
            case INTEGRITY -> buildIntegrity(contentX, y, contentW, controlsW);
            case HUD -> buildHud(contentX, y, contentW, controlsW);
            case PREVIEW -> {
            }
        }

        int footerY = height - OUTER_MARGIN - CONTROL_HEIGHT;
        int footerX = Math.max(OUTER_MARGIN, width - OUTER_MARGIN - 282);
        saveButton = addRenderableWidget(Button.builder(Component.translatable("screen.justenoughbackups.common.save"), ignored -> saveAndClose())
                .bounds(footerX, footerY, 58, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughbackups.config.reset_tab"), ignored -> resetCurrentTab())
                .bounds(footerX + 128, footerY, 72, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughbackups.config.reset_all"), ignored -> resetAll())
                .bounds(footerX + 206, footerY, 76, CONTROL_HEIGHT)
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
        return addTextRow(x, y, width, controlsW, "Backup directory", () -> working.backupDirectory, value -> working.backupDirectory = value);
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
        if (width < 430) {
            return buildHudColumn(x, y, width, controlsW);
        }

        int leftW = (width - COLUMN_GAP) / 2;
        int rightW = width - leftW - COLUMN_GAP;
        int leftX = x;
        int rightX = x + leftW + COLUMN_GAP;
        int leftControlsW = Math.clamp(leftW / 2, 80, controlsW);
        int rightControlsW = Math.clamp(rightW / 2, 80, controlsW);

        int leftY = y;
        leftY = addBooleanRow(leftX, leftY, leftW, leftControlsW, "Show backup popup", () -> working.popup.enabled, value -> working.popup.enabled = value);
        leftY = addBooleanRow(leftX, leftY, leftW, leftControlsW, "Show title", () -> working.popup.showTitle, value -> working.popup.showTitle = value);
        leftY = addBooleanRow(leftX, leftY, leftW, leftControlsW, "Center text", () -> working.popup.centerText, value -> working.popup.centerText = value);
        leftY = addBooleanRow(leftX, leftY, leftW, leftControlsW, "Show border", () -> working.popup.showBorder, value -> working.popup.showBorder = value);
        leftY = addPreviewStateRow(leftX, leftY, leftW, leftControlsW);
        leftY += 6;
        leftY = addColorChannelRow(leftX, leftY, leftW, leftControlsW, ColorChannel.ALPHA);
        leftY = addColorChannelRow(leftX, leftY, leftW, leftControlsW, ColorChannel.RED);

        int rightY = y;
        for (ColorTarget target : ColorTarget.values()) {
            rightY = addColorRow(rightX, rightY, rightW, rightControlsW, target);
        }
        rightY += 6;
        rightY = addColorChannelRow(rightX, rightY, rightW, rightControlsW, ColorChannel.GREEN);
        rightY = addColorChannelRow(rightX, rightY, rightW, rightControlsW, ColorChannel.BLUE);
        syncSlidersFromSelectedColor();
        return Math.max(leftY, rightY);
    }

    private int buildHudColumn(int x, int y, int width, int controlsW) {
        y = addBooleanRow(x, y, width, controlsW, "Show backup popup", () -> working.popup.enabled, value -> working.popup.enabled = value);
        y = addBooleanRow(x, y, width, controlsW, "Show title", () -> working.popup.showTitle, value -> working.popup.showTitle = value);
        y = addBooleanRow(x, y, width, controlsW, "Center text", () -> working.popup.centerText, value -> working.popup.centerText = value);
        y = addBooleanRow(x, y, width, controlsW, "Show border", () -> working.popup.showBorder, value -> working.popup.showBorder = value);
        y = addPreviewStateRow(x, y, width, controlsW);
        y += 6;

        
        for (ColorTarget target : ColorTarget.values()) {
            y = addColorRow(x, y, width, controlsW, target);
        }
        y += 6;
        for (ColorChannel channel : ColorChannel.values()) {
            y = addColorChannelRow(x, y, width, controlsW, channel);
        }
        syncSlidersFromSelectedColor();
        return y;
    }

    private int addBooleanRow(int x, int y, int width, int controlsW, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        addRow(x, y, width, label);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlW = rowControlWidth(width, controlsW);
        int controlX = rowControlX(x, width, controlW);
        Button button = Button.builder(toggleMessage(getter.get()), ignored -> {
                    setter.accept(!getter.get());
                    rebuildWidgets();
                })
                .bounds(controlX, y + 6, controlW, CONTROL_HEIGHT)
                .build();
        addRenderableWidget(button);
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private <T extends Enum<T>> int addEnumRow(int x, int y, int width, int controlsW, String label, T selected, T[] values, Consumer<T> setter) {
        addRow(x, y, width, label);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlW = rowControlWidth(width, controlsW);
        int controlX = rowControlX(x, width, controlW);
        addRenderableWidget(CycleButton.builder((T value) -> Component.literal(value.toString()), selected)
                .withValues(values)
                .create(controlX, y + 6, controlW, CONTROL_HEIGHT, labelComponent(label), (_, value) -> setter.accept(value)));
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private int addIntRow(int x, int y, int width, int controlsW, String label, IntSupplier getter, IntConsumer setter, int min, int max) {
        addRow(x, y, width, label);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlW = rowControlWidth(width, controlsW);
        int controlX = rowControlX(x, width, controlW);
        int stepW = 22;
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughbackups.config.decrement"), ignored -> {
                    int next = Math.clamp(getter.getAsInt() - 1, min, max);
                    setter.accept(next);
                    rawInputs.put(label, String.valueOf(next));
                    rebuildWidgets();
                })
                .bounds(controlX, y + 6, stepW, CONTROL_HEIGHT)
                .build());
        EditBox field = new EditBox(font, controlX + stepW + 4, y + 6, Math.max(20, controlW - (stepW * 2) - 8), CONTROL_HEIGHT, labelComponent(label));
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
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughbackups.config.increment"), ignored -> {
                    int next = Math.clamp(getter.getAsInt() + 1, min, max);
                    setter.accept(next);
                    rawInputs.put(label, String.valueOf(next));
                    rebuildWidgets();
                })
                .bounds(controlX + controlW - stepW, y + 6, stepW, CONTROL_HEIGHT)
                .build());
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private int addTextRow(int x, int y, int width, int controlsW, String label, Supplier<String> getter, Consumer<String> setter) {
        addRow(x, y, width, label);
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlW = rowControlWidth(width, controlsW);
        int controlX = rowControlX(x, width, controlW);
        EditBox field = new EditBox(font, controlX, y + 6, controlW, CONTROL_HEIGHT, labelComponent(label));
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
        addRow(x, y, width, "Preview state");
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlW = rowControlWidth(width, controlsW);
        int controlX = rowControlX(x, width, controlW);
        int buttonW = Math.max(24, (controlW - 8) / 3);
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughbackups.config.preview_state.running"), ignored -> previewState = PreviewState.RUNNING)
                .bounds(controlX, y + 6, buttonW, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughbackups.config.preview_state.done"), ignored -> previewState = PreviewState.COMPLETED)
                .bounds(controlX + buttonW + 4, y + 6, buttonW, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughbackups.config.preview_state.failed"), ignored -> previewState = PreviewState.FAILED)
                .bounds(controlX + controlW - buttonW, y + 6, buttonW, CONTROL_HEIGHT)
                .build());
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private int addColorRow(int x, int y, int width, int controlsW, ColorTarget target) {
        addRow(x, y, width, target.label + " color");
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int controlW = rowControlWidth(width, controlsW);
        int controlX = rowControlX(x, width, controlW);
        int editW = Math.clamp(controlW / 3, 34, 52);
        addRenderableWidget(Button.builder(Component.translatable(target == selectedColor ? "screen.justenoughbackups.config.editing" : "screen.justenoughbackups.config.edit"), ignored -> {
                    selectedColor = target;
                    syncSlidersFromSelectedColor();
                    rebuildWidgets();
                })
                .bounds(controlX, y + 6, editW, CONTROL_HEIGHT)
                .build());
        EditBox field = new EditBox(font, controlX + editW + 6, y + 6, Math.max(20, controlW - editW - 6), CONTROL_HEIGHT, labelComponent(target.label + " color"));
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

    private int addColorChannelRow(int x, int y, int width, int controlsW, ColorChannel channel) {
        rows.add(new RowInfo(x, y, width, ROW_HEIGHT, channel.label + " channel", tooltipFor(channel.label + " channel")));
        if (!isRowInteractive(y)) {
            return y + ROW_HEIGHT + ROW_GAP;
        }
        int sliderW = rowControlWidth(width, controlsW);
        int controlX = rowControlX(x, width, sliderW);
        ColorSlider slider = new ColorSlider(controlX, y + 6, sliderW, CONTROL_HEIGHT, channel, this::setSelectedChannel);
        sliders.put(channel, addRenderableWidget(slider));
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private void addRow(int x, int y, int width, String label) {
        rows.add(new RowInfo(x, y, width, ROW_HEIGHT, label, tooltipFor(label)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC050505);
        graphics.text(font, title, OUTER_MARGIN, 8, 0xFFFFFFFF, true);

        int panelX = contentX() - 4;
        int panelY = contentTop();
        int panelW = contentWidth() + 8;
        int panelH = height - FOOTER_HEIGHT - panelY - 4;
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xBB101010);
        graphics.outline(panelX, panelY, panelW, panelH, 0xFF4A4A4A);
        graphics.text(font, Component.translatable(selectedTab.key), contentX(), panelY + 8, 0xFFFFFFFF, true);

        RowInfo hoveredRow = null;
        for (RowInfo row : rows) {
            if (row.y + row.height < panelY + HEADER_HEIGHT || row.y > height - FOOTER_HEIGHT - 8) {
                continue;
            }
            int color = isInvalid(row.label) ? 0x66AA2222 : 0x66181818;
            graphics.fill(row.x, row.y, row.x + row.width, row.y + row.height, color);
            graphics.outline(row.x, row.y, row.width, row.height, isInvalid(row.label) ? 0xFFFF5555 : 0xFF333333);
            graphics.text(font, Component.literal(trimToWidth(labelText(row.label), Math.max(50, row.width / 2 - 16))), row.x + ROW_INSET, row.y + 11, 0xFFE0E0E0, true);
            drawColorSwatch(graphics, row);
            if (mouseX >= row.x && mouseX <= row.x + row.width && mouseY >= row.y && mouseY <= row.y + row.height) {
                hoveredRow = row;
            }
        }

        if (!validationErrors.isEmpty()) {
            String message = validationErrors.getFirst().message().getString();
            graphics.text(font, Component.literal(trimToWidth(message, Math.max(100, width - 230))), contentX(), height - 22, 0xFFFF7777, true);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (hoveredRow != null && !hoveredRow.tooltipKey.isBlank()) {
            drawTooltipBox(graphics, Component.translatable(hoveredRow.tooltipKey).getString(), mouseX, mouseY);
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
            case PREVIEW -> {
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
            validationErrors.add(new ValidationError("Automatic interval minutes", Component.translatable("screen.justenoughbackups.config.error.interval_min")));
        }
        if (working.commandPermissionLevel < 0 || working.commandPermissionLevel > 4) {
            validationErrors.add(new ValidationError("Command permission level", Component.translatable("screen.justenoughbackups.config.error.permission_range")));
        }
        if (working.retention.full < 1) {
            validationErrors.add(new ValidationError("Keep full backups", Component.translatable("screen.justenoughbackups.config.error.full_min")));
        }
        if (working.retention.incremental < 0) {
            validationErrors.add(new ValidationError("Keep partial backups", Component.translatable("screen.justenoughbackups.config.error.partial_min")));
        }
        if (working.retention.differential < 0) {
            validationErrors.add(new ValidationError("Keep differential backups", Component.translatable("screen.justenoughbackups.config.error.differential_min")));
        }
        String backupDirectory = rawInputs.getOrDefault("Backup directory", value(working.backupDirectory));
        if (backupDirectory.isBlank()) {
            validationErrors.add(new ValidationError("Backup directory", Component.translatable("screen.justenoughbackups.config.error.backup_directory_empty")));
        }
        for (ColorTarget target : ColorTarget.values()) {
            String label = target.label + " color";
            if (parseColor(rawInputs.getOrDefault(label, target.get(working.popup))).isEmpty()) {
                validationErrors.add(new ValidationError(target.label + " color", Component.translatable("screen.justenoughbackups.config.error.color", labelText(target.label + " color"))));
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
                validationErrors.add(new ValidationError(label, Component.translatable("screen.justenoughbackups.config.error.number_range", labelText(label), min, max)));
            }
        } catch (NumberFormatException exception) {
            validationErrors.add(new ValidationError(label, Component.translatable("screen.justenoughbackups.config.error.whole_number", labelText(label))));
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
        int x = row.x + Math.max(54, row.width / 2 - 42);
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

    private void clampPreviewPosition() {
        PopupPositioning.clampAndRemember(font, working.popup, previewPayload(), width, height);
    }

    private void rememberPreviewPosition() {
        PopupPositioning.rememberRatios(font, working.popup, previewPayload(), width, height);
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
        return OUTER_MARGIN;
    }

    private int contentTop() {
        return OUTER_MARGIN + 56;
    }

    private int contentWidth() {
        return Math.max(1, width - OUTER_MARGIN * 2);
    }

    private int currentScroll() {
        return scrollByTab.getOrDefault(selectedTab, 0);
    }

    private int maxScroll() {
        int maxRowBottom = 0;
        for (RowInfo row : rows) {
            maxRowBottom = Math.max(maxRowBottom, row.y + row.height + currentScroll());
        }
        int contentHeight = rows.isEmpty() ? 0 : maxRowBottom - (contentTop() + HEADER_HEIGHT);
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

    private int rowControlWidth(int rowWidth, int requestedWidth) {
        int available = Math.max(1, rowWidth - ROW_INSET * 2);
        return Math.clamp(requestedWidth, Math.min(available, 56), available);
    }

    private int rowControlX(int rowX, int rowWidth, int controlWidth) {
        return rowX + rowWidth - ROW_INSET - controlWidth;
    }

    private static Component toggleMessage(boolean enabled) {
        return Component.translatable(enabled ? "screen.justenoughbackups.config.toggle.on" : "screen.justenoughbackups.config.toggle.off");
    }

    private Component labelComponent(String label) {
        return Component.translatable(labelKey(label));
    }

    private String labelText(String label) {
        return Component.translatable(labelKey(label)).getString();
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
        RETENTION("screen.justenoughbackups.config.tab.retention"),
        PERMISSIONS("screen.justenoughbackups.config.tab.permissions"),
        INTEGRITY("screen.justenoughbackups.config.tab.integrity"),
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
            setMessage(Component.translatable("screen.justenoughbackups.config.channel_value", channel.label, channelValue()));
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

    private record RowInfo(int x, int y, int width, int height, String label, String tooltipKey) {
    }

    private record ValidationError(String label, Component message) {
    }
}
