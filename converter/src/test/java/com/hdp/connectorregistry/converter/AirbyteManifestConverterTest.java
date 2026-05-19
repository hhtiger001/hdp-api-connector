package com.hdp.connectorregistry.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
        assertThat(result.connector().spec().connectionSpec().path("required")).hasSize(2);
        assertThat(result.connector().spec().request().baseUrl()).isEqualTo("{{ config['base_url'] }}");
        assertThat(result.connector().spec().request().auth().path("type").asText()).isEqualTo("apiKey");
        assertThat(result.connector().spec().request().auth().path("name").asText()).isEqualTo("X-API-Key");

        assertThat(result.endpointsByPath()).containsOnlyKeys("endpoints/users.json");
        var endpoint = result.endpointsByPath().get("endpoints/users.json");
        assertThat(endpoint.name()).isEqualTo("users");
        assertThat(endpoint.inputSchema().path("type").asText()).isEqualTo("object");
        assertThat(endpoint.request().path()).isEqualTo("/users");
        assertThat(endpoint.request().method()).isEqualTo("GET");
        assertThat(endpoint.outputSchema().path("properties").path("id").path("type").asText())
                .isEqualTo("string");
        assertThat(result.report().issues()).isEmpty();
        assertThat(result.report().originalApiBudget().path("policies")).hasSize(1);
    }

    @Test
    void marksManifestWithoutStreamsAsBlocked() throws Exception {
        ConversionResult result = converter.convert(fixture("blocked_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.BLOCKED);
        assertThat(result.endpointsByPath()).isEmpty();
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
        assertThat(result.endpointsByPath()).hasSize(1);
        assertThat(result.report().issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW");
                    assertThat(issue.severity()).isEqualTo("WARNING");
                });
    }

    @Test
    void mapsCustomAuthenticatorToExtensionAuthAndKeepsDraftStatus() throws Exception {
        ConversionResult result = converter.convert(fixture("custom_authenticator_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.DRAFT);
        assertThat(result.connector().spec().request().auth().path("type").asText()).isEqualTo("extension");
        assertThat(result.connector().spec().request().auth().path("extension").path("source").asText())
                .isEqualTo("airbyte");
        assertThat(result.connector().spec().request().auth().path("extension").path("originalType").asText())
                .isEqualTo("CustomAuthenticator");
        assertThat(result.connector().spec().request().auth().path("extension").path("className").asText())
                .isEqualTo("source_signed.components.HmacAuthenticator");
    }

    @Test
    void keepsComplexBudgetInReportWithoutInventingRuntimePagination() throws Exception {
        ConversionResult result = converter.convert(fixture("complex_budget_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.DRAFT);
        assertThat(result.report().issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("API_BUDGET_REQUIRES_MANUAL_REVIEW");
                    assertThat(issue.severity()).isEqualTo("WARNING");
                    assertThat(issue.originalValue()).contains("BurstBudget");
                });
        assertThat(result.report().originalApiBudget().path("policies")).hasSize(2);
    }

    @Test
    void downgradesMissingSchemaAndAvoidsDanglingSchemaFile() throws Exception {
        ConversionResult result = converter.convert(fixture("missing_schema_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.DRAFT);
        assertThat(result.report().issues())
                .anySatisfy(issue -> assertThat(issue.code()).isEqualTo("STREAM_SCHEMA_MISSING"));
        assertThat(result.endpointsByPath()).containsOnlyKeys("endpoints/users.json");
        assertThat(result.endpointsByPath().get("endpoints/users.json").outputSchema().isNull()).isTrue();

        new OutputWriter().write(result, tempDir);

        assertThat(Files.exists(tempDir.resolve("connector.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("endpoints/users.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("schemas/users.json"))).isFalse();
    }

    @Test
    void createsEndpointForInlineRequester() throws Exception {
        ConversionResult result = converter.convert(fixture("inline_requester_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.READY);
        assertThat(result.connector().spec().request().baseUrl()).isEqualTo("https://orders.example.com");
        assertThat(result.endpointsByPath()).containsOnlyKeys("endpoints/orders.json");
        assertThat(result.endpointsByPath().get("endpoints/orders.json").request().path()).isEqualTo("/v2/orders");
    }

    @Test
    void mapsBasicAuthenticatorIntoGlobalRequestAuth() throws Exception {
        ConversionResult result = converter.convert(fixture("basic_auth_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.READY);
        assertThat(result.connector().spec().request().auth().path("type").asText()).isEqualTo("basic");
        assertThat(result.connector().spec().request().auth().path("username").asText())
                .isEqualTo("{{ config['api_key'] }}");
        assertThat(result.connector().spec().request().auth().path("password").asText())
                .isEqualTo("{{ config['api_secret'] }}");
    }

    @Test
    void convertsOfficialPokeApiStyleRefsIntoReadyConnector() throws Exception {
        ConversionResult result = converter.convert(fixture("pokeapi_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.READY);
        assertThat(result.report().issues()).isEmpty();
        assertThat(result.connector().spec().request().baseUrl()).isEqualTo("https://pokeapi.co/api/v2/pokemon");

        assertThat(result.endpointsByPath()).containsOnlyKeys("endpoints/pokemon.json");
        var endpoint = result.endpointsByPath().get("endpoints/pokemon.json");
        assertThat(endpoint.name()).isEqualTo("pokemon");
        assertThat(endpoint.request().path()).isEqualTo("/{{config['pokemon_name']}}");
        assertThat(endpoint.request().method()).isEqualTo("GET");
        assertThat(endpoint.outputSchema().path("properties").path("name").path("type").asText()).isEqualTo("string");
    }

    @Test
    void convertsOfficialClockifyStyleInlineRequestersIntoReadyConnector() throws Exception {
        ConversionResult result = converter.convert(fixture("clockify_manifest.yaml"));

        assertThat(result.report().status()).isEqualTo(ConversionStatus.READY);
        assertThat(result.report().issues()).isEmpty();
        assertThat(result.endpointsByPath()).containsOnlyKeys("endpoints/users.json", "endpoints/projects.json");
        assertThat(result.connector().spec().connectionSpec().path("properties").path("api_key").path("secret").asBoolean())
                .isTrue();
        assertThat(result.connector().spec().connectionSpec().path("properties").path("api_key").has("airbyte_secret"))
                .isFalse();
        assertThat(result.connector().spec().request().baseUrl()).isEqualTo("https://api.clockify.me/api/v1/");
        assertThat(result.connector().spec().request().auth().path("type").asText()).isEqualTo("apiKey");
        assertThat(result.connector().spec().request().auth().path("name").asText()).isEqualTo("X-Api-Key");
        assertThat(result.endpointsByPath().get("endpoints/users.json").request().path())
                .isEqualTo("/workspaces/{{ config['workspace_id'] }}/users");
        assertThat(result.endpointsByPath().get("endpoints/projects.json").request().path())
                .isEqualTo("/workspaces/{{ config['workspace_id'] }}/projects");
    }

    @Test
    void writesConnectorJsonAndEndpoints() throws Exception {
        ConversionResult result = converter.convert(fixture("simple_manifest.yaml"));

        new OutputWriter().write(result, tempDir);

        assertThat(Files.exists(tempDir.resolve("connector.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("endpoints/users.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("compiled-plan.json"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("config.json"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("conversion-report.json"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("connector.yaml"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("schemas"))).isFalse();

        JsonNode endpoint = new AirbyteManifestLoader().loadJson(tempDir.resolve("endpoints/users.json"));
        assertThat(endpoint.path("outputSchema").path("properties").path("id").path("type").asText())
                .isEqualTo("string");

        JsonNode connector = new AirbyteManifestLoader().loadJson(tempDir.resolve("connector.json"));
        assertThat(connector.path("apiVersion").asText()).isEqualTo("hdp.connector/v1alpha1");
        assertThat(connector.path("metadata").path("name").asText()).isEqualTo("simple-manifest");
        assertThat(connector.path("tools").path(0).path("name").asText()).isEqualTo("users");
        assertThat(connector.path("tools").path(0).path("endpointRef").asText()).isEqualTo("endpoints/users.json");
        assertThat(connector.path("tools").path(0).has("outputSchema")).isFalse();
        assertThat(connector.path("tools").path(0).has("request")).isFalse();
    }

    @Test
    void writesSlimConnectorJsonForMultiEndpointConnectors() throws Exception {
        ConversionResult result = converter.convert(fixture("clockify_manifest.yaml"));

        new OutputWriter().write(result, tempDir);

        String connector = Files.readString(tempDir.resolve("connector.json"));
        String usersEndpoint = Files.readString(tempDir.resolve("endpoints/users.json"));
        String projectsEndpoint = Files.readString(tempDir.resolve("endpoints/projects.json"));

        assertThat(connector).contains("\"endpointRef\"").doesNotContain("\"outputSchema\"");
        assertThat(usersEndpoint).contains("\"outputSchema\"");
        assertThat(projectsEndpoint).contains("\"outputSchema\"");
        assertThat(connector.length()).isLessThan(usersEndpoint.length() + projectsEndpoint.length());
    }

    private Path fixture(String name) throws URISyntaxException {
        URL resource = Objects.requireNonNull(getClass().getResource("/fixtures/airbyte/" + name));
        return Path.of(resource.toURI());
    }
}
