package com.hdp.connectorregistry.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hdp.connectorregistry.io.ConnectorLoader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AirbyteManifestConverterTest {
    @TempDir
    Path tempDir;

    private final AirbyteManifestConverter converter = new AirbyteManifestConverter();

    @Test
    void convertsSimpleManifestIntoReadyConnector() throws Exception {
        ConversionResult result = converter.convert(fixture("simple_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.READY);
        assertThat(result.connector().metadata().name()).isEqualTo("simple-manifest");
        assertThat(result.connector().metadata().source().type()).isEqualTo("airbyte-manifest");
        assertThat(result.connector().metadata().source().originVersion()).isEqualTo("0.1.0");
        assertThat(result.connector().spec().connectionSpec().path("required")).hasSize(2);
        assertThat(result.connector().spec().defaults().baseUrl()).isEqualTo("{{ config['base_url'] }}");
        assertThat(result.connector().spec().defaults().qps()).isEqualTo("2");
        assertThat(result.connector().spec().definitions().requesters()).containsKey("base_requester");
        assertThat(result.connector().spec().definitions().authenticators()).containsKey("base_authenticator");
        assertThat(result.connector().spec().streams()).hasSize(1);

        var stream = result.connector().spec().streams().get(0);
        assertThat(stream.name()).isEqualTo("users");
        assertThat(stream.qps()).isEqualTo("2");
        assertThat(stream.request().requesterRef()).isEqualTo("base_requester");
        assertThat(stream.request().path()).isEqualTo("/users");
        assertThat(stream.request().method()).isEqualTo("GET");
        assertThat(stream.request().qps()).isEqualTo("2");
        assertThat(stream.schema().ref()).isEqualTo("schemas/users.json");
        assertThat(stream.schema().inline()).isNull();

        assertThat(result.schemasByPath()).containsOnlyKeys("schemas/users.json");
        assertThat(result.schemasByPath().get("schemas/users.json").path("properties").path("id").path("type").asText())
                .isEqualTo("string");
        assertThat(result.report().issues()).isEmpty();
        assertThat(result.report().originalApiBudget().path("policies")).hasSize(1);
    }

    @Test
    void marksManifestWithoutStreamsAsBlocked() throws Exception {
        ConversionResult result = converter.convert(fixture("blocked_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.BLOCKED);
        assertThat(result.connector().spec().streams()).isEmpty();
        assertThat(result.report().issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("NO_STREAMS_FOUND");
                    assertThat(issue.severity()).isEqualTo("ERROR");
                });
    }

    @Test
    void marksManifestWithCustomComponentAsDraft() throws Exception {
        ConversionResult result = converter.convert(fixture("custom_component_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.DRAFT);
        assertThat(result.connector().spec().streams()).hasSize(1);
        assertThat(result.report().issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW");
                    assertThat(issue.severity()).isEqualTo("WARNING");
                });
    }

    @Test
    void keepsComplexBudgetInReportWithoutInventingQps() throws Exception {
        ConversionResult result = converter.convert(fixture("complex_budget_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.DRAFT);
        assertThat(result.connector().spec().defaults().qps()).isNull();
        assertThat(result.connector().spec().streams()).singleElement().satisfies(stream ->
                assertThat(stream.qps()).isNull());
        assertThat(result.report().issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("API_BUDGET_REQUIRES_MANUAL_REVIEW");
                    assertThat(issue.severity()).isEqualTo("WARNING");
                    assertThat(issue.originalValue()).contains("BurstBudget");
                });
        assertThat(result.report().originalApiBudget().path("policies")).hasSize(2);
    }

    @Test
    void downgradesMissingSchemaAndAvoidsDanglingSchemaRef() throws Exception {
        ConversionResult result = converter.convert(fixture("missing_schema_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.DRAFT);
        assertThat(result.report().issues())
                .anySatisfy(issue -> assertThat(issue.code()).isEqualTo("STREAM_SCHEMA_MISSING"));
        assertThat(result.connector().spec().streams()).singleElement().satisfies(stream -> {
            assertThat(stream.schema()).isNull();
            assertThat(stream.request().requesterRef()).isEqualTo("users_requester");
        });
        assertThat(result.schemasByPath()).isEmpty();

        new OutputWriter().write(result, tempDir);

        String connectorYaml = Files.readString(tempDir.resolve("connector.yaml"));
        assertThat(connectorYaml).doesNotContain("schemas/users.json");
        assertThat(new ConnectorLoader().load(tempDir.resolve("connector.yaml")).schemasByRef()).isEmpty();
    }

    @Test
    void createsDedicatedRequesterDefinitionForInlineRequester() throws Exception {
        ConversionResult result = converter.convert(fixture("inline_requester_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.READY);
        assertThat(result.connector().spec().definitions().requesters())
                .containsKeys("base_requester", "orders_requester");
        assertThat(result.connector().spec().definitions().requesters().get("base_requester").path("urlBase").asText())
                .isEqualTo("https://api.example.com");
        assertThat(result.connector().spec().definitions().requesters().get("orders_requester").path("urlBase").asText())
                .isEqualTo("https://orders.example.com");
        assertThat(result.connector().spec().streams()).singleElement().satisfies(stream -> {
            assertThat(stream.name()).isEqualTo("orders");
            assertThat(stream.request().requesterRef()).isEqualTo("orders_requester");
            assertThat(stream.request().path()).isEqualTo("/v2/orders");
        });
    }

    @Test
    void writesConnectorSchemasAndConversionReport() throws Exception {
        ConversionResult result = converter.convert(fixture("simple_manifest.yaml"));

        new OutputWriter().write(result, tempDir);

        assertThat(Files.exists(tempDir.resolve("connector.yaml"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("schemas/users.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("conversion-report.json"))).isTrue();

        String connectorYaml = Files.readString(tempDir.resolve("connector.yaml"));
        assertThat(connectorYaml).contains("airbyte-manifest");
        assertThat(connectorYaml).contains("schemas/users.json");

        JsonNode report = new AirbyteManifestLoader().loadJson(tempDir.resolve("conversion-report.json"));
        assertThat(report.path("status").asText()).isEqualTo("READY");
    }

    private Path fixture(String name) throws URISyntaxException {
        URL resource = Objects.requireNonNull(getClass().getResource("/fixtures/airbyte/" + name));
        return Path.of(resource.toURI());
    }
}
