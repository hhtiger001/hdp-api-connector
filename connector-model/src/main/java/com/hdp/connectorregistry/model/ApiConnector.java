package com.hdp.connectorregistry.model;

public record ApiConnector(
        String apiVersion,
        String kind,
        Metadata metadata,
        ConnectorSpec spec) {}
