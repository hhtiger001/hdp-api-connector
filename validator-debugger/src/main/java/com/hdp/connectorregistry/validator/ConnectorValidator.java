package com.hdp.connectorregistry.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.hdp.connectorregistry.io.ConnectorLoader.LoadedConnector;
import com.hdp.connectorregistry.model.SignerDefinition;
import com.hdp.connectorregistry.model.StreamDefinition;
import com.hdp.connectorregistry.signer.SignerRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConnectorValidator {
    private final SignerRegistry signerRegistry = new SignerRegistry();

    public List<Diagnostic> validate(LoadedConnector loadedConnector, JsonNode config) {
        var diagnostics = new ArrayList<Diagnostic>();
        if (loadedConnector == null) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "CONNECTOR_MISSING",
                    "Connector definition is missing"));
            return diagnostics;
        }

        validateConnectionSpec(loadedConnector.connector().spec().connectionSpec(), config, diagnostics);
        validateSchemaReferences(loadedConnector, diagnostics);
        validateSigners(loadedConnector.connector().spec().signers(), diagnostics);
        return diagnostics;
    }

    private void validateConnectionSpec(JsonNode connectionSpec, JsonNode config, List<Diagnostic> diagnostics) {
        if (connectionSpec == null || connectionSpec.isNull()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "CONNECTION_SPEC_MISSING",
                    "Missing connectionSpec"));
            return;
        }
        if (config == null || config.isNull()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "CONFIG_MISSING",
                    "Missing config payload"));
            return;
        }

        validateNodeAgainstSchema("$", connectionSpec, config, diagnostics);
    }

    private void validateSchemaReferences(LoadedConnector loadedConnector, List<Diagnostic> diagnostics) {
        for (StreamDefinition stream : loadedConnector.connector().spec().streams()) {
            if (stream.schema() == null || stream.schema().ref() == null || stream.schema().ref().isBlank()) {
                continue;
            }
            if (!loadedConnector.schemasByRef().containsKey(stream.schema().ref())) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.ERROR,
                        "SCHEMA_MISSING",
                        "Missing schema ref: " + stream.schema().ref()));
            }
        }
    }

    private void validateSigners(Map<String, SignerDefinition> signers, List<Diagnostic> diagnostics) {
        if (signers == null || signers.isEmpty()) {
            return;
        }

        for (var entry : signers.entrySet()) {
            String name = entry.getKey();
            SignerDefinition signerDefinition = entry.getValue();
            if (signerDefinition == null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.ERROR,
                        "SIGNER_MISSING",
                        "Signer " + name + " is missing"));
                continue;
            }
            if (signerDefinition.type() != null && !"java".equalsIgnoreCase(signerDefinition.type())) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.ERROR,
                        "SIGNER_TYPE_UNSUPPORTED",
                        "Signer " + name + " must use type java"));
                continue;
            }

            try {
                signerRegistry.instantiate(signerDefinition.className());
            } catch (RuntimeException exception) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.ERROR,
                        "SIGNER_LOAD_FAILED",
                        "Signer " + name + " failed to load: " + exception.getMessage()));
            }
        }
    }

    private void validateNodeAgainstSchema(String path, JsonNode schema, JsonNode value, List<Diagnostic> diagnostics) {
        if (schema == null || schema.isNull() || value == null || value.isMissingNode()) {
            return;
        }

        String expectedType = schema.path("type").asText(null);
        if (expectedType != null && !matchesType(expectedType, value)) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "CONFIG_TYPE_MISMATCH",
                    path + " expected " + expectedType + " but was " + describeType(value)));
            return;
        }

        if (value.isObject()) {
            validateRequiredFields(path, schema, value, diagnostics);
            JsonNode properties = schema.path("properties");
            if (properties.isObject()) {
                properties.fields().forEachRemaining(entry -> {
                    JsonNode childSchema = entry.getValue();
                    JsonNode childValue = value.get(entry.getKey());
                    if (childValue != null && !childValue.isNull()) {
                        validateNodeAgainstSchema(path + "." + entry.getKey(), childSchema, childValue, diagnostics);
                    }
                });
            }
        }

        if (value.isArray()) {
            JsonNode items = schema.path("items");
            if (items.isObject()) {
                for (int index = 0; index < value.size(); index++) {
                    validateNodeAgainstSchema(path + "[" + index + "]", items, value.get(index), diagnostics);
                }
            }
        }
    }

    private void validateRequiredFields(
            String path, JsonNode schema, JsonNode value, List<Diagnostic> diagnostics) {
        JsonNode required = schema.path("required");
        if (!required.isArray()) {
            return;
        }

        required.forEach(requiredField -> {
            String fieldName = requiredField.asText(null);
            if (fieldName == null || fieldName.isBlank()) {
                return;
            }
            JsonNode childValue = value.get(fieldName);
            if (childValue == null || childValue.isNull() || childValue.isMissingNode()) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.ERROR,
                        "CONFIG_MISSING_REQUIRED",
                        path + " missing required field: " + fieldName));
            }
        });
    }

    private boolean matchesType(String expectedType, JsonNode value) {
        return switch (expectedType) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private String describeType(JsonNode value) {
        if (value.isTextual()) {
            return "string";
        }
        if (value.isIntegralNumber()) {
            return "integer";
        }
        if (value.isFloatingPointNumber()) {
            return "number";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        if (value.isArray()) {
            return "array";
        }
        if (value.isObject()) {
            return "object";
        }
        if (value.isNull()) {
            return "null";
        }
        return value.getNodeType().name().toLowerCase();
    }
}
