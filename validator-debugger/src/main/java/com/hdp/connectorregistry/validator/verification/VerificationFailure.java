package com.hdp.connectorregistry.validator.verification;

public final class VerificationFailure extends RuntimeException {
    public VerificationFailure(String message) {
        super(message);
    }

    public VerificationFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
