package com.frandm.justenoughbackups.client.screen.config;

import com.frandm.justenoughbackups.config.ConfigColor;

public enum ConfigColorChannel {
    ALPHA("screen.justenoughbackups.config.a_channel", 24, "A"),
    RED("screen.justenoughbackups.config.r_channel", 16, "R"),
    GREEN("screen.justenoughbackups.config.g_channel", 8, "G"),
    BLUE("screen.justenoughbackups.config.b_channel", 0, "B");

    private final String labelKey;
    private final int shift;
    private final String channelLabel;

    ConfigColorChannel(String labelKey, int shift, String channelLabel) {
        this.labelKey = labelKey;
        this.shift = shift;
        this.channelLabel = channelLabel;
    }

    public String labelKey() {
        return labelKey;
    }

    public String tooltipKey() {
        return labelKey + ".tooltip";
    }

    public String channelLabel() {
        return channelLabel;
    }

    public int extract(int color) {
        return ConfigColor.channel(color, shift);
    }

    public int apply(int color, int value) {
        return ConfigColor.applyChannel(color, shift, value);
    }
}
