package com.frandm.advancedbackups.client.input;

import com.frandm.advancedbackups.client.BackupUiClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class BackupKeyBindings {
    private static KeyMapping backupMenu;

    private BackupKeyBindings() {
    }

    public static void register() {
        backupMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.advancedbackups.backup_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (backupMenu.consumeClick()) {
                if (client.screen == null) {
                    BackupUiClient.openScreen();
                }
            }
        });
    }
}
