package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorObjectMapperFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class OutputWriter {
    private final ObjectMapper yamlMapper = ConnectorObjectMapperFactory.yamlMapper();
    private final ObjectMapper jsonMapper = new ObjectMapper().findAndRegisterModules();

    public void write(ConversionResult result, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Files.createDirectories(outputDir.resolve("schemas"));

        yamlMapper.writeValue(outputDir.resolve("connector.yaml").toFile(), result.connector());
        writeSchemas(result.schemasByPath(), outputDir);
        jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValue(outputDir.resolve("conversion-report.json").toFile(), result.report());
    }

    private void writeSchemas(Map<String, com.fasterxml.jackson.databind.JsonNode> schemasByPath, Path outputDir)
            throws IOException {
        for (Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> entry : schemasByPath.entrySet()) {
            Path schemaPath = outputDir.resolve(entry.getKey());
            Files.createDirectories(schemaPath.getParent());
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(schemaPath.toFile(), entry.getValue());
        }
    }
}
