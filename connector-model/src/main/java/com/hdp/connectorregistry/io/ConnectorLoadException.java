package com.hdp.connectorregistry.io;

public final class ConnectorLoadException extends IllegalStateException {
    public ConnectorLoadException(String message) {
        super(message);
    }

    public ConnectorLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
