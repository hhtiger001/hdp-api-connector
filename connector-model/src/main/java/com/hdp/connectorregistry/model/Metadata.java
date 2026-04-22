package com.hdp.connectorregistry.model;

public record Metadata(
        String name,
        String displayName,
        SourceMetadata source) {

    public record SourceMetadata(
            String type,
            String originVersion,
            String originRef) {}
}
