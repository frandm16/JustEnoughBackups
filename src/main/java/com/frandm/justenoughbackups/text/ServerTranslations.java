package com.frandm.justenoughbackups.text;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ServerTranslations {
    private static final String LANG_PATH = "/assets/justenoughbackups/lang/en_us.json";
    private static final Gson GSON = new Gson();
    private static final Map<String, String> TRANSLATIONS = loadTranslations();

    private ServerTranslations() {
    }

    public static Component component(String key, Object... args) {
        return Component.literal(text(key, args));
    }

    public static String text(String key, Object... args) {
        String template = TRANSLATIONS.get(key);
        if (template == null || template.isBlank()) {
            return fallback(key, args);
        }

        String result = template;
        Object[] safeArgs = args == null ? new Object[0] : args;
        for (Object arg : safeArgs) {
            int placeholder = result.indexOf("%s");
            if (placeholder < 0) {
                break;
            }
            String replacement = String.valueOf(arg);
            result = result.substring(0, placeholder) + replacement + result.substring(placeholder + 2);
        }
        return result;
    }

    private static Map<String, String> loadTranslations() {
        try (InputStream stream = ServerTranslations.class.getResourceAsStream(LANG_PATH)) {
            if (stream == null) {
                WorldBackupMod.LOGGER.warn("Missing bundled language file: {}", LANG_PATH);
                return Map.of();
            }

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                java.lang.reflect.Type type = new TypeToken<Map<String, String>>() {
                }.getType();
                Map<String, String> translations = GSON.fromJson(reader, type);
                return translations == null ? Map.of() : Map.copyOf(translations);
            }
        } catch (IOException | RuntimeException exception) {
            WorldBackupMod.LOGGER.warn("Failed to load bundled server translations from {}.", LANG_PATH, exception);
            return Map.of();
        }
    }

    private static String fallback(String key, Object... args) {
        if (args == null || args.length == 0) {
            return key;
        }
        return key + " " + List.of(args).stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
