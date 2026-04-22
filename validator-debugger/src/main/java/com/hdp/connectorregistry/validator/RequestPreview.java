package com.hdp.connectorregistry.validator;

import java.util.Map;

public record RequestPreview(
        String streamName,
        String method,
        String url,
        Map<String, String> headers,
        Map<String, String> queryParameters,
        String body,
        Integer effectiveQps,
        String signerName) {}
