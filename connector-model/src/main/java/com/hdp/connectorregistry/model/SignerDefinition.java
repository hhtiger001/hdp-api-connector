package com.hdp.connectorregistry.model;

import java.util.Map;

public record SignerDefinition(
        String type,
        String className,
        Map<String, Object> config) {}
