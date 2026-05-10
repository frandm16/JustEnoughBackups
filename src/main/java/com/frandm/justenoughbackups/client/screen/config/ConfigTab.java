package com.frandm.justenoughbackups.client.screen.config;

public enum ConfigTab {
    BACKUPS("screen.justenoughbackups.config.tab.backups"),
    HUD("screen.justenoughbackups.config.tab.hud"),
    PREVIEW("screen.justenoughbackups.config.tab.preview");

    private final String key;

    ConfigTab(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
