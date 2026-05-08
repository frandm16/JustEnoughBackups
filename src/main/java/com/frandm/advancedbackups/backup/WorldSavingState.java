package com.frandm.advancedbackups.backup;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WorldSavingState {
    private final Map<ResourceKey<Level>, Boolean> previousStates;

    private WorldSavingState(Map<ResourceKey<Level>, Boolean> previousStates) {
        this.previousStates = previousStates;
    }

    public static WorldSavingState captureAndDisable(MinecraftServer server) {
        Map<ResourceKey<Level>, Boolean> states = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            states.put(level.dimension(), level.noSave);
            level.noSave = true;
        }
        return new WorldSavingState(states);
    }

    public void restore(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Boolean previous = previousStates.get(level.dimension());
            if (previous != null) {
                level.noSave = previous;
            }
        }
    }
}
