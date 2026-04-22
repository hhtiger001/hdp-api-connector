package com.hdp.connectorregistry.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.model.ApiConnector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ConnectorLoader {
    private final ObjectMapper yamlMapper = ConnectorObjectMapperFactory.yamlMapper();
    private final SchemaResolver schemaResolver = new SchemaResolver();

    public LoadedConnector load(Path connectorPath) {
        try {
            ApiConnector connector = yamlMapper.readValue(Files.readString(connectorPath), ApiConnector.class);
            validateStructure(connector, connectorPath);
            Map<String, JsonNode> schemasByRef = schemaResolver.resolve(connectorPath, connector);
            return new LoadedConnector(connector, schemasByRef);
        } catch (IOException exception) {
            throw new ConnectorLoadException("Unable to load connector: " + connectorPath, exception);
        }
    }

    private static void validateStructure(ApiConnector connector, Path connectorPath) {
        if (connector == null) {
            throw new ConnectorLoadException("Connector is empty: " + connectorPath);
        }
        if (connector.spec() == null) {
            throw new ConnectorLoadException("Connector is missing required field: spec (" + connectorPath + ")");
        }
        if (connector.spec().streams() == null) {
            throw new ConnectorLoadException("Connector is missing required field: spec.streams (" + connectorPath + ")");
        }
    }

    public record LoadedConnector(ApiConnector connector, Map<String, JsonNode> schemasByRef) {}
}
