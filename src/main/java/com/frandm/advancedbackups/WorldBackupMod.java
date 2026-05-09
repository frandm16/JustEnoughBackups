package com.frandm.advancedbackups;

import com.frandm.advancedbackups.backup.BackupService;
import com.frandm.advancedbackups.backup.progress.BackupProgressBroadcaster;
import com.frandm.advancedbackups.command.BackupCommand;
import com.frandm.advancedbackups.config.BackupConfig;
import com.frandm.advancedbackups.network.BackupUiNetworking;
import com.frandm.advancedbackups.scheduler.BackupScheduler;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldBackupMod implements ModInitializer {
    public static final String MOD_ID = "advancedbackups";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BackupProgressBroadcaster.registerPayloadType();
        BackupUiNetworking.registerPayloadTypes();
        BackupConfig.reload();
        BackupCommand.register();
        BackupUiNetworking.registerServerReceivers();
        BackupScheduler.register();
        BackupService.registerRestoreHandler();
        LOGGER.info("Advanced Backups loaded.");
    }
}
