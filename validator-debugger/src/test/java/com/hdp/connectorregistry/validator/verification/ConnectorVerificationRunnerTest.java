package com.hdp.connectorregistry.validator.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorVerificationRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void verifiesAllScenariosAndCanFilterByTool() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> apiKey = new AtomicReference<>();
        server.createContext("/users", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            respond(exchange, "{\"records\":[{\"id\":\"user-1\",\"name\":\"User 1\"}]}");
        });
        server.createContext("/projects", exchange -> respond(exchange, "{\"records\":[]}"));
        server.start();

        try {
            writeConnector();
            writeScenario("users", "/users");
            writeScenario("projects", "/projects");
            Path configPath = tempDir.resolve("test-config.json");
            Files.writeString(configPath, """
                    {
                      "config": {
                        "base_url": "http://127.0.0.1:%s",
                        "api_key": "key-1"
                      },
                      "input": {
                        "users": {},
                        "projects": {}
                      }
                    }
                    """.formatted(server.getAddress().getPort()));

            ConnectorVerificationRunner runner = new ConnectorVerificationRunner();

            var allResults = runner.verify(tempDir.resolve("connector.json"), configPath);
            var usersOnly = runner.verify(tempDir.resolve("connector.json"), configPath, "users");

            assertThat(allResults).extracting(VerificationResult::tool).containsExactly("projects", "users");
            assertThat(usersOnly).extracting(VerificationResult::tool).containsExactly("users");
            assertThat(apiKey.get()).isEqualTo("key-1");
            assertThat(Files.readString(tempDir.resolve("tests/users.verify.json")))
                    .contains("\"example\" : {")
                    .contains("\"id\" : \"user-1\"")
                    .contains("\"name\" : \"User 1\"");
            assertThat(Files.readString(tempDir.resolve("tests/projects.verify.json")))
                    .doesNotContain("\"example\"");
        } finally {
            server.stop(0);
        }
    }

    private void writeConnector() throws Exception {
        Files.createDirectories(tempDir.resolve("endpoints"));
        Files.writeString(tempDir.resolve("connector.json"), """
                {
                  "apiVersion": "hdp.connector/v1alpha1",
                  "metadata": {
                    "name": "verify-demo"
                  },
                  "connectionSpec": {
                    "type": "object",
                    "required": [ "base_url", "api_key" ],
                    "properties": {
                      "base_url": {
                        "type": "string"
                      },
                      "api_key": {
                        "type": "string"
                      }
                    }
                  },
                  "request": {
                    "baseUrl": "{{ config['base_url'] }}",
                    "auth": {
                      "type": "apiKey",
                      "in": "header",
                      "name": "X-API-Key",
                      "value": "{{ config['api_key'] }}"
                    }
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
        Files.writeString(tempDir.resolve("endpoints/users.json"), endpoint("users", "/users"));
        Files.writeString(tempDir.resolve("endpoints/projects.json"), endpoint("projects", "/projects"));
    }

    private void writeScenario(String tool, String urlContains) throws Exception {
        Files.createDirectories(tempDir.resolve("tests"));
        Files.writeString(tempDir.resolve("tests/" + tool + ".verify.json"), """
                {
                  "name": "%s",
                  "tool": "%s",
                  "input": {},
                  "records": {
                    "path": [ "records" ],
                    "min": 0
                  },
                  "expect": {
                    "method": "GET",
                    "urlContains": "%s",
                    "statusCode": 200,
                    "responseJson": true
                  }
                }
                """.formatted(tool, tool, urlContains));
    }

    private String endpoint(String name, String path) {
        return """
                {
                  "name": "%s",
                  "inputSchema": {
                    "type": "object",
                    "additionalProperties": false
                  },
                  "outputSchema": {
                    "type": "object"
                  },
                  "request": {
                    "method": "GET",
                    "path": "%s"
                  }
                }
                """.formatted(name, path);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String response) throws java.io.IOException {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
