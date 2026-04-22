package com.hdp.connectorregistry.model;

import com.fasterxml.jackson.databind.JsonNode;

public record SchemaDefinition(
        String ref,
        JsonNode inline) {}
