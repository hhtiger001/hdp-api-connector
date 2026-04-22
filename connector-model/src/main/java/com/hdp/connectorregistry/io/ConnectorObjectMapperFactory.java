package com.hdp.connectorregistry.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class ConnectorObjectMapperFactory {
    private ConnectorObjectMapperFactory() {}

    public static ObjectMapper yamlMapper() {
        return new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    }
}
