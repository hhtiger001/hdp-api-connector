package com.hdp.connectorregistry.validator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hdp.connectorregistry.io.ConnectorLoadException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RawConnectorLoaderTest {
    @Test
    void failsFastWhenSpecIsMissing() throws Exception {
        Path connectorPath = resourcePath("fixtures/connector/malformed/missing-spec.yaml");

        assertThatThrownBy(() -> new RawConnectorLoader().load(connectorPath))
                .isInstanceOf(ConnectorLoadException.class)
                .hasMessageContaining("spec");
    }

    @Test
    void failsFastWhenStreamsAreMissing(@TempDir Path tempDir) throws IOException {
        Path connectorPath = tempDir.resolve("missing-streams.yaml");
        Files.writeString(connectorPath, """
                apiVersion: hdp.connector/v1alpha1
                kind: ApiConnector
                metadata:
                  name: missing-streams
                  displayName: Missing Streams
                spec:
                  connectionSpec:
                    type: object
                    properties: {}
                """);

        assertThatThrownBy(() -> new RawConnectorLoader().load(connectorPath))
                .isInstanceOf(ConnectorLoadException.class)
                .hasMessageContaining("spec.streams");
    }

    @Test
    void failsFastWhenConnectorIsEmpty(@TempDir Path tempDir) throws IOException {
        Path connectorPath = tempDir.resolve("empty.yaml");
        Files.writeString(connectorPath, "");

        assertThatThrownBy(() -> new RawConnectorLoader().load(connectorPath))
                .isInstanceOf(ConnectorLoadException.class)
                .hasMessageContaining("Connector is empty");
    }

    private static Path resourcePath(String resourceName) throws URISyntaxException {
        var resource = RawConnectorLoaderTest.class.getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource, resourceName).toURI());
    }
}
