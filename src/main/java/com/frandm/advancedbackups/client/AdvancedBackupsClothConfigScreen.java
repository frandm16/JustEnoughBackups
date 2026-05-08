package com.frandm.advancedbackups.client;

import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.config.BackupConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AdvancedBackupsClothConfigScreen {
    private AdvancedBackupsClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        BackupConfig config = BackupConfig.get().copy();
        BackupConfig defaults = BackupConfig.defaults();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Advanced Backups"));
        builder.setSavingRunnable(() -> BackupConfig.saveAndApply(config));

        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Backups"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        category.addEntry(entries.startEnumSelector(
                        Component.literal("Backup mode"),
                        BackupType.class,
                        config.backupMode
                )
                .setDefaultValue(defaults.backupMode)
                .setTooltip(Component.literal("FULL: copies the whole world every time. Safest and easiest to restore.\nPARTIAL: copies only files changed since the latest backup. Smaller, but depends on the backup chain.\nDIFFERENTIAL: copies files changed since the latest full backup. Larger than partial, simpler to restore."))
                .setSaveConsumer(value -> config.backupMode = value)
                .build());

        category.addEntry(entries.startBooleanToggle(
                        Component.literal("Automatic backups"),
                        config.automaticBackupsEnabled
                )
                .setDefaultValue(defaults.automaticBackupsEnabled)
                .setTooltip(Component.literal("When enabled, the server creates backups automatically at the configured interval."))
                .setSaveConsumer(value -> config.automaticBackupsEnabled = value)
                .build());

        category.addEntry(entries.startBooleanToggle(
                        Component.literal("Backup when world starts"),
                        config.backupOnServerStart
                )
                .setDefaultValue(defaults.backupOnServerStart)
                .setTooltip(Component.literal("Creates one backup when the server finishes starting the world. Disabled by default."))
                .setSaveConsumer(value -> config.backupOnServerStart = value)
                .build());

        category.addEntry(entries.startBooleanToggle(
                        Component.literal("Backup when world closes"),
                        config.backupOnServerStop
                )
                .setDefaultValue(defaults.backupOnServerStop)
                .setTooltip(Component.literal("Creates one backup while the server is shutting down. Shutdown waits for it to finish. Disabled by default."))
                .setSaveConsumer(value -> config.backupOnServerStop = value)
                .build());

        category.addEntry(entries.startIntField(
                        Component.literal("Automatic interval minutes"),
                        config.automaticIntervalMinutes
                )
                .setDefaultValue(defaults.automaticIntervalMinutes)
                .setMin(1)
                .setTooltip(Component.literal("Minutes between automatic backups. The timer resets when this value is saved."))
                .setSaveConsumer(value -> config.automaticIntervalMinutes = value)
                .build());

        category.addEntry(entries.startIntField(
                        Component.literal("Keep full backups"),
                        config.retention.full
                )
                .setDefaultValue(defaults.retention.full)
                .setMin(1)
                .setTooltip(Component.literal("Maximum number of full backups to keep. At least one full backup is always retained."))
                .setSaveConsumer(value -> config.retention.full = value)
                .build());

        category.addEntry(entries.startIntField(
                        Component.literal("Keep partial backups"),
                        config.retention.incremental
                )
                .setDefaultValue(defaults.retention.incremental)
                .setMin(0)
                .setTooltip(Component.literal("Maximum number of partial backups to keep. Required base backups are protected."))
                .setSaveConsumer(value -> config.retention.incremental = value)
                .build());

        category.addEntry(entries.startIntField(
                        Component.literal("Keep differential backups"),
                        config.retention.differential
                )
                .setDefaultValue(defaults.retention.differential)
                .setMin(0)
                .setTooltip(Component.literal("Maximum number of differential backups to keep. Required full backups are protected."))
                .setSaveConsumer(value -> config.retention.differential = value)
                .build());

        category.addEntry(entries.startStrField(
                        Component.literal("Backup directory"),
                        config.backupDirectory
                )
                .setDefaultValue(defaults.backupDirectory)
                .setTooltip(Component.literal("Directory where backups are stored. Relative paths are resolved from the game directory."))
                .setSaveConsumer(value -> config.backupDirectory = value)
                .build());

        return builder.build();
    }
}
