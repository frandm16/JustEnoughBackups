package com.frandm.justenoughbackups.client.screen.config;

import com.frandm.justenoughbackups.config.ConfigColor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ConfigValidator {
    List<ValidationError> validate(ConfigScreenState state) {
        List<ValidationError> errors = new ArrayList<>();
        validateInt(errors, ConfigFieldId.INTERVAL_MINUTES, state.working.automaticIntervalMinutes, 1, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.interval_min");
        validateInt(errors, ConfigFieldId.AUTOMATIC_BACKUP_WARNING_MINUTES, state.working.automaticBackupWarningMinutes, 1, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.warning_minutes_min");
        validateInt(errors, ConfigFieldId.PERMISSION_LEVEL, state.working.commandPermissionLevel, 0, 4, "screen.justenoughbackups.config.error.permission_range");
        validateInt(errors, ConfigFieldId.KEEP_FULL, state.working.retention.full, 1, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.full_min");
        validateInt(errors, ConfigFieldId.KEEP_PARTIAL, state.working.retention.incremental, 0, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.partial_min");
        validateInt(errors, ConfigFieldId.KEEP_DIFFERENTIAL, state.working.retention.differential, 0, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.differential_min");
        validateInt(errors, ConfigFieldId.MAX_TOTAL_SIZE_MB, state.working.retention.maxTotalSizeMb, 0, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.max_total_size_mb_min");
        validateInt(errors, ConfigFieldId.MINIMUM_FREE_SPACE_RESERVE_MB, state.working.minimumFreeSpaceReserveMb, 0, Integer.MAX_VALUE, "screen.justenoughbackups.config.error.minimum_free_space_reserve_mb_min");

        validateRawInt(errors, state, ConfigFieldId.INTERVAL_MINUTES, 1, Integer.MAX_VALUE);
        validateRawInt(errors, state, ConfigFieldId.AUTOMATIC_BACKUP_WARNING_MINUTES, 1, Integer.MAX_VALUE);
        validateRawInt(errors, state, ConfigFieldId.PERMISSION_LEVEL, 0, 4);
        validateRawInt(errors, state, ConfigFieldId.KEEP_FULL, 1, Integer.MAX_VALUE);
        validateRawInt(errors, state, ConfigFieldId.KEEP_PARTIAL, 0, Integer.MAX_VALUE);
        validateRawInt(errors, state, ConfigFieldId.KEEP_DIFFERENTIAL, 0, Integer.MAX_VALUE);
        validateRawInt(errors, state, ConfigFieldId.MAX_TOTAL_SIZE_MB, 0, Integer.MAX_VALUE);
        validateRawInt(errors, state, ConfigFieldId.MINIMUM_FREE_SPACE_RESERVE_MB, 0, Integer.MAX_VALUE);

        if (state.rawInputs.getOrDefault(ConfigFieldId.BACKUP_DIRECTORY, value(state.working.backupDirectory)).isBlank()) {
            errors.add(new ValidationError(ConfigFieldId.BACKUP_DIRECTORY, Component.translatable("screen.justenoughbackups.config.error.backup_directory_empty")));
        }

        for (ConfigColorTarget target : ConfigColorTarget.values()) {
            ConfigFieldId fieldId = colorFieldId(target);
            String raw = state.rawInputs.getOrDefault(fieldId, target.get(state.working.popup));
            if (ConfigColor.parse(raw).isEmpty()) {
                errors.add(new ValidationError(fieldId, Component.translatable("screen.justenoughbackups.config.error.color", Component.translatable(fieldId.labelKey()))));
            }
        }

        return errors;
    }

    private void validateInt(List<ValidationError> errors, ConfigFieldId fieldId, int value, int min, int max, String key) {
        if (value < min || value > max) {
            errors.add(new ValidationError(fieldId, Component.translatable(key)));
        }
    }

    private void validateRawInt(List<ValidationError> errors, ConfigScreenState state, ConfigFieldId fieldId, int min, int max) {
        String raw = state.rawInputs.get(fieldId);
        if (raw == null) {
            return;
        }
        Optional<Integer> parsed = parseInt(raw);
        if (parsed.isEmpty()) {
            errors.add(new ValidationError(fieldId, Component.translatable("screen.justenoughbackups.config.error.whole_number", Component.translatable(fieldId.labelKey()))));
        } else if (parsed.get() < min || parsed.get() > max) {
            errors.add(new ValidationError(fieldId, Component.translatable("screen.justenoughbackups.config.error.number_range", Component.translatable(fieldId.labelKey()), min, max)));
        }
    }

    private ConfigFieldId colorFieldId(ConfigColorTarget target) {
        return switch (target) {
            case BACKGROUND -> ConfigFieldId.POPUP_BACKGROUND_COLOR;
            case RUNNING -> ConfigFieldId.POPUP_RUNNING_COLOR;
            case COMPLETED -> ConfigFieldId.POPUP_COMPLETED_COLOR;
            case FAILED -> ConfigFieldId.POPUP_FAILED_COLOR;
            case TEXT -> ConfigFieldId.POPUP_TEXT_COLOR;
        };
    }

    private static Optional<Integer> parseInt(String value) {
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
