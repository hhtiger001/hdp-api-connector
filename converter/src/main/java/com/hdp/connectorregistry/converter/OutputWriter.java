package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorFileBuilder;
import com.hdp.connectorregistry.io.ConnectorObjectMapperFactory;
import com.hdp.connectorregistry.model.EndpointDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

public final class OutputWriter {
    private final ObjectMapper jsonMapper = ConnectorObjectMapperFactory.jsonMapper();

    public void write(ConversionResult result, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        cleanGeneratedArtifacts(outputDir);
        Files.createDirectories(outputDir.resolve("endpoints"));

        writeEndpoints(result.endpointsByPath(), outputDir);
        jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValue(
                        outputDir.resolve("connector.json").toFile(),
                        new ConnectorFileBuilder().build(
                                result.connector(),
                                result.endpointsByPath().values(),
                                endpointRefsByName(result.endpointsByPath())));
    }

    private void cleanGeneratedArtifacts(Path outputDir) throws IOException {
        Files.deleteIfExists(outputDir.resolve("connector.yaml"));
        Files.deleteIfExists(outputDir.resolve("config.json"));
        Files.deleteIfExists(outputDir.resolve("compiled-plan.json"));
        Files.deleteIfExists(outputDir.resolve("conversion-report.json"));
        deleteRecursivelyIfExists(outputDir.resolve("schemas"));
        deleteRecursivelyIfExists(outputDir.resolve("endpoints"));
    }

    private void deleteRecursivelyIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path child : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(child);
            }
        }
    }

    private void writeEndpoints(Map<String, EndpointDefinition> endpointsByPath, Path outputDir)
            throws IOException {
        for (Map.Entry<String, EndpointDefinition> entry : endpointsByPath.entrySet()) {
            Path endpointPath = outputDir.resolve(entry.getKey());
            Files.createDirectories(endpointPath.getParent());
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(endpointPath.toFile(), entry.getValue());
        }
    }

    private Map<String, String> endpointRefsByName(Map<String, EndpointDefinition> endpointsByPath) {
        return endpointsByPath.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getValue().name(),
                        Map.Entry::getKey,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
    }
}
