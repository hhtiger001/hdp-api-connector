package com.hdp.connectorregistry.validator;

import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.io.ConnectorLoadException;
import com.hdp.connectorregistry.io.ConnectorObjectMapperFactory;
import com.hdp.connectorregistry.model.ApiConnector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class RawConnectorLoader {
    public ConnectorLoader.LoadedConnector load(Path connectorPath) {
        if (Files.isDirectory(connectorPath)
                || (connectorPath.getFileName() != null
                        && "connector.json".equals(connectorPath.getFileName().toString()))) {
            return new ConnectorLoader().load(connectorPath);
        }
        try {
            String rawConnector = Files.readString(connectorPath);
            if (rawConnector.isBlank()) {
                throw new ConnectorLoadException("Connector is empty: " + connectorPath);
            }
            try {
                ApiConnector connector = ConnectorObjectMapperFactory.yamlMapper()
                        .readValue(rawConnector, ApiConnector.class);
                validateStructure(connector, connectorPath);
                return new ConnectorLoader.LoadedConnector(connector, Map.of());
            } catch (IOException exception) {
                throw new ConnectorLoadException("Malformed legacy connector YAML: " + connectorPath, exception);
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
            throw new ConnectorLoadException("Legacy connector YAML is missing required field: spec (" + connectorPath + ")");
        }
        if (connector.spec().streams() == null) {
            throw new ConnectorLoadException(
                    "Legacy connector YAML is missing required field: spec.streams (" + connectorPath + ")");
        }
        for (int index = 0; index < connector.spec().streams().size(); index++) {
            if (connector.spec().streams().get(index) == null) {
                throw new ConnectorLoadException(
                        "Legacy connector YAML contains invalid stream entry at spec.streams[" + index + "] ("
                                + connectorPath + ")");
            }
        }
    }
}
