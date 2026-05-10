package com.frandm.justenoughbackups.client.screen.config;

import com.frandm.justenoughbackups.config.BackupConfig;

public enum ConfigColorTarget {
    BACKGROUND("screen.justenoughbackups.config.background_color") {
        @Override
        public String get(BackupConfig.Popup popup) {
            return popup.backgroundColor;
        }

        @Override
        public void set(BackupConfig.Popup popup, String value) {
            popup.backgroundColor = value;
        }

        @Override
        public int argb(BackupConfig.Popup popup) {
            return popup.backgroundColorArgb();
        }
    },
    RUNNING("screen.justenoughbackups.config.running_color") {
        @Override
        public String get(BackupConfig.Popup popup) {
            return popup.runningColor;
        }

        @Override
        public void set(BackupConfig.Popup popup, String value) {
            popup.runningColor = value;
        }

        @Override
        public int argb(BackupConfig.Popup popup) {
            return popup.runningColorArgb();
        }
    },
    COMPLETED("screen.justenoughbackups.config.completed_color") {
        @Override
        public String get(BackupConfig.Popup popup) {
            return popup.completedColor;
        }

        @Override
        public void set(BackupConfig.Popup popup, String value) {
            popup.completedColor = value;
        }

        @Override
        public int argb(BackupConfig.Popup popup) {
            return popup.completedColorArgb();
        }
    },
    FAILED("screen.justenoughbackups.config.failed_color") {
        @Override
        public String get(BackupConfig.Popup popup) {
            return popup.failedColor;
        }

        @Override
        public void set(BackupConfig.Popup popup, String value) {
            popup.failedColor = value;
        }

        @Override
        public int argb(BackupConfig.Popup popup) {
            return popup.failedColorArgb();
        }
    },
    TEXT("screen.justenoughbackups.config.text_color") {
        @Override
        public String get(BackupConfig.Popup popup) {
            return popup.textColor;
        }

        @Override
        public void set(BackupConfig.Popup popup, String value) {
            popup.textColor = value;
        }

        @Override
        public int argb(BackupConfig.Popup popup) {
            return popup.textColorArgb();
        }
    };

    private final String labelKey;

    ConfigColorTarget(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }

    public String tooltipKey() {
        return labelKey + ".tooltip";
    }

    public abstract String get(BackupConfig.Popup popup);

    public abstract void set(BackupConfig.Popup popup, String value);

    public abstract int argb(BackupConfig.Popup popup);
}
