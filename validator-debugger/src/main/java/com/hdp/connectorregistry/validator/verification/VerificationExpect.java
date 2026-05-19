package com.hdp.connectorregistry.validator.verification;

public record VerificationExpect(
        String method,
        String urlContains,
        Integer statusCode,
        Boolean responseJson) {}
