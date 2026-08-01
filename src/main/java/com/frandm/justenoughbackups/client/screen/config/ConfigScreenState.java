package com.frandm.justenoughbackups.client.screen.config;

import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.progress.BackupProgressPayload;
import com.frandm.justenoughbackups.config.BackupConfig;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

final class ConfigScreenState {
    private static final int DEFAULT_PREVIEW_BYTES = 128 * 1024 * 1024;
    private static final int DEFAULT_TOTAL_BYTES = 304 * 1024 * 1024;

    final Map<ConfigTab, Integer> scrollByTab = new EnumMap<>(ConfigTab.class);
    final Map<ConfigFieldId, String> rawInputs = new HashMap<>();
    BackupConfig working;
    ConfigTab selectedTab = ConfigTab.BACKUPS;
    ConfigPreviewState previewState = ConfigPreviewState.RUNNING;
    ConfigColorTarget selectedColor = ConfigColorTarget.BACKGROUND;
    ConfigFieldId focusedField;
    Integer focusedExcludedPathIndex;
    int cursor;

    ConfigScreenState(BackupConfig working) {
        this.working = working;
    }

    void resetTab(ConfigTab tab) {
        BackupConfig defaults = BackupConfig.defaults();
        switch (tab) {
            case BACKUPS -> {
                working.backupMode = defaults.backupMode;
                working.pauseAutomaticBackupsWithoutPlayers = defaults.pauseAutomaticBackupsWithoutPlayers;
                working.backupOnServerStart = defaults.backupOnServerStart;
                working.backupOnServerStop = defaults.backupOnServerStop;
                working.automaticBackupWarningEnabled = defaults.automaticBackupWarningEnabled;
                working.automaticBackupWarningMinutes = defaults.automaticBackupWarningMinutes;
                working.commandPermissionLevel = defaults.commandPermissionLevel;
                working.messageChannel = defaults.messageChannel;
                working.integrityMode = defaults.integrityMode;
                working.includeSummaryFile = defaults.includeSummaryFile;
                working.backupDirectory = defaults.backupDirectory;
                working.excludedPaths = new ArrayList<>(defaults.excludedPaths);
                working.retention.full = defaults.retention.full;
                working.retention.incremental = defaults.retention.incremental;
                working.retention.differential = defaults.retention.differential;
                working.retention.maxTotalSizeMb = defaults.retention.maxTotalSizeMb;
                working.minimumFreeSpaceReserveMb = defaults.minimumFreeSpaceReserveMb;

                BackupConfig.AutomaticSchedule defaultSchedule = new BackupConfig.AutomaticSchedule();
                defaultSchedule.normalize();
                working.automaticSchedule = defaultSchedule;
            }
            case HUD -> {
                working.popup = defaults.popup.copy();
                previewState = ConfigPreviewState.RUNNING;
                selectedColor = ConfigColorTarget.BACKGROUND;
            }
            case PREVIEW -> previewState = ConfigPreviewState.RUNNING;
        }
    }

    void resetAll() {
        working = BackupConfig.defaults();
        selectedTab = ConfigTab.BACKUPS;
        previewState = ConfigPreviewState.RUNNING;
        selectedColor = ConfigColorTarget.BACKGROUND;
        focusedField = null;
        focusedExcludedPathIndex = null;
        cursor = 0;
        rawInputs.clear();
        scrollByTab.clear();
    }

    BackupProgressPayload previewPayload() {
        long written = previewState == ConfigPreviewState.COMPLETED ? DEFAULT_TOTAL_BYTES : DEFAULT_PREVIEW_BYTES;
        return new BackupProgressPayload("preview", BackupType.FULL, "automatic", written, DEFAULT_TOTAL_BYTES, 42, 100, previewState.progressState());
    }

    int currentScroll() {
        return scrollByTab.getOrDefault(selectedTab, 0);
    }

    void setCurrentScroll(int value) {
        scrollByTab.put(selectedTab, Math.max(0, value));
    }
}
