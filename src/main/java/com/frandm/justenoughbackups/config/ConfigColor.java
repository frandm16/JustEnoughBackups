package com.frandm.justenoughbackups.config;

import java.util.Locale;
import java.util.Optional;

public final class ConfigColor {
    private ConfigColor() {
    }

    public static Optional<Integer> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }

        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() == 6) {
            normalized = "FF" + normalized;
        }
        if (normalized.length() != 8) {
            return Optional.empty();
        }

        try {
            return Optional.of((int) Long.parseLong(normalized, 16));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public static int parseOrDefault(String raw, String fallback) {
        return parse(raw).or(() -> parse(fallback)).orElse(0xFFFFFFFF);
    }

    public static String normalize(String raw, String fallback) {
        return format(parseOrDefault(raw, fallback));
    }

    public static String format(int argb) {
        return String.format(Locale.ROOT, "0x%08X", argb);
    }

    public static int channel(int color, int shift) {
        return (color >>> shift) & 0xFF;
    }

    public static int applyChannel(int color, int shift, int value) {
        int mask = 0xFF << shift;
        return (color & ~mask) | (Math.clamp(value, 0, 255) << shift);
    }
}
