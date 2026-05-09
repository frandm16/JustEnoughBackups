package com.frandm.advancedbackups.client;

import com.frandm.advancedbackups.network.BackupUiResponsePayload;

public interface BackupUiResponseConsumer {
    void handleResponse(BackupUiResponsePayload payload);

    void setStatus(boolean ok, String message);
}
