package com.hdp.connectorregistry.signer;

public final class SignerRegistry {
    public RequestSigner instantiate(String className) {
        try {
            Class<?> signerClass = Class.forName(className);
            if (!RequestSigner.class.isAssignableFrom(signerClass)) {
                throw new IllegalStateException("Class does not implement RequestSigner: " + className);
            }
            return (RequestSigner) signerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to instantiate signer: " + className, exception);
        }
    }
}
