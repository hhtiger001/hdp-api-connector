package com.hdp.connectorregistry.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class ConnectorObjectMapperFactory {
    private ConnectorObjectMapperFactory() {}

    public static ObjectMapper jsonMapper() {
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .findAndRegisterModules();
    }

    public static ObjectMapper yamlMapper() {
        return new ObjectMapper(new YAMLFactory())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .findAndRegisterModules();
    }
}
