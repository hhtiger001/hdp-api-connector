package com.hdp.connectorregistry.validator.verification;

import com.fasterxml.jackson.databind.JsonNode;

public record VerificationScenario(
        String name,
        String tool,
        JsonNode input,
        VerificationRecords records,
        VerificationExpect expect) {}
