package com.frandm.advancedbackups.client;

import com.frandm.advancedbackups.backup.BackupConstants;
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
                .setDefaultValue(BackupType.FULL)
                .setSaveConsumer(value -> config.backupMode = value)
                .build());

        category.addEntry(entries.startBooleanToggle(
                        Component.literal("Automatic backups"),
                        config.automaticBackupsEnabled
                )
                .setDefaultValue(true)
                .setSaveConsumer(value -> config.automaticBackupsEnabled = value)
                .build());

        category.addEntry(entries.startIntField(
                        Component.literal("Automatic interval minutes"),
                        config.automaticIntervalMinutes
                )
                .setDefaultValue(15)
                .setMin(1)
                .setSaveConsumer(value -> config.automaticIntervalMinutes = value)
                .build());

        category.addEntry(entries.startIntField(
                        Component.literal("Retain full backups"),
                        config.retention.full
                )
                .setDefaultValue(5)
                .setMin(1)
                .setSaveConsumer(value -> config.retention.full = value)
                .build());

        category.addEntry(entries.startIntField(
                        Component.literal("Retain incremental backups"),
                        config.retention.incremental
                )
                .setDefaultValue(20)
                .setMin(0)
                .setSaveConsumer(value -> config.retention.incremental = value)
                .build());

        category.addEntry(entries.startIntField(
                        Component.literal("Retain differential backups"),
                        config.retention.differential
                )
                .setDefaultValue(10)
                .setMin(0)
                .setSaveConsumer(value -> config.retention.differential = value)
                .build());

        category.addEntry(entries.startStrField(
                        Component.literal("Backup directory"),
                        config.backupDirectory
                )
                .setDefaultValue(BackupConstants.DEFAULT_BACKUP_DIRECTORY)
                .setSaveConsumer(value -> config.backupDirectory = value)
                .build());

        return builder.build();
    }
}
