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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    public static final int TEXT_COLOR = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFF8F8F8F;
    public static final int TEXT_DIMMED = 0xFF666666;
    public static final int STATUS_OK_COLOR = 0xFFB8E986;
    public static final int STATUS_NOT_OK_COLOR = 0xFFFF7777;

    private static final int PANEL_BG = 0xDD121212;
    private static final int PANEL_BORDER = 0xFF2D2D2D;
    private static final int CARD_BG = 0xCC181818;
    private static final int CARD_BORDER = 0xFF353535;
    private static final int CARD_HOVER_BG = 0xE6222222;
    private static final int CARD_SELECTED_BG = 0xCC666666;
    private static final int CARD_SELECTED_BORDER = 0xFF8F8F8F;


    private static final int ACCENT_NORMAL_BG = 0x74E171F4;
    private static final int ACCENT_HOVER_BG = 0x74E480F6;
    private static final int ACCENT_OUTLINE_COLOR = 0xFFD54DED;
    public static final int ACCENT_BASEBACKUP_TEXT = 0xFFAE6DBA;
    private static final int DELETE_NORMAL_BG = 0xCC6B2525;
    private static final int DELETE_HOVER_BG = 0xCC8B3333;
    private static final int DELETE_OUTLINE_COLOR = 0xFFB52A2A;

    private static final int FULL_BG = 0xFF24572D;
    private static final int FULL_TEXT = 0xFFB8E986;
    private static final int DIFF_BG = 0xFF5A4418;
    private static final int DIFF_TEXT = 0xFFFFD073;
    private static final int PARTIAL_BG = 0xFF1F4863;
    private static final int PARTIAL_TEXT = 0xFF90D0FF;

    private static final int CARD_HEIGHT = 44;
    private static final int CARD_GAP = 4;
    private static final int CHIP_HEIGHT = 16;
    private static final int TOOLBAR_BTN_HEIGHT = 20;
    public static final int BLOCKED_NORMAL_BG = 0x55552211;
    public static final int BLOCKED_OUTLINE_COLOR = 0xFFAA5533;
    public static final int BLOCKED_TEXT_COLOR = 0xFFFFB088;



    public enum BackupFilterType {
        ALL,
        FULL,
        DIFFERENTIAL,
        PARTIAL;

        Component label() {
            return switch (this) {
                case ALL -> Component.translatable("screen.justenoughbackups.backups.filter.all");
                case FULL -> Component.translatable("screen.justenoughbackups.backups.filter.full");
                case DIFFERENTIAL -> Component.translatable("screen.justenoughbackups.backups.filter.differential");
                case PARTIAL -> Component.translatable("screen.justenoughbackups.backups.filter.partial");
            };
        }
    }

    private enum ActiveModal {
        NONE,
        CREATE,
        RENAME,
        CONFIRM_RESTORE_STEP_1,
        CONFIRM_RESTORE_STEP_2,
        CONFIRM_DELETE
    }

    private final List<BackupUiBackup> backups = new ArrayList<>();
    private final Map<String, BackupListItem> itemsById = new LinkedHashMap<>();
    private List<BackupListItem> filteredItems = List.of();
    private String selectedBackupId = null;

    private EditBox searchBox;
    private String filter = "";
    private BackupFilterType filterType = BackupFilterType.ALL;

    private Component status = Component.translatable("screen.justenoughbackups.backups.status.loading");
    private boolean statusOk = true;

    private int listScroll = 0;
    private int detailScroll = 0;

    private ActiveModal activeModal = ActiveModal.NONE;
    private EditBox modalInputBox;
    private BackupType createSelectedType = BackupConfig.get().backupMode;
    private BackupUiBackup modalTargetBackup = null;

    public BackupManagementScreen() {
        super(Component.translatable("screen.justenoughbackups.backups.title"));
    }

    @Override
    protected void init() {
        BackupUiClient.setActiveScreen(this);
        rebuildWidgets();
        BackupUiClient.requestList();
    }

    @Override
    public void removed() {
        BackupUiClient.clearActiveScreen(this);
        super.removed();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        if (activeModal == ActiveModal.NONE) {
            buildMainWidgets();
        } else {
            buildModalWidgets();
        }
        refreshFilteredList();
    }

    private void buildMainWidgets() {
        int leftX = leftPanelX();
        int leftW = leftPanelWidth();
        int searchY = contentTop() + 24;

        searchBox = new EditBox(font, leftX, searchY, leftW, 18, Component.translatable("screen.justenoughbackups.backups.search"));
        searchBox.setHint(Component.translatable("screen.justenoughbackups.backups.search_hint"));
        searchBox.setValue(filter);
        searchBox.setResponder(value -> {
            filter = value == null ? "" : value;
            listScroll = 0;
            refreshFilteredList();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);
    }

    private void buildModalWidgets() {
        if (activeModal == ActiveModal.CREATE) {
            int panelW = 340;
            int panelH = 190;
            int x = (width - panelW) / 2;
            int y = (height - panelH) / 2;
            modalInputBox = new EditBox(font, x + 16, y + 124, panelW - 32, 20, Component.translatable("screen.justenoughbackups.backups.name"));
            modalInputBox.setHint(Component.translatable("screen.justenoughbackups.backups.name_hint"));
            addRenderableWidget(modalInputBox);
            setInitialFocus(modalInputBox);
        } else if (activeModal == ActiveModal.RENAME && modalTargetBackup != null) {
            int panelW = 300;
            int panelH = 92;
            int x = (width - panelW) / 2;
            int y = (height - panelH) / 2;
            modalInputBox = new EditBox(font, x + 16, y + 30, panelW - 32, 20, Component.translatable("screen.justenoughbackups.backups.name"));
            modalInputBox.setValue(modalTargetBackup.displayName());
            addRenderableWidget(modalInputBox);
            setInitialFocus(modalInputBox);
        }
    }

    private void refreshFilteredList() {
        rebuildItemsIndex();
        String needle = filter.trim().toLowerCase(Locale.ROOT);

        filteredItems = itemsById.values().stream()
                .filter(item -> {
                    if (filterType != BackupFilterType.ALL) {
                        if (filterType == BackupFilterType.FULL && item.backup().type() != BackupType.FULL) return false;
                        if (filterType == BackupFilterType.DIFFERENTIAL && item.backup().type() != BackupType.DIFFERENTIAL) return false;
                        if (filterType == BackupFilterType.PARTIAL && item.backup().type() != BackupType.PARTIAL) return false;
                    }
                    if (needle.isEmpty()) return true;
                    return matches(item.backup(), needle);
                })
                .toList();

        if (!filteredItems.isEmpty()) {
            boolean hasSelection = filteredItems.stream().anyMatch(item -> item.backup().id().equals(selectedBackupId));
            if (!hasSelection) {
                selectedBackupId = filteredItems.get(0).backup().id();
                detailScroll = 0;
            }
        } else {
            selectedBackupId = null;
            detailScroll = 0;
        }

        listScroll = Math.clamp(listScroll, 0, maxListScroll());
        detailScroll = Math.clamp(detailScroll, 0, maxDetailScroll());
    }

    private void rebuildItemsIndex() {
        itemsById.clear();
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

        for (BackupUiBackup backup : backups) {
            BackupUiBackup base = byId.get(value(backup.baseBackupId()));
            List<BackupUiBackup> children = List.copyOf(childrenByBase.getOrDefault(backup.id(), List.of()));
            itemsById.put(backup.id(), new BackupListItem(backup, base, children));
        }
    }

    @Override
    public void handleResponse(BackupUiResponsePayload payload) {
        setStatus(payload.success(), payload.message());
        if (payload.backups() != null && payload.success()) {
            backups.clear();
            backups.addAll(payload.backups());
            backups.sort(Comparator.comparing((BackupUiBackup backup) -> value(backup.createdAt())).reversed());
        }
        refreshFilteredList();
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, ScreenChrome.BG_COLOR);

        renderTopHeader(graphics, mouseX, mouseY);
        renderBottomFooter(graphics);

        renderLeftPanel(graphics, mouseX, mouseY);

        renderRightPanel(graphics, mouseX, mouseY);

        if (activeModal != ActiveModal.NONE) {
            renderModal(graphics, mouseX, mouseY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTopHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int headerY = 10;
        graphics.text(font, title, ScreenChrome.OUTER, headerY + 4, TEXT_COLOR, true);

        String worldDesc = backups.isEmpty() ? "" : "(" + backups.get(0).worldName() + ")";
        if (!worldDesc.isEmpty()) {
            int titleW = font.width(title);
            graphics.text(font, Component.literal(worldDesc), ScreenChrome.OUTER + titleW + 8, headerY + 4, TEXT_MUTED, true);
        }

        drawSurfaceButton(graphics, refreshBtnRect(), Component.translatable("screen.justenoughbackups.backups.refresh"), mouseX, mouseY, true);
        drawSurfaceButton(graphics, configBtnRect(), Component.translatable("screen.justenoughbackups.backups.config"), mouseX, mouseY, true);

        graphics.horizontalLine(ScreenChrome.OUTER, width - ScreenChrome.OUTER, contentTop() - 4, PANEL_BORDER);
    }

    private void renderBottomFooter(GuiGraphicsExtractor graphics) {
        int footerY = height - 18;
        int statusDotColor = statusOk ? STATUS_OK_COLOR : STATUS_NOT_OK_COLOR;

        graphics.fill(ScreenChrome.OUTER, footerY + 2, ScreenChrome.OUTER + 5, footerY + 7, statusDotColor);
        graphics.text(font, status, ScreenChrome.OUTER + 10, footerY, statusDotColor, true);

        long totalBytes = backups.stream().mapToLong(BackupUiBackup::includedBytes).sum();
        String summary = filteredItems.size() == backups.size()
                ? filteredItems.size() + " backups (" + formatBytes(totalBytes) + ")"
                : filteredItems.size() + "/" + backups.size() + " backups";

        graphics.text(font, summary, width - ScreenChrome.OUTER - font.width(summary), footerY, TEXT_MUTED, true);
    }

    private void renderLeftPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int leftX = leftPanelX();
        int leftW = leftPanelWidth();
        int top = contentTop();
        int bottom = contentBottom();

        Rect createBtn = createBtnRect();
        drawAccentButton(graphics, createBtn, Component.translatable("screen.justenoughbackups.backups.create_new"), mouseX, mouseY, true, ACCENT_NORMAL_BG, ACCENT_HOVER_BG, ACCENT_OUTLINE_COLOR, ACCENT_OUTLINE_COLOR);

        renderFilterChips(graphics, mouseX, mouseY);

        int listTop = listViewportTop();
        int listH = bottom - listTop;
        graphics.fill(leftX, listTop, leftX + leftW, bottom, PANEL_BG);
        graphics.outline(leftX, listTop, leftW, listH, PANEL_BORDER);

        graphics.enableScissor(leftX + 1, listTop + 1, leftX + leftW - 1, bottom - 1);
        try {
            if (filteredItems.isEmpty()) {
                Component emptyMsg = Component.translatable(backups.isEmpty()
                        ? "screen.justenoughbackups.backups.empty"
                        : "screen.justenoughbackups.backups.no_matches");
                graphics.text(font, emptyMsg, leftX + 10, listTop + 16, TEXT_MUTED, true);
            } else {
                for (int i = 0; i < filteredItems.size(); i++) {
                    BackupListItem item = filteredItems.get(i);
                    int cardY = listTop + 4 + i * (CARD_HEIGHT + CARD_GAP) - listScroll;

                    if (cardY + CARD_HEIGHT < listTop || cardY > bottom) {
                        continue;
                    }

                    renderBackupCard(graphics, item, leftX + 4, cardY, leftW - 8, mouseX, mouseY);
                }
            }
        } finally {
            graphics.disableScissor();
        }

        renderScrollbar(graphics, leftX + leftW - 5, listTop + 2, 3, listH - 4, listScroll, maxListScroll(), filteredItems.size() * (CARD_HEIGHT + CARD_GAP), listH);
    }

    private void renderFilterChips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        BackupFilterType[] types = BackupFilterType.values();
        for (int i = 0; i < types.length; i++) {
            Rect rect = filterChipRect(i);
            boolean active = filterType == types[i];
            boolean hovered = rect.contains(mouseX, mouseY);

            int bg = hovered ? (active ? ACCENT_HOVER_BG : 0xFF2A2A2A) : (active ? ACCENT_NORMAL_BG : 0xFF1C1C1C);
            int border = active ? ACCENT_OUTLINE_COLOR : (hovered ? 0xFF4F4F4F : 0xFF333333);
            int textColor = active ? ACCENT_OUTLINE_COLOR : (hovered ? 0xFFD0D0D0 : 0xFF888888);

            graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), bg);
            graphics.outline(rect.x(), rect.y(), rect.w(), rect.h(), border);
            graphics.centeredText(font, types[i].label(), rect.x() + rect.w() / 2, rect.y() + (rect.h() - font.lineHeight) / 2 + 1, textColor);
        }
    }

    private void renderBackupCard(GuiGraphicsExtractor graphics, BackupListItem item, int x, int y, int w, int mouseX, int mouseY) {
        BackupUiBackup backup = item.backup();
        boolean selected = backup.id().equals(selectedBackupId);
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + CARD_HEIGHT;

        int bg = selected ? CARD_SELECTED_BG : (hovered ? CARD_HOVER_BG : CARD_BG);
        int border = selected ? CARD_SELECTED_BORDER : (hovered ? 0xFF505050 : CARD_BORDER);

        graphics.fill(x, y, x + w, y + CARD_HEIGHT, bg);
        graphics.outline(x, y, w, CARD_HEIGHT, border);

        int nameW = w - 80;
        graphics.text(font, Component.literal(trimToWidth(backup.displayName(), nameW)), x + 8, y + 6, TEXT_COLOR, true);

        int badgeW = 76;
        drawTypeBadge(graphics, x + w - badgeW - 8, y + 5, badgeW, 14, backup.type());

        String metaText = shortDate(backup.createdAt()) + " • " + formatBytes(backup.includedBytes());
        graphics.text(font, Component.literal(trimToWidth(metaText, w - 28)), x + 8, y + 24, TEXT_MUTED, true);

        if (!backup.canDelete()) {
            graphics.text(font, Component.literal("🔒"), x + w - 18, y + 23, BLOCKED_TEXT_COLOR, true);
        } else if (!item.children().isEmpty()) {
            graphics.text(font, Component.literal("🔗"), x + w - 18, y + 23, 0xFFA7D1A5, true);
        }
    }

    private void renderRightPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int rightX = rightPanelX();
        int rightW = rightPanelWidth();
        int top = contentTop();
        int bottom = contentBottom();
        int panelH = bottom - top;

        graphics.fill(rightX, top, rightX + rightW, bottom, PANEL_BG);
        graphics.outline(rightX, top, rightW, panelH, PANEL_BORDER);

        BackupListItem selectedItem = selectedBackupId != null ? itemsById.get(selectedBackupId) : null;

        if (selectedItem == null) {
            renderEmptyDetailState(graphics, rightX, top, rightW, panelH, mouseX, mouseY);
            return;
        }

        int actionFooterH = 34;
        int detailContentH = panelH - actionFooterH;

        graphics.enableScissor(rightX + 1, top + 1, rightX + rightW - 1, top + detailContentH - 1);
        try {
            renderDetailContent(graphics, selectedItem, rightX + 12, top + 10 - detailScroll, rightW - 24, mouseX, mouseY);
        } finally {
            graphics.disableScissor();
        }

        renderActionFooter(graphics, selectedItem.backup(), rightX, bottom - actionFooterH, rightW, actionFooterH, mouseX, mouseY);

        int totalDetailHeight = calculateDetailHeight(selectedItem);
        renderScrollbar(graphics, rightX + rightW - 5, top + 2, 3, detailContentH - 4, detailScroll, maxDetailScroll(), totalDetailHeight, detailContentH);
    }

    private void renderEmptyDetailState(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        int centerY = y + h / 2 - 30;
        if (backups.isEmpty()) {
            graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.empty_title"), x + w / 2, centerY, TEXT_COLOR);
            graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.empty_desc"), x + w / 2, centerY + 16, TEXT_MUTED);

            Rect cta = new Rect(x + w / 2 - 80, centerY + 40, 160, 24);
            drawAccentButton(graphics, cta, Component.translatable("screen.justenoughbackups.backups.empty_action"), mouseX, mouseY, true, ACCENT_NORMAL_BG, ACCENT_HOVER_BG, ACCENT_OUTLINE_COLOR, ACCENT_OUTLINE_COLOR);
        } else {
            graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.no_selection"), x + w / 2, centerY + 10, TEXT_MUTED);
            if (!filter.isEmpty() || filterType != BackupFilterType.ALL) {
                Rect clearFilter = new Rect(x + w / 2 - 60, centerY + 34, 120, 20);
                drawSurfaceButton(graphics, clearFilter, Component.translatable("screen.justenoughbackups.backups.clear_filter"), mouseX, mouseY, true);
            }
        }
    }

    private void renderDetailContent(GuiGraphicsExtractor graphics, BackupListItem item, int x, int y, int w, int mouseX, int mouseY) {
        BackupUiBackup backup = item.backup();
        int curY = y;

        int badgeW = 76;
        int renameBtnW = 60;
        int titleMaxW = w - badgeW - renameBtnW - 16;
        graphics.text(font, Component.literal(trimToWidth(backup.displayName(), titleMaxW)), x, curY + 2, TEXT_COLOR, true);

        drawTypeBadge(graphics, x + w - badgeW, curY, badgeW, 16, backup.type());

        curY += 24;

        int metricGap = 6;
        int metricW = (w - metricGap) / 2;
        int metricH = 34;

        renderMetricBox(graphics, x, curY, metricW, metricH,
                Component.translatable("screen.justenoughbackups.backups.metric.size"),
                Component.literal(formatBytes(backup.includedBytes())));

        renderMetricBox(graphics, x + metricW + metricGap, curY, metricW, metricH,
                Component.translatable("screen.justenoughbackups.backups.metric.files"),
                Component.literal(String.format(Locale.ROOT, "%,d files", backup.includedFiles())));

        curY += metricH + metricGap;

        renderMetricBox(graphics, x, curY, metricW, metricH,
                Component.translatable("screen.justenoughbackups.backups.metric.date"),
                Component.literal(shortDate(backup.createdAt())));

        String reasonStr = value(backup.reason()).isBlank() ? "Manual" : value(backup.reason()).replace('_', ' ');
        renderMetricBox(graphics, x + metricW + metricGap, curY, metricW, metricH,
                Component.translatable("screen.justenoughbackups.backups.metric.reason"),
                Component.literal(reasonStr));

        curY += metricH + 12;

        if (!backup.canDelete() && !value(backup.deleteBlockedReason()).isBlank()) {
            int bannerH = 26;
            graphics.fill(x, curY, x + w, curY + bannerH, BLOCKED_NORMAL_BG);
            graphics.outline(x, curY, w, bannerH, BLOCKED_OUTLINE_COLOR);
            graphics.text(font, Component.literal("⚠ " + backup.deleteBlockedReason()), x + 8, curY + 8, BLOCKED_TEXT_COLOR, true);
            curY += bannerH + 12;
        }

        graphics.horizontalLine(x, x + w, curY, PANEL_BORDER);
        curY += 8;
        graphics.text(font, Component.translatable("screen.justenoughbackups.backups.lineage.title"), x, curY, TEXT_MUTED, true);
        curY += 16;

        if (item.base() != null) {
            graphics.text(font, Component.translatable("screen.justenoughbackups.backups.lineage.base"), x, curY, ACCENT_BASEBACKUP_TEXT, true);
            curY += 12;

            int baseCardH = 24;
            Rect baseRect = new Rect(x, curY, w, baseCardH);
            boolean hovered = baseRect.contains(mouseX, mouseY);
            graphics.fill(x, curY, x + w, curY + baseCardH, hovered ? CARD_HOVER_BG : CARD_BG);
            graphics.outline(x, curY, w, baseCardH, hovered ? ACCENT_OUTLINE_COLOR : CARD_BORDER);

            String baseTitle = item.base().displayName() + " [" + item.base().type() + "] • " + shortDate(item.base().createdAt());
            graphics.text(font, Component.literal(trimToWidth(baseTitle, w - 60)), x + 8, curY + 7, TEXT_COLOR, true);

            drawSurfaceButton(graphics, jumpToBaseRect(x + w - 46, curY + 3), Component.translatable("screen.justenoughbackups.backups.lineage.jump"), mouseX, mouseY, true);
            curY += baseCardH + 4;
        }

        if (!item.children().isEmpty()) {
            graphics.text(font, Component.translatable("screen.justenoughbackups.backups.lineage.dependents", item.children().size()), x, curY, 0xFF8FC0A0, true);
            curY += 12;

            for (int i = 0; i < item.children().size(); i++) {
                BackupUiBackup child = item.children().get(i);
                int childCardH = 24;
                Rect childRect = childItemRect(x, curY, w);
                boolean hovered = childRect.contains(mouseX, mouseY);

                graphics.fill(x, curY, x + w, curY + childCardH, hovered ? CARD_HOVER_BG : CARD_BG);
                graphics.outline(x, curY, w, childCardH, hovered ? 0xFF4E9A68 : CARD_BORDER);

                String childSummary = child.displayName() + " [" + child.type() + "] • " + shortDate(child.createdAt()) + " (" + formatBytes(child.includedBytes()) + ")";
                graphics.text(font, Component.literal(trimToWidth(childSummary, w - 60)), x + 8, curY + 7, TEXT_COLOR, true);

                drawSurfaceButton(graphics, jumpToChildRect(x + w - 46, curY + 3), Component.translatable("screen.justenoughbackups.backups.lineage.jump"), mouseX, mouseY, true);
                curY += childCardH + 4;
            }
        } else if (item.base() == null) {
            graphics.text(font, Component.translatable("screen.justenoughbackups.backups.lineage.none"), x + 8, curY, TEXT_DIMMED, true);
        }
    }

    private void renderMetricBox(GuiGraphicsExtractor graphics, int x, int y, int w, int h, Component title, Component value) {
        graphics.fill(x, y, x + w, y + h, 0x99191919);
        graphics.outline(x, y, w, h, 0xFF303030);
        graphics.text(font, title, x + 8, y + 4, TEXT_MUTED, true);
        graphics.text(font, value, x + 8, y + 17, TEXT_COLOR, true);
    }

    private void renderActionFooter(GuiGraphicsExtractor graphics, BackupUiBackup backup, int x, int y, int w, int h, int mouseX, int mouseY) {
        graphics.fill(x + 1, y, x + w - 1, y + h - 1, 0xEE161616);
        graphics.horizontalLine(x + 1, x + w - 1, y, PANEL_BORDER);

        int btnY = y + (h - 22) / 2;

        Rect restoreRect = new Rect(x + 12, btnY, 90, 22);
        drawSurfaceButton(graphics, restoreRect, Component.translatable("screen.justenoughbackups.backups.restore"), mouseX, mouseY, backup.restorable());

        Rect renameRect = new Rect(restoreRect.x() + restoreRect.w() + 8, btnY, 80, 22);
        drawSurfaceButton(graphics, renameRect, Component.translatable("screen.justenoughbackups.backups.rename"), mouseX, mouseY, true);

        Rect deleteRect = new Rect(x + w - 12 - 80, btnY, 80, 22);
        drawAccentButton(graphics, deleteRect, Component.translatable("screen.justenoughbackups.backups.delete"), mouseX, mouseY, backup.canDelete(), DELETE_NORMAL_BG, DELETE_HOVER_BG, DELETE_OUTLINE_COLOR, DELETE_OUTLINE_COLOR);
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int currentScroll, int maxScroll, int contentHeight, int viewportHeight) {
        if (maxScroll <= 0 || contentHeight <= 0) return;
        graphics.fill(x, y, x + w, y + h, 0x55111111);

        int thumbH = Math.max(16, (int) ((float) viewportHeight / contentHeight * h));
        int thumbY = y + (int) ((float) currentScroll / maxScroll * (h - thumbH));
        graphics.fill(x, thumbY, x + w, thumbY + thumbH, 0xFF555555);
    }

    private void renderModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0xBB000000);

        switch (activeModal) {
            case CREATE -> renderCreateModal(graphics, mouseX, mouseY);
            case RENAME -> renderRenameModal(graphics, mouseX, mouseY);
            case CONFIRM_RESTORE_STEP_1 -> renderRestoreStep1Modal(graphics, mouseX, mouseY);
            case CONFIRM_RESTORE_STEP_2 -> renderRestoreStep2Modal(graphics, mouseX, mouseY);
            case CONFIRM_DELETE -> renderDeleteModal(graphics, mouseX, mouseY);
            default -> {}
        }
    }

    private void renderCreateModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int panelW = 340;
        int panelH = 190;
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;

        graphics.fill(x, y, x + panelW, y + panelH, 0xEE161616);
        graphics.outline(x, y, panelW, panelH, ScreenChrome.OUTLINE_COLOR);
        graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.create_title"), width / 2, y + 8, ScreenChrome.TITLE_COLOR);

        BackupType[] types = {BackupType.FULL, BackupType.DIFFERENTIAL, BackupType.PARTIAL};
        int cardH = 24;
        int cardY = y + 26;

        for (BackupType type : types) {
            Rect rect = createModalTypeRect(x, cardY, panelW - 32, cardH);
            boolean selected = createSelectedType == type;
            boolean hovered = rect.contains(mouseX, mouseY);

            graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), selected ? CARD_SELECTED_BG : (hovered ? 0xFF2A2A2A : 0xFF1D1D1D));
            graphics.outline(rect.x(), rect.y(), rect.w(), rect.h(), selected ? CARD_SELECTED_BORDER : (hovered ? 0xFF505050 : 0xFF353535));

            Component desc = switch (type) {
                case FULL -> Component.translatable("screen.justenoughbackups.backups.type.full_desc");
                case DIFFERENTIAL -> Component.translatable("screen.justenoughbackups.backups.type.differential_desc");
                case PARTIAL -> Component.translatable("screen.justenoughbackups.backups.type.partial_desc");
            };
            graphics.text(font, Component.literal(trimToWidth(type.name() + ": " + desc.getString(), rect.w() - 20)), rect.x() + 10, rect.y() + 8, selected ? ScreenChrome.TITLE_COLOR : TEXT_MUTED, false);

            cardY += cardH + 4;
        }

        Rect confirmRect = new Rect(x + panelW / 2 - 90, y + panelH - 32, 85, 20);
        Rect cancelRect = new Rect(x + panelW / 2 + 5, y + panelH - 32, 85, 20);
        drawAccentButton(graphics, confirmRect, Component.translatable("screen.justenoughbackups.backups.create"), mouseX, mouseY, true, ACCENT_NORMAL_BG, ACCENT_HOVER_BG, ACCENT_OUTLINE_COLOR, ACCENT_OUTLINE_COLOR);
        drawSurfaceButton(graphics, cancelRect, Component.translatable("screen.justenoughbackups.common.cancel"), mouseX, mouseY, true);
    }

    private void renderRenameModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int panelW = 300;
        int panelH = 92;
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;

        graphics.fill(x, y, x + panelW, y + panelH, 0xEE161616);
        graphics.outline(x, y, panelW, panelH, ScreenChrome.OUTLINE_COLOR);
        graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.rename_title"), width / 2, y + 8, ScreenChrome.TITLE_COLOR);

        Rect saveRect = new Rect(x + 58, y + 60, 84, 20);
        Rect cancelRect = new Rect(x + 158, y + 60, 84, 20);
        drawAccentButton(graphics, saveRect, Component.translatable("screen.justenoughbackups.common.save"), mouseX, mouseY, true, ACCENT_NORMAL_BG, ACCENT_HOVER_BG, ACCENT_OUTLINE_COLOR, ACCENT_OUTLINE_COLOR);
        drawSurfaceButton(graphics, cancelRect, Component.translatable("screen.justenoughbackups.common.cancel"), mouseX, mouseY, true);
    }

    private void renderRestoreStep1Modal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int panelW = 330;
        int panelH = 110;
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;

        graphics.fill(x, y, x + panelW, y + panelH, 0xEE161616);
        graphics.outline(x, y, panelW, panelH, ScreenChrome.OUTLINE_COLOR);

        String titleStr = modalTargetBackup != null ? modalTargetBackup.displayName() : "Backup";
        graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.restore_title", titleStr), width / 2, y + 10, ScreenChrome.TITLE_COLOR);
        graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.restore_message"), width / 2, y + 36, TEXT_MUTED);

        Rect nextRect = new Rect(x + 65, y + 74, 95, 22);
        Rect cancelRect = new Rect(x + 170, y + 74, 95, 22);
        drawAccentButton(graphics, nextRect, Component.literal("Proceed"), mouseX, mouseY, true, ACCENT_NORMAL_BG, ACCENT_HOVER_BG, ACCENT_OUTLINE_COLOR, ACCENT_OUTLINE_COLOR);
        drawSurfaceButton(graphics, cancelRect, Component.translatable("screen.justenoughbackups.common.cancel"), mouseX, mouseY, true);
    }

    private void renderRestoreStep2Modal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int panelW = 330;
        int panelH = 110;
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;

        graphics.fill(x, y, x + panelW, y + panelH, 0xEE161616);
        graphics.outline(x, y, panelW, panelH, DELETE_OUTLINE_COLOR);

        graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.restore_second_title"), width / 2, y + 10, DELETE_OUTLINE_COLOR);
        graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.restore_second_message"), width / 2, y + 36, TEXT_MUTED);

        Rect confirmRect = new Rect(x + 55, y + 74, 110, 22);
        Rect cancelRect = new Rect(x + 175, y + 74, 100, 22);
        drawAccentButton(graphics, confirmRect, Component.translatable("screen.justenoughbackups.backups.restore"), mouseX, mouseY, true, DELETE_NORMAL_BG, DELETE_HOVER_BG, DELETE_OUTLINE_COLOR, DELETE_OUTLINE_COLOR);
        drawSurfaceButton(graphics, cancelRect, Component.translatable("screen.justenoughbackups.common.cancel"), mouseX, mouseY, true);
    }

    private void renderDeleteModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int panelW = 320;
        int panelH = 106;
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;

        graphics.fill(x, y, x + panelW, y + panelH, 0xEE161616);
        graphics.outline(x, y, panelW, panelH, DELETE_OUTLINE_COLOR);

        String titleStr = modalTargetBackup != null ? modalTargetBackup.displayName() : "Backup";
        graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.delete_title", titleStr), width / 2, y + 10, DELETE_OUTLINE_COLOR);
        graphics.centeredText(font, Component.translatable("screen.justenoughbackups.backups.delete_message"), width / 2, y + 34, TEXT_MUTED);

        Rect deleteRect = new Rect(x + 50, y + 70, 105, 22);
        Rect cancelRect = new Rect(x + 165, y + 70, 105, 22);
        drawAccentButton(graphics, deleteRect, Component.translatable("screen.justenoughbackups.backups.delete"), mouseX, mouseY, true, DELETE_NORMAL_BG, DELETE_HOVER_BG, DELETE_OUTLINE_COLOR, DELETE_OUTLINE_COLOR);
        drawSurfaceButton(graphics, cancelRect, Component.translatable("screen.justenoughbackups.common.cancel"), mouseX, mouseY, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double mx = event.x();
            double my = event.y();

            if (activeModal != ActiveModal.NONE) {
                if (handleModalClick(mx, my)) return true;
                return true;
            }

            if (handleTopHeaderClick(mx, my)) return true;
            if (handleLeftPanelClick(mx, my)) return true;
            if (handleRightPanelClick(mx, my)) return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleTopHeaderClick(double mx, double my) {
        if (refreshBtnRect().contains(mx, my)) {
            BackupUiClient.requestList();
            return true;
        }
        if (configBtnRect().contains(mx, my)) {
            minecraft.setScreenAndShow(JEBConfigScreens.create(this));
            return true;
        }
        return false;
    }

    private boolean handleLeftPanelClick(double mx, double my) {
        if (createBtnRect().contains(mx, my)) {
            openCreateModal();
            return true;
        }

        for (int i = 0; i < BackupFilterType.values().length; i++) {
            if (filterChipRect(i).contains(mx, my)) {
                filterType = BackupFilterType.values()[i];
                listScroll = 0;
                refreshFilteredList();
                return true;
            }
        }

        int leftX = leftPanelX();
        int leftW = leftPanelWidth();
        int listTop = listViewportTop();
        int bottom = contentBottom();

        if (mx >= leftX && mx <= leftX + leftW && my >= listTop && my <= bottom) {
            for (int i = 0; i < filteredItems.size(); i++) {
                int cardY = listTop + 4 + i * (CARD_HEIGHT + CARD_GAP) - listScroll;
                Rect cardRect = new Rect(leftX + 4, cardY, leftW - 8, CARD_HEIGHT);
                if (cardRect.contains(mx, my)) {
                    selectedBackupId = filteredItems.get(i).backup().id();
                    detailScroll = 0;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleRightPanelClick(double mx, double my) {
        int rightX = rightPanelX();
        int rightW = rightPanelWidth();
        int bottom = contentBottom();
        int top = contentTop();

        BackupListItem selectedItem = selectedBackupId != null ? itemsById.get(selectedBackupId) : null;
        if (selectedItem == null) {
            if (backups.isEmpty()) {
                Rect cta = new Rect(rightX + rightW / 2 - 80, top + (bottom - top) / 2 + 10, 160, 24);
                if (cta.contains(mx, my)) {
                    openCreateModal();
                    return true;
                }
            } else if (!filter.isEmpty() || filterType != BackupFilterType.ALL) {
                Rect clearFilter = new Rect(rightX + rightW / 2 - 60, top + (bottom - top) / 2 + 4, 120, 20);
                if (clearFilter.contains(mx, my)) {
                    filter = "";
                    if (searchBox != null) searchBox.setValue("");
                    filterType = BackupFilterType.ALL;
                    refreshFilteredList();
                    return true;
                }
            }
            return false;
        }

        BackupUiBackup backup = selectedItem.backup();
        int actionFooterH = 34;
        int actionY = bottom - actionFooterH;

        int renameBtnW = 60;
        Rect inlineRename = renameBtnInlineRect(rightX + rightW - 12 - renameBtnW, top + 10 - detailScroll);
        if (inlineRename.contains(mx, my)) {
            openRenameModal(backup);
            return true;
        }

        if (selectedItem.base() != null) {
            int baseY = top + 10 - detailScroll + 24 + 34 * 2 + 6 + 12 + 8 + 16;
            if (!backup.canDelete() && !value(backup.deleteBlockedReason()).isBlank()) {
                baseY += 26 + 12;
            }
            Rect jumpBase = jumpToBaseRect(rightX + rightW - 12 - 50, baseY + 4);
            if (jumpBase.contains(mx, my)) {
                selectBackupAndScrollToIt(selectedItem.base().id());
                return true;
            }
        }

        if (!selectedItem.children().isEmpty()) {
            int childrenStartY = top + 10 - detailScroll + 24 + 34 * 2 + 6 + 12 + 8 + 16;
            if (!backup.canDelete() && !value(backup.deleteBlockedReason()).isBlank()) {
                childrenStartY += 26 + 12;
            }
            if (selectedItem.base() != null) {
                childrenStartY += 28 + 6;
            }
            childrenStartY += 12;

            for (int i = 0; i < selectedItem.children().size(); i++) {
                int childY = childrenStartY + i * 28;
                Rect jumpChild = jumpToChildRect(rightX + rightW - 12 - 46, childY + 3);
                if (jumpChild.contains(mx, my)) {
                    selectBackupAndScrollToIt(selectedItem.children().get(i).id());
                    return true;
                }
            }
        }

        int btnY = actionY + (actionFooterH - 22) / 2;
        Rect restoreRect = new Rect(rightX + 12, btnY, 90, 22);
        if (restoreRect.contains(mx, my) && backup.restorable()) {
            modalTargetBackup = backup;
            activeModal = ActiveModal.CONFIRM_RESTORE_STEP_1;
            return true;
        }

        Rect renameRect = new Rect(restoreRect.x() + restoreRect.w() + 8, btnY, 80, 22);
        if (renameRect.contains(mx, my)) {
            openRenameModal(backup);
            return true;
        }

        Rect deleteRect = new Rect(rightX + rightW - 12 - 80, btnY, 80, 22);
        if (deleteRect.contains(mx, my) && backup.canDelete()) {
            modalTargetBackup = backup;
            activeModal = ActiveModal.CONFIRM_DELETE;
            return true;
        }

        return false;
    }

    private boolean handleModalClick(double mx, double my) {
        switch (activeModal) {
            case CREATE -> {
                int panelW = 340;
                int panelH = 190;
                int x = (width - panelW) / 2;
                int y = (height - panelH) / 2;

                BackupType[] types = {BackupType.FULL, BackupType.DIFFERENTIAL, BackupType.PARTIAL};
                int cardH = 24;
                int cardY = y + 26;
                for (BackupType type : types) {
                    Rect rect = createModalTypeRect(x, cardY, panelW - 32, cardH);
                    if (rect.contains(mx, my)) {
                        createSelectedType = type;
                        return true;
                    }
                    cardY += cardH + 4;
                }

                Rect confirmRect = new Rect(x + panelW / 2 - 90, y + panelH - 32, 85, 20);
                Rect cancelRect = new Rect(x + panelW / 2 + 5, y + panelH - 32, 85, 20);
                if (confirmRect.contains(mx, my)) {
                    String reqName = modalInputBox != null ? modalInputBox.getValue() : "";
                    closeModal();
                    BackupUiClient.createBackup(createSelectedType, reqName);
                    return true;
                }
                if (cancelRect.contains(mx, my)) {
                    closeModal();
                    return true;
                }
            }
            case RENAME -> {
                int panelW = 300;
                int panelH = 92;
                int x = (width - panelW) / 2;
                int y = (height - panelH) / 2;

                Rect saveRect = new Rect(x + 58, y + 60, 84, 20);
                Rect cancelRect = new Rect(x + 158, y + 60, 84, 20);
                if (saveRect.contains(mx, my) && modalTargetBackup != null) {
                    String newName = modalInputBox != null ? modalInputBox.getValue() : "";
                    String backupId = modalTargetBackup.id();
                    closeModal();
                    BackupUiClient.renameBackup(backupId, newName);
                    return true;
                }
                if (cancelRect.contains(mx, my)) {
                    closeModal();
                    return true;
                }
            }
            case CONFIRM_RESTORE_STEP_1 -> {
                int panelW = 330;
                int panelH = 110;
                int x = (width - panelW) / 2;
                int y = (height - panelH) / 2;

                Rect nextRect = new Rect(x + 65, y + 74, 95, 22);
                Rect cancelRect = new Rect(x + 170, y + 74, 95, 22);
                if (nextRect.contains(mx, my)) {
                    activeModal = ActiveModal.CONFIRM_RESTORE_STEP_2;
                    return true;
                }
                if (cancelRect.contains(mx, my)) {
                    closeModal();
                    return true;
                }
            }
            case CONFIRM_RESTORE_STEP_2 -> {
                int panelW = 330;
                int panelH = 110;
                int x = (width - panelW) / 2;
                int y = (height - panelH) / 2;

                Rect confirmRect = new Rect(x + 55, y + 74, 110, 22);
                Rect cancelRect = new Rect(x + 175, y + 74, 100, 22);
                if (confirmRect.contains(mx, my) && modalTargetBackup != null) {
                    String id = modalTargetBackup.id();
                    closeModal();
                    BackupUiClient.restoreBackup(id);
                    minecraft.setScreenAndShow(null);
                    return true;
                }
                if (cancelRect.contains(mx, my)) {
                    closeModal();
                    return true;
                }
            }
            case CONFIRM_DELETE -> {
                int panelW = 320;
                int panelH = 106;
                int x = (width - panelW) / 2;
                int y = (height - panelH) / 2;

                Rect deleteRect = new Rect(x + 50, y + 70, 105, 22);
                Rect cancelRect = new Rect(x + 165, y + 70, 105, 22);
                if (deleteRect.contains(mx, my) && modalTargetBackup != null) {
                    String id = modalTargetBackup.id();
                    closeModal();
                    BackupUiClient.deleteBackup(id);
                    return true;
                }
                if (cancelRect.contains(mx, my)) {
                    closeModal();
                    return true;
                }
            }
            default -> {}
        }
        return false;
    }

    private void openCreateModal() {
        createSelectedType = BackupConfig.get().backupMode;
        activeModal = ActiveModal.CREATE;
        rebuildWidgets();
    }

    private void openRenameModal(BackupUiBackup backup) {
        modalTargetBackup = backup;
        activeModal = ActiveModal.RENAME;
        rebuildWidgets();
    }

    private void closeModal() {
        activeModal = ActiveModal.NONE;
        modalTargetBackup = null;
        modalInputBox = null;
        rebuildWidgets();
    }

    private void selectBackupAndScrollToIt(String backupId) {
        selectedBackupId = backupId;
        detailScroll = 0;

        for (int i = 0; i < filteredItems.size(); i++) {
            if (filteredItems.get(i).backup().id().equals(backupId)) {
                int cardTop = i * (CARD_HEIGHT + CARD_GAP);
                int viewportH = contentBottom() - listViewportTop();
                if (cardTop < listScroll) {
                    listScroll = cardTop;
                } else if (cardTop + CARD_HEIGHT > listScroll + viewportH) {
                    listScroll = cardTop + CARD_HEIGHT - viewportH;
                }
                listScroll = Math.clamp(listScroll, 0, maxListScroll());
                break;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeModal != ActiveModal.NONE) {
            return true;
        }

        int leftX = leftPanelX();
        int leftW = leftPanelWidth();
        int rightX = rightPanelX();
        int rightW = rightPanelWidth();
        int top = contentTop();
        int bottom = contentBottom();

        if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= top && mouseY <= bottom) {
            listScroll = Math.clamp(listScroll - (int) (scrollY * 24), 0, maxListScroll());
            return true;
        }

        if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= top && mouseY <= bottom) {
            detailScroll = Math.clamp(detailScroll - (int) (scrollY * 24), 0, maxDetailScroll());
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (activeModal != ActiveModal.NONE) {
                closeModal();
                return true;
            }
            onClose();
            return true;
        }

        if (activeModal != ActiveModal.NONE) {
            if (event.key() == GLFW.GLFW_KEY_ENTER) {
                if (activeModal == ActiveModal.CREATE) {
                    String reqName = modalInputBox != null ? modalInputBox.getValue() : "";
                    closeModal();
                    BackupUiClient.createBackup(createSelectedType, reqName);
                    return true;
                } else if (activeModal == ActiveModal.RENAME && modalTargetBackup != null) {
                    String newName = modalInputBox != null ? modalInputBox.getValue() : "";
                    String backupId = modalTargetBackup.id();
                    closeModal();
                    BackupUiClient.renameBackup(backupId, newName);
                    return true;
                }
            }
            return super.keyPressed(event);
        }

        if ((searchBox == null || !searchBox.isFocused()) && !filteredItems.isEmpty()) {
            if (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN) {
                int currentIndex = -1;
                for (int i = 0; i < filteredItems.size(); i++) {
                    if (filteredItems.get(i).backup().id().equals(selectedBackupId)) {
                        currentIndex = i;
                        break;
                    }
                }

                int newIndex;
                if (event.key() == GLFW.GLFW_KEY_UP) {
                    newIndex = currentIndex > 0 ? currentIndex - 1 : filteredItems.size() - 1;
                } else {
                    newIndex = currentIndex < filteredItems.size() - 1 ? currentIndex + 1 : 0;
                }

                selectBackupAndScrollToIt(filteredItems.get(newIndex).backup().id());
                return true;
            }
        }

        return super.keyPressed(event);
    }

    private int contentTop() {
        return 38;
    }

    private int contentBottom() {
        return height - 24;
    }

    private int leftPanelX() {
        return ScreenChrome.OUTER;
    }

    private int leftPanelWidth() {
        int totalContentW = width - ScreenChrome.OUTER * 2;
        return Math.clamp((int) (totalContentW * 0.40f), 180, 320);
    }

    private int rightPanelX() {
        return leftPanelX() + leftPanelWidth() + 8;
    }

    private int rightPanelWidth() {
        return width - ScreenChrome.OUTER - rightPanelX();
    }

    private int listViewportTop() {
        return contentTop() + 46 + CHIP_HEIGHT + 4;
    }

    private int maxListScroll() {
        int contentH = filteredItems.size() * (CARD_HEIGHT + CARD_GAP) + 8;
        int viewportH = Math.max(1, contentBottom() - listViewportTop());
        return Math.max(0, contentH - viewportH);
    }

    private int calculateDetailHeight(BackupListItem item) {
        int h = 24 + 34 * 2 + 6 + 12 + 8 + 16;
        if (!item.backup().canDelete() && !value(item.backup().deleteBlockedReason()).isBlank()) {
            h += 26 + 12;
        }
        if (item.base() != null) {
            h += 28 + 6;
        }
        if (!item.children().isEmpty()) {
            h += 12 + item.children().size() * 28;
        } else if (item.base() == null) {
            h += 20;
        }
        return h + 20;
    }

    private int maxDetailScroll() {
        BackupListItem item = selectedBackupId != null ? itemsById.get(selectedBackupId) : null;
        if (item == null) return 0;
        int contentH = calculateDetailHeight(item);
        int viewportH = Math.max(1, (contentBottom() - contentTop()) - 34);
        return Math.max(0, contentH - viewportH);
    }

    private Rect refreshBtnRect() {
        return new Rect(width - ScreenChrome.OUTER - 60 - 64 - 8, 8, 64, TOOLBAR_BTN_HEIGHT);
    }

    private Rect configBtnRect() {
        return new Rect(width - ScreenChrome.OUTER - 60 - 4, 8, 60, TOOLBAR_BTN_HEIGHT);
    }

    private Rect createBtnRect() {
        return new Rect(leftPanelX(), contentTop(), leftPanelWidth(), 20);
    }

    private Rect filterChipRect(int index) {
        int leftX = leftPanelX();
        int leftW = leftPanelWidth();
        int count = BackupFilterType.values().length;
        int chipW = (leftW - (count - 1) * 3) / count;
        int x = leftX + index * (chipW + 3);
        int y = contentTop() + 46;
        return new Rect(x, y, chipW, CHIP_HEIGHT);
    }

    private Rect renameBtnInlineRect(int x, int y) {
        return new Rect(x, y, 60, 16);
    }

    private Rect jumpToBaseRect(int x, int y) {
        return new Rect(x, y, 42, 18);
    }

    private Rect jumpToChildRect(int x, int y) {
        return new Rect(x, y, 42, 18);
    }

    private Rect childItemRect(int x, int y, int w) {
        return new Rect(x, y, w, 24);
    }

    private Rect createModalTypeRect(int modalX, int y, int w, int h) {
        return new Rect(modalX + 16, y, w, h);
    }

    private void drawSurfaceButton(GuiGraphicsExtractor graphics, Rect rect, Component text, int mouseX, int mouseY, boolean active) {
        ScreenChrome.drawSurfaceButton(graphics, font, new ScreenChrome.Rect(rect.x(), rect.y(), rect.w(), rect.h()), text, active, rect.contains(mouseX, mouseY));
    }

    private void drawAccentButton(GuiGraphicsExtractor graphics, Rect rect, Component text, int mouseX, int mouseY, boolean active, int normalBg, int hoverBg, int outlineColor, int textColor) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int fill = !active ? ScreenChrome.BUTTON_DISABLED : (hovered ? hoverBg : normalBg);
        int outline = active ? outlineColor : ScreenChrome.BUTTON_OUTLINE_DISABLED;

        graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), fill);
        graphics.outline(rect.x(), rect.y(), rect.w(), rect.h(), outline);
        int textY = rect.y() + (rect.h() - font.lineHeight) / 2 + 1;
        graphics.centeredText(font, text, rect.x() + rect.w() / 2, textY, active ? textColor : ScreenChrome.BUTTON_TEXT_DISABLED);
    }

    private void drawTypeBadge(GuiGraphicsExtractor graphics, int x, int y, int w, int h, BackupType type) {
        int fill = switch (type) {
            case FULL -> FULL_BG;
            case DIFFERENTIAL -> DIFF_BG;
            case PARTIAL -> PARTIAL_BG;
        };
        int textColor = switch (type) {
            case FULL -> FULL_TEXT;
            case DIFFERENTIAL -> DIFF_TEXT;
            case PARTIAL -> PARTIAL_TEXT;
        };
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.outline(x, y, w, h, (textColor & 0x00FFFFFF) | 0x88000000);
        graphics.centeredText(font, Component.literal(type.name()), x + w / 2, y + (h - font.lineHeight) / 2 + 1, textColor);
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

    private boolean matches(BackupUiBackup backup, String needle) {
        return backup.displayName().toLowerCase(Locale.ROOT).contains(needle)
                || backup.type().name().toLowerCase(Locale.ROOT).contains(needle)
                || value(backup.reason()).toLowerCase(Locale.ROOT).contains(needle)
                || value(backup.baseBackupId()).toLowerCase(Locale.ROOT).contains(needle);
    }

    private record BackupListItem(BackupUiBackup backup, BackupUiBackup base, List<BackupUiBackup> children) {
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }
}
