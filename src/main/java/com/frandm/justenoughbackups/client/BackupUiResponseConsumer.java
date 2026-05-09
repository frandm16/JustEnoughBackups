package com.frandm.justenoughbackups.client;

import com.frandm.justenoughbackups.network.BackupUiResponsePayload;

public interface BackupUiResponseConsumer {
    void handleResponse(BackupUiResponsePayload payload);

    void setStatus(boolean ok, String message);
}
