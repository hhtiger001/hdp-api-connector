package com.hdp.connectorregistry.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ConnectorValidatorTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void returnsNoErrorsForValidConnectorAndConfig() throws Exception {
        var loaded = new ConnectorLoader().load(resourcePath("fixtures/connector/minimal/connector.yaml"));
        var config = OBJECT_MAPPER.readTree(resourcePath("fixtures/config/valid-config.json").toFile());

        var diagnostics = new ConnectorValidator().validate(loaded, config);

        assertThat(diagnostics).noneMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void returnsErrorWhenRequiredConfigIsMissing() throws Exception {
        var loaded = new ConnectorLoader().load(resourcePath("fixtures/connector/minimal/connector.yaml"));
        var config = OBJECT_MAPPER.readTree(resourcePath("fixtures/config/missing-api-key.json").toFile());

        var diagnostics = new ConnectorValidator().validate(loaded, config);

        assertThat(diagnostics)
                .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR
                        && diagnostic.message().contains("api_key"));
    }

    @Test
    void returnsErrorWhenSignerCannotLoad() throws Exception {
        var loaded = new ConnectorLoader().load(resourcePath("fixtures/connector/bad-signer/connector.yaml"));
        var config = OBJECT_MAPPER.readTree(resourcePath("fixtures/config/valid-config.json").toFile());

        var diagnostics = new ConnectorValidator().validate(loaded, config);

        assertThat(diagnostics)
                .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR
                        && diagnostic.message().contains("broken_signer"));
    }

    private static Path resourcePath(String resourceName) throws URISyntaxException {
        var resource = ConnectorValidatorTest.class.getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource, resourceName).toURI());
    }
}
