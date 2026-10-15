package com.frandm.justenoughbackups.backup.storage.remote;

import java.io.IOException;
import java.io.EOFException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeoutException;

public final class RemoteStorageException extends IOException {
    private final boolean retryable;

    public RemoteStorageException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static boolean isRetryable(IOException failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RemoteStorageException remoteFailure) {
                return remoteFailure.isRetryable();
            }
        }
        return false;
    }

    public static boolean isTransientNetworkFailure(Throwable failure) {
        return hasCause(failure, SocketTimeoutException.class)
                || hasCause(failure, HttpTimeoutException.class)
                || hasCause(failure, ConnectException.class)
                || hasCause(failure, SocketException.class)
                || hasCause(failure, EOFException.class)
                || hasCause(failure, ClosedChannelException.class)
                || hasCause(failure, TimeoutException.class);
    }

    private static <T extends Throwable> boolean hasCause(Throwable failure, Class<T> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }
}
