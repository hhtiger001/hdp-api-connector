# Airbyte-Compatible Connector Registry Design

Date: 2026-04-22
Status: Approved for planning
Scope: First-phase design for an open connector-definition repository that can rapidly reuse Airbyte API source manifests while defining an HDP-owned project structure.

## 1. Summary

This project will provide a connector-definition repository, not a production execution engine.

The repository will:

- store HDP-owned connector YAML definitions
- support converting a single Airbyte `manifest.yaml` into the HDP connector format
- support Java signer SPI references for non-standard request signing
- support local Java validation and request preview for debugging

The repository will not, in phase 1:

- execute real sync jobs in production
- own scheduling, orchestration, or state management
- implement a full Java runtime equivalent of Airbyte declarative execution
- automatically fetch connector sources from GitHub
- automatically translate Airbyte custom Python components into Java

The core strategy is a high-compatibility overlay:

- stay structurally close to Airbyte declarative manifests
- introduce only the minimum HDP-specific additions required for rate limits, schema organization, and signer extension
- treat Airbyte manifest files as an input format, not the internal source of truth

## 2. Goals

### 2.1 Primary goals

- rapidly reuse Airbyte API source manifests as input assets
- define an HDP-owned connector YAML format that external services can read and execute later
- support standard declarative auth plus HDP-specific signing extensions
- enable static local debugging in Java before a full runtime exists

### 2.2 Secondary goals

- keep the connector repository suitable for open-source publication
- keep the transformation model traceable and understandable
- make later migration to a full execution runtime possible without redefining the connector format

## 3. Non-goals

- building a job scheduler
- building a production sync runtime
- building a full Airbyte manifest server replacement
- building a generic arbitrary code execution platform inside connector definitions
- supporting every advanced Airbyte feature in phase 1

## 4. Design principles

### 4.1 Compatibility first

The HDP format should preserve as much of the Airbyte declarative mental model as possible so that conversion stays mechanical and low-risk.

### 4.2 Minimal extension surface

HDP-specific features should be limited to the following first-phase additions:

- repository metadata
- simplified QPS controls
- schema reference conventions
- Java signer SPI references

### 4.3 Definition and execution must stay separate

This repository is a definition registry. Execution belongs to another system.

### 4.4 No Python runtime requirement in phase 1

Although Airbyte supports custom Python components, phase 1 will not introduce Python execution into the HDP repository. Non-standard request signing will use Java SPI.

## 5. Repository structure

The repository should be organized into four main areas.

```text
connectors/
  <connector-name>/
    connector.yaml
    conversion-report.json
    schemas/
      <stream>.json

converter/
  ...

validator-debugger/
  ...

docs/
  superpowers/
    specs/
      2026-04-22-airbyte-compatible-connector-registry-design.md
  format/
    connector-schema.md
    airbyte-mapping.md
```

### 5.1 `connectors/`

This is the authoritative output area. Each connector directory contains the HDP connector definition and any referenced schemas.

### 5.2 `converter/`

This module converts a single Airbyte `manifest.yaml` into an HDP connector definition. It does not fetch manifests from GitHub in phase 1.

### 5.3 `validator-debugger/`

This module is a Java local debugging tool. It validates connector definitions and previews resolved requests, but does not execute real syncs.

### 5.4 `docs/`

This area documents the format, field semantics, mapping rules, and future implementation guidance.

## 6. System boundary

Phase 1 ends at connector definition production and static validation.

### 6.1 In scope

- connector spec format
- Airbyte manifest conversion
- schema organization
- signer extension contract
- local validation
- request preview

### 6.2 Out of scope

- real HTTP sync execution as a product feature
- retries and pagination behavior at runtime
- state persistence
- orchestration and scheduling
- connector lifecycle management service

## 7. Connector YAML format

The HDP connector format should remain close to Airbyte, but with a stable HDP envelope.

### 7.1 Top-level shape

```yaml
apiVersion: hdp.connector/v1alpha1
kind: ApiConnector

metadata:
  name: metabase
  displayName: Metabase

spec:
  connectionSpec:
    type: object
    properties: {}
    required: []

  defaults:
    qps: 10
    baseUrl: https://api.example.com

  definitions:
    requesters: {}
    authenticators: {}

  signers:
    hmac_v1:
      type: java
      className: com.hdp.connectors.signer.HmacSigner
      config:
        headerName: X-Signature

  streams:
    - name: users
      qps: 5
      request:
        requesterRef: base_requester
        path: /users
        method: GET
        signerRef: hmac_v1
      schema:
        ref: schemas/users.json
```

### 7.2 Field groups

#### `metadata`

Repository and origin metadata. This is not part of execution behavior.

Recommended fields:

- `name`
- `displayName`
- `labels`

#### `spec.connectionSpec`

This is the configuration schema for connector users. It should preserve the shape of Airbyte `connection_specification` as closely as possible.

#### `spec.defaults`

This section contains connector-level execution defaults for later consumers.

Phase 1 defaults:

- `qps`
- `baseUrl` when normalization benefits from a dedicated field

#### `spec.definitions`

This preserves reusable declarative components. The first phase should support at least:

- reusable requesters
- reusable authenticators

Additional component groups can be added later without breaking the core shape.

#### `spec.signers`

This is the HDP extension point for non-standard signing behavior.

Each signer entry must include:

- `type: java`
- `className`
- optional static `config`

Signer implementations are loaded from the Java tool or service classpath through SPI or an equivalent plugin-loading mechanism. Connector directories reference signer implementations; they do not ship Java bytecode themselves in phase 1.

#### `spec.streams`

Each stream is a logical dataset, similar to a table or API resource collection.

Each stream may include:

- `name`
- `qps`
- `request`
- `schema`
- optional compatibility metadata

### 7.3 Schema representation

The format must support both inline and referenced schema, but should recommend referenced JSON files.

Supported forms:

```yaml
schema:
  ref: schemas/users.json
```

```yaml
schema:
  inline:
    type: object
    properties: {}
```

Recommended default:

- store schema in `schemas/<stream>.json`
- keep `connector.yaml` readable and compact

### 7.4 Rate limit representation

The first phase rate limit model is intentionally simpler than Airbyte `api_budget`.

Rules:

- connector-level default `qps` is required when a meaningful default can be inferred
- `stream.qps` may override connector default
- a future request-level override is allowed within a stream when one stream contains multiple concrete API requests

This design supports:

- one global default rate limit
- stricter stream-specific limits
- later extension to endpoint-specific overrides without redesigning the top level

## 8. Signer SPI design

Phase 1 signer support uses Java SPI only.

### 8.1 Why Java SPI

This project will already use Java for local validation and debugging. Requiring Python or another runtime in phase 1 would increase packaging and operational complexity without adding enough value.

### 8.2 Signer responsibility

A signer is not a general hook system. It only computes signing-related request mutations.

Expected input:

- method
- path or URL
- headers
- query parameters
- request body
- user config
- signer config
- runtime context such as timestamp or nonce

Expected output:

- headers to add or override
- query parameters to add or override
- body mutations when strictly required for signing

### 8.3 Explicit exclusions

Phase 1 signers must not be responsible for:

- pagination
- retries
- full request execution
- response transformation
- arbitrary post-response business logic

This keeps the extension contract narrow and safe.

## 9. Airbyte manifest conversion strategy

Phase 1 converter input is exactly one Airbyte `manifest.yaml`.

### 9.1 Converter outputs

For each conversion, the tool should produce:

- `connectors/<name>/connector.yaml`
- `connectors/<name>/schemas/*.json` when externalized
- `connectors/<name>/conversion-report.json`

### 9.2 Automatic mappings

The converter should automatically map the following:

- Airbyte `spec.connection_specification` to `spec.connectionSpec`
- Airbyte `definitions` to HDP `spec.definitions`
- Airbyte `streams` to HDP `spec.streams`
- Airbyte inline schemas to referenced schema files when practical

### 9.3 Auth mapping

The converter should preserve standard declarative auth semantics whenever possible.

Supported direct mappings include:

- `ApiKeyAuthenticator`
- `BasicHttpAuthenticator`
- `BearerAuthenticator`
- `SessionTokenAuthenticator`
- `JwtAuthenticator`
- `OAuthAuthenticator`

These should remain declarative auth configuration, not be rewritten into signer definitions.

### 9.4 Rate limit mapping

Airbyte rate limiting is richer than the HDP phase 1 model. The converter must follow explicit downgrade rules.

Rules:

- if a stable fixed rate can be inferred, map it to `qps`
- if the rate applies globally, map to connector default
- if the rate applies to a specific stream or request, map to the narrowest stable level available
- if the policy is too complex for safe conversion, do not invent behavior

Examples of complex cases that must not be silently flattened:

- multiple overlapping policies
- header-driven dynamic remaining-budget logic
- matcher-based policies with ambiguous scope

In those cases, the converter should:

- leave the HDP operational `qps` field conservative or unset
- record the original Airbyte budget details in compatibility metadata or the conversion report
- mark the output as requiring manual review

### 9.5 Custom component handling

Airbyte supports custom declarative components implemented in Python. Phase 1 must not translate those automatically into Java.

If the converter encounters custom components such as:

- `CustomAuthenticator`
- `CustomRequester`
- `CustomRetriever`

it must:

- produce an HDP connector draft rather than a fully trusted ready artifact
- include the original component type and `class_name` in the conversion report
- describe the required manual action

Typical manual actions:

- replace with standard declarative auth if possible
- introduce an HDP Java signer if the custom code is only for signing
- defer the connector until a richer runtime exists

### 9.6 Conversion status

Every conversion result should carry one of three statuses:

- `ready`
- `draft`
- `blocked`

Definitions:

- `ready`: usable in the HDP registry with no known mandatory manual steps
- `draft`: generated successfully but requires manual follow-up
- `blocked`: could not produce a valid HDP connector artifact

## 10. Java validator-debugger

Phase 1 local tooling is intentionally static and deterministic.

### 10.1 Commands

Recommended commands:

- `validate`
- `preview-request`
- `list-components`

### 10.2 `validate`

This command checks:

- connector YAML structure
- config against `connectionSpec`
- schema file presence and readability
- references to definitions
- signer declarations and class loading

### 10.3 `preview-request`

This command resolves one logical request and outputs the final request preview without performing network I/O.

It should show:

- effective method
- effective URL or path
- headers
- query parameters
- request body
- resolved auth shape
- signer before and after effects
- effective inherited `qps`

### 10.4 `list-components`

This command lists available:

- streams
- schemas
- signers
- reusable definitions

### 10.5 Internal pipeline

The local tool should follow a clean staged pipeline:

1. `Loader`
2. `Normalizer`
3. `ConfigValidator`
4. `ReferenceResolver`
5. `RequestPlanner`
6. `SignerInspector`
7. `Reporter`

This separates format parsing from semantic resolution and keeps later runtime work reusable.

### 10.6 Output modes

The validator-debugger should support:

- human-readable output by default
- JSON output for tooling and CI

### 10.7 Diagnostics

Diagnostics should be grouped as:

- `ERROR`
- `WARNING`
- `INFO`

Error examples:

- invalid YAML structure
- missing schema file
- unresolved signer class
- missing required config field

Warning examples:

- complex Airbyte budget only partially mapped
- draft conversion requiring manual action
- ignored compatibility field

Info examples:

- inherited connector default `qps`
- schema resolved from external file

## 11. Licensing and reuse strategy

The repository design must stay license-neutral for HDP while avoiding accidental over-coupling to upstream licensed assets.

### 11.1 Safe reuse approach

The project should reuse:

- concepts
- structure
- field semantics
- conversion logic

The project should not assume phase 1 will vendor upstream Airbyte connector assets into the HDP repository as first-class source files.

### 11.2 Practical rule

Airbyte manifests are treated as conversion inputs. The HDP repository publishes HDP-owned transformed connector definitions.

This makes the repository easier to publish under a future HDP-selected license and reduces complexity around bundling upstream connector assets.

### 11.3 Implication

High compatibility is acceptable. Direct file-level mirroring as the primary repository strategy is not the preferred phase 1 model.

## 12. Why this is the recommended phase 1

This design is recommended because it creates the shortest path to usable HDP connector definitions while preserving future flexibility.

Benefits:

- rapid reuse of Airbyte declarative manifests
- minimal first-phase runtime burden
- clear ownership boundary between definition and execution
- no Python runtime dependency
- explicit path for manual handling of non-standard connectors
- future-safe enough to evolve toward a richer execution system

## 13. Future evolution after phase 1

The expected next steps after this design are:

- define the exact YAML JSON schema for `ApiConnector`
- define converter mapping rules in executable detail
- define the Java signer SPI interface and packaging convention
- implement the Java validator-debugger
- later decide whether to add endpoint-level request objects
- later decide whether to add richer rate-limit policies
- later decide whether to add a real execution runtime

## 14. Reference context

This design was informed by the following official upstream references reviewed on 2026-04-22:

- Airbyte platform declarative source API:
  - https://github.com/airbytehq/airbyte-platform/blob/main/airbyte-api/server-api/src/main/openapi/api_documentation_declarative_source_definitions.yaml
- Airbyte Python CDK declarative component schema:
  - https://github.com/airbytehq/airbyte-python-cdk/blob/main/airbyte_cdk/sources/declarative/declarative_component_schema.yaml
- Airbyte manifest-only CLI:
  - https://github.com/airbytehq/airbyte-python-cdk/blob/main/airbyte_cdk/cli/source_declarative_manifest/README.md
- Airbyte manifest server:
  - https://github.com/airbytehq/airbyte-python-cdk/blob/main/airbyte_cdk/manifest_server/README.md
- Airbyte custom code execution guard:
  - https://github.com/airbytehq/airbyte-python-cdk/blob/main/airbyte_cdk/sources/declarative/parsers/custom_code_compiler.py

## 15. Final design decisions

The approved first-phase decisions are:

- choose the high-compatibility overlay approach
- treat Airbyte manifest as input, not as the HDP repository source of truth
- keep the repository as a connector-definition registry only
- use Java signer SPI instead of Python scripts
- support converter input as a single Airbyte `manifest.yaml`
- model QPS as connector default plus stream or endpoint override
- support both inline and referenced schema, with referenced schema as the recommended form
- build a Java validator-debugger limited to static validation and request preview
