package com.hdp.connectorregistry.validator;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateResolver {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*(.+?)\\s*\\}\\}");

    public String resolve(String value, JsonNode config) {
        return resolve(value, config, null);
    }

    public String resolve(String value, JsonNode config, JsonNode input) {
        if (value == null) {
            return null;
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = resolveExpression(matcher.group(1), config, input);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String resolveExpression(String expression, JsonNode config, JsonNode input) {
        if (expression == null) {
            return "";
        }

        String trimmed = expression.trim();
        JsonNode root;
        String pathExpression;
        if (trimmed.startsWith("config")) {
            root = config;
            pathExpression = trimmed.substring("config".length());
        } else if (trimmed.startsWith("input")) {
            root = input;
            pathExpression = trimmed.substring("input".length());
        } else {
            return "";
        }

        if (root == null || root.isMissingNode() || root.isNull()) {
            return "";
        }

        JsonNode resolved = resolvePath(pathExpression, root);
        if (resolved == null || resolved.isMissingNode() || resolved.isNull()) {
            return "";
        }
        if (resolved.isTextual() || resolved.isNumber() || resolved.isBoolean()) {
            return resolved.asText();
        }
        return resolved.toString();
    }

    private JsonNode resolvePath(String pathExpression, JsonNode root) {
        List<String> segments = new ArrayList<>();
        int index = 0;
        while (index < pathExpression.length()) {
            char current = pathExpression.charAt(index);
            if (Character.isWhitespace(current) || current == '.') {
                index++;
                continue;
            }
            if (current == '[') {
                int endBracket = pathExpression.indexOf(']', index);
                if (endBracket < 0) {
                    return null;
                }
                String token = pathExpression.substring(index + 1, endBracket).trim();
                if ((token.startsWith("\"") && token.endsWith("\""))
                        || (token.startsWith("'") && token.endsWith("'"))) {
                    token = token.substring(1, token.length() - 1);
                }
                if (!token.isEmpty()) {
                    segments.add(token);
                }
                index = endBracket + 1;
                continue;
            }

            int start = index;
            while (index < pathExpression.length()) {
                char character = pathExpression.charAt(index);
                if (character == '.' || character == '[' || Character.isWhitespace(character)) {
                    break;
                }
                index++;
            }
            String token = pathExpression.substring(start, index).trim();
            if (!token.isEmpty()) {
                segments.add(token);
            }
        }

        JsonNode current = root;
        for (String segment : segments) {
            current = current.path(segment);
        }
        return current;
    }
}
