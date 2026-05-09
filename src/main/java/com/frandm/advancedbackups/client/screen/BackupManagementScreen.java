package com.frandm.advancedbackups.client.screen;

import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.client.AdvancedBackupsConfigScreens;
import com.frandm.advancedbackups.client.BackupUiClient;
import com.frandm.advancedbackups.client.BackupUiResponseConsumer;
import com.frandm.advancedbackups.config.BackupConfig;
import com.frandm.advancedbackups.network.BackupUiBackup;
import com.frandm.advancedbackups.network.BackupUiResponsePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BackupManagementScreen extends Screen implements BackupUiResponseConsumer {
    private static final int TOOLBAR_HEIGHT = 34;
    private static final int ROW_HEIGHT = 92;
    private static final int ROW_GAP = 8;
    private static final int MARGIN = 14;
    private static final int ACTION_WIDTH = 86;
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final List<BackupUiBackup> backups = new ArrayList<>();
    private EditBox searchBox;
    private String filter = "";
    private String status = "Loading backups...";
    private boolean statusOk = true;
    private int scroll;

    public BackupManagementScreen() {
        super(Component.literal("Advanced Backups"));
    }

    @Override
    protected void init() {
        rebuildWidgets();
        BackupUiClient.requestList();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        int x = MARGIN;
        int y = MARGIN;

        searchBox = new EditBox(font, x, y, Math.min(260, width / 3), 20, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setValue(filter);
        searchBox.setResponder(value -> {
            filter = value == null ? "" : value;
            scroll = 0;
            rebuildWidgets();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        int buttonX = searchBox.getX() + searchBox.getWidth() + 8;
        addRenderableWidget(Button.builder(Component.literal("Create"), button -> minecraft.setScreen(new CreateBackupScreen(this)))
                .bounds(buttonX, y, 76, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> BackupUiClient.requestList())
                .bounds(buttonX + 82, y, 76, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Config"), button -> minecraft.setScreen(AdvancedBackupsConfigScreens.create(this)))
                .bounds(width - MARGIN - 60, y, 60, 20)
                .build());

        List<BackupUiBackup> visible = visibleBackups();
        int listTop = listTop();
        int listBottom = listBottom();
        for (int i = 0; i < visible.size(); i++) {
            int rowY = listTop + i * (ROW_HEIGHT + ROW_GAP) - scroll;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }
            addRowButtons(visible.get(i), rowY);
        }
    }

    private void addRowButtons(BackupUiBackup backup, int rowY) {
        int actionX = width - MARGIN - ACTION_WIDTH;
        int y = rowY + 10;
        addRenderableWidget(Button.builder(Component.literal("Restore"), button -> confirmRestore(backup))
                .bounds(actionX, y, ACTION_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Rename"), button -> minecraft.setScreen(new RenameBackupScreen(this, backup)))
                .bounds(actionX, y + 24, ACTION_WIDTH, 20)
                .build());
        Button delete = Button.builder(Component.literal("Delete"), button -> confirmDelete(backup))
                .bounds(actionX, y + 48, ACTION_WIDTH, 20)
                .build();
        delete.active = backup.canDelete();
        addRenderableWidget(delete);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC080808);
        graphics.text(font, title, MARGIN, 6, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal(status), MARGIN, TOOLBAR_HEIGHT + 4, statusOk ? 0xFFB8E986 : 0xFFFF7777, true);

        int listTop = listTop();
        graphics.fill(MARGIN, listTop - 6, width - MARGIN, height - MARGIN, 0x88000000);
        List<BackupUiBackup> visible = visibleBackups();
        if (visible.isEmpty()) {
            graphics.text(font, Component.literal(backups.isEmpty() ? "No backups found for this world." : "No backups match the search."),
                    MARGIN + 10, listTop + 12, 0xFFB0B0B0, true);
        }

        for (int i = 0; i < visible.size(); i++) {
            int rowY = listTop + i * (ROW_HEIGHT + ROW_GAP) - scroll;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom()) {
                continue;
            }
            renderRow(graphics, visible.get(i), rowY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphicsExtractor graphics, BackupUiBackup backup, int rowY) {
        int rowX = MARGIN + 4;
        int rowW = width - MARGIN * 2 - 8;
        int actionX = width - MARGIN - ACTION_WIDTH;
        graphics.fill(rowX, rowY, rowX + rowW, rowY + ROW_HEIGHT, 0xDD151515);
        graphics.outline(rowX, rowY, rowW, ROW_HEIGHT, 0xFF4A4A4A);

        int textX = rowX + 10;
        int maxTextWidth = Math.max(80, actionX - textX - 12);
        graphics.text(font, Component.literal(trimToWidth(backup.displayName(), maxTextWidth)), textX, rowY + 9, 0xFFFFFFFF, true);
        String meta = backup.type() + " | " + shortDate(backup.createdAt()) + " | "
                + backup.includedFiles() + " files | " + formatBytes(backup.includedBytes());
        graphics.text(font, Component.literal(trimToWidth(meta, maxTextWidth)), textX, rowY + 29, 0xFFC8C8C8, true);
        graphics.text(font, Component.literal(trimToWidth(reasonLine(backup), maxTextWidth)), textX, rowY + 47, 0xFF8FB3FF, true);
        if (!backup.canDelete() && !backup.deleteBlockedReason().isBlank()) {
            graphics.text(font, Component.literal(trimToWidth(backup.deleteBlockedReason(), maxTextWidth)), textX, rowY + 67, 0xFFFFC47A, true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll();
        scroll = Math.clamp(scroll - (int) (scrollY * 24), 0, max);
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
    public void handleResponse(BackupUiResponsePayload payload) {
        setStatus(payload.success(), payload.message());
        if (!payload.backups().isEmpty() || payload.success()) {
            backups.clear();
            backups.addAll(payload.backups());
            backups.sort(Comparator.comparing((BackupUiBackup backup) -> value(backup.createdAt())).reversed());
            scroll = Math.clamp(scroll, 0, maxScroll());
        }
        rebuildWidgets();
    }

    @Override
    public void setStatus(boolean ok, String message) {
        statusOk = ok;
        status = message == null || message.isBlank() ? (ok ? "Ready" : "Request failed") : message;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void confirmRestore(BackupUiBackup backup) {
        minecraft.setScreen(new ConfirmScreen(first -> {
            if (!first) {
                minecraft.setScreen(this);
                return;
            }
            minecraft.setScreen(new ConfirmScreen(second -> {
                minecraft.setScreen(this);
                if (second) {
                    BackupUiClient.restoreBackup(backup.id());
                }
            }, Component.literal("Restore backup?"), Component.literal("This will stop the server after preparing the restored world.")));
        }, Component.literal("Restore " + backup.displayName() + "?"), Component.literal("The current world will be preserved as a full backup first.")));
    }

    private void confirmDelete(BackupUiBackup backup) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                BackupUiClient.deleteBackup(backup.id());
            }
        }, Component.literal("Delete " + backup.displayName() + "?"), Component.literal("This permanently removes the ZIP file.")));
    }

    private List<BackupUiBackup> visibleBackups() {
        if (filter.isBlank()) {
            return backups;
        }
        String needle = filter.toLowerCase(Locale.ROOT);
        return backups.stream()
                .filter(backup -> backup.displayName().toLowerCase(Locale.ROOT).contains(needle)
                        || backup.type().name().toLowerCase(Locale.ROOT).contains(needle)
                        || value(backup.reason()).toLowerCase(Locale.ROOT).contains(needle)
                        || value(backup.baseBackupId()).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    private int listTop() {
        return TOOLBAR_HEIGHT + 24;
    }

    private int listBottom() {
        return height - MARGIN;
    }

    private int maxScroll() {
        int contentHeight = visibleBackups().size() * (ROW_HEIGHT + ROW_GAP);
        int viewportHeight = Math.max(1, listBottom() - listTop());
        return Math.max(0, contentHeight - viewportHeight);
    }

    private String trimToWidth(String value, int maxWidth) {
        String text = value == null ? "" : value;
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        while (!text.isEmpty() && font.width(text + suffix) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + suffix;
    }

    private static String reasonLine(BackupUiBackup backup) {
        String reason = value(backup.reason()).isBlank() ? "manual" : value(backup.reason()).replace('_', ' ');
        String base = value(backup.baseBackupId()).isBlank() ? "none" : backup.baseBackupId();
        return "Reason: " + reason + " | Base: " + base;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String shortDate(String value) {
        try {
            return SHORT_DATE.format(Instant.parse(value));
        } catch (DateTimeParseException exception) {
            return value == null ? "" : value;
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (value >= 1024.0D && unit < units.length - 1) {
            value /= 1024.0D;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private static final class CreateBackupScreen extends Screen {
        private final BackupManagementScreen parent;
        private BackupType selected = BackupConfig.get().backupMode;

        private CreateBackupScreen(BackupManagementScreen parent) {
            super(Component.literal("Create Backup"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int panelWidth = 260;
            int x = (width - panelWidth) / 2;
            int y = height / 2 - 42;
            addRenderableWidget(CycleButton.builder((BackupType type) -> Component.literal(type.toString()), selected)
                    .withValues(BackupType.values())
                    .create(x + 20, y + 18, panelWidth - 40, 20, Component.literal("Type"), (button, value) -> selected = value));
            addRenderableWidget(Button.builder(Component.literal("Create"), button -> {
                minecraft.setScreen(parent);
                BackupUiClient.createBackup(selected);
            }).bounds(x + 38, y + 52, 84, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> minecraft.setScreen(parent))
                    .bounds(x + 138, y + 52, 84, 20).build());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xAA000000);
            int panelWidth = 260;
            int x = (width - panelWidth) / 2;
            int y = height / 2 - 42;
            graphics.fill(x, y, x + panelWidth, y + 88, 0xEE151515);
            graphics.outline(x, y, panelWidth, 88, 0xFF4A4A4A);
            graphics.centeredText(font, title, width / 2, y + 7, 0xFFFFFFFF);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class RenameBackupScreen extends Screen {
        private final BackupManagementScreen parent;
        private final BackupUiBackup backup;
        private EditBox nameBox;

        private RenameBackupScreen(BackupManagementScreen parent, BackupUiBackup backup) {
            super(Component.literal("Rename Backup"));
            this.parent = parent;
            this.backup = backup;
        }

        @Override
        protected void init() {
            int panelWidth = 300;
            int x = (width - panelWidth) / 2;
            int y = height / 2 - 42;
            nameBox = new EditBox(font, x + 20, y + 24, panelWidth - 40, 20, Component.literal("Backup name"));
            nameBox.setValue(backup.displayName());
            addRenderableWidget(nameBox);
            addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
                minecraft.setScreen(parent);
                BackupUiClient.renameBackup(backup.id(), nameBox.getValue());
            }).bounds(x + 58, y + 56, 84, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> minecraft.setScreen(parent))
                    .bounds(x + 158, y + 56, 84, 20).build());
            setInitialFocus(nameBox);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xAA000000);
            int panelWidth = 300;
            int x = (width - panelWidth) / 2;
            int y = height / 2 - 42;
            graphics.fill(x, y, x + panelWidth, y + 90, 0xEE151515);
            graphics.outline(x, y, panelWidth, 90, 0xFF4A4A4A);
            graphics.centeredText(font, title, width / 2, y + 7, 0xFFFFFFFF);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }
}
