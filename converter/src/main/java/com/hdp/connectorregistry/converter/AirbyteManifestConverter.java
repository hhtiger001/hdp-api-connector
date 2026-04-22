package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.hdp.connectorregistry.model.ApiConnector;
import com.hdp.connectorregistry.model.ConnectorSpec;
import com.hdp.connectorregistry.model.Defaults;
import com.hdp.connectorregistry.model.Definitions;
import com.hdp.connectorregistry.model.Metadata;
import com.hdp.connectorregistry.model.RequestDefinition;
import com.hdp.connectorregistry.model.SchemaDefinition;
import com.hdp.connectorregistry.model.StreamDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AirbyteManifestConverter {
    private static final String CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW =
            "CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW";
    private static final String NO_STREAMS_FOUND = "NO_STREAMS_FOUND";
    private static final String API_BUDGET_REQUIRES_MANUAL_REVIEW =
            "API_BUDGET_REQUIRES_MANUAL_REVIEW";

    private final AirbyteManifestLoader loader = new AirbyteManifestLoader();

    public ConversionResult convert(Path manifestPath) {
        JsonNode manifest = loader.load(manifestPath);
        List<ConversionIssue> issues = new ArrayList<>();
        detectCustomComponent(manifest.path("definitions"), "/definitions", issues);

        BudgetMapping budgetMapping = mapBudget(manifest.path("api_budget"), issues);
        Definitions definitions = mapDefinitions(manifest.path("definitions"));
        List<StreamDefinition> streams = new ArrayList<>();
        Map<String, JsonNode> schemasByPath = new LinkedHashMap<>();

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
                JsonNode streamNode = streamsNode.get(index);
                StreamDefinition stream = mapStream(streamNode, index, definitions, budgetMapping.qps(), issues);
                streams.add(stream);

                JsonNode schema = extractSchema(streamNode);
                if (!schema.isMissingNode() && !schema.isNull()) {
                    schemasByPath.put(stream.schema().ref(), schema.deepCopy());
                }
            }
        }

        ConversionStatus status = deriveStatus(issues);
        String originVersion = textValue(manifest.path("version"));
        String metadataName = deriveMetadataName(manifestPath);
        ApiConnector connector = new ApiConnector(
                "hdp.connector/v1alpha1",
                "ApiConnector",
                new Metadata(
                        metadataName,
                        metadataName,
                        new Metadata.SourceMetadata(
                                "airbyte-manifest",
                                originVersion,
                                manifestPath.toString())),
                new ConnectorSpec(
                        missingAsNull(manifest.path("spec").path("connection_specification")),
                        new Defaults(budgetMapping.qps(), deriveBaseUrl(manifest, definitions)),
                        definitions,
                        Map.of(),
                        List.copyOf(streams)));

        ConversionReport report = new ConversionReport(
                status,
                List.copyOf(issues),
                originVersion,
                missingAsNull(manifest.path("api_budget")));

        return new ConversionResult(connector, Map.copyOf(schemasByPath), report);
    }

    private Definitions mapDefinitions(JsonNode definitionsNode) {
        if (!definitionsNode.isObject()) {
            return new Definitions(Map.of(), Map.of());
        }

        Map<String, JsonNode> requesters = new LinkedHashMap<>();
        Map<String, JsonNode> authenticators = new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = definitionsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode definition = entry.getValue();
            if (looksLikeRequester(definition)) {
                requesters.put(entry.getKey(), definition.deepCopy());
            }
            if (looksLikeAuthenticator(definition)) {
                authenticators.put(entry.getKey(), definition.deepCopy());
            }
        }

        return new Definitions(Map.copyOf(requesters), Map.copyOf(authenticators));
    }

    private StreamDefinition mapStream(
            JsonNode streamNode,
            int index,
            Definitions definitions,
            String defaultQps,
            List<ConversionIssue> issues) {
        String streamName = streamNode.path("name").asText("stream-" + (index + 1));
        detectCustomComponent(streamNode, "/streams/" + index, issues);

        JsonNode schema = extractSchema(streamNode);
        if (schema.isMissingNode() || schema.isNull()) {
            issues.add(new ConversionIssue(
                    "WARNING",
                    "STREAM_SCHEMA_MISSING",
                    "Stream schema could not be extracted and requires manual review",
                    "/streams/" + index + "/schema_loader",
                    null));
        }

        String schemaRef = schemaPathFor(streamName, index);
        String qps = textOrDefault(streamNode.path("qps"), defaultQps);
        return new StreamDefinition(
                streamName,
                qps,
                new RequestDefinition(
                        requesterRef(streamNode, definitions.requesters()),
                        requestPath(streamNode, streamName),
                        requestMethod(streamNode),
                        null,
                        qps),
                new SchemaDefinition(schemaRef, null));
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

        boolean draft = issues.stream().anyMatch(issue -> CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW.equals(issue.code()));
        if (draft) {
            return ConversionStatus.DRAFT;
        }

        return ConversionStatus.READY;
    }

    private boolean looksLikeRequester(JsonNode definition) {
        String type = definition.path("type").asText("");
        return definition.has("urlBase")
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

    private JsonNode extractSchema(JsonNode streamNode) {
        JsonNode schema = streamNode.path("schema_loader").path("schema");
        if (!schema.isMissingNode() && !schema.isNull()) {
            return schema;
        }

        schema = streamNode.path("json_schema");
        if (!schema.isMissingNode() && !schema.isNull()) {
            return schema;
        }

        schema = streamNode.path("schema");
        if (!schema.isMissingNode() && !schema.isNull()) {
            return schema;
        }

        return MissingNodeHolder.INSTANCE;
    }

    private String requesterRef(JsonNode streamNode, Map<String, JsonNode> requesters) {
        String directRef = jsonPointerLeaf(streamNode.path("retriever").path("requester").path("$ref"));
        if (directRef != null) {
            return directRef;
        }

        if (!requesters.isEmpty()) {
            return requesters.keySet().iterator().next();
        }

        return "inline_requester";
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

    private String deriveBaseUrl(JsonNode manifest, Definitions definitions) {
        JsonNode definitionsNode = manifest.path("definitions");
        if (definitionsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = definitionsNode.fields();
            while (fields.hasNext()) {
                JsonNode definition = fields.next().getValue();
                String urlBase = textValue(definition.path("urlBase"));
                if (urlBase != null) {
                    return urlBase;
                }
            }
        }

        for (JsonNode requester : definitions.requesters().values()) {
            String urlBase = textValue(requester.path("urlBase"));
            if (urlBase != null) {
                return urlBase;
            }
        }

        JsonNode firstStreamRequester = manifest.path("streams").path(0).path("retriever").path("requester");
        return textValue(firstStreamRequester.path("urlBase"));
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

    private String schemaPathFor(String streamName, int index) {
        String slug = slugify(streamName);
        if (slug.isBlank()) {
            slug = "stream-" + (index + 1);
        }
        return "schemas/" + slug + ".json";
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

    private static final class MissingNodeHolder {
        private static final JsonNode INSTANCE = NullNode.getInstance();
    }
}
