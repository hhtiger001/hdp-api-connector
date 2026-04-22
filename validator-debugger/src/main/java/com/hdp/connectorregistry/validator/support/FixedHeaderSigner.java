package com.hdp.connectorregistry.validator.support;

import com.hdp.connectorregistry.signer.RequestSigner;
import com.hdp.connectorregistry.signer.SignerContext;
import com.hdp.connectorregistry.signer.SignerResult;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FixedHeaderSigner implements RequestSigner {
    @Override
    public SignerResult sign(SignerContext context) {
        String headerName = "X-Signature";
        if (context.signerConfig() != null) {
            Object configuredHeaderName = context.signerConfig().get("headerName");
            if (configuredHeaderName != null && !Objects.toString(configuredHeaderName).isBlank()) {
                headerName = Objects.toString(configuredHeaderName);
            }
        }

        Map<String, String> headers = new HashMap<>();
        headers.put(headerName, "signed");
        return new SignerResult(headers, Map.of(), context.body());
    }
}
