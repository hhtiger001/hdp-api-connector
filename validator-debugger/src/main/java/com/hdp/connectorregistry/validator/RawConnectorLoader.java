package com.hdp.connectorregistry.validator;

import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.io.ConnectorObjectMapperFactory;
import com.hdp.connectorregistry.model.ApiConnector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class RawConnectorLoader {
    public ConnectorLoader.LoadedConnector load(Path connectorPath) {
        try {
            ApiConnector connector = ConnectorObjectMapperFactory.yamlMapper()
                    .readValue(Files.readString(connectorPath), ApiConnector.class);
            return new ConnectorLoader.LoadedConnector(connector, Map.of());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load connector: " + connectorPath, exception);
        }
    }
}
