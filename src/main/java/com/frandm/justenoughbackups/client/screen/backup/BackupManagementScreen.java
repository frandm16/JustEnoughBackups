package com.frandm.justenoughbackups.client.screen.backup;

import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.client.net.BackupUiClient;
import com.frandm.justenoughbackups.client.net.BackupUiResponseConsumer;
import com.frandm.justenoughbackups.client.screen.config.JEBConfigScreens;
import com.frandm.justenoughbackups.client.ui.ScreenChrome;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.frandm.justenoughbackups.network.BackupUiBackup;
import com.frandm.justenoughbackups.network.BackupUiResponsePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BackupManagementScreen extends Screen implements BackupUiResponseConsumer {
    private static final int HEADER_Y = 34;
    private static final int TOOLBAR_HEIGHT = 22;
    private static final int LIST_HEADER_HEIGHT = 18;
    private static final int ROW_HEADER_HEIGHT = 54;
    private static final int DETAIL_LINE_HEIGHT = 14;
    private static final int ROW_GAP = 6;
    private static final int MARGIN = 14;
    private static final int ROW_PADDING = 10;
    private static final int ACTION_WIDTH = 54;
    private static final int ACTION_GAP = 4;
    private static final int EXPAND_SIZE = 14;
    private static final int MAX_INLINE_CHILDREN = 6;
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    public static final int TEXT_COLOR = ScreenChrome.TITLE_COLOR;
    public static final int STATUS_OK_COLOR = 0xFFB8E986;
    public static final int STATUS_NOT_OK_COLOR = 0xFFFF7777;
    public static final int ROW_HOVERED_COLOR = 0xE0171717;
    public static final int ROW_COLOR = 0xDD151515;
    public static final int ROW_BORDER_COLOR = 0xFF3F3F3F;
    public static final int TEXT_ALTER_COLOR = 0xFFB8B8B8;

    private final List<BackupUiBackup> backups = new ArrayList<>();
    private final Map<String, Boolean> expandedById = new HashMap<>();
    private EditBox searchBox;
    private String filter = "";
    private Component status = Component.translatable("screen.justenoughbackups.backups.status.loading");
    private boolean statusOk = true;
    private int scroll;
    private List<BackupListItem> items = List.of();
    private List<RowLayout> rowLayouts = List.of();

    public BackupManagementScreen() {
        super(Component.translatable("screen.justenoughbackups.backups.title"));
    }

    @Override
    protected void init() {
        rebuildWidgets();
        BackupUiClient.requestList();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        buildToolbar();
        items = buildItems();
        rowLayouts = buildRowLayouts(filteredItems());
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    private void buildToolbar() {
        int x = contentX();
        int y = HEADER_Y;

        searchBox = new EditBox(font, x, y, Math.min(280, width / 3), 20, Component.translatable("screen.justenoughbackups.backups.search"));
        searchBox.setHint(Component.translatable("screen.justenoughbackups.backups.search_hint"));
        searchBox.setValue(filter);
        searchBox.setResponder(value -> {
            filter = value == null ? "" : value;
            scroll = 0;
            rebuildWidgets();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, ScreenChrome.BG_COLOR);
        graphics.centeredText(font, title, width / 2, ScreenChrome.TITLE_Y, TEXT_COLOR);
        graphics.horizontalLine(0, width, viewportTop(), ScreenChrome.LINE_COLOR);
        graphics.horizontalLine(0, width, footerTop(), ScreenChrome.LINE_COLOR);
        renderToolbar(graphics, mouseX, mouseY);

        int listTop = listTop();
        renderListHeader(graphics);

        graphics.enableScissor(rowX() - 4, listContentTop(), rowX() + rowWidth() + 4, footerTop() - 4);
        try {
            if (rowLayouts.isEmpty()) {
                graphics.text(font, Component.translatable(backups.isEmpty() ? "screen.justenoughbackups.backups.empty" : "screen.justenoughbackups.backups.no_matches"),
                        MARGIN + 10, listTop + LIST_HEADER_HEIGHT + 10, TEXT_COLOR, true);
            }

            for (RowLayout layout : rowLayouts) {
                int rowY = screenY(layout);
                if (rowY + layout.height() < listContentTop() || rowY > listBottom()) {
                    continue;
                }
                renderRow(graphics, layout, rowY, mouseX, mouseY);
            }
        } finally {
            graphics.disableScissor();
        }

        renderFooter(graphics);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawSurfaceButton(graphics, createButtonRect(), Component.translatable("screen.justenoughbackups.backups.create"), mouseX, mouseY, true);
        drawSurfaceButton(graphics, refreshButtonRect(), Component.translatable("screen.justenoughbackups.backups.refresh"), mouseX, mouseY, true);
        drawSurfaceButton(graphics, configButtonRect(), Component.translatable("screen.justenoughbackups.backups.config"), mouseX, mouseY, true);
    }

    private void renderFooter(GuiGraphicsExtractor graphics) {
        int footerY = height - ScreenChrome.OUTER - 10;
        int count = filteredItems().size();
        String summary = count == backups.size()
                ? count + " backups"
                : count + "/" + backups.size() + " backups";
        graphics.text(font, status, ScreenChrome.OUTER, footerY, statusOk ? STATUS_OK_COLOR : STATUS_NOT_OK_COLOR, true);
        graphics.text(font, summary, viewportRight() - font.width(summary), footerY, 0xFF9B9B9B, true);
    }

    private void renderListHeader(GuiGraphicsExtractor graphics) {
        int x = rowX();
        int y = listTop();
        int w = rowWidth();
        graphics.fill(x, y, x + w, y + LIST_HEADER_HEIGHT, 0xAA111111);
        graphics.horizontalLine(x, x + w -1, y + LIST_HEADER_HEIGHT, 0xFF353535);

        int actionsX = actionsX(w);
        graphics.text(font, Component.translatable("screen.justenoughbackups.backups.column.backup"), x + ROW_PADDING + EXPAND_SIZE + 8, y + 5, 0xFF8F8F8F, false);
        graphics.text(font, Component.translatable("screen.justenoughbackups.backups.column.details"), x + 218, y + 5, 0xFF8F8F8F, false);
        graphics.text(font, Component.translatable("screen.justenoughbackups.backups.column.actions"), actionsX + 12, y + 5, 0xFF8F8F8F, false);
    }

    private void renderRow(GuiGraphicsExtractor graphics, RowLayout layout, int rowY, int mouseX, int mouseY) {
        int x = rowX();
        int w = rowWidth();
        int actionsX = actionsX(w);
        int headerBottom = rowY + ROW_HEADER_HEIGHT;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY <= rowY + layout.height();

        graphics.fill(x, rowY, x + w, rowY + layout.height(), hovered ? ROW_HOVERED_COLOR : ROW_COLOR);
        graphics.outline(x, rowY, w, layout.height(), ROW_BORDER_COLOR);
        graphics.horizontalLine(x + 1, x + w - 1, headerBottom - 1, ROW_BORDER_COLOR);

        Rect expander = expanderRect(layout, rowY);
        if (layout.item().hasDetails()) {
            drawExpander(graphics, expander, expanded(layout.item()));
        }

        int nameX = expander.x() + expander.w() + 8;
        int metaX = x + 244;
        int dependencyX = x + 420;
        int maxNameWidth = Math.max(90, metaX - nameX - 10);
        int maxMetaWidth = Math.max(90, dependencyX - metaX - 10);
        int maxDependencyWidth = Math.max(80, actionsX - dependencyX - 10);

        graphics.text(font, Component.literal(trimToWidth(layout.item().backup().displayName(), maxNameWidth)), nameX, rowY + 8, TEXT_COLOR, true);
        drawTypeBadge(graphics, typeBadgeRect(nameX, rowY + 24), layout.item().backup().type());
        graphics.text(font, Component.literal(trimToWidth(shortDate(layout.item().backup().createdAt()), maxMetaWidth)), metaX, rowY + 8, TEXT_COLOR, true);
        graphics.text(font, Component.literal(trimToWidth(formatBytes(layout.item().backup().includedBytes()) + " | " + layout.item().backup().includedFiles() + " files", maxMetaWidth)), metaX, rowY + 26, TEXT_ALTER_COLOR, true);

        Component dependencySummary = dependencySummary(layout.item());
        graphics.text(font, Component.literal(trimToWidth(dependencySummary.getString(), maxDependencyWidth)), dependencyX, rowY + 8, dependencyColor(layout.item()), true);
        graphics.text(font, Component.literal(trimToWidth(reasonLine(layout.item().backup()).getString(), maxDependencyWidth)), dependencyX, rowY + 26, 0xFF8FA8D8, true);
        drawSurfaceButton(graphics, restoreRect(layout, rowY), Component.translatable("screen.justenoughbackups.backups.restore"), mouseX, mouseY, true);
        drawSurfaceButton(graphics, renameRect(layout, rowY), Component.translatable("screen.justenoughbackups.backups.rename"), mouseX, mouseY, true);
        drawSurfaceButton(graphics, deleteRect(layout, rowY), Component.translatable("screen.justenoughbackups.backups.delete"), mouseX, mouseY, layout.item().backup().canDelete());

        if (expanded(layout.item())) {
            renderExpandedDetails(graphics, layout, headerBottom + 8, x, w - ROW_PADDING * 2);
        }
    }

    private void renderExpandedDetails(GuiGraphicsExtractor graphics, RowLayout layout, int startY, int x, int width) {
        BackupListItem item = layout.item();
        int lineY = startY;
        int textX = x + ROW_PADDING + EXPAND_SIZE + 8;
        int connectorX = x + ROW_PADDING + EXPAND_SIZE / 2;
        int detailRight = x + width - ROW_PADDING;

        if (item.base() != null) {
            drawConnector(graphics, connectorX, lineY + 4, 10, 0xFF555555);
            graphics.text(font, Component.literal(trimToWidth(baseLine(item.base()), detailRight - textX)), textX, lineY, 0xFFB7C7FF, true);
            lineY += DETAIL_LINE_HEIGHT;
        }

        if (!item.children().isEmpty()) {
            for (int i = 0; i < item.children().size() && i < MAX_INLINE_CHILDREN; i++) {
                BackupUiBackup child = item.children().get(i);
                drawConnector(graphics, connectorX, lineY + 4, 10, 0xFF555555);
                graphics.text(font, Component.literal(trimToWidth(childLine(child), detailRight - textX)), textX, lineY, 0xFF9FD5B6, true);
                lineY += DETAIL_LINE_HEIGHT;
            }
            if (item.children().size() > MAX_INLINE_CHILDREN) {
                drawConnector(graphics, connectorX, lineY + 4, 10, 0xFF555555);
                int hidden = item.children().size() - MAX_INLINE_CHILDREN;
                graphics.text(font, Component.translatable("screen.justenoughbackups.backups.more_children", hidden), textX, lineY, 0xFF9B9B9B, true);
                lineY += DETAIL_LINE_HEIGHT;
            }
        }

        if (!item.backup().canDelete() && !item.backup().deleteBlockedReason().isBlank()) {
            drawConnector(graphics, connectorX, lineY + 4, 10, 0xFF885555);
            graphics.text(font, Component.literal(trimToWidth(item.backup().deleteBlockedReason(), detailRight - textX)), textX, lineY, 0xFFFFC47A, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (handleToolbarClick(event.x(), event.y()) || handleExpandClick(event.x(), event.y()) || handleRowActionClick(event.x(), event.y())) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleToolbarClick(double mouseX, double mouseY) {
        if (createButtonRect().contains(mouseX, mouseY)) {
            minecraft.setScreen(new CreateBackupScreen(this));
            return true;
        }
        if (refreshButtonRect().contains(mouseX, mouseY)) {
            BackupUiClient.requestList();
            return true;
        }
        if (configButtonRect().contains(mouseX, mouseY)) {
            minecraft.setScreen(JEBConfigScreens.create(this));
            return true;
        }
        return false;
    }

    private boolean handleExpandClick(double mouseX, double mouseY) {
        for (RowLayout layout : rowLayouts) {
            int rowY = screenY(layout);
            if (rowY + layout.height() < listContentTop() || rowY > listBottom()) {
                continue;
            }
            if (layout.item().hasDetails() && expanderRect(layout, rowY).contains(mouseX, mouseY)) {
                expandedById.put(layout.item().backup().id(), !expanded(layout.item()));
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    private boolean handleRowActionClick(double mouseX, double mouseY) {
        for (RowLayout layout : rowLayouts) {
            int rowY = screenY(layout);
            if (rowY + layout.height() < listContentTop() || rowY > listBottom()) {
                continue;
            }
            BackupUiBackup backup = layout.item().backup();
            if (restoreRect(layout, rowY).contains(mouseX, mouseY)) {
                confirmRestore(backup);
                return true;
            }
            if (renameRect(layout, rowY).contains(mouseX, mouseY)) {
                minecraft.setScreen(new RenameBackupScreen(this, backup));
                return true;
            }
            if (backup.canDelete() && deleteRect(layout, rowY).contains(mouseX, mouseY)) {
                confirmDelete(backup);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.clamp(scroll - (int) (scrollY * 24), 0, maxScroll());
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
    public void setStatus(boolean ok, Component message) {
        statusOk = ok;
        status = message == null ? Component.translatable(ok ? "screen.justenoughbackups.backups.status.ready" : "screen.justenoughbackups.backups.status.failed") : message;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<BackupListItem> buildItems() {
        Map<String, BackupUiBackup> byId = new LinkedHashMap<>();
        Map<String, List<BackupUiBackup>> childrenByBase = new HashMap<>();

        for (BackupUiBackup backup : backups) {
            byId.put(backup.id(), backup);
        }
        for (BackupUiBackup backup : backups) {
            String baseId = value(backup.baseBackupId());
            if (!baseId.isBlank()) {
                childrenByBase.computeIfAbsent(baseId, ignored -> new ArrayList<>()).add(backup);
            }
        }
        for (List<BackupUiBackup> children : childrenByBase.values()) {
            children.sort(Comparator.comparing(BackupUiBackup::createdAt));
        }

        return backups.stream()
                .map(backup -> new BackupListItem(
                        backup,
                        byId.get(value(backup.baseBackupId())),
                        List.copyOf(childrenByBase.getOrDefault(backup.id(), List.of())),
                        rootId(backup, byId)
                ))
                .toList();
    }

    private List<BackupListItem> filteredItems() {
        if (filter.isBlank()) {
            return items;
        }
        String needle = filter.toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(item -> matches(item.backup(), needle))
                .toList();
    }

    private List<RowLayout> buildRowLayouts(List<BackupListItem> visibleItems) {
        List<RowLayout> layouts = new ArrayList<>();
        int y = 0;
        for (BackupListItem item : visibleItems) {
            int height = rowHeight(item);
            layouts.add(new RowLayout(item, y, height));
            y += height + ROW_GAP;
        }
        return layouts;
    }

    private int rowHeight(BackupListItem item) {
        int height = ROW_HEADER_HEIGHT;
        if (expanded(item)) {
            if (item.base() != null) {
                height += DETAIL_LINE_HEIGHT;
            }
            if (!item.children().isEmpty()) {
                height += Math.min(item.children().size(), MAX_INLINE_CHILDREN) * DETAIL_LINE_HEIGHT;
                if (item.children().size() > MAX_INLINE_CHILDREN) {
                    height += DETAIL_LINE_HEIGHT;
                }
            }
            if (!item.backup().canDelete() && !item.backup().deleteBlockedReason().isBlank()) {
                height += DETAIL_LINE_HEIGHT;
            }
            height += 12;
        }
        return height;
    }

    private boolean matches(BackupUiBackup backup, String needle) {
        return backup.displayName().toLowerCase(Locale.ROOT).contains(needle)
                || backup.type().name().toLowerCase(Locale.ROOT).contains(needle)
                || value(backup.reason()).toLowerCase(Locale.ROOT).contains(needle)
                || value(backup.baseBackupId()).toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean expanded(BackupListItem item) {
        return expandedById.getOrDefault(item.backup().id(), false);
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
            }, Component.translatable("screen.justenoughbackups.backups.restore_second_title"), Component.translatable("screen.justenoughbackups.backups.restore_second_message")));
        }, Component.translatable("screen.justenoughbackups.backups.restore_title", backup.displayName()), Component.translatable("screen.justenoughbackups.backups.restore_message")));
    }

    private void confirmDelete(BackupUiBackup backup) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                BackupUiClient.deleteBackup(backup.id());
            }
        }, Component.translatable("screen.justenoughbackups.backups.delete_title", backup.displayName()), Component.translatable("screen.justenoughbackups.backups.delete_message")));
    }

    private int listTop() {
        return viewportTop();
    }

    private int listContentTop() {
        return listTop() + LIST_HEADER_HEIGHT + 7;
    }

    private int listBottom() {
        return footerTop();
    }

    private int rowX() {
        return contentX() + 4;
    }

    private int rowWidth() {
        return Math.max(ACTION_WIDTH * 3 + ACTION_GAP * 2 + 220, contentWidth() - 8);
    }

    private int actionsX(int rowWidth) {
        return rowX() + rowWidth - ROW_PADDING - (ACTION_WIDTH * 3 + ACTION_GAP * 2);
    }

    private int maxScroll() {
        int contentHeight = rowLayouts.isEmpty() ? 0 : rowLayouts.getLast().y() + rowLayouts.getLast().height();
        int viewportHeight = Math.max(1, viewportBottom() - listContentTop());
        return Math.max(0, contentHeight - viewportHeight);
    }

    private int screenY(RowLayout layout) {
        return listContentTop() + layout.y() - scroll;
    }

    private Rect expanderRect(RowLayout layout, int rowY) {
        return new Rect(rowX() + ROW_PADDING, rowY + 9, EXPAND_SIZE, EXPAND_SIZE);
    }

    private Rect createButtonRect() {
        int x = searchBox.getX() + searchBox.getWidth() + 8;
        return new Rect(x, HEADER_Y, 76, TOOLBAR_HEIGHT);
    }

    private Rect refreshButtonRect() {
        Rect create = createButtonRect();
        return new Rect(create.x() + create.w() + 6, HEADER_Y, 76, TOOLBAR_HEIGHT);
    }

    private Rect configButtonRect() {
        return new Rect(viewportRight() - 60, HEADER_Y, 60, TOOLBAR_HEIGHT);
    }

    private int contentX() {
        return ScreenChrome.contentX();
    }

    private int contentWidth() {
        return ScreenChrome.contentWidth(width);
    }

    private int viewportTop() {
        return ScreenChrome.viewportTop();
    }

    private int viewportBottom() {
        return footerTop();
    }

    private int footerTop() {
        return ScreenChrome.footerTop(height);
    }

    private int viewportRight() {
        return ScreenChrome.viewportRight(width);
    }

    private Rect typeBadgeRect(int nameX, int y) {
        return new Rect(nameX, y, 88, 16);
    }

    private Rect restoreRect(RowLayout layout, int rowY) {
        int x = actionsX(rowWidth());
        return new Rect(x, rowY + 16, ACTION_WIDTH, 20);
    }

    private Rect renameRect(RowLayout layout, int rowY) {
        Rect restore = restoreRect(layout, rowY);
        return new Rect(restore.x() + ACTION_WIDTH + ACTION_GAP, restore.y(), ACTION_WIDTH, restore.h());
    }

    private Rect deleteRect(RowLayout layout, int rowY) {
        Rect rename = renameRect(layout, rowY);
        return new Rect(rename.x() + ACTION_WIDTH + ACTION_GAP, rename.y(), ACTION_WIDTH, rename.h());
    }

    private void drawSurfaceButton(GuiGraphicsExtractor graphics, Rect rect, Component text, int mouseX, int mouseY, boolean active) {
        ScreenChrome.drawSurfaceButton(graphics, font, new ScreenChrome.Rect(rect.x(), rect.y(), rect.w(), rect.h()), text, active, rect.contains(mouseX, mouseY));
    }

    private void drawExpander(GuiGraphicsExtractor graphics, Rect rect, boolean expanded) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), 0xFF202020);
        graphics.outline(rect.x(), rect.y(), rect.w(), rect.h(), 0xFF5A5A5A);
        graphics.centeredText(font, Component.literal(expanded ? "v" : ">"), rect.x() + rect.w() / 2, rect.y() + 4, 0xFFE0E0E0);
    }

    private void drawTypeBadge(GuiGraphicsExtractor graphics, Rect rect, BackupType type) {
        int fill = switch (type) {
            case FULL -> 0xFF365E3A;
            case PARTIAL -> 0xFF35506C;
            case DIFFERENTIAL -> 0xFF6B5230;
        };
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), fill);
        graphics.centeredText(font, Component.literal(type.toString()), rect.x() + rect.w() / 2, rect.y() + 4, TEXT_COLOR);
    }

    private void drawConnector(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + 1, y + DETAIL_LINE_HEIGHT - 4, color);
        graphics.fill(x, y + DETAIL_LINE_HEIGHT / 2, x + width, y + DETAIL_LINE_HEIGHT / 2 + 1, color);
    }

    private Component dependencySummary(BackupListItem item) {
        if (!item.backup().canDelete() && !item.backup().deleteBlockedReason().isBlank()) {
            return Component.translatable("screen.justenoughbackups.backups.dependency.blocked");
        }
        if (!item.children().isEmpty()) {
            return Component.translatable("screen.justenoughbackups.backups.dependency.dependents", item.children().size());
        }
        if (item.base() != null) {
            return Component.translatable("screen.justenoughbackups.backups.dependency.base", item.base().displayName());
        }
        return Component.translatable("screen.justenoughbackups.backups.dependency.root");
    }

    private int dependencyColor(BackupListItem item) {
        if (!item.backup().canDelete() && !item.backup().deleteBlockedReason().isBlank()) {
            return 0xFFFFC47A;
        }
        if (!item.children().isEmpty()) {
            return 0xFFA7D1A5;
        }
        if (item.base() != null) {
            return 0xFFB7C7FF;
        }
        return 0xFFB0B0B0;
    }

    private static String baseLine(BackupUiBackup backup) {
        return Component.translatable("screen.justenoughbackups.backups.detail.base", backup.displayName(), backup.type()).getString();
    }

    private static String childLine(BackupUiBackup backup) {
        return Component.translatable("screen.justenoughbackups.backups.detail.child",
                backup.displayName(),
                backup.type(),
                shortDate(backup.createdAt()),
                formatBytes(backup.includedBytes()))
                .getString();
    }

    private static String rootId(BackupUiBackup backup, Map<String, BackupUiBackup> byId) {
        BackupUiBackup current = backup;
        while (current != null && !value(current.baseBackupId()).isBlank()) {
            current = byId.get(current.baseBackupId());
        }
        return current == null ? backup.id() : current.id();
    }

    private static Component reasonLine(BackupUiBackup backup) {
        String reason = value(backup.reason()).isBlank()
                ? Component.translatable("screen.justenoughbackups.backups.reason.manual").getString()
                : value(backup.reason()).replace('_', ' ');
        String base = value(backup.baseBackupId()).isBlank()
                ? Component.translatable("screen.justenoughbackups.backups.base.none").getString()
                : backup.baseBackupId();
        return Component.translatable("screen.justenoughbackups.backups.reason_line", reason, base);
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

    private record BackupListItem(BackupUiBackup backup, BackupUiBackup base, List<BackupUiBackup> children, String rootId) {
        boolean hasDetails() {
            return base != null || !children.isEmpty() || (!backup.canDelete() && !value(backup.deleteBlockedReason()).isBlank());
        }
    }

    private record RowLayout(BackupListItem item, int y, int height) {
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    private static final class CreateBackupScreen extends Screen {
        private static final int PANEL_WIDTH = 300;
        private static final int PANEL_HEIGHT = 116;
        private final BackupManagementScreen parent;
        private BackupType selected = BackupConfig.get().backupMode;
        private EditBox nameBox;

        private CreateBackupScreen(BackupManagementScreen parent) {
            super(Component.translatable("screen.justenoughbackups.backups.create_title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int x = (width - PANEL_WIDTH) / 2;
            int y = height / 2 - 56;
            nameBox = new EditBox(font, x + 20, y + 48, PANEL_WIDTH - 40, 20, Component.translatable("screen.justenoughbackups.backups.name"));
            nameBox.setHint(Component.translatable("screen.justenoughbackups.backups.name_hint"));
            addRenderableWidget(nameBox);
            setInitialFocus(nameBox);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xAA000000);
            int x = (width - PANEL_WIDTH) / 2;
            int y = height / 2 - 56;
            graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xEE151515);
            graphics.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, ScreenChrome.OUTLINE_COLOR);
            graphics.centeredText(font, title, width / 2, y + 7, ScreenChrome.TITLE_COLOR);
            ScreenChrome.drawSurfaceButton(graphics, font, toChromeRect(typeRect()), Component.literal(selected.toString()), true, typeRect().contains(mouseX, mouseY));
            ScreenChrome.drawSurfaceButton(graphics, font, toChromeRect(confirmRect()), Component.translatable("screen.justenoughbackups.backups.create"), true, confirmRect().contains(mouseX, mouseY));
            ScreenChrome.drawSurfaceButton(graphics, font, toChromeRect(cancelRect()), Component.translatable("screen.justenoughbackups.common.cancel"), true, cancelRect().contains(mouseX, mouseY));
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (typeRect().contains(event.x(), event.y())) {
                    BackupType[] values = BackupType.values();
                    int next = (Arrays.asList(values).indexOf(selected) + 1) % values.length;
                    selected = values[next];
                    return true;
                }
                if (confirmRect().contains(event.x(), event.y())) {
                    minecraft.setScreen(parent);
                    BackupUiClient.createBackup(selected, nameBox.getValue());
                    return true;
                }
                if (cancelRect().contains(event.x(), event.y())) {
                    minecraft.setScreen(parent);
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        private Rect typeRect() {
            int x = (width - PANEL_WIDTH) / 2;
            int y = height / 2 - 56;
            return new Rect(x + 20, y + 18, PANEL_WIDTH - 40, 20);
        }

        private Rect confirmRect() {
            int x = (width - PANEL_WIDTH) / 2;
            int y = height / 2 - 56;
            return new Rect(x + 58, y + 80, 84, 20);
        }

        private Rect cancelRect() {
            int x = (width - PANEL_WIDTH) / 2;
            int y = height / 2 - 56;
            return new Rect(x + 158, y + 80, 84, 20);
        }
    }

    private static final class RenameBackupScreen extends Screen {
        private static final int PANEL_WIDTH = 300;
        private static final int PANEL_HEIGHT = 90;
        private final BackupManagementScreen parent;
        private final BackupUiBackup backup;
        private EditBox nameBox;

        private RenameBackupScreen(BackupManagementScreen parent, BackupUiBackup backup) {
            super(Component.translatable("screen.justenoughbackups.backups.rename_title"));
            this.parent = parent;
            this.backup = backup;
        }

        @Override
        protected void init() {
            int x = (width - PANEL_WIDTH) / 2;
            int y = height / 2 - 42;
            nameBox = new EditBox(font, x + 20, y + 24, PANEL_WIDTH - 40, 20, Component.translatable("screen.justenoughbackups.backups.name"));
            nameBox.setValue(backup.displayName());
            addRenderableWidget(nameBox);
            setInitialFocus(nameBox);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xAA000000);
            int x = (width - PANEL_WIDTH) / 2;
            int y = height / 2 - 42;
            graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xEE151515);
            graphics.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, ScreenChrome.OUTLINE_COLOR);
            graphics.centeredText(font, title, width / 2, y + 7, ScreenChrome.TITLE_COLOR);
            ScreenChrome.drawSurfaceButton(graphics, font, toChromeRect(saveRect()), Component.translatable("screen.justenoughbackups.common.save"), true, saveRect().contains(mouseX, mouseY));
            ScreenChrome.drawSurfaceButton(graphics, font, toChromeRect(cancelRect()), Component.translatable("screen.justenoughbackups.common.cancel"), true, cancelRect().contains(mouseX, mouseY));
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (saveRect().contains(event.x(), event.y())) {
                    minecraft.setScreen(parent);
                    BackupUiClient.renameBackup(backup.id(), nameBox.getValue());
                    return true;
                }
                if (cancelRect().contains(event.x(), event.y())) {
                    minecraft.setScreen(parent);
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        private Rect saveRect() {
            int x = (width - PANEL_WIDTH) / 2;
            int y = height / 2 - 42;
            return new Rect(x + 58, y + 56, 84, 20);
        }

        private Rect cancelRect() {
            int x = (width - PANEL_WIDTH) / 2;
            int y = height / 2 - 42;
            return new Rect(x + 158, y + 56, 84, 20);
        }
    }

    private static ScreenChrome.Rect toChromeRect(Rect rect) {
        return new ScreenChrome.Rect(rect.x(), rect.y(), rect.w(), rect.h());
    }
}
