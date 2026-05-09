package com.frandm.justenoughbackups.client;

import com.frandm.justenoughbackups.network.BackupUiResponsePayload;
import net.minecraft.network.chat.Component;

public interface BackupUiResponseConsumer {
    void handleResponse(BackupUiResponsePayload payload);

    void setStatus(boolean ok, Component message);
}
