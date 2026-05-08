package com.frandm.advancedbackups.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class AdvancedBackupsConfigScreens {
    private static final boolean CLOTH_CONFIG_LOADED = FabricLoader.getInstance().isModLoaded("cloth-config")
            || FabricLoader.getInstance().isModLoaded("cloth-config2")
            || FabricLoader.getInstance().isModLoaded("cloth_config")
            || FabricLoader.getInstance().isModLoaded("cloth_config2");

    private AdvancedBackupsConfigScreens() {
    }

    public static @Nullable Screen create(Screen parent) {
        if (CLOTH_CONFIG_LOADED) {
            return AdvancedBackupsClothConfigScreen.create(parent);
        }

        return new AlertScreen(
                () -> Minecraft.getInstance().setScreen(parent),
                Component.literal("Advanced Backups"),
                Component.literal("Install Cloth Config API to edit this config from Mod Menu. You can still edit config/advancedbackups.json manually.")
        );
    }
}
