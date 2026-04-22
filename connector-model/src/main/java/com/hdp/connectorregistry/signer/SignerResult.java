package com.hdp.connectorregistry.signer;

import java.util.Map;

public record SignerResult(
        Map<String, String> headers,
        Map<String, String> queryParameters,
        String body) {}
