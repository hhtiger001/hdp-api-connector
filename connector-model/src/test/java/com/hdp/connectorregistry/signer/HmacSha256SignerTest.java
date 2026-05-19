package com.hdp.connectorregistry.signer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HmacSha256SignerTest {
    @Test
    void signsCanonicalRequestAndAddsConfiguredHeaders() {
        SignerResult result = new HmacSha256Signer().sign(new SignerContext(
                "GET",
                URI.create("https://api.example.com/v1/users"),
                Map.of(),
                Map.of("page", "1"),
                "",
                Map.of("api_key", "key-1", "api_secret", "secret-1"),
                Map.of(
                        "signatureHeader", "X-HDP-Signature",
                        "timestampHeader", "X-HDP-Timestamp",
                        "keyField", "api_key",
                        "keyHeader", "X-HDP-Key"),
                Instant.ofEpochSecond(1700000000),
                "nonce-1"));

        assertThat(result.headers())
                .containsEntry("X-HDP-Signature", "d68d39679b3b0cea487d1009f797465e63144ce0c50f8438c331249d2f2ef55c")
                .containsEntry("X-HDP-Timestamp", "1700000000")
                .containsEntry("X-HDP-Key", "key-1");
        assertThat(result.queryParameters()).isEmpty();
    }
}
