# HDP Connector Schema

This document describes the HDP `ApiConnector` YAML shape used by the registry MVP.

## Top-level keys

- `apiVersion`: fixed to `hdp.connector/v1alpha1`
- `kind`: fixed to `ApiConnector`
- `metadata`: connector identity and source information
- `spec.connectionSpec`: JSON Schema for connection configuration
- `spec.defaults.qps`: default QPS value for the connector
- `spec.signers`: named Java signer references
- `spec.streams[*].schema`: per-stream schema definition

## `metadata`

`metadata` carries human-readable and provenance data for the connector. The MVP shape includes:

- `name`
- `displayName`
- `source`

The `source` block records where the connector came from, such as the originating manifest version and reference.

## `spec.connectionSpec`

`spec.connectionSpec` stores the user-facing connection specification as JSON Schema.

This field is the HDP home for the Airbyte `connection_specification` payload.

## `spec.defaults.qps`

`spec.defaults.qps` defines the connector-wide default request rate limit.

The converter may derive this value from a simple Airbyte `api_budget` when the budget can be reduced to a stable fixed QPS.

## `spec.signers`

`spec.signers` is a map of signer names to signer definitions.

Each signer entry identifies the signer type, the Java class to load, and any signer-specific config payload.

## `spec.streams[*].schema`

Each stream may declare a schema with one of two forms:

- `ref`: a reference to a schema document
- `inline`: an embedded JSON Schema document

The MVP supports both forms so a schema can be reused from a file or carried directly in the connector manifest.

## Schema forms

- Use `ref` when the schema is stored as a separate file under the connector directory.
- Use `inline` when the schema is small enough to embed directly in the stream definition.
- A stream schema should use the form that preserves the original source data with the least ambiguity.
