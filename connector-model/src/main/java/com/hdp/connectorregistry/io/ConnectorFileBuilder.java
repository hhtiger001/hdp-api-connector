package com.hdp.connectorregistry.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdp.connectorregistry.io.ConnectorLoader.LoadedConnector;
import com.hdp.connectorregistry.model.ApiConnector;
import com.hdp.connectorregistry.model.EndpointDefinition;
import com.hdp.connectorregistry.model.RequestDefinition;
import java.util.Collection;
import java.util.Map;

public final class ConnectorFileBuilder {
    private final ObjectMapper objectMapper = ConnectorObjectMapperFactory.jsonMapper();

    public JsonNode build(LoadedConnector loadedConnector) {
        return build(loadedConnector.connector(), loadedConnector.tools(), loadedConnector.endpointRefsByName());
    }

    public JsonNode build(ApiConnector apiConnector, Collection<EndpointDefinition> tools) {
        return build(apiConnector, tools, Map.of());
    }

    public JsonNode build(
            ApiConnector apiConnector,
            Collection<EndpointDefinition> tools,
            Map<String, String> endpointRefsByName) {
        ObjectNode connector = objectMapper.createObjectNode();
        connector.put("apiVersion", "hdp.connector/v1alpha1");

        ObjectNode metadata = connector.putObject("metadata");
        if (apiConnector.metadata() != null) {
            metadata.put("name", apiConnector.metadata().name());
            metadata.put("displayName", apiConnector.metadata().displayName());
        }

        connector.set("connectionSpec", apiConnector.spec().connectionSpec());
        ObjectNode globalRequest = globalRequest(apiConnector.spec().request());
        if (!globalRequest.isEmpty()) {
            connector.set("request", globalRequest);
        }

        ArrayNode toolNodes = connector.putArray("tools");
        for (EndpointDefinition tool : tools) {
            ObjectNode toolNode = toolNodes.addObject();
            toolNode.put("name", tool.name());
            if (tool.title() != null) {
                toolNode.put("title", tool.title());
            }
            if (tool.description() != null) {
                toolNode.put("description", tool.description());
            }
            String endpointRef = endpointRefsByName.get(tool.name());
            if (endpointRef == null || endpointRef.isBlank()) {
                endpointRef = "endpoints/" + tool.name() + ".json";
            }
            toolNode.put("endpointRef", endpointRef);
        }
        return connector;
    }

    private ObjectNode globalRequest(RequestDefinition requestDefinition) {
        ObjectNode request = objectMapper.createObjectNode();
        if (requestDefinition == null) {
            return request;
        }
        if (requestDefinition.baseUrl() != null) {
            request.put("baseUrl", requestDefinition.baseUrl());
        }
        if (requestDefinition.auth() != null && !requestDefinition.auth().isNull()) {
            request.set("auth", requestDefinition.auth());
        }
        if (requestDefinition.headers() != null) {
            request.set("headers", requestDefinition.headers());
        }
        if (requestDefinition.query() != null) {
            request.set("query", requestDefinition.query());
        }
        if (requestDefinition.body() != null) {
            request.set("body", requestDefinition.body());
        }
        return request;
    }
}
