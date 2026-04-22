package com.hdp.connectorregistry.model;

public record RequestDefinition(
        String requesterRef,
        String path,
        String method,
        String signerRef,
        String qps) {}
