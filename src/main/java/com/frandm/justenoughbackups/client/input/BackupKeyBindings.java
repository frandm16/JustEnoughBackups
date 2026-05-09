package com.frandm.justenoughbackups.client.input;

import com.frandm.justenoughbackups.client.JEBConfigScreens;
import com.frandm.justenoughbackups.client.BackupUiClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class BackupKeyBindings {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("justenoughbackups", "just_enough_backups"));
    private static KeyMapping backupMenu;
    private static KeyMapping configMenu;

    private BackupKeyBindings() {
    }

    public static void register() {
        backupMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.justenoughbackups.backup_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        ));
        configMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.justenoughbackups.config_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (backupMenu.consumeClick()) {
                if (client.screen == null) {
                    BackupUiClient.openScreen();
                }
            }
            while (configMenu.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(JEBConfigScreens.create(null));
                }
            }
        });
    }
}
