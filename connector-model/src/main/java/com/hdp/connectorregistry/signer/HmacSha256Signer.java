package com.hdp.connectorregistry.signer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacSha256Signer implements RequestSigner {
    @Override
    public SignerResult sign(SignerContext context) {
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, Object> signerConfig = context.signerConfig() == null ? Map.of() : context.signerConfig();
        Map<String, Object> connectorConfig = context.connectorConfig() == null ? Map.of() : context.connectorConfig();

        String secretField = stringConfig(signerConfig, "secretField", "api_secret");
        String secret = Objects.toString(connectorConfig.get(secretField), "");
        if (secret.isBlank()) {
            throw new IllegalStateException("Missing signer secret config field: " + secretField);
        }

        String payload = canonicalPayload(context);
        String signature = hmacSha256(payload, secret, stringConfig(signerConfig, "encoding", "hex"));
        headers.put(stringConfig(signerConfig, "signatureHeader", "X-Signature"), signature);

        String timestampHeader = stringConfig(signerConfig, "timestampHeader", "");
        if (!timestampHeader.isBlank()) {
            headers.put(timestampHeader, Long.toString(context.timestamp().getEpochSecond()));
        }

        String keyField = stringConfig(signerConfig, "keyField", "");
        String keyHeader = stringConfig(signerConfig, "keyHeader", "");
        if (!keyField.isBlank() && !keyHeader.isBlank() && connectorConfig.containsKey(keyField)) {
            headers.put(keyHeader, Objects.toString(connectorConfig.get(keyField), ""));
        }

        return new SignerResult(headers, Map.of(), context.body());
    }

    private String canonicalPayload(SignerContext context) {
        String path = context.uri().getRawPath() == null ? "" : context.uri().getRawPath();
        String rawQuery = context.uri().getRawQuery();
        Map<String, String> queryParameters = new TreeMap<>(
                context.queryParameters() == null ? Map.of() : context.queryParameters());
        String renderedQuery = queryParameters.isEmpty()
                ? ""
                : queryParameters.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((left, right) -> left + "&" + right)
                        .orElse("");
        String query = rawQuery == null || rawQuery.isBlank()
                ? renderedQuery
                : renderedQuery.isBlank() ? rawQuery : rawQuery + "&" + renderedQuery;
        String pathAndQuery = query == null || query.isBlank() ? path : path + "?" + query;
        return String.join(
                "\n",
                context.method(),
                pathAndQuery,
                context.body() == null ? "" : context.body(),
                Long.toString(context.timestamp().getEpochSecond()),
                context.nonce() == null ? "" : context.nonce());
    }

    private String hmacSha256(String payload, String secret, String encoding) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            if ("base64".equalsIgnoreCase(encoding)) {
                return Base64.getEncoder().encodeToString(signature);
            }
            return HexFormat.of().formatHex(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate HMAC-SHA256 signature", exception);
        }
    }

    private String stringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value == null ? defaultValue : Objects.toString(value);
    }
}
