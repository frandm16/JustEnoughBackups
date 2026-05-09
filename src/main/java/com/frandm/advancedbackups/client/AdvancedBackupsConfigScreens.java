package com.frandm.advancedbackups.client;

import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

public final class AdvancedBackupsConfigScreens {
    private AdvancedBackupsConfigScreens() {
    }

    public static @Nullable Screen create(Screen parent) {
        return new AdvancedBackupsConfigScreen(parent);
    }
}
