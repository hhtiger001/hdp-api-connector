package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorObjectMapperFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AirbyteManifestLoader {
    private final ObjectMapper yamlMapper = ConnectorObjectMapperFactory.yamlMapper();
    private final ObjectMapper jsonMapper = new ObjectMapper().findAndRegisterModules();

    public JsonNode load(Path manifestPath) {
        return read(manifestPath, yamlMapper, "manifest");
    }

    public JsonNode loadJson(Path jsonPath) {
        return read(jsonPath, jsonMapper, "json");
    }

    private JsonNode read(Path path, ObjectMapper mapper, String kind) {
        try {
            return mapper.readTree(Files.readString(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + kind + ": " + path, exception);
        }
    }
}
