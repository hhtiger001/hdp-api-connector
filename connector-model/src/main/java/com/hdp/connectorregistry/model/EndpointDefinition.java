package com.hdp.connectorregistry.model;

import com.fasterxml.jackson.databind.JsonNode;

public record EndpointDefinition(
        String name,
        String title,
        String description,
        JsonNode inputSchema,
        JsonNode outputSchema,
        RequestDefinition request,
        JsonNode annotations) {}
