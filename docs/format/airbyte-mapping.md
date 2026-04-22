# Airbyte Mapping

This document explains how the registry MVP maps an Airbyte declarative manifest into HDP connector YAML.

## Direct mappings

- `spec.connection_specification` -> `spec.connectionSpec`
- `definitions` -> `spec.definitions`
- `streams` -> `spec.streams`

These mappings are structural. The converter should preserve the manifest content as closely as possible while adapting it to the HDP wrapper.

## Downgrade rules

- When a simple fixed-window `api_budget` can be reduced safely, map it to the narrowest stable HDP qps field; a common outcome is connector default qps
- Custom component -> `draft` or `blocked`
- Complex budget -> keep the original detail in `conversion-report.json`

## Notes on downgrade handling

- Use `draft` when the converter can keep the connector shape but not fully normalize the Airbyte feature.
- Use `blocked` when the feature cannot be represented safely in the MVP connector format.
- QPS downgrades should prefer the narrowest stable HDP scope that can be derived without ambiguity instead of always forcing connector defaults.
- Preserve complex or non-stable budget details in `conversion-report.json` so the conversion remains auditable without inventing a lossy QPS value.
