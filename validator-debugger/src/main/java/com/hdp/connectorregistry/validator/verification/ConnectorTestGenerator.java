package com.hdp.connectorregistry.validator.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.io.ConnectorObjectMapperFactory;
import com.hdp.connectorregistry.model.EndpointDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ConnectorTestGenerator {
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{[^}]+}}");
    private final ObjectMapper objectMapper = ConnectorObjectMapperFactory.jsonMapper();
    private final ConnectorLoader connectorLoader = new ConnectorLoader();

    public List<Path> generate(Path connectorPath) {
        Path connectorFile = VerificationPaths.connectorFile(connectorPath);
        Path connectorDirectory = VerificationPaths.connectorDirectory(connectorPath);
        var loadedConnector = connectorLoader.load(connectorFile);
        Path testsDirectory = connectorDirectory.resolve("tests");
        try {
            Files.createDirectories(testsDirectory);
        } catch (IOException exception) {
            throw new VerificationFailure("Unable to create tests directory: " + testsDirectory, exception);
        }

        List<Path> generated = new ArrayList<>();
        Path configPath = testsDirectory.resolve("config.example.json");
        if (!Files.exists(configPath)) {
            writeConfig(loadedConnector.connector().spec().connectionSpec(), loadedConnector.tools(), configPath);
            generated.add(configPath);
        }
        for (EndpointDefinition endpoint : loadedConnector.tools()) {
            Path scenarioPath = testsDirectory.resolve(fileName(endpoint.name()));
            if (Files.exists(scenarioPath)) {
                continue;
            }
            writeScenario(endpoint, scenarioPath);
            generated.add(scenarioPath);
        }
        return List.copyOf(generated);
    }

    private void writeScenario(EndpointDefinition endpoint, Path scenarioPath) {
        ObjectNode scenario = objectMapper.createObjectNode();
        scenario.put("name", endpoint.name());
        scenario.put("tool", endpoint.name());
        scenario.set("input", inputSkeleton(endpoint.inputSchema()));

        ObjectNode expect = scenario.putObject("expect");
        expect.put("method", endpoint.request().method());
        expect.put("urlContains", urlContains(endpoint.request().path()));
        expect.put("statusCode", 200);
        expect.put("responseJson", true);

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(scenarioPath.toFile(), scenario);
        } catch (IOException exception) {
            throw new VerificationFailure("Unable to write verification scenario: " + scenarioPath, exception);
        }
    }

    private void writeConfig(JsonNode connectionSpec, List<EndpointDefinition> endpoints, Path configPath) {
        ObjectNode config = objectMapper.createObjectNode();
        config.set("config", inputSkeleton(connectionSpec));
        ObjectNode input = config.putObject("input");
        for (EndpointDefinition endpoint : endpoints) {
            input.set(endpoint.name(), inputSkeleton(endpoint.inputSchema()));
        }
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        } catch (IOException exception) {
            throw new VerificationFailure("Unable to write verification config template: " + configPath, exception);
        }
    }

    private JsonNode inputSkeleton(JsonNode inputSchema) {
        ObjectNode input = objectMapper.createObjectNode();
        if (inputSchema == null || !inputSchema.isObject()) {
            return input;
        }
        JsonNode required = inputSchema.path("required");
        JsonNode properties = inputSchema.path("properties");
        if (!required.isArray() || !properties.isObject()) {
            return input;
        }
        for (JsonNode requiredField : required) {
            String fieldName = requiredField.asText(null);
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            input.put(fieldName, todoValue(properties.path(fieldName)));
        }
        return input;
    }

    private String todoValue(JsonNode schema) {
        String type = "string";
        JsonNode typeNode = schema.path("type");
        type = firstType(typeNode, type);
        return switch (type) {
            case "integer", "number" -> "TODO_NUMBER";
            case "boolean" -> "TODO_BOOLEAN";
            default -> "TODO";
        };
    }

    private String firstType(JsonNode typeNode, String defaultType) {
        if (typeNode.isArray()) {
            for (JsonNode candidate : typeNode) {
                String type = candidate.asText();
                if (!"null".equals(type)) {
                    return type;
                }
            }
            return defaultType;
        }
        return typeNode.asText(defaultType);
    }

    private String urlContains(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        if (!TEMPLATE.matcher(path).find()) {
            return path;
        }

        List<String> trailingStaticSegments = new ArrayList<>();
        List<String> staticSegments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            if (TEMPLATE.matcher(segment).find()) {
                trailingStaticSegments.clear();
                continue;
            }
            staticSegments.add(segment);
            trailingStaticSegments.add(segment);
        }
        if (!trailingStaticSegments.isEmpty()) {
            return "/" + String.join("/", trailingStaticSegments);
        }
        if (!staticSegments.isEmpty()) {
            return "/" + staticSegments.get(staticSegments.size() - 1);
        }
        return "/";
    }

    private String fileName(String toolName) {
        String safeName = toolName.replaceAll("[^A-Za-z0-9._-]", "_");
        return safeName + ".verify.json";
    }
}
