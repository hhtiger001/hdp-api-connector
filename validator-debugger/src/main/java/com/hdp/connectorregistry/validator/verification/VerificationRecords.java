package com.hdp.connectorregistry.validator.verification;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record VerificationRecords(
        List<String> path,
        Integer min,
        JsonNode example) {}
