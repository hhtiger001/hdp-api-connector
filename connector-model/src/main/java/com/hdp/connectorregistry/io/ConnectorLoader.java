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
            String rawConnector = Files.readString(connectorPath);
            try {
                ApiConnector connector = yamlMapper.readValue(rawConnector, ApiConnector.class);
                validateStructure(connector, connectorPath);
                Map<String, JsonNode> schemasByRef = schemaResolver.resolve(connectorPath, connector);
                return new LoadedConnector(connector, schemasByRef);
            } catch (IOException exception) {
                throw new ConnectorLoadException("Malformed connector YAML: " + connectorPath, exception);
            }
        } catch (IOException exception) {
            throw new ConnectorLoadException("Unable to read connector: " + connectorPath, exception);
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
        for (int index = 0; index < connector.spec().streams().size(); index++) {
            if (connector.spec().streams().get(index) == null) {
                throw new ConnectorLoadException(
                        "Connector contains invalid stream entry at spec.streams[" + index + "] (" + connectorPath + ")");
            }
        }
    }

    public record LoadedConnector(ApiConnector connector, Map<String, JsonNode> schemasByRef) {}
}
