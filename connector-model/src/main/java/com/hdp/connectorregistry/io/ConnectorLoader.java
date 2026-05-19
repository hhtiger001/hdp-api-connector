package com.hdp.connectorregistry.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.model.ApiConnector;
import com.hdp.connectorregistry.model.ConnectorSpec;
import com.hdp.connectorregistry.model.EndpointDefinition;
import com.hdp.connectorregistry.model.Metadata;
import com.hdp.connectorregistry.model.RequestDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConnectorLoader {
    private final ObjectMapper yamlMapper = ConnectorObjectMapperFactory.yamlMapper();
    private final ObjectMapper jsonMapper = ConnectorObjectMapperFactory.jsonMapper();
    private final SchemaResolver schemaResolver = new SchemaResolver();

    public LoadedConnector load(Path connectorPath) {
        if (Files.isDirectory(connectorPath)) {
            Path connectorJson = connectorPath.resolve("connector.json");
            return loadConnectorFile(connectorJson);
        }
        if (connectorPath.getFileName() != null && "connector.json".equals(connectorPath.getFileName().toString())) {
            return loadConnectorFile(connectorPath);
        }
        return loadYamlConnector(connectorPath);
    }

    private LoadedConnector loadYamlConnector(Path connectorPath) {
        try {
            String rawConnector = Files.readString(connectorPath);
            try {
                ApiConnector connector = yamlMapper.readValue(rawConnector, ApiConnector.class);
                validateLegacyStructure(connector, connectorPath);
                Map<String, JsonNode> schemasByRef = schemaResolver.resolve(connectorPath, connector);
                return new LoadedConnector(connector, schemasByRef, List.of());
            } catch (IOException exception) {
                throw new ConnectorLoadException("Malformed legacy connector YAML: " + connectorPath, exception);
            }
        } catch (IOException exception) {
            throw new ConnectorLoadException("Unable to read connector: " + connectorPath, exception);
        }
    }

    private LoadedConnector loadConnectorFile(Path connectorPath) {
        try {
            JsonNode connectorFile = jsonMapper.readTree(Files.readString(connectorPath));
            validateConnectorFile(connectorFile, connectorPath);
            JsonNode metadata = connectorFile.has("metadata")
                    ? connectorFile.path("metadata")
                    : connectorFile.path("connector");

            ApiConnector connector = new ApiConnector(
                    connectorFile.path("apiVersion").asText("hdp.connector/v1alpha1"),
                    null,
                    new Metadata(
                            metadata.path("name").asText(null),
                            metadata.path("displayName").asText(null)),
                    new ConnectorSpec(
                            connectorFile.path("connectionSpec"),
                            null,
                            null,
                            null,
                            null,
                            requestFromConnectorFile(connectorFile.path("request")),
                            null));

            Path baseDirectory = connectorPath.toAbsolutePath().normalize().getParent();
            if (baseDirectory == null) {
                baseDirectory = Path.of(".").toAbsolutePath().normalize();
            }

            List<EndpointDefinition> endpoints = new ArrayList<>();
            Map<String, String> endpointRefsByName = new LinkedHashMap<>();
            for (JsonNode tool : connectorFile.path("tools")) {
                String endpointRef = text(tool.path("endpointRef"));
                Path endpointPath = resolveEndpointRef(baseDirectory, endpointRef);
                EndpointDefinition endpoint =
                        jsonMapper.readValue(Files.readString(endpointPath), EndpointDefinition.class);
                validateEndpoint(endpoint, endpointPath);
                endpoints.add(endpoint);
                endpointRefsByName.put(endpoint.name(), endpointRef);
            }
            return new LoadedConnector(connector, Map.of(), List.copyOf(endpoints), Map.copyOf(endpointRefsByName));
        } catch (IOException exception) {
            throw new ConnectorLoadException("Unable to read connector JSON: " + connectorPath, exception);
        }
    }

    private RequestDefinition requestFromConnectorFile(JsonNode request) {
        if (request == null || !request.isObject()) {
            return null;
        }
        return new RequestDefinition(
                null,
                null,
                null,
                null,
                null,
                text(request.path("baseUrl")),
                request.path("auth").isMissingNode() ? null : request.path("auth"),
                request.path("headers").isMissingNode() ? null : request.path("headers"),
                request.path("query").isMissingNode() ? null : request.path("query"),
                request.path("body").isMissingNode() ? null : request.path("body"));
    }

    private static Path resolveEndpointRef(Path baseDirectory, String endpointRef) {
        if (endpointRef == null || endpointRef.isBlank()) {
            throw new ConnectorLoadException("Connector tool is missing endpointRef");
        }
        Path refPath = Path.of(endpointRef);
        if (refPath.isAbsolute()) {
            throw new ConnectorLoadException("endpointRef must be relative: " + endpointRef);
        }
        Path endpointPath = baseDirectory.resolve(refPath).normalize();
        if (!endpointPath.startsWith(baseDirectory)) {
            throw new ConnectorLoadException("endpointRef escapes connector directory: " + endpointRef);
        }
        return endpointPath;
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static void validateLegacyStructure(ApiConnector connector, Path connectorPath) {
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

    private static void validateEndpoint(EndpointDefinition endpoint, Path endpointPath) {
        if (endpoint == null) {
            throw new ConnectorLoadException("Endpoint is empty: " + endpointPath);
        }
        if (endpoint.name() == null || endpoint.name().isBlank()) {
            throw new ConnectorLoadException("Endpoint is missing required field: name (" + endpointPath + ")");
        }
        if (endpoint.outputSchema() == null || endpoint.outputSchema().isNull()) {
            throw new ConnectorLoadException("Endpoint is missing required field: outputSchema (" + endpointPath + ")");
        }
        if (endpoint.request() == null) {
            throw new ConnectorLoadException("Endpoint is missing required field: request (" + endpointPath + ")");
        }
        if (endpoint.request().method() == null || endpoint.request().method().isBlank()) {
            throw new ConnectorLoadException("Endpoint is missing required field: request.method (" + endpointPath + ")");
        }
        if (endpoint.request().path() == null || endpoint.request().path().isBlank()) {
            throw new ConnectorLoadException("Endpoint is missing required field: request.path (" + endpointPath + ")");
        }
    }

    private static void validateConnectorFile(JsonNode connectorFile, Path connectorPath) {
        if (connectorFile == null || connectorFile.isNull() || !connectorFile.isObject()) {
            throw new ConnectorLoadException("Connector JSON is empty: " + connectorPath);
        }
        if (connectorFile.path("connectionSpec").isMissingNode() || connectorFile.path("connectionSpec").isNull()) {
            throw new ConnectorLoadException("Connector JSON is missing required field: connectionSpec (" + connectorPath + ")");
        }
        if (!connectorFile.path("tools").isArray() || connectorFile.path("tools").isEmpty()) {
            throw new ConnectorLoadException("Connector JSON is missing required field: tools (" + connectorPath + ")");
        }
    }

    public record LoadedConnector(
            ApiConnector connector,
            Map<String, JsonNode> schemasByRef,
            List<EndpointDefinition> tools,
            Map<String, String> endpointRefsByName) {

        public LoadedConnector(ApiConnector connector, Map<String, JsonNode> schemasByRef) {
            this(connector, schemasByRef, List.of(), Map.of());
        }

        public LoadedConnector(
                ApiConnector connector,
                Map<String, JsonNode> schemasByRef,
                List<EndpointDefinition> tools) {
            this(connector, schemasByRef, tools, Map.of());
        }
    }
}
