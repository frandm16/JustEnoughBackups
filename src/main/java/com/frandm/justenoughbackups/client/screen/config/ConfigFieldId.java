package com.frandm.justenoughbackups.client.screen.config;

public enum ConfigFieldId {
    BACKUP_MODE("screen.justenoughbackups.config.backup_mode", ConfigTab.BACKUPS, ConfigControlType.ENUM),
    AUTOMATIC_BACKUPS("screen.justenoughbackups.config.automatic_backups", ConfigTab.BACKUPS, ConfigControlType.BOOLEAN),
    PAUSE_WITHOUT_PLAYERS("screen.justenoughbackups.config.pause_without_players", ConfigTab.BACKUPS, ConfigControlType.BOOLEAN),
    BACKUP_ON_START("screen.justenoughbackups.config.backup_on_start", ConfigTab.BACKUPS, ConfigControlType.BOOLEAN),
    BACKUP_ON_STOP("screen.justenoughbackups.config.backup_on_stop", ConfigTab.BACKUPS, ConfigControlType.BOOLEAN),
    INTERVAL_MINUTES("screen.justenoughbackups.config.interval_minutes", ConfigTab.BACKUPS, ConfigControlType.INT),
    AUTOMATIC_BACKUP_WARNING("screen.justenoughbackups.config.automatic_backup_warning", ConfigTab.BACKUPS, ConfigControlType.BOOLEAN),
    AUTOMATIC_BACKUP_WARNING_MINUTES("screen.justenoughbackups.config.automatic_backup_warning_minutes", ConfigTab.BACKUPS, ConfigControlType.INT),
    KEEP_FULL("screen.justenoughbackups.config.keep_full", ConfigTab.BACKUPS, ConfigControlType.INT),
    KEEP_PARTIAL("screen.justenoughbackups.config.keep_partial", ConfigTab.BACKUPS, ConfigControlType.INT),
    KEEP_DIFFERENTIAL("screen.justenoughbackups.config.keep_differential", ConfigTab.BACKUPS, ConfigControlType.INT),
    MAX_TOTAL_SIZE_MB("screen.justenoughbackups.config.max_total_size_mb", ConfigTab.BACKUPS, ConfigControlType.INT),
    MINIMUM_FREE_SPACE_RESERVE_MB("screen.justenoughbackups.config.minimum_free_space_reserve_mb", ConfigTab.BACKUPS, ConfigControlType.INT),
    PERMISSION_LEVEL("screen.justenoughbackups.config.permission_level", ConfigTab.BACKUPS, ConfigControlType.INT),
    MESSAGE_CHANNEL("screen.justenoughbackups.config.message_channel", ConfigTab.BACKUPS, ConfigControlType.ENUM),

    AUTOMATIC_BACKUPS_HEADER("screen.justenoughbackups.config.automatic_backups_header", ConfigTab.BACKUPS, ConfigControlType.SECTION_HEADER),
    AUTOMATIC_FULL_ENABLED("screen.justenoughbackups.config.automatic_full_enabled", ConfigTab.BACKUPS, ConfigControlType.BOOLEAN),
    AUTOMATIC_DIFFERENTIAL_ENABLED("screen.justenoughbackups.config.automatic_differential_enabled", ConfigTab.BACKUPS, ConfigControlType.BOOLEAN),
    AUTOMATIC_PARTIAL_ENABLED("screen.justenoughbackups.config.automatic_partial_enabled", ConfigTab.BACKUPS, ConfigControlType.BOOLEAN),
    AUTOMATIC_FULL_INTERVAL_MINUTES("screen.justenoughbackups.config.automatic_full_interval_minutes", ConfigTab.BACKUPS, ConfigControlType.INT),
    AUTOMATIC_DIFFERENTIAL_INTERVAL_MINUTES("screen.justenoughbackups.config.automatic_differential_interval_minutes", ConfigTab.BACKUPS, ConfigControlType.INT),
    AUTOMATIC_PARTIAL_INTERVAL_MINUTES("screen.justenoughbackups.config.automatic_partial_interval_minutes", ConfigTab.BACKUPS, ConfigControlType.INT),

    INTEGRITY_MODE("screen.justenoughbackups.config.integrity_mode", ConfigTab.BACKUPS, ConfigControlType.ENUM),
    INCLUDE_SUMMARY_FILE("screen.justenoughbackups.config.include_summary_file", ConfigTab.BACKUPS, ConfigControlType.BOOLEAN),
    BACKUP_DIRECTORY("screen.justenoughbackups.config.backup_directory", ConfigTab.BACKUPS, ConfigControlType.TEXT),
    EXCLUDED_PATHS("screen.justenoughbackups.config.excluded_paths", ConfigTab.BACKUPS, ConfigControlType.ACTION),
    POPUP_TITLE("screen.justenoughbackups.config.popup_title", ConfigTab.HUD, ConfigControlType.TEXT),
    POPUP_RUNNING_TEXT("screen.justenoughbackups.config.running_text", ConfigTab.HUD, ConfigControlType.TEXT),
    POPUP_SCANNING_TEXT("screen.justenoughbackups.config.scanning_text", ConfigTab.HUD, ConfigControlType.TEXT),
    POPUP_COMPLETED_TEXT("screen.justenoughbackups.config.completed_text", ConfigTab.HUD, ConfigControlType.TEXT),
    POPUP_FAILED_TEXT("screen.justenoughbackups.config.failed_text", ConfigTab.HUD, ConfigControlType.TEXT),
    POPUP_ENABLED("screen.justenoughbackups.config.show_popup", ConfigTab.HUD, ConfigControlType.BOOLEAN),
    POPUP_SHOW_TITLE("screen.justenoughbackups.config.show_title", ConfigTab.HUD, ConfigControlType.BOOLEAN),
    POPUP_CENTER_TEXT("screen.justenoughbackups.config.center_text", ConfigTab.HUD, ConfigControlType.BOOLEAN),
    POPUP_SHOW_BORDER("screen.justenoughbackups.config.show_border", ConfigTab.HUD, ConfigControlType.BOOLEAN),
    PREVIEW_STATE("screen.justenoughbackups.config.preview_state", ConfigTab.PREVIEW, ConfigControlType.ACTION),
    POPUP_BACKGROUND_COLOR("screen.justenoughbackups.config.background_color", ConfigTab.HUD, ConfigControlType.COLOR),
    POPUP_RUNNING_COLOR("screen.justenoughbackups.config.running_color", ConfigTab.HUD, ConfigControlType.COLOR),
    POPUP_COMPLETED_COLOR("screen.justenoughbackups.config.completed_color", ConfigTab.HUD, ConfigControlType.COLOR),
    POPUP_FAILED_COLOR("screen.justenoughbackups.config.failed_color", ConfigTab.HUD, ConfigControlType.COLOR),
    POPUP_TEXT_COLOR("screen.justenoughbackups.config.text_color", ConfigTab.HUD, ConfigControlType.COLOR),
    POPUP_CHANNEL_A("screen.justenoughbackups.config.a_channel", ConfigTab.HUD, ConfigControlType.CHANNEL),
    POPUP_CHANNEL_R("screen.justenoughbackups.config.r_channel", ConfigTab.HUD, ConfigControlType.CHANNEL),
    POPUP_CHANNEL_G("screen.justenoughbackups.config.g_channel", ConfigTab.HUD, ConfigControlType.CHANNEL),
    POPUP_CHANNEL_B("screen.justenoughbackups.config.b_channel", ConfigTab.HUD, ConfigControlType.CHANNEL),
    OPEN_PREVIEW("screen.justenoughbackups.config.open_preview", ConfigTab.PREVIEW, ConfigControlType.ACTION);

    private final String labelKey;
    private final ConfigTab tab;
    private final ConfigControlType controlType;

    ConfigFieldId(String labelKey, ConfigTab tab, ConfigControlType controlType) {
        this.labelKey = labelKey;
        this.tab = tab;
        this.controlType = controlType;
    }

    public String labelKey() {
        return labelKey;
    }

    public String tooltipKey() {
        return labelKey + ".tooltip";
    }

    public ConfigTab tab() {
        return tab;
    }

    public ConfigControlType controlType() {
        return controlType;
    }
}
