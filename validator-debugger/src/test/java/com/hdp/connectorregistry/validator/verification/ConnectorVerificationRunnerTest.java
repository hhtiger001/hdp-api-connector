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
            respond(exchange, "{\"records\":[{\"id\":\"user-1\",\"name\":\"User 1\",\"ignored\":\"drop\"}]}");
        });
        server.createContext("/projects", exchange -> respond(exchange, "{\"records\":[]}"));
        server.createContext("/teams", exchange -> respond(exchange, "{\"data\":[{\"id\":\"team-1\",\"name\":\"Team 1\"}]}"));
        server.createContext("/tasks", exchange -> respond(exchange, "{\"items\":[{\"id\":\"task-1\",\"name\":\"Task 1\"}]}"));
        server.createContext("/profile", exchange -> respond(exchange, "{\"id\":\"profile-1\",\"name\":\"Profile 1\"}"));
        server.start();

        try {
            writeConnector();
            writeScenario("users", "/users");
            writeScenario("projects", "/projects");
            writeScenario("teams", "/teams");
            writeScenario("tasks", "/tasks");
            writeScenario("profile", "/profile");
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

            assertThat(allResults).extracting(VerificationResult::tool)
                    .containsExactly("profile", "projects", "tasks", "teams", "users");
            assertThat(usersOnly).extracting(VerificationResult::tool).containsExactly("users");
            assertThat(apiKey.get()).isEqualTo("key-1");
            assertThat(Files.readString(tempDir.resolve("tests/users.verify.json")))
                    .contains("\"response\" : {")
                    .contains("\"id\" : \"user-1\"")
                    .contains("\"name\" : \"User 1\"")
                    .doesNotContain("ignored")
                    .doesNotContain("\"records\"");
            assertThat(Files.readString(tempDir.resolve("tests/projects.verify.json")))
                    .doesNotContain("\"response\"");
            assertThat(Files.readString(tempDir.resolve("tests/teams.verify.json")))
                    .contains("\"id\" : \"team-1\"")
                    .contains("\"name\" : \"Team 1\"");
            assertThat(Files.readString(tempDir.resolve("tests/tasks.verify.json")))
                    .contains("\"id\" : \"task-1\"")
                    .contains("\"name\" : \"Task 1\"");
            assertThat(Files.readString(tempDir.resolve("tests/profile.verify.json")))
                    .contains("\"id\" : \"profile-1\"")
                    .contains("\"name\" : \"Profile 1\"");
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
                    },
                    {
                      "name": "teams",
                      "endpointRef": "endpoints/teams.json"
                    },
                    {
                      "name": "tasks",
                      "endpointRef": "endpoints/tasks.json"
                    },
                    {
                      "name": "profile",
                      "endpointRef": "endpoints/profile.json"
                    }
                  ]
                }
                """);
        Files.writeString(tempDir.resolve("endpoints/users.json"), endpoint("users", "/users"));
        Files.writeString(tempDir.resolve("endpoints/projects.json"), endpoint("projects", "/projects"));
        Files.writeString(tempDir.resolve("endpoints/teams.json"), endpoint("teams", "/teams"));
        Files.writeString(tempDir.resolve("endpoints/tasks.json"), endpoint("tasks", "/tasks"));
        Files.writeString(tempDir.resolve("endpoints/profile.json"), endpoint("profile", "/profile"));
    }

    private void writeScenario(String tool, String urlContains) throws Exception {
        Files.createDirectories(tempDir.resolve("tests"));
        Files.writeString(tempDir.resolve("tests/" + tool + ".verify.json"), """
                {
                  "name": "%s",
                  "tool": "%s",
                  "input": {},
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
                    "type": "object",
                    "properties": {
                      "id": {
                        "type": "string"
                      },
                      "name": {
                        "type": "string"
                      }
                    }
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
