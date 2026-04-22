package com.hdp.connectorregistry.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.model.ApiConnector;
import com.hdp.connectorregistry.model.SchemaDefinition;
import com.hdp.connectorregistry.model.StreamDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SchemaResolver {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public Map<String, JsonNode> resolve(Path connectorPath, ApiConnector connector) {
        Map<String, JsonNode> schemasByRef = new LinkedHashMap<>();
        Path connectorDirectory = connectorPath.toAbsolutePath().getParent();
        if (connectorDirectory == null) {
            connectorDirectory = Path.of(".");
        }

        if (connector.spec() == null || connector.spec().streams() == null) {
            return schemasByRef;
        }

        for (StreamDefinition stream : connector.spec().streams()) {
            SchemaDefinition schema = stream.schema();
            if (schema == null || schema.ref() == null || schema.ref().isBlank()) {
                continue;
            }

            Path schemaPath = connectorDirectory.resolve(schema.ref()).normalize();
            try {
                schemasByRef.put(schema.ref(), objectMapper.readTree(Files.readString(schemaPath)));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read schema: " + schemaPath, exception);
            }
        }

        return schemasByRef;
    }
}
