package com.hdp.connectorregistry.validator.verification;

import java.util.List;

public record VerificationResult(
        String scenario,
        String tool,
        List<String> passedSteps,
        String method,
        String url,
        int statusCode) {}
