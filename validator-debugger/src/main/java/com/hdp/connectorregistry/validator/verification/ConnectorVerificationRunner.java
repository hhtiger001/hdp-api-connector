package com.hdp.connectorregistry.validator.verification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.io.ConnectorObjectMapperFactory;
import com.hdp.connectorregistry.model.EndpointDefinition;
import com.hdp.connectorregistry.syncruntime.SyncTaskRuntime;
import com.hdp.connectorregistry.validator.ConnectorValidator;
import com.hdp.connectorregistry.validator.DiagnosticSeverity;
import com.hdp.connectorregistry.validator.RequestPlanner;
import com.hdp.connectorregistry.validator.RequestPreview;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConnectorVerificationRunner {
    private final ObjectMapper objectMapper = ConnectorObjectMapperFactory.jsonMapper();
    private final ConnectorLoader connectorLoader = new ConnectorLoader();
    private final RequestPlanner requestPlanner = new RequestPlanner();
    private final ConnectorValidator connectorValidator = new ConnectorValidator();
    private final SyncTaskRuntime syncTaskRuntime = new SyncTaskRuntime();

    public List<VerificationResult> verify(Path connectorPath, Path configPath) {
        return verify(connectorPath, configPath, null);
    }

    public List<VerificationResult> verify(Path connectorPath, Path configPath, String toolName) {
        Path connectorFile = VerificationPaths.connectorFile(connectorPath);
        Path connectorDirectory = VerificationPaths.connectorDirectory(connectorPath);
        var loadedConnector = connectorLoader.load(connectorFile);
        JsonNode injection = readJson(configPath);
        JsonNode config = requireObject(injection.path("config"), "Verification config must contain object field: config");
        Map<String, Object> connectionConfig = objectMapper.convertValue(config, new TypeReference<>() {});

        List<ScenarioFile> scenarios = loadScenarios(connectorDirectory, toolName);
        List<VerificationResult> results = new ArrayList<>();
        for (ScenarioFile scenarioFile : scenarios) {
            VerificationScenario scenario = scenarioFile.scenario();
            EndpointDefinition endpoint = loadedConnector.tools().stream()
                    .filter(tool -> Objects.equals(tool.name(), scenario.tool()))
                    .findFirst()
                    .orElseThrow(() -> new VerificationFailure("Unknown tool in scenario " + scenario.name()
                            + ": " + scenario.tool()));
            results.add(verifyScenario(
                    connectorFile,
                    loadedConnector,
                    endpoint,
                    scenarioFile,
                    config,
                    connectionConfig,
                    injection));
        }
        return List.copyOf(results);
    }

    private VerificationResult verifyScenario(
            Path connectorFile,
            ConnectorLoader.LoadedConnector loadedConnector,
            EndpointDefinition endpoint,
            ScenarioFile scenarioFile,
            JsonNode config,
            Map<String, Object> connectionConfig,
            JsonNode injection) {
        VerificationScenario scenario = scenarioFile.scenario();
        List<String> passedSteps = new ArrayList<>();
        passedSteps.add("load");

        var diagnostics = connectorValidator.validate(loadedConnector, config);
        var errors = diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)
                .toList();
        if (!errors.isEmpty()) {
            throw new VerificationFailure("Config validation failed for " + scenario.name() + ": " + errors);
        }
        passedSteps.add("validate");

        if (requestPlanner.listComponents(loadedConnector).lines().noneMatch(line -> line.equals("- " + scenario.tool()))) {
            throw new VerificationFailure("Tool is missing from component list: " + scenario.tool());
        }
        passedSteps.add("listComponents");

        RequestPreview preview = requestPlanner.preview(loadedConnector, scenario.tool(), config);
        verifyPreview(scenario, preview);
        passedSteps.add("previewRequest");

        JsonNode input = inputForScenario(scenario, injection);
        Map<String, Object> inputMap = objectMapper.convertValue(input, new TypeReference<>() {});
        SyncTaskRuntime.TestRequestResult response =
                syncTaskRuntime.testRequest(connectorFile, endpoint.name(), connectionConfig, inputMap);
        ResponseRecords records = verifyResponse(scenario, response);
        updateResponseExample(scenarioFile, records, endpoint.outputSchema());
        passedSteps.add("testRequest");

        return new VerificationResult(
                scenario.name(),
                scenario.tool(),
                List.copyOf(passedSteps),
                preview.method(),
                preview.url(),
                response.statusCode());
    }

    private void verifyPreview(VerificationScenario scenario, RequestPreview preview) {
        VerificationExpect expect = scenario.expect();
        if (expect == null) {
            return;
        }
        if (expect.method() != null && !expect.method().equalsIgnoreCase(preview.method())) {
            throw new VerificationFailure("Expected method " + expect.method() + " but was " + preview.method()
                    + " for " + scenario.name());
        }
        if (expect.urlContains() != null && !preview.url().contains(expect.urlContains())) {
            throw new VerificationFailure("Expected URL to contain " + expect.urlContains() + " but was "
                    + preview.url() + " for " + scenario.name());
        }
    }

    private ResponseRecords verifyResponse(VerificationScenario scenario, SyncTaskRuntime.TestRequestResult response) {
        VerificationExpect expect = scenario.expect();
        JsonNode responseJson = null;
        if (expect == null) {
            return responseRecords(response.responseBody(), false);
        }
        if (expect.statusCode() != null && expect.statusCode() != response.statusCode()) {
            throw new VerificationFailure("Expected status " + expect.statusCode() + " but was "
                    + response.statusCode() + " for " + scenario.name());
        }
        if (Boolean.TRUE.equals(expect.responseJson()) || scenario.records() != null) {
            try {
                responseJson = objectMapper.readTree(response.responseBody());
            } catch (IOException exception) {
                throw new VerificationFailure("Expected JSON response for " + scenario.name(), exception);
            }
        }
        ResponseRecords records = responseRecords(
                response.responseBody(),
                responseJson,
                Boolean.TRUE.equals(expect.responseJson()),
                scenario.records());
        Integer min = scenario.records() == null ? null : scenario.records().min();
        if (min != null && records.count() < min) {
            throw new VerificationFailure("Expected at least " + min + " records but found "
                    + records.count() + " for " + scenario.name());
        }
        return records;
    }

    private ResponseRecords responseRecords(String responseBody, boolean parseJson) {
        if (!parseJson) {
            return new ResponseRecords(0, null);
        }
        try {
            return responseRecords(responseBody, objectMapper.readTree(responseBody), true, null);
        } catch (IOException exception) {
            return new ResponseRecords(0, null);
        }
    }

    private ResponseRecords responseRecords(
            String responseBody,
            JsonNode responseJson,
            boolean jsonExpected,
            VerificationRecords recordsConfig) {
        if (responseJson == null || responseJson.isMissingNode() || responseJson.isNull()) {
            return new ResponseRecords(0, null);
        }
        JsonNode records = recordsNode(responseJson, recordsConfig);
        if (records.isArray()) {
            JsonNode example = records.isEmpty() ? null : records.get(0);
            return new ResponseRecords(records.size(), example);
        }
        if (records.isObject()) {
            return new ResponseRecords(1, records);
        }
        return new ResponseRecords(0, null);
    }

    private void updateResponseExample(ScenarioFile scenarioFile, ResponseRecords records, JsonNode outputSchema) {
        VerificationScenario scenario = scenarioFile.scenario();
        if (scenario.records() == null || records.example() == null) {
            return;
        }
        JsonNode root = readJson(scenarioFile.path());
        if (!root.isObject()) {
            throw new VerificationFailure("Verification scenario must be an object: " + scenarioFile.path());
        }
        ObjectNode rootObject = (ObjectNode) root;
        JsonNode recordsNode = rootObject.path("records");
        ObjectNode recordsObject;
        if (recordsNode.isObject()) {
            recordsObject = (ObjectNode) recordsNode;
        } else {
            recordsObject = rootObject.putObject("records");
        }
        recordsObject.set("example", exampleForSchema(records.example(), outputSchema));
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(scenarioFile.path().toFile(), rootObject);
        } catch (IOException exception) {
            throw new VerificationFailure("Unable to update response example: " + scenarioFile.path(), exception);
        }
    }

    private JsonNode exampleForSchema(JsonNode example, JsonNode schema) {
        if (example == null || schema == null || !schema.isObject()) {
            return example;
        }
        JsonNode properties = schema.path("properties");
        if (example.isObject() && properties.isObject() && !properties.isEmpty()) {
            ObjectNode projected = objectMapper.createObjectNode();
            properties.fieldNames().forEachRemaining(fieldName -> {
                JsonNode value = example.path(fieldName);
                if (!value.isMissingNode()) {
                    projected.set(fieldName, exampleForSchema(value, properties.path(fieldName)));
                }
            });
            return projected;
        }
        if (example.isArray() && schema.path("items").isObject()) {
            var projected = objectMapper.createArrayNode();
            example.forEach(item -> projected.add(exampleForSchema(item, schema.path("items"))));
            return projected;
        }
        return example;
    }

    private JsonNode recordsNode(JsonNode responseJson, VerificationRecords recordsConfig) {
        if (recordsConfig != null && recordsConfig.path() != null && !recordsConfig.path().isEmpty()) {
            JsonNode cursor = responseJson;
            for (String pathPart : recordsConfig.path()) {
                cursor = cursor.path(pathPart);
            }
            return cursor;
        }
        if (responseJson.isArray()) {
            return responseJson;
        }
        if (responseJson.isObject()) {
            for (String fieldName : List.of("records", "data", "items")) {
                JsonNode candidate = responseJson.path(fieldName);
                if (candidate.isArray()) {
                    return candidate;
                }
            }
            return responseJson;
        }
        return responseJson;
    }

    private JsonNode inputForScenario(VerificationScenario scenario, JsonNode injection) {
        JsonNode inputRoot = injection.path("input");
        if (inputRoot.isObject() && inputRoot.has(scenario.tool())) {
            return inputRoot.path(scenario.tool());
        }
        if (scenario.input() != null && !scenario.input().isMissingNode() && !scenario.input().isNull()) {
            return scenario.input();
        }
        return objectMapper.createObjectNode();
    }

    private List<ScenarioFile> loadScenarios(Path connectorDirectory, String toolName) {
        Path testsDirectory = connectorDirectory.resolve("tests");
        if (!Files.isDirectory(testsDirectory)) {
            throw new VerificationFailure("Missing tests directory: " + testsDirectory);
        }
        List<Path> scenarioPaths;
        try (var paths = Files.list(testsDirectory)) {
            scenarioPaths = paths
                    .filter(path -> path.getFileName().toString().endsWith(".verify.json"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException exception) {
            throw new VerificationFailure("Unable to read tests directory: " + testsDirectory, exception);
        }
        List<ScenarioFile> scenarios = new ArrayList<>();
        for (Path scenarioPath : scenarioPaths) {
            VerificationScenario scenario = readScenario(scenarioPath);
            if (toolName == null || Objects.equals(toolName, scenario.tool())) {
                scenarios.add(new ScenarioFile(scenarioPath, scenario));
            }
        }
        if (scenarios.isEmpty()) {
            throw new VerificationFailure(toolName == null
                    ? "No verification scenarios found in: " + testsDirectory
                    : "No verification scenario found for tool: " + toolName);
        }
        return List.copyOf(scenarios);
    }

    private VerificationScenario readScenario(Path scenarioPath) {
        try {
            VerificationScenario scenario = objectMapper.readValue(Files.readString(scenarioPath), VerificationScenario.class);
            if (scenario.tool() == null || scenario.tool().isBlank()) {
                throw new VerificationFailure("Verification scenario is missing tool: " + scenarioPath);
            }
            return scenario;
        } catch (IOException exception) {
            throw new VerificationFailure("Unable to read verification scenario: " + scenarioPath, exception);
        }
    }

    private JsonNode readJson(Path path) {
        try {
            return objectMapper.readTree(Files.readString(path));
        } catch (IOException exception) {
            throw new VerificationFailure("Unable to read JSON file: " + path, exception);
        }
    }

    private JsonNode requireObject(JsonNode node, String message) {
        if (node == null || !node.isObject()) {
            throw new VerificationFailure(message);
        }
        return node;
    }

    private record ScenarioFile(Path path, VerificationScenario scenario) {}

    private record ResponseRecords(int count, JsonNode example) {}
}
