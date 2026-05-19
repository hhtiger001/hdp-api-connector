package com.hdp.connectorregistry.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class RequestPlannerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void resolvesBaseUrlQpsAndSignerHeaders() throws Exception {
        var loaded = new ConnectorLoader().load(resourcePath("fixtures/connector/minimal/connector.yaml"));
        var config = OBJECT_MAPPER.readTree(resourcePath("fixtures/config/preview-config.json").toFile());

        RequestPreview preview = new RequestPlanner().preview(loaded, "users", config);

        assertThat(preview.streamName()).isEqualTo("users");
        assertThat(preview.method()).isEqualTo("GET");
        assertThat(preview.url()).isEqualTo("https://api.example.com/users");
        assertThat(preview.effectiveQps()).isEqualTo(2);
        assertThat(preview.headers()).containsEntry("X-Signature", "signed");
    }

    @Test
    void preservesBaseUrlPathWhenCombiningRelativeRequestPath() throws Exception {
        var loaded = new ConnectorLoader().load(resourcePath("fixtures/connector/base-path/connector.yaml"));
        var config = OBJECT_MAPPER.readTree("{}");

        RequestPreview preview = new RequestPlanner().preview(loaded, "pokemon", config);

        assertThat(preview.url()).isEqualTo("https://pokeapi.co/api/v2/pokemon/bulbasaur");
    }

    @Test
    void rejectsExtensionAuthPreviewWithoutRuntimeImplementation() throws Exception {
        Path connectorDirectory = connectorJsonFixtureWithExtensionAuth();
        var loaded = new ConnectorLoader().load(connectorDirectory.resolve("connector.json"));
        var config = OBJECT_MAPPER.readTree("{}");

        assertThatThrownBy(() -> new RequestPlanner().preview(loaded, "signed", config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to instantiate signer");
    }

    @Test
    void appliesExtensionAuthSignerWhenClassIsAvailable() throws Exception {
        Path connectorDirectory = connectorJsonFixtureWithAvailableExtensionAuth();
        var loaded = new ConnectorLoader().load(connectorDirectory.resolve("connector.json"));
        var config = OBJECT_MAPPER.readTree("""
                {
                  "api_key": "key-1",
                  "api_secret": "secret-1"
                }
                """);

        RequestPreview preview = new RequestPlanner().preview(loaded, "signed", config);

        assertThat(preview.url()).isEqualTo("https://api.example.com/signed");
        assertThat(preview.headers())
                .containsKeys("X-HDP-Signature", "X-HDP-Timestamp", "X-HDP-Key")
                .containsEntry("X-HDP-Key", "key-1");
    }

    private static Path connectorJsonFixtureWithExtensionAuth() throws IOException {
        Path connectorDirectory = Files.createTempDirectory("validator-extension-auth-connector");
        Files.createDirectories(connectorDirectory.resolve("endpoints"));
        Files.writeString(connectorDirectory.resolve("connector.json"), """
                {
                  "apiVersion": "hdp.connector/v1alpha1",
                  "metadata": {
                    "name": "signed"
                  },
                  "connectionSpec": {
                    "type": "object"
                  },
                  "request": {
                    "baseUrl": "https://api.example.com",
                    "auth": {
                      "type": "extension",
                      "extension": {
                        "type": "java",
                        "className": "com.example.HmacSigner"
                      }
                    }
                  },
                  "tools": [
                    {
                      "name": "signed",
                      "endpointRef": "endpoints/signed.json"
                    }
                  ]
                }
                """);
        Files.writeString(connectorDirectory.resolve("endpoints/signed.json"), """
                {
                  "name": "signed",
                  "inputSchema": {
                    "type": "object"
                  },
                  "outputSchema": {
                    "type": "object",
                    "properties": {
                      "id": {
                        "type": "string"
                      }
                    }
                  },
                  "request": {
                    "method": "GET",
                    "path": "/signed"
                  }
                }
                """);
        return connectorDirectory;
    }

    private static Path connectorJsonFixtureWithAvailableExtensionAuth() throws IOException {
        Path connectorDirectory = Files.createTempDirectory("validator-available-extension-auth-connector");
        Files.createDirectories(connectorDirectory.resolve("endpoints"));
        Files.writeString(connectorDirectory.resolve("connector.json"), """
                {
                  "apiVersion": "hdp.connector/v1alpha1",
                  "metadata": {
                    "name": "signed"
                  },
                  "connectionSpec": {
                    "type": "object"
                  },
                  "request": {
                    "baseUrl": "https://api.example.com",
                    "auth": {
                      "type": "extension",
                      "extension": {
                        "type": "java",
                        "className": "com.hdp.connectorregistry.signer.HmacSha256Signer",
                        "config": {
                          "signatureHeader": "X-HDP-Signature",
                          "timestampHeader": "X-HDP-Timestamp",
                          "keyField": "api_key",
                          "keyHeader": "X-HDP-Key",
                          "secretField": "api_secret"
                        }
                      }
                    }
                  },
                  "tools": [
                    {
                      "name": "signed",
                      "endpointRef": "endpoints/signed.json"
                    }
                  ]
                }
                """);
        Files.writeString(connectorDirectory.resolve("endpoints/signed.json"), """
                {
                  "name": "signed",
                  "inputSchema": {
                    "type": "object"
                  },
                  "outputSchema": {
                    "type": "object",
                    "properties": {
                      "id": {
                        "type": "string"
                      }
                    }
                  },
                  "request": {
                    "method": "GET",
                    "path": "/signed"
                  }
                }
                """);
        return connectorDirectory;
    }

    private static Path resourcePath(String resourceName) throws URISyntaxException {
        var resource = RequestPlannerTest.class.getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource, resourceName).toURI());
    }
}
