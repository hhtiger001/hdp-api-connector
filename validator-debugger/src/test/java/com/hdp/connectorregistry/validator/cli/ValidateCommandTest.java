package com.hdp.connectorregistry.validator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ValidateCommandTest {
    @Test
    void reportsSchemaLoadFailureAsDiagnostic() throws Exception {
        var commandLine = new CommandLine(new ValidateCommand());
        var output = new ByteArrayOutputStream();
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute(
                "--connector",
                resourcePath("fixtures/connector/missing-schema/connector.yaml").toString(),
                "--config",
                resourcePath("fixtures/config/valid-config.json").toString());

        assertThat(exitCode).isEqualTo(1);
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("ERROR SCHEMA_LOAD_FAILED")
                .contains("Unable to read schema");
    }

    private static Path resourcePath(String resourceName) throws URISyntaxException {
        var resource = ValidateCommandTest.class.getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource, resourceName).toURI());
    }
}
