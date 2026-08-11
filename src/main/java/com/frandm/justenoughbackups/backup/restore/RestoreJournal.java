package com.frandm.justenoughbackups.backup.restore;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.BackupConstants;
import com.frandm.justenoughbackups.backup.model.BackupIntegrityMode;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.RestoreIntent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class RestoreJournal {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String INTENT_SUFFIX = ".json";

    private RestoreJournal() {
    }

    static Path intentPath(Path worldParent, Path worldPath) {
        return worldParent.resolve(BackupConstants.RESTORE_INTENT_PREFIX + worldPath.getFileName() + INTENT_SUFFIX);
    }

    static void write(RestoreIntent intent) throws IOException {
        Path file = intentPath(intent.worldPath().getParent(), intent.worldPath());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(toData(intent), writer);
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    static Optional<RestoreIntent> read(Path worldParent, Path worldPath) throws IOException {
        Path file = intentPath(worldParent, worldPath);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            IntentData data = GSON.fromJson(reader, IntentData.class);
            RestoreIntent intent = fromData(data, worldParent, worldPath);
            if (intent == null) {
                WorldBackupMod.LOGGER.warn("Discarding invalid restore intent: {}", file);
                Files.deleteIfExists(file);
                return Optional.empty();
            }
            return Optional.of(intent);
        } catch (IOException | RuntimeException exception) {
            WorldBackupMod.LOGGER.warn("Unable to read restore intent {}, discarding it.", file, exception);
            Files.deleteIfExists(file);
            return Optional.empty();
        }
    }

    static List<RestoreIntent> readAll(Path worldParent) throws IOException {
        List<RestoreIntent> intents = new ArrayList<>();
        if (!Files.isDirectory(worldParent)) {
            return intents;
        }
        try (var stream = Files.list(worldParent)) {
            for (Path file : stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(BackupConstants.RESTORE_INTENT_PREFIX) && name.endsWith(INTENT_SUFFIX);
                    })
                    .toList()) {
                String worldName = file.getFileName().toString()
                        .substring(BackupConstants.RESTORE_INTENT_PREFIX.length(),
                                file.getFileName().toString().length() - INTENT_SUFFIX.length());
                Path worldPath = worldParent.resolve(worldName);
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    RestoreIntent intent = fromData(GSON.fromJson(reader, IntentData.class), worldParent, worldPath);
                    if (intent != null) {
                        intents.add(intent);
                    } else {
                        WorldBackupMod.LOGGER.warn("Discarding invalid restore intent: {}", file);
                        Files.deleteIfExists(file);
                    }
                } catch (IOException | RuntimeException exception) {
                    WorldBackupMod.LOGGER.warn("Unable to read restore intent {}, discarding it.", file, exception);
                    Files.deleteIfExists(file);
                }
            }
        }
        return intents;
    }

    static void delete(Path worldParent, Path worldPath) throws IOException {
        Files.deleteIfExists(intentPath(worldParent, worldPath));
    }

    private static IntentData toData(RestoreIntent intent) {
        IntentData data = new IntentData();
        data.version = intent.version();
        data.backupId = intent.backupId();
        data.worldPath = intent.worldPath().toString();
        data.stagingPath = intent.stagingPath().toString();
        data.oldWorldPath = intent.oldWorldPath() == null ? null : intent.oldWorldPath().toString();
        data.state = intent.state().name();
        data.integrityMode = intent.integrityMode().name();
        data.strictSnapshotVerification = intent.strictSnapshotVerification();
        data.snapshot = intent.snapshot();
        return data;
    }

    private static RestoreIntent fromData(IntentData data, Path worldParent, Path worldPath) {
        if (data == null
                || data.version != RestoreIntent.CURRENT_VERSION
                || data.backupId == null
                || data.stagingPath == null
                || data.state == null
                || data.integrityMode == null
                || data.snapshot == null) {
            return null;
        }
        RestoreIntent.RestoreState state;
        try {
            state = RestoreIntent.RestoreState.valueOf(data.state);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        BackupIntegrityMode integrityMode;
        try {
            integrityMode = BackupIntegrityMode.valueOf(data.integrityMode);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (!data.worldPath.equals(worldPath.toAbsolutePath().normalize().toString())) {
            return null;
        }
        return new RestoreIntent(
                data.version,
                data.backupId,
                Path.of(data.worldPath),
                Path.of(data.stagingPath),
                data.oldWorldPath == null ? null : Path.of(data.oldWorldPath),
                state,
                integrityMode,
                data.strictSnapshotVerification,
                data.snapshot
        );
    }

    private static final class IntentData {
        int version;
        String backupId;
        String worldPath;
        String stagingPath;
        String oldWorldPath;
        String state;
        String integrityMode;
        boolean strictSnapshotVerification;
        Map<String, BackupManifest.FileState> snapshot;
    }
}
