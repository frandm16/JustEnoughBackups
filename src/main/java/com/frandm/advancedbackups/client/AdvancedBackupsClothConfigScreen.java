package com.frandm.advancedbackups.client;

import com.frandm.advancedbackups.backup.model.BackupIntegrityMode;
import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.config.BackupConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.TextListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
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

        ConfigCategory backups = builder.getOrCreateCategory(Component.literal("Backups"));
        ConfigCategory retention = builder.getOrCreateCategory(Component.literal("Retention"));
        ConfigCategory permissions = builder.getOrCreateCategory(Component.literal("Permissions"));
        ConfigCategory integrity = builder.getOrCreateCategory(Component.literal("Integrity"));
        ConfigCategory popup = builder.getOrCreateCategory(Component.literal("Popup"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        backups.addEntry(entries.startEnumSelector(
                        Component.literal("Backup mode"),
                        BackupType.class,
                        config.backupMode
                )
                .setDefaultValue(defaults.backupMode)
                .setTooltip(Component.literal("""
                        FULL: copies the whole world every time. Safest and easiest to restore.
                        PARTIAL: copies only files changed since the latest backup. Smaller, but depends on the backup chain.
                        DIFFERENTIAL: copies files changed since the latest full backup. Larger than partial, simpler to restore."""))
                .setSaveConsumer(value -> config.backupMode = value)
                .build());

        backups.addEntry(entries.startBooleanToggle(
                        Component.literal("Automatic backups"),
                        config.automaticBackupsEnabled
                )
                .setDefaultValue(defaults.automaticBackupsEnabled)
                .setTooltip(Component.literal("When enabled, the server creates backups automatically at the configured interval."))
                .setSaveConsumer(value -> config.automaticBackupsEnabled = value)
                .build());

        backups.addEntry(entries.startBooleanToggle(
                        Component.literal("Pause automatic backups when no players joined"),
                        config.pauseAutomaticBackupsWithoutPlayers
                )
                .setDefaultValue(defaults.pauseAutomaticBackupsWithoutPlayers)
                .setTooltip(Component.literal("Skips scheduled automatic backups if no player has been online since the last backup."))
                .setSaveConsumer(value -> config.pauseAutomaticBackupsWithoutPlayers = value)
                .build());

        backups.addEntry(entries.startBooleanToggle(
                        Component.literal("Backup when world starts"),
                        config.backupOnServerStart
                )
                .setDefaultValue(defaults.backupOnServerStart)
                .setTooltip(Component.literal("Creates one backup when the server finishes starting the world. Disabled by default."))
                .setSaveConsumer(value -> config.backupOnServerStart = value)
                .build());

        backups.addEntry(entries.startBooleanToggle(
                        Component.literal("Backup when world closes"),
                        config.backupOnServerStop
                )
                .setDefaultValue(defaults.backupOnServerStop)
                .setTooltip(Component.literal("Creates one backup while the server is shutting down. Shutdown waits for it to finish. Disabled by default."))
                .setSaveConsumer(value -> config.backupOnServerStop = value)
                .build());

        backups.addEntry(entries.startIntField(
                        Component.literal("Automatic interval minutes"),
                        config.automaticIntervalMinutes
                )
                .setDefaultValue(defaults.automaticIntervalMinutes)
                .setMin(1)
                .setTooltip(Component.literal("Minutes between automatic backups. The timer resets when this value is saved."))
                .setSaveConsumer(value -> config.automaticIntervalMinutes = value)
                .build());

        backups.addEntry(entries.startStrField(
                        Component.literal("Backup directory"),
                        config.backupDirectory
                )
                .setDefaultValue(defaults.backupDirectory)
                .setTooltip(Component.literal("Directory where backups are stored. Relative paths are resolved from the game directory."))
                .setSaveConsumer(value -> config.backupDirectory = value)
                .build());

        permissions.addEntry(entries.startIntField(
                        Component.literal("Command permission level"),
                        config.commandPermissionLevel
                )
                .setDefaultValue(defaults.commandPermissionLevel)
                .setMin(0)
                .setMax(4)
                .setTooltip(Component.literal("""
                        Minimum Minecraft permission level required to use /advancedbackups.
                        0: anyone can use it.
                        1: low-level permissions.
                        2: operators/gamemasters. Default.
                        3: admins.
                        4: server owners/highest permission level."""))
                .setSaveConsumer(value -> config.commandPermissionLevel = value)
                .build());

        integrity.addEntry(entries.startEnumSelector(
                        Component.literal("Integrity mode"),
                        BackupIntegrityMode.class,
                        config.integrityMode
                )
                .setDefaultValue(defaults.integrityMode)
                .setTooltip(Component.literal("""
                        STRICT: failed backups are discarded and damaged restores are blocked.
                        PERMISSIVE: partial backups may remain, but damaged restores are blocked.
                        VERY_PERMISSIVE: damaged restores can continue with a warning. Use only for manual recovery."""))
                .setSaveConsumer(value -> config.integrityMode = value)
                .build());

        retention.addEntry(entries.startIntField(
                        Component.literal("Keep full backups"),
                        config.retention.full
                )
                .setDefaultValue(defaults.retention.full)
                .setMin(1)
                .setTooltip(Component.literal("Maximum number of full backups to keep. At least one full backup is always retained."))
                .setSaveConsumer(value -> config.retention.full = value)
                .build());

        retention.addEntry(entries.startIntField(
                        Component.literal("Keep partial backups"),
                        config.retention.incremental
                )
                .setDefaultValue(defaults.retention.incremental)
                .setMin(0)
                .setTooltip(Component.literal("Maximum number of partial backups to keep. Required base backups are protected."))
                .setSaveConsumer(value -> config.retention.incremental = value)
                .build());

        retention.addEntry(entries.startIntField(
                        Component.literal("Keep differential backups"),
                        config.retention.differential
                )
                .setDefaultValue(defaults.retention.differential)
                .setMin(0)
                .setTooltip(Component.literal("Maximum number of differential backups to keep. Required full backups are protected."))
                .setSaveConsumer(value -> config.retention.differential = value)
                .build());

        popup.addEntry(entries.startBooleanToggle(
                        Component.literal("Show backup popup"),
                        config.popup.enabled
                )
                .setDefaultValue(defaults.popup.enabled)
                .setTooltip(Component.literal("Shows the backup progress HUD on clients with the mod installed."))
                .setSaveConsumer(value -> config.popup.enabled = value)
                .build());

        popup.addEntry(new PopupEditorEntry(config));

        popup.addEntry(entries.startStrField(Component.literal("Title"), config.popup.title)
                .setDefaultValue(defaults.popup.title)
                .setTooltip(Component.literal("Title shown at the top of the backup popup."))
                .setSaveConsumer(value -> config.popup.title = value)
                .build());

        popup.addEntry(entries.startStrField(Component.literal("Running text"), config.popup.runningText)
                .setDefaultValue(defaults.popup.runningText)
                .setTooltip(Component.literal("Supports {reason}, {type}, {percent}, {bytesWritten}, and {totalBytes}."))
                .setSaveConsumer(value -> config.popup.runningText = value)
                .build());

        popup.addEntry(entries.startStrField(Component.literal("Completed text"), config.popup.completedText)
                .setDefaultValue(defaults.popup.completedText)
                .setTooltip(Component.literal("Supports {reason}, {type}, {percent}, {bytesWritten}, and {totalBytes}."))
                .setSaveConsumer(value -> config.popup.completedText = value)
                .build());

        popup.addEntry(entries.startStrField(Component.literal("Failed text"), config.popup.failedText)
                .setDefaultValue(defaults.popup.failedText)
                .setTooltip(Component.literal("Supports {reason}, {type}, {percent}, {bytesWritten}, and {totalBytes}."))
                .setSaveConsumer(value -> config.popup.failedText = value)
                .build());

        return builder.build();
    }

    private static final class PopupEditorEntry extends TextListEntry {
        private final BackupConfig config;

        private PopupEditorEntry(BackupConfig config) {
            super(
                    Component.literal("Edit popup layout"),
                    Component.literal("Click to drag the popup preview and adjust colors with RGBA controls.")
            );
            this.config = config;
        }

        @Override
        public void extractRenderState(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                int x,
                int y,
                int entryWidth,
                int entryHeight,
                int itemHeight,
                boolean hovered,
                float tickDelta
        ) {
            super.extractRenderState(graphics, mouseX, mouseY, x, y, entryWidth, entryHeight, itemHeight, hovered, tickDelta);
            if (hovered) {
                graphics.text(Minecraft.getInstance().font, Component.literal(">"), x + entryWidth - 12, y + 6, 0xFFFFFFFF, true);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new PopupEditorScreen(minecraft.screen, config));
            return true;
        }
    }
}
