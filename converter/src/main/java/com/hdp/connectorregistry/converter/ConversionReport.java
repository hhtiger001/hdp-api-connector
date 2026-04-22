package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ConversionReport(
        ConversionStatus status,
        List<ConversionIssue> issues,
        String originVersion,
        JsonNode originalApiBudget) {}
