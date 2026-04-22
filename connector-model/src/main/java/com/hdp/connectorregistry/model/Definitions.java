package com.hdp.connectorregistry.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record Definitions(
        Map<String, JsonNode> requesters,
        Map<String, JsonNode> authenticators) {}
