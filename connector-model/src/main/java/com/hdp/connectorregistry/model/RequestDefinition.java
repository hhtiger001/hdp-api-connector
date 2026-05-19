package com.hdp.connectorregistry.model;

import com.fasterxml.jackson.databind.JsonNode;

public record RequestDefinition(
        String requesterRef,
        String path,
        String method,
        String signerRef,
        String qps,
        String baseUrl,
        JsonNode auth,
        JsonNode headers,
        JsonNode query,
        JsonNode body) {

    public RequestDefinition(
            String requesterRef,
            String path,
            String method,
            String signerRef,
            String qps) {
        this(requesterRef, path, method, signerRef, qps, null, null, null, null, null);
    }
}
