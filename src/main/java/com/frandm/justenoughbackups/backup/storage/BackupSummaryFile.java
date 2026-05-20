package com.frandm.justenoughbackups.backup.storage;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;

import java.util.List;
import java.util.Locale;

final class BackupSummaryFile {
    private BackupSummaryFile() {
    }

    static String build(BackupManifest manifest) {
        String jebVersion = versionFor(WorldBackupMod.MOD_ID);
        String loaderVersion = versionFor("fabricloader");
        List<String> mods = FabricLoader.getInstance().getAllMods().stream()
                .map(container -> container.getMetadata().getId() + " " + container.getMetadata().getVersion().getFriendlyString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        StringBuilder builder = new StringBuilder();
        line(builder, "World", value(manifest.worldName));
        line(builder, "World Folder", value(manifest.worldDirectoryName));
        line(builder, "Created At", value(manifest.createdAt));
        line(builder, "Backup Type", manifest.type == null ? "unknown" : manifest.type.commandName().toLowerCase(Locale.ROOT));
        line(builder, "Reason", value(manifest.reason));
        line(builder, "Minecraft Version", SharedConstants.getCurrentVersion().name());
        line(builder, "Loader", "Fabric Loader " + loaderVersion);
        line(builder, "JustEnoughBackups Version", jebVersion);
        builder.append(System.lineSeparator());
        builder.append("Mods (").append(mods.size()).append("):").append(System.lineSeparator());
        for (String mod : mods) {
            builder.append("- ").append(mod).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String versionFor(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static void line(StringBuilder builder, String label, String value) {
        builder.append(label).append(": ").append(value).append(System.lineSeparator());
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
