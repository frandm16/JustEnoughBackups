package com.frandm.justenoughbackups.client.screen.config;

import java.util.List;

final class ConfigFieldSpecs {
    private static final List<ConfigFieldSpec> ALL = List.of(
            //Backups tab
            ConfigFieldSpec.of(ConfigFieldId.BACKUP_MODE),
            ConfigFieldSpec.of(ConfigFieldId.AUTOMATIC_BACKUPS_HEADER),
            ConfigFieldSpec.of(ConfigFieldId.AUTOMATIC_FULL_ENABLED),
            ConfigFieldSpec.intField(ConfigFieldId.AUTOMATIC_FULL_INTERVAL_MINUTES, 1, Integer.MAX_VALUE),
            ConfigFieldSpec.of(ConfigFieldId.AUTOMATIC_DIFFERENTIAL_ENABLED),
            ConfigFieldSpec.intField(ConfigFieldId.AUTOMATIC_DIFFERENTIAL_INTERVAL_MINUTES, 1, Integer.MAX_VALUE),
            ConfigFieldSpec.of(ConfigFieldId.AUTOMATIC_PARTIAL_ENABLED),
            ConfigFieldSpec.intField(ConfigFieldId.AUTOMATIC_PARTIAL_INTERVAL_MINUTES, 1, Integer.MAX_VALUE),
            ConfigFieldSpec.of(ConfigFieldId.PAUSE_WITHOUT_PLAYERS),
            ConfigFieldSpec.of(ConfigFieldId.BACKUP_ON_START),
            ConfigFieldSpec.of(ConfigFieldId.BACKUP_ON_STOP),
            ConfigFieldSpec.of(ConfigFieldId.AUTOMATIC_BACKUP_WARNING),
            ConfigFieldSpec.intField(ConfigFieldId.AUTOMATIC_BACKUP_WARNING_MINUTES, 1, Integer.MAX_VALUE),
            ConfigFieldSpec.intField(ConfigFieldId.KEEP_FULL, 1, Integer.MAX_VALUE),
            ConfigFieldSpec.intField(ConfigFieldId.KEEP_PARTIAL, 0, Integer.MAX_VALUE),
            ConfigFieldSpec.intField(ConfigFieldId.KEEP_DIFFERENTIAL, 0, Integer.MAX_VALUE),
            ConfigFieldSpec.textField(ConfigFieldId.EXCLUDED_PATHS, 512),
            ConfigFieldSpec.intField(ConfigFieldId.MAX_TOTAL_SIZE_MB, 0, Integer.MAX_VALUE),
            ConfigFieldSpec.intField(ConfigFieldId.MINIMUM_FREE_SPACE_RESERVE_MB, 0, Integer.MAX_VALUE),
            ConfigFieldSpec.intField(ConfigFieldId.THREAD_COUNT, 1, Math.max(1, Runtime.getRuntime().availableProcessors())),
            ConfigFieldSpec.intField(ConfigFieldId.PERMISSION_LEVEL, 0, 4),
            ConfigFieldSpec.of(ConfigFieldId.MESSAGE_CHANNEL),
            ConfigFieldSpec.of(ConfigFieldId.INTEGRITY_MODE),
            ConfigFieldSpec.of(ConfigFieldId.INCLUDE_SUMMARY_FILE),
            ConfigFieldSpec.textField(ConfigFieldId.BACKUP_DIRECTORY, 256),

            // HUD tab
            ConfigFieldSpec.textField(ConfigFieldId.POPUP_TITLE, 256),
            ConfigFieldSpec.textField(ConfigFieldId.POPUP_SCANNING_TEXT, 256),
            ConfigFieldSpec.textField(ConfigFieldId.POPUP_RUNNING_TEXT, 256),
            ConfigFieldSpec.textField(ConfigFieldId.POPUP_COMPLETED_TEXT, 256),
            ConfigFieldSpec.textField(ConfigFieldId.POPUP_FAILED_TEXT, 256),
            ConfigFieldSpec.of(ConfigFieldId.POPUP_ENABLED),
            ConfigFieldSpec.of(ConfigFieldId.POPUP_SHOW_TITLE),
            ConfigFieldSpec.of(ConfigFieldId.POPUP_CENTER_TEXT),
            ConfigFieldSpec.of(ConfigFieldId.POPUP_SHOW_BORDER),
            ConfigFieldSpec.colorField(ConfigFieldId.POPUP_BACKGROUND_COLOR),
            ConfigFieldSpec.colorField(ConfigFieldId.POPUP_RUNNING_COLOR),
            ConfigFieldSpec.colorField(ConfigFieldId.POPUP_COMPLETED_COLOR),
            ConfigFieldSpec.colorField(ConfigFieldId.POPUP_FAILED_COLOR),
            ConfigFieldSpec.colorField(ConfigFieldId.POPUP_TEXT_COLOR),
            ConfigFieldSpec.of(ConfigFieldId.POPUP_CHANNEL_A),
            ConfigFieldSpec.of(ConfigFieldId.POPUP_CHANNEL_R),
            ConfigFieldSpec.of(ConfigFieldId.POPUP_CHANNEL_G),
            ConfigFieldSpec.of(ConfigFieldId.POPUP_CHANNEL_B),

            //Preview tab
            ConfigFieldSpec.of(ConfigFieldId.PREVIEW_STATE),
            ConfigFieldSpec.of(ConfigFieldId.OPEN_PREVIEW)
    );

    private ConfigFieldSpecs() {
    }

    static List<ConfigFieldSpec> forTab(ConfigTab tab) {
        return ALL.stream().filter(spec -> spec.id().tab() == tab).toList();
    }
}
