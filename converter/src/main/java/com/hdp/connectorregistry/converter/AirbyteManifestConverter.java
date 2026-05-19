package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdp.connectorregistry.model.ApiConnector;
import com.hdp.connectorregistry.model.ConnectorSpec;
import com.hdp.connectorregistry.model.EndpointDefinition;
import com.hdp.connectorregistry.model.EndpointDiscovery;
import com.hdp.connectorregistry.model.Metadata;
import com.hdp.connectorregistry.model.RequestDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AirbyteManifestConverter {
    private static final String CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW =
            "CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW";
    private static final String NO_STREAMS_FOUND = "NO_STREAMS_FOUND";
    private static final String API_BUDGET_REQUIRES_MANUAL_REVIEW =
            "API_BUDGET_REQUIRES_MANUAL_REVIEW";
    private static final String STREAM_SCHEMA_MISSING = "STREAM_SCHEMA_MISSING";
    private static final Pattern STREAM_PARTITION_TEMPLATE = Pattern.compile(
            "\\{\\{\\s*stream_partition(?:\\.([A-Za-z0-9_-]+)|\\[['\"]([^'\"]+)['\"]])\\s*}}");

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final AirbyteManifestLoader loader = new AirbyteManifestLoader();

    public ConversionResult convert(Path manifestPath) {
        JsonNode manifest = loader.load(manifestPath);
        List<ConversionIssue> issues = new ArrayList<>();
        detectCustomComponent(manifest.path("definitions"), "/definitions", issues);

        mapBudget(manifest.path("api_budget"), issues);
        DefinitionBuckets definitionBuckets = mapDefinitions(manifest.path("definitions"));
        Map<String, EndpointDefinition> endpointsByPath = new LinkedHashMap<>();

        JsonNode streamsNode = manifest.path("streams");
        if (!streamsNode.isArray() || streamsNode.isEmpty()) {
            issues.add(new ConversionIssue(
                    "ERROR",
                    NO_STREAMS_FOUND,
                    "Manifest does not define any convertible streams",
                    "/streams",
                    streamsNode.isMissingNode() ? null : streamsNode.toString()));
        } else {
            for (int index = 0; index < streamsNode.size(); index++) {
                JsonNode streamNode = resolveLocalRef(manifest, streamsNode.get(index));
                MappedEndpoint mappedEndpoint =
                        mapStream(manifest, streamNode, index, definitionBuckets.requesters(), issues);
                endpointsByPath.put(mappedEndpoint.path(), mappedEndpoint.endpoint());
            }
        }

        ConversionStatus status = deriveStatus(issues);
        String metadataName = deriveMetadataName(manifestPath);
        ApiConnector connector = new ApiConnector(
                "hdp.connector/v1alpha2",
                null,
                new Metadata(
                        metadataName,
                        metadataName),
                new ConnectorSpec(
                        normalizeConnectionSpec(missingAsNull(manifest.path("spec").path("connection_specification"))),
                        null,
                        null,
                        null,
                        null,
                        new RequestDefinition(
                                null,
                                null,
                                null,
                                null,
                                null,
                                deriveBaseUrl(manifest, definitionBuckets.requesters()),
                                deriveAuth(manifest, definitionBuckets.requesters()),
                                null,
                                null,
                                null),
                        new EndpointDiscovery("endpoints/*.json")));

        ConversionReport report = new ConversionReport(
                status,
                List.copyOf(issues),
                missingAsNull(manifest.path("api_budget")));

        return new ConversionResult(
                connector,
                Collections.unmodifiableMap(new LinkedHashMap<>(endpointsByPath)),
                report);
    }

    private DefinitionBuckets mapDefinitions(JsonNode definitionsNode) {
        if (!definitionsNode.isObject()) {
            return new DefinitionBuckets(new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        Map<String, JsonNode> requesters = new LinkedHashMap<>();
        Map<String, JsonNode> authenticators = new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = definitionsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode definition = entry.getValue();
            if (looksLikeRequester(definition)) {
                requesters.put(entry.getKey(), normalizeRequester(definition));
            }
            if (looksLikeAuthenticator(definition)) {
                authenticators.put(entry.getKey(), definition.deepCopy());
            }
        }

        return new DefinitionBuckets(requesters, authenticators);
    }

    private MappedEndpoint mapStream(
            JsonNode manifest,
            JsonNode streamNode,
            int index,
            Map<String, JsonNode> requesters,
            List<ConversionIssue> issues) {
        String streamName = streamNode.path("name").asText("stream-" + (index + 1));
        detectCustomComponent(streamNode, "/streams/" + index, issues);

        JsonNode schema = extractSchema(manifest, streamNode);
        if (schema.isMissingNode() || schema.isNull()) {
            issues.add(new ConversionIssue(
                    "WARNING",
                    STREAM_SCHEMA_MISSING,
                    "Stream schema could not be extracted and requires manual review",
                    "/streams/" + index + "/schema_loader",
                    null));
        }

        PartitionInputMapping partitionInputMapping = mapPartitionInputs(requestPath(streamNode, streamName));

        EndpointDefinition endpoint = new EndpointDefinition(
                streamName,
                titleize(streamName),
                null,
                partitionInputMapping.inputSchema(),
                schema.isMissingNode() || schema.isNull() ? NullNode.getInstance() : schema.deepCopy(),
                new RequestDefinition(
                        null,
                        partitionInputMapping.path(),
                        requestMethod(streamNode),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                annotationsForMethod(requestMethod(streamNode)));
        return new MappedEndpoint(endpointPathFor(streamName, index), endpoint);
    }

    private BudgetMapping mapBudget(JsonNode apiBudgetNode, List<ConversionIssue> issues) {
        if (apiBudgetNode.isMissingNode() || apiBudgetNode.isNull()) {
            return new BudgetMapping(null);
        }

        JsonNode policies = apiBudgetNode.path("policies");
        if (!policies.isArray() || policies.size() != 1) {
            addComplexBudgetIssue(apiBudgetNode, issues);
            return new BudgetMapping(null);
        }

        JsonNode policy = policies.get(0);
        if (!"HTTPAPIBudget".equals(policy.path("type").asText())) {
            addComplexBudgetIssue(apiBudgetNode, issues);
            return new BudgetMapping(null);
        }

        JsonNode rates = policy.path("rates");
        if (!rates.isArray() || rates.size() != 1) {
            addComplexBudgetIssue(apiBudgetNode, issues);
            return new BudgetMapping(null);
        }

        JsonNode rate = rates.get(0);
        long limit = rate.path("limit").asLong(-1);
        long seconds = secondsForInterval(rate.path("interval").asText(null));
        if (limit <= 0 || seconds <= 0 || limit % seconds != 0) {
            addComplexBudgetIssue(apiBudgetNode, issues);
            return new BudgetMapping(null);
        }

        return new BudgetMapping(Long.toString(limit / seconds));
    }

    private void addComplexBudgetIssue(JsonNode apiBudgetNode, List<ConversionIssue> issues) {
        issues.add(new ConversionIssue(
                "WARNING",
                API_BUDGET_REQUIRES_MANUAL_REVIEW,
                "api_budget could not be reduced to a stable HDP qps value",
                "/api_budget",
                apiBudgetNode.toString()));
    }

    private void detectCustomComponent(JsonNode node, String location, List<ConversionIssue> issues) {
        if (containsCustomComponent(node)) {
            issues.add(new ConversionIssue(
                    "WARNING",
                    CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW,
                    "Custom Airbyte component requires manual review for MVP conversion",
                    location,
                    node.toString()));
        }
    }

    private boolean containsCustomComponent(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            String value = node.asText();
            return "CustomAuthenticator".equals(value)
                    || "JwtAuthenticator".equals(value)
                    || "CustomRequester".equals(value)
                    || "CustomRetriever".equals(value);
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsCustomComponent(child)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isObject()) {
            Iterator<JsonNode> iterator = node.elements();
            while (iterator.hasNext()) {
                if (containsCustomComponent(iterator.next())) {
                    return true;
                }
            }
        }
        return false;
    }

    private ConversionStatus deriveStatus(List<ConversionIssue> issues) {
        boolean blocked = issues.stream().anyMatch(issue -> NO_STREAMS_FOUND.equals(issue.code()));
        if (blocked) {
            return ConversionStatus.BLOCKED;
        }

        boolean draft = issues.stream().anyMatch(issue ->
                CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW.equals(issue.code())
                        || API_BUDGET_REQUIRES_MANUAL_REVIEW.equals(issue.code())
                        || STREAM_SCHEMA_MISSING.equals(issue.code()));
        if (draft) {
            return ConversionStatus.DRAFT;
        }

        return ConversionStatus.READY;
    }

    private boolean looksLikeRequester(JsonNode definition) {
        String type = definition.path("type").asText("");
        return definition.has("urlBase")
                || definition.has("url_base")
                || definition.has("path")
                || definition.has("http_method")
                || type.endsWith("Requester")
                || type.endsWith("Retriever");
    }

    private boolean looksLikeAuthenticator(JsonNode definition) {
        String type = definition.path("type").asText("");
        return definition.has("api_token")
                || definition.has("header")
                || type.endsWith("Authenticator");
    }

    private JsonNode extractSchema(JsonNode manifest, JsonNode streamNode) {
        JsonNode schema = streamNode.path("schema_loader").path("schema");
        if (!schema.isMissingNode() && !schema.isNull()) {
            return normalizeOutputSchema(resolveLocalRef(manifest, schema));
        }

        schema = streamNode.path("json_schema");
        if (!schema.isMissingNode() && !schema.isNull()) {
            return normalizeOutputSchema(resolveLocalRef(manifest, schema));
        }

        schema = streamNode.path("schema");
        if (!schema.isMissingNode() && !schema.isNull()) {
            return normalizeOutputSchema(resolveLocalRef(manifest, schema));
        }

        return MissingNodeHolder.INSTANCE;
    }

    private JsonNode normalizeOutputSchema(JsonNode schema) {
        JsonNode normalized = schema.deepCopy();
        normalizeOutputSchemaNode(normalized);
        return normalized;
    }

    private void normalizeOutputSchemaNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode type = object.path("type");
            if (type.isArray()) {
                ArrayNode nonNullTypes = JSON.arrayNode();
                for (JsonNode typeItem : type) {
                    if (!"null".equals(typeItem.asText())) {
                        nonNullTypes.add(typeItem);
                    }
                }
                if (nonNullTypes.size() == 1) {
                    object.set("type", nonNullTypes.get(0));
                } else if (nonNullTypes.size() > 1) {
                    object.set("type", nonNullTypes);
                }
            }
            Iterator<JsonNode> children = object.elements();
            while (children.hasNext()) {
                normalizeOutputSchemaNode(children.next());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                normalizeOutputSchemaNode(child);
            }
        }
    }

    private JsonNode resolveLocalRef(JsonNode root, JsonNode node) {
        String ref = textValue(node.path("$ref"));
        if (ref == null || !ref.startsWith("#/")) {
            return node;
        }

        JsonNode resolved = root.at(ref.substring(1));
        return resolved.isMissingNode() ? node : resolved;
    }

    private String requesterRef(JsonNode streamNode, String streamName, Map<String, JsonNode> requesters) {
        String directRef = jsonPointerLeaf(streamNode.path("retriever").path("requester").path("$ref"));
        if (directRef != null) {
            return directRef;
        }

        JsonNode inlineRequester = streamNode.path("retriever").path("requester");
        if (inlineRequester.isObject() && !inlineRequester.isEmpty()) {
            String requesterKey = uniqueRequesterKey(streamName, requesters);
            requesters.put(requesterKey, normalizeRequester(inlineRequester));
            return requesterKey;
        }

        return null;
    }

    private String requestPath(JsonNode streamNode, String streamName) {
        JsonNode retriever = streamNode.path("retriever");
        JsonNode requester = retriever.path("requester");
        String path = textValue(requester.path("path"));
        if (path == null) {
            path = textValue(retriever.path("path"));
        }
        if (path != null) {
            return path.startsWith("/") ? path : "/" + path;
        }
        return "/" + slugify(streamName);
    }

    private PartitionInputMapping mapPartitionInputs(String path) {
        Matcher matcher = STREAM_PARTITION_TEMPLATE.matcher(path);
        List<String> fields = new ArrayList<>();
        StringBuffer mappedPath = new StringBuffer();
        while (matcher.find()) {
            String field = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            if (field != null && !fields.contains(field)) {
                fields.add(field);
            }
            matcher.appendReplacement(mappedPath, Matcher.quoteReplacement("{{ input['" + field + "'] }}"));
        }
        matcher.appendTail(mappedPath);

        if (fields.isEmpty()) {
            return new PartitionInputMapping(path, emptyInputSchema());
        }
        return new PartitionInputMapping(mappedPath.toString(), inputSchemaForFields(fields));
    }

    private JsonNode inputSchemaForFields(List<String> fields) {
        ObjectNode schema = JSON.objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ArrayNode required = schema.putArray("required");
        ObjectNode properties = schema.putObject("properties");
        for (String field : fields) {
            required.add(field);
            ObjectNode property = properties.putObject(field);
            property.put("type", "string");
            property.put("description", "Parent stream partition value.");
        }
        return schema;
    }

    private String requestMethod(JsonNode streamNode) {
        JsonNode retriever = streamNode.path("retriever");
        JsonNode requester = retriever.path("requester");
        String method = textValue(requester.path("http_method"));
        if (method == null) {
            method = textValue(requester.path("method"));
        }
        if (method == null) {
            method = textValue(retriever.path("http_method"));
        }
        return method == null ? "GET" : method.toUpperCase(Locale.ROOT);
    }

    private String deriveBaseUrl(JsonNode manifest, Map<String, JsonNode> requesters) {
        JsonNode firstStream = resolveLocalRef(manifest, manifest.path("streams").path(0));
        JsonNode firstStreamRequester = firstStream.path("retriever").path("requester");
        String firstStreamBaseUrl = textValue(firstPresent(firstStreamRequester, "urlBase", "url_base"));
        if (firstStreamBaseUrl != null) {
            return firstStreamBaseUrl;
        }

        JsonNode definitionsNode = manifest.path("definitions");
        if (definitionsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = definitionsNode.fields();
            while (fields.hasNext()) {
                JsonNode definition = fields.next().getValue();
                String urlBase = textValue(firstPresent(definition, "urlBase", "url_base"));
                if (urlBase != null) {
                    return urlBase;
                }
            }
        }

        for (JsonNode requester : requesters.values()) {
            String urlBase = textValue(firstPresent(requester, "urlBase", "url_base"));
            if (urlBase != null) {
                return urlBase;
            }
        }

        return textValue(firstPresent(firstStreamRequester, "urlBase", "url_base"));
    }

    private JsonNode deriveAuth(JsonNode manifest, Map<String, JsonNode> requesters) {
        for (JsonNode requester : requesters.values()) {
            JsonNode auth = mapAuthenticator(manifest, requester.path("authenticator"));
            if (auth != null) {
                return auth;
            }
        }

        JsonNode streamsNode = manifest.path("streams");
        if (streamsNode.isArray()) {
            for (JsonNode streamRef : streamsNode) {
                JsonNode stream = resolveLocalRef(manifest, streamRef);
                JsonNode auth = mapAuthenticator(manifest, stream.path("retriever").path("requester").path("authenticator"));
                if (auth != null) {
                    return auth;
                }
            }
        }

        return null;
    }

    private JsonNode mapAuthenticator(JsonNode manifest, JsonNode authenticator) {
        authenticator = resolveLocalRef(manifest, authenticator);
        if (!authenticator.isObject()) {
            return null;
        }
        String type = authenticator.path("type").asText("");
        return switch (type) {
            case "ApiKeyAuthenticator" -> mapApiKeyAuthenticator(authenticator);
            case "BasicHttpAuthenticator" -> mapBasicAuthenticator(authenticator);
            case "BearerAuthenticator" -> mapBearerAuthenticator(authenticator);
            case "CustomAuthenticator", "JwtAuthenticator" -> mapExtensionAuthenticator(authenticator, type);
            default -> null;
        };
    }

    private JsonNode mapApiKeyAuthenticator(JsonNode authenticator) {
        ObjectNode auth = JSON.objectNode();
        auth.put("type", "apiKey");
        JsonNode injectInto = authenticator.path("inject_into");
        String location = textValue(injectInto.path("inject_into"));
        auth.put("in", location == null ? "header" : location);

        String name = textValue(injectInto.path("field_name"));
        if (name == null) {
            name = textValue(authenticator.path("header"));
        }
        if (name == null) {
            name = textValue(authenticator.path("field_name"));
        }
        if (name != null) {
            auth.put("name", name);
        }

        String value = textValue(authenticator.path("api_token"));
        if (value == null) {
            value = textValue(authenticator.path("value"));
        }
        if (value != null) {
            auth.put("value", value);
        }
        return auth;
    }

    private JsonNode mapBasicAuthenticator(JsonNode authenticator) {
        ObjectNode auth = JSON.objectNode();
        auth.put("type", "basic");
        String username = textValue(authenticator.path("username"));
        if (username != null) {
            auth.put("username", username);
        }
        String password = textValue(authenticator.path("password"));
        if (password != null) {
            auth.put("password", password);
        }
        return auth;
    }

    private JsonNode mapBearerAuthenticator(JsonNode authenticator) {
        ObjectNode auth = JSON.objectNode();
        auth.put("type", "bearerToken");
        String value = textValue(authenticator.path("api_token"));
        if (value == null) {
            value = textValue(authenticator.path("token"));
        }
        if (value == null) {
            value = textValue(authenticator.path("value"));
        }
        if (value != null) {
            auth.put("value", value);
        }
        return auth;
    }

    private JsonNode mapExtensionAuthenticator(JsonNode authenticator, String type) {
        ObjectNode auth = JSON.objectNode();
        auth.put("type", "extension");

        ObjectNode extension = auth.putObject("extension");
        extension.put("type", "java");
        extension.put("source", "airbyte");
        extension.put("originalType", type);

        String className = textValue(authenticator.path("class_name"));
        if (className == null) {
            className = textValue(authenticator.path("className"));
        }
        if (className != null) {
            extension.put("className", className);
        }

        extension.set("original", authenticator.deepCopy());
        return auth;
    }

    private JsonNode emptyInputSchema() {
        ObjectNode schema = JSON.objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        return schema;
    }

    private JsonNode annotationsForMethod(String method) {
        ObjectNode annotations = JSON.objectNode();
        if ("GET".equalsIgnoreCase(method)) {
            annotations.put("readOnlyHint", true);
        }
        return annotations;
    }

    private JsonNode normalizeRequester(JsonNode requester) {
        if (!requester.isObject()) {
            return requester.deepCopy();
        }

        ObjectNode normalized = requester.deepCopy();
        renameField(normalized, "url_base", "urlBase");
        renameField(normalized, "http_method", "method");
        return normalized;
    }

    private JsonNode normalizeConnectionSpec(JsonNode schema) {
        JsonNode normalized = schema.deepCopy();
        normalizeConnectionSpecNode(normalized);
        return normalized;
    }

    private void normalizeConnectionSpecNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            renameField(object, "airbyte_secret", "secret");
            Iterator<JsonNode> children = object.elements();
            while (children.hasNext()) {
                normalizeConnectionSpecNode(children.next());
            }
            return;
        }
        if (node.isArray()) {
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                normalizeConnectionSpecNode(children.next());
            }
        }
    }

    private void renameField(ObjectNode object, String from, String to) {
        if (object.has(from) && !object.has(to)) {
            object.set(to, object.get(from));
            object.remove(from);
        }
    }

    private JsonNode firstPresent(JsonNode node, String firstField, String secondField) {
        JsonNode first = node.path(firstField);
        return first.isMissingNode() || first.isNull() ? node.path(secondField) : first;
    }

    private JsonNode missingAsNull(JsonNode node) {
        return node == null || node.isMissingNode() ? NullNode.getInstance() : node;
    }

    private String textOrDefault(JsonNode node, String fallback) {
        String value = textValue(node);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isValueNode()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String jsonPointerLeaf(JsonNode refNode) {
        String ref = textValue(refNode);
        if (ref == null) {
            return null;
        }
        int slash = ref.lastIndexOf('/');
        return slash >= 0 ? ref.substring(slash + 1) : ref;
    }

    private String endpointPathFor(String streamName, int index) {
        String slug = slugify(streamName);
        if (slug.isBlank()) {
            slug = "stream-" + (index + 1);
        }
        return "endpoints/" + slug + ".json";
    }

    private String titleize(String value) {
        String slug = slugify(value).replace('-', ' ');
        if (slug.isBlank()) {
            return value;
        }
        String[] parts = slug.split("\\s+");
        StringBuilder title = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!title.isEmpty()) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return title.toString();
    }

    private String uniqueRequesterKey(String streamName, Map<String, JsonNode> requesters) {
        String baseName = definitionNameSlug(streamName);
        if (baseName.isBlank()) {
            baseName = "stream";
        }

        String candidate = baseName + "_requester";
        int suffix = 2;
        while (requesters.containsKey(candidate)) {
            candidate = baseName + "_requester_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String deriveMetadataName(Path manifestPath) {
        String fileName = manifestPath.getFileName().toString();
        return fileName.replaceAll("(?i)\\.ya?ml$", "")
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
    }

    private String slugify(String value) {
        return value == null
                ? ""
                : value.trim()
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-+", "")
                        .replaceAll("-+$", "");
    }

    private String definitionNameSlug(String value) {
        return value == null
                ? ""
                : value.trim()
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "_")
                        .replaceAll("^_+", "")
                        .replaceAll("_+$", "");
    }

    private long secondsForInterval(String interval) {
        if (interval == null) {
            return -1;
        }
        return switch (interval.toLowerCase(Locale.ROOT)) {
            case "second" -> 1;
            case "minute" -> 60;
            case "hour" -> 3600;
            case "day" -> 86400;
            default -> -1;
        };
    }

    private record BudgetMapping(String qps) {}

    private record DefinitionBuckets(
            Map<String, JsonNode> requesters,
            Map<String, JsonNode> authenticators) {}

    private record MappedEndpoint(
            String path,
            EndpointDefinition endpoint) {}

    private record PartitionInputMapping(
            String path,
            JsonNode inputSchema) {}

    private static final class MissingNodeHolder {
        private static final JsonNode INSTANCE = NullNode.getInstance();
    }
}
