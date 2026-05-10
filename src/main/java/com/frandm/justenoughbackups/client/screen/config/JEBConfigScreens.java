package com.frandm.justenoughbackups.client.screen.config;

import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

public final class JEBConfigScreens {
    private JEBConfigScreens() {
    }

    public static @Nullable Screen create(Screen parent) {
        return new JEBConfigScreen(parent);
    }
}
