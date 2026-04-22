package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.hdp.connectorregistry.model.ApiConnector;
import java.util.Map;

public record ConversionResult(
        ApiConnector connector,
        Map<String, JsonNode> schemasByPath,
        ConversionReport report) {}
