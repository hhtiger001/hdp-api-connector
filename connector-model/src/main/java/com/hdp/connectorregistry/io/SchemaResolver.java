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
        Path connectorDirectory = connectorPath.toAbsolutePath().normalize().getParent();
        if (connectorDirectory == null) {
            connectorDirectory = Path.of(".").toAbsolutePath().normalize();
        }

        for (StreamDefinition stream : connector.spec().streams()) {
            SchemaDefinition schema = stream.schema();
            if (schema == null || schema.ref() == null || schema.ref().isBlank()) {
                continue;
            }

            Path schemaPath = resolveSchemaPath(connectorDirectory, schema.ref());
            try {
                schemasByRef.put(schema.ref(), objectMapper.readTree(Files.readString(schemaPath)));
            } catch (IOException exception) {
                throw new SchemaResolutionException("Unable to read schema: " + schemaPath, exception);
            }
        }

        return schemasByRef;
    }

    private static Path resolveSchemaPath(Path connectorDirectory, String schemaRef) {
        Path refPath = Path.of(schemaRef);
        if (refPath.isAbsolute()) {
            throw new SchemaResolutionException("Schema ref must be relative to the connector directory: " + schemaRef);
        }

        Path schemaPath = connectorDirectory.resolve(refPath).normalize();
        if (!schemaPath.startsWith(connectorDirectory)) {
            throw new SchemaResolutionException("Schema ref escapes the connector directory: " + schemaRef);
        }

        return schemaPath;
    }
}
