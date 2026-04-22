package com.hdp.connectorregistry.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConnectorLoaderTest {
    @Test
    void loadsConnectorAndResolvesExternalSchemaReference() {
        Path connectorPath = Path.of("src/test/resources/fixtures/connector/minimal/connector.yaml");

        var loaded = new ConnectorLoader().load(connectorPath);

        assertThat(loaded.connector().kind()).isEqualTo("ApiConnector");
        assertThat(loaded.connector().metadata().name()).isEqualTo("demo-users");
        assertThat(loaded.connector().spec().defaults().qps()).isEqualTo("2");
        assertThat(loaded.connector().spec().signers().get("fixed_header").className())
                .isEqualTo("com.hdp.connectorregistry.validator.support.FixedHeaderSigner");
        assertThat(loaded.schemasByRef()).containsKey("schemas/users.json");
        assertThat(loaded.schemasByRef().get("schemas/users.json").get("properties")).isNotNull();
    }

    @Test
    void failsFastWhenSpecIsMissing() {
        Path connectorPath = Path.of("src/test/resources/fixtures/connector/malformed/missing-spec.yaml");

        assertThatThrownBy(() -> new ConnectorLoader().load(connectorPath))
                .isInstanceOf(ConnectorLoadException.class)
                .hasMessageContaining("spec");
    }

    @Test
    void failsFastWhenStreamsAreMissing() {
        Path connectorPath = Path.of("src/test/resources/fixtures/connector/malformed/missing-streams.yaml");

        assertThatThrownBy(() -> new ConnectorLoader().load(connectorPath))
                .isInstanceOf(ConnectorLoadException.class)
                .hasMessageContaining("spec.streams");
    }

    @Test
    void failsFastWhenAStreamEntryIsNull() {
        Path connectorPath = Path.of("src/test/resources/fixtures/connector/malformed/null-stream-item.yaml");

        assertThatThrownBy(() -> new ConnectorLoader().load(connectorPath))
                .isInstanceOf(ConnectorLoadException.class)
                .hasMessageContaining("spec.streams[0]");
    }

    @Test
    void failsWhenStreamsHasAnIllegalStructure() {
        Path connectorPath = Path.of("src/test/resources/fixtures/connector/malformed/streams-not-list.yaml");

        assertThatThrownBy(() -> new ConnectorLoader().load(connectorPath))
                .isInstanceOf(ConnectorLoadException.class)
                .hasMessageContaining("Malformed connector YAML")
                .hasMessageContaining("streams-not-list.yaml");
    }

    @Test
    void rejectsAbsoluteSchemaRefs() throws IOException {
        Path connectorDirectory = Files.createTempDirectory("connector-model-absolute-schema");
        Path connectorPath = connectorDirectory.resolve("connector.yaml");
        Path outsideSchemaRef = connectorDirectory.resolveSibling("outside-schema.json").toAbsolutePath();
        Files.writeString(connectorPath, """
                apiVersion: hdp.connector/v1alpha1
                kind: ApiConnector
                metadata:
                  name: absolute-schema
                  displayName: Absolute Schema
                  source:
                    type: airbyte-manifest
                    originVersion: "0.1.0"
                spec:
                  connectionSpec:
                    type: object
                    properties:
                      base_url:
                        type: string
                  defaults:
                    qps: 1
                    baseUrl: "{{ config.base_url }}"
                  definitions:
                    requesters: {}
                    authenticators: {}
                  streams:
                    - name: users
                      request:
                        requesterRef: base_requester
                        path: /users
                        method: GET
                      schema:
                        ref: "%s"
                """.formatted(outsideSchemaRef));

        assertThatThrownBy(() -> new ConnectorLoader().load(connectorPath))
                .isInstanceOf(SchemaResolutionException.class)
                .hasMessageContaining("relative");
    }

    @Test
    void rejectsTraversingSchemaRefs() throws IOException {
        Path connectorDirectory = Files.createTempDirectory("connector-model-traversal-schema");
        Path connectorPath = connectorDirectory.resolve("connector.yaml");
        Files.writeString(connectorPath, """
                apiVersion: hdp.connector/v1alpha1
                kind: ApiConnector
                metadata:
                  name: traversing-schema
                  displayName: Traversing Schema
                  source:
                    type: airbyte-manifest
                    originVersion: "0.1.0"
                spec:
                  connectionSpec:
                    type: object
                    properties:
                      base_url:
                        type: string
                  defaults:
                    qps: 1
                    baseUrl: "{{ config.base_url }}"
                  definitions:
                    requesters: {}
                    authenticators: {}
                  streams:
                    - name: users
                      request:
                        requesterRef: base_requester
                        path: /users
                        method: GET
                      schema:
                        ref: ../outside-schema.json
                """);

        assertThatThrownBy(() -> new ConnectorLoader().load(connectorPath))
                .isInstanceOf(SchemaResolutionException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void loadsInlineSchemaWithoutReadingExternalFiles() {
        Path connectorPath = Path.of("src/test/resources/fixtures/connector/inline-only/connector.yaml");

        var loaded = new ConnectorLoader().load(connectorPath);

        assertThat(loaded.schemasByRef()).isEmpty();
        assertThat(loaded.connector().spec().streams()).hasSize(1);
        assertThat(loaded.connector().spec().streams().get(0).schema().inline()).isNotNull();
    }
}
