package com.hdp.connectorregistry.converter;

public record ConversionIssue(
        String severity,
        String code,
        String message,
        String location,
        String originalValue) {}
