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
import java.util.ArrayList;
import java.util.List;

public final class BackupConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile BackupConfig current;

    public BackupType backupMode = BackupType.FULL;
    public boolean pauseAutomaticBackupsWithoutPlayers = true;
    public boolean backupOnServerStart = false;
    public boolean backupOnServerStop = false;
    public boolean automaticBackupsEnabled = true; // not in use anymore
    public int automaticIntervalMinutes = 60; // not in use anymore
    public AutomaticSchedule automaticSchedule;
    public boolean automaticBackupWarningEnabled = true;
    public int automaticBackupWarningMinutes = 5;
    public int commandPermissionLevel = 2;
    public BackupMessageChannel messageChannel = BackupMessageChannel.ACTION_BAR;
    public BackupIntegrityMode integrityMode = BackupIntegrityMode.STRICT;
    public boolean includeSummaryFile = false;
    public int minimumFreeSpaceReserveMb = 512;
    public int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    public Retention retention = new Retention();
    public Popup popup = new Popup();
    public String backupDirectory = BackupConstants.DEFAULT_BACKUP_DIRECTORY;
    public String tempBackupDirectory = "";
    public List<String> excludedPaths = new ArrayList<>();

    public static BackupConfig get() {
        BackupConfig config = current;
        if (config == null) {
            config = reload();
        }
        return config;
    }

    public static BackupConfig defaults() {
        BackupConfig config = new BackupConfig();
        config.automaticSchedule = new AutomaticSchedule();
        config.normalize();
        return config;
    }

    public static BackupConfig reload() {
        Path configFile = configFile();
        BackupConfig config = new BackupConfig();
        boolean persist = !Files.exists(configFile);

        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                BackupConfig loaded = GSON.fromJson(reader, BackupConfig.class);
                if (loaded != null) {
                    config = loaded;
                    persist = true;
                }
            } catch (IOException | RuntimeException exception) {
                WorldBackupMod.LOGGER.error("Failed to read Just Enough Backups config. Defaults will be used in memory, but the file will not be overwritten.", exception);
            }
        }

        config.normalize();
        current = config;
        if (persist) {
            save(config);
        }
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
        copy.pauseAutomaticBackupsWithoutPlayers = pauseAutomaticBackupsWithoutPlayers;
        copy.backupOnServerStart = backupOnServerStart;
        copy.backupOnServerStop = backupOnServerStop;
        copy.automaticIntervalMinutes = automaticIntervalMinutes;
        copy.automaticSchedule = automaticSchedule.copy();
        copy.automaticBackupWarningEnabled = automaticBackupWarningEnabled;
        copy.automaticBackupWarningMinutes = automaticBackupWarningMinutes;
        copy.commandPermissionLevel = commandPermissionLevel;
        copy.messageChannel = messageChannel;
        copy.integrityMode = integrityMode;
        copy.includeSummaryFile = includeSummaryFile;
        copy.minimumFreeSpaceReserveMb = minimumFreeSpaceReserveMb;
        copy.threadCount = threadCount;
        copy.backupDirectory = backupDirectory;
        copy.tempBackupDirectory = tempBackupDirectory;
        copy.retention = retention.copy();
        copy.popup = popup.copy();
        copy.excludedPaths = new ArrayList<>(excludedPaths);
        return copy;
    }

    public static void setCurrent(BackupConfig config) {
        current = config;
    }

    public Path resolveBackupRoot() {
        Path configured = Path.of(backupDirectory);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        try {
            if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getGameDir() != null) {
                return FabricLoader.getInstance().getGameDir().resolve(configured).normalize();
            }
        } catch (Throwable ignored) {
        }
        return configured.toAbsolutePath().normalize();
    }

    public Path resolveTempRoot() {
        if (tempBackupDirectory == null || tempBackupDirectory.isBlank()) {
            return resolveBackupRoot();
        }

        Path configured = Path.of(tempBackupDirectory);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        try {
            if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getGameDir() != null) {
                return FabricLoader.getInstance().getGameDir().resolve(configured).normalize();
            }
        } catch (Throwable ignored) {
        }
        return configured.toAbsolutePath().normalize();
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
        try {
            if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
                return FabricLoader.getInstance().getConfigDir().resolve("justenoughbackups.json");
            }
        } catch (Throwable ignored) {
        }
        return Path.of("config", "justenoughbackups.json");
    }

    private void normalize() {
        if (backupMode == null) {
            backupMode = BackupType.FULL;
        }

        if (automaticSchedule == null) {
            automaticSchedule = new AutomaticSchedule();

            automaticSchedule.full = new ScheduledBackup(false, 240);
            automaticSchedule.differential = new ScheduledBackup(false, 120);
            automaticSchedule.partial = new ScheduledBackup(false, 60);

            int legacyInterval = Math.max(1, automaticIntervalMinutes);

            ScheduledBackup legacy = automaticSchedule.forType(backupMode);
            legacy.enabled = automaticBackupsEnabled;
            legacy.intervalMinutes = legacyInterval;
        } else {
            automaticSchedule.normalize();
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
        if (tempBackupDirectory == null) {
            tempBackupDirectory = "";
        }
        minimumFreeSpaceReserveMb = Math.max(0, minimumFreeSpaceReserveMb);
        threadCount = Math.clamp(threadCount, 1, Math.max(1, Runtime.getRuntime().availableProcessors()));
        if (retention == null) {
            retention = new Retention();
        }
        retention.normalize();
        if (popup == null) {
            popup = new Popup();
        }
        popup.normalize();

        if (excludedPaths == null) {
            excludedPaths = new ArrayList<>();
        } else {
            excludedPaths = excludedPaths.stream()
                    .map(BackupConfig::normalizeExcludedPath)
                    .filter(path -> !path.isBlank())
                    .distinct()
                    .toList();
        }
    }

    private static String normalizeExcludedPath(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static final class AutomaticSchedule {
        public ScheduledBackup full = new ScheduledBackup(true, 240);
        public ScheduledBackup differential = new ScheduledBackup(false, 120);
        public ScheduledBackup partial = new ScheduledBackup(false, 60);

        public ScheduledBackup forType(BackupType type) {
            return switch (type) {
                case FULL -> full;
                case DIFFERENTIAL -> differential;
                case PARTIAL -> partial;
            };
        }

        public AutomaticSchedule copy() {
            AutomaticSchedule copy = new AutomaticSchedule();
            copy.full = full.copy();
            copy.differential = differential.copy();
            copy.partial = partial.copy();
            return copy;
        }

        public void normalize() {
            if (full == null) full = new ScheduledBackup(true, 240);
            if (differential == null) differential = new ScheduledBackup(false, 120);
            if (partial == null) partial = new ScheduledBackup(false, 60);

            full.normalize();
            differential.normalize();
            partial.normalize();
        }
    }

    public static final class ScheduledBackup {
        public boolean enabled;
        public int intervalMinutes;

        public ScheduledBackup(boolean enabled, int intervalMinutes) {
            this.enabled = enabled;
            this.intervalMinutes = intervalMinutes;
        }

        public ScheduledBackup copy() {
            return new ScheduledBackup(enabled, intervalMinutes);
        }

        private void normalize() {
            intervalMinutes = Math.max(1, intervalMinutes);
        }
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
        private static final String DEFAULT_BG = "0xAA101010";
        private static final String DEFAULT_RUNNING = "0xFF55FFFF";
        private static final String DEFAULT_COMPLETED = "0xFF55FF55";
        private static final String DEFAULT_FAILED = "0xFFFF5555";
        private static final String DEFAULT_TEXT = "0xFFE0E0E0";
        private static final String DEFAULT_TITLE = "Just Enough Backups";
        private static final String DEFAULT_RUNNING_TEXT = "Running {reason} {type}";
        private static final String DEFAULT_SCANNING_TEXT = "Scanning {reason} {type}";
        private static final String DEFAULT_COMPLETED_TEXT = "Completed {reason} {type}";
        private static final String DEFAULT_FAILED_TEXT = "Unable to Backup";

        public boolean enabled = true;
        public boolean showTitle = true;
        public boolean centerText = true;
        public boolean showBorder = true;
        public int x = 8;
        public int y = 8;
        public double xRatio = -1.0D;
        public double yRatio = -1.0D;
        public String backgroundColor = DEFAULT_BG;
        public String runningColor = DEFAULT_RUNNING;
        public String completedColor = DEFAULT_COMPLETED;
        public String failedColor = DEFAULT_FAILED;
        public String textColor = DEFAULT_TEXT;
        public String title = DEFAULT_TITLE;
        public String runningText = DEFAULT_RUNNING_TEXT;
        public String scanningText = DEFAULT_SCANNING_TEXT;
        public String completedText = DEFAULT_COMPLETED_TEXT;
        public String failedText = DEFAULT_FAILED_TEXT;

        public int backgroundColorArgb() {
            return ConfigColor.parseOrDefault(backgroundColor, DEFAULT_BG);
        }

        public int runningColorArgb() {
            return ConfigColor.parseOrDefault(runningColor, DEFAULT_RUNNING);
        }

        public int completedColorArgb() {
            return ConfigColor.parseOrDefault(completedColor, DEFAULT_COMPLETED);
        }

        public int failedColorArgb() {
            return ConfigColor.parseOrDefault(failedColor, DEFAULT_FAILED);
        }

        public int textColorArgb() {
            return ConfigColor.parseOrDefault(textColor, DEFAULT_TEXT);
        }

        private void normalize() {
            x = Math.max(0, x);
            y = Math.max(0, y);
            xRatio = normalizeRatio(xRatio);
            yRatio = normalizeRatio(yRatio);
            backgroundColor = normalizeColor(backgroundColor, DEFAULT_BG);
            runningColor = normalizeColor(runningColor, DEFAULT_RUNNING);
            completedColor = normalizeColor(completedColor, DEFAULT_COMPLETED);
            failedColor = normalizeColor(failedColor, DEFAULT_FAILED);
            textColor = normalizeColor(textColor, DEFAULT_TEXT);
            title = normalizeText(title, DEFAULT_TITLE);
            runningText = normalizeText(runningText, DEFAULT_RUNNING_TEXT);
            scanningText = normalizeText(scanningText, DEFAULT_SCANNING_TEXT);
            completedText = normalizeText(completedText, DEFAULT_COMPLETED_TEXT);
            failedText = normalizeText(failedText, DEFAULT_FAILED_TEXT);
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
            copy.scanningText = scanningText;
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
