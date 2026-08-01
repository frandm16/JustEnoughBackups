package com.frandm.justenoughbackups.client.screen.config;

import com.frandm.justenoughbackups.backup.model.BackupIntegrityMode;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.BackupMessageChannel;
import com.frandm.justenoughbackups.client.screen.preview.PopupPreviewScreen;
import com.frandm.justenoughbackups.client.ui.ScreenChrome;
import com.frandm.justenoughbackups.client.ui.popup.PopupPositioning;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.frandm.justenoughbackups.config.ConfigColor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class JEBConfigScreen extends Screen {
    private static final int TAB_Y = 32;
    private static final int TAB_H = 20;
    private static final int ROW_H = 32;
    private static final int ROW_GAP = 6;
    private static final int ROW_INSET = 8;
    private static final int CONTROL_H = 20;
    private static final int SCROLL_GUTTER = 12;
    private static final int SCROLL_W = 4;
    private static final int MIN_THUMB_H = 18;
    private static final int LIST_BUTTON_W = 22;

    private static final int ROW_COLOR = 0x66181818;
    private static final int ROW_BAD_COLOR = 0x66AA2222;
    private static final int OUTLINE_BAD_COLOR = 0xFFFF5555;

    private final Screen parent;
    private final ConfigScreenState state;
    private final ConfigValidator validator = new ConfigValidator();
    private final List<ConfigRow> rows = new ArrayList<>();
    private List<ValidationError> validationErrors = List.of();
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private ConfigColorChannel draggingChannel;

    public JEBConfigScreen(Screen parent) {
        super(Component.translatable("screen.justenoughbackups.config.title"));
        this.parent = parent;
        this.state = new ConfigScreenState(BackupConfig.get().copy());
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        PopupPositioning.applyRatios(font, state.working.popup, state.previewPayload(), width, height);
        buildRowsForTab(state.selectedTab);
        clampScroll();
        validationErrors = validator.validate(state);
        clampPreviewPosition();
    }

    private void buildRowsForTab(ConfigTab tab) {
        int y = 0;
        int baseContainerX = contentWidth() / 6;
        int indentX = 16;

        for (ConfigFieldSpec spec : ConfigFieldSpecs.forTab(tab)) {
            ConfigFieldId id = spec.id();

            boolean isSubOption = isAutomaticBackupSubOption(id);
            int x = isSubOption ? baseContainerX + indentX : baseContainerX;
            int w = isSubOption ? (4 * contentWidth() / 6) - indentX : (4 * contentWidth() / 6);
            int controlsW = Math.min(w, Math.clamp(w / 2, 80, 240));

            y = switch (id.controlType()) {
                case BOOLEAN -> booleanRow(x, y, w, controlsW, id, booleanGetter(id), booleanSetter(id));
                case ENUM -> enumRow(x, y, w, controlsW, id);
                case INT -> intRow(x, y, w, controlsW, spec, intGetter(id), intSetter(id));
                case TEXT -> textRow(x, y, w, controlsW, spec, textGetter(id), textSetter(id));
                case COLOR -> colorRow(x, y, w, controlsW, id, colorTarget(id));
                case CHANNEL -> channelRow(x, y, w, controlsW, channelFor(id));
                case ACTION -> actionRow(x, y, w, controlsW, id);
                case SECTION_HEADER -> sectionHeaderRow(x, y, w, id);
            };
        }
    }

    private boolean isAutomaticBackupSubOption(ConfigFieldId id) {
        return id == ConfigFieldId.AUTOMATIC_FULL_ENABLED
                || id == ConfigFieldId.AUTOMATIC_FULL_INTERVAL_MINUTES
                || id == ConfigFieldId.AUTOMATIC_PARTIAL_ENABLED
                || id == ConfigFieldId.AUTOMATIC_PARTIAL_INTERVAL_MINUTES
                || id == ConfigFieldId.AUTOMATIC_DIFFERENTIAL_ENABLED
                || id == ConfigFieldId.AUTOMATIC_DIFFERENTIAL_INTERVAL_MINUTES;
    }

    private Supplier<Boolean> booleanGetter(ConfigFieldId fieldId) {
        return switch (fieldId) {
            case PAUSE_WITHOUT_PLAYERS -> () -> state.working.pauseAutomaticBackupsWithoutPlayers;
            case BACKUP_ON_START -> () -> state.working.backupOnServerStart;
            case BACKUP_ON_STOP -> () -> state.working.backupOnServerStop;
            case AUTOMATIC_BACKUP_WARNING -> () -> state.working.automaticBackupWarningEnabled;
            case AUTOMATIC_FULL_ENABLED -> () -> state.working.automaticSchedule.full.enabled;
            case AUTOMATIC_PARTIAL_ENABLED -> () -> state.working.automaticSchedule.partial.enabled;
            case AUTOMATIC_DIFFERENTIAL_ENABLED -> () -> state.working.automaticSchedule.differential.enabled;
            case INCLUDE_SUMMARY_FILE -> () -> state.working.includeSummaryFile;
            case POPUP_ENABLED -> () -> state.working.popup.enabled;
            case POPUP_SHOW_TITLE -> () -> state.working.popup.showTitle;
            case POPUP_CENTER_TEXT -> () -> state.working.popup.centerText;
            case POPUP_SHOW_BORDER -> () -> state.working.popup.showBorder;
            default -> throw new IllegalArgumentException("Not a boolean field: " + fieldId);
        };
    }

    private Consumer<Boolean> booleanSetter(ConfigFieldId fieldId) {
        return switch (fieldId) {
            case PAUSE_WITHOUT_PLAYERS -> value -> state.working.pauseAutomaticBackupsWithoutPlayers = value;
            case BACKUP_ON_START -> value -> state.working.backupOnServerStart = value;
            case BACKUP_ON_STOP -> value -> state.working.backupOnServerStop = value;
            case AUTOMATIC_BACKUP_WARNING -> value -> state.working.automaticBackupWarningEnabled = value;
            case AUTOMATIC_FULL_ENABLED -> value -> state.working.automaticSchedule.full.enabled = value;
            case AUTOMATIC_PARTIAL_ENABLED -> value -> state.working.automaticSchedule.partial.enabled = value;
            case AUTOMATIC_DIFFERENTIAL_ENABLED -> value -> state.working.automaticSchedule.differential.enabled = value;
            case INCLUDE_SUMMARY_FILE -> value -> state.working.includeSummaryFile = value;
            case POPUP_ENABLED -> value -> state.working.popup.enabled = value;
            case POPUP_SHOW_TITLE -> value -> state.working.popup.showTitle = value;
            case POPUP_CENTER_TEXT -> value -> state.working.popup.centerText = value;
            case POPUP_SHOW_BORDER -> value -> state.working.popup.showBorder = value;
            default -> throw new IllegalArgumentException("Not a boolean field: " + fieldId);
        };
    }

    private IntSupplier intGetter(ConfigFieldId fieldId) {
        return switch (fieldId) {
            case INTERVAL_MINUTES -> () -> state.working.automaticIntervalMinutes;
            case AUTOMATIC_BACKUP_WARNING_MINUTES -> () -> state.working.automaticBackupWarningMinutes;
            case AUTOMATIC_FULL_INTERVAL_MINUTES -> () -> state.working.automaticSchedule.full.intervalMinutes;
            case AUTOMATIC_PARTIAL_INTERVAL_MINUTES -> () -> state.working.automaticSchedule.partial.intervalMinutes;
            case AUTOMATIC_DIFFERENTIAL_INTERVAL_MINUTES -> () -> state.working.automaticSchedule.differential.intervalMinutes;
            case KEEP_FULL -> () -> state.working.retention.full;
            case KEEP_PARTIAL -> () -> state.working.retention.incremental;
            case KEEP_DIFFERENTIAL -> () -> state.working.retention.differential;
            case MAX_TOTAL_SIZE_MB -> () -> state.working.retention.maxTotalSizeMb;
            case MINIMUM_FREE_SPACE_RESERVE_MB -> () -> state.working.minimumFreeSpaceReserveMb;
            case PERMISSION_LEVEL -> () -> state.working.commandPermissionLevel;
            default -> throw new IllegalArgumentException("Not an int field: " + fieldId);
        };
    }

    private IntConsumer intSetter(ConfigFieldId fieldId) {
        return switch (fieldId) {
            case INTERVAL_MINUTES -> value -> state.working.automaticIntervalMinutes = value;
            case AUTOMATIC_BACKUP_WARNING_MINUTES -> value -> state.working.automaticBackupWarningMinutes = value;
            case AUTOMATIC_FULL_INTERVAL_MINUTES -> value -> state.working.automaticSchedule.full.intervalMinutes = value;
            case AUTOMATIC_PARTIAL_INTERVAL_MINUTES -> value -> state.working.automaticSchedule.partial.intervalMinutes = value;
            case AUTOMATIC_DIFFERENTIAL_INTERVAL_MINUTES -> value -> state.working.automaticSchedule.differential.intervalMinutes = value;
            case KEEP_FULL -> value -> state.working.retention.full = value;
            case KEEP_PARTIAL -> value -> state.working.retention.incremental = value;
            case KEEP_DIFFERENTIAL -> value -> state.working.retention.differential = value;
            case MAX_TOTAL_SIZE_MB -> value -> state.working.retention.maxTotalSizeMb = value;
            case MINIMUM_FREE_SPACE_RESERVE_MB -> value -> state.working.minimumFreeSpaceReserveMb = value;
            case PERMISSION_LEVEL -> value -> state.working.commandPermissionLevel = value;
            default -> throw new IllegalArgumentException("Not an int field: " + fieldId);
        };
    }

    private Supplier<String> textGetter(ConfigFieldId fieldId) {
        return switch (fieldId) {
            case BACKUP_DIRECTORY -> () -> state.working.backupDirectory;
            case POPUP_TITLE -> () -> state.working.popup.title;
            case POPUP_RUNNING_TEXT -> () -> state.working.popup.runningText;
            case POPUP_COMPLETED_TEXT -> () -> state.working.popup.completedText;
            case POPUP_FAILED_TEXT -> () -> state.working.popup.failedText;
            default -> throw new IllegalArgumentException("Not a text field: " + fieldId);
        };
    }

    private Consumer<String> textSetter(ConfigFieldId fieldId) {
        return switch (fieldId) {
            case BACKUP_DIRECTORY -> value -> state.working.backupDirectory = value;
            case POPUP_TITLE -> value -> state.working.popup.title = value;
            case POPUP_RUNNING_TEXT -> value -> state.working.popup.runningText = value;
            case POPUP_COMPLETED_TEXT -> value -> state.working.popup.completedText = value;
            case POPUP_FAILED_TEXT -> value -> state.working.popup.failedText = value;
            default -> throw new IllegalArgumentException("Not a text field: " + fieldId);
        };
    }

    private ConfigColorTarget colorTarget(ConfigFieldId fieldId) {
        return switch (fieldId) {
            case POPUP_BACKGROUND_COLOR -> ConfigColorTarget.BACKGROUND;
            case POPUP_RUNNING_COLOR -> ConfigColorTarget.RUNNING;
            case POPUP_COMPLETED_COLOR -> ConfigColorTarget.COMPLETED;
            case POPUP_FAILED_COLOR -> ConfigColorTarget.FAILED;
            case POPUP_TEXT_COLOR -> ConfigColorTarget.TEXT;
            default -> throw new IllegalArgumentException("Not a color field: " + fieldId);
        };
    }

    private ConfigColorChannel channelFor(ConfigFieldId fieldId) {
        return switch (fieldId) {
            case POPUP_CHANNEL_A -> ConfigColorChannel.ALPHA;
            case POPUP_CHANNEL_R -> ConfigColorChannel.RED;
            case POPUP_CHANNEL_G -> ConfigColorChannel.GREEN;
            case POPUP_CHANNEL_B -> ConfigColorChannel.BLUE;
            default -> throw new IllegalArgumentException("Not a channel field: " + fieldId);
        };
    }

    private int booleanRow(int x, int y, int w, int controlsW, ConfigFieldId fieldId, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        addRow(x, y, w, fieldId, (graphics, row, screenY, mouseX, mouseY) -> {
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

    private int enumRow(int x, int y, int w, int controlsW, ConfigFieldId fieldId) {
        return switch (fieldId) {
            case BACKUP_MODE -> enumRow(x, y, w, controlsW, fieldId, state.working.backupMode, BackupType.values(), value -> state.working.backupMode = value);
            case MESSAGE_CHANNEL -> enumRow(x, y, w, controlsW, fieldId, state.working.messageChannel, BackupMessageChannel.values(), value -> state.working.messageChannel = value);
            case INTEGRITY_MODE -> enumRow(x, y, w, controlsW, fieldId, state.working.integrityMode, BackupIntegrityMode.values(), value -> state.working.integrityMode = value);
            default -> throw new IllegalArgumentException("Unsupported enum field: " + fieldId);
        };
    }

    private <T extends Enum<T>> int enumRow(int x, int y, int w, int controlsW, ConfigFieldId fieldId, T selected, T[] values, Consumer<T> setter) {
        addRow(x, y, w, fieldId, (graphics, row, screenY, mouseX, mouseY) -> {
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

    private int intRow(int x, int y, int w, int controlsW, ConfigFieldSpec spec, IntSupplier getter, IntConsumer setter) {
        addRow(x, y, w, spec.id(), (graphics, row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            Rect minus = new Rect(control.x, control.y, 22, CONTROL_H);
            Rect plus = new Rect(control.x + control.w - 22, control.y, 22, CONTROL_H);
            Rect field = new Rect(control.x + 26, control.y, control.w - 52, CONTROL_H);
            drawButton(graphics, minus, Component.translatable("screen.justenoughbackups.config.decrement"), true, minus.contains(mouseX, mouseY));
            drawField(graphics, field, spec.id(), state.rawInputs.getOrDefault(spec.id(), String.valueOf(getter.getAsInt())), mouseX, mouseY);
            drawButton(graphics, plus, Component.translatable("screen.justenoughbackups.config.increment"), true, plus.contains(mouseX, mouseY));
        }, (row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            Rect minus = new Rect(control.x, control.y, 22, CONTROL_H);
            Rect plus = new Rect(control.x + control.w - 22, control.y, 22, CONTROL_H);
            Rect field = new Rect(control.x + 26, control.y, control.w - 52, CONTROL_H);
            if (minus.contains(mouseX, mouseY)) {
                int next = Math.clamp(getter.getAsInt() - 1, spec.min(), spec.max());
                setter.accept(next);
                state.rawInputs.put(spec.id(), String.valueOf(next));
                rebuildRows();
                return true;
            }
            if (plus.contains(mouseX, mouseY)) {
                int next = Math.clamp(getter.getAsInt() + 1, spec.min(), spec.max());
                setter.accept(next);
                state.rawInputs.put(spec.id(), String.valueOf(next));
                rebuildRows();
                return true;
            }
            if (field.contains(mouseX, mouseY)) {
                focusField(spec.id(), state.rawInputs.getOrDefault(spec.id(), String.valueOf(getter.getAsInt())));
                return true;
            }
            return false;
        });
        return y + ROW_H + ROW_GAP;
    }

    private int textRow(int x, int y, int w, int controlsW, ConfigFieldSpec spec, Supplier<String> getter, Consumer<String> setter) {
        addRow(x, y, w, spec.id(), (graphics, row, screenY, mouseX, mouseY) -> {
            Rect field = controlRect(row, screenY, controlsW);
            drawField(graphics, field, spec.id(), state.rawInputs.getOrDefault(spec.id(), value(getter.get())), mouseX, mouseY);
        }, (row, screenY, mouseX, mouseY) -> {
            Rect field = controlRect(row, screenY, controlsW);
            if (!field.contains(mouseX, mouseY)) {
                return false;
            }
            focusField(spec.id(), state.rawInputs.getOrDefault(spec.id(), value(getter.get())));
            setter.accept(state.rawInputs.getOrDefault(spec.id(), value(getter.get())));
            return true;
        });
        return y + ROW_H + ROW_GAP;
    }

    private int colorRow(int x, int y, int w, int controlsW, ConfigFieldId fieldId, ConfigColorTarget target) {
        addRow(x, y, w, fieldId, (graphics, row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            Rect edit = new Rect(control.x, control.y, Math.clamp(control.w / 3, 34, 52), CONTROL_H);
            Rect field = new Rect(edit.x + edit.w + 6, control.y, control.w - edit.w - 6, CONTROL_H);
            drawButton(graphics, edit, Component.translatable(target == state.selectedColor ? "screen.justenoughbackups.config.editing" : "screen.justenoughbackups.config.edit"), true, edit.contains(mouseX, mouseY));
            drawField(graphics, field, fieldId, state.rawInputs.getOrDefault(fieldId, target.get(state.working.popup)), mouseX, mouseY);
            int swatchX = row.x + Math.max(54, row.w / 2 - 42);
            graphics.fill(swatchX, screenY + 8, swatchX + 18, screenY + 26, 0xFF000000);
            graphics.fill(swatchX + 1, screenY + 9, swatchX + 17, screenY + 25, target.argb(state.working.popup));
            graphics.outline(swatchX, screenY + 8, 18, 18, target == state.selectedColor ? 0xFFFFFFFF : 0xFF555555);
        }, (row, screenY, mouseX, mouseY) -> {
            Rect control = controlRect(row, screenY, controlsW);
            Rect edit = new Rect(control.x, control.y, Math.clamp(control.w / 3, 34, 52), CONTROL_H);
            Rect field = new Rect(edit.x + edit.w + 6, control.y, control.w - edit.w - 6, CONTROL_H);
            if (edit.contains(mouseX, mouseY)) {
                state.selectedColor = target;
                return true;
            }
            if (field.contains(mouseX, mouseY)) {
                focusField(fieldId, state.rawInputs.getOrDefault(fieldId, target.get(state.working.popup)));
                state.selectedColor = target;
                return true;
            }
            return false;
        });
        return y + ROW_H + ROW_GAP;
    }

    private int channelRow(int x, int y, int w, int controlsW, ConfigColorChannel channel) {
        ConfigFieldId fieldId = switch (channel) {
            case ALPHA -> ConfigFieldId.POPUP_CHANNEL_A;
            case RED -> ConfigFieldId.POPUP_CHANNEL_R;
            case GREEN -> ConfigFieldId.POPUP_CHANNEL_G;
            case BLUE -> ConfigFieldId.POPUP_CHANNEL_B;
        };
        addRow(x, y, w, fieldId, (graphics, row, screenY, mouseX, mouseY) -> {
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

    private int actionRow(int x, int y, int w, int controlsW, ConfigFieldId fieldId) {
        if (fieldId == ConfigFieldId.PREVIEW_STATE) {
            addRow(x, y, w, fieldId, (graphics, row, screenY, mouseX, mouseY) -> {
                Rect control = controlRect(row, screenY, controlsW);
                Rect running = segment(control, 0, 3);
                Rect done = segment(control, 1, 3);
                Rect failed = segment(control, 2, 3);
                drawButton(graphics, running, Component.translatable(ConfigPreviewState.RUNNING.key()), state.previewState != ConfigPreviewState.RUNNING, running.contains(mouseX, mouseY));
                drawButton(graphics, done, Component.translatable(ConfigPreviewState.COMPLETED.key()), state.previewState != ConfigPreviewState.COMPLETED, done.contains(mouseX, mouseY));
                drawButton(graphics, failed, Component.translatable(ConfigPreviewState.FAILED.key()), state.previewState != ConfigPreviewState.FAILED, failed.contains(mouseX, mouseY));
            }, (row, screenY, mouseX, mouseY) -> {
                Rect control = controlRect(row, screenY, controlsW);
                if (segment(control, 0, 3).contains(mouseX, mouseY)) {
                    state.previewState = ConfigPreviewState.RUNNING;
                    return true;
                }
                if (segment(control, 1, 3).contains(mouseX, mouseY)) {
                    state.previewState = ConfigPreviewState.COMPLETED;
                    return true;
                }
                if (segment(control, 2, 3).contains(mouseX, mouseY)) {
                    state.previewState = ConfigPreviewState.FAILED;
                    return true;
                }
                return false;
            });
        } else if (fieldId == ConfigFieldId.OPEN_PREVIEW) {
            addRow(x, y, w, fieldId, (graphics, row, screenY, mouseX, mouseY) -> {
                Rect control = controlRect(row, screenY, controlsW);
                drawButton(graphics, control, Component.translatable(fieldId.labelKey()), true, control.contains(mouseX, mouseY));
            }, (row, screenY, mouseX, mouseY) -> {
                Rect control = controlRect(row, screenY, controlsW);
                if (!control.contains(mouseX, mouseY)) {
                    return false;
                }
                minecraft.setScreenAndShow(new PopupPreviewScreen(this, state.working.popup, state.previewPayload()));
                return true;
            });
        } else if (fieldId == ConfigFieldId.EXCLUDED_PATHS) {
            int entryCount = Math.max(1, state.working.excludedPaths.size());
            int rowHeight = ROW_H + entryCount * (ROW_H + ROW_GAP);
            addRow(x, y, w, rowHeight, fieldId, (graphics, row, screenY, mouseX, mouseY) -> {
                Rect add = excludedPathsAddRect(row, screenY);
                drawButton(graphics, add, Component.translatable("screen.justenoughbackups.config.increment"), true, add.contains(mouseX, mouseY));

                int listY = screenY + ROW_H + ROW_GAP;
                if (state.working.excludedPaths.isEmpty()) {
                    graphics.text(
                            font,
                            Component.translatable("screen.justenoughbackups.config.excluded_paths.empty"),
                            row.x + ROW_INSET,
                            listY + 11,
                            0xFFAAAAAA,
                            true
                    );
                } else {
                    for (int i = 0; i < state.working.excludedPaths.size(); i++) {
                        Rect field = excludedPathFieldRect(row, screenY, i);
                        Rect remove = excludedPathRemoveRect(row, screenY, i);
                        drawDynamicField(graphics, field, state.focusedExcludedPathIndex != null && state.focusedExcludedPathIndex == i, state.working.excludedPaths.get(i), mouseX, mouseY);
                        drawButton(graphics, remove, Component.translatable("screen.justenoughbackups.config.decrement"), true, remove.contains(mouseX, mouseY));
                    }
                }
            }, (row, screenY, mouseX, mouseY) -> {
                Rect add = excludedPathsAddRect(row, screenY);
                if (add.contains(mouseX, mouseY)) {
                    state.working.excludedPaths.add("");
                    focusExcludedPath(state.working.excludedPaths.size() - 1);
                    validationErrors = validator.validate(state);
                    rebuildRows();
                    return true;
                }

                for (int i = 0; i < state.working.excludedPaths.size(); i++) {
                    Rect remove = excludedPathRemoveRect(row, screenY, i);
                    if (remove.contains(mouseX, mouseY)) {
                        removeExcludedPath(i);
                        validationErrors = validator.validate(state);
                        rebuildRows();
                        return true;
                    }

                    Rect field = excludedPathFieldRect(row, screenY, i);
                    if (field.contains(mouseX, mouseY)) {
                        focusExcludedPath(i);
                        return true;
                    }
                }
                return false;
            });
            return y + rowHeight + ROW_GAP;
        }
        return y + ROW_H + ROW_GAP;
    }

    private int sectionHeaderRow(int x, int y, int w, ConfigFieldId fieldId) {
        addRow(x, y, w, fieldId, (_, _, _, _, _) -> {
        }, (_, _, _, _) -> false);
        return y + ROW_H + ROW_GAP;
    }

    private void addRow(int x, int y, int w, ConfigFieldId fieldId, RowRenderer renderer, RowClick click) {
        rows.add(new ConfigRow(x, y, w, ROW_H, fieldId, fieldId.tooltipKey(), renderer, click));
    }

    private void addRow(int x, int y, int w, int h, ConfigFieldId fieldId, RowRenderer renderer, RowClick click) {
        rows.add(new ConfigRow(x, y, w, h, fieldId, fieldId.tooltipKey(), renderer, click));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, ScreenChrome.BG_COLOR);
        graphics.centeredText(font, title, width / 2, ScreenChrome.TITLE_Y, ScreenChrome.TITLE_COLOR);
        renderTabs(graphics, mouseX, mouseY);
        graphics.horizontalLine(0, width, viewportTop(), ScreenChrome.LINE_COLOR);
        graphics.horizontalLine(0, width, viewportBottom(), ScreenChrome.LINE_COLOR);

        graphics.enableScissor(viewportX(), viewportTop() + 1, viewportRight(), viewportBottom());
        ConfigRow hovered = null;
        for (ConfigRow row : rows) {
            int y = rowScreenY(row) + 1;
            if (y + row.h <= viewportTop() || y >= viewportBottom()) {
                continue;
            }
            int color = isInvalid(row.fieldId) ? ROW_BAD_COLOR : ROW_COLOR;
            graphics.fill(row.x, y, row.x + row.w, y + row.h, color);
            graphics.outline(row.x, y, row.w, row.h, isInvalid(row.fieldId) ? OUTLINE_BAD_COLOR : ScreenChrome.OUTLINE_COLOR);
            graphics.text(font, Component.literal(trimToWidth(Component.translatable(row.fieldId.labelKey()).getString(), Math.max(50, row.w / 2 - 16))), row.x + ROW_INSET, y + 11, 0xFFE0E0E0, true);
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
            drawButton(graphics, rect, Component.translatable(tab.key()), state.selectedTab != tab, rect.contains(mouseX, mouseY));
        }
    }

    private void renderFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int footerY = height - ScreenChrome.OUTER - CONTROL_H;
        if (!validationErrors.isEmpty()) {
            String message = validationErrors.getFirst().message().getString();
            graphics.text(font, Component.literal(trimToWidth(message, Math.max(100, width - 280))), ScreenChrome.OUTER, footerY + 6, 0xFFFF7777, true);
        }
        Rect resetTab = resetTabRect();
        Rect resetAll = resetAllRect();
        Rect save = saveRect();
        drawButton(graphics, resetTab, Component.translatable("screen.justenoughbackups.config.reset_tab"), true, resetTab.contains(mouseX, mouseY));
        drawButton(graphics, resetAll, Component.translatable("screen.justenoughbackups.config.reset_all"), true, resetAll.contains(mouseX, mouseY));
        drawButton(graphics, save, Component.translatable("screen.justenoughbackups.common.save"), validationErrors.isEmpty(), save.contains(mouseX, mouseY));
    }

    private void drawButton(GuiGraphicsExtractor graphics, Rect rect, Component text, boolean active, boolean hovered) {
        ScreenChrome.drawSurfaceButton(graphics, font, new ScreenChrome.Rect(rect.x, rect.y, rect.w, rect.h), text, active, hovered);
    }

    private void drawField(GuiGraphicsExtractor graphics, Rect rect, ConfigFieldId fieldId, String text, int mouseX, int mouseY) {
        boolean focused = fieldId == state.focusedField;
        drawField(graphics, rect, focused, text, mouseX, mouseY);
    }

    private void drawDynamicField(GuiGraphicsExtractor graphics, Rect rect, boolean focused, String text, int mouseX, int mouseY) {
        drawField(graphics, rect, focused, text.isBlank() && !focused
                ? Component.translatable("screen.justenoughbackups.config.excluded_paths.hint").getString()
                : text, mouseX, mouseY);
    }

    private void drawField(GuiGraphicsExtractor graphics, Rect rect, boolean focused, String text, int mouseX, int mouseY) {
        graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, focused ? 0xFF202020 : 0xFF151515);
        graphics.outline(rect.x, rect.y, rect.w, rect.h, focused ? 0xFFFFFFFF : rect.contains(mouseX, mouseY) ? 0xFF888888 : 0xFF555555);
        String visible = trimToWidth(text, Math.max(8, rect.w - 8));
        int color = focused || !text.equals(Component.translatable("screen.justenoughbackups.config.excluded_paths.hint").getString()) ? 0xFFE0E0E0 : 0xFF888888;
        graphics.text(font, visible, rect.x + 4, rect.y + 6, color, true);
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caretX = rect.x + 4 + font.width(text.substring(0, Math.min(state.cursor, text.length())));
            graphics.fill(Math.min(caretX, rect.x + rect.w - 3), rect.y + 4, Math.min(caretX + 1, rect.x + rect.w - 2), rect.y + rect.h - 4, 0xFFFFFFFF);
        }
    }

    private void drawSlider(GuiGraphicsExtractor graphics, Rect rect, ConfigColorChannel channel) {
        int value = channel.extract(state.selectedColor.argb(state.working.popup));
        graphics.fill(rect.x, rect.y + 8, rect.x + rect.w, rect.y + 12, 0xFF303030);
        int knob = rect.x + Math.round((rect.w - 6) * (value / 255.0F));
        graphics.fill(knob, rect.y + 3, knob + 6, rect.y + 17, 0xFFE0E0E0);
        graphics.outline(rect.x, rect.y, rect.w, rect.h, 0xFF555555);
        graphics.centeredText(font, Component.translatable("screen.justenoughbackups.config.channel_value", channel.channelLabel(), value), rect.x + rect.w / 2, rect.y + 6, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mouseX = event.x();
        double mouseY = event.y();
        state.focusedField = null;
        state.focusedExcludedPathIndex = null;
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
                state.selectedTab = tab;
                rebuildRows();
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
        int tabX = x + tab.ordinal() * (tabW + gap);
        return new Rect(tabX, TAB_Y, tabW, TAB_H);
    }

    private boolean handleFooterClick(double mouseX, double mouseY) {
        if (resetTabRect().contains(mouseX, mouseY)) {
            state.resetTab(state.selectedTab);
            clearInputsForTab(state.selectedTab);
            rebuildRows();
            return true;
        }
        if (resetAllRect().contains(mouseX, mouseY)) {
            state.resetAll();
            rebuildRows();
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
        int next = Math.clamp(state.currentScroll() - (int) Math.signum(scrollY) * (ROW_H + ROW_GAP), 0, maxScroll());
        state.setCurrentScroll(next);
        rebuildRows();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (state.focusedExcludedPathIndex != null) {
            String text = excludedPathValue(state.focusedExcludedPathIndex);
            switch (event.key()) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (state.cursor > 0 && !text.isEmpty()) {
                        updateExcludedPath(state.focusedExcludedPathIndex, text.substring(0, state.cursor - 1) + text.substring(state.cursor));
                        state.cursor--;
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (state.cursor < text.length()) {
                        updateExcludedPath(state.focusedExcludedPathIndex, text.substring(0, state.cursor) + text.substring(state.cursor + 1));
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    state.cursor = Math.max(0, state.cursor - 1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    state.cursor = Math.min(text.length(), state.cursor + 1);
                    return true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    state.cursor = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_END -> {
                    state.cursor = text.length();
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER -> {
                    state.focusedExcludedPathIndex = null;
                    return true;
                }
                default -> {
                    return true;
                }
            }
        }
        if (state.focusedField == null) {
            return super.keyPressed(event);
        }
        String text = state.rawInputs.getOrDefault(state.focusedField, "");
        switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (state.cursor > 0 && !text.isEmpty()) {
                    state.rawInputs.put(state.focusedField, text.substring(0, state.cursor - 1) + text.substring(state.cursor));
                    state.cursor--;
                    applyFocusedField();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (state.cursor < text.length()) {
                    state.rawInputs.put(state.focusedField, text.substring(0, state.cursor) + text.substring(state.cursor + 1));
                    applyFocusedField();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                state.cursor = Math.max(0, state.cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                state.cursor = Math.min(text.length(), state.cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                state.cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                state.cursor = text.length();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER -> {
                state.focusedField = null;
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter()) {
            return false;
        }
        if (state.focusedExcludedPathIndex != null) {
            String text = excludedPathValue(state.focusedExcludedPathIndex);
            String inserted = event.codepointAsString();
            if (text.length() + inserted.length() > maxLengthFor(ConfigFieldId.EXCLUDED_PATHS)) {
                return true;
            }
            updateExcludedPath(state.focusedExcludedPathIndex, text.substring(0, state.cursor) + inserted + text.substring(state.cursor));
            state.cursor += inserted.length();
            return true;
        }
        if (state.focusedField == null) {
            return false;
        }
        String text = state.rawInputs.getOrDefault(state.focusedField, "");
        String inserted = event.codepointAsString();
        int maxLength = maxLengthFor(state.focusedField);
        if (text.length() + inserted.length() > maxLength) {
            return true;
        }
        state.rawInputs.put(state.focusedField, text.substring(0, state.cursor) + inserted + text.substring(state.cursor));
        state.cursor += inserted.length();
        applyFocusedField();
        return true;
    }

    private void applyFocusedField() {
        if (state.focusedField == null) {
            return;
        }
        String input = state.rawInputs.getOrDefault(state.focusedField, "");
        switch (state.focusedField) {
            case INTERVAL_MINUTES -> parseInt(input).ifPresent(value -> state.working.automaticIntervalMinutes = value);
            case AUTOMATIC_BACKUP_WARNING_MINUTES -> parseInt(input).ifPresent(value -> state.working.automaticBackupWarningMinutes = value);
            case AUTOMATIC_FULL_INTERVAL_MINUTES -> parseInt(input).ifPresent(value -> state.working.automaticSchedule.full.intervalMinutes = value);
            case AUTOMATIC_PARTIAL_INTERVAL_MINUTES -> parseInt(input).ifPresent(value -> state.working.automaticSchedule.partial.intervalMinutes = value);
            case AUTOMATIC_DIFFERENTIAL_INTERVAL_MINUTES -> parseInt(input).ifPresent(value -> state.working.automaticSchedule.differential.intervalMinutes = value);
            case KEEP_FULL -> parseInt(input).ifPresent(value -> state.working.retention.full = value);
            case KEEP_PARTIAL -> parseInt(input).ifPresent(value -> state.working.retention.incremental = value);
            case KEEP_DIFFERENTIAL -> parseInt(input).ifPresent(value -> state.working.retention.differential = value);
            case MAX_TOTAL_SIZE_MB -> parseInt(input).ifPresent(value -> state.working.retention.maxTotalSizeMb = value);
            case MINIMUM_FREE_SPACE_RESERVE_MB -> parseInt(input).ifPresent(value -> state.working.minimumFreeSpaceReserveMb = value);
            case PERMISSION_LEVEL -> parseInt(input).ifPresent(value -> state.working.commandPermissionLevel = value);
            case BACKUP_DIRECTORY -> state.working.backupDirectory = input;
            case POPUP_TITLE -> state.working.popup.title = input;
            case POPUP_RUNNING_TEXT -> state.working.popup.runningText = input;
            case POPUP_COMPLETED_TEXT -> state.working.popup.completedText = input;
            case POPUP_FAILED_TEXT -> state.working.popup.failedText = input;
            case POPUP_BACKGROUND_COLOR, POPUP_RUNNING_COLOR, POPUP_COMPLETED_COLOR, POPUP_FAILED_COLOR, POPUP_TEXT_COLOR -> {
                ConfigColor.parse(input).ifPresent(color -> colorTarget(state.focusedField).set(state.working.popup, ConfigColor.format(color)));
            }
            default -> {
            }
        }
        validationErrors = validator.validate(state);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void refreshAfterPreview() {
        rebuildWidgets();
    }

    private void saveAndClose() {
        validationErrors = validator.validate(state);
        if (validationErrors.isEmpty()) {
            BackupConfig.saveAndApply(state.working);
            minecraft.setScreenAndShow(parent);
        }
    }

    private void clearInputsForTab(ConfigTab tab) {
        state.rawInputs.keySet().removeIf(fieldId -> fieldId.tab() == tab);
        state.focusedField = null;
        state.focusedExcludedPathIndex = null;
    }

    private boolean isInvalid(ConfigFieldId fieldId) {
        for (ValidationError error : validationErrors) {
            if (error.fieldId() == fieldId) {
                return true;
            }
        }
        return false;
    }

    private void clampPreviewPosition() {
        PopupPositioning.clampAndRemember(font, state.working.popup, state.previewPayload(), width, height);
    }

    private int contentX() {
        return ScreenChrome.contentX();
    }

    private int contentWidth() {
        return Math.max(1, ScreenChrome.contentWidth(width) - SCROLL_GUTTER);
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
        int width = index == count - 1 ? rect.x + rect.w - x : each;
        return new Rect(x, rect.y, width, rect.h);
    }

    private int rowScreenY(ConfigRow row) {
        return viewportTop() + row.y - state.currentScroll();
    }

    private int maxScroll() {
        int contentH = 0;
        for (ConfigRow row : rows) {
            contentH = Math.max(contentH, row.y + row.h);
        }
        return Math.max(0, contentH - viewportHeight());
    }

    private void clampScroll() {
        state.setCurrentScroll(Math.clamp(state.currentScroll(), 0, maxScroll()));
    }

    private int viewportX() {
        return ScreenChrome.contentX();
    }

    private int viewportRight() {
        return ScreenChrome.viewportRight(width);
    }

    private int viewportTop() {
        return ScreenChrome.viewportTop();
    }

    private int viewportBottom() {
        return footerTop();
    }

    private int viewportHeight() {
        return Math.max(1, viewportBottom() - viewportTop());
    }

    private int footerTop() {
        return ScreenChrome.footerTop(height);
    }

    private boolean isInsideViewport(double mouseY) {
        return mouseY >= viewportTop() && mouseY <= viewportBottom();
    }

    private boolean hasScrollbar() {
        return maxScroll() > 0;
    }

    private int scrollbarX() {
        return ScreenChrome.viewportRight(width) - (SCROLL_GUTTER + SCROLL_W) / 2;
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
        return max == 0 ? viewportTop() : viewportTop() + state.currentScroll() * travel / max;
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
        state.setCurrentScroll(Math.clamp((y - viewportTop()) * maxScroll() / travel, 0, maxScroll()));
    }

    private void setChannelFromMouse(Rect rect, ConfigColorChannel channel, double mouseX) {
        int value = Math.clamp((int) Math.round(((mouseX - rect.x) / Math.max(1.0D, rect.w - 1)) * 255.0D), 0, 255);
        int color = state.selectedColor.argb(state.working.popup);
        state.selectedColor.set(state.working.popup, ConfigColor.format(channel.apply(color, value)));
        state.rawInputs.put(colorFieldId(state.selectedColor), state.selectedColor.get(state.working.popup));
        rebuildRows();
    }

    private ConfigFieldId colorFieldId(ConfigColorTarget target) {
        return switch (target) {
            case BACKGROUND -> ConfigFieldId.POPUP_BACKGROUND_COLOR;
            case RUNNING -> ConfigFieldId.POPUP_RUNNING_COLOR;
            case COMPLETED -> ConfigFieldId.POPUP_COMPLETED_COLOR;
            case FAILED -> ConfigFieldId.POPUP_FAILED_COLOR;
            case TEXT -> ConfigFieldId.POPUP_TEXT_COLOR;
        };
    }

    private ConfigRow rowForChannel(ConfigColorChannel channel) {
        ConfigFieldId fieldId = switch (channel) {
            case ALPHA -> ConfigFieldId.POPUP_CHANNEL_A;
            case RED -> ConfigFieldId.POPUP_CHANNEL_R;
            case GREEN -> ConfigFieldId.POPUP_CHANNEL_G;
            case BLUE -> ConfigFieldId.POPUP_CHANNEL_B;
        };
        for (ConfigRow row : rows) {
            if (row.fieldId == fieldId) {
                return row;
            }
        }
        return null;
    }

    private Rect resetTabRect() {
        int y = height - ScreenChrome.OUTER - CONTROL_H;
        int x = Math.max(ScreenChrome.OUTER, width - ScreenChrome.OUTER - 248);
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

    private void focusField(ConfigFieldId id, String value) {
        state.focusedField = id;
        state.focusedExcludedPathIndex = null;
        state.rawInputs.putIfAbsent(id, value);
        state.cursor = state.rawInputs.get(id).length();
    }

    private void focusExcludedPath(int index) {
        state.focusedField = null;
        state.focusedExcludedPathIndex = index;
        state.cursor = excludedPathValue(index).length();
    }

    private String excludedPathValue(int index) {
        if (index < 0 || index >= state.working.excludedPaths.size()) {
            return "";
        }
        return value(state.working.excludedPaths.get(index));
    }

    private void updateExcludedPath(int index, String value) {
        if (index < 0 || index >= state.working.excludedPaths.size()) {
            return;
        }
        state.working.excludedPaths.set(index, value);
        validationErrors = validator.validate(state);
    }

    private void removeExcludedPath(int index) {
        if (index < 0 || index >= state.working.excludedPaths.size()) {
            return;
        }
        state.working.excludedPaths.remove(index);
        if (state.focusedExcludedPathIndex == null) {
            return;
        }
        if (state.focusedExcludedPathIndex == index) {
            state.focusedExcludedPathIndex = null;
            state.cursor = 0;
        } else if (state.focusedExcludedPathIndex > index) {
            state.focusedExcludedPathIndex--;
        }
    }

    private Rect excludedPathsAddRect(ConfigRow row, int screenY) {
        Rect control = controlRect(row, screenY, Math.min(controlsWidthForRow(row), 48));
        return new Rect(control.x + control.w - LIST_BUTTON_W, control.y, LIST_BUTTON_W, CONTROL_H);
    }

    private Rect excludedPathFieldRect(ConfigRow row, int screenY, int index) {
        int y = screenY + ROW_H + ROW_GAP + index * (ROW_H + ROW_GAP) + 6;
        int x = row.x + ROW_INSET;
        int w = row.w - ROW_INSET * 2 - LIST_BUTTON_W - 6;
        return new Rect(x, y, w, CONTROL_H);
    }

    private Rect excludedPathRemoveRect(ConfigRow row, int screenY, int index) {
        Rect field = excludedPathFieldRect(row, screenY, index);
        return new Rect(field.x + field.w + 6, field.y, LIST_BUTTON_W, CONTROL_H);
    }

    private int controlsWidthForRow(ConfigRow row) {
        return Math.min(row.w, Math.clamp(row.w / 2, 80, 240));
    }

    private Component toggleMessage(boolean enabled) {
        return Component.translatable(enabled ? "screen.justenoughbackups.config.toggle.on" : "screen.justenoughbackups.config.toggle.off");
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

    private int maxLengthFor(ConfigFieldId fieldId) {
        return ConfigFieldSpecs.forTab(fieldId.tab()).stream()
                .filter(spec -> spec.id() == fieldId)
                .findFirst()
                .map(ConfigFieldSpec::maxLength)
                .orElse(256);
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

    @FunctionalInterface
    private interface RowRenderer {
        void render(GuiGraphicsExtractor graphics, ConfigRow row, int screenY, int mouseX, int mouseY);
    }

    @FunctionalInterface
    private interface RowClick {
        boolean click(ConfigRow row, int screenY, double mouseX, double mouseY);
    }

    private record ConfigRow(int x, int y, int w, int h, ConfigFieldId fieldId, String tooltipKey, RowRenderer renderer, RowClick click) {
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }
}
