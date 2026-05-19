package com.hdp.connectorregistry.validator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader.LoadedConnector;
import com.hdp.connectorregistry.model.EndpointDefinition;
import com.hdp.connectorregistry.model.RequestDefinition;
import com.hdp.connectorregistry.model.SignerDefinition;
import com.hdp.connectorregistry.model.StreamDefinition;
import com.hdp.connectorregistry.signer.RequestSigner;
import com.hdp.connectorregistry.signer.SignerContext;
import com.hdp.connectorregistry.signer.SignerRegistry;
import com.hdp.connectorregistry.signer.SignerResult;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public final class RequestPlanner {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final TemplateResolver templateResolver = new TemplateResolver();
    private final SignerRegistry signerRegistry = new SignerRegistry();

    public RequestPreview preview(LoadedConnector loadedConnector, String streamName, JsonNode config) {
        return preview(loadedConnector, streamName, config, OBJECT_MAPPER.createObjectNode());
    }

    public RequestPreview preview(LoadedConnector loadedConnector, String streamName, JsonNode config, JsonNode input) {
        if (loadedConnector.tools() != null && !loadedConnector.tools().isEmpty()) {
            return previewTool(loadedConnector, streamName, config, input);
        }

        StreamDefinition stream = findStream(loadedConnector, streamName);
        String method = defaultString(stream.request().method(), "GET");
        String baseUrl = resolveBaseUrl(loadedConnector, stream, config);
        String path = templateResolver.resolve(defaultString(stream.request().path(), ""), config);
        String url = resolveUrl(baseUrl, path);
        Integer effectiveQps = resolveEffectiveQps(loadedConnector, stream, config);

        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> queryParameters = new LinkedHashMap<>();
        String body = null;

        String signerName = stream.request().signerRef();
        if (signerName != null && !signerName.isBlank()) {
            SignerDefinition signerDefinition = requireSignerDefinition(loadedConnector, signerName);
            RequestSigner signer = signerRegistry.instantiate(signerDefinition.className());
            SignerResult signerResult = signer.sign(new SignerContext(
                    method,
                    URI.create(url),
                    Map.copyOf(headers),
                    Map.copyOf(queryParameters),
                    body,
                    connectorConfigAsMap(config),
                    signerDefinition.config() == null ? Map.of() : signerDefinition.config(),
                    Instant.now(),
                    "preview"));
            merge(headers, signerResult.headers());
            merge(queryParameters, signerResult.queryParameters());
            if (signerResult.body() != null) {
                body = signerResult.body();
            }
        }

        return new RequestPreview(
                stream.name(),
                method,
                url,
                Map.copyOf(headers),
                Map.copyOf(queryParameters),
                body,
                effectiveQps,
                signerName);
    }

    public String listComponents(LoadedConnector loadedConnector) {
        if (loadedConnector.tools() != null && !loadedConnector.tools().isEmpty()) {
            var sections = new ArrayList<String>();
            sections.add(formatSection("tools", loadedConnector.tools().stream()
                    .map(EndpointDefinition::name)
                    .toList()));
            return String.join(System.lineSeparator(), sections) + System.lineSeparator();
        }

        var sections = new ArrayList<String>();
        sections.add(formatSection("streams", loadedConnector.connector().spec().streams().stream()
                .map(StreamDefinition::name)
                .toList()));
        sections.add(formatSection("schemas", schemaIdentifiers(loadedConnector)));

        var signers = loadedConnector.connector().spec().signers() == null
                ? List.<String>of()
                : new ArrayList<>(loadedConnector.connector().spec().signers().keySet());
        sections.add(formatSection("signers", signers));

        var definitions = loadedConnector.connector().spec().definitions();
        if (definitions != null) {
            sections.add(formatSection("definitions.requesters",
                    definitions.requesters() == null ? List.of() : new ArrayList<>(definitions.requesters().keySet())));
            sections.add(formatSection("definitions.authenticators",
                    definitions.authenticators() == null
                            ? List.of()
                            : new ArrayList<>(definitions.authenticators().keySet())));
        }

        return String.join(System.lineSeparator(), sections) + System.lineSeparator();
    }

    private RequestPreview previewTool(LoadedConnector loadedConnector, String toolName, JsonNode config, JsonNode input) {
        EndpointDefinition tool = findTool(loadedConnector, toolName);
        RequestDefinition request = tool.request();
        String method = requireText(request == null ? null : request.method(), "Missing request.method for tool: " + tool.name());
        String baseUrl = resolveToolBaseUrl(loadedConnector, request, config);
        String path = templateResolver.resolve(defaultString(request.path(), ""), config, input);
        String url = resolveUrl(baseUrl, path);

        Map<String, String> headers = stringMap(request.headers(), config, input);
        Map<String, String> queryParameters = stringMap(request.query(), config, input);
        String body = bodyString(request.body(), config, input);

        JsonNode auth = request.auth();
        if (auth == null || auth.isMissingNode()) {
            auth = loadedConnector.connector().spec().request() == null
                    ? null
                    : loadedConnector.connector().spec().request().auth();
        }
        body = applyAuth(auth, config, method, url, headers, queryParameters, body);

        return new RequestPreview(
                tool.name(),
                method,
                url,
                Map.copyOf(headers),
                Map.copyOf(queryParameters),
                body,
                null,
                null);
    }

    private EndpointDefinition findTool(LoadedConnector loadedConnector, String toolName) {
        return loadedConnector.tools().stream()
                .filter(tool -> Objects.equals(tool.name(), toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));
    }

    private String resolveToolBaseUrl(LoadedConnector loadedConnector, RequestDefinition request, JsonNode config) {
        String baseUrl = request == null ? null : request.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = loadedConnector.connector().spec().request() == null
                    ? null
                    : loadedConnector.connector().spec().request().baseUrl();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Missing baseUrl");
        }
        return templateResolver.resolve(baseUrl, config);
    }

    private Map<String, String> stringMap(JsonNode node, JsonNode config, JsonNode input) {
        Map<String, String> values = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return values;
        }
        node.fields().forEachRemaining(entry ->
                values.put(entry.getKey(), templateResolver.resolve(entry.getValue().asText(), config, input)));
        return values;
    }

    private String bodyString(JsonNode body, JsonNode config, JsonNode input) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return null;
        }
        if (body.isTextual()) {
            return templateResolver.resolve(body.asText(), config, input);
        }
        return body.toString();
    }

    private String applyAuth(
            JsonNode auth,
            JsonNode config,
            String method,
            String url,
            Map<String, String> headers,
            Map<String, String> queryParameters,
            String body) {
        if (auth == null || auth.isNull() || !auth.isObject()) {
            return body;
        }
        String type = auth.path("type").asText("");
        switch (type) {
            case "apiKey" -> {
                String name = auth.path("name").asText(null);
                String value = templateResolver.resolve(auth.path("value").asText(""), config);
                if (name == null || name.isBlank()) {
                    return body;
                }
                String in = auth.path("in").asText("header");
                if ("query".equalsIgnoreCase(in)) {
                    queryParameters.put(name, value);
                } else {
                    headers.put(name, value);
                }
            }
            case "bearerToken" -> headers.put("Authorization",
                    "Bearer " + templateResolver.resolve(auth.path("value").asText(""), config));
            case "basic" -> headers.put("Authorization", "Basic <redacted>");
            case "extension" -> {
                SignerResult signerResult = signExtension(auth.path("extension"), config, method, url, headers, queryParameters, body);
                merge(headers, signerResult.headers());
                merge(queryParameters, signerResult.queryParameters());
                if (signerResult.body() != null) {
                    body = signerResult.body();
                }
            }
            default -> {
            }
        }
        return body;
    }

    private SignerResult signExtension(
            JsonNode extension,
            JsonNode config,
            String method,
            String url,
            Map<String, String> headers,
            Map<String, String> queryParameters,
            String body) {
        if (extension == null || !extension.isObject() || !"java".equalsIgnoreCase(extension.path("type").asText(""))) {
            throw new IllegalStateException("Extension auth requires java runtime implementation");
        }
        String className = extension.path("className").asText("");
        if (className.isBlank()) {
            throw new IllegalStateException("Extension auth is missing className");
        }
        RequestSigner signer = signerRegistry.instantiate(className);
        return signer.sign(new SignerContext(
                method,
                URI.create(url),
                Map.copyOf(headers),
                Map.copyOf(queryParameters),
                body,
                connectorConfigAsMap(config),
                extension.path("config").isMissingNode()
                        ? Map.of()
                        : OBJECT_MAPPER.convertValue(extension.path("config"), new TypeReference<>() {}),
                Instant.now(),
                "preview"));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private StreamDefinition findStream(LoadedConnector loadedConnector, String streamName) {
        return loadedConnector.connector().spec().streams().stream()
                .filter(stream -> Objects.equals(stream.name(), streamName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown stream: " + streamName));
    }

    private String resolveBaseUrl(LoadedConnector loadedConnector, StreamDefinition stream, JsonNode config) {
        var definitions = loadedConnector.connector().spec().definitions();
        if (definitions != null
                && definitions.requesters() != null
                && stream.request().requesterRef() != null) {
            JsonNode requester = definitions.requesters().get(stream.request().requesterRef());
            if (requester != null && requester.hasNonNull("urlBase")) {
                String resolved = templateResolver.resolve(requester.path("urlBase").asText(), config);
                if (!resolved.isBlank()) {
                    return resolved;
                }
            }
        }

        String baseUrl = loadedConnector.connector().spec().defaults() == null
                ? null
                : loadedConnector.connector().spec().defaults().baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Missing baseUrl for stream: " + stream.name());
        }
        return templateResolver.resolve(baseUrl, config);
    }

    private Integer resolveEffectiveQps(LoadedConnector loadedConnector, StreamDefinition stream, JsonNode config) {
        String qps = stream.request() == null ? null : stream.request().qps();
        if (qps == null || qps.isBlank()) {
            qps = stream.qps();
        }
        if (qps == null || qps.isBlank()) {
            qps = loadedConnector.connector().spec().defaults() == null
                    ? null
                    : loadedConnector.connector().spec().defaults().qps();
        }
        if (qps == null || qps.isBlank()) {
            return null;
        }
        String resolved = templateResolver.resolve(qps, config).trim();
        try {
            return Integer.parseInt(resolved);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Unable to parse qps: " + resolved, exception);
        }
    }

    private SignerDefinition requireSignerDefinition(LoadedConnector loadedConnector, String signerName) {
        var signers = loadedConnector.connector().spec().signers();
        if (signers == null || !signers.containsKey(signerName)) {
            throw new IllegalArgumentException("Unknown signer: " + signerName);
        }
        return signers.get(signerName);
    }

    private Map<String, Object> connectorConfigAsMap(JsonNode config) {
        if (config == null || config.isNull()) {
            return Map.of();
        }
        return OBJECT_MAPPER.convertValue(config, new TypeReference<>() {});
    }

    private void merge(Map<String, String> target, Map<String, String> additions) {
        if (additions != null) {
            target.putAll(additions);
        }
    }

    private String resolveUrl(String baseUrl, String path) {
        if (path == null || path.isBlank()) {
            return baseUrl;
        }
        if (path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return path;
        }
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return normalizedBaseUrl + "/" + normalizedPath;
    }

    private List<String> schemaIdentifiers(LoadedConnector loadedConnector) {
        var identifiers = new LinkedHashSet<String>();
        for (StreamDefinition stream : loadedConnector.connector().spec().streams()) {
            if (stream.schema() == null) {
                continue;
            }
            if (stream.schema().ref() != null && !stream.schema().ref().isBlank()) {
                identifiers.add(stream.schema().ref());
                continue;
            }
            if (stream.schema().inline() != null && !stream.schema().inline().isNull()) {
                identifiers.add("inline:" + stream.name());
            }
        }
        return new ArrayList<>(identifiers);
    }

    private String formatSection(String title, List<String> values) {
        var joiner = new StringJoiner(System.lineSeparator());
        joiner.add(title + ":");
        if (values == null || values.isEmpty()) {
            joiner.add("- none");
            return joiner.toString();
        }
        for (String value : values) {
            joiner.add("- " + value);
        }
        return joiner.toString();
    }

    private String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
