package com.frandm.advancedbackups.config;

import com.frandm.advancedbackups.WorldBackupMod;
import com.frandm.advancedbackups.backup.BackupConstants;
import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.scheduler.BackupScheduler;
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
    public boolean backupOnServerStart = false;
    public boolean backupOnServerStop = false;
    public int automaticIntervalMinutes = 15;
    public Retention retention = new Retention();
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
                WorldBackupMod.LOGGER.error("Failed to read advanced backups config. Defaults will be used.", exception);
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
        copy.backupOnServerStart = backupOnServerStart;
        copy.backupOnServerStop = backupOnServerStop;
        copy.automaticIntervalMinutes = automaticIntervalMinutes;
        copy.backupDirectory = backupDirectory;
        copy.retention = retention.copy();
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
            WorldBackupMod.LOGGER.error("Failed to write advanced backups config.", exception);
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("advancedbackups.json");
    }

    private void normalize() {
        if (backupMode == null) {
            backupMode = BackupType.FULL;
        }
        if (automaticIntervalMinutes < 1) {
            automaticIntervalMinutes = defaults().automaticIntervalMinutes;
        }
        if (backupDirectory == null || backupDirectory.isBlank()) {
            backupDirectory = BackupConstants.DEFAULT_BACKUP_DIRECTORY;
        }
        if (retention == null) {
            retention = new Retention();
        }
        retention.normalize();
    }

    public static final class Retention {
        public int full = 5;
        public int incremental = 20;
        public int differential = 10;

        private void normalize() {
            full = Math.max(1, full);
            incremental = Math.max(0, incremental);
            differential = Math.max(0, differential);
        }

        private Retention copy() {
            Retention copy = new Retention();
            copy.full = full;
            copy.incremental = incremental;
            copy.differential = differential;
            return copy;
        }
    }
}
