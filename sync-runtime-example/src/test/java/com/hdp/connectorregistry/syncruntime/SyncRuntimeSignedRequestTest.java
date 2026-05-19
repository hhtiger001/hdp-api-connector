package com.hdp.connectorregistry.syncruntime;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SyncRuntimeSignedRequestTest {
    private final SyncTaskRuntime runtime = new SyncTaskRuntime();

    @Test
    void sendsSignedRequestUsingConnectorJsonEndpointRefAndJavaSigner() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<Map<String, String>> receivedHeaders = new AtomicReference<>(Map.of());
        AtomicReference<String> receivedPath = new AtomicReference<>();
        server.createContext("/signed-records", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((key, values) ->
                    headers.put(key, values == null || values.isEmpty() ? "" : values.get(0)));
            receivedHeaders.set(headers);
            byte[] body = "{\"records\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            Path connectorPath = Path.of("../connectors/signed-demo/connector.json").normalize();
            Map<String, Object> connectionConfig = Map.of(
                    "base_url", "http://127.0.0.1:" + server.getAddress().getPort(),
                    "api_key", "key-1",
                    "api_secret", "secret-1");

            SyncTaskRuntime.TestRequestResult result =
                    runtime.testRequest(connectorPath, "signed-records", connectionConfig, Map.of());

            assertThat(result.ok()).isTrue();
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.method()).isEqualTo("GET");
            assertThat(result.url()).contains("/signed-records");
            assertThat(receivedPath.get()).isEqualTo("/signed-records");
            assertThat(receivedHeaders.get())
                    .containsKey("X-hdp-signature")
                    .containsEntry("X-hdp-key", "key-1")
                    .containsKey("X-hdp-timestamp");
        } finally {
            server.stop(0);
        }
    }
}
