package com.hdp.connectorregistry.signer;

public interface RequestSigner {
    SignerResult sign(SignerContext context);
}
