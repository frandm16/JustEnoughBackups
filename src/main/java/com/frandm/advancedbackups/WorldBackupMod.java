package com.frandm.advancedbackups;

import com.frandm.advancedbackups.backup.BackupService;
import com.frandm.advancedbackups.command.BackupCommand;
import com.frandm.advancedbackups.config.BackupConfig;
import com.frandm.advancedbackups.scheduler.BackupScheduler;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldBackupMod implements ModInitializer {
    public static final String MOD_ID = "advancedbackups";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BackupConfig.reload();
        BackupCommand.register();
        BackupScheduler.register();
        BackupService.registerRestoreHandler();
        LOGGER.info("Advanced Backups loaded.");
    }
}
