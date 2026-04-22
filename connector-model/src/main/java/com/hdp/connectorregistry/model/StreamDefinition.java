package com.hdp.connectorregistry.model;

public record StreamDefinition(
        String name,
        String qps,
        RequestDefinition request,
        SchemaDefinition schema) {}
