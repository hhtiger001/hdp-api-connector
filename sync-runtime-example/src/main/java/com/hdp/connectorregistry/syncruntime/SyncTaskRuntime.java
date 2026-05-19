package com.hdp.connectorregistry.syncruntime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.io.ConnectorObjectMapperFactory;
import com.hdp.connectorregistry.model.EndpointDefinition;
import com.hdp.connectorregistry.model.RequestDefinition;
import com.hdp.connectorregistry.signer.RequestSigner;
import com.hdp.connectorregistry.signer.SignerContext;
import com.hdp.connectorregistry.signer.SignerRegistry;
import com.hdp.connectorregistry.signer.SignerResult;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SyncTaskRuntime {
    private static final ObjectMapper JSON_MAPPER = ConnectorObjectMapperFactory.jsonMapper();
    private static final Pattern CONFIG_TEMPLATE =
            Pattern.compile("\\{\\{\\s*config\\[['\"]([^'\"]+)['\"]]\\s*}}");
    private static final Pattern INPUT_TEMPLATE =
            Pattern.compile("\\{\\{\\s*input\\[['\"]([^'\"]+)['\"]]\\s*}}");

    private final ConnectorLoader connectorLoader;
    private final SignerRegistry signerRegistry;
    private final HttpClient httpClient;

    public SyncTaskRuntime() {
        this(new ConnectorLoader(), new SignerRegistry(), HttpClient.newHttpClient());
    }

    SyncTaskRuntime(ConnectorLoader connectorLoader, SignerRegistry signerRegistry, HttpClient httpClient) {
        this.connectorLoader = connectorLoader;
        this.signerRegistry = signerRegistry;
        this.httpClient = httpClient;
    }

    public TestRequestResult testRequest(
            Path connectorPath,
            String toolName,
            Map<String, Object> connectionConfig,
            Map<String, Object> input) {
        PreparedRequest request = prepareRequest(connectorPath, toolName, connectionConfig, input);
        try {
            HttpResponse<String> response = httpClient.send(toHttpRequest(request), HttpResponse.BodyHandlers.ofString());
            return new TestRequestResult(
                    response.statusCode() >= 200 && response.statusCode() < 300,
                    response.statusCode(),
                    request.method(),
                    request.url(),
                    request.headers(),
                    response.body());
        } catch (IOException exception) {
            throw new SyncRuntimeException("Unable to execute test request: " + request.url(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SyncRuntimeException("Interrupted while executing test request: " + request.url(), exception);
        }
    }

    public PreparedRequest prepareRequest(
            Path connectorPath,
            String toolName,
            Map<String, Object> connectionConfig,
            Map<String, Object> input) {
        ConnectorLoader.LoadedConnector loadedConnector = connectorLoader.load(connectorPath);
        EndpointDefinition endpoint = loadedConnector.tools().stream()
                .filter(tool -> Objects.equals(tool.name(), toolName))
                .findFirst()
                .orElseThrow(() -> new SyncRuntimeException("Unknown tool: " + toolName));
        return prepareRequest(loadedConnector, endpoint, connectionConfig, input);
    }

    public PreparedRequest prepareRequest(
            ConnectorLoader.LoadedConnector loadedConnector,
            EndpointDefinition endpoint,
            Map<String, Object> connectionConfig,
            Map<String, Object> input) {
        RequestDefinition globalRequest = loadedConnector.connector().spec().request();
        RequestDefinition endpointRequest = endpoint.request();
        String method = requireText(endpointRequest.method(), "Endpoint request.method is required: " + endpoint.name());
        String baseUrl = render(
                firstNonBlank(endpointRequest.baseUrl(), globalRequest == null ? null : globalRequest.baseUrl()),
                connectionConfig,
                input);
        String path = render(endpointRequest.path(), connectionConfig, input);
        String url = joinUrl(baseUrl, path);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.putAll(stringMap(globalRequest == null ? null : globalRequest.headers(), connectionConfig, input));
        headers.putAll(stringMap(endpointRequest.headers(), connectionConfig, input));

        Map<String, String> query = new LinkedHashMap<>();
        query.putAll(stringMap(globalRequest == null ? null : globalRequest.query(), connectionConfig, input));
        query.putAll(stringMap(endpointRequest.query(), connectionConfig, input));

        String body = bodyString(
                firstNonMissing(endpointRequest.body(), globalRequest == null ? null : globalRequest.body()),
                connectionConfig,
                input);
        JsonNode auth = firstNonMissing(endpointRequest.auth(), globalRequest == null ? null : globalRequest.auth());
        body = applyAuth(auth, method, url, headers, query, body, connectionConfig);

        return new PreparedRequest(method, appendQuery(url, query), Map.copyOf(headers), body);
    }

    private HttpRequest toHttpRequest(PreparedRequest request) {
        HttpRequest.BodyPublisher bodyPublisher = request.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url()))
                .method(request.method(), bodyPublisher);
        if (!request.headers().isEmpty()) {
            builder.headers(flattenHeaders(request.headers()));
        }
        return builder.build();
    }

    private String applyAuth(
            JsonNode auth,
            String method,
            String url,
            Map<String, String> headers,
            Map<String, String> query,
            String body,
            Map<String, Object> connectionConfig) {
        if (auth == null || auth.isNull() || auth.isMissingNode() || !auth.isObject()) {
            return body;
        }
        String type = auth.path("type").asText("");
        switch (type) {
            case "apiKey" -> applyApiKeyAuth(auth, headers, query, connectionConfig);
            case "bearerToken" -> headers.put("Authorization",
                    "Bearer " + render(auth.path("value").asText(""), connectionConfig, Map.of()));
            case "basic" -> headers.put("Authorization", basicAuth(auth, connectionConfig));
            case "extension" -> {
                SignerResult result = signExtension(auth.path("extension"), method, url, headers, query, body, connectionConfig);
                headers.putAll(result.headers());
                query.putAll(result.queryParameters());
                if (result.body() != null) {
                    body = result.body();
                }
            }
            default -> {
            }
        }
        return body;
    }

    private void applyApiKeyAuth(
            JsonNode auth,
            Map<String, String> headers,
            Map<String, String> query,
            Map<String, Object> connectionConfig) {
        String name = auth.path("name").asText("");
        if (name.isBlank()) {
            return;
        }
        String value = render(auth.path("value").asText(""), connectionConfig, Map.of());
        if ("query".equalsIgnoreCase(auth.path("in").asText("header"))) {
            query.put(name, value);
        } else {
            headers.put(name, value);
        }
    }

    private String basicAuth(JsonNode auth, Map<String, Object> connectionConfig) {
        String username = render(auth.path("username").asText(""), connectionConfig, Map.of());
        String password = render(auth.path("password").asText(""), connectionConfig, Map.of());
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private SignerResult signExtension(
            JsonNode extension,
            String method,
            String url,
            Map<String, String> headers,
            Map<String, String> query,
            String body,
            Map<String, Object> connectionConfig) {
        if (extension == null || !extension.isObject() || !"java".equalsIgnoreCase(extension.path("type").asText(""))) {
            throw new SyncRuntimeException("Extension auth requires java runtime implementation");
        }
        String className = extension.path("className").asText("");
        if (className.isBlank()) {
            throw new SyncRuntimeException("Extension auth is missing className");
        }

        RequestSigner signer = signerRegistry.instantiate(className);
        return signer.sign(new SignerContext(
                method,
                URI.create(url),
                Map.copyOf(headers),
                Map.copyOf(query),
                body,
                connectionConfig,
                extension.path("config").isMissingNode()
                        ? Map.of()
                        : JSON_MAPPER.convertValue(extension.path("config"), new TypeReference<>() {}),
                Instant.now(),
                UUID.randomUUID().toString()));
    }

    private Map<String, String> stringMap(JsonNode node, Map<String, Object> config, Map<String, Object> input) {
        Map<String, String> values = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return values;
        }
        node.fields().forEachRemaining(entry ->
                values.put(entry.getKey(), render(entry.getValue().asText(), config, input)));
        return values;
    }

    private String bodyString(JsonNode body, Map<String, Object> config, Map<String, Object> input) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return null;
        }
        if (body.isTextual()) {
            String template = body.asText();
            if ("{{ input }}".equals(template.trim())) {
                return toJson(input);
            }
            return render(template, config, input);
        }
        return renderJson(body.deepCopy(), config, input).toString();
    }

    private JsonNode renderJson(JsonNode node, Map<String, Object> config, Map<String, Object> input) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                            .set(entry.getKey(), renderJson(entry.getValue(), config, input)));
            return node;
        }
        if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode array = (com.fasterxml.jackson.databind.node.ArrayNode) node;
            for (int index = 0; index < array.size(); index++) {
                array.set(index, renderJson(array.get(index), config, input));
            }
            return node;
        }
        if (node.isTextual()) {
            return com.fasterxml.jackson.databind.node.TextNode.valueOf(render(node.asText(), config, input));
        }
        return node;
    }

    private String render(String template, Map<String, Object> config, Map<String, Object> input) {
        if (template == null) {
            return "";
        }
        String rendered = renderTemplate(CONFIG_TEMPLATE, template, config);
        return renderTemplate(INPUT_TEMPLATE, rendered, input);
    }

    private String renderTemplate(Pattern pattern, String template, Map<String, Object> values) {
        var matcher = pattern.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = values.get(matcher.group(1));
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String toJson(Map<String, Object> input) {
        try {
            return JSON_MAPPER.writeValueAsString(input);
        } catch (IOException exception) {
            throw new SyncRuntimeException("Unable to serialize request input", exception);
        }
    }

    private String appendQuery(String url, Map<String, String> query) {
        if (query.isEmpty()) {
            return url;
        }
        String encodedQuery = query.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return url + (url.contains("?") ? "&" : "?") + encodedQuery;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String joinUrl(String baseUrl, String path) {
        String normalizedBaseUrl = requireText(baseUrl, "Missing request.baseUrl");
        normalizedBaseUrl = normalizedBaseUrl.endsWith("/")
                ? normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1)
                : normalizedBaseUrl;
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return normalizedBaseUrl + "/" + normalizedPath;
    }

    private String[] flattenHeaders(Map<String, String> headers) {
        return headers.entrySet().stream()
                .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                .toArray(String[]::new);
    }

    private JsonNode firstNonMissing(JsonNode primary, JsonNode fallback) {
        if (primary != null && !primary.isMissingNode() && !primary.isNull()) {
            return primary;
        }
        return fallback;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new SyncRuntimeException(message);
        }
        return value;
    }

    public record PreparedRequest(
            String method,
            String url,
            Map<String, String> headers,
            String body) {}

    public record TestRequestResult(
            boolean ok,
            int statusCode,
            String method,
            String url,
            Map<String, String> requestHeaders,
            String responseBody) {}

    public static final class SyncRuntimeException extends RuntimeException {
        public SyncRuntimeException(String message) {
            super(message);
        }

        public SyncRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
