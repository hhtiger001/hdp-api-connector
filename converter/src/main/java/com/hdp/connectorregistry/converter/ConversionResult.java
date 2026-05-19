package com.hdp.connectorregistry.converter;

import com.hdp.connectorregistry.model.EndpointDefinition;
import com.hdp.connectorregistry.model.ApiConnector;
import java.util.Map;

public record ConversionResult(
        ApiConnector connector,
        Map<String, EndpointDefinition> endpointsByPath,
        ConversionReport report) {}
