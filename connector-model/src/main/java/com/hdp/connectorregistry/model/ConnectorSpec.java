package com.hdp.connectorregistry.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record ConnectorSpec(
        JsonNode connectionSpec,
        Defaults defaults,
        Definitions definitions,
        Map<String, SignerDefinition> signers,
        List<StreamDefinition> streams) {}
