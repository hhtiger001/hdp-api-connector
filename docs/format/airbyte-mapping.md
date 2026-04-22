# Airbyte Mapping

This document explains how the registry MVP maps an Airbyte declarative manifest into HDP connector YAML.

## Direct mappings

- `connection_specification` -> `spec.connectionSpec`
- `definitions` -> `spec.definitions`
- `streams` -> `spec.streams`

These mappings are structural. The converter should preserve the manifest content as closely as possible while adapting it to the HDP wrapper.

## Downgrade rules

- Simple fixed-window `api_budget` -> `spec.defaults.qps`
- Custom component -> `draft` or `blocked`
- Complex budget -> keep the original detail in `conversion-report.json`

## Notes on downgrade handling

- Use `draft` when the converter can keep the connector shape but not fully normalize the Airbyte feature.
- Use `blocked` when the feature cannot be represented safely in the MVP connector format.
- Preserve complex or non-stable budget details in `conversion-report.json` so the conversion remains auditable without inventing a lossy QPS value.
