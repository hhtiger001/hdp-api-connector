package com.hdp.connectorregistry.io;

public final class SchemaResolutionException extends IllegalStateException {
    public SchemaResolutionException(String message) {
        super(message);
    }

    public SchemaResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
