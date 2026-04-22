package com.hdp.connectorregistry.signer;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record SignerContext(
        String method,
        URI uri,
        Map<String, String> headers,
        Map<String, String> queryParameters,
        String body,
        Map<String, Object> connectorConfig,
        Map<String, Object> signerConfig,
        Instant timestamp,
        String nonce) {}
