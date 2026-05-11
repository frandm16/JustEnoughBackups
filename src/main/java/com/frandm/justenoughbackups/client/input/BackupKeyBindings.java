package com.frandm.justenoughbackups.client.input;

import com.frandm.justenoughbackups.client.net.BackupUiClient;
import com.frandm.justenoughbackups.client.screen.config.JEBConfigScreens;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class BackupKeyBindings {

    private static final String CATEGORY = "key.categories.justenoughbackups";
    private static KeyMapping backupMenu;
    private static KeyMapping configMenu;

    private BackupKeyBindings() {
    }

    public static void register() {

        backupMenu = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.justenoughbackups.backup_menu",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_B,
                        CATEGORY
                )
        );

        configMenu = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.justenoughbackups.config_menu",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_N,
                        CATEGORY
                )
        );

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