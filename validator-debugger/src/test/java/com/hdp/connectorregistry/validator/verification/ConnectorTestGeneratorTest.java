package com.hdp.connectorregistry.validator.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorTestGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesMissingScenariosAndPreservesExistingFiles() throws Exception {
        writeConnector();
        Files.createDirectories(tempDir.resolve("tests"));
        Files.writeString(tempDir.resolve("tests/users.verify.json"), "{\"name\":\"custom\"}");

        var generated = new ConnectorTestGenerator().generate(tempDir.resolve("connector.json"));

        assertThat(generated)
                .containsExactly(
                        tempDir.resolve("tests/config.example.json"),
                        tempDir.resolve("tests/projects.verify.json"));
        assertThat(Files.readString(tempDir.resolve("tests/users.verify.json"))).isEqualTo("{\"name\":\"custom\"}");
        String projects = Files.readString(tempDir.resolve("tests/projects.verify.json"));
        assertThat(projects)
                .contains("\"tool\" : \"projects\"")
                .contains("\"method\" : \"POST\"")
                .contains("\"urlContains\" : \"/projects\"")
                .contains("\"name\" : \"TODO\"")
                .doesNotContain("\"records\"")
                .doesNotContain("\"response\"")
                .doesNotContain("\"example\"");
        String config = Files.readString(tempDir.resolve("tests/config.example.json"));
        assertThat(config)
                .contains("\"config\" : {")
                .contains("\"api_key\" : \"TODO\"")
                .contains("\"input\" : {")
                .contains("\"projects\" : {")
                .contains("\"name\" : \"TODO\"");

        Files.writeString(tempDir.resolve("tests/config.example.json"), "{\"config\":\"custom\"}");
        var secondRun = new ConnectorTestGenerator().generate(tempDir.resolve("connector.json"));

        assertThat(secondRun).isEmpty();
        assertThat(Files.readString(tempDir.resolve("tests/config.example.json"))).isEqualTo("{\"config\":\"custom\"}");
    }

    private void writeConnector() throws Exception {
        Files.createDirectories(tempDir.resolve("endpoints"));
        Files.writeString(tempDir.resolve("connector.json"), """
                {
                  "apiVersion": "hdp.connector/v1alpha1",
                  "metadata": {
                    "name": "generated-demo"
                  },
                  "connectionSpec": {
                    "type": "object",
                    "required": [ "api_key" ],
                    "properties": {
                      "api_key": {
                        "type": "string"
                      }
                    }
                  },
                  "request": {
                    "baseUrl": "http://127.0.0.1"
                  },
                  "tools": [
                    {
                      "name": "users",
                      "endpointRef": "endpoints/users.json"
                    },
                    {
                      "name": "projects",
                      "endpointRef": "endpoints/projects.json"
                    }
                  ]
                }
                """);
        Files.writeString(tempDir.resolve("endpoints/users.json"), endpoint("users", "GET", "/users", "{}"));
        Files.writeString(tempDir.resolve("endpoints/projects.json"), endpoint("projects", "POST", "/projects", """
                {
                  "type": "object",
                  "required": [ "name" ],
                  "properties": {
                    "name": {
                      "type": "string"
                    }
                  }
                }
                """));
    }

    private String endpoint(String name, String method, String path, String inputSchema) {
        return """
                {
                  "name": "%s",
                  "inputSchema": %s,
                  "outputSchema": {
                    "type": "object"
                  },
                  "request": {
                    "method": "%s",
                    "path": "%s"
                  }
                }
                """.formatted(name, inputSchema, method, path);
    }
}
