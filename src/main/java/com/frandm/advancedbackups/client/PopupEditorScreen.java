package com.frandm.advancedbackups.client;

import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.backup.progress.BackupProgressPayload;
import com.frandm.advancedbackups.backup.progress.BackupProgressState;
import com.frandm.advancedbackups.config.BackupConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

final class PopupEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_PADDING = 10;
    private static final int CONTROL_HEIGHT = 20;
    private static final int DEFAULT_PREVIEW_BYTES = 128 * 1024 * 1024;
    private static final int DEFAULT_TOTAL_BYTES = 304 * 1024 * 1024;

    private final Screen parent;
    private final BackupConfig config;
    private BackupConfig.Popup working;
    private PreviewState previewState = PreviewState.RUNNING;
    private ColorTarget selectedColor = ColorTarget.BACKGROUND;
    private final Map<ColorTarget, Button> colorButtons = new EnumMap<>(ColorTarget.class);
    private final Map<ColorTarget, EditBox> colorFields = new EnumMap<>(ColorTarget.class);
    private final Map<ColorChannel, ColorSlider> sliders = new EnumMap<>(ColorChannel.class);
    private boolean updatingColorFields;
    private boolean draggingPreview;
    private int dragOffsetX;
    private int dragOffsetY;

    PopupEditorScreen(Screen parent, BackupConfig config) {
        super(Component.literal("Edit backup popup"));
        this.parent = parent;
        this.config = config;
        this.working = config.popup.copy();
    }

    @Override
    protected void init() {
        colorButtons.clear();
        colorFields.clear();
        sliders.clear();

        int panelX = panelX();
        int y = 34;

        addRenderableWidget(Button.builder(Component.literal("Running"), button -> setPreviewState(PreviewState.RUNNING))
                .bounds(panelX, y, 66, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> setPreviewState(PreviewState.COMPLETED))
                .bounds(panelX + 70, y, 66, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Failed"), button -> setPreviewState(PreviewState.FAILED))
                .bounds(panelX + 140, y, 66, CONTROL_HEIGHT)
                .build());

        y += 34;
        for (ColorTarget target : ColorTarget.values()) {
            ColorTarget captured = target;
            Button button = Button.builder(Component.literal(target.label), ignored -> {
                        selectedColor = captured;
                        syncSlidersFromSelectedColor();
                        refreshColorButtons();
                    })
                    .bounds(panelX + 26, y, 76, CONTROL_HEIGHT)
                    .build();
            colorButtons.put(target, addRenderableWidget(button));

            EditBox field = new EditBox(font, panelX + 108, y, 100, CONTROL_HEIGHT, Component.literal(target.label + " color"));
            field.setMaxLength(10);
            field.setValue(target.get(working));
            field.setResponder(value -> {
                if (updatingColorFields) {
                    return;
                }
                parseColor(value).ifPresent(color -> {
                    target.set(working, formatColor(color));
                    if (target == selectedColor) {
                        syncSlidersFromSelectedColor();
                    }
                });
            });
            colorFields.put(target, addRenderableWidget(field));
            y += 24;
        }

        y += 8;
        for (ColorChannel channel : ColorChannel.values()) {
            ColorSlider slider = new ColorSlider(panelX, y, 208, CONTROL_HEIGHT, channel, this::setSelectedChannel);
            sliders.put(channel, addRenderableWidget(slider));
            y += 24;
        }

        int buttonY = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveAndClose())
                .bounds(panelX, buttonY, 64, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(panelX + 72, buttonY, 64, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> resetDefaults())
                .bounds(panelX + 144, buttonY, 64, CONTROL_HEIGHT)
                .build());

        clampPreviewPosition();
        syncSlidersFromSelectedColor();
        refreshColorButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.text(font, title, 12, 12, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Drag the preview. Arrow keys nudge it."), 12, height - 18, 0xFFB0B0B0, true);

        int panelX = panelX() - PANEL_PADDING;
        graphics.fill(panelX, 0, width, height, 0xCC101010);
        graphics.text(font, Component.literal("Preview"), panelX + PANEL_PADDING, 12, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Colors"), panelX + PANEL_PADDING, 56, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("RGBA"), panelX + PANEL_PADDING, 196, 0xFFFFFFFF, true);

        renderSwatches(graphics);
        BackupPopupRenderer.render(graphics, font, working, previewPayload(), working.x, working.y);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsidePreview(event.x(), event.y())) {
            draggingPreview = true;
            dragOffsetX = (int) event.x() - working.x;
            dragOffsetY = (int) event.y() - working.y;
            setDragging(true);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingPreview) {
            working.x = (int) event.x() - dragOffsetX;
            working.y = (int) event.y() - dragOffsetY;
            clampPreviewPosition();
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
        int amount = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> {
                working.x -= amount;
                clampPreviewPosition();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                working.x += amount;
                clampPreviewPosition();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                working.y -= amount;
                clampPreviewPosition();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                working.y += amount;
                clampPreviewPosition();
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
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void setPreviewState(PreviewState previewState) {
        this.previewState = previewState;
    }

    private void saveAndClose() {
        config.popup = working.copy();
        BackupConfig liveConfig = BackupConfig.get().copy();
        liveConfig.popup = working.copy();
        BackupConfig.saveAndApply(liveConfig);
        minecraft.setScreen(parent);
    }

    private void resetDefaults() {
        working = BackupConfig.defaults().popup.copy();
        selectedColor = ColorTarget.BACKGROUND;
        clampPreviewPosition();
        syncColorFields();
        syncSlidersFromSelectedColor();
        refreshColorButtons();
    }

    private void setSelectedChannel(ColorChannel channel, int value) {
        int color = selectedColor.argb(working);
        int updated = channel.apply(color, value);
        selectedColor.set(working, formatColor(updated));
        syncColorFields();
        refreshColorButtons();
    }

    private void syncColorFields() {
        updatingColorFields = true;
        for (ColorTarget target : ColorTarget.values()) {
            EditBox field = colorFields.get(target);
            if (field != null) {
                field.setValue(target.get(working));
            }
        }
        updatingColorFields = false;
    }

    private void syncSlidersFromSelectedColor() {
        int color = selectedColor.argb(working);
        for (ColorChannel channel : ColorChannel.values()) {
            ColorSlider slider = sliders.get(channel);
            if (slider != null) {
                slider.setChannelValue(channel.extract(color));
            }
        }
    }

    private void refreshColorButtons() {
        for (Map.Entry<ColorTarget, Button> entry : colorButtons.entrySet()) {
            String prefix = entry.getKey() == selectedColor ? "> " : "";
            entry.getValue().setMessage(Component.literal(prefix + entry.getKey().label));
        }
    }

    private void renderSwatches(GuiGraphicsExtractor graphics) {
        int panelX = panelX();
        for (ColorTarget target : ColorTarget.values()) {
            EditBox field = colorFields.get(target);
            if (field == null) {
                continue;
            }
            int y = field.getY();
            int color = target.argb(working);
            graphics.fill(panelX, y + 2, panelX + 18, y + 18, 0xFF000000);
            graphics.fill(panelX + 1, y + 3, panelX + 17, y + 17, color);
            if (target == selectedColor) {
                graphics.outline(panelX - 1, y + 1, 20, 18, 0xFFFFFFFF);
            }
        }
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        BackupPopupRenderer.Dimensions dimensions = BackupPopupRenderer.measure(font, working, previewPayload());
        return mouseX >= working.x - 4
                && mouseX <= working.x + dimensions.width()
                && mouseY >= working.y - 4
                && mouseY <= working.y + dimensions.height();
    }

    private void clampPreviewPosition() {
        BackupPopupRenderer.Dimensions dimensions = BackupPopupRenderer.measure(font, working, previewPayload());
        working.x = Math.clamp(working.x, 4, Math.max(4, width - dimensions.width()));
        working.y = Math.clamp(working.y, 4, Math.max(4, height - dimensions.height()));
    }

    private BackupProgressPayload previewPayload() {
        BackupProgressState state = switch (previewState) {
            case RUNNING -> BackupProgressState.RUNNING;
            case COMPLETED -> BackupProgressState.COMPLETED;
            case FAILED -> BackupProgressState.FAILED;
        };
        long written = state == BackupProgressState.COMPLETED ? DEFAULT_TOTAL_BYTES : DEFAULT_PREVIEW_BYTES;
        return new BackupProgressPayload(
                "preview",
                BackupType.FULL,
                "automatic",
                written,
                DEFAULT_TOTAL_BYTES,
                42,
                100,
                state
        );
    }

    private int panelX() {
        return Math.max(12, width - PANEL_WIDTH - PANEL_PADDING);
    }

    private static java.util.Optional<Integer> parseColor(String value) {
        if (value == null) {
            return java.util.Optional.empty();
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
            return java.util.Optional.empty();
        }

        try {
            return java.util.Optional.of((int) Long.parseLong(normalized, 16));
        } catch (NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }

    private static String formatColor(int color) {
        return String.format(Locale.ROOT, "0x%08X", color);
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
}
