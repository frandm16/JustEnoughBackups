package com.frandm.justenoughbackups;

import com.frandm.justenoughbackups.backup.BackupService;
import com.frandm.justenoughbackups.backup.progress.BackupProgressBroadcaster;
import com.frandm.justenoughbackups.command.BackupCommand;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.frandm.justenoughbackups.network.BackupUiNetworking;
import com.frandm.justenoughbackups.scheduler.BackupScheduler;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldBackupMod implements ModInitializer {
    public static final String MOD_ID = "justenoughbackups";
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
        LOGGER.info("Just Enough Backups loaded.");
    }
}
