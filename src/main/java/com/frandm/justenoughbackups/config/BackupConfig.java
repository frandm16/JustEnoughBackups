package com.frandm.justenoughbackups.config;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.BackupConstants;
import com.frandm.justenoughbackups.backup.BackupMessageChannel;
import com.frandm.justenoughbackups.backup.model.BackupIntegrityMode;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.scheduler.BackupScheduler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BackupConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile BackupConfig current;

    public BackupType backupMode = BackupType.FULL;
    public boolean automaticBackupsEnabled = true;
    public boolean pauseAutomaticBackupsWithoutPlayers = true;
    public boolean backupOnServerStart = false;
    public boolean backupOnServerStop = false;
    public int automaticIntervalMinutes = 60;
    public boolean automaticBackupWarningEnabled = true;
    public int automaticBackupWarningMinutes = 5;
    public int commandPermissionLevel = 2;
    public BackupMessageChannel messageChannel = BackupMessageChannel.ACTION_BAR;
    public BackupIntegrityMode integrityMode = BackupIntegrityMode.STRICT;
    public boolean includeSummaryFile = false;
    public int minimumFreeSpaceReserveMb = 512;
    public Retention retention = new Retention();
    public Popup popup = new Popup();
    public String backupDirectory = BackupConstants.DEFAULT_BACKUP_DIRECTORY;

    public static BackupConfig get() {
        BackupConfig config = current;
        if (config == null) {
            config = reload();
        }
        return config;
    }

    public static BackupConfig defaults() {
        return new BackupConfig();
    }

    public static BackupConfig reload() {
        Path configFile = configFile();
        BackupConfig config = new BackupConfig();

        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                BackupConfig loaded = GSON.fromJson(reader, BackupConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException | RuntimeException exception) {
                WorldBackupMod.LOGGER.error("Failed to read Just Enough Backups config. Defaults will be used.", exception);
            }
        }

        config.normalize();
        current = config;
        save(config);
        return config;
    }

    public static BackupConfig saveAndApply(BackupConfig config) {
        config.normalize();
        current = config;
        save(config);
        BackupScheduler.resetTimer();
        return config;
    }

    public BackupConfig copy() {
        BackupConfig copy = new BackupConfig();
        copy.backupMode = backupMode;
        copy.automaticBackupsEnabled = automaticBackupsEnabled;
        copy.pauseAutomaticBackupsWithoutPlayers = pauseAutomaticBackupsWithoutPlayers;
        copy.backupOnServerStart = backupOnServerStart;
        copy.backupOnServerStop = backupOnServerStop;
        copy.automaticIntervalMinutes = automaticIntervalMinutes;
        copy.automaticBackupWarningEnabled = automaticBackupWarningEnabled;
        copy.automaticBackupWarningMinutes = automaticBackupWarningMinutes;
        copy.commandPermissionLevel = commandPermissionLevel;
        copy.messageChannel = messageChannel;
        copy.integrityMode = integrityMode;
        copy.includeSummaryFile = includeSummaryFile;
        copy.minimumFreeSpaceReserveMb = minimumFreeSpaceReserveMb;
        copy.backupDirectory = backupDirectory;
        copy.retention = retention.copy();
        copy.popup = popup.copy();
        return copy;
    }

    public Path resolveBackupRoot() {
        Path configured = Path.of(backupDirectory);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        return FabricLoader.getInstance().getGameDir().resolve(configured).normalize();
    }

    private static void save(BackupConfig config) {
        Path configFile = configFile();
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            WorldBackupMod.LOGGER.error("Failed to write Just Enough Backups config.", exception);
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("justenoughbackups.json");
    }

    private void normalize() {
        if (backupMode == null) {
            backupMode = BackupType.FULL;
        }
        if (automaticIntervalMinutes < 1) {
            automaticIntervalMinutes = defaults().automaticIntervalMinutes;
        }
        if (automaticBackupWarningMinutes < 1) {
            automaticBackupWarningMinutes = defaults().automaticBackupWarningMinutes;
        }
        commandPermissionLevel = Math.clamp(commandPermissionLevel, 0, 4);
        if (messageChannel == null) {
            messageChannel = BackupMessageChannel.ACTION_BAR;
        }
        if (integrityMode == null) {
            integrityMode = BackupIntegrityMode.STRICT;
        }
        if (backupDirectory == null || backupDirectory.isBlank()) {
            backupDirectory = BackupConstants.DEFAULT_BACKUP_DIRECTORY;
        }
        minimumFreeSpaceReserveMb = Math.max(0, minimumFreeSpaceReserveMb);
        if (retention == null) {
            retention = new Retention();
        }
        retention.normalize();
        if (popup == null) {
            popup = new Popup();
        }
        popup.normalize();
    }

    public static final class Retention {
        public int full = 5;
        public int incremental = 20;
        public int differential = 10;
        public int maxTotalSizeMb = 0;

        private void normalize() {
            full = Math.max(1, full);
            incremental = Math.max(0, incremental);
            differential = Math.max(0, differential);
            maxTotalSizeMb = Math.max(0, maxTotalSizeMb);
        }

        private Retention copy() {
            Retention copy = new Retention();
            copy.full = full;
            copy.incremental = incremental;
            copy.differential = differential;
            copy.maxTotalSizeMb = maxTotalSizeMb;
            return copy;
        }
    }

    public static final class Popup {
        public boolean enabled = true;
        public boolean showTitle = true;
        public boolean centerText = true;
        public boolean showBorder = true;
        public int x = 8;
        public int y = 8;
        public double xRatio = -1.0D;
        public double yRatio = -1.0D;
        public String backgroundColor = "0xAA101010";
        public String runningColor = "0xFF55FFFF";
        public String completedColor = "0xFF55FF55";
        public String failedColor = "0xFFFF5555";
        public String textColor = "0xFFE0E0E0";
        public String title = "Just Enough Backups";
        public String runningText = "Running {reason} {type}";
        public String completedText = "Completed {reason} {type}";
        public String failedText = "Unable to Backup";

        public int backgroundColorArgb() {
            return ConfigColor.parseOrDefault(backgroundColor, defaults().popup.backgroundColor);
        }

        public int runningColorArgb() {
            return ConfigColor.parseOrDefault(runningColor, defaults().popup.runningColor);
        }

        public int completedColorArgb() {
            return ConfigColor.parseOrDefault(completedColor, defaults().popup.completedColor);
        }

        public int failedColorArgb() {
            return ConfigColor.parseOrDefault(failedColor, defaults().popup.failedColor);
        }

        public int textColorArgb() {
            return ConfigColor.parseOrDefault(textColor, defaults().popup.textColor);
        }

        private void normalize() {
            x = Math.max(0, x);
            y = Math.max(0, y);
            xRatio = normalizeRatio(xRatio);
            yRatio = normalizeRatio(yRatio);
            backgroundColor = normalizeColor(backgroundColor, defaults().popup.backgroundColor);
            runningColor = normalizeColor(runningColor, defaults().popup.runningColor);
            completedColor = normalizeColor(completedColor, defaults().popup.completedColor);
            failedColor = normalizeColor(failedColor, defaults().popup.failedColor);
            textColor = normalizeColor(textColor, defaults().popup.textColor);
            title = normalizeText(title, defaults().popup.title);
            runningText = normalizeText(runningText, defaults().popup.runningText);
            completedText = normalizeText(completedText, defaults().popup.completedText);
            failedText = normalizeText(failedText, defaults().popup.failedText);
        }

        public Popup copy() {
            Popup copy = new Popup();
            copy.enabled = enabled;
            copy.showTitle = showTitle;
            copy.centerText = centerText;
            copy.showBorder = showBorder;
            copy.x = x;
            copy.y = y;
            copy.xRatio = xRatio;
            copy.yRatio = yRatio;
            copy.backgroundColor = backgroundColor;
            copy.runningColor = runningColor;
            copy.completedColor = completedColor;
            copy.failedColor = failedColor;
            copy.textColor = textColor;
            copy.title = title;
            copy.runningText = runningText;
            copy.completedText = completedText;
            copy.failedText = failedText;
            return copy;
        }

        private static String normalizeText(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        private static double normalizeRatio(double value) {
            if (!Double.isFinite(value) || value < 0.0D) {
                return -1.0D;
            }
            return Math.clamp(value, 0.0D, 1.0D);
        }

        private static String normalizeColor(String value, String fallback) {
            return ConfigColor.normalize(value, fallback);
        }
    }
}
